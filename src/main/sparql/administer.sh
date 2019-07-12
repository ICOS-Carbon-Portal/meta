#!/bin/bash
curl -X POST -H "Cookie: cpauthToken=" -d @purgeColl.rq https://meta.icos-cp.eu/admin/delete/icoscolls?dryRun=false

