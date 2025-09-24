#!/usr/bin/python

import psycopg2
from psycopg2.extras import execute_batch
import json
import csv
import sys
import argparse
from collections import defaultdict
from pathlib import Path
import os

# csv_path = "./dump_full.csv"
# csv_path = "./dump_mini.csv"
csv_path = "./dump_partial.csv"

def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )

def execute_sql_file(conn, path):
    cursor = conn.cursor()
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
        print(content)
        cursor.execute(content)
        conn.commit()

def recreate_dependent_tables():
    conn = get_connection()
    print("Recreating dependent tables")
    execute_sql_file(conn, 'psql/create_dependent_tables.sql')


def process_triples(csv_path, limit=None):
    print("Processing RDF triples")
    subject_data = defaultdict(lambda: defaultdict(list))

    with open(csv_path, 'r', encoding='utf-8') as csvfile:
        reader = csv.DictReader(csvfile)
        total_rows = 0

        for row in reader:
            subject = row['subj']
            predicate = row['pred']
            obj = row['obj']

            subject_data[subject][predicate].append(obj)
            total_rows += 1

            if total_rows % 10000 == 0:
                print(f"Processed {total_rows} triples...")

            if limit and total_rows >= limit:
                print(f"Reached limit of {limit} triples")
                break

    print(f"Total triples processed: {total_rows}")
    print(f"Unique subjects found: {len(subject_data)}")

    processed_data = []
    for subject, predicates in subject_data.items():
        properties = {}
        for pred, objs in predicates.items():
            if len(objs) == 1:
                properties[pred] = objs[0]
            else:
                properties[pred] = objs

        processed_data.append((subject, json.dumps(properties)))

    return processed_data

def populate_triples(limit):
    data = process_triples(csv_path, limit)
    conn = get_connection()
    cursor = conn.cursor()
    batch_size = 10000
    total_inserted = 0

    for i in range(0, len(data), batch_size):
        batch = data[i:i+batch_size]
        execute_batch(
            cursor,
            "INSERT INTO triples (subject, properties) VALUES (%s, %s)",
            batch
        )
        total_inserted += len(batch)

        if total_inserted % 10000 == 0:
            print(f"Inserted {total_inserted} triples...")
            conn.commit()

    conn.commit()
    print(f"Total triples inserted: {total_inserted}")

def get_temporal_data(cursor, subject):
    started_at_time_pred = "http://www.w3.org/ns/prov#startedAtTime"
    ended_at_time_pred = "http://www.w3.org/ns/prov#endedAtTime"

    cursor.execute("""
        SELECT properties->>%s as start_time,
               properties->>%s as end_time
        FROM triples WHERE subject = %s
    """, (started_at_time_pred, ended_at_time_pred, subject))

    result = cursor.fetchone()
    if result:
        return result
    return None, None


def get_property_value(properties, predicate, convert_type=None):
    value = properties.get(predicate)
    if convert_type == 'int' and value != None:
        return int(value)

    return value

