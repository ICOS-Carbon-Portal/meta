#!/usr/bin/env python3
"""
Script to format SPARQL log entries with proper newlines.
Reads sparql_partial.log and outputs each entry with proper formatting.
"""

def format_sparql_log(input_file):
    """Read and format SPARQL log entries."""
    with open(input_file, 'r') as f:
        for line in f:
            # Remove the leading line number if present (format: "N→")
            if '→' in line:
                content = line.split('→', 1)[1].strip()
            else:
                content = line.strip()

            # Skip empty lines
            if not content:
                continue

            # Handle separator lines
            if content == '-':
                print('\n' + '='*80 + '\n')
                continue

            # Replace escape sequences
            formatted = content.replace('\\x0A', '\n')
            formatted = formatted.replace('\\x09', '\t')
            formatted = formatted.replace('\\x22', '"')

            # Print the formatted query
            print(formatted)
            print('\n' + '-'*80 + '\n')

if __name__ == '__main__':
    import sys
    format_sparql_log(sys.argv[1])
