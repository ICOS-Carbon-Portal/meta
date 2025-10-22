#!/usr/bin/env python3
"""
Automatic Ontop Mapping Generator for Class-Based Tables

Generates Ontop .obda mappings from the class-based database schema
and OWL ontology. Intelligently creates mappings for:
- rdf:type assertions
- Datatype properties
- Object properties (foreign keys)
- Multi-valued properties (junction tables)

The generator introspects the database schema and parses the ontology
to create optimized, well-documented Ontop mappings.
"""

import psycopg2
import psycopg2.extras
import argparse
import sys
import os
import re
from datetime import datetime
from collections import defaultdict

try:
    from rdflib import Graph, RDF, RDFS, OWL, Namespace
    HAS_RDFLIB = True
except ImportError:
    HAS_RDFLIB = False
    print("Warning: rdflib not installed. Ontology parsing disabled.")
    print("Install with: pip install rdflib")


# ============================================================
# Configuration
# ============================================================

NAMESPACES = {
    'cpmeta': 'http://meta.icos-cp.eu/ontologies/cpmeta/',
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'xsd': 'http://www.w3.org/2001/XMLSchema#',
    'prov': 'http://www.w3.org/ns/prov#',
    'owl': 'http://www.w3.org/2002/07/owl#',
}

# Columns to skip (internal/denormalized)
SKIP_COLUMNS = {
    'id', 'created_at', 'updated_at',
    # Denormalized columns (documented in comments)
    'spec_label', 'theme_label', 'theme_icon', 'format_uri',
    'responsible_org_name', 'climate_zone_label',
    'station_uri', 'station_name', 'station_id_value',
}

# PostgreSQL to XSD type mapping
PG_TO_XSD = {
    'bigint': 'xsd:long',
    'integer': 'xsd:integer',
    'smallint': 'xsd:integer',
    'real': 'xsd:float',
    'double precision': 'xsd:double',
    'numeric': 'xsd:decimal',
    'boolean': 'xsd:boolean',
    'date': 'xsd:date',
    'timestamp with time zone': 'xsd:dateTime',
    'timestamp without time zone': 'xsd:dateTime',
    'time': 'xsd:time',
    'text': None,  # No type cast for strings
    'character varying': None,
    'jsonb': None,
}


# ============================================================
# Database Introspection
# ============================================================