def populate_data_objects_table(conn, object_spec_mapping=None):
    print("Populating data_objects table")
    cursor = conn.cursor()

    object_spec_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec"
    name_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasName"
    was_acquired_by_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/wasAcquiredBy"
    was_submitted_by_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/wasSubmittedBy"
    has_start_time_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasStartTime"
    has_end_time_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasEndTime"
    has_sha256sum_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasSha256sum"
    has_number_of_rows_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasNumberOfRows"
    has_size_in_bytes_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes"
    has_actual_column_names_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasActualColumnNames"
    is_next_version_of_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf"
    was_produced_by_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/wasProducedBy"
    has_doi_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasDoi"

    if object_spec_mapping is None:
        cursor.execute("SELECT subject, id FROM object_specs")
        object_spec_mapping = dict(cursor.fetchall())

    print("Building data object URI mapping for foreign key resolution...")
    query = """
        SELECT subject, id
        FROM triples
        WHERE properties->%s IS NOT NULL
    """
    cursor.execute(query, (object_spec_pred,))
    data_object_uri_to_triple_id = dict(cursor.fetchall())
    print(f"Built mapping for {len(data_object_uri_to_triple_id):,} data object URIs")

    query = """
        SELECT id, subject, properties
        FROM triples
        WHERE properties->%s IS NOT NULL
    """

    cursor.execute(query, (object_spec_pred,))
    data_objects = cursor.fetchall()

    print(f"\nFound {len(data_objects)} triples with hasObjectSpec predicate")

    batch_size = 100000
    total_inserted = 0
    data_to_insert = []
    insertion_query = "INSERT INTO data_objects (triple_id, subject, object_spec_id, name, acquisition_start_time, acquisition_end_time, submission_start_time, submission_end_time, data_start_time, data_end_time, hasSha256sum, hasNumberOfRows, hasSizeInBytes, hasActualColumnNames, isNextVersionOf, wasProducedBy, hasDoi) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"

    for triple_id, subject, properties in data_objects:
        object_spec_uri = get_property_value(properties, object_spec_pred)
        object_spec_id = object_spec_mapping.get(object_spec_uri) if object_spec_uri else None

        # Object spec for this data object was cut from data-set.
        # Very likely, when using a partial rdf log
        if object_spec_id == None:
            continue

        # Extract core properties
        name = get_property_value(properties, name_pred)
        acquisition_uri = get_property_value(properties, was_acquired_by_pred)
        submission_uri = get_property_value(properties, was_submitted_by_pred)
        data_start = properties.get(has_start_time_pred)
        data_end = properties.get(has_end_time_pred)

        # Extract new properties
        sha256sum = get_property_value(properties, has_sha256sum_pred)
        number_of_rows = get_property_value(properties, has_number_of_rows_pred, 'int')
        size_in_bytes = get_property_value(properties, has_size_in_bytes_pred, 'int')
        column_names = get_property_value(properties, has_actual_column_names_pred)
        next_version_of_uri = get_property_value(properties, is_next_version_of_pred)
        produced_by = get_property_value(properties, was_produced_by_pred)
        doi = get_property_value(properties, has_doi_pred)

        # Store isNextVersionOf URI as None for now - will be resolved in second pass
        next_version_of_id = None
        # Get temporal data from acquisition and submission triples
        acq_start, acq_end = get_temporal_data(cursor, acquisition_uri)
        sub_start, sub_end = get_temporal_data(cursor, submission_uri)

        data_to_insert.append((
            triple_id, subject, object_spec_id, name,
            acq_start, acq_end, sub_start, sub_end, data_start, data_end,
            sha256sum, number_of_rows, size_in_bytes, column_names,
            next_version_of_id, produced_by, doi
        ))

        if len(data_to_insert) >= batch_size:
            execute_batch(cursor, insertion_query, data_to_insert)
            total_inserted += len(data_to_insert)
            data_to_insert = []

            if total_inserted % 10000 == 0:
                print(f"Inserted {total_inserted} data objects...")
                conn.commit()

    execute_batch(cursor,insertion_query, data_to_insert)
    total_inserted += len(data_to_insert)

    conn.commit()
    print(f"Total data objects inserted: {total_inserted}")


    return total_inserted


