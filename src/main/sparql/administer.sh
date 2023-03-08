#!/bin/bash

curl -X POST --cookie "cpauthToken=WzE2NzgzNjc0MDg0MDgsIm9sZWcubWlyem92QG5hdGVrby5sdS5zZSIsIlNhbWwiXR4OnaiZXwWu/1kM1UiNKIxCNJMBkZz1DCaaCTukGCw/Lo4lWU9lXgEZygJaPFbE212x854Kfd2FvOUCW8qYhljomrHD1t3KkDfxRcMRSozju8g1d5uv5Gh5ifK8Sd5Y9I46wynwZx8KFOju8vXYOklgwefiQFg7FA6V8UxWTn6Qdgy4SKjyeNPvksvtkBwiiuNWhu5Ywl9Dnpbr4WsiknsHt+WUkb4CdZuyiLNYKrPLBMKGF4IPCJDZtmDBw7rq1RyMYekYlWtZwMO5r4BpaCnkap2tQRjKHkuQb2sZErol+hCFSH5+IZGHeBPYXJe2E9PjI9yrjAzdNcAJGMo9eFWF" -d @purgeDobjs.rq https://meta.icos-cp.eu/admin/delete/etcmulti?dryRun=false

#curl --cookie "cpauthToken=..." --data-urlencode "subject=https://meta.icos-cp.eu/objects/I_HZ0N-B0SOWu_hUiD_MMdlG" -G --data-urlencode "predicate=http://meta.icos-cp.eu/ontologies/cpmeta/hasSizeInBytes" https://meta.icos-cp.eu/admin/dropRdf4jTripleObjects

