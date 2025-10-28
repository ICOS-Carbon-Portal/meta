#!/usr/bin/env python3
"""
Script to create and populate class-based tables from rdf_triples.
Uses a normalized relational schema with one table per major OWL class.

This approach is significantly faster for SPARQL queries via Ontop compared
to the predicate-table approach, as it minimizes JOINs and enables better
query optimization.
"""

import psycopg2
import psycopg2.extras
import argparse
import os
import sys
import json
from collections import defaultdict

# Configuration for which table to read from
TRIPLES_TABLE = os.environ.get('TRIPLES_TABLE', 'rdf_triples')

# Namespace definitions
NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'xsd': 'http://www.w3.org/2001/XMLSchema#',
    'cpmeta': 'http://meta.icos-cp.eu/ontologies/cpmeta/',
    'prov': 'http://www.w3.org/ns/prov#',
}

# Type URIs for main classes
TYPE_URIS = {
    'Station': f"{NS['cpmeta']}Station",
    'IcosStation': f"{NS['cpmeta']}IcosStation",
    'ES': f"{NS['cpmeta']}ES",
    'AS': f"{NS['cpmeta']}AS",
    'OS': f"{NS['cpmeta']}OS",
    'Organization': f"{NS['cpmeta']}Organization",
    'Person': f"{NS['cpmeta']}Person",
    'DataObject': f"{NS['cpmeta']}DataObject",
    'SimpleDataObject': f"{NS['cpmeta']}SimpleDataObject",
    'SpatialDataObject': f"{NS['cpmeta']}SpatialDataObject",
    'DataObjectSpec': f"{NS['cpmeta']}DataObjectSpec",
    'SimpleObjectSpec': f"{NS['cpmeta']}SimpleObjectSpec",
    'Project': f"{NS['cpmeta']}Project",
    'DataAcquisition': f"{NS['cpmeta']}DataAcquisition",
    'Instrument': f"{NS['cpmeta']}Instrument",
    'Membership': f"{NS['cpmeta']}Membership",
}


def get_connection(host='localhost', port=5432, user='postgres', dbname='postgres', password='ontop'):
    """Create and return a PostgreSQL database connection."""
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


def check_triples_table(cursor):
    """Check which triples table exists and return its name."""
    global TRIPLES_TABLE

    # Try both possible table names
    for table_name in ['rdf_triples', 'triples']:
        cursor.execute("""
            SELECT EXISTS (
                SELECT FROM information_schema.tables
                WHERE table_name = %s
            );
        """, (table_name,))

        if cursor.fetchone()[0]:
            TRIPLES_TABLE = table_name
            print(f"Using triples table: {TRIPLES_TABLE}")
            return TRIPLES_TABLE

    print("Error: No triples table found (tried 'rdf_triples' and 'triples')")
    sys.exit(1)


def drop_class_tables(cursor):
    """Drop all class-based tables in proper order (respecting foreign keys)."""
    print("Dropping existing class-based tables...")

    tables_to_drop = [
        # Junction tables first (they reference other tables)
        'station_ecosystem_types',
        'station_responsible_organizations',
        'data_object_keywords',
        'data_object_acquisitions',
        'memberships',
        'object_spec_columns',

        # Main tables
        'dataset_columns',
        'data_acquisitions',
        'data_objects',
        'object_specs',
        'instruments',
        'persons',
        'stations',
        'organizations',

        # Lookup tables
        'data_themes',
        'object_formats',
        'object_encodings',
        'climate_zones',
        'projects',
        'keywords',
        'specific_dataset_types',
    ]

    dropped_count = 0
    for table in tables_to_drop:
        try:
            cursor.execute(f"DROP TABLE IF EXISTS {table} CASCADE;")
            dropped_count += 1
        except psycopg2.Error as e:
            print(f"Warning: Could not drop table {table}: {e}")

    print(f"✓ Dropped {dropped_count} tables")