def update_next_version(conn):
    cursor = conn.cursor()
    # Second pass: Update isNextVersionOf foreign keys
    print("\nSecond data object pass: Resolving isNextVersionOf foreign key relationships...")

    # Create mapping from subject URI to data_objects.id
    cursor.execute("SELECT subject, id FROM data_objects")
    subject_to_id_mapping = dict(cursor.fetchall())

    is_next_version_of_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/isNextVersionOf"

    # Find all data objects that have isNextVersionOf relationships to update
    cursor.execute("""
        SELECT e.subject, e.properties->>%s as next_version_uri
        FROM triples e
        JOIN data_objects data_object ON e.id = data_object.triple_id
        WHERE e.properties->%s IS NOT NULL
    """, (is_next_version_of_pred, is_next_version_of_pred))

    updates_to_make = []
    for subject, next_version_uri_raw in cursor.fetchall():
        # Handle list values
        if isinstance(next_version_uri_raw, str) and next_version_uri_raw.startswith('['):
            next_version_uri = json.loads(next_version_uri_raw)[0] if next_version_uri_raw != '[]' else None
        else:
            next_version_uri = next_version_uri_raw

        if next_version_uri:
            referenced_id = subject_to_id_mapping.get(next_version_uri)
            if referenced_id:
                updates_to_make.append((referenced_id, subject))
            else:
                print(f"Warning: isNextVersionOf reference not found: {next_version_uri}")

    print(f"Updating {len(updates_to_make)} isNextVersionOf foreign key relationships...")

    if updates_to_make:
        execute_batch(
            cursor,
            "UPDATE data_objects SET isNextVersionOf = %s WHERE subject = %s",
            updates_to_make
        )
        conn.commit()

    print("Foreign key relationships updated successfully")
    # Second pass: Update isNextVersionOf foreign keys
    print("\nSecond pass: Resolving isNextVersionOf foreign key relationships...")

    # Create mapping from subject URI to data_objects.id
    cursor.execute("SELECT subject, id FROM data_objects")
    subject_to_id_mapping = dict(cursor.fetchall())

    # Find all data objects that have isNextVersionOf relationships to update
    cursor.execute("""
        SELECT e.subject, e.properties->>%s as next_version_uri
        FROM triples e
        JOIN data_objects data_object ON e.id = data_object.triple_id
        WHERE e.properties->%s IS NOT NULL
    """, (is_next_version_of_pred, is_next_version_of_pred))

    updates_to_make = []
    for subject, next_version_uri_raw in cursor.fetchall():
        # Handle list values
        if isinstance(next_version_uri_raw, str) and next_version_uri_raw.startswith('['):
            next_version_uri = json.loads(next_version_uri_raw)[0] if next_version_uri_raw != '[]' else None
        else:
            next_version_uri = next_version_uri_raw

        if next_version_uri:
            referenced_id = subject_to_id_mapping.get(next_version_uri)
            if referenced_id:
                updates_to_make.append((referenced_id, subject))
            else:
                print(f"Warning: isNextVersionOf reference not found: {next_version_uri}")

    print(f"Updating {len(updates_to_make)} isNextVersionOf foreign key relationships...")

    if updates_to_make:
        execute_batch(
            cursor,
            "UPDATE data_objects SET isNextVersionOf = %s WHERE subject = %s",
            updates_to_make
        )
        conn.commit()

    print("Foreign key relationships updated successfully")


def populate_keywords_tables(conn):
    print("Step 8: Populating keywords")
    cursor = conn.cursor()

    keywords_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords"

    query = """
        SELECT id, subject, properties->>%s as keywords_str
        FROM triples
        WHERE properties->%s IS NOT NULL
    """

    cursor.execute(query, (keywords_pred, keywords_pred))
    triples_with_keywords = cursor.fetchall()

    print(f"\nFound {len(triples_with_keywords)} triples with keywords")

    # Dictionary to cache keyword IDs
    keyword_to_id = {}

    triple_keyword_pairs = []

    for triple_id, subject, keywords_str in triples_with_keywords:
        if keywords_str:
            # Handle case where keywords might be a list
            if isinstance(keywords_str, str) and keywords_str.startswith('['):
                keywords_list = json.loads(keywords_str)
                if keywords_list:
                    keywords_str = keywords_list[0] if isinstance(keywords_list, list) else keywords_list

            # Parse comma-separated keywords
            keywords = [k.strip() for k in str(keywords_str).split(',') if k.strip()]

            for keyword in keywords:
                # Get or create keyword ID
                if keyword not in keyword_to_id:
                    # Try to insert the keyword
                    cursor.execute("INSERT INTO keywords (keyword) VALUES (%s) ON CONFLICT (keyword) DO NOTHING", (keyword,))
                    cursor.execute("SELECT id FROM keywords WHERE keyword = %s", (keyword,))
                    keyword_to_id[keyword] = cursor.fetchone()[0]

                triple_keyword_pairs.append((triple_id, keyword_to_id[keyword]))

    execute_batch(
        cursor,
        "INSERT INTO triple_keywords (triple_id, keyword_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
        triple_keyword_pairs
    )

    conn.commit()

    # Get statistics
    cursor.execute("SELECT COUNT(DISTINCT id) FROM keywords")
    unique_keywords = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM triple_keywords")
    total_relationships = cursor.fetchone()[0]

    print(f"Total unique keywords: {unique_keywords}")
    print(f"Total triple-keyword relationships: {total_relationships}")

    # Show top keywords
    cursor.execute("""
        SELECT k.keyword, COUNT(ek.triple_id) as count
        FROM keywords k
        JOIN triple_keywords ek ON k.id = ek.keyword_id
        GROUP BY k.keyword
        ORDER BY count DESC
        LIMIT 10
    """)

    print("\nTop 10 keywords by frequency:")
    for keyword, count in cursor.fetchall():
        print(f"  {keyword}: {count} triples")

    # Populate object spec keywords if object_specs table exists
    cursor.execute("""
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_name = 'object_specs'
    """)
    if cursor.fetchone()[0] > 0:
        populate_object_spec_keywords(conn, keyword_to_id)

    # Populate project keywords if projects table exists
    cursor.execute("""
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_name = 'projects'
    """)
    if cursor.fetchone()[0] > 0:
        populate_project_keywords(conn, keyword_to_id)

    return unique_keywords

