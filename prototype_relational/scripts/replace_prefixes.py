#!/usr/bin/env python3
"""
Replace full predicate URIs with shortened prefix notation.
Reads from stdin and outputs to stdout.
"""

import sys
import re

# Known prefix mappings
PREFIXES = {
    'http://www.w3.org/1999/02/22-rdf-syntax-ns#': 'rdf',
    'http://www.w3.org/2000/01/rdf-schema#': 'rdfs',
    'http://www.w3.org/2002/07/owl#': 'owl',
    'http://www.w3.org/ns/prov#': 'prov',
    'http://www.w3.org/2001/XMLSchema#': 'xsd',
    'http://meta.icos-cp.eu/ontologies/cpmeta/': 'cpmeta',
    'http://meta.icos-cp.eu/ontologies/otcmeta/': 'otcmeta',
    'http://meta.icos-cp.eu/ontologies/stationentry/': 'cpstation',
    'https://meta.fieldsites.se/ontologies/sites/': 'cpsites',
    'http://meta.icos-cp.eu/files/': 'cpfiles',
    'http://meta.icos-cp.eu/resources/': 'cpres',
    'http://purl.org/dc/terms/': 'terms',
    'http://www.w3.org/ns/dcat#': 'dcat',
    'http://purl.org/dc/elements/1.1/': 'purl-elements',
    'http://purl.org/vocab/vann/': 'purl-vann',
    'http://www.w3.org/ns/ssn/': 'w3ssn',
    'http://www.w3.org/2006/vcard/': 'w3_vcard',
    'http://www.w3.org/ns/locn': 'w3locn',
    'http://www.w3.org/2004/02/skos/core': 'w3skos_core',
    'http://creativecommons.org/ns': 'creativecommons'
}


def replace_predicates(line):
    """Replace full URIs with prefix notation in a line."""
    result = line
    # Sort by length (longest first) to avoid partial replacements
    for uri, prefix in sorted(PREFIXES.items(), key=lambda x: len(x[0]), reverse=True):
        result = result.replace(uri, f'{prefix}:')
    return result


def main():
    """Read from stdin, replace predicates, write to stdout."""
    for line in sys.stdin:
        # Keep the line ending intact
        modified = replace_predicates(line)
        if not modified.startswith(' rdf:_'):
            sys.stdout.write(modified)


if __name__ == '__main__':
    main()
