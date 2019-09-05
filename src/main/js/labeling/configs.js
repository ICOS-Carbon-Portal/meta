export const baseUri = 'http://meta.icos-cp.eu/resources/stationentry/';
export const lblUri = 'http://meta.icos-cp.eu/resources/stationlabeling/';
export const ontUri = 'http://meta.icos-cp.eu/ontologies/stationentry/';
export const filesUri = 'http://meta.icos-cp.eu/files/';

export function stationOwlClassToTheme(owlClass){
	if(owlClass === ontUri + 'AS')
		return 'Atmosphere';
	else if (owlClass === ontUri + 'ES')
		return 'Ecosystem';
	else if (owlClass === ontUri + 'OS')
		return 'Ocean';
};

export const themeGlyphs = {
	Atmosphere: 'cloud',
	Ecosystem: 'leaf',
	Ocean: 'tint'
};

const docsFolder = 'https://static.icos-cp.eu/share/stations/docs/labelling/';
export const etc = {
	step1Doc: docsFolder + 'etcLabelling20151123.pdf',
	step1DocAssStations: docsFolder + 'Instructions_ECO_Associated_station_20160830.pdf',
	windDirectionFileType1: 'Wind direction (sectors/months)',
	windDirectionFileType2: 'Wind direction (time series)',
	powerAvailabilityFileType: 'Power availability',
	internetConnFileType: 'Internet connection',
	otherProjectsFileType: 'Other projects',
	demFileType: 'DEM',
	instrumentsFileType: 'Instruments and short setup description',
	highResImageFileType: 'High resolution image',
	photos30DegOutsideFileType: '30-degrees photo outside canopy'
};

export const otc = {
	geoCoverageFileType: 'Geographical coverage',
	bboxPropNames: ['hasWesternmostLon', 'hasEasternmostLon', 'hasNothernmostLat', 'hasSouthernmostLat'],
	latAndLonPropNames: ['hasLat', 'hasLon']
};

export const themeToFiles = {
	Atmosphere: [
		{min: 1, max: 20, type: "Photo(s) of the site"},
		{min: 1, max: 1, type: "Prevailing wind directions (closest meteo station)", tip: "Prevailing wind directions or better seasonal wind roses (include reference to the meteo data used)"},
		{min: 0, max: 1, type: "Distribution system diagram", tip: "Distribution system diagram: plumbing and Instrumentation Diagram (if available)"},
		{min: 0, max: 1, type: "Further comments", tip: "Further comments (voluntary)"},
		{min: 0, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ecosystem: [
		{min: 1, max: 1, type: "Basic station information", tip: "Ecosystem type, management, recent disturbances, site description, etc."},
		{min: 0, max: 1, type: etc.instrumentsFileType, tip: "Requested variables, general method, sensors position (overall idea)"},
		{min: 0, max: 1, type: etc.powerAvailabilityFileType, tip: "Source and kW available in total"},
		{min: 0, max: 1, type: etc.internetConnFileType, tip: "Internet connection, system and capacity (speed, robustness)"},
		{min: 0, max: 1, type: etc.otherProjectsFileType, tip: "Sharing of the facility with other initiatives, networks, and projects"},
		{min: 0, max: 1, type: etc.windDirectionFileType1, tip: "XX-YYY_Wind.ext (Excel or ASCII), see §6 of the document"},
		{min: 0, max: 1, type: etc.windDirectionFileType2, tip: "XX-YYY_WindData.ext (Excel or ASCII), see §6 of the document"},
		{min: 0, max: 3, type: etc.demFileType, tip: "Digital elevation model of an area 3x3 km around the tower"},
		{min: 0, max: 3, type: etc.highResImageFileType, tip: "High resolution aerial or satellite color image of an area 3x3 km around the tower"},
		{min: 1, max: 3, type: "Vegetation map", tip: "Vegetation map of the 3x3 km around the tower (1x1 km accepted for class-3 stations)"},
		{min: 0, max: 12, type: etc.photos30DegOutsideFileType, tip: "12 photos (every 30 degrees starting from North) taken from the tower position (§11)"},
		{min: 0, max: 12, type: "30-degrees photo below canopy", tip: "Same as \"30-degrees photo outside canopy\", applicable to forest sites only (§11)"},
		{min: 0, max: 4, type: "4-direction photo", tip: "Forest sites: from the tower top looking down; non-forest: 4 photos of the tower"},
		{min: 0, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	],
	Ocean: [
		{min: 0, max: 1, type: otc.geoCoverageFileType, tip: "A data file or just lat/lon info (used to calculate the geo boundaries of the section)"},
		{min: 0, max: 1, type: "Financial letter", tip: "Financial stakeholder supporting letter"}
	]
};

let commonPropNames = otc.latAndLonPropNames.concat(['hasApplicationStatus']);

let propNamesLookup = {
	Atmosphere: ['hasShortName', 'hasLongName', 'hasMainPersonnelNamesList', 'hasResponsibleInstitutionName',
		'hasStationClass', 'hasAddress', 'hasWebsite', 'hasElevationAboveGround',
		'hasElevationAboveSea', 'hasAccessibility', 'hasVegetation', 'hasAnthropogenics',
		'hasConstructionStartDate', 'hasConstructionEndDate', 'hasOperationalDateEstimate',
		'hasTelecom', 'hasExistingInfrastructure', 'hasNameListOfNetworksItBelongsTo'],

	Ecosystem: ['hasStationClass', 'hasAnemometerDirection', 'hasEddyHeight', 'hasWindDataInEuropeanDatabase'],

	Ocean: ['hasMainPersonnelNamesList', 'hasPlatformType', 'hasTypeOfSampling', 'hasShortName', 'hasLongName',
		'hasCountry', 'hasVesselOwner', 'hasLocationDescription',
		'hasUnderwayEquilibratorType', 'hasUnderwayCo2SensorManufacturer', 'hasUnderwayCo2SensorModel',
		'hasUnderwayOtherSensorManufacturer', 'hasUnderwayOtherSensorModel', 'hasUnderwayMethodReferences',
		'hasUnderwayAdditionalInfo', 'hasDiscreteTco2AnalysisMethod', 'hasDiscreteTco2StandardizationTechnique',
		'hasDiscreteTco2TechniqueDescription', 'hasDiscreteTco2MethodReferences', 'hasDiscreteAlkalinityCurveFitting',
		'hasDiscreteAlkalinityTitrationType', 'hasDiscreteAlkalinityOtherTitration', 'hasDiscreteAlkalinityMethodReferences',
		'hasDiscretePco2Analysis', 'hasDiscretePco2AnalysisMethod', 'hasDiscretePco2MethodReferences', 'hasDiscretePhScale',
		'hasDiscretePhAnalysisMethod', 'hasDiscretePhMethodReferences', 'hasDiscreteAdditionalInfo',
		'hasNrtDataDeliveryMethod', 'hasNrtDataUpdateFrequency'].concat(etc.bboxPropNames)
};

export function themeToProperties(theme){
	return commonPropNames.concat(propNamesLookup[theme]);
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

