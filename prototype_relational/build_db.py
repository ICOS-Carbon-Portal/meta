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
from datetime import datetime

csv_path = "./dump_full.csv"
# csv_path = "./dump_mini.csv"
# csv_path = "./dump_partial.csv"

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



def get_temporal_data(cursor, subject):
    started_at_time_pred = "http://www.w3.org/ns/prov#startedAtTime"
    ended_at_time_pred = "http://www.w3.org/ns/prov#endedAtTime"

    if not subject:
        return None, None

    cursor.execute("""
        SELECT
            MAX(CASE WHEN pred = %s THEN obj END) as start_time,
            MAX(CASE WHEN pred = %s THEN obj END) as end_time
        FROM rdf_triples
        WHERE subj = %s
          AND pred IN (%s, %s)
    """, (started_at_time_pred, ended_at_time_pred, subject, started_at_time_pred, ended_at_time_pred))

    result = cursor.fetchone()
    if result:
        start_time, end_time = result
        return start_time, end_time

    return None, None


def get_acquisition_metadata(cursor, acquisition_uri):
    """Fetch wasAssociatedWith and hasSamplingHeight from acquisition triple."""
    was_associated_with_pred = "http://www.w3.org/ns/prov#wasAssociatedWith"
    has_sampling_height_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasSamplingHeight"

    if not acquisition_uri:
        return None, None

    cursor.execute("""
        SELECT
            MAX(CASE WHEN pred = %s THEN obj END) as station_uri,
            MAX(CASE WHEN pred = %s THEN obj END) as sampling_height
        FROM rdf_triples
        WHERE subj = %s
          AND pred IN (%s, %s)
    """, (was_associated_with_pred, has_sampling_height_pred, acquisition_uri, was_associated_with_pred, has_sampling_height_pred))

    result = cursor.fetchone()
    if result:
        station_uri, sampling_height = result
        # Convert sampling height to float if present
        if sampling_height:
            try:
                sampling_height = float(sampling_height)
            except (ValueError, TypeError):
                print(f"WARNING: Could not convert sampling height to float: {sampling_height}")
                sampling_height = None
        return station_uri, sampling_height

    return None, None


def get_property_value(properties, predicate, convert_type=None):
    value = properties.get(predicate)
    if convert_type == 'int' and value != None:
        return int(value)

    return value