def create_schema(cursor):
    """Create all class-based tables with proper structure and indexes."""
    print("\nCreating class-based table schema...")

    # Lookup/vocabulary tables
    print("  Creating lookup tables...")

    cursor.execute("""
        CREATE TABLE keywords (
            id SERIAL PRIMARY KEY,
            keyword TEXT UNIQUE NOT NULL
        );
        CREATE INDEX idx_keywords_keyword ON keywords(keyword);
    """)

    cursor.execute("""
        CREATE TABLE data_themes (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            label TEXT,
            icon TEXT,
            marker_icon TEXT
        );
        CREATE INDEX idx_data_themes_uri ON data_themes(uri);
    """)

    cursor.execute("""
        CREATE TABLE object_formats (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            label TEXT
        );
        CREATE INDEX idx_object_formats_uri ON object_formats(uri);
    """)

    cursor.execute("""
        CREATE TABLE object_encodings (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            label TEXT
        );
        CREATE INDEX idx_object_encodings_uri ON object_encodings(uri);
    """)

    cursor.execute("""
        CREATE TABLE climate_zones (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            label TEXT
        );
        CREATE INDEX idx_climate_zones_uri ON climate_zones(uri);
    """)

    cursor.execute("""
        CREATE TABLE specific_dataset_types (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            label TEXT
        );
        CREATE INDEX idx_specific_dataset_types_uri ON specific_dataset_types(uri);
    """)

    # Organizations table
    print("  Creating organizations table...")
    cursor.execute("""
        CREATE TABLE organizations (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            type TEXT NOT NULL,
            name TEXT,
            email TEXT,
            country_code TEXT,
            location_latitude DOUBLE PRECISION,
            location_longitude DOUBLE PRECISION,
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_organizations_uri ON organizations(uri);
        CREATE INDEX idx_organizations_type ON organizations(type);
        CREATE INDEX idx_organizations_name ON organizations(name);
    """)

    # Persons table
    print("  Creating persons table...")
    cursor.execute("""
        CREATE TABLE persons (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            first_name TEXT,
            last_name TEXT,
            email TEXT,
            orcid_id TEXT,
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_persons_uri ON persons(uri);
        CREATE INDEX idx_persons_orcid ON persons(orcid_id);
        CREATE INDEX idx_persons_name ON persons(last_name, first_name);
    """)

    # Stations table
    print("  Creating stations table...")
    cursor.execute("""
        CREATE TABLE stations (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            type TEXT NOT NULL,
            name TEXT,
            station_id TEXT,
            country_code TEXT,
            latitude DOUBLE PRECISION,
            longitude DOUBLE PRECISION,
            elevation FLOAT,
            mean_annual_temp FLOAT,
            mean_annual_precip FLOAT,
            mean_annual_radiation FLOAT,
            time_zone_offset INTEGER,
            operational_period TEXT,
            is_discontinued BOOLEAN DEFAULT FALSE,
            station_class TEXT,
            labeling_date DATE,
            wigos_id TEXT,
            responsible_organization_id INTEGER REFERENCES organizations(id),
            climate_zone_id INTEGER REFERENCES climate_zones(id),
            created_at TIMESTAMP DEFAULT NOW(),
            updated_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_stations_uri ON stations(uri);
        CREATE INDEX idx_stations_type ON stations(type);
        CREATE INDEX idx_stations_station_id ON stations(station_id);
        CREATE INDEX idx_stations_country ON stations(country_code);
        CREATE INDEX idx_stations_lat_lon ON stations(latitude, longitude);
        CREATE INDEX idx_stations_resp_org ON stations(responsible_organization_id);
    """)

    # Projects table
    print("  Creating projects table...")
    cursor.execute("""
        CREATE TABLE projects (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            name TEXT,
            keywords TEXT,
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_projects_uri ON projects(uri);
        CREATE INDEX idx_projects_name ON projects(name);
    """)

    # Object specs table
    print("  Creating object_specs table...")
    cursor.execute("""
        CREATE TABLE object_specs (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            type TEXT NOT NULL,
            label TEXT,
            description TEXT,
            data_level INTEGER,
            theme_id INTEGER REFERENCES data_themes(id),
            format_id INTEGER REFERENCES object_formats(id),
            encoding_id INTEGER REFERENCES object_encodings(id),
            project_id INTEGER REFERENCES projects(id),
            dataset_type_id INTEGER REFERENCES specific_dataset_types(id),
            dataset_spec_uri TEXT,
            documentation_object_uri TEXT,
            keywords TEXT,
            see_also TEXT,
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_object_specs_uri ON object_specs(uri);
        CREATE INDEX idx_object_specs_theme ON object_specs(theme_id);
        CREATE INDEX idx_object_specs_format ON object_specs(format_id);
        CREATE INDEX idx_object_specs_data_level ON object_specs(data_level);
    """)

    # Data objects table
    print("  Creating data_objects table...")
    cursor.execute("""
        CREATE TABLE data_objects (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            sha256 TEXT NOT NULL,
            type TEXT NOT NULL,
            name TEXT,
            doi TEXT,
            acquisition_start_time TIMESTAMP WITH TIME ZONE,
            acquisition_end_time TIMESTAMP WITH TIME ZONE,
            submission_start_time TIMESTAMP WITH TIME ZONE,
            submission_end_time TIMESTAMP WITH TIME ZONE,
            data_start_time TIMESTAMP WITH TIME ZONE,
            data_end_time TIMESTAMP WITH TIME ZONE,
            size_in_bytes BIGINT,
            number_of_rows BIGINT,
            actual_column_names JSONB,
            object_spec_id INTEGER REFERENCES object_specs(id),
            was_produced_by TEXT,
            was_submitted_by TEXT,
            actual_variable_uri TEXT,
            temporal_resolution TEXT,
            created_at TIMESTAMP DEFAULT NOW(),
            updated_at TIMESTAMP DEFAULT NOW()
        );
    """)
        # CREATE INDEX idx_data_objects_uri ON data_objects(uri);
        # CREATE INDEX idx_data_objects_sha256 ON data_objects(sha256);
        # CREATE INDEX idx_data_objects_spec ON data_objects(object_spec_id);
        # CREATE INDEX idx_data_objects_theme ON data_objects(theme_uri);
        # CREATE INDEX idx_data_objects_acq_times ON data_objects(acquisition_start_time, acquisition_end_time);
        # CREATE INDEX idx_data_objects_data_times ON data_objects(data_start_time, data_end_time);
        # CREATE INDEX idx_data_objects_size ON data_objects(size_in_bytes);
        # CREATE INDEX idx_data_objects_column_names_gin ON data_objects USING GIN (actual_column_names);

    # Instruments table
    print("  Creating instruments table...")
    cursor.execute("""
        CREATE TABLE instruments (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            name TEXT,
            model TEXT,
            serial_number TEXT,
            vendor_id INTEGER REFERENCES organizations(id),
            owner_id INTEGER REFERENCES organizations(id),
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_instruments_uri ON instruments(uri);
        CREATE INDEX idx_instruments_model ON instruments(model);
        CREATE INDEX idx_instruments_serial ON instruments(serial_number);
    """)

    # Data acquisitions table
    print("  Creating data_acquisitions table...")
    cursor.execute("""
        CREATE TABLE data_acquisitions (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            station_id INTEGER REFERENCES stations(id),
            instrument_id INTEGER REFERENCES instruments(id),
            sampling_height FLOAT,
            sampling_point_uri TEXT,
            was_performed_at TEXT,
            started_at_time TIMESTAMP WITH TIME ZONE,
            ended_at_time TIMESTAMP WITH TIME ZONE,
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_acquisitions_uri ON data_acquisitions(uri);
        CREATE INDEX idx_acquisitions_station ON data_acquisitions(station_id);
        CREATE INDEX idx_acquisitions_instrument ON data_acquisitions(instrument_id);
    """)

    # Dataset columns table
    print("  Creating dataset_columns table...")
    cursor.execute("""
        CREATE TABLE dataset_columns (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            label TEXT,
            description TEXT,
            column_title TEXT,
            value_type_uri TEXT,
            value_format_uri TEXT,
            is_optional_column BOOLEAN,
            is_regex_column BOOLEAN,
            is_quality_flag_for_uri TEXT,
            see_also TEXT,
            created_at TIMESTAMP DEFAULT NOW()
        );
        CREATE INDEX idx_dataset_columns_uri ON dataset_columns(uri);
        CREATE INDEX idx_dataset_columns_title ON dataset_columns(column_title);
    """)

    # Junction tables
    print("  Creating junction tables...")

    cursor.execute("""
        CREATE TABLE station_ecosystem_types (
            station_id INTEGER REFERENCES stations(id),
            ecosystem_type_uri TEXT NOT NULL,
            PRIMARY KEY (station_id, ecosystem_type_uri)
        );
        CREATE INDEX idx_station_ecosystem_station ON station_ecosystem_types(station_id);
    """)

    cursor.execute("""
        CREATE TABLE station_responsible_organizations (
            station_id INTEGER REFERENCES stations(id),
            organization_id INTEGER REFERENCES organizations(id),
            PRIMARY KEY (station_id, organization_id)
        );
        CREATE INDEX idx_station_resp_org_station ON station_responsible_organizations(station_id);
        CREATE INDEX idx_station_resp_org_org ON station_responsible_organizations(organization_id);
    """)

    cursor.execute("""
        CREATE TABLE data_object_keywords (
            data_object_id INTEGER REFERENCES data_objects(id),
            keyword_id INTEGER REFERENCES keywords(id),
            PRIMARY KEY (data_object_id, keyword_id)
        );
        CREATE INDEX idx_data_object_keywords_obj ON data_object_keywords(data_object_id);
        CREATE INDEX idx_data_object_keywords_kw ON data_object_keywords(keyword_id);
    """)

    cursor.execute("""
        CREATE TABLE data_object_acquisitions (
            data_object_id INTEGER REFERENCES data_objects(id),
            acquisition_id INTEGER REFERENCES data_acquisitions(id),
            PRIMARY KEY (data_object_id, acquisition_id)
        );
        CREATE INDEX idx_data_object_acq_obj ON data_object_acquisitions(data_object_id);
        CREATE INDEX idx_data_object_acq_acq ON data_object_acquisitions(acquisition_id);
    """)

    cursor.execute("""
        CREATE TABLE object_spec_columns (
            object_spec_id INTEGER REFERENCES object_specs(id),
            column_id INTEGER REFERENCES dataset_columns(id),
            PRIMARY KEY (object_spec_id, column_id)
        );
        CREATE INDEX idx_object_spec_columns_spec ON object_spec_columns(object_spec_id);
        CREATE INDEX idx_object_spec_columns_col ON object_spec_columns(column_id);
    """)

    cursor.execute("""
        CREATE TABLE memberships (
            id SERIAL PRIMARY KEY,
            uri TEXT UNIQUE NOT NULL,
            person_id INTEGER NOT NULL REFERENCES persons(id),
            organization_id INTEGER NOT NULL REFERENCES organizations(id),
            role_uri TEXT NOT NULL,
            start_time TIMESTAMP WITH TIME ZONE,
            end_time TIMESTAMP WITH TIME ZONE,
            attribution_weight INTEGER
        );
        CREATE INDEX idx_memberships_uri ON memberships(uri);
        CREATE INDEX idx_memberships_person ON memberships(person_id);
        CREATE INDEX idx_memberships_org ON memberships(organization_id);
    """)

    print("✓ Schema created successfully")