class DatabaseIntrospector:
    """Introspects PostgreSQL database schema."""

    def __init__(self, conn):
        self.conn = conn
        self.cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)
        self._tables_cache = None
        self._columns_cache = {}
        self._fks_cache = {}

    def get_tables(self, exclude_patterns=None):
        """Get all user tables."""
        if self._tables_cache is not None:
            return self._tables_cache

        self.cursor.execute("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
        """)

        tables = [row['table_name'] for row in self.cursor.fetchall()]

        # Filter out excluded patterns
        if exclude_patterns:
            filtered = []
            for table in tables:
                skip = False
                for pattern in exclude_patterns:
                    if re.search(pattern, table):
                        skip = True
                        break
                if not skip:
                    filtered.append(table)
            tables = filtered

        self._tables_cache = tables
        return tables

    def get_columns(self, table):
        """Get columns for a table with types."""
        if table in self._columns_cache:
            return self._columns_cache[table]

        self.cursor.execute("""
            SELECT
                column_name,
                data_type,
                udt_name,
                is_nullable,
                column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = %s
            ORDER BY ordinal_position
        """, (table,))

        columns = []
        for row in self.cursor.fetchall():
            col_info = {
                'name': row['column_name'],
                'type': row['data_type'],
                'udt_name': row['udt_name'],
                'nullable': row['is_nullable'] == 'YES',
                'default': row['column_default'],
            }
            columns.append(col_info)

        self._columns_cache[table] = columns
        return columns

    def get_foreign_keys(self, table):
        """Get foreign key relationships for a table."""
        if table in self._fks_cache:
            return self._fks_cache[table]

        self.cursor.execute("""
            SELECT
                kcu.column_name,
                ccu.table_name AS foreign_table_name,
                ccu.column_name AS foreign_column_name,
                tc.constraint_name
            FROM information_schema.table_constraints AS tc
            JOIN information_schema.key_column_usage AS kcu
              ON tc.constraint_name = kcu.constraint_name
              AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage AS ccu
              ON ccu.constraint_name = tc.constraint_name
              AND ccu.table_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_schema = 'public'
              AND tc.table_name = %s
        """, (table,))

        fks = []
        for row in self.cursor.fetchall():
            fk_info = {
                'column': row['column_name'],
                'ref_table': row['foreign_table_name'],
                'ref_column': row['foreign_column_name'],
                'constraint': row['constraint_name'],
            }
            fks.append(fk_info)

        self._fks_cache[table] = fks
        return fks

    def is_junction_table(self, table):
        """
        Detect if table is a junction table (many-to-many relationship).
        Junction tables typically:
        - Have exactly 2 foreign keys
        - Composite primary key
        - Few or no other columns
        """
        fks = self.get_foreign_keys(table)
        if len(fks) != 2:
            return False

        columns = self.get_columns(table)
        # Should have mostly just the FK columns
        return len(columns) <= 4

    def get_uri_column(self, table):
        """Get the URI column name (usually 'uri' or 'subject')."""
        columns = self.get_columns(table)
        for col in columns:
            if col['name'] in ['uri', 'subject', 'subj']:
                return col['name']
        return None

    def get_table_row_count(self, table):
        """Get approximate row count for a table."""
        try:
            self.cursor.execute(f"""
                SELECT reltuples::bigint AS estimate
                FROM pg_class
                WHERE relname = %s
            """, (table,))
            result = self.cursor.fetchone()
            return int(result['estimate']) if result else 0
        except:
            return 0


# ============================================================
# Ontology Parsing
# ============================================================

class OntologyParser:
    """Parses OWL ontology to understand classes and properties."""

    def __init__(self, ontology_file):
        self.ontology_file = ontology_file
        self.graph = None
        self.property_ranges = {}
        self.property_domains = {}
        self.functional_properties = set()

        if HAS_RDFLIB:
            self.parse()

    def parse(self):
        """Parse the ontology file."""
        if not os.path.exists(self.ontology_file):
            print(f"Warning: Ontology file not found: {self.ontology_file}")
            return

        try:
            self.graph = Graph()
            self.graph.parse(self.ontology_file, format='turtle')

            # Extract property information
            cpmeta = Namespace(NAMESPACES['cpmeta'])

            # Get ranges for object and datatype properties
            for prop in self.graph.subjects(RDF.type, OWL.ObjectProperty):
                for obj in self.graph.objects(prop, RDFS.range):
                    self.property_ranges[str(prop)] = str(obj)
                for obj in self.graph.objects(prop, RDFS.domain):
                    self.property_domains[str(prop)] = str(obj)

            for prop in self.graph.subjects(RDF.type, OWL.DatatypeProperty):
                for obj in self.graph.objects(prop, RDFS.range):
                    self.property_ranges[str(prop)] = str(obj)
                for obj in self.graph.objects(prop, RDFS.domain):
                    self.property_domains[str(prop)] = str(obj)

            # Get functional properties
            for prop in self.graph.subjects(RDF.type, OWL.FunctionalProperty):
                self.functional_properties.add(str(prop))

            print(f"✓ Parsed ontology: {len(self.property_ranges)} properties")

        except Exception as e:
            print(f"Warning: Could not parse ontology: {e}")

    def get_property_for_column(self, column_name, table_name=None):
        """
        Map a database column name to an ontology property.
        Uses heuristics and naming conventions.
        """
        # Direct column name to property mappings
        mappings = {
            'uri': None,  # Not a property
            'type': None,  # Used for rdf:type
            'name': 'hasName',
            'label': 'rdfs:label',
            'description': 'rdfs:comment',
            'email': 'hasEmail',
            'first_name': 'hasFirstName',
            'last_name': 'hasLastName',
            'orcid_id': 'hasOrcidId',
            'station_id': 'hasStationId',
            'country_code': 'countryCode',
            'latitude': 'hasLatitude',
            'longitude': 'hasLongitude',
            'elevation': 'hasElevation',
            'sha256': 'hasSha256sum',
            'doi': 'hasDoi',
            'size_in_bytes': 'hasSizeInBytes',
            'number_of_rows': 'hasNumberOfRows',
            'actual_column_names': 'hasActualColumnNames',
            'model': 'hasModel',
            'serial_number': 'hasSerialNumber',
            'sampling_height': 'hasSamplingHeight',
            'keywords': 'hasKeywords',
            'mean_annual_temp': 'hasMeanAnnualTemp',
            'mean_annual_precip': 'hasMeanAnnualPrecip',
            'mean_annual_radiation': 'hasMeanAnnualRadiation',
            'time_zone_offset': 'hasTimeZoneOffset',
            'operational_period': 'hasOperationalPeriod',
            'is_discontinued': 'isDiscontinued',
            'station_class': 'hasStationClass',
            'labeling_date': 'hasLabelingDate',
            'wigos_id': 'hasWigosId',
            'data_level': 'hasDataLevel',
            'attribution_weight': 'hasAttributionWeight',
            'role_uri': 'hasRole',
            'role_label': None,  # Derived from role_uri
            'was_produced_by': 'prov:wasProducedBy',
        }

        # Temporal properties
        temporal_mappings = {
            'acquisition_start_time': 'hasAcquisitionStartTime',
            'acquisition_end_time': 'hasAcquisitionEndTime',
            'submission_start_time': 'hasSubmissionStartTime',
            'submission_end_time': 'hasSubmissionEndTime',
            'data_start_time': 'hasDataStartTime',
            'data_end_time': 'hasDataEndTime',
            'start_time': 'hasStartTime',
            'end_time': 'hasEndTime',
        }
        mappings.update(temporal_mappings)

        if column_name in mappings:
            prop_name = mappings[column_name]
            if prop_name:
                # Check if it's a prefixed property
                if ':' in prop_name:
                    return prop_name
                return f"cpmeta:{prop_name}"
            return None

        # Try camelCase conversion
        # e.g., some_property -> hasSomeProperty
        if '_' in column_name and not column_name.endswith('_id'):
            parts = column_name.split('_')
            camel = 'has' + ''.join(p.capitalize() for p in parts)
            return f"cpmeta:{camel}"

        return None

    def get_xsd_type(self, pg_type):
        """Get XSD type for a PostgreSQL column type."""
        # Normalize type name
        pg_type = pg_type.lower()
        return PG_TO_XSD.get(pg_type)


# ============================================================
# Mapping Generator
# ============================================================

class MappingGenerator:
    """Generates Ontop mappings from database schema and ontology."""

    def __init__(self, db, onto, strategy='grouped', with_comments=True):
        self.db = db
        self.onto = onto
        self.strategy = strategy
        self.with_comments = with_comments
        self.mappings = []
        self.stats = {
            'type_mappings': 0,
            'datatype_mappings': 0,
            'object_mappings': 0,
            'junction_mappings': 0,
            'skipped_columns': 0,
            'skipped_tables': 0,
        }

    def generate_all(self, include_tables=None, exclude_tables=None):
        """Generate all mappings."""
        tables = self.db.get_tables()

        if include_tables:
            tables = [t for t in tables if t in include_tables]
        if exclude_tables:
            tables = [t for t in tables if t not in exclude_tables]

        for table in tables:
            if self.db.is_junction_table(table):
                self.generate_junction_mapping(table)
            else:
                self.generate_table_mappings(table)

    def generate_table_mappings(self, table):
        """Generate all mappings for a table."""
        if self.with_comments:
            self.add_comment(f"\n{'='*60}")
            self.add_comment(f"TABLE: {table}")
            row_count = self.db.get_table_row_count(table)
            if row_count > 0:
                self.add_comment(f"Estimated rows: {row_count:,}")
            self.add_comment(f"{'='*60}\n")

        # Type mapping
        columns = self.db.get_columns(table)
        if any(col['name'] == 'type' for col in columns):
            self.generate_type_mapping(table)

        # Datatype properties
        self.generate_datatype_mappings(table)

        # Object properties (foreign keys)
        self.generate_object_property_mappings(table)

    def generate_type_mapping(self, table):
        """Generate rdf:type mapping using type column."""
        uri_col = self.db.get_uri_column(table)
        if not uri_col:
            return

        mapping_id = f"{table}-type"
        target = "{%s} a cpmeta:{type} ." % uri_col
        source = f"SELECT {uri_col}, type FROM {table}"

        self.add_mapping(mapping_id, target, source)
        self.stats['type_mappings'] += 1

    def generate_datatype_mappings(self, table):
        """Generate mappings for datatype properties."""
        uri_col = self.db.get_uri_column(table)
        if not uri_col:
            return

        columns = self.db.get_columns(table)
        fks = {fk['column'] for fk in self.db.get_foreign_keys(table)}

        # Group columns by required vs optional
        required_cols = []
        optional_cols = []

        for col in columns:
            col_name = col['name']

            # Skip certain columns
            if col_name in SKIP_COLUMNS or col_name == uri_col or col_name in fks:
                if col_name in SKIP_COLUMNS:
                    self.stats['skipped_columns'] += 1
                continue

            # Get property mapping
            prop = self.onto.get_property_for_column(col_name, table)
            if not prop:
                continue

            # Get XSD type
            xsd_type = self.onto.get_xsd_type(col['type'])

            col_info = {
                'name': col_name,
                'property': prop,
                'xsd_type': xsd_type,
            }

            if col['nullable']:
                optional_cols.append(col_info)
            else:
                required_cols.append(col_info)

        # Generate mapping for required columns
        if required_cols:
            self.generate_column_group_mapping(
                table, uri_col, required_cols, 'basic', required=True
            )

        # Generate mapping for optional columns
        if optional_cols:
            self.generate_column_group_mapping(
                table, uri_col, optional_cols, 'optional', required=False
            )

    def generate_column_group_mapping(self, table, uri_col, columns, suffix, required=True):
        """Generate a mapping for a group of columns."""
        if not columns:
            return

        mapping_id = f"{table}-{suffix}"

        # Build target triples
        target_parts = []
        for col in columns:
            prop = col['property']
            col_name = col['name']
            xsd_type = col['xsd_type']

            if xsd_type:
                triple = f"{prop} {{{col_name}}}^^{xsd_type}"
            else:
                triple = f"{prop} {{{col_name}}}"
            target_parts.append(triple)

        target = "{%s} %s ." % (uri_col, " ;\n                ".join(target_parts))

        # Build source query
        col_names = [uri_col] + [col['name'] for col in columns]
        source_parts = [f"SELECT {', '.join(col_names)}"]
        source_parts.append(f"FROM {table}")

        if not required:
            # Add WHERE clause for at least one non-null optional column
            where_parts = [f"{col['name']} IS NOT NULL" for col in columns]
            source_parts.append(f"WHERE {' OR '.join(where_parts)}")

        source = "\n             ".join(source_parts)

        self.add_mapping(mapping_id, target, source)
        self.stats['datatype_mappings'] += 1

    def generate_object_property_mappings(self, table):
        """Generate mappings for object properties (foreign keys)."""
        uri_col = self.db.get_uri_column(table)
        if not uri_col:
            return

        fks = self.db.get_foreign_keys(table)

        for fk in fks:
            # Infer property name from column name
            col_name = fk['column']
            ref_table = fk['ref_table']

            # Skip internal FKs
            if col_name in SKIP_COLUMNS:
                continue

            # Map column to property
            # e.g., object_spec_id -> hasObjectSpec
            # e.g., responsible_organization_id -> hasResponsibleOrganization
            if col_name.endswith('_id'):
                prop_base = col_name[:-3]  # Remove _id
                # Convert snake_case to CamelCase
                parts = prop_base.split('_')
                camel = ''.join(p.capitalize() for p in parts)
                prop = f"cpmeta:has{camel}"
            else:
                prop = self.onto.get_property_for_column(col_name, table)
                if not prop:
                    continue

            # Get referenced table's URI column
            ref_uri_col = self.db.get_uri_column(ref_table)
            if not ref_uri_col:
                continue

            prop_base_name = prop_base if col_name.endswith('_id') else col_name
            mapping_id = f"{table}-{prop_base_name}"
            target = "{%s_uri} %s {%s_uri} ." % (table, prop, ref_table)

            source = f"""SELECT
                 s.{uri_col} as {table}_uri,
                 r.{ref_uri_col} as {ref_table}_uri
             FROM {table} s
             JOIN {ref_table} r ON s.{col_name} = r.id"""

            self.add_mapping(mapping_id, target, source)
            self.stats['object_mappings'] += 1

    def generate_junction_mapping(self, table):
        """Generate mapping for junction table (many-to-many relationship)."""
        if self.with_comments:
            self.add_comment(f"\n# Junction table: {table}")

        fks = self.db.get_foreign_keys(table)
        if len(fks) != 2:
            return

        # Determine which FK is subject and which is object
        fk1, fk2 = fks

        # Get URI columns for referenced tables
        table1 = fk1['ref_table']
        table2 = fk2['ref_table']

        uri_col1 = self.db.get_uri_column(table1)
        uri_col2 = self.db.get_uri_column(table2)

        if not uri_col1 or not uri_col2:
            return

        # Infer property name from table name or second FK
        # e.g., data_object_keywords -> hasKeyword
        # Pattern: {entity1}_{entity2}s
        prop = None

        if table2 == 'keywords':
            # Special case: keywords table has keyword column
            mapping_id = f"{table}-keywords"
            prop = "cpmeta:hasKeyword"
            target = "{subj_uri} %s {keyword} ." % prop
            source = f"""SELECT
                 s.{uri_col1} as subj_uri,
                 k.keyword
             FROM {table} jt
             JOIN {table1} s ON jt.{fk1['column']} = s.id
             JOIN keywords k ON jt.{fk2['column']} = k.id"""
        else:
            # General case
            prop_name = table2.rstrip('s')  # Remove trailing 's'
            parts = prop_name.split('_')
            camel = ''.join(p.capitalize() for p in parts)
            prop = f"cpmeta:has{camel}"

            mapping_id = f"{table}"
            target = "{subj_uri} %s {obj_uri} ." % prop
            source = f"""SELECT
                 s.{uri_col1} as subj_uri,
                 o.{uri_col2} as obj_uri
             FROM {table} jt
             JOIN {table1} s ON jt.{fk1['column']} = s.id
             JOIN {table2} o ON jt.{fk2['column']} = o.id"""

        self.add_mapping(mapping_id, target, source)
        self.stats['junction_mappings'] += 1

    def add_mapping(self, mapping_id, target, source):
        """Add a mapping to the list."""
        self.mappings.append({
            'id': mapping_id,
            'target': target,
            'source': source,
        })

    def add_comment(self, comment):
        """Add a comment to the mappings."""
        self.mappings.append({
            'comment': comment
        })

    def write_obda_file(self, output_file):
        """Write mappings to .obda file."""
        with open(output_file, 'w') as f:
            # Write prefix declarations
            f.write("[PrefixDeclaration]\n")
            for prefix, uri in sorted(NAMESPACES.items()):
                f.write(f"{prefix}: {uri}\n")
            f.write("\n")

            # Write mapping declarations
            f.write("[MappingDeclaration] @collection [[\n\n")

            # Write header comment
            f.write(f"# Auto-generated Ontop mappings\n")
            f.write(f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"# Total mappings: {len([m for m in self.mappings if 'id' in m])}\n")
            f.write("\n")

            # Write mappings
            for mapping in self.mappings:
                if 'comment' in mapping:
                    f.write(mapping['comment'] + "\n")
                elif 'id' in mapping:
                    f.write(f"mappingId\t{mapping['id']}\n")
                    f.write(f"target\t\t{mapping['target']}\n")
                    f.write(f"source\t\t{mapping['source']}\n")
                    f.write("\n")

            f.write("]]\n")

        print(f"\n✓ Wrote {len([m for m in self.mappings if 'id' in m])} mappings to: {output_file}")


# ============================================================
# Main
# ============================================================

def get_connection(host='localhost', port=5432, user='postgres', dbname='postgres', password='ontop'):
    """Create database connection."""
    try:
        return psycopg2.connect(
            host=host,
            port=port,
            user=user,
            dbname=dbname,
            password=password
        )
    except psycopg2.Error as e:
        print(f"Error connecting to database: {e}")
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="Generate Ontop mappings from class-based database schema",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s
  %(prog)s --output ontop/auto_mappings.obda
  %(prog)s --include-tables data_objects,stations
  %(prog)s --strategy grouped --with-comments
        """
    )

    # Database connection
    parser.add_argument('--host', default='localhost', help='Database host')
    parser.add_argument('--port', type=int, default=5432, help='Database port')
    parser.add_argument('--user', default='postgres', help='Database user')
    parser.add_argument('--dbname', default='postgres', help='Database name')
    parser.add_argument('--password', default='ontop', help='Database password')

    # Generation options
    parser.add_argument('--ontology', default='ontop/cpmeta.ttl',
                        help='Ontology file (default: ontop/cpmeta.ttl)')
    parser.add_argument('--output', default='ontop/generated_mappings.obda',
                        help='Output file (default: ontop/generated_mappings.obda)')
    parser.add_argument('--include-tables', help='Comma-separated list of tables to include')
    parser.add_argument('--exclude-tables', help='Comma-separated list of tables to exclude')
    parser.add_argument('--strategy', choices=['grouped', 'split'], default='grouped',
                        help='Mapping strategy (default: grouped)')
    parser.add_argument('--with-comments', action='store_true', default=True,
                        help='Include explanatory comments (default: True)')
    parser.add_argument('--dry-run', action='store_true',
                        help='Print statistics without writing file')

    args = parser.parse_args()

    # Parse table lists
    include_tables = args.include_tables.split(',') if args.include_tables else None
    exclude_tables = args.exclude_tables.split(',') if args.exclude_tables else None

    print("=" * 60)
    print("ONTOP MAPPING GENERATOR")
    print("=" * 60)

    # Connect to database
    print(f"\nConnecting to database at {args.host}:{args.port}...")
    conn = get_connection(args.host, args.port, args.user, args.dbname, args.password)

    try:
        # Initialize components
        print("Introspecting database schema...")
        db = DatabaseIntrospector(conn)

        print(f"Parsing ontology: {args.ontology}...")
        onto = OntologyParser(args.ontology)

        # Generate mappings
        print("\nGenerating mappings...")
        generator = MappingGenerator(db, onto, args.strategy, args.with_comments)
        generator.generate_all(include_tables, exclude_tables)

        # Print statistics
        print("\n" + "=" * 60)
        print("GENERATION REPORT")
        print("=" * 60)
        print(f"Type mappings:          {generator.stats['type_mappings']}")
        print(f"Datatype mappings:      {generator.stats['datatype_mappings']}")
        print(f"Object mappings:        {generator.stats['object_mappings']}")
        print(f"Junction mappings:      {generator.stats['junction_mappings']}")
        print(f"Skipped columns:        {generator.stats['skipped_columns']}")
        total = generator.stats['type_mappings'] + generator.stats['datatype_mappings'] + \
                generator.stats['object_mappings'] + generator.stats['junction_mappings']
        print(f"Total mappings:         {total}")

        # Write output
        if not args.dry_run:
            # Backup existing file
            if os.path.exists(args.output):
                backup = f"{args.output}.backup"
                os.rename(args.output, backup)
                print(f"\nBacked up existing file to: {backup}")

            generator.write_obda_file(args.output)
            file_size = os.path.getsize(args.output) / 1024
            print(f"File size: {file_size:.1f} KB")
        else:
            print("\n(Dry run - no file written)")

        print("\n✓ Mapping generation completed successfully")

    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
