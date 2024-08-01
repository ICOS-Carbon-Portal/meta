from datetime import datetime
import warnings
from typing import TypeAlias
from dataclasses import dataclass
from icoscp_core.icos import meta, data
from icoscp_core.metacore import StationTimeSeriesMeta, PlainStaticObject, UriResource, Agent
import sparql
from obspack_netcdf import ObspackNetcdf


WdcggMetadataDict: TypeAlias = dict[str, str | int | list[str] | list[dict[str, str]]]


CONTRIBUTOR = "159"
WDCGG_GAS_SPECIES_CODES = {"CO2": "1001", "CH4": "1002", "N2O": "1003", None: ""}
SCALES = {"CO2": "WMO CO2 X2019", "CH4": "WMO CH4 X2004A", None: ""}
WDCGG_SCALE_CODES = {"WMO CO2 X2019": "158", "WMO CH4 X2004A": "3", None: ""}
WDCGG_METHODS = {
	"Picarro": {"method": "CRDS", "code": "18"},
	"LI-COR": {"method": "NDIR", "code": "9"},
	"Los Gatos Research": {"method": "off-axis integrated cavity output spectroscopy (OA-ICOS)", "code": "25"},
	"Siemens, ULTRAMAT": {"method": "NDIR", "code": "9"},
	"Maihak": {"method": "NDIR", "code": "9"},
	"Ecotech, Spectronus": {"method": "Fourier Transform Spectrometer", "code": "50"},
	"ABB, URAS": {"method": "NDIR", "code": "9"},
	"Laboratoire des Sciences du Climat et de l'Environnement, Caribou": {"method": "NDIR", "code": "9"}
}
WDCGG_PLATFORMS = {
	"surface": "01", "tower": "02", "balloon": "03", "aircraft": "05",
	"ship": "06", "buoy": "07", "satellite": "08", "human": "09"
}
WDCGG_SAMPLING_TYPES = {
	"insitu": "01", "flask": "02", "filter": "03", "bottle": "05",
	"bag": "06", "PFP": "07", "remote": "08"
}


@dataclass
class DobjInfo:
	url: str
	fileName: str
	doi: str | None
	pid: str | None
	citationString: str | None
	station: str | None
	samplingHeight: float | None
	sources: list[PlainStaticObject] | None
	authors: list[Agent] | None
	gasSpecies: str | None
	instruments: UriResource | list[UriResource] | None


