#!/bin/bash
/usr/bin/time -f "%e" curl -s -o /dev/null -H "Host: meta.icos-cp.eu" http://127.0.0.1:9094$1