def populate_data_objects_table(conn, max_data_objects=None):
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

    # Build query to get all data objects with their properties
    query = """
        WITH data_object_subjects AS (
            SELECT DISTINCT subj, obj as hasObjectSpec
            FROM rdf_triples
            WHERE pred = %s
        )
        SELECT
            t.subj,
            dos.hasObjectSpec,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as name,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as wasAcquiredBy,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as wasSubmittedBy,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasStartTime,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasEndTime,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasSha256sum,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasNumberOfRows,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasSizeInBytes,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasActualColumnNames,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as isNextVersionOf,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as wasProducedBy,
            MAX(CASE WHEN t.pred = %s THEN t.obj END) as hasDoi
        FROM rdf_triples t
        INNER JOIN data_object_subjects dos ON t.subj = dos.subj
        GROUP BY t.subj, dos.hasObjectSpec
    """

    if max_data_objects is not None:
        query += f" LIMIT {max_data_objects}"

    cursor.execute(query, (
        object_spec_pred,
        name_pred, was_acquired_by_pred, was_submitted_by_pred,
        has_start_time_pred, has_end_time_pred, has_sha256sum_pred,
        has_number_of_rows_pred, has_size_in_bytes_pred, has_actual_column_names_pred,
        is_next_version_of_pred, was_produced_by_pred, has_doi_pred
    ))
    data_objects = cursor.fetchall()

    print(f"\nFound {len(data_objects)} data objects with hasObjectSpec predicate")

    batch_size = 100000
    total_inserted = 0
    data_to_insert = []
    insertion_query = "INSERT INTO data_objects (subject, hasObjectSpec, name, acquisition_start_time, acquisition_end_time, acquisition_wasAssociatedWith, acquisition_hasSamplingHeight, submission_start_time, submission_end_time, data_start_time, data_end_time, hasSha256sum, hasNumberOfRows, hasSizeInBytes, hasActualColumnNames, isNextVersionOf, wasProducedBy, hasDoi) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"

    for row in data_objects:
        subject, object_spec_uri, name, acquisition_uri, submission_uri, data_start, data_end, sha256sum, number_of_rows, size_in_bytes, column_names, next_version_of_uri, produced_by, doi = row

        # Convert numeric fields
        if number_of_rows:
            try:
                number_of_rows = int(number_of_rows)
            except (ValueError, TypeError):
                number_of_rows = None

        if size_in_bytes:
            try:
                size_in_bytes = int(size_in_bytes)
            except (ValueError, TypeError):
                size_in_bytes = None

        # Store isNextVersionOf URI as None for now - will be resolved in second pass
        next_version_of_id = None
        # Get temporal data from acquisition and submission triples
        acq_start, acq_end = get_temporal_data(cursor, acquisition_uri)
        acq_station, acq_sampling_height = get_acquisition_metadata(cursor, acquisition_uri)
        sub_start, sub_end = get_temporal_data(cursor, submission_uri)

        data_to_insert.append((
            subject, object_spec_uri, name,
            acq_start, acq_end, acq_station, acq_sampling_height,
            sub_start, sub_end, data_start, data_end,
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
        SELECT e.subj, e.obj as next_version_uri
        FROM rdf_triples e
        JOIN data_objects data_object ON e.subj = data_object.subject
        WHERE e.pred = %s
    """, (is_next_version_of_pred,))

    updates_to_make = []
    for subject, next_version_uri in cursor.fetchall():
        if next_version_uri:
            referenced_id = subject_to_id_mapping.get(next_version_uri)
            if referenced_id:
                updates_to_make.append((referenced_id, subject))
            else:
                print(f"Warning: isNextVersionOf reference not found: {next_version_uri}")

    print(f"Updating {len(updates_to_make)} isNextVersionOf foreign key relationships...")

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
        SELECT e.subj, e.obj as next_version_uri
        FROM rdf_triples e
        JOIN data_objects data_object ON e.subj = data_object.subject
        WHERE e.pred = %s
    """, (is_next_version_of_pred,))

    updates_to_make = []
    for subject, next_version_uri in cursor.fetchall():
        if next_version_uri:
            referenced_id = subject_to_id_mapping.get(next_version_uri)
            if referenced_id:
                updates_to_make.append((referenced_id, subject))
            else:
                print(f"Warning: isNextVersionOf reference not found: {next_version_uri}")

    print(f"Updating {len(updates_to_make)} isNextVersionOf foreign key relationships...")

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
        SELECT subj, obj as keywords_str
        FROM rdf_triples
        WHERE pred = %s
    """

    cursor.execute(query, (keywords_pred,))
    triples_with_keywords = cursor.fetchall()

    print(f"\nFound {len(triples_with_keywords)} triples with keywords")

    # Dictionary to cache keyword IDs
    keyword_to_id = {}

    triple_keyword_pairs = []

    for subject, keywords_str in triples_with_keywords:
        if keywords_str:
            # Parse comma-separated keywords
            keywords = [k.strip() for k in str(keywords_str).split(',') if k.strip()]

            for keyword in keywords:
                # Get or create keyword ID
                if keyword not in keyword_to_id:
                    # Try to insert the keyword
                    cursor.execute("INSERT INTO keywords (keyword) VALUES (%s) ON CONFLICT (keyword) DO NOTHING", (keyword,))
                    cursor.execute("SELECT id FROM keywords WHERE keyword = %s", (keyword,))
                    keyword_to_id[keyword] = cursor.fetchone()[0]

                triple_keyword_pairs.append((subject, keyword_to_id[keyword]))

    execute_batch(
        cursor,
        "INSERT INTO triple_keywords (subject, keyword_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
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
        SELECT k.keyword, COUNT(ek.subject) as count
        FROM keywords k
        JOIN triple_keywords ek ON k.id = ek.keyword_id
        GROUP BY k.keyword
        ORDER BY count DESC
        LIMIT 10
    """)

    print("\nTop 10 keywords by frequency:")
    for keyword, count in cursor.fetchall():
        print(f"  {keyword}: {count} triples")

    # Populate project keywords if projects table exists
    cursor.execute("""
        SELECT COUNT(*) FROM information_schema.tables
        WHERE table_name = 'projects'
    """)
    if cursor.fetchone()[0] > 0:
        populate_project_keywords(conn, keyword_to_id)

    return unique_keywords

