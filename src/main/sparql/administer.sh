#!/bin/bash

curl -X POST --cookie "cpauthToken=WzE2ODI2ODEyMzQ5NzcsIm9sZWcubWlyem92QG5hdGVrby5sdS5zZSIsIlNhbWwiXR6wFK9A5AYzvInVw/XkAQ6HqLjOq/uyu7ndlxxubNj+42QipFsykil05C5rg31sm8jjd0L8vXltZc823MT5oxsZ9B9tB8r1GkwDDq69MOalXGUm/fr4HDGRbQEcKxWCp1UI9rIMXe/P4/5zNG1Hc8D5FqgyIm3YaBNzWEh8VGTk1ITmsH3/hJCPFeRjKBPm7slbrP6GTjkMhOYo1CO3ca6maoXHVU17jtu/Zle6HdzZ2k+muyP8lqapXvZhlwoLnFmqk0O0uYLYIRlQbmVnb7mVqrgDYXXYMbV82qmnKxvzpmWntT0IXGi5/5ztl6qHtUcDLbWPMrowrzxrM6hUMCg3" -d @purgeDobjs.rq https://meta.icos-cp.eu/admin/delete/otcprodcsv?dryRun=false

#curl --cookie "cpauthToken=..." --data-urlencode "subject=https://meta.icos-cp.eu/objects/I_HZ0N-B0SOWu_hUiD_MMdlG" -G --data-urlencode "predicate=http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes" https://meta.icos-cp.eu/admin/dropRdf4jTripleObjects

