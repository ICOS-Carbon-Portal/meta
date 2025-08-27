from dataclasses import dataclass
from typing import Any, TypeAlias, Tuple
import numpy as np
import netCDF4
import pandas as pd
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

	def wdcgg_data_table(self, wdcgg_station_id: str) -> Tuple[str, pd.DataFrame]:
		"""Structure the data in a table complying with WDCGG requirements.
		
		Returns
		-------
		The name of the file according to WDCGG convention and the data
		formatted according to WDCGG template.
		"""

		conv_factor = 1e6 if self.dataset.dataset_parameter == "co2" else 1e9
		filename = "_".join(self.dataset.dataset_name.split("_")[:3] + [f"{int(float(self.dataset.dataset_intake_ht))}magl", CONTRIBUTOR, "hourly.txt"])
		table = pd.DataFrame({
			"site_wdcgg_id": wdcgg_station_id,
			"st_year": [tc[0] or -999 for tc in self.dataset.variables["time_components"][:].tolist()],
			"st_month": [tc[1] or -9 for tc in self.dataset.variables["time_components"][:].tolist()],
			"st_day": [tc[2] or -9 for tc in self.dataset.variables["time_components"][:].tolist()],
			"st_hour": [tc[3] or -9 for tc in self.dataset.variables["time_components"][:].tolist()],
			"st_minute": [tc[4] or -9 for tc in self.dataset.variables["time_components"][:].tolist()],
			"st_second": [tc[5] or -9 for tc in self.dataset.variables["time_components"][:].tolist()],
			"end_year": -999,
			"end_month": -9,
			"end_day": -9,
			"end_hour": -9,
			"end_minute": -9,
			"end_second": -9,
			"value": self.dataset.variables["value"][:]*conv_factor,
			"value_wmo_scale": self.dataset.variables["value"][:]*conv_factor,
			"value_sd": self.dataset.variables["value_std_dev"][:]*conv_factor,
			"value_unc_1": self.dataset.variables["icos_SMR"][:]*conv_factor,     # continuous measurement repeatability
			"value_unc_1_id": -9,
			"value_unc_1_method": 10,
			"value_unc_2": self.dataset.variables["icos_LTR"][:]*conv_factor,     # long term repeatability
			"value_unc_2_id": -9,
			"value_unc_2_method": 10,
			"value_unc_3": self.dataset.variables["icos_STTB"][:]*conv_factor,    # short term target bias
			"value_unc_3_id": -9,
			"value_unc_3_method": 10,
			"nvalue": self.dataset.variables["nvalue"][:],
			"latitude": self.dataset.site_latitude or -999.999999999,
			"longitude": self.dataset.site_longitude or -999.999999999,
			"altitude": float(self.dataset.site_elevation) + float(self.dataset.dataset_intake_ht) or -999999.999,
			"elevation": float(self.dataset.site_elevation) or -999999.999,
			"intake_height": float(self.dataset.dataset_intake_ht) or -999999.999,
			"flask_no": -999.999,
			"ORG_QCflag": [flag.decode() or -9 for flag in self.dataset.variables["qc_flag"][:].tolist()],
			"QCflag": -9,
			"instrument": -9,
			"measurement_method": -9,
			"scale": -9
		})
		value_columns = ["value", "value_wmo_scale", "value_sd", "value_unc_1", "value_unc_2", "value_unc_3"]
		for col in value_columns:
			table[col] = table[col].replace(np.nan, -999.999)
		table["nvalue"] = table["nvalue"].replace(np.nan, -9)
		table = table.astype({col: np.float64 for col in value_columns})
		table.loc[table["nvalue"] == 0, "value"] = -999.999
		table.loc[np.logical_or(table["nvalue"] == 0, table["nvalue"] == 1), "value_sd"] = -999.999
		return filename, table