def populate_project_keywords(conn, keyword_to_id):
    cursor = conn.cursor()

    keywords_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasKeywords"

    # Get all projects with keywords (only those that exist in rdf_triples)
    query = """
        SELECT p.id, p.subject, e.obj as keywords_str
        FROM projects p
        JOIN rdf_triples e ON p.subject = e.subj
        WHERE e.pred = %s
    """

    cursor.execute(query, (keywords_pred,))
    projects_with_keywords = cursor.fetchall()

    print(f"\nFound {len(projects_with_keywords)} projects with keywords")

    # Process each project's keywords
    project_keyword_pairs = []

    for project_id, subject, keywords_str in projects_with_keywords:
        if keywords_str:
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

def populate_projects_table(conn):
    print("Populating projects")
    cursor = conn.cursor()

    associated_project_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject"
    name_pred = "http://meta.icos-cp.eu/ontologies/cpmeta/hasName"

    # Get all unique project URIs from data object specs
    query = """
        SELECT DISTINCT e.obj as project_uri
        FROM data_objects dobj
        JOIN rdf_triples e ON dobj.hasObjectSpec = e.subj
        WHERE e.pred = %s
    """

    cursor.execute(query, (associated_project_pred,))
    project_uris_raw = cursor.fetchall()

    # Collect unique project URIs
    unique_project_uris = set()
    for row in project_uris_raw:
        project_uri = row[0]
        if project_uri:
            unique_project_uris.add(project_uri)

    print(f"\nFound {len(unique_project_uris)} unique project URIs")

    # Insert projects
    total_inserted = 0
    for project_uri in unique_project_uris:
        # Check if this project exists in rdf_triples to get name
        cursor.execute("SELECT obj FROM rdf_triples WHERE subj = %s AND pred = %s", (project_uri, name_pred))
        name_row = cursor.fetchone()

        name = name_row[0] if name_row else None

        cursor.execute(
            "INSERT INTO projects (subject, name) VALUES (%s, %s) ON CONFLICT (subject) DO NOTHING",
            (project_uri, name)
        )
        total_inserted += 1

    conn.commit()
    print(f"Total projects inserted: {total_inserted}")

    # Return mapping of project URI to project ID
    cursor.execute("SELECT subject, id FROM projects")
    project_mapping = dict(cursor.fetchall())

    return project_mapping

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
            WHERE ek.subject = data_object.subject

            UNION

            -- Keywords from object spec (using hasObjectSpec URI to look up in triple_keywords)
            SELECT tk.keyword_id
            FROM triple_keywords tk
            WHERE tk.subject = data_object.hasObjectSpec

            UNION

            -- Keywords from associated projects (querying rdf_triples for hasAssociatedProject)
            SELECT pk.keyword_id
            FROM project_keywords pk
            JOIN projects p ON pk.project_id = p.id
            WHERE p.subject IN (
                SELECT obj
                FROM rdf_triples
                WHERE subj = data_object.hasObjectSpec
                AND pred = 'http://meta.icos-cp.eu/ontologies/cpmeta/hasAssociatedProject'
            )
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

def populate_dependent(max_data_objects=None):
    conn = get_connection()
    project_mapping = populate_projects_table(conn)
    populate_data_objects_table(conn, max_data_objects)
    update_next_version(conn)
    populate_keywords_tables(conn)
    populate_aggregate_keywords(conn)

    print("Creating indices")
    execute_sql_file(conn, "psql/create_indices.sql")

def rebuild_dependent(max_data_objects=None):
    recreate_dependent_tables()
    populate_dependent(max_data_objects)


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
        help="Rebuild all tables except rdf_triples (projects + data_objects + keywords + aggregated)"
    )

    rebuild_group.add_argument(
        "--create-tables",
        action="store_true"
    )

    parser.add_argument(
        "--max-data-objects",
        type=int,
        metavar="N",
        help="Limit the maximum number of data object entries to process"
    )

    args = parser.parse_args()

    if args.dependent:
        rebuild_dependent(args.max_data_objects)
    elif args.create_tables:
        recreate_dependent_tables()

if __name__ == "__main__":
    main()
