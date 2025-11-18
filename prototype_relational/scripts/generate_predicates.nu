#!/usr/bin/env nu

cat predicate_counts.csv | parse '{pred} | {count}' | upsert pred {split column ':' 'prefix' 'pred'} | flatten | flatten | save -f 'predicates.csv'