def populate_object_spec_keywords(conn, keyword_to_id):
    cursor = conn.cursor()

    keywords_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords"

    query = """
        SELECT os.id, os.subject, e.properties->>%s as keywords_str
        FROM object_specs os
        JOIN triples e ON os.triple_id = e.id
        WHERE e.properties->%s IS NOT NULL
    """

    cursor.execute(query, (keywords_pred, keywords_pred))
    object_specs_with_keywords = cursor.fetchall()

    print(f"\nFound {len(object_specs_with_keywords)} object specs with keywords")

    object_spec_keyword_pairs = []

    for object_spec_id, subject, keywords_str in object_specs_with_keywords:
        if keywords_str:
            # Handle case where keywords might be a list
            if isinstance(keywords_str, str) and keywords_str.startswith('['):
                keywords_list = json.loads(keywords_str)
                if keywords_list:
                    keywords_str = keywords_list[0] if isinstance(keywords_list, list) else keywords_list

            # Parse comma-separated keywords
            keywords = [k.strip() for k in str(keywords_str).split(',') if k.strip()]

            for keyword in keywords:
                # Get keyword ID (should already exist from populate_keywords_tables)
                if keyword in keyword_to_id:
                    object_spec_keyword_pairs.append((object_spec_id, keyword_to_id[keyword]))

    execute_batch(
        cursor,
        "INSERT INTO object_spec_keywords (object_spec_id, keyword_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
        object_spec_keyword_pairs
    )

    conn.commit()

    # Get statistics
    cursor.execute("SELECT COUNT(*) FROM object_spec_keywords")
    total_relationships = cursor.fetchone()[0]

    print(f"Total object spec-keyword relationships: {total_relationships}")

    # Show top keywords for object specs
    cursor.execute("""
        SELECT k.keyword, COUNT(osk.object_spec_id) as count
        FROM keywords k
        JOIN object_spec_keywords osk ON k.id = osk.keyword_id
        GROUP BY k.keyword
        ORDER BY count DESC
        LIMIT 10
    """)

    print("\nTop 10 keywords for object specs by frequency:")
    for keyword, count in cursor.fetchall():
        print(f"  {keyword}: {count} object specs")

    return total_relationships

