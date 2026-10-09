#!/usr/bin/env python3
"""
Mirror selected ICOS data objects on the SITES data portal.

For every ICOS data object of the configured data types and stations, build a
SITES-shaped DTO pointing back to the ICOS landing page, save it as JSON,
register it on the SITES metadata service and upload the data file to the SITES
data service. Objects already completed on SITES only get their metadata
updated.

Usage:
    conda run -n icoscp-pylibs python3 icos2sites.py [--meta-url URL] [--token COOKIE] [--dry-run] [--insecure]

Authentication on SITES uses the cookie given with --token (for example
'fieldsitesToken=...') or, if omitted, the icoscp_core password file for SITES.
Downloading from ICOS uses the icoscp_core password file for ICOS.
"""

import argparse
import json
import os
import sys

import requests

from icoscp_core.icos import data, meta
from icoscp_core.metacore import DataObject, StationTimeSeriesMeta
from icoscp_core.queries.dataobjlist import DataObjectLite
from typing import Any, TypeAlias


PointCoordinates: TypeAlias = dict[str, float]
Polygon: TypeAlias = list[list[int]]
Geometry: TypeAlias = dict[str, str | list[Polygon]]
GeoFeature: TypeAlias = dict[str, str | Geometry | dict[str, str]]
Feature: TypeAlias = dict[str, str | list[PointCoordinates] | GeoFeature]
FeatureWithGeoJson: TypeAlias = dict[str, str | Geometry | GeoFeature | Feature]


DEFAULT_SITES_META_URL = 'https://meta.fieldsites.se'

# Host of the metadata object URIs. A replicated ICOS object keeps its hash id
# on the SITES side, so its stored URI differs only by host.
SITES_META_HOST = 'meta.fieldsites.se'

# ICOS targets
DATATYPE_URIS = [
    'http://meta.icos-cp.eu/resources/cpmeta/etcL2Meteo'
]

# Station mapping: ICOS station ID to SITES constants
STATION_MAP = {
    'ES_SE-Sto': {
        'sites_uri': 'https://meta.fieldsites.se/resources/stations/abisko',
        'sites_site_uri': 'https://meta.fieldsites.se/resources/sites/stordalen-mire',
        'area_uri': 'https://meta.fieldsites.se/resources/areas/stordalen',
        'area_label': 'Stordalen'
    },
    #'ES_SE-Deg': {
    #    'sites_uri': 'https://meta.fieldsites.se/resources/stations/Svartberget',
    #    'area_uri': 'https://meta.fieldsites.se/resources/areas/degero',
    #    'area_label': 'Degerö'
    #},
    #'ES_SE-Svb': {
    #    'sites_uri': 'https://meta.fieldsites.se/resources/stations/Svartberget',
    #    'area_uri': 'https://meta.fieldsites.se/resources/areas/svartberget',
    #    'area_label': 'Svartberget'
    #},
    #'ES_SE-Myc': {
    #    'sites_uri': 'https://meta.fieldsites.se/resources/stations/Skogaryd',
    #    'area_uri': 'https://meta.fieldsites.se/resources/areas/mycklemossen',
    #    'area_label': 'Mycklemossen'
    #}
}

# Fixed SITES fields
OBJECT_SPEC = 'https://meta.fieldsites.se/resources/objspecs/icos'
LICENCE     = 'http://meta.icos-cp.eu/ontologies/cpmeta/icosLicence'
SUBMITTER   = 'SITES'
KEYWORDS    = ['ICOS']

DTO_OUTPUT_DIR = os.path.join('output', 'dtos')
DATA_OUTPUT_DIR = os.path.join('output', 'data')

PAGE_SIZE = 500