def get_entities_by_type(cursor, type_uri, limit=None):
    """
    Fetch all entities of a specific rdf:type from the triples table.

    Args:
        cursor: Database cursor
        type_uri: RDF type URI to filter by
        limit: Optional limit on number of entities

    Returns:
        List of (subject_uri, properties_dict) tuples
    """
    rdf_type_pred = f"{NS['rdf']}type"

    if limit:
        cursor.execute(f"""
            SELECT subj, obj
            FROM {TRIPLES_TABLE}
            WHERE pred = %s AND obj = %s
            LIMIT %s
        """, (rdf_type_pred, type_uri, limit))
    else:
        cursor.execute(f"""
            SELECT subj, obj
            FROM {TRIPLES_TABLE}
            WHERE pred = %s AND obj = %s
        """, (rdf_type_pred, type_uri))

    subjects = [row[0] for row in cursor.fetchall()]

    # Now fetch all properties for these subjects
    if not subjects:
        return []

    # Use a dictionary to accumulate properties
    entity_props = {subj: {} for subj in subjects}

    # Fetch all triples for these subjects
    cursor.execute(f"""
        SELECT subj, pred, obj
        FROM {TRIPLES_TABLE}
        WHERE subj = ANY(%s)
    """, (subjects,))

    for subj, pred, obj in cursor:
        # Handle multi-valued properties
        if pred in entity_props[subj]:
            # Convert to list if not already
            if not isinstance(entity_props[subj][pred], list):
                entity_props[subj][pred] = [entity_props[subj][pred]]
            entity_props[subj][pred].append(obj)
        else:
            entity_props[subj][pred] = obj

    return [(subj, props) for subj, props in entity_props.items()]


def get_property_value(props, *predicates, default=None):
    """
    Get a single property value, trying multiple predicate URIs in order.
    Returns the first found value or default.
    """
    for pred in predicates:
        if pred in props:
            val = props[pred]
            # If it's a list, return the first element
            if isinstance(val, list):
                return val[0] if val else default
            return val
    return default


def get_property_values(props, predicate):
    """
    Get all values for a multi-valued property.
    Always returns a list.
    """
    if predicate not in props:
        return []

    val = props[predicate]
    if isinstance(val, list):
        return val
    return [val]


