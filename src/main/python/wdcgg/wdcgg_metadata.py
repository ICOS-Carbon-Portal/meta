from datetime import datetime, timezone
import warnings
from typing import Optional, Tuple, Any
from dataclasses import dataclass, asdict
from icoscp_core.icos import meta
from icoscp_core.metacore import StationTimeSeriesMeta, Station, Person
import sparql
from obspack_netcdf import ObspackNetcdf, InstrumentDeployment


@dataclass
class MeasurementUnit:
	unit: str
	wdcgg_code: str

@dataclass
class CalibrationScale:
	name: str
	wdcgg_code: str

@dataclass
class InstrumentMethod:
	method: str
	code: str

CONTRIBUTOR = "162"
SUBMISSION_DATE = "2025-10-08 12:00:00"
EDITOR_NAME = "Jonathan Schenk"
EDITOR_EMAIL = "jonathan.schenk@nateko.lu.se"
WDCGG_GAS_SPECIES_CODES = {"CO2": "1001", "CH4": "1002", "N2O": "1003", "CO": "3001"}
MEASUREMENT_UNITS = {
	"CO2": MeasurementUnit(unit="ppm", wdcgg_code="1"),
	"CH4": MeasurementUnit(unit="ppb", wdcgg_code="2"),
	"N2O": MeasurementUnit(unit="ppb", wdcgg_code="2"),
	"CO": MeasurementUnit(unit="ppb", wdcgg_code="2")
}
SCALES = {
	"CO2": CalibrationScale(name="WMO CO2 X2019", wdcgg_code="158"),
	"CH4": CalibrationScale(name="WMO CH4 X2004A", wdcgg_code="3"),
	"N2O": CalibrationScale(name="WMO N2O X2006A", wdcgg_code="5"),
	"CO": CalibrationScale(name="WMO CO X2014A", wdcgg_code="8")
}
# In WDCGG_METHODS, code "0" is used to indicate that the specific measurement method depends
# on the gas species and is determined in the method "get_wdcgg_instrument_method".
WDCGG_METHODS = {
	"Picarro": InstrumentMethod(method="CRDS", code="18"),
	"LI-COR": InstrumentMethod(method="NDIR", code="9"),
	"Los Gatos Research": InstrumentMethod(method="off-axis integrated cavity output spectroscopy (OA-ICOS)", code="25"),
	"Siemens, ULTRAMAT": InstrumentMethod(method="NDIR", code="9"),
	"Maihak, Maihak S710": InstrumentMethod(method="NDIR", code="9"),
	"Ecotech, Spectronus": InstrumentMethod(method="Fourier Transform Spectrometer", code="50"),
	"ABB, URAS": InstrumentMethod(method="NDIR", code="9"),
	"Laboratoire des Sciences du Climat et de l'Environnement, Caribou": InstrumentMethod(method="NDIR", code="9"),
	"Agilent, 6890N Network Gas Chromatograph": InstrumentMethod(method="Gas chromatography", code="0"),
	"Agilent, 6890 Network Gas Chromatograph": InstrumentMethod(method="Gas chromatography", code="0"),
	"Agilent, 7890A Gas Chromatograph": InstrumentMethod(method="Gas chromatography", code="0"),
	"PerkinElmer, Clarus 500": InstrumentMethod(method="Gas chromatography", code="0"),
	"Siemens, Sichromat-2": InstrumentMethod(method="Gas chromatography", code="0"),
	"HP, HP 5890 II Gas Chromatograph": InstrumentMethod(method="Gas chromatography", code="0"),
	"HP, HP_LUT": InstrumentMethod(method="Gas chromatography", code="0"),
	"Varian, Inc, Varian 3800": InstrumentMethod(method="Gas chromatography", code="0"),
	"DANI, Dani 3800": InstrumentMethod(method="Gas chromatography", code="0")
}
WDCGG_PLATFORMS = {
	"surface": "01", "tower": "02", "balloon": "03", "aircraft": "05",
	"ship": "06", "buoy": "07", "satellite": "08", "human": "09"
}
WDCGG_SAMPLING_TYPES = {
	"insitu": "01", "flask": "02", "filter": "03", "bottle": "05",
	"bag": "06", "PFP": "07", "remote": "08"
}
MAX_INSTRUMENTS = 5
OBJECT_SPECS_OBSPACK_RELEASE = {"CO2": "icosObspackCo2", "CH4": "icosObspackCh4", "N2O": "icosObspackN2o", "CO": "icosObspackCo"}


