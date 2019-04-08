#!/bin/bash
cat dobjs.txt | sed -e 's/https:\/\/meta.icos-cp.eu//' | xargs -n 1 -P $1 ./fetchLandingPage.sh

