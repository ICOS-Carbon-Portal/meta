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


const queries = [
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
	{name:"Collections", query: existingCollections},
	{name:"Documents", query: existingDocuments},
	{name:"Data object counts per format", query: perFormatStats},
	{name:"DCAT metadata demo", query: dcat},
	{name:"ETC METEOSENS sensor deployments demo", query: sensorsDeployments},
	{name:"OTC SOCAT polygon-approximated tracks", query: concaveHulls},
	{name:"ETC, labelling app form values", query: etcLabelingValues}
];

export default queries;