@dataclass
class DobjInfo:
	url: str
	file_name: str
	pid: str
	citation_string: str
	station: Station
	sampling_height: str
	authors: list[Person]
	gas_species: str

@dataclass
class ScaleHistoryItem:
	sh_start_date_time: str
	sh_end_date_time: str
	sc_scale_code: str
	sc_scale: str

@dataclass
class InstrumentDeploymentWdcgg:
	ih_start_date_time: str
	ih_end_date_time: str
	ih_instrument: str
	mm_measurement_method_code: str
	mm_measurement_method: str

@dataclass
class SamplingHeightHistoryItem:
	sh_start_date_time: str
	sh_end_date_time: str
	sh_sampling_height: str

@dataclass
class ValueUncertaintyHistory:
	vh_start_datetime_1: str
	vh_end_datetime_1: str
	vm_value_unc_method_code_1: str
	vm_value_unc_method_1: str
	vh_start_datetime_2: str
	vh_end_datetime_2: str
	vm_value_unc_method_code_2: str
	vm_value_unc_method_2: str
	vh_start_datetime_3: str
	vh_end_datetime_3: str
	vm_value_unc_method_code_3: str
	vm_value_unc_method_3: str

@dataclass
class OrganizationId:
	or_organization_code: str

@dataclass
class JointPerson:
	or_organization_code: str
	ps_person_id: str

@dataclass
class ContactPersonId:
	ps_person_id: str

@dataclass
class ContactPersonDetails:
	organization: str
	role: str
	role_code: str
	country: str

@dataclass
class DataFlagItem:
	df_data_flag_code: str
	df_data_flag: str
	dg_data_flag: str

@dataclass
class Reference:
	rg_reference: str

@dataclass
class DoiInfo:
	doi: str
	doi_category_code: str

@dataclass
class WdcggOrganization:
	"""
	Parameters
	----------
	or_organization_code : WDCGG code for the organization. Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
	or_acronym :           Organization's acronym.
	or_name :              Organization's name.
	na_nation_code :       ISO 3166-1 alpha-2 code of country/territory to which the organization belongs to.
	or_address_1 :         [OPTIONAL] First line of the organization's address.
	or_address_2 :         [OPTIONAL] Second line of the organization's address.
	or_address_3 :         [OPTIONAL] Third line of the organization's address.
	or_website :           [OPTIONAL] URL of the organization's website.
	"""
	or_organization_code: str
	or_acronym: str
	or_name: str
	na_nation_code: str
	or_address_1: str
	or_address_2: str
	or_address_3: str
	or_website: str

@dataclass
class ContactPerson:
	"""
	Parameters
	----------
	ps_person_id         : Existing WDCGG ID of the person if known, otherwise 'NEW' + the sequential number.
	ps_name              : Name of the contact.
	ps_email             : Email address of the contact.
	pc_person_class_code : WDCGG code for the type of contact/collaborator. 1: Contact Person, 2: Principal Investigator (PI), 3: Contact Person and Principal Investigator (PI), 5: Partner. '5: Partner' is available for individual collaborators.
	pc_person_class      : Type of contact/collaborator. See list above.
	or_organization_code : WDCGG code for the organization to which the contact belongs. Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
	ps_prefix            : [OPTIONAL] Prefix to use when addressing the contact person.
	na_nation_code       : ISO 3166-1 alpha-2 code of country/territory to which the contact's organization belongs to.
	ps_address_1         : [OPTIONAL] First line of the contact's address.
	ps_address_2         : [OPTIONAL] Second line of the contact's address.
	ps_address_3         : [OPTIONAL] Third line of the contact's address.
	ps_phone             : [OPTIONAL] Phone number of the contact.
	ps_fax               : [OPTIONAL] Fax number of the contact.
	"""
	ps_person_id: str
	ps_name: str
	ps_email: str
	pc_person_class_code: str
	pc_person_class: str
	or_organization_code: str
	ps_prefix: str
	na_nation_code: str
	ps_address_1: str
	ps_address_2: str
	ps_address_3: str
	ps_phone: str
	ps_fax: str

