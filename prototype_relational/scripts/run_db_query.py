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

def get_connection():
    """Create and return a PostgreSQL database connection."""
    return psycopg2.connect(
        host="localhost",
        user="postgres",
        port=5432,
        password="ontop"
    )


conn = get_connection()
cursor = conn.cursor()
# started_at_time_pred = "http://www.w3.org/ns/prov#startedAtTime"
# ended_at_time_pred = "http://www.w3.org/ns/prov#endedAtTime"
# subject = "https://meta.fieldsites.se/objects/8uNF3qqyXBclYG7mBFj9Adn4"
label = "http://www.w3.org/2000/01/rdf-schema#label"
cursor.execute("""
    SELECT pred, obj as label
    FROM rdf_triples
    WHERE pred = %s
    LIMIT 10
""", (label,))

result = cursor.fetchall()
print(result)