class SitesClient:
    def __init__(self, meta_url: str, token: str, verify: bool) -> None:
        self.meta_url = meta_url.rstrip('/')
        self._session = requests.Session()
        self._session.headers['Cookie'] = token
        self._session.verify = verify

    def upload_completed(self, hash_id: str) -> bool | None:
        obj_uri = f'https://{SITES_META_HOST}/objects/{hash_id}'
        query = f'''
            PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
            PREFIX prov: <http://www.w3.org/ns/prov#>
            SELECT ?stop WHERE {{
                <{obj_uri}> cpmeta:hasSha256sum ?hash .
                OPTIONAL {{ <{obj_uri}> cpmeta:wasSubmittedBy/prov:endedAtTime ?stop }}
            }}
        '''
        resp = self._session.post(
            f'{self.meta_url}/sparql',
            headers={'Content-Type': 'text/plain', 'Accept': 'application/sparql-results+json'},
            data=query.encode('utf-8')
        )
        if not resp.ok:
            raise Exception(f'Looking up {obj_uri} on SITES failed: {resp.status_code} {resp.text}')
        bindings = resp.json()['results']['bindings']
        if not bindings:
            return None
        return any('stop' in binding for binding in bindings)

    def register(self, dto: dict[str, Any]) -> str:
        resp = self._session.post(f'{self.meta_url}/upload', json=dto)
        if not resp.ok:
            raise Exception(f'Metadata upload failed: {resp.status_code} {resp.text}')
        return resp.text.strip()

    def upload_data(self, upload_url: str, file_path: str) -> None:
        with open(file_path, 'rb') as f:
            resp = self._session.put(upload_url, data=f)
        if not resp.ok:
            raise Exception(f'Data upload to {upload_url} failed: {resp.status_code} {resp.text}')


def list_icos_dobjs() -> list[DataObjectLite]:
    station_url_prefix = 'http://meta.icos-cp.eu/resources/stations/'
    stations = [f'{station_url_prefix}{station_id}' for station_id in STATION_MAP.keys()]
    dobjs: list[DataObjectLite] = []
    while True:
        page = meta.list_data_objects(
            datatype=DATATYPE_URIS,
            station=stations,
            include_deprecated=False,
            limit=PAGE_SIZE,
            offset=len(dobjs)
        )
        dobjs.extend(page)
        if len(page) < PAGE_SIZE:
            return dobjs


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


def build_spatial(polygon: Polygon, label: str) -> FeatureWithGeoJson:
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
            'vertices': vertices,
        },
        'geo': geo_feature,
        'geoJson': json.dumps(geometry),
    }


def hash_id(uri: str) -> str:
    return uri.rstrip('/').split('/')[-1]


def as_list(uris: str | list[str] | None) -> list[str]:
    if uris is None:
        return []
    return [uris] if isinstance(uris, str) else uris


def build_dto(
        dobj_lite: DataObjectLite,
        dobj: DataObject,
        sites_station_constants: dict[str, str],
        spatial: FeatureWithGeoJson,
        previous_versions: list[str]) -> dict[str, Any]:
    si = dobj.specificInfo
    if not isinstance(si, StationTimeSeriesMeta):
        raise Exception('not a station time series data object')
    landing_page = dobj_lite.uri

    specific_info: dict[str, Any] = {
        'station': sites_station_constants['sites_uri'],
        'site': sites_station_constants['sites_site_uri'],
        'spatial': spatial,
        'customLandingPage': landing_page,
    }
    if si.acquisition.interval is not None:
        specific_info['acquisitionInterval'] = {
            'start': si.acquisition.interval.start,
            'stop': si.acquisition.interval.stop,
        }
    if si.productionInfo is not None:
        specific_info['production'] = {
            'comment': f'ICOS ETC data hosted on ICOS Carbon Portal: {landing_page}',
            'contributors': [],
            'creationDate': si.productionInfo.dateTime,
            'creator': sites_station_constants['sites_uri'],
            'sources': [],
        }

    return {
        'fileName': dobj.fileName,
        'hashSum': dobj.hash,
        'isNextVersionOf': previous_versions,
        'objectSpecification': OBJECT_SPEC,
        'references': {
            'keywords': KEYWORDS,
            'licence': LICENCE,
        },
        'specificInfo': specific_info,
        'submitterId': SUBMITTER,
    }