@dataclass
class WdcggMetadata:
	"""
	Parameters
	----------
	Contributor :               WDCGG code for the Contributor organization. It is a three-digit number assigned by WDCGG. Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
	Submission_date :           Submission date in format 'YYYY-MM-DD hh:mm:ss'.
	md_editor_name :            Name of the person submitting data/metadata.
	md_editor_email :           Email of the person submitting data/metadata.
	Option :                    Type of submission. 1: data & metadata submission, 2: Update Contributor(Metadata only), 3: Update Contact(Metadata only)
	Update :                    List of updated metadata items.
	wc_wdcgg_catalogue_id :     Catalog ID of the updated data. The catalog ID is shown in the first 25 characters of 'Version' on 'Data Update Information' tab at https://gaw.kishou.go.jp/contributor .
	or_organization_code :      WDCGG code for the Contributor organization. Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
	or_organization :           Contributor organization's name. Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
	jl_joint_laboratory :       [OPTIONAL] WDCGG code for the organizations that have a role of collaborator on the observation. Full list at https://gaw.kishou.go.jp/documents/db_list/organization .
	pg_person_group :           IDs of the contacts for the observation. The IDs should match those provided in the separate contact metadata.
	jp_joint_person :           [OPTIONAL] Individual collaborators who should not receive inquiries, requests or other forms of contact regarding the observation.
	ao_aim_of_observation_code: WDCGG code for the aim of observation. Full list at https://gaw.kishou.go.jp/documents/db_list/aim_of_observation .
	ao_aim_of_observation:      Aim of observation. Full list at https://gaw.kishou.go.jp/documents/db_list/aim_of_observation .
	tz_time_zone_code:          WDCGG code for the time zone of the timestamps in the dataset. Full list in the JSON schema file.
	tz_time_zone:               Time zone for the timestamps of the data. Full list in the JSON schema file.
	un_unit_code:               WDCGG code for the unit of the data. Full list at https://gaw.kishou.go.jp/documents/db_list/unit .
	un_unit:                    Unit of the data. Full list at https://gaw.kishou.go.jp/documents/db_list/unit .
	sh_scale_history:           [OPTIONAL] History of calibration scales used for the data. It must include the scale's name and its corresponding WDCGG code, as well as the time period when it was used. Full list of WDCGG scale codes at https://gaw.kishou.go.jp/documents/db_list/scale .
	ih_instrument_history:      [OPTIONAL] Instrument history. It must include the instrument's method and its corresponding WDCGG code, the time period when it was used, and the instrument's manufacturer product name and model number. Full list of instrument methods at https://gaw.kishou.go.jp/documents/db_list/measurement_method .
	sh_sampling_height_history: [OPTIONAL] Sampling height history. It must include the height and the corresponding time period.
	sf_sampling_frequency_code: [OPTIONAL] WDCGG code for the sampling frequency. Some examples are 1: event, 2: weekly, 3: daily, 4: hourly, 5: 30 minutes, 6: 5 minutes, ... 99: Other. Full list in the JSON schema file.
	sf_sampling_frequency:      [OPTIONAL] The name of the sampling frequency must only be provided if code "99" is used.
	md_measurement_calibration: [OPTIONAL] Text description of the measurement calibration procedure. Describe calibration procedure for determining mole fractions along with procedures for analysis such as the order of introduction (sequence) for sample and standard gases (or zero gases) to the instrument, the relevant time period and the number of calibration points.
	md_data_processing:         [OPTIONAL] Text description of the data processing procedure. Describe details of instrumental data processing and averaging. Criteria used in selection for data processing should also be described.
	md_hr_mean_processing:      [OPTIONAL] Text description of average hourly data processing. Describe detailed processes on hourly data or data selections on qualities.
	md_da_mean_processing:      [OPTIONAL] Text description of average daily data processing. Describe detailed processes on daily data or data selections on qualities.
	md_mo_mean_processing:      [OPTIONAL] Text description of average monthly data processing. Describe detailed processes on monthly data or data selections on qualities.
	md_original_data_flag:      [OPTIONAL] Text description of the data flags used. Describe the criteria for data flagging. If data have already been flagged with WDCGG data quality flags, Original Data Flag metadata do not need to be added.
	dg_data_flag_group:         [OPTIONAL] Correspondance between original data flags and WDCGG data flags. It must include the WDCGG data flag code, WDCGG data flag name and the original data flag. Full list of WDCGG data flags at https://gaw.kishou.go.jp/documents/db_list/data_flag .
	rg_reference_group:         [OPTIONAL] References. Can be references to observations (such as instruments, data processing and calibration) in literature or URLs.
	st_status_code:             WDCGG code for the status of the measurements (operational or not). Full list at https://gaw.kishou.go.jp/documents/db_list/status .
	st_status:                  Status of the measurements (operational or not). Full list at https://gaw.kishou.go.jp/documents/db_list/status .
	dc_doi_category_code:       WDCGG code of the DOI status of the data. 1: Request for WDCGG DOI issuance, 2: Original DOI already present, 9: Undecided
	dc_doi_category:            If code "2" is selected, the original DOI must be provided.
	md_description:             [OPTIONAL] Any additional information that cannot be provided elsewhere.
	ri_reporting_interval_code
	ri_reporting_interval
	vh_value_unc_history
	"""
	Contributor: str
	Submission_date: str
	md_editor_name: str
	md_editor_email: str
	Option: str
	Update: list[str]
	wc_wdcgg_catalogue_id: str
	or_organization_code: str
	or_organization: str
	jl_joint_laboratory: list[OrganizationId]
	pg_person_group: list[ContactPersonId]
	jp_joint_person: list[JointPerson]
	ao_aim_of_observation_code: str
	ao_aim_of_observation: str
	tz_time_zone_code: str
	tz_time_zone: str
	un_unit_code: str
	un_unit: str
	sh_scale_history: list[ScaleHistoryItem]
	ih_instrument_history: list[InstrumentDeploymentWdcgg]
	sh_sampling_height_history: list[SamplingHeightHistoryItem]
	sf_sampling_frequency_code: str
	sf_sampling_frequency: str
	md_measurement_calibration: str
	md_data_processing: str
	md_hr_mean_processing: str
	md_da_mean_processing: str
	md_mo_mean_processing: str
	md_original_data_flag: str
	dg_data_flag_group: list[DataFlagItem]
	rg_reference_group: list[Reference]
	st_status_code: str
	st_status: str
	dc_doi_category_code: str
	dc_doi_category: str
	md_description: str
	ri_reporting_interval_code: str
	ri_reporting_interval: str
	vh_value_unc_history: list[ValueUncertaintyHistory]


