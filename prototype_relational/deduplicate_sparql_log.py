#!/usr/bin/env python3
"""
Script to deduplicate SPARQL log entries.
Reads sparql_partial.log and outputs unique queries to a new file.
"""

def deduplicate_sparql_log(input_file, output_file='sparql_deduplicated.log'):
    """Read SPARQL log and write deduplicated entries."""
    seen = set()
    unique_entries = []
    total_count = 0
    duplicate_count = 0

    with open(input_file, 'r') as f:
        for line in f:
            content = line.strip()

            # Skip empty lines
            if not content:
                continue

            total_count += 1

            # Handle separator lines separately
            if content == '-':
                if '-' not in seen:
                    unique_entries.append(content)
                    seen.add('-')
                continue

            # Check if we've seen this query before
            if content not in seen:
                unique_entries.append(content)
                seen.add(content)
            else:
                duplicate_count += 1

    # Write deduplicated entries to output file
    with open(output_file, 'w') as f:
        for entry in unique_entries:
            f.write(entry + '\n')

    # Print statistics
    unique_count = len(unique_entries)
    print(f"Total entries: {total_count}")
    print(f"Unique entries: {unique_count}")
    print(f"Duplicates removed: {duplicate_count}")
    print(f"Deduplicated log written to: {output_file}")

if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1:
        input_file = sys.argv[1]
        output_file = sys.argv[2] if len(sys.argv) > 2 else 'sparql_deduplicated.log'
    else:
        input_file = 'sparql_partial.log'
        output_file = 'sparql_deduplicated.log'

    deduplicate_sparql_log(input_file, output_file)
