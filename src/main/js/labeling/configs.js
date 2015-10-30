export const baseUri = 'http://meta.icos-cp.eu/ontologies/stationentry/';
export const lblUri = 'http://meta.icos-cp.eu/ontologies/stationlabeling/';
export const ontUri = 'http://meta.icos-cp.eu/ontologies/stationsschema/';
export const filesUri = 'http://meta.icos-cp.eu/files/';

export function stationOwlClassToTheme(owlClass){
	if(owlClass === baseUri + 'AS')
		return 'Atmosphere';
	else if (owlClass === baseUri + 'ES')
		return 'Ecosystem';
	else if (owlClass === baseUri + 'OS')
		return 'Ocean';
};

export const themeGlyphs = {
	Atmosphere: 'cloud',
	Ecosystem: 'leaf',
	Ocean: 'tint'
};