class WdcggMetadataClient:
	def __init__(self, submission_window: sparql.SubmissionWindow):
		self.submission_window = submission_window
		self.metadata: list[dict[str, Any]] = []
		self.contacts: list[dict[str, Any]] = []
		self.contact_ids: dict[str, str] = {}
		self.organizations: list[dict[str, Any]] = []
		self.organization_ids: dict[str, str] = {}
		self.instruments: dict[int, str] = {}

	def dobj_metadata(self, dobj_info: DobjInfo, netcdf_data: ObspackNetcdf, wdcgg_station_id: str) -> None:
		"""Structure metadata according to WDCGG template for dataset metadata.

		Returns
		-------
		WdcggMetadata object containing metadata about the data object
		according to the WDCGG JSON template.
		"""

		measurement_unit = MEASUREMENT_UNITS[dobj_info.gas_species]
		scale = SCALES[dobj_info.gas_species]
		doi_info = self.doi_obspack_release(OBJECT_SPECS_OBSPACK_RELEASE[dobj_info.gas_species])

		self.metadata.append(asdict(WdcggMetadata(
			Contributor = CONTRIBUTOR,
			Submission_date = SUBMISSION_DATE,
			md_editor_name = EDITOR_NAME,
			md_editor_email = EDITOR_EMAIL,
			Option = "1",
			Update = [
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
				"dc_doi_category",
				"md_description",
				"ri_reporting_interval",
				"vh_value_unc_history"
			],
			wc_wdcgg_catalogue_id = self.wdcgg_catalog_id(dobj_info.url, wdcgg_station_id, dobj_info.file_name, dobj_info.gas_species),
			or_organization_code = CONTRIBUTOR,
			or_organization = "ICOS",
			jl_joint_laboratory = [],
			pg_person_group = self.get_contacts_metadata(dobj_info.authors, dobj_info.station),
			jp_joint_person = [],
			ao_aim_of_observation_code = "1",
			ao_aim_of_observation = "Background observation",
			tz_time_zone_code = "1",
			tz_time_zone = "UTC",
			un_unit_code = measurement_unit.wdcgg_code,
			un_unit = measurement_unit.wdcgg_code,
			sh_scale_history = [ScaleHistoryItem(
				sh_start_date_time="9999-12-31T00:00:00",
				sh_end_date_time="9999-12-31T23:59:59",
				sc_scale_code=scale.wdcgg_code,
				sc_scale=scale.name
			)],
			ih_instrument_history = self.instrument_history(netcdf_data, dobj_info.gas_species),
			sh_sampling_height_history = [SamplingHeightHistoryItem(
				sh_start_date_time="9999-12-31T00:00:00",
				sh_end_date_time="9999-12-31T23:59:59",
				sh_sampling_height=dobj_info.sampling_height
			)],
			sf_sampling_frequency_code = "88",
			sf_sampling_frequency = "",
			md_measurement_calibration = "Measurement calibration at ICOS ATC is described in [Hazan et al., 2016], doi:10.5194/amt-9-4719-2016",
			md_data_processing = "Data processing at ICOS ATC is described in [Hazan et al., 2016], doi:10.5194/amt-9-4719-2016",
			md_hr_mean_processing = "Time-averaged values are reported at the start time of the averaging interval. The document 'ICOS Atmospheric Station specifications' (doi:10.18160/GK28-2188) provides more detailed explanations about the sampling frequency.",
			md_da_mean_processing = "",
			md_mo_mean_processing = "",
			md_original_data_flag = ("- Flag 'U' = data correct before manual quality control\n"
				"- Flag 'N' = data incorrect before manual quality control\n"
				"- Flag 'O' = data correct after manual quality control\n"
				"- Flag 'K' = data incorrect after manual quality control\n"
				"- Flag 'R' = data correct after manual quality control and backward propagation of manual quality control from hourly data to minutely and raw data\n"
				"- Flag 'H' = data incorrect after manual quality control and backward propagation of manual quality control from hourly data to minutely and raw data"),
			dg_data_flag_group = [
				DataFlagItem(df_data_flag_code="1", df_data_flag="Valid (background)", dg_data_flag="U"),
				DataFlagItem(df_data_flag_code="1", df_data_flag="Valid (background)", dg_data_flag="O"),
				DataFlagItem(df_data_flag_code="1", df_data_flag="Valid (background)", dg_data_flag="R"),
				DataFlagItem(df_data_flag_code="3", df_data_flag="Invalid", dg_data_flag="N"),
				DataFlagItem(df_data_flag_code="3", df_data_flag="Invalid", dg_data_flag="K"),
				DataFlagItem(df_data_flag_code="3", df_data_flag="Invalid", dg_data_flag="H"),
			],
			rg_reference_group = [
				Reference(rg_reference="Hazan, L., Tarniewicz, J., Ramonet, M., Laurent, O., and Abbaris, A.: Automatic processing of atmospheric CO2 and CH4 mole fractions at the ICOS Atmosphere Thematic Centre, Atmos. Meas. Tech., 9, 4719-4736, doi:10.5194/amt-9-4719-2016, 2016."),
				Reference(rg_reference="Yver Kwok, C., Laurent, O., Guemri, A., Philippon, C., Wastine, B., Rella, C. W., Vuillemin, C., Truong, F., Delmotte, M., Kazan, V., Darding, M., Lebègue, B., Kaiser, C., Xueref-Rémy, I., and Ramonet, M.: Comprehensive laboratory and field testing of cavity ring-down spectroscopy analyzers measuring H2O, CO2, CH4 and CO, Atmos. Meas. Tech., 8, 3867–3892, https://doi.org/10.5194/amt-8-3867-2015, 2015."),
				Reference(rg_reference="Yver-Kwok, C., Philippon, C., Bergamaschi, P., Biermann, T., Calzolari, F., Chen, H., Conil, S., Cristofanelli, P., Delmotte, M., Hatakka, J., Heliasz, M., Hermansen, O., Komínková, K., Kubistin, D., Kumps, N., Laurent, O., Laurila, T., Lehner, I., Levula, J., Lindauer, M., Lopez, M., Mammarella, I., Manca, G., Marklund, P., Metzger, J.-M., Mölder, M., Platt, S. M., Ramonet, M., Rivier, L., Scheeren, B., Sha, M. K., Smith, P., Steinbacher, M., Vítková, G., and Wyss, S.: Evaluation and optimization of ICOS atmosphere station data as part of the labeling process, Atmos. Meas. Tech., 14, 89–116, https://doi.org/10.5194/amt-14-89-2021, 2021.")
			],
			st_status_code = "1",
			st_status = "Operational/Reporting",
			dc_doi_category_code = doi_info.doi_category_code,
			dc_doi_category = doi_info.doi,
			md_description = ("ICOS atmospheric station\n"
				f"Observational timeseries of ambient mole fraction of {dobj_info.gas_species} in dry air, composed of (all whenever available) historical PI quality-checked data, ICOS Level 2 data and ICOS NRT data.\n\n"
				"The following uncertainty estimates are used:\n"
				"  a. Continuous Measurement Repeatability: monthly average of the standard deviations of short-term target raw data over 1-minute intervals\n"
				"  b. Long Term Repeatability: standard deviation of the averaged short-term target measurement intervals (of 10 minutes every 5 hours approximately) over three days\n"
				"  c. Short Term Target Bias: difference between the hourly average of the short-term target injections and the value assigned by the calibration lab\n"
				'The method used for "value_unc_1" is "Continuous Measurement Repeatability". The method used for "value_unc_2" is "Long Term Repeatability". The method used for "value_unc_3" is "Short Term Target Bias".\n'
				"More information about the uncertainty estimates can be found in Yver Kwok et al. (2015) (https://doi.org/10.5194/amt-8-3867-2015) and Yver-Kwok et al. (2021) (https://amt.copernicus.org/articles/14/89/2021/).\n\n"
				f"PID: {dobj_info.pid}\n\n"
				"Citation:\n"
				f"{dobj_info.citation_string}\n\n"
				"DATA POLICY:\n"
				"ICOS data is licensed under a Creative Commons Attribution 4.0 international licence (https://creativecommons.org/licenses/by/4.0/). ICOS data licence is described at https://data.icos-cp.eu/licence."),
			ri_reporting_interval_code = "3001",
			ri_reporting_interval = "1 hour",
			vh_value_unc_history = [ValueUncertaintyHistory(
				vh_start_datetime_1="9999-12-31T00:00:00",
				vh_end_datetime_1="9999-12-31T23:59:59",
				vm_value_unc_method_code_1="10",
				vm_value_unc_method_1="short term repeatability",
				vh_start_datetime_2="9999-12-31T00:00:00",
				vh_end_datetime_2="9999-12-31T23:59:59",
				vm_value_unc_method_code_2="10",
				vm_value_unc_method_2="short term repeatability",
				vh_start_datetime_3="9999-12-31T00:00:00",
				vh_end_datetime_3="9999-12-31T23:59:59",
				vm_value_unc_method_code_3="10",
				vm_value_unc_method_3="short term repeatability"
			)]
		)))

	def get_contacts_metadata(self, authors: list[Person], station: Station) -> list[ContactPersonId]:
		"""Gather metadata about contact persons.

		Returns
		-------
		A list of ContactPersonId objects to be used in the dataset metadata.
		At the same time, fill in a contact persons list to be used for
		producing the contact metadata file.
		"""

		contacts: list[ContactPersonId] = []
		for author in authors:
			uri = author.self.uri
			person_details = self.get_person_details(uri, station)
			if uri in self.contact_ids.keys():
				person_id = self.contact_ids[uri]
			else:
				person_id = f"NEW{len(self.contact_ids) + 1}"
				self.contact_ids[uri] = person_id
				self.contacts.append(asdict(ContactPerson(
					ps_person_id=person_id,
					ps_name=" ".join([author.firstName, author.lastName]),
					ps_email=author.email or "",
					pc_person_class_code=person_details.role_code,
					pc_person_class=person_details.role,
					or_organization_code=person_details.organization,
					ps_prefix="",
					na_nation_code=person_details.country,
					ps_address_1="",
					ps_address_2="",
					ps_address_3="",
					ps_phone="",
					ps_fax=""
				)))
			contacts.append(ContactPersonId(ps_person_id=person_id))
		return contacts

	def get_person_details(self, person_uri: str, station: Station) -> ContactPersonDetails:
		query = sparql.contributor_roles_query(person_uri, station.org.self.uri)
		results = sparql.run_sparql_select_query_single_param(query)
		if len(results) == 0:
			warnings.warn(f"No role was found for {person_uri} at station {station.org.self.uri}.")
			role = "Contact Person"
			role_code = "1"
		elif "Principal Investigator" not in results:
			role = "Contact Person"
			role_code = "1"
		else:
			role = "Contact Person and Principal Investigator (PI)"
			role_code = "3"
		org = station.responsibleOrganization or station.org
		org_label = org.self.label or org.self.uri.split("/")[-1]
		if org.website is None: country = ""
		else: country = org.website[8:].split("/")[0].split(".")[-1].upper()
		if country == "CAT": country = "ES"
		if org_label in self.organization_ids.keys():
			org_id = self.organization_ids[org_label]
		else:
			org_id = f"{len(self.organization_ids) + 1}"
			self.organization_ids[org_label] = org_id
			self.organizations.append(asdict(WdcggOrganization(
				or_organization_code=org_id,
				or_acronym=org_label,
				or_name=org.name,
				na_nation_code=country,
				or_address_1="",
				or_address_2="",
				or_address_3="",
				or_website=org.website or ""
			)))

		return ContactPersonDetails(role=role, role_code=role_code, organization=org_id, country=country)


	def wdcgg_catalog_id(self, url: str, wdcgg_station_id: str, file_name: str, gas_species: str) -> str:
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

		# Observational platform ID and sampling type ID
		platform = file_name.split("_")[2].split("-")[0]
		sampling_type = file_name.split("_")[2].split("-")[1]
		if platform in WDCGG_PLATFORMS.keys():
			platform_id = WDCGG_PLATFORMS[platform]
		else:
			warnings.warn(f"Unknow platform '{platform}' used for data object {url}.")
			platform_id = ""
		if sampling_type in WDCGG_SAMPLING_TYPES.keys():
			sampling_type_id = WDCGG_SAMPLING_TYPES[sampling_type]
		else:
			warnings.warn(
				f"Unknown sampling type '{sampling_type}' used for data object {url}."
			)
			sampling_type_id = ""

		return "-".join([
			CONTRIBUTOR.zfill(4),
			wdcgg_station_id.zfill(4),
			WDCGG_GAS_SPECIES_CODES[gas_species],
			platform_id,
			sampling_type_id,
			"9999"
		])

	def instrument_history(
			self, netcdf_data: ObspackNetcdf, gas_species: str) -> list[InstrumentDeploymentWdcgg]:
		"""Format the instrument history according to WDCGG requirements.

		Returns
		-------
		A list of dictionaries where each dictionary contains information
		about one instrument's deployment according to WDCGG requirements.
		Do not return anything for observations where instruments have been
		changed more than a predefined number of times.
		"""

		instr_hist = netcdf_data.instrument_history("start_time", "instrument")
		if len(instr_hist) == 0 or len(instr_hist) > MAX_INSTRUMENTS:
			return []
		instr_hist_wdcgg: list[InstrumentDeploymentWdcgg] = []
		for deployment in instr_hist:
			instr_hist_wdcgg.append(self.instrument_deployment_to_wdcgg_format(deployment, gas_species))
		instr_hist_wdcgg[0].ih_start_date_time = "9999-12-31T00:00:00"
		instr_hist_wdcgg[-1].ih_end_date_time = "9999-12-31T23:59:59"
		return instr_hist_wdcgg

	def instrument_deployment_to_wdcgg_format(
			self, deployment: InstrumentDeployment, gas_species: str) -> InstrumentDeploymentWdcgg:
		if deployment.atc_id in self.instruments.keys():
			instr_label = self.instruments[deployment.atc_id]
		else:
			instr_query = sparql.instrument_query(deployment.atc_id)
			instr_label = sparql.run_sparql_select_query_single_param(instr_query)
			if len(instr_label) == 0:
				warnings.warn(f"Instrument ATC_{deployment.atc_id} was not found.")
				return InstrumentDeploymentWdcgg(
					ih_start_date_time=timestamp_to_str(deployment.time_period.start_time, "%Y-%m-%dT%H:%M:%S"),
					ih_end_date_time=timestamp_to_str(deployment.time_period.end_time, "%Y-%m-%dT%H:%M:%S"),
					ih_instrument="",
					mm_measurement_method_code="",
					mm_measurement_method=""
				)
			instr_label = instr_label[0]
			self.instruments[deployment.atc_id] = instr_label
		wdcgg_method_code, wdcgg_method = self.get_wdcgg_instrument_method(instr_label, gas_species)
		return InstrumentDeploymentWdcgg(
			ih_start_date_time=timestamp_to_str(deployment.time_period.start_time, "%Y-%m-%dT%H:%M:%S"),
			ih_end_date_time=timestamp_to_str(deployment.time_period.end_time, "%Y-%m-%dT%H:%M:%S"),
			ih_instrument=instr_label,
			mm_measurement_method_code=wdcgg_method_code,
			mm_measurement_method=wdcgg_method
		)

	def get_wdcgg_instrument_method(self, instr_label: str, gas_species: str) -> Tuple[str, str]:
		for vendor_or_model, wdcgg_method_info in WDCGG_METHODS.items():
			if instr_label.startswith(vendor_or_model):
				if wdcgg_method_info.code != "0":
					return wdcgg_method_info.method, wdcgg_method_info.code
				elif wdcgg_method_info.method == "Gas chromatography":
					if gas_species.lower() in ["co2", "ch4"]:
						return "Gas chromatography (FID)", "2"
					elif gas_species.lower() == "n2o":
						return "Gas chromatography (ECD)", "1"
		else:
			return "", ""

	def doi_obspack_release(self, object_spec: str) -> DoiInfo:
		query = sparql.obspack_release_query(object_spec, self.submission_window)
		dois = sparql.run_sparql_select_query_single_param(query)
		earliest, latest = sparql.submission_window_to_utc_str(self.submission_window, "%Y-%m-%d")
		if len(dois) > 1:
			warnings.warn(
				"More than one Obspack release was found in the time period "
				f"{earliest} to {latest}. The latest was used."
			)
			return DoiInfo(doi=dois[0], doi_category_code="2")
		elif len(dois) == 1:
			return DoiInfo(doi=dois[0], doi_category_code="2")
		else:
			warnings.warn(
				f"No Obspack release was found in the time period {earliest} to {latest}."
			)
			return DoiInfo(doi="", doi_category_code="9")