def populate_keywords(cursor, limit=None):
    """Populate the keywords table from all hasKeyword predicates."""
    print("\nPopulating keywords table...")

    keyword_pred = f"{NS['cpmeta']}hasKeyword"

    if limit:
        cursor.execute(f"""
            INSERT INTO keywords (keyword)
            SELECT DISTINCT obj
            FROM {TRIPLES_TABLE}
            WHERE pred = %s
            LIMIT %s
            ON CONFLICT (keyword) DO NOTHING
        """, (keyword_pred, limit))
    else:
        cursor.execute(f"""
            INSERT INTO keywords (keyword)
            SELECT DISTINCT obj
            FROM {TRIPLES_TABLE}
            WHERE pred = %s
            ON CONFLICT (keyword) DO NOTHING
        """, (keyword_pred,))

    count = cursor.rowcount
    print(f"  Inserted {count} keywords")
    return count


def populate_data_themes(cursor, limit=None):
    """Populate data_themes lookup table."""
    print("\nPopulating data_themes table...")

    entities = get_entities_by_type(cursor, f"{NS['cpmeta']}DataTheme", limit)

    if not entities:
        print("  No DataTheme entities found")
        return 0

    for uri, props in entities:
        label = get_property_value(props, f"{NS['rdfs']}label")
        icon = get_property_value(props, f"{NS['cpmeta']}hasIcon")
        marker_icon = get_property_value(props, f"{NS['cpmeta']}hasMarkerIcon")

        cursor.execute("""
            INSERT INTO data_themes (uri, label, icon, marker_icon)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET label = EXCLUDED.label,
                icon = EXCLUDED.icon,
                marker_icon = EXCLUDED.marker_icon
        """, (uri, label, icon, marker_icon))

    print(f"  Inserted {len(entities)} data themes")
    return len(entities)


def populate_object_formats(cursor, limit=None):
    """Populate object_formats lookup table."""
    print("\nPopulating object_formats table...")

    entities = get_entities_by_type(cursor, f"{NS['cpmeta']}ObjectFormat", limit)

    if not entities:
        print("  No ObjectFormat entities found")
        return 0

    for uri, props in entities:
        label = get_property_value(props, f"{NS['rdfs']}label")

        cursor.execute("""
            INSERT INTO object_formats (uri, label)
            VALUES (%s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET label = EXCLUDED.label
        """, (uri, label))

    print(f"  Inserted {len(entities)} object formats")
    return len(entities)


def populate_climate_zones(cursor, limit=None):
    """Populate climate_zones lookup table."""
    print("\nPopulating climate_zones table...")

    entities = get_entities_by_type(cursor, f"{NS['cpmeta']}ClimateZone", limit)

    if not entities:
        print("  No ClimateZone entities found")
        return 0

    for uri, props in entities:
        label = get_property_value(props, f"{NS['rdfs']}label")

        cursor.execute("""
            INSERT INTO climate_zones (uri, label)
            VALUES (%s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET label = EXCLUDED.label
        """, (uri, label))

    print(f"  Inserted {len(entities)} climate zones")
    return len(entities)


def populate_organizations(cursor, limit=None):
    """Populate organizations table."""
    print("\nPopulating organizations table...")

    # Get all Organization entities (including subclasses)
    entities = []
    for type_name in ['Organization', 'CentralFacility', 'ThematicCenter', 'Funder']:
        type_uri = TYPE_URIS.get(type_name, f"{NS['cpmeta']}{type_name}")
        entities.extend(get_entities_by_type(cursor, type_uri, limit))

    if not entities:
        print("  No Organization entities found")
        return 0

    for uri, props in entities:
        # Determine the most specific type
        types = get_property_values(props, f"{NS['rdf']}type")
        type_name = 'Organization'
        for t in types:
            if 'ThematicCenter' in t:
                type_name = 'ThematicCenter'
                break
            elif 'CentralFacility' in t:
                type_name = 'CentralFacility'
                break
            elif 'Funder' in t:
                type_name = 'Funder'
                break

        name = get_property_value(props, f"{NS['cpmeta']}hasName")
        email = get_property_value(props, f"{NS['cpmeta']}hasEmail")
        country_code = get_property_value(props, f"{NS['cpmeta']}countryCode")

        cursor.execute("""
            INSERT INTO organizations (uri, type, name, email, country_code)
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET type = EXCLUDED.type,
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                country_code = EXCLUDED.country_code
            RETURNING id
        """, (uri, type_name, name, email, country_code))

    print(f"  Inserted {len(entities)} organizations")
    return len(entities)


def populate_persons(cursor, limit=None):
    """Populate persons table."""
    print("\nPopulating persons table...")

    entities = get_entities_by_type(cursor, TYPE_URIS['Person'], limit)

    if not entities:
        print("  No Person entities found")
        return 0

    for uri, props in entities:
        first_name = get_property_value(props, f"{NS['cpmeta']}hasFirstName")
        last_name = get_property_value(props, f"{NS['cpmeta']}hasLastName")
        email = get_property_value(props, f"{NS['cpmeta']}hasEmail")
        orcid_id = get_property_value(props, f"{NS['cpmeta']}hasOrcidId")

        cursor.execute("""
            INSERT INTO persons (uri, first_name, last_name, email, orcid_id)
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                email = EXCLUDED.email,
                orcid_id = EXCLUDED.orcid_id
            RETURNING id
        """, (uri, first_name, last_name, email, orcid_id))

    print(f"  Inserted {len(entities)} persons")
    return len(entities)


