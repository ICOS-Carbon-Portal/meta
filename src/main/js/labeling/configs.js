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

export const etc = {
	step1Doc: 'https://static.icos-cp.eu/share/stations/docs/labeling/etcLabeling20151030.pdf',
	windDirectionFileType1: 'Wind direction (sectors/months)',
	windDirectionFileType2: 'Wind direction (time series)'
};

export const themeToFiles = {
	Atmosphere: [
		{min: 1, max: 20, type: "Photo(s) of the site"},
		{min: 1, max: 1, type: "Station map", tip: "Map centered on the station (at the 1km/3000ft scale)"},
		{min: 1, max: 1, type: "Prevailing wind directions", tip: "Prevailing wind directions or better seasonal wind roses (include reference to the meteo data used)"},
		{min: 1, max: 1, type: "Distribution system diagram", tip: "Distribution system diagram: plumbing and Instrumentation Diagram (if available)"},
		{min: 0, max: 1, type: "Further comments", tip: "Further comments (voluntary)"},
		{min: 0, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ecosystem: [
		{min: 1, max: 1, type: "Basic site information", tip: "Ecosystem type, management, recent disturbances, site description, etc."},
		{min: 1, max: 1, type: "Power availability", tip: "Source and kW available in total"},
		{min: 1, max: 1, type: "Internet connection", tip: "Internet connection, system and capacity (speed, robustness)"},
		{min: 1, max: 1, type: "Other projects", tip: "Sharing of the facility with other initiatives, networks, and projects"},
		{min: 0, max: 1, type: etc.windDirectionFileType1, tip: "XX-YYY_Wind.ext (Excel or ASCII), see §6 of the document"},
		{min: 0, max: 1, type: etc.windDirectionFileType2, tip: "XX-YYY_WindData.ext (Excel or ASCII), see §6 of the document"},
		{min: 1, max: 3, type: "DEM", tip: "Digital elevation model of an area 3x3 km around the tower"},
		{min: 1, max: 3, type: "High resolution image", tip: "High resolution aerial or satellite color image of an area 3x3 km around the tower"},
		{min: 1, max: 3, type: "Vegetation map", tip: "Vegetation map of the 3x3 km around the tower"},

		{min: 12, max: 12, type: "30-degrees photo outside canopy", tip: "12 photos (every 30 degrees starting from North) taken from the tower position (§11)"},
		{min: 0, max: 12, type: "30-degrees photo below canopy", tip: "Same as \"30-degrees photo outside canopy\", applicable to forest sites only (§11)"},
		{min: 0, max: 4, type: "4-direction photo", tip: "Forest sites: from the tower top looking down; non-forest: 4 photos of the tower"},
		{min: 0, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ocean: [
		{min: 0, max: 1, type: "Geographical coverage", tip: "Format to be defined by Benjamin Pfeil"},
		{min: 0, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	]
};

let commonPropNames = ['hasLat', 'hasLon', 'hasApplicationStatus'];

let propNames = {
	Atmosphere: ['hasShortName', 'hasLongName', 'hasMainPersonnelNamesList', 'hasResponsibleInstitutionName',
		'hasStationClass', 'hasAddress', 'hasWebsite', 'hasElevationAboveGround',
		'hasElevationAboveSea', 'hasAccessibility', 'hasVegetation', 'hasAnthropogenics',
		'hasConstructionStartDate', 'hasConstructionEndDate', 'hasOperationalDateEstimate',
		'hasTelecom', 'hasExistingInfrastructure', 'hasNameListOfNetworksItBelongsTo'],

	Ecosystem: ['hasStationClass', 'hasAnemometerDirection', 'hasEddyHeight', 'hasWindDataInEuropeanDatabase'],

	Ocean: ['hasMainPersonnelNamesList', 'hasPlatformType', 'hasTypeOfSampling', 'hasShortName', 'hasLongName',
		'hasCountry', 'hasVesselOwner', 'hasLocationDescription',
		'hasWesternmostLon', 'hasEasternmostLon', 'hasNothernmostLat', 'hasSouthernmostLat']
};

export function themeToProperties(theme){
	return commonPropNames.concat(propNames[theme]);
};

export const countryCodes = {
	BE:"Belgium",
	CH:"Switzerland",
	CZ:"Czech Republic",
	DE:"Germany",
	FI:"Finland",
	FR:"France",
	GF:"French Guiana",
	IE:"Ireland",
	IT:"Italy",
	NL:"Netherlands",
	NO:"Norway",
	PL:"Poland",
	SE:"Sweden",
	GB:"United Kingdom of Great Britain and Northern Ireland"
};

