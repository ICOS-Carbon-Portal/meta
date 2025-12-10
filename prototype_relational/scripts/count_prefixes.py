#!/usr/bin/env python

import duckdb
import sys
sys.path.insert(0, "..")
from db_connection import get_connection
import json
from datetime import datetime
from collections import defaultdict





def find_all_common_prefixes(subjects, min_count=50):
    """
    Find all common prefixes among subjects at natural boundaries.
    This includes nested prefixes beyond just the last '/' delimiter.

    Args:
        subjects: List of subject strings
        min_count: Minimum number of subjects required for a valid prefix (default: 10)
    """
    delimiters = ['/', '-', '_', '.', ':', '#']
    prefix_counts = defaultdict(set)

    for subject in subjects:
        # Find all delimiter positions
        positions = []
        for i, char in enumerate(subject):
            if char in delimiters:
                positions.append(i + 1)

        # Consider prefixes up to each delimiter position
        for pos in positions:
            if pos > 0:
                prefix = subject[:pos]
                prefix_counts[prefix].add(subject)

    # Convert to counts and filter to only those with min_count+ subjects
    result = {}
    for prefix, subject_set in prefix_counts.items():
        if len(subject_set) >= min_count:
            result[prefix] = len(subject_set)

    return result


def filter_to_leaf_prefixes(prefix_counts):
    """
    Filter to only leaf prefixes - prefixes that have subjects not accounted
    for by any longer prefixes.

    A prefix is a leaf if its count is greater than the sum of counts of all
    longer prefixes that start with it.
    """
    leaf_prefixes = {}

    # Sort prefixes by length to process shorter ones first
    sorted_prefixes = sorted(prefix_counts.keys(), key=len)

    for prefix in sorted_prefixes:
        # Find all longer prefixes that start with this prefix
        child_count = 0
        for other_prefix in prefix_counts:
            if other_prefix != prefix and other_prefix.startswith(prefix):
                child_count += prefix_counts[other_prefix]

        # If the counts don't match, this prefix has unique subjects (it's a leaf)
        if child_count < prefix_counts[prefix]:
            leaf_prefixes[prefix] = prefix_counts[prefix]

    return leaf_prefixes


def fetch_unique_subject_prefixes():
    """Fetch all unique subjects from rdf_triples and extract their prefixes."""
    conn = get_connection()
    cursor = conn.cursor()

    # Fetch all unique subjects
    query = """
        SELECT DISTINCT subj
        FROM rdf_triples;
    """

    print("Querying database for unique subjects...")
    cursor.execute(query)
    subjects = cursor.fetchall()

    cursor.close()
    conn.close()

    # Extract the subject strings from tuples
    subject_list = [subject_tuple[0] for subject_tuple in subjects]

    # Find all common prefixes at natural boundaries
    print("Finding all common prefixes at natural boundaries...")
    all_prefix_counts = find_all_common_prefixes(subject_list)

    # Filter to only leaf prefixes
    print("Filtering to only leaf prefixes...")
    prefix_counts = filter_to_leaf_prefixes(all_prefix_counts)

    # Sort by count (descending), most used prefixes first
    # sorted_prefixes = sorted(prefix_counts.keys(), key=lambda p: prefix_counts[p], reverse=True)
    sorted_prefixes = sorted(prefix_counts.keys())
    return sorted_prefixes, prefix_counts


def save_to_file(prefixes, prefix_counts, filename='subject_prefixes.json'):
    """Save the prefixes to a JSON file."""
    # Create a dictionary with counts for valid prefixes only
    prefix_count_dict = {prefix: prefix_counts[prefix] for prefix in prefixes}

    data = {
        'timestamp': datetime.now().isoformat(),
        'total_unique_prefixes': len(prefixes),
        'prefix_counts': prefix_count_dict
    }

    with open(filename, 'w') as f:
        json.dump(data, f, indent=2)

    print(f"\nResults saved to {filename}")


def main():
    print("Fetching unique subject prefixes from the rdf_triples table...")
    print("Finding leaf prefixes at delimiter boundaries (/, -, _, ., :, #)")
    print("Minimum 1000 subjects required for a valid prefix")
    print("Leaf prefixes = prefixes with subjects not fully covered by longer prefixes")
    print("=" * 80)

    prefixes, prefix_counts = fetch_unique_subject_prefixes()

    print(f"\nTotal leaf prefixes found: {len(prefixes)}\n")
    print("Sample prefixes with counts:")
    print("-" * 80)

    # Show first 15 prefixes as a sample
    for prefix in prefixes[:15]:
        count = prefix_counts[prefix]
        print(f"{prefix} ({count} subjects)")

    if len(prefixes) > 15:
        print(f"... and {len(prefixes) - 15} more")

    # Save to file
    save_to_file(prefixes, prefix_counts)


if __name__ == "__main__":
    main()