def populate_instruments(cursor, limit=None):
    """Populate instruments table."""
    print("\nPopulating instruments table...")

    entities = get_entities_by_type(cursor, TYPE_URIS['Instrument'], limit)

    if not entities:
        print("  No Instrument entities found")
        return 0

    for uri, props in entities:
        name = get_property_value(props, f"{NS['rdfs']}label")
        model = get_property_value(props, f"{NS['cpmeta']}hasModel")
        serial_number = get_property_value(props, f"{NS['cpmeta']}hasSerialNumber")

        # Get vendor organization URI
        vendor_uri = get_property_value(props, f"{NS['cpmeta']}hasVendor")
        vendor_id = None
        if vendor_uri:
            cursor.execute("SELECT id FROM organizations WHERE uri = %s", (vendor_uri,))
            result = cursor.fetchone()
            if result:
                vendor_id = result[0]

        # Get owner organization URI
        owner_uri = get_property_value(props, f"{NS['cpmeta']}hasInstrumentOwner")
        owner_id = None
        if owner_uri:
            cursor.execute("SELECT id FROM organizations WHERE uri = %s", (owner_uri,))
            result = cursor.fetchone()
            if result:
                owner_id = result[0]

        cursor.execute("""
            INSERT INTO instruments (uri, name, model, serial_number, vendor_id, owner_id)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET name = EXCLUDED.name,
                model = EXCLUDED.model,
                serial_number = EXCLUDED.serial_number,
                vendor_id = EXCLUDED.vendor_id,
                owner_id = EXCLUDED.owner_id
        """, (uri, name, model, serial_number, vendor_id, owner_id))

    print(f"  Inserted {len(entities)} instruments")
    return len(entities)


def populate_stations(cursor, limit=None):
    """Populate stations table."""
    print("\nPopulating stations table...")

    # Get all Station entities (including subclasses)
    entities = []
    for type_name in ['Station', 'IcosStation', 'ES', 'AS', 'OS']:
        type_uri = TYPE_URIS.get(type_name, f"{NS['cpmeta']}{type_name}")
        entities.extend(get_entities_by_type(cursor, type_uri, limit))

    if not entities:
        print("  No Station entities found")
        return 0

    for uri, props in entities:
        # Determine the most specific type
        types = get_property_values(props, f"{NS['rdf']}type")
        type_name = 'Station'
        for t in types:
            if 'ES' in t:
                type_name = 'ES'
                break
            elif 'AS' in t:
                type_name = 'AS'
                break
            elif 'OS' in t:
                type_name = 'OS'
                break
            elif 'IcosStation' in t:
                type_name = 'IcosStation'
                break

        name = get_property_value(props, f"{NS['cpmeta']}hasName")
        station_id = get_property_value(props, f"{NS['cpmeta']}hasStationId")
        country_code = get_property_value(props, f"{NS['cpmeta']}countryCode")
        latitude = get_property_value(props, f"{NS['cpmeta']}hasLatitude")
        longitude = get_property_value(props, f"{NS['cpmeta']}hasLongitude")
        elevation = get_property_value(props, f"{NS['cpmeta']}hasElevation")
        mean_annual_temp = get_property_value(props, f"{NS['cpmeta']}hasMeanAnnualTemp")
        mean_annual_precip = get_property_value(props, f"{NS['cpmeta']}hasMeanAnnualPrecip")
        mean_annual_radiation = get_property_value(props, f"{NS['cpmeta']}hasMeanAnnualRadiation")
        time_zone_offset = get_property_value(props, f"{NS['cpmeta']}hasTimeZoneOffset")
        operational_period = get_property_value(props, f"{NS['cpmeta']}hasOperationalPeriod")
        is_discontinued = get_property_value(props, f"{NS['cpmeta']}isDiscontinued")
        station_class = get_property_value(props, f"{NS['cpmeta']}hasStationClass")
        labeling_date = get_property_value(props, f"{NS['cpmeta']}hasLabelingDate")
        wigos_id = get_property_value(props, f"{NS['cpmeta']}hasWigosId")

        # Get responsible organization URI
        resp_org_uri = get_property_value(props, f"{NS['cpmeta']}hasResponsibleOrganization")
        resp_org_id = None
        if resp_org_uri:
            cursor.execute("SELECT id FROM organizations WHERE uri = %s", (resp_org_uri,))
            result = cursor.fetchone()
            if result:
                resp_org_id = result[0]

        # Get climate zone URI
        climate_zone_uri = get_property_value(props, f"{NS['cpmeta']}hasClimateZone")
        climate_zone_id = None
        if climate_zone_uri:
            cursor.execute("SELECT id FROM climate_zones WHERE uri = %s", (climate_zone_uri,))
            result = cursor.fetchone()
            if result:
                climate_zone_id = result[0]

        cursor.execute("""
            INSERT INTO stations (
                uri, type, name, station_id, country_code,
                latitude, longitude, elevation,
                mean_annual_temp, mean_annual_precip, mean_annual_radiation,
                time_zone_offset, operational_period, is_discontinued,
                station_class, labeling_date, wigos_id,
                responsible_organization_id, climate_zone_id
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET type = EXCLUDED.type,
                name = EXCLUDED.name,
                station_id = EXCLUDED.station_id,
                country_code = EXCLUDED.country_code,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                elevation = EXCLUDED.elevation,
                responsible_organization_id = EXCLUDED.responsible_organization_id,
                climate_zone_id = EXCLUDED.climate_zone_id,
                updated_at = NOW()
            RETURNING id
        """, (
            uri, type_name, name, station_id, country_code,
            latitude, longitude, elevation,
            mean_annual_temp, mean_annual_precip, mean_annual_radiation,
            time_zone_offset, operational_period, is_discontinued,
            station_class, labeling_date, wigos_id,
            resp_org_id, climate_zone_id
        ))

    print(f"  Inserted {len(entities)} stations")
    return len(entities)


def populate_projects(cursor, limit=None):
    """Populate projects table."""
    print("\nPopulating projects table...")

    entities = get_entities_by_type(cursor, TYPE_URIS['Project'], limit)

    if not entities:
        print("  No Project entities found")
        return 0

    for uri, props in entities:
        name = get_property_value(props, f"{NS['cpmeta']}hasName")
        keywords = get_property_value(props, f"{NS['cpmeta']}hasKeywords")

        cursor.execute("""
            INSERT INTO projects (uri, name, keywords)
            VALUES (%s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET name = EXCLUDED.name,
                keywords = EXCLUDED.keywords
            RETURNING id
        """, (uri, name, keywords))

    print(f"  Inserted {len(entities)} projects")
    return len(entities)


