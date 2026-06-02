#!/usr/bin/env python3
"""
Fetch ICOS metadata and data for specific ICOS data types and stations
and produce SITES-shaped DTOs. Save DTOs in JSON files and upload ICOS data
on the SITES data portal using SITES-shaped DTOs.

Usage:
    conda run -n icoscp-pylibs python3 icos2sites.py

Outputs one JSON file per data object into output/.
Uploads one data object on the SITES data portal per data object found
on the ICOS data portal.
"""

import json
import requests
import os
import sys

from icoscp_core.icos import data, meta
from icoscp_core.sites import auth as sites_auth
from icoscp_core.metacore import DataObject, SpatioTemporalMeta
from icoscp_core.queries.dataobjlist import DataObjectLite
from typing import Any, TypeAlias


PointCoordinates: TypeAlias = dict[str, float]
Polygon: TypeAlias = list[list[int]]
Geometry: TypeAlias = dict[str, str | list[Polygon]]
GeoFeature: TypeAlias = dict[str, str | Geometry | dict[str, str]]
Feature: TypeAlias = dict[str, str | list[PointCoordinates] | GeoFeature]
FeatureWithGeoJson: TypeAlias = dict[str, str | Geometry | GeoFeature | Feature]


# Constants
SITES_UPLOAD_URL = 'https://locmeta.fieldsites.se/upload'

# ICOS targets
DATATYPE_URIS = [
    'http://meta.icos-cp.eu/resources/cpmeta/etcL2Meteo'
]

# Station mapping: ICOS station ID to SITES constants
STATION_MAP = {
    'ES_SE-Sto': {
        'sites_uri': 'https://meta.fieldsites.se/resources/stations/abisko',
        'area_uri': 'https://meta.fieldsites.se/resources/areas/stordalen',
        'area_label': 'Stordalen'
    },
    'ES_SE-Deg': {
        'sites_uri': 'https://meta.fieldsites.se/resources/stations/Svartberget',
        'area_uri': 'https://meta.fieldsites.se/resources/areas/degero',
        'area_label': 'Degerö'
    },
    'ES_SE-Svb': {
        'sites_uri': 'https://meta.fieldsites.se/resources/stations/Svartberget',
        'area_uri': 'https://meta.fieldsites.se/resources/areas/svartberget',
        'area_label': 'Svartberget'
    },
    'ES_SE-Myc': {
        'sites_uri': 'https://meta.fieldsites.se/resources/stations/Skogaryd',
        'area_uri': 'https://meta.fieldsites.se/resources/areas/mycklemossen',
        'area_label': 'Mycklemossen'
    }
}

# Fixed SITES fields
OBJECT_SPEC = 'https://meta.fieldsites.se/resources/objspecs/project'
LICENCE     = 'https://meta.fieldsites.se/ontologies/sites/sitesLicence'
SUBMITTER   = 'SITES'
KEYWORDS    = ['ICOS']

DTO_OUTPUT_DIR = os.path.join('output', 'dtos')
DATA_OUTPUT_DIR = os.path.join('output', 'data')


def get_datatype_description(datatype_uri: str) -> str:
    results = meta.sparql_select(f'''
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        SELECT ?comment WHERE {{
            <{datatype_uri}> rdfs:comment ?comment .
        }}
    ''')
    if results.bindings:
        comments: list[str] = [binding['comment'].value for binding in results.bindings]
        return '\n\n'.join(comments)
    return ''


def get_polygon_coordinates(area_uri: str) -> list[list[int]]:
    results = meta.sparql_select(f'''
        PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
        SELECT ?polygon WHERE {{
            <{area_uri}> cpmeta:asGeoJSON ?polygon .
        }}
    ''')
    if results.bindings:
        polygon: dict[str, dict[str, str | int]] = json.loads(results.bindings[0]['polygon'].value)
        return polygon['coordinates'][0]
    return []


def build_spatial(polygon: Polygon, label: str, area_uri: str) -> FeatureWithGeoJson:
    vertices: list[PointCoordinates] = [{'lat': c[1], 'lon': c[0]} for c in polygon[:-1]]
    geometry: Geometry = {'coordinates': [polygon], 'type': 'Polygon'}
    geo_feature: GeoFeature = {
        'geometry': geometry,
        'properties': {'label': label},
        'type': 'Feature',
    }
    return {
        '_type': 'FeatureWithGeoJson',
        'feature': {
            '_type': 'Polygon',
            'geo': geo_feature,
            'label': label,
            #'uri': area_uri,
            'vertices': vertices,
        },
        'geo': geo_feature,
        'geoJson': json.dumps(geometry),
    }