def get_dobj_info(dobj_url: str) -> Optional[DobjInfo]:
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
		# PID
		if dobj_meta.pid is None:
			warnings.warn(f"No PID provided for data object {dobj_url}.")
			pid = ""
		else:
			pid = dobj_meta.pid
		# Citation string
		if dobj_meta.references.citationString is None:
			warnings.warn(f"No citation string provided for data object {dobj_url}.")
			citation_string = ""
		else:
			citation_string = dobj_meta.references.citationString
		# Sampling height
		if dobj_meta.specificInfo.acquisition.samplingHeight is None:
			warnings.warn(f"No sampling height provided for data object {dobj_url}.")
			sampling_height = ""
		else:
			sampling_height = str(dobj_meta.specificInfo.acquisition.samplingHeight)
		# Authors
		if dobj_meta.references.authors is None:
			warnings.warn(f"No individual author is listed for data object {dobj_url}.")
			authors = []
		else:
			authors = [author for author in dobj_meta.references.authors if isinstance(author, Person)]
		# Gas species
		prefix_msg = f"The gas species covered by data object {dobj_url} cannot be determined"
		keywords = dobj_meta.specification.keywords
		if keywords is None:
			raise ValueError(f"{prefix_msg} for lack of keyword.")
		else:
			dobj_gas_species = set(keywords).intersection(WDCGG_GAS_SPECIES_CODES.keys())
			if len(dobj_gas_species) == 0:
				raise ValueError(f"{prefix_msg} because none of 'CO2', 'CH4', 'N2O' and 'CO' appear in the keywords.")
			elif len(dobj_gas_species) == 1:
				gas_species = list(dobj_gas_species)[0]
			else:
				raise ValueError(f"{prefix_msg} because more than one gas species were found in the keywords.")

		return DobjInfo(
			url=dobj_url,
			file_name=dobj_meta.fileName,
			pid=pid,
			citation_string=citation_string,
			station=dobj_meta.specificInfo.acquisition.station,
			sampling_height=sampling_height,
			authors=authors,
			gas_species=gas_species
		)
	else:
		warnings.warn(
			f"Data object {dobj_url} is skipped because its 'specificInfo' field is not "
			f"of type 'StationTimeSeriesMeta' but of type '{type(dobj_meta.specificInfo)}'."
		)
		return None


def timestamp_to_str(timestamp: float, fmt: str) -> str:
	return datetime.fromtimestamp(timestamp, tz=timezone.utc).strftime(fmt)