def populate_object_specs(cursor, limit=None):
    """Populate object_specs table."""
    print("\nPopulating object_specs table...")

    # Get all DataObjectSpec entities
    entities = []
    for type_name in ['DataObjectSpec', 'SimpleObjectSpec']:
        type_uri = TYPE_URIS.get(type_name, f"{NS['cpmeta']}{type_name}")
        entities.extend(get_entities_by_type(cursor, type_uri, limit))

    if not entities:
        print("  No DataObjectSpec entities found")
        return 0

    for uri, props in entities:
        # Determine type
        types = get_property_values(props, f"{NS['rdf']}type")
        type_name = 'DataObjectSpec'
        for t in types:
            if 'SimpleObjectSpec' in t:
                type_name = 'SimpleObjectSpec'
                break

        label = get_property_value(props, f"{NS['rdfs']}label")
        description = get_property_value(props, f"{NS['rdfs']}comment")
        data_level = get_property_value(props, f"{NS['cpmeta']}hasDataLevel")
        keywords = get_property_value(props, f"{NS['cpmeta']}hasKeywords")
        see_also = get_property_value(props, f"{NS['rdfs']}seeAlso")

        # Get foreign key IDs and URIs
        theme_uri = get_property_value(props, f"{NS['cpmeta']}hasDataTheme")
        format_uri = get_property_value(props, f"{NS['cpmeta']}hasFormat")
        encoding_uri = get_property_value(props, f"{NS['cpmeta']}hasEncoding")
        project_uri = get_property_value(props, f"{NS['cpmeta']}hasAssociatedProject")
        dataset_spec_uri = get_property_value(props, f"{NS['cpmeta']}containsDataset")
        documentation_object_uri = get_property_value(props, f"{NS['cpmeta']}hasDocumentationObject")

        theme_id = None
        if theme_uri:
            cursor.execute("SELECT id FROM data_themes WHERE uri = %s", (theme_uri,))
            result = cursor.fetchone()
            if result:
                theme_id = result[0]

        format_id = None
        if format_uri:
            cursor.execute("SELECT id FROM object_formats WHERE uri = %s", (format_uri,))
            result = cursor.fetchone()
            if result:
                format_id = result[0]

        encoding_id = None
        if encoding_uri:
            cursor.execute("SELECT id FROM object_encodings WHERE uri = %s", (encoding_uri,))
            result = cursor.fetchone()
            if result:
                encoding_id = result[0]

        project_id = None
        if project_uri:
            cursor.execute("SELECT id FROM projects WHERE uri = %s", (project_uri,))
            result = cursor.fetchone()
            if result:
                project_id = result[0]

        dataset_type_id = None
        dataset_type_uri = get_property_value(props, f"{NS['cpmeta']}hasSpecificDatasetType")
        if dataset_type_uri:
            cursor.execute("SELECT id FROM specific_dataset_types WHERE uri = %s", (dataset_type_uri,))
            result = cursor.fetchone()
            if result:
                dataset_type_id = result[0]

        cursor.execute("""
            INSERT INTO object_specs (
                uri, type, label, description, data_level,
                theme_id, format_id, encoding_id, project_id, dataset_type_id,
                dataset_spec_uri, documentation_object_uri, keywords, see_also
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (uri) DO UPDATE
            SET type = EXCLUDED.type,
                label = EXCLUDED.label,
                description = EXCLUDED.description,
                data_level = EXCLUDED.data_level,
                theme_id = EXCLUDED.theme_id,
                format_id = EXCLUDED.format_id,
                encoding_id = EXCLUDED.encoding_id,
                project_id = EXCLUDED.project_id,
                dataset_type_id = EXCLUDED.dataset_type_id,
                dataset_spec_uri = EXCLUDED.dataset_spec_uri,
                documentation_object_uri = EXCLUDED.documentation_object_uri,
                keywords = EXCLUDED.keywords,
                see_also = EXCLUDED.see_also
            RETURNING id
        """, (
            uri, type_name, label, description, data_level,
            theme_id, format_id, encoding_id, project_id, dataset_type_id,
            dataset_spec_uri, documentation_object_uri, keywords, see_also
        ))

    print(f"  Inserted {len(entities)} object specs")
    return len(entities)