class WdcggMetadata:
	def __init__(self, dobj_info: DobjInfo, gawsis_to_wdcgg_station_id: dict[str, str]):
		self.dobj_info = dobj_info
		self.gawsis_to_wdcgg_station_id = gawsis_to_wdcgg_station_id
		self.instruments: dict[int, str] = {}

	def dobj_metadata(self) -> WdcggMetadataDict:
		"""Fill information in a JSON object according to WDCGG template for metadata.

		Returns
		-------
		Dictionary matching the WDCGG JSON template for metadata.
		"""

		metadata: dict[str, str | int | list[str] | list[dict[str, str]]] = {}

		# Contributor organization's three-digit number assigned by WDCGG.
		# Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
		metadata["Contributor"] = CONTRIBUTOR

		# To be changed to the actual submission date.
		# Format must be 'YYYY-MM-DD hh:mm:ss'.
		metadata["Submission_date"] = "2024-08-01 12:00:00"

		metadata["md_editor_name"] = "Jonathan Schenk"
		metadata["md_editor_email"] = "jonathan.schenk@nateko.lu.se"

		# Represents the submission type.
		# 1: data & metadata submission
		# 2: Update Contributor(Metadata only)
		# 3: Update Contact(Metadata only)
		metadata["Option"] = "1"

		# List of updated metadata items.
		metadata["Update"] = [
			"or_organization",
			"jl_joint_laboratory",
			"pg_person_group",
			"ao_aim_of_observation",
			"tz_time_zone",
			"un_unit",
			"sh_scale_history",
			"ih_instrument_history",
			"sh_sampling_height_history",
			"sf_sampling_frequency",
			"md_measurement_calibration",
			"md_data_processing",
			"md_hr_mean_processing",
			"md_da_mean_processing",
			"md_mo_mean_processing",
			"md_original_data_flag",
			"dg_data_flag_group",
			"rg_reference_group",
			"st_status",
			"md_description",
			"dc_doi_category"
		]

		# Catalog ID of the updated data. The wdcgg_catalogue_id is shown in
		# the first 25 characters of 'Version' on 'Data Update Information' tab
		# at https://gaw.kishou.go.jp/contributor .
		metadata["wc_wdcgg_catalogue_id"] = self.wdcgg_catalog_id()

		# Same Contributor organization's number as above.
		# Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
		metadata["or_organization_code"] = CONTRIBUTOR

		# Name of the Contributor organization.
		# Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
		metadata["or_organization"] = "ICOS"

		# Organization IDs of collaborators, if any (OPTIONAL).
		# Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
		metadata["jl_joint_laboratory"] = []

		# IDs of the contacts for the observation as given in the contact metadata.
		metadata["pg_person_group"] = []

		# Individual collaborators who should not receive inquiries, requests
		# or other forms of contact regarding the observation (OPTIONAL).
		#metadata["jp_joint_person"] = []

		# Full list at https://gaw.kishou.go.jp/documents/db_list/aim_of_observation .
		metadata["ao_aim_of_observation_code"] = "1"
		metadata["ao_aim_of_observation"] = "Background observation"

		# Time zone for the timestamps of the data.
		metadata["tz_time_zone_code"] = "1"
		metadata["tz_time_zone"] = "UTC"

		# Unit of the data.
		# Full list at https://gaw.kishou.go.jp/documents/db_list/unit .
		metadata["un_unit_code"] = "99"
		metadata["un_unit"] = "mol mol-1"

		# History of calibration scales used for the data (OPTIONAL).
		# It must include the scale's name and its corresponding WDCGG code,
		# as well as the time period when it was used.
		# Full list of WDCGG scale codes at https://gaw.kishou.go.jp/documents/db_list/scale .
		scale = SCALES[self.dobj_info.gasSpecies]
		metadata["sh_scale_history"] = [{
			"sh_start_date_time": "9999-12-31T00:00:00",
			"sh_end_date_time": "9999-12-31T23:59:59",
			"sc_scale_code": scale,
			"sc_scale": WDCGG_SCALE_CODES[scale]
		}]

		# Instrument history (OPTIONAL).
		# It must include the instrument's method and its corresponding WDCGG code,
		# the time period when it was used, and the instrument's manufacturer
		# product name and model number.
		# Full list of instrument methods at https://gaw.kishou.go.jp/documents/db_list/measurement_method .
		metadata["ih_instrument_history"] = self.instrument_history()

		# Sampling height history (OPTIONAL).
		# It must include the height and the corresponding time period.
		if self.dobj_info.samplingHeight is None:
			warnings.warn(f"No sampling height provided for data object {self.dobj_info.url}.")
			sampling_height = ""
		else:
			sampling_height = str(self.dobj_info.samplingHeight)
		metadata["sh_sampling_height_history"] = [{
			"sh_start_date_time": "9999-12-31T00:00:00",
			"sh_end_date_time": "9999-12-31T23:59:59",
			"sh_sampling_height": sampling_height
		}]

		# Sampling frequency (OPTIONAL).
		# 1: event, 2: weekly, 3: daily, 4: hourly, 5: 30 minutes, 6: 5 minutes,
		# ... 99: Other
		# The name of the sampling frequency must only be provided if code "99"
		# is used.
		metadata["sf_sampling_frequency_code"] = "6"
		#metadata["sf_sampling_frequency"] = ""

		# Text description of the measurement calibration procedure (OPTIONAL).
		# Includes calibration procedure for determining mole fractions along
		# with procedures for analysis such as the order of introduction (sequence)
		# for sample and standard gases (or zero gases) to the instrument,
		# the relevant time period and the number of calibration points.
		metadata["md_measurement_calibration"] = """Calibration sequence every 15 days with three calibration tanks
Gas injection duration: 30 min
Number of cycles (tank analysis) during a calibration: 4
Performance target frequency: every 7 hours
Archive target frequency: every 15 days"""

		# Text description of the data processing procedure (OPTIONAL).
		# Includes details of instrumental data processing and averaging.
		# Criteria used in selection for data processing should also be described.
		metadata["md_data_processing"] = "Data processing at ICOS ATC is described in [Hazan et al., 2016], doi:10.5194/amt-9-4719-2016"

		# Text description of average hourly data processing (OPTIONAL).
		# Describe detailed processes on hourly data or data selections on qualities.
		metadata["md_hr_mean_processing"] = "Time-averaged values are reported at the beginning of the averaging interval."

		# Text description of average daily data processing (OPTIONAL).
		# Describe detailed processes on daily data or data selections on qualities.
		#metadata["md_da_mean_processing"] = ""

		# Text description of average monthly data processing (OPTIONAL).
		# Describe detailed processes on monthly data or data selections on qualities.
		#metadata["md_mo_mean_processing"] = ""

		# Text description of the data flags used (OPTIONAL).
		# Describe the criteria for data flagging. If data have already been flagged
		# with WDCGG data quality flags, Original Data Flag metadata do not need to be added.
		metadata["md_original_data_flag"] = """- Flag 'U' = data correct before manual quality control
- Flag 'N' = data incorrect before manual quality control
- Flag 'O' = data correct after manual quality control
- Flag 'K' = data incorrect after manual quality control
- Flag 'R' = data correct after manual quality control and backward propagation of manual quality control from hourly data to minutely and raw data
- Flag 'H' = data incorrect after manual quality control and backward propagation of manual quality control from hourly data to minutely and raw data"""

		# Correspondance between original data flags and WDCGG data flags (OPTIONAL).
		# Includes WDCGG data flag code, WDCGG data flag name and the original data flag.
		# Full list of WDCGG data flags at https://gaw.kishou.go.jp/documents/db_list/data_flag .
		metadata["dg_data_flag_group"] = [
			{"df_data_flag_code": "1", "df_data_flag": "Valid (background)", "dg_data_flag": "U"},
			{"df_data_flag_code": "1", "df_data_flag": "Valid (background)", "dg_data_flag": "O"},
			{"df_data_flag_code": "1", "df_data_flag": "Valid (background)", "dg_data_flag": "R"},
			{"df_data_flag_code": "3", "df_data_flag": "Invalid", "dg_data_flag": "N"},
			{"df_data_flag_code": "3", "df_data_flag": "Invalid", "dg_data_flag": "K"},
			{"df_data_flag_code": "3", "df_data_flag": "Invalid", "dg_data_flag": "H"},
		]

		# References (OPTIONAL).
		# Can be references to observations (such as instruments, data processing
		# and calibration) in literature or URLs.
		metadata["rg_reference_group"] = [
			{"rg_reference": "Hazan, L., Tarniewicz, J., Ramonet, M., Laurent, O., and Abbaris, A.: Automatic processing of atmospheric CO2 and CH4 mole fractions at the ICOS Atmosphere Thematic Centre, Atmos. Meas. Tech., 9, 4719-4736, doi:10.5194/amt-9-4719-2016, 2016."}
		]

		# Status of the measurements (operational or not).
		# Full list at https://gaw.kishou.go.jp/documents/db_list/status .
		metadata["st_status_code"] = "1"
		metadata["st_status"] = "Operational/Reporting"

		# DOI status of the data.
		# 1: Request for WDCGG DOI issuance, 2: Original DOI already present, 9: Undecided
		# If code "2" is selected, the original DOI must be provided.
		if self.dobj_info.doi is None:
			metadata["dc_doi_category_code"] = "9"
		else:
			metadata["dc_doi_category_code"] = "2"
			metadata["dc_doi_category"] = self.dobj_info.doi

		# Additional information in the form of text (OPTIONAL).
		metadata["md_description"] = f"""ICOS atmospheric station
Observational timeseries of ambient mole fraction of {self.dobj_info.gasSpecies} in dry air, composed of (all whenever available) historical PI quality-checked data, ICOS Level 2 data and ICOS NRT data.

PID: {self.dobj_info.pid}

Citation:
{self.dobj_info.citationString}

DATA POLICY:
ICOS data is licensed under a Creative Commons Attribution 4.0 international licence (https://creativecommons.org/licenses/by/4.0/). ICOS data licence is described at https://data.icos-cp.eu/licence.
"""

		return metadata

	def contact_metadata(self) -> WdcggMetadataDict:
		"""Fill information in a JSON object according to WDCGG template for contacts metadata.

		Returns
		-------
		Dictionary matching the WDCGG JSON template for contacts metadata.
		"""

		return {}

	def wdcgg_catalog_id(self) -> str:
		"""Produce a string containing the data object's catalog ID.

		Returns
		-------
		A string representing the data object's catalog ID, which follows
		the pattern ORG-STA-GAS-PTF-SAM-BUF where:
		  - ORG is the four-digit organization ID according to
			https://gaw.kishou.go.jp/documents/db_list/organization
		  - STA is the four-digit station ID according to
			https://gaw.kishou.go.jp/documents/db_list/station
		  - GAS is the four-digit gas species ID according to
			https://gaw.kishou.go.jp/documents/db_list/gas_species
		  - PTF is the two-digit observational platform ID according to
			https://gaw.kishou.go.jp/documents/db_list/platform
		  - SAM is the two-digit sampling type ID according to
			https://gaw.kishou.go.jp/documents/db_list/sampling_type
		  - BUF is the four-digit buffer item ID according to
			https://gaw.kishou.go.jp/documents/db_list/buffer
		"""

		# Station ID
		if self.dobj_info.station is None:
			warnings.warn(f"No station name for data object {self.dobj_info.url}")
			wdcgg_station_id = ""
		elif self.dobj_info.station not in self.gawsis_to_wdcgg_station_id.keys():
			warnings.warn(f"Station {self.dobj_info.station} is not registered in GAWSIS.")
			wdcgg_station_id = ""
		else:
			wdcgg_station_id = self.gawsis_to_wdcgg_station_id[self.dobj_info.station]

		# Observational platform ID and sampling type ID
		platform = self.dobj_info.fileName.split("_")[2].split("-")[0]
		sampling_type = self.dobj_info.fileName.split("_")[2].split("-")[1]
		if platform in WDCGG_PLATFORMS.keys():
			platform_id = WDCGG_PLATFORMS[platform]
		else:
			warnings.warn(f"Unknow platform '{platform}' used for data object {self.dobj_info.url}.")
			platform_id = ""
		if sampling_type in WDCGG_SAMPLING_TYPES.keys():
			sampling_type_id = WDCGG_SAMPLING_TYPES[sampling_type]
		else:
			warnings.warn(
				f"Unknown sampling type '{sampling_type}' used for data object {self.dobj_info.url}."
			)
			sampling_type_id = ""

		return "-".join([
			CONTRIBUTOR.zfill(4),
			wdcgg_station_id.zfill(4),
			WDCGG_GAS_SPECIES_CODES[self.dobj_info.gasSpecies],
			platform_id,
			sampling_type_id,
			"9999"
		])

	def instrument_history(self) -> list[dict[str, str]]:
		"""Format the instrument history according to WDCGG requirements.

		Returns
		-------
		A list of dictionaries where each dictionary contains information
		about one instrument's deployment according to WDCGG requirements.
		"""

		buffer = data.get_file_stream(self.dobj_info.url)
		obspack_nc = ObspackNetcdf(self.dobj_info.fileName, buffer[1].read())
		instr_hist = obspack_nc.instrument_history("time", "instrument")
		if len(instr_hist) == 0 or len(instr_hist) > 5:
			return []
		instr_hist_wdcgg: list[dict[str, str]] = []
		for deployment in instr_hist:
			instr_label: str
			if deployment.atc_id in self.instruments.keys():
				instr_label = self.instruments[deployment.atc_id]
			else:
				instr_query = sparql.instrument_query(deployment.atc_id)
				instr_label = sparql.run_query(instr_query)[0]
				self.instruments[deployment.atc_id] = instr_label
			for vendor_or_model, wdcgg_method_info in WDCGG_METHODS.items():
				if instr_label.startswith(vendor_or_model):
					wdcgg_method = wdcgg_method_info["method"]
					wdcgg_method_code = wdcgg_method_info["code"]
					break
			else:
				wdcgg_method = ""
				wdcgg_method_code = ""
			instr_hist_wdcgg.append({
				"ih_start_date_time": timestamp_to_str(deployment.time_period.start_time, "%Y-%m-%dT%H:%M:%S"),
				"ih_end_date_time": timestamp_to_str(deployment.time_period.end_time, "%Y-%m-%dT%H:%M:%S"),
				"ih_instrument": instr_label,
				"mm_measurement_method_code": wdcgg_method_code,
				"mm_measurement_method": wdcgg_method
			})
		instr_hist_wdcgg[0]["ih_start_date_time"] = "9999-12-31T00:00:00"
		instr_hist_wdcgg[-1]["ih_end_date_time"] = "9999-12-31T23:59:59"

		return instr_hist_wdcgg


