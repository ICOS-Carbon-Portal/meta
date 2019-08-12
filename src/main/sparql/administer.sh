#!/bin/bash
curl -X POST -H "Cookie: cpauthToken=" -d @insertL1Deprecation.rq https://meta.icos-cp.eu/admin/insert/atmprodcsv?dryRun=false

