#!/usr/bin/env bash
printf "" > mappings.obda

{ \
  printf "[PrefixDeclaration]\n" ;\
  cat ./generated_prefixes.txt; \
  printf "\n[MappingDeclaration] @collection [[\n\n" ;\
  cat ./tables.obda ;\
  cat ./generated_triples.obda ;\
  printf "]]"
} >> mappings.obda