def populate_project_keywords(conn, keyword_to_id):
    cursor = conn.cursor()

    keywords_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords"

    # Get all projects with keywords (only those that exist as triples)
    query = """
        SELECT p.id, p.subject, e.properties->>%s as keywords_str
        FROM projects p
        JOIN triples e ON p.triple_id = e.id
        WHERE p.triple_id IS NOT NULL
        AND e.properties->%s IS NOT NULL
    """

    cursor.execute(query, (keywords_pred, keywords_pred))
    projects_with_keywords = cursor.fetchall()

    print(f"\nFound {len(projects_with_keywords)} projects with keywords")

    # Process each project's keywords
    project_keyword_pairs = []

    for project_id, subject, keywords_str in projects_with_keywords:
        if keywords_str:
            # Handle case where keywords might be a list
            if isinstance(keywords_str, str) and keywords_str.startswith('['):
                keywords_list = json.loads(keywords_str)
                if keywords_list:
                    keywords_str = keywords_list[0] if isinstance(keywords_list, list) else keywords_list

            # Parse comma-separated keywords
            keywords = [k.strip() for k in str(keywords_str).split(',') if k.strip()]

            for keyword in keywords:
                # Get keyword ID (should already exist from populate_keywords_tables)
                if keyword in keyword_to_id:
                    project_keyword_pairs.append((project_id, keyword_to_id[keyword]))

    # Batch insert project-keyword relationships
    if project_keyword_pairs:
        execute_batch(
            cursor,
            "INSERT INTO project_keywords (project_id, keyword_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            project_keyword_pairs
        )

    conn.commit()

    # Get statistics
    cursor.execute("SELECT COUNT(*) FROM project_keywords")
    total_relationships = cursor.fetchone()[0]

    print(f"Total project-keyword relationships: {total_relationships}")

    # Show top keywords for projects
    cursor.execute("""
        SELECT k.keyword, COUNT(pk.project_id) as count
        FROM keywords k
        JOIN project_keywords pk ON k.id = pk.keyword_id
        GROUP BY k.keyword
        ORDER BY count DESC
        LIMIT 10
    """)

    print("\nTop 10 keywords for projects by frequency:")
    for keyword, count in cursor.fetchall():
        print(f"  {keyword}: {count} projects")

    return total_relationships

def populate_object_specs_table(conn):
    print("Populating object_specs")
    cursor = conn.cursor()

    rdf_type_pred = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    data_object_type = "http://meta.icos-cp.eu/ontologies/cpmeta/DataObject"
    object_spec_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasObjectSpec"

    # First, find all unique object spec URIs referenced by data objects
    cursor.execute("""
        SELECT DISTINCT properties->>%s as object_spec_uri
        FROM triples e
        WHERE properties->>%s = %s
          AND properties->%s IS NOT NULL
    """, (object_spec_pred, rdf_type_pred, data_object_type, object_spec_pred))

    referenced_specs = cursor.fetchall()
    referenced_spec_uris = set()

    for row in referenced_specs:
        spec_uri = row[0]
        if isinstance(spec_uri, str) and spec_uri.startswith('['):
            import json
            spec_list = json.loads(spec_uri)
            referenced_spec_uris.update(spec_list)
        elif spec_uri:
            referenced_spec_uris.add(spec_uri)

    print(f"\nFound {len(referenced_spec_uris)} unique object spec URIs referenced by data objects")

    # Now find all triples that match these URIs
    object_specs = []
    for spec_uri in referenced_spec_uris:
        cursor.execute("SELECT id, subject FROM triples WHERE subject = %s", (spec_uri,))
        result = cursor.fetchone()
        if result:
            object_specs.append(result)
        else:
            print(f"Warning: Referenced object spec not found in triples: {spec_uri}")

    print(f"Found {len(object_specs)} object spec triples in database")

    batch_size = 1000
    total_inserted = 0
    data_to_insert = []

    for triple_id, subject in object_specs:
        data_to_insert.append((triple_id, subject))

        if len(data_to_insert) >= batch_size:
            execute_batch(
                cursor,
                "INSERT INTO object_specs (triple_id, subject) VALUES (%s, %s)",
                data_to_insert
            )
            total_inserted += len(data_to_insert)
            data_to_insert = []

            if total_inserted % 10000 == 0:
                print(f"Inserted {total_inserted} object specs...")
                conn.commit()

    if data_to_insert:
        execute_batch(
            cursor,
            "INSERT INTO object_specs (triple_id, subject) VALUES (%s, %s)",
            data_to_insert
        )
        total_inserted += len(data_to_insert)

    conn.commit()
    print(f"Total object specs inserted: {total_inserted}")

    cursor.execute("SELECT subject, id FROM object_specs")
    subject_to_id = dict(cursor.fetchall())

    return subject_to_id

def populate_projects_table(conn):
    print("Populating projects")
    cursor = conn.cursor()

    associated_project_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject"
    name_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasName"

    # Get all unique project URIs from object specs
    query = """
        SELECT DISTINCT e.properties->>%s as project_uri
        FROM object_specs os
        JOIN triples e ON os.triple_id = e.id
        WHERE e.properties->%s IS NOT NULL
    """

    cursor.execute(query, (associated_project_pred, associated_project_pred))
    project_uris_raw = cursor.fetchall()

    # Process and flatten project URIs (might be lists or single values)
    unique_project_uris = set()
    for row in project_uris_raw:
        project_uri = row[0]
        if project_uri:
            if isinstance(project_uri, str) and project_uri.startswith('['):
                uri_list = json.loads(project_uri)
                for uri in (uri_list if isinstance(uri_list, list) else [uri_list]):
                    if uri:
                        unique_project_uris.add(uri)
            else:
                unique_project_uris.add(project_uri)

    print(f"\nFound {len(unique_project_uris)} unique project URIs")

    # Insert projects
    total_inserted = 0
    for project_uri in unique_project_uris:
        # Check if this project exists as an triple
        cursor.execute("SELECT id, properties FROM triples WHERE subject = %s", (project_uri,))
        triple_row = cursor.fetchone()

        triple_id = None
        name = None

        if triple_row:
            triple_id = triple_row[0]
            properties = triple_row[1]
            name = properties.get(name_pred)
            if isinstance(name, list):
                name = name[0] if name else None

        cursor.execute(
            "INSERT INTO projects (triple_id, subject, name) VALUES (%s, %s, %s) ON CONFLICT (subject) DO NOTHING",
            (triple_id, project_uri, name)
        )
        total_inserted += 1

    conn.commit()
    print(f"Total projects inserted: {total_inserted}")

    # Return mapping of project URI to project ID
    cursor.execute("SELECT subject, id FROM projects")
    project_mapping = dict(cursor.fetchall())

    return project_mapping

def populate_object_spec_projects(conn, project_mapping=None):
    print("Populating object_spec_projects")
    cursor = conn.cursor()

    associated_project_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject"

    if project_mapping is None:
        cursor.execute("SELECT subject, id FROM projects")
        project_mapping = dict(cursor.fetchall())

    # Get all object specs with associated projects
    query = """
        SELECT os.id, os.subject, e.properties->>%s as project_uris
        FROM object_specs os
        JOIN triples e ON os.triple_id = e.id
        WHERE e.properties->%s IS NOT NULL
    """

    cursor.execute(query, (associated_project_pred, associated_project_pred))
    object_specs_with_projects = cursor.fetchall()

    print(f"\nFound {len(object_specs_with_projects)} object specs with associated projects")

    # Process relationships
    relationships = []
    for object_spec_id, subject, project_uris in object_specs_with_projects:
        if project_uris:
            # Handle case where project URIs might be a list
            if isinstance(project_uris, str) and project_uris.startswith('['):
                uri_list = json.loads(project_uris)
                project_uri_list = uri_list if isinstance(uri_list, list) else [uri_list]
            else:
                project_uri_list = [project_uris]

            for project_uri in project_uri_list:
                if project_uri and project_uri in project_mapping:
                    relationships.append((object_spec_id, project_mapping[project_uri]))

    # Batch insert relationships
    if relationships:
        execute_batch(
            cursor,
            "INSERT INTO object_spec_projects (object_spec_id, project_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            relationships
        )

    conn.commit()

    total_relationships = len(relationships)
    print(f"Total object spec-project relationships: {total_relationships}")

    # Show statistics
    cursor.execute("""
        SELECT p.subject, p.name, COUNT(osp.object_spec_id) as spec_count
        FROM projects p
        JOIN object_spec_projects osp ON p.id = osp.project_id
        GROUP BY p.id, p.subject, p.name
        ORDER BY spec_count DESC
        LIMIT 10
    """)

    print("\nTop 10 projects by number of associated object specs:")
    for subject, name, count in cursor.fetchall():
        display_name = name if name else subject
        print(f"  {display_name}: {count} object specs")

    return total_relationships