def get_dobj_info(dobj_url: str) -> DobjInfo | None:
	"""Extract relevant information from ICOS metadata.

	Parameters
	----------
	dobj_url : str
		Data object's URL.

	Returns
	-------
	A DobjInfo object containing metadata about the data object.
	If the data object is not a 'StationTimeSeries', returns None.
	"""

	dobj_meta = meta.get_dobj_meta(dobj_url)

	if isinstance(dobj_meta.specificInfo, StationTimeSeriesMeta):
		# Sources
		sources: list[PlainStaticObject] | None
		if dobj_meta.specificInfo.productionInfo is None: sources = None
		else: sources = dobj_meta.specificInfo.productionInfo.sources
		# Gas species
		gas_species: str | None
		keywords = dobj_meta.specification.keywords
		if keywords is None:
			warnings.warn(f"The gas species covered by data object {dobj_url} cannot be determined for lack of keyword.")
			gas_species = None
		else:
			dobj_gas_species = set(keywords).intersection(WDCGG_GAS_SPECIES_CODES.keys())
			if len(dobj_gas_species) == 0:
				warnings.warn(f"The gas species covered by data object {dobj_url} cannot be determined.")
				gas_species = None
			elif len(dobj_gas_species) == 1:
				gas_species = list(dobj_gas_species)[0]
			else:
				warnings.warn(f"More than one gas species was found for data object {dobj_url}.")
				gas_species = None

		return DobjInfo(
			url=dobj_url,
			fileName=dobj_meta.fileName,
			doi=dobj_meta.doi,
			pid=dobj_meta.pid,
			citationString=dobj_meta.references.citationString,
			station=dobj_meta.specificInfo.acquisition.station.org.self.label,
			samplingHeight=dobj_meta.specificInfo.acquisition.samplingHeight,
			sources=sources,
			authors=dobj_meta.references.authors,
			gasSpecies=gas_species,
			instruments=dobj_meta.specificInfo.acquisition.instrument
		)
	else:
		warnings.warn(
			f"Data object {dobj_url} is skipped because its 'specificInfo' field is not "
			f"of type 'StationTimeSeriesMeta' but of type '{type(dobj_meta.specificInfo)}'."
		)
		return None


def timestamp_to_str(timestamp: float, fmt: str) -> str:
	return datetime.fromtimestamp(timestamp).strftime(fmt)