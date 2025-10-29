#!/usr/bin/env nu

open class_predicates_analysis.json | select 'classes' | get 'classes' | reject class_uri |  sort-by instance_count --reverse | explore
