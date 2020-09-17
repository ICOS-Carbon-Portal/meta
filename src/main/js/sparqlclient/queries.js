import {stationPis, etcClass1And2Pis} from './queries/stationPis';
import {stationsTable} from './queries/stationsTable';
import {activeStations} from './queries/activeStations';
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

const queries = [
	{name:"Table of stations", query: stationsTable},
	{name:"Provisional stations' PIs", query: stationPis},
	{name:"Stations' labelling status", query: labelingStatus},
	{name:"Stations that used the labelling app", query: activeStations},
	{name:"PIs of \"labelling-inactive\" stations", query: inactivePis},
	{name:"Last 1000 data objects", query: lastDataObjects},
	{name:"Search with sampling heights", query: bySamplingHeights},
	{name:"Re-submitted files", query: resubmittedFiles},
	{name:"Collections", query: existingCollections},
	{name:"Documents", query: existingDocuments},
	{name:"Data object counts per format", query: perFormatStats},
	{name:"OTC SOCAT polygon-approximated tracks", query: concaveHulls},
	{name:"ETC, class 1 and 2 PIs", query: etcClass1And2Pis},
	{name:"ETC, labelling app form values", query: etcLabelingValues}
];

export default queries;

