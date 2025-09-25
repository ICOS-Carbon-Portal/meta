#!/usr/bin/env python3
from rdflib import Graph

g = Graph()
g.parse("cpmeta.owl", format="xml")
g.serialize(destination="cpmeta.ttl", format="turtle")
print("Successfully converted cpmeta.owl to cpmeta.ttl")