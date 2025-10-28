#!/usr/bin/env bash

PGPASSWORD=ontop psql --host localhost --port 5432 -U postgres -d postgres -a -f "$1"
