#!/usr/bin/env python3
"""
Script to decode ASCII escape sequences in sparql.log file.
Reads sparql.log and writes decoded output to sparql_decoded.log.
"""

import codecs

def decode_escape_sequences(input_file, output_file):
    """
    Read a file with ASCII escape sequences and write decoded output.

    Args:
        input_file: Path to input file with escape sequences
        output_file: Path to output file for decoded content
    """
    try:
        # Read the input file
        with open(input_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Decode escape sequences using Python's codecs module
        decoded_content = codecs.decode(content, 'unicode_escape')

        # Write the decoded content to output file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(decoded_content)

        print(f"Successfully decoded {input_file}")
        print(f"Output written to {output_file}")

    except FileNotFoundError:
        print(f"Error: Could not find {input_file}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    input_file = "sparql.log"
    output_file = "sparql_decoded.log"

    decode_escape_sequences(input_file, output_file)