def populate_data_objects(cursor, limit=None):
    """Populate data_objects table using a single optimized SQL query."""
    print("\nPopulating data_objects table...")

    # Build the type filter
    type_filter = "', '".join([
        TYPE_URIS['DataObject'],
        TYPE_URIS['SimpleDataObject'],
        TYPE_URIS['SpatialDataObject']
    ])

    # Build the LIMIT clause if specified
    limit_clause = f"LIMIT {limit}" if limit else ""

    # Single SQL query that pivots RDF triples into relational columns
    query = f"""
        WITH data_object_subjects AS (
            -- Find all subjects that are DataObjects (or subclasses)
            SELECT DISTINCT subj
            FROM {TRIPLES_TABLE}
            WHERE pred = %s
              AND obj IN ('{type_filter}')
            {limit_clause}
        )
        INSERT INTO data_objects (
            uri, sha256, type, name, doi,
            acquisition_start_time, acquisition_end_time,
            submission_start_time, submission_end_time,
            data_start_time, data_end_time,
            size_in_bytes, number_of_rows, actual_column_names,
            object_spec_id, was_produced_by, was_submitted_by,
            actual_variable_uri, temporal_resolution
        )
        SELECT
            t.subj as uri,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as sha256,
            -- Determine most specific type
            CASE
                WHEN bool_or(t.pred = %s AND t.obj = %s) THEN 'SimpleDataObject'
                WHEN bool_or(t.pred = %s AND t.obj = %s) THEN 'SpatialDataObject'
                ELSE 'DataObject'
            END as type,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as name,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as doi,
            -- Temporal properties (cast to timestamp with time zone)
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::timestamp with time zone as acquisition_start_time,
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::timestamp with time zone as acquisition_end_time,
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::timestamp with time zone as submission_start_time,
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::timestamp with time zone as submission_end_time,
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::timestamp with time zone as data_start_time,
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::timestamp with time zone as data_end_time,
            -- Size properties (cast to bigint)
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::bigint as size_in_bytes,
            MAX(CASE WHEN t.pred = %s THEN t.obj END)::bigint as number_of_rows,
            -- Column names as JSONB
            CASE
                WHEN MAX(CASE WHEN t.pred = %s THEN t.obj END) IS NOT NULL
                THEN to_jsonb(MAX(CASE WHEN t.pred = %s THEN t.obj END))
                ELSE NULL
            END as actual_column_names,
            -- Foreign key to object_specs
            spec.id as object_spec_id,
            -- Provenance
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as was_produced_by,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as was_submitted_by,
            -- Additional properties
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as actual_variable_uri,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as temporal_resolution
        FROM data_object_subjects dos
        JOIN {TRIPLES_TABLE} t ON dos.subj = t.subj
        LEFT JOIN object_specs spec ON spec.uri = (
            SELECT obj
            FROM {TRIPLES_TABLE}
            WHERE subj = dos.subj
              AND pred = %s
            LIMIT 1
        )
        GROUP BY t.subj, spec.id
        HAVING MAX(CASE WHEN t.pred = %s THEN t.obj END) IS NOT NULL
           AND spec.id IS NOT NULL
        ON CONFLICT (uri) DO UPDATE
        SET sha256 = EXCLUDED.sha256,
            type = EXCLUDED.type,
            name = EXCLUDED.name,
            doi = EXCLUDED.doi,
            acquisition_start_time = EXCLUDED.acquisition_start_time,
            acquisition_end_time = EXCLUDED.acquisition_end_time,
            submission_start_time = EXCLUDED.submission_start_time,
            submission_end_time = EXCLUDED.submission_end_time,
            data_start_time = EXCLUDED.data_start_time,
            data_end_time = EXCLUDED.data_end_time,
            size_in_bytes = EXCLUDED.size_in_bytes,
            number_of_rows = EXCLUDED.number_of_rows,
            actual_column_names = EXCLUDED.actual_column_names,
            object_spec_id = EXCLUDED.object_spec_id,
            was_produced_by = EXCLUDED.was_produced_by,
            was_submitted_by = EXCLUDED.was_submitted_by,
            actual_variable_uri = EXCLUDED.actual_variable_uri,
            temporal_resolution = EXCLUDED.temporal_resolution,
            updated_at = NOW()
    """

    # Define all the predicate URIs
    params = [
        f"{NS['rdf']}type",                              # CTE WHERE clause
        f"{NS['cpmeta']}hasSha256sum",                   # sha256
        f"{NS['rdf']}type",                              # type check 1 - SimpleDataObject
        TYPE_URIS['SimpleDataObject'],
        f"{NS['rdf']}type",                              # type check 2 - SpatialDataObject
        TYPE_URIS['SpatialDataObject'],
        f"{NS['cpmeta']}hasName",                        # name
        f"{NS['cpmeta']}hasDoi",                         # doi
        f"{NS['cpmeta']}hasAcquisitionStartTime",        # acquisition_start_time
        f"{NS['cpmeta']}hasAcquisitionEndTime",          # acquisition_end_time
        f"{NS['cpmeta']}hasSubmissionStartTime",         # submission_start_time
        f"{NS['cpmeta']}hasSubmissionEndTime",           # submission_end_time
        f"{NS['cpmeta']}hasDataStartTime",               # data_start_time
        f"{NS['cpmeta']}hasDataEndTime",                 # data_end_time
        f"{NS['cpmeta']}hasSizeInBytes",                 # size_in_bytes
        f"{NS['cpmeta']}hasNumberOfRows",                # number_of_rows
        f"{NS['cpmeta']}hasActualColumnNames",           # actual_column_names (1)
        f"{NS['cpmeta']}hasActualColumnNames",           # actual_column_names (2)
        f"{NS['prov']}wasProducedBy",                    # was_produced_by
        f"{NS['cpmeta']}wasSubmittedBy",                 # was_submitted_by
        f"{NS['cpmeta']}hasActualVariable",              # actual_variable_uri
        f"{NS['cpmeta']}hasTemporalResolution",          # temporal_resolution
        f"{NS['cpmeta']}hasObjectSpec",                  # object spec subquery
        f"{NS['cpmeta']}hasSha256sum",                   # HAVING clause
    ]

    cursor.execute(query, params)
    count = cursor.rowcount

    print(f"  Inserted {count} data objects")
    return count