def populate_aggregate_keywords(conn):
    print("Populating aggregate keywords")
    cursor = conn.cursor()

    print("\nPopulating data object aggregated keywords table...")

    # Get all keywords from all three sources for each data object
    query = """
        SELECT DISTINCT data_object.id as data_object_id, k.id as keyword_id
        FROM data_objects data_object
        JOIN keywords k ON 1=1  -- Cross join, we'll filter in the UNIONs
        WHERE k.id IN (
            -- Direct keywords from data object triple
            SELECT ek.keyword_id
            FROM triple_keywords ek
            WHERE ek.triple_id = data_object.triple_id

            UNION

            -- Keywords from object spec (if exists)
            SELECT osk.keyword_id
            FROM object_spec_keywords osk
            WHERE data_object.object_spec_id IS NOT NULL
            AND osk.object_spec_id = data_object.object_spec_id

            UNION

            -- Keywords from associated projects (if exist)
            SELECT pk.keyword_id
            FROM project_keywords pk
            JOIN object_spec_projects osp ON pk.project_id = osp.project_id
            WHERE data_object.object_spec_id IS NOT NULL
            AND osp.object_spec_id = data_object.object_spec_id
        )
    """

    cursor.execute(query)
    relationships = cursor.fetchall()

    print(f"Found {len(relationships)} data object-keyword relationships")

    # Batch insert the relationships
    batch_size = 10000
    total_inserted = 0

    for i in range(0, len(relationships), batch_size):
        batch = relationships[i:i+batch_size]
        execute_batch(
            cursor,
            "INSERT INTO data_object_all_keywords (data_object_id, keyword_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            batch
        )
        total_inserted += len(batch)

        if total_inserted % 50000 == 0:
            print(f"Inserted {total_inserted} relationships...")
            conn.commit()

    conn.commit()
    print(f"Total relationships inserted: {total_inserted}")

    # Show statistics
    cursor.execute("SELECT COUNT(DISTINCT data_object_id) FROM data_object_all_keywords")
    data_objects_with_keywords = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(DISTINCT keyword_id) FROM data_object_all_keywords")
    unique_keywords = cursor.fetchone()[0]

    print(f"Data objects with keywords: {data_objects_with_keywords}")
    print(f"Unique keywords used: {unique_keywords}")

    # Show top keywords by data object count
    cursor.execute("""
        SELECT k.keyword, COUNT(doak.data_object_id) as data_object_count
        FROM keywords k
        JOIN data_object_all_keywords doak ON k.id = doak.keyword_id
        GROUP BY k.id, k.keyword
        ORDER BY data_object_count DESC
        LIMIT 10
    """)

    print("\nTop 10 keywords by number of data objects:")
    for keyword, count in cursor.fetchall():
        print(f"  {keyword}: {count} data objects")

    return total_inserted

def populate_dependent():
    conn = get_connection()
    object_spec_mapping = populate_object_specs_table(conn)
    project_mapping = populate_projects_table(conn)
    populate_object_spec_projects(conn, project_mapping)
    populate_data_objects_table(conn, object_spec_mapping)
    update_next_version(conn)
    populate_keywords_tables(conn)
    populate_aggregate_keywords(conn)

    print("Creating indices")
    execute_sql_file(conn, "psql/create_indices.sql")

def rebuild_dependent():
    recreate_dependent_tables()
    populate_dependent()


def rebuild_all(csv_path, limit=None):
    print("Creating triples table")
    execute_sql_file(get_connection(), "psql/create_triples_table.sql")
    populate_triples(limit)
    rebuild_dependent()

def main():
    parser = argparse.ArgumentParser(
        description="Build and manage PostgreSQL database from RDF triples CSV",
        formatter_class=argparse.RawTextHelpFormatter
    )

    # Create mutually exclusive group for rebuild options
    rebuild_group = parser.add_mutually_exclusive_group(required=True)

    rebuild_group.add_argument(
        "--dependent",
        action="store_true",
        help="Rebuild all tables except triples (object_specs + projects + data_objects + keywords + aggregated)"
    )

    rebuild_group.add_argument(
        "--rebuild-all",
        action="store_true",
        help="Rebuild the entire database"
    )

    parser.add_argument(
        "--limit",
        type=int,
        metavar="N",
        help="Process only the first N triples from CSV (applies to triples rebuilding)"
    )

    args = parser.parse_args()

    if args.dependent:
        rebuild_dependent()
    elif args.rebuild_all:
        rebuild_all(csv_path, args.limit)

if __name__ == "__main__":
    main()