def mirror_dobj(sites: SitesClient, dobj_lite: DataObjectLite, dto: dict[str, Any], completed: bool | None) -> None:
    upload_url = sites.register(dto)
    if completed:
        print(f'  Updated metadata of {dobj_lite.filename}, data already uploaded')
        return
    file_name = data.save_to_folder(dobj_lite, DATA_OUTPUT_DIR)
    file_path = os.path.join(DATA_OUTPUT_DIR, file_name)
    try:
        sites.upload_data(upload_url, file_path)
    finally:
        os.remove(file_path)
    print(f'  Uploaded {dobj_lite.filename} to {upload_url}')


def sites_token(token: str | None) -> str:
    if token is not None:
        return token
    from icoscp_core.sites import auth as sites_auth
    return sites_auth.get_token().cookie_value


def main(meta_url: str, token: str | None, dry_run: bool, verify: bool) -> int:
    os.makedirs(DTO_OUTPUT_DIR, exist_ok=True)
    os.makedirs(DATA_OUTPUT_DIR, exist_ok=True)

    sites = None if dry_run else SitesClient(meta_url, sites_token(token), verify)

    dobjs_all = list_icos_dobjs()
    print(f'Found {len(dobjs_all)} data object(s).')

    spatial_by_area: dict[str, FeatureWithGeoJson] = {}
    failures: list[str] = []

    for dobj_lite in dobjs_all:
        station_id = hash_id(dobj_lite.station_uri) if dobj_lite.station_uri is not None else ''
        if station_id not in STATION_MAP:
            print(f'  SKIP {dobj_lite.filename}: unknown station {station_id}')
            continue
        sites_station_constants = STATION_MAP[station_id]
        area_uri = sites_station_constants['area_uri']
        if area_uri not in spatial_by_area:
            spatial_by_area[area_uri] = build_spatial(
                get_polygon_coordinates(area_uri),
                sites_station_constants['area_label']
            )

        try:
            dobj = meta.get_dobj_meta(dobj_lite.uri)
            completed = None if sites is None else sites.upload_completed(hash_id(dobj_lite.uri))
            previous_versions = [
                hash_id(prev) for prev in as_list(dobj.previousVersion)
                if sites is not None and sites.upload_completed(hash_id(prev)) is not None
            ]
            dto = build_dto(dobj_lite, dobj, sites_station_constants, spatial_by_area[area_uri], previous_versions)

            out_path = os.path.join(DTO_OUTPUT_DIR, dobj.fileName + '.json')
            with open(out_path, 'w') as f:
                json.dump(dto, f, indent=2)
            print(f'  Wrote {out_path}')

            if sites is not None:
                mirror_dobj(sites, dobj_lite, dto, completed)
        except Exception as err:
            print(f'  FAILED {dobj_lite.filename}: {err}')
            failures.append(dobj_lite.filename)

    if failures:
        print(f'{len(failures)} failure(s): {", ".join(failures)}')
        return 1
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Mirror selected ICOS data objects on the SITES data portal.')
    parser.add_argument('--meta-url', default=DEFAULT_SITES_META_URL,
        help=f'base URL of the SITES metadata service (default: {DEFAULT_SITES_META_URL})')
    parser.add_argument('--token',
        help="SITES authentication cookie, e.g. 'fieldsitesToken=...' (default: from the icoscp_core password file)")
    parser.add_argument('--dry-run', action='store_true',
        help='only write the DTOs, do not upload anything')
    parser.add_argument('--insecure', action='store_true',
        help='do not verify TLS certificates of the SITES services, e.g. for a local development deployment')
    return parser.parse_args()


if __name__ == '__main__':
    args = parse_args()
    sys.exit(main(args.meta_url, args.token, args.dry_run, not args.insecure))