def populate_data_acquisitions(cursor, limit=None):
    """Populate data_acquisitions table."""
    print("\nPopulating data_acquisitions table...")

    type_filter = f"'{TYPE_URIS['DataAcquisition']}'"
    limit_clause = f"LIMIT {limit}" if limit else ""

    query = f"""
        INSERT INTO data_acquisitions (
            uri, station_id, instrument_id, sampling_height, sampling_point_uri,
            was_performed_at, started_at_time, ended_at_time
        )
        SELECT
            acq_uri,
            s.id as station_id,
            i.id as instrument_id,
            sampling_height,
            sampling_point_uri,
            was_performed_at,
            started_at_time,
            ended_at_time
        FROM (
            SELECT
                subj as acq_uri,
                MAX(CASE WHEN pred = %s THEN obj END) as station_uri,
                MAX(CASE WHEN pred = %s THEN obj END) as instrument_uri,
                MAX(CASE WHEN pred = %s THEN obj END)::float as sampling_height,
                MAX(CASE WHEN pred = %s THEN obj END) as sampling_point_uri,
                MAX(CASE WHEN pred = %s THEN obj END) as was_performed_at,
                MAX(CASE WHEN pred = %s THEN obj::timestamp with time zone END) as started_at_time,
                MAX(CASE WHEN pred = %s THEN obj::timestamp with time zone END) as ended_at_time
            FROM {TRIPLES_TABLE}
            WHERE subj IN (
                SELECT subj FROM {TRIPLES_TABLE}
                WHERE pred = '{NS['rdf']}type'
                AND obj IN ({type_filter})
                {limit_clause}
            )
            GROUP BY subj
        ) acq
        LEFT JOIN stations s ON s.uri = acq.station_uri
        LEFT JOIN instruments i ON i.uri = acq.instrument_uri
        ON CONFLICT (uri) DO UPDATE
        SET station_id = EXCLUDED.station_id,
            instrument_id = EXCLUDED.instrument_id,
            sampling_height = EXCLUDED.sampling_height,
            sampling_point_uri = EXCLUDED.sampling_point_uri,
            was_performed_at = EXCLUDED.was_performed_at,
            started_at_time = EXCLUDED.started_at_time,
            ended_at_time = EXCLUDED.ended_at_time
    """

    params = [
        f"{NS['prov']}wasAssociatedWith",                 # station_uri
        f"{NS['cpmeta']}wasPerformedWith",                # instrument_uri
        f"{NS['cpmeta']}hasSamplingHeight",               # sampling_height
        f"{NS['cpmeta']}hasSamplingPoint",                # sampling_point_uri
        f"{NS['cpmeta']}wasPerformedAt",                  # was_performed_at
        f"{NS['prov']}startedAtTime",                     # started_at_time
        f"{NS['prov']}endedAtTime",                       # ended_at_time
    ]

    cursor.execute(query, params)
    count = cursor.rowcount

    print(f"  Inserted {count} data acquisitions")
    return count


def populate_data_object_keywords(cursor):
    """Populate data_object_keywords junction table."""
    print("\nPopulating data_object_keywords table...")

    keyword_pred = f"{NS['cpmeta']}hasKeyword"

    cursor.execute(f"""
        INSERT INTO data_object_keywords (data_object_id, keyword_id)
        SELECT DISTINCT d.id, k.id
        FROM {TRIPLES_TABLE} t
        JOIN data_objects d ON t.subj = d.uri
        JOIN keywords k ON t.obj = k.keyword
        WHERE t.pred = %s
        ON CONFLICT DO NOTHING
    """, (keyword_pred,))

    count = cursor.rowcount
    print(f"  Inserted {count} data object-keyword relationships")
    return count


def populate_data_object_acquisitions(cursor):
    """Populate data_object_acquisitions junction table."""
    print("\nPopulating data_object_acquisitions table...")

    was_acquired_by_pred = f"{NS['cpmeta']}wasAcquiredBy"

    cursor.execute(f"""
        INSERT INTO data_object_acquisitions (data_object_id, acquisition_id)
        SELECT DISTINCT d.id, a.id
        FROM {TRIPLES_TABLE} t
        JOIN data_objects d ON t.subj = d.uri
        JOIN data_acquisitions a ON t.obj = a.uri
        WHERE t.pred = %s
        ON CONFLICT DO NOTHING
    """, (was_acquired_by_pred,))

    count = cursor.rowcount
    print(f"  Inserted {count} data object-acquisition relationships")
    return count


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Create and populate class-based tables from RDF triples",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s                    # Full schema creation and population
  %(prog)s --limit 1000       # Limit entities per class
  %(prog)s --drop-only        # Only drop existing tables
        """
    )

    parser.add_argument('--host', default='localhost', help='Database host')
    parser.add_argument('--port', type=int, default=5432, help='Database port')
    parser.add_argument('--user', default='postgres', help='Database user')
    parser.add_argument('--dbname', default='postgres', help='Database name')
    parser.add_argument('--password', default='ontop', help='Database password')
    parser.add_argument('--limit', type=int, help='Limit entities per class')
    parser.add_argument('--drop-only', action='store_true', help='Only drop tables')

    args = parser.parse_args()

    print(f"Connecting to PostgreSQL at {args.host}:{args.port}...")
    conn = get_connection(args.host, args.port, args.user, args.dbname, args.password)

    try:
        cursor = conn.cursor()

        # Check which triples table exists
        check_triples_table(cursor)

        # Drop existing tables
        drop_class_tables(cursor)
        conn.commit()

        if args.drop_only:
            print("\n✓ Dropped all class-based tables")
            return

        # Create schema
        create_schema(cursor)
        conn.commit()

        # Populate tables in dependency order
        print("\n" + "="*60)
        print("POPULATING TABLES")
        print("="*60)

        populate_keywords(cursor, args.limit)
        conn.commit()

        populate_data_themes(cursor, args.limit)
        conn.commit()

        populate_object_formats(cursor, args.limit)
        conn.commit()

        populate_climate_zones(cursor, args.limit)
        conn.commit()

        populate_organizations(cursor, args.limit)
        conn.commit()

        populate_persons(cursor, args.limit)
        conn.commit()

        populate_instruments(cursor, args.limit)
        conn.commit()

        populate_stations(cursor, args.limit)
        conn.commit()

        populate_projects(cursor, args.limit)
        conn.commit()

        populate_object_specs(cursor, args.limit)
        conn.commit()

        populate_data_objects(cursor, args.limit)
        conn.commit()

        populate_data_acquisitions(cursor, args.limit)
        conn.commit()

        populate_data_object_keywords(cursor)
        conn.commit()

        populate_data_object_acquisitions(cursor)
        conn.commit()

        print("\n" + "="*60)
        print("✓ SUCCESSFULLY COMPLETED")
        print("="*60)

        cursor.close()

    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        conn.rollback()
        sys.exit(1)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
