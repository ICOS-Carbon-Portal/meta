import {badmEntries} from './queries/badmEntries';
import {stationPis, etcClass1And2Pis} from './queries/stationPis';
import {stationsTable} from './queries/stationsTable';
import {activeStations} from './queries/activeStations';
import {etcLabelingValues} from './queries/etcLabelingValues';

const queries = [
	{name:"Table of stations", query: stationsTable},
	{name:"Provisional stations' PIs", query: stationPis},
	{name:"Stations that used the labeling app", query: activeStations},
	{name:"ETC, class 1 and 2 PIs", query: etcClass1And2Pis},
	{name:"ETC, labeling app form values", query: etcLabelingValues},
	{name:"ETC, BADM ancillary values", query: badmEntries}
];

export default queries;

