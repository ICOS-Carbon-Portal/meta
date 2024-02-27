import {provStationPis, prodStationPis} from './queries/stationPis';
import {stationsTable} from './queries/stationsTable';
import {provisionlessProdStations} from './queries/provisionlessProdStations'
import {etcLabelingValues} from './queries/etcLabelingValues';
import {inactivePis} from './queries/inactivePis';
import {lastDataObjects} from './queries/lastDataObjects';
import {labelingStatus} from './queries/labelingStatus';
import {resubmittedFiles} from './queries/resubmittedFiles';
import {existingCollections} from './queries/existingCollections';
import {existingDocuments} from './queries/existingDocuments';
import {concaveHulls} from './queries/concaveHulls';
import {perFormatStats} from './queries/perFormatStats';
import { bySamplingHeights } from './queries/bySamplingHeights';
import { stationRoles } from './queries/stationRoles';
import { dcat } from './queries/dcat.js'
import { sensorsDeployments } from './queries/sensorsDeployments';
import { hoVsTc } from './queries/hoVsTcStationDiscrepancies';
import { atcSpeciesAndHeights } from './queries/atcSpeciesAndHeights';
import { dupL2s } from './queries/duplicateL2s';
import { geoFilter } from './queries/geoFilter.js';

import { stations as sitesStations } from './queries/SITES/stations';
import { locations as sitesLocations } from './queries/SITES/locations';
import { samplingPoints as sitesSamplingPoints } from './queries/SITES/samplingPoints';

const envri = typeof location !== 'undefined' && location.host.indexOf('fieldsites.se') >= 0 ? "SITES" : "ICOS";
const host = envri === "SITES" ? 'meta.fieldsites.se' : 'meta.icos-cp.eu';

const icosQueries = [
	{name:"Table of stations", query: stationsTable},
	{name:"Production stations not linked to provisional ones", query: provisionlessProdStations},
	{name:"Provisional stations and their PIs, entered by HO", query: provStationPis},
	{name:"Production stations and current PIs, provided by TCs", query: prodStationPis},
	{name:"Production stations' people and roles", query: stationRoles},
	{name:"Provisional vs production station metadata differences", query: hoVsTc},
	{name:"Stations' labelling status", query: labelingStatus},
	{name:"PIs (provisional) of \"labelling-inactive\" stations", query: inactivePis},
	{name:"Last 1000 data objects", query: lastDataObjects},
	{name:"Search with sampling heights", query: bySamplingHeights},
	{name:"Re-submitted files", query: resubmittedFiles},
	{name: "Duplicate L2 atmo dobjs (by spec/station/height)", query: dupL2s},
	{name:"Collections", query: existingCollections(host)},
	{name:"Documents", query: existingDocuments(host)},
	{name:"Data object counts per format", query: perFormatStats},
	{name:"DCAT metadata demo", query: dcat},
	{name:"ATC species and sampling heights (in the data)", query: atcSpeciesAndHeights},
	{name:"ETC METEOSENS sensor deployments demo", query: sensorsDeployments},
	{name:"OTC SOCAT polygon-approximated tracks", query: concaveHulls},
	{ name: "ETC, labelling app form values", query: etcLabelingValues },
	{ name: "Geo filter", query: geoFilter(host) }
];
const sitesQueries = [
	{ name: "Stations", query: sitesStations },
	{ name: "Last 1000 data objects", query: lastDataObjects },
	{ name: "Collections", query: existingCollections(host) },
	{ name: "Documents", query: existingDocuments(host) },
	{ name: "Locations", query: sitesLocations },
	{ name: "Sampling points", query: sitesSamplingPoints },
	{ name: "Geo filter", query: geoFilter(host) }
]
const queries = envri === "SITES" ? sitesQueries : icosQueries;

export default queries;

