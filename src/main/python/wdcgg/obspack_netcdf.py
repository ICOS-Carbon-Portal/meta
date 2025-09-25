from dataclasses import dataclass
from typing import Any, TypeAlias, Callable
import numpy as np
from numpy.typing import ArrayLike
import netCDF4
from icoscp_core.icos import data


Dataset: TypeAlias = Any

CONTRIBUTOR = "162"

@dataclass
class TimePeriod:
	start_time: float
	end_time: float

@dataclass
class InstrumentDeployment:
	atc_id: int
	time_period: TimePeriod


class ObspackNetcdf:
	def __init__(self, url: str):
		file_name, buffer = data.get_file_stream(url)
		self.dataset: Dataset = netCDF4.Dataset(file_name, memory=buffer.read())

	def instrument_history(self, time_var: str, instr_var: str) -> list[InstrumentDeployment]:
		"""List which instruments were used and when.

		Parameters
		----------
		time_var : str
			Name of the time variable in the netCDF file.
		instr_var : str
			Name of the instrument variable in the netCDF file.

		Returns
		-------
		A list of InstrumentDeployment objects containing the ATC ID
		of the instrument, and the start and end time of the deployment.
		"""

		time = self.dataset.variables[time_var][:]
		instr = self.dataset.variables[instr_var][:]

		tstart = time[0]
		current_instr = instr[0]
		deployments: list[InstrumentDeployment] = []
		for n, t in enumerate(time[:-1]):
			if instr[n + 1] != current_instr:
				depl = InstrumentDeployment(current_instr, TimePeriod(tstart, t))
				deployments.append(depl)
				tstart = time[n + 1]
				current_instr = instr[n + 1]
		else:
			depl = InstrumentDeployment(current_instr, TimePeriod(tstart, time[-1]))
			deployments.append(depl)

		return deployments

	def wdcgg_filename(self) -> str:
		"""Build a file name according to WDCGG convention.

		Returns
		-------
		The file name according to WDCGG convention.
		"""
		first_components = self.dataset.dataset_name.split("_")[:3]
		last_components = [f"{int(float(self.dataset.dataset_intake_ht))}magl", CONTRIBUTOR, "hourly.txt"]
		return "_".join(first_components + last_components)

	def wdcgg_data_table(self, wdcgg_station_id: str) -> str:
		"""Structure the data in a table complying with WDCGG requirements.
		
		Parameters
		----------
		wdcgg_station_id : str
			WDCGG ID of the station.

		Returns
		-------
		The data formatted according to WDCGG's template.
		"""

		# Helper functions
		to_str: Callable[[int | float | str, int], str] = lambda x, n: str(x).zfill(n)
		to_str = np.vectorize(to_str)
		replace_no_value_placeholder: Callable[[str, str, str], str] = lambda x, old, new: new if x == old else x
		replace_no_value_placeholder = np.vectorize(replace_no_value_placeholder)
		# Conversion from 4-bytes float used in ObsPack netCDF files to Python native 8-bytes float,
		# conversion to proper units and text formatting for consistency.
		float32_to_str_with_conversion: Callable[[np.float32, float], str] = \
			lambda v, conv_factor: f"{float(str(v))*conv_factor:.3f}" if not np.isnan(v) else "-999.999"
		to_str_with_default: Callable[[float | int | str | None, str], str] = \
			lambda x, default: str(x) if x is not None else default

		# Conversion from netCDF content to various values, lists and NumPy arrays
		n: int = self.dataset.variables["time"].shape[0]
		time_components: list[ArrayLike] = list(
			map(
				lambda tup: to_str(np.array(tup), 2),
				zip(*self.dataset.variables["time_components"][:].tolist())))
		conv_factor = 1e6 if self.dataset.dataset_parameter == "co2" else 1e9
		values = {
			var: np.array(
				[float32_to_str_with_conversion(v, conv_factor) for v in self.dataset.variables[var][:].data],
				dtype="=U12")
			for var in ["value", "value_std_dev", "icos_SMR", "icos_LTR", "icos_STTB"]}
		nvalue = self.dataset.variables["nvalue"][:].data
		qc_flag = np.array([
			flag.decode() if flag is not None else "-999.999" for flag in self.dataset.variables["qc_flag"][:].tolist()])
		valid = np.logical_or(np.logical_or(qc_flag == "U", qc_flag == "O"), qc_flag == "R")
		nvalue[np.logical_and(valid, nvalue == 0)] = -9
		values["value_std_dev"][nvalue == 1] = "-999.999"
		latitude = to_str_with_default(self.dataset.site_latitude, "-999.999999999")
		longitude = to_str_with_default(self.dataset.site_longitude, "-999.999999999")
		elevation = to_str_with_default(self.dataset.site_elevation, "-999999.999")
		intake_height = to_str_with_default(self.dataset.dataset_intake_ht, "-999999.999")
		altitude = str(np.round(float(self.dataset.site_elevation) + float(self.dataset.dataset_intake_ht), 3)) \
			if self.dataset.site_elevation is not None and self.dataset.dataset_intake_ht is not None else "-999999.999"

		# Content of the table
		columns: dict[str, ArrayLike | list[str]] = {
			"site_wdcgg_id": [wdcgg_station_id]*n,
			"st_year": replace_no_value_placeholder(time_components[0], "-9", "-999"),
			"st_month": time_components[1],
			"st_day": time_components[2],
			"st_hour": time_components[3],
			"st_minute": time_components[4],
			"st_second": time_components[5],
			"end_year": ["-999"]*n,
			"end_month": ["-9"]*n,
			"end_day": ["-9"]*n,
			"end_hour": ["-9"]*n,
			"end_minute": ["-9"]*n,
			"end_second": ["-9"]*n,
			"value": values["value"],
			"value_wmo_scale": values["value"],
			"value_sd": values["value_std_dev"],
			"value_unc_1": values["icos_SMR"],     # continuous measurement repeatability
			"value_unc_1_id": ["1"]*n,
			"value_unc_1_method": ["10"]*n,
			"value_unc_2": values["icos_LTR"],     # long term repeatability
			"value_unc_2_id": ["1"]*n,
			"value_unc_2_method": ["10"]*n,
			"value_unc_3": values["icos_STTB"],    # short term target bias
			"value_unc_3_id": ["1"]*n,
			"value_unc_3_method": ["10"]*n,
			"nvalue": to_str(nvalue, 0),
			"latitude": [latitude]*n,
			"longitude": [longitude]*n,
			"altitude": [altitude]*n,
			"elevation": [elevation]*n,
			"intake_height": [intake_height]*n,
			"flask_no": ["-999.999"]*n,
			"ORG_QCflag": qc_flag,
			"QCflag": ["-9"]*n,
			"instrument": ["-9"]*n,
			"measurement_method": ["-9"]*n,
			"scale": ["-9"]*n
		}
		return "\n".join([" ".join(columns.keys())] + [" ".join(vals) for vals in zip(*columns.values())])