def build_dto(
        dobj_lite: DataObjectLite,
        dobj: DataObject,
        sites_station_constants: dict[str, str],
        datatype_description: str) -> Any:
    si = dobj.specificInfo
    landing_page = dobj_lite.uri
    creation_date = si.productionInfo.dateTime if si.productionInfo is not None else ''
    if isinstance(si, SpatioTemporalMeta):
        time_start = si.temporal.interval.start
        time_stop = si.temporal.interval.stop
    else:
        acq = si.acquisition
        time_start = acq.interval.start if acq.interval is not None else ''
        time_stop = acq.interval.stop if acq.interval is not None else ''

    return {
        'fileName': dobj.fileName,
        'hashSum': dobj.hash,
        'isNextVersionOf': [],
        'objectSpecification': OBJECT_SPEC,
        'references': {
            'keywords': KEYWORDS,
            'licence': LICENCE,
        },
        'specificInfo': {
            'description': datatype_description,
            'forStation': sites_station_constants['sites_uri'],
            'production': {
                'comment': f'ICOS ETC data hosted on ICOS Carbon Portal: {landing_page}',
                'contributors': [],
                'creationDate': creation_date,
                'creator': sites_station_constants['sites_uri'],
                'sources': [],
            },
            'spatial': build_spatial(
                get_polygon_coordinates(sites_station_constants['area_uri']),
                sites_station_constants['area_label'],
                sites_station_constants['area_uri'],
            ),
            'temporal': {
                'interval': {
                    'start': time_start,
                    'stop': time_stop,
                }
            },
            'title': dobj.references.title,
            'customLandingPage': landing_page,
        },
        'submitterId': SUBMITTER,
    }


def update_metadata(dto_file: str, token: str) -> None:
    with open(dto_file, 'r') as f:
        dto = json.load(f)
    try:
        resp = requests.post(
            SITES_UPLOAD_URL,
            headers={'Content-Type': 'application/json', 'Cookie': token},
            json=dto,
            verify=False
        )
        print(f'Status code: {resp.status_code}\nResponse: {resp.content.decode()}')
    except Exception as err:
        print(f'An exception occured while POSTing metadata to {SITES_UPLOAD_URL}:\n{err}')


def upload_dobj(dto: Any, token: str) -> None:
    resp = requests.post(
        SITES_UPLOAD_URL,
        headers={'Content-Type': 'application/json', 'Cookie': token},
        json = dto
    )
    if resp.ok:
        dobj_url = resp.content.decode()
        icos_dobj_url = f'https://data.icos-cp.eu/objects/{dto['hashSum'][:24]}'
        data.save_to_folder(icos_dobj_url, DATA_OUTPUT_DIR)
        #with open('')
        requests.put(
            dobj_url,
            headers={'Transfer-Encoding': 'chunked', 'Cookie': token}
        )
    else:
        resp.raise_for_status()


def main(token: str | None):
    os.makedirs(DTO_OUTPUT_DIR, exist_ok=True)
    os.makedirs(DATA_OUTPUT_DIR, exist_ok=True)

    if token is None:
        token = sites_auth.get_token().cookie_value

    station_url_prefix = 'http://meta.icos-cp.eu/resources/stations/'
    dobjs_all = meta.list_data_objects(
        datatype=DATATYPE_URIS,
        station=[f'{station_url_prefix}{station_id}' for station_id in STATION_MAP.keys()],
        include_deprecated=False
    )
    print(f'Found {len(dobjs_all)} data object(s).')

    for dobj_lite in dobjs_all:
        dobj = meta.get_dobj_meta(dobj_lite.uri)
        station_id = dobj_lite.station_uri.rstrip('/').split('/')[-1] if dobj_lite.station_uri is not None else ''
        if station_id not in STATION_MAP:
            print(f'  SKIP {dobj_lite.filename}: unknown station {station_id}')
            continue
        sites_station_constants = STATION_MAP[station_id]
        datatype_description = get_datatype_description(dobj_lite.datatype_uri)
        dto = build_dto(dobj_lite, dobj, sites_station_constants, datatype_description)

        out_path = os.path.join(DTO_OUTPUT_DIR, dobj.fileName + '.json')
        with open(out_path, 'w') as f:
            json.dump(dto, f, indent=2)
        print(f'  Wrote {out_path}')

        #upload_dobj(dto)


if __name__ == '__main__':
    token = sys.argv[1] if len(sys.argv) > 1 else None
    main(token)
