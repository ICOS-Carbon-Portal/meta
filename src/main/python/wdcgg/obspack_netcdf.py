from dataclasses import dataclass
from typing import Any, TypeAlias, Tuple
import netCDF4
import pandas as pd
from icoscp_core.icos import data


Dataset: TypeAlias = Any

CONTRIBUTOR = "159"

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

	def wdcgg_data_table(self) -> Tuple[str, pd.DataFrame]:
		"""Structure the data in a table complying with WDCGG requirements.
		
		Returns
		-------
		The name of the file according to WDCGG convention and the data
		formatted according to WDCGG template.
		"""

		conv_factor = 1e6 if self.dataset.dataset_parameter == "co2" else 1e9
		filename = "_".join(self.dataset.dataset_name.split("_")[:3] + [f"{int(float(self.dataset.dataset_intake_ht))}magl", CONTRIBUTOR, "hourly.txt"])
		table = pd.DataFrame({
			"site_gaw_id": self.dataset.site_code,
			"year": [tc[0] for tc in self.dataset.variables["time_components"][:].tolist()],
			"month": [tc[1] for tc in self.dataset.variables["time_components"][:].tolist()],
			"day": [tc[2] for tc in self.dataset.variables["time_components"][:].tolist()],
			"hour": [tc[3] for tc in self.dataset.variables["time_components"][:].tolist()],
			"minute": [tc[4] for tc in self.dataset.variables["time_components"][:].tolist()],
			"second": [tc[5] for tc in self.dataset.variables["time_components"][:].tolist()],
			"year_final": -999,
			"month_final": -9,
			"day_final": -9,
			"hour_final": -9,
			"minute_final": -9,
			"second_final": -9,
			"value": self.dataset.variables['value'][:]*conv_factor,
			"value_unc": self.dataset.variables['value_std_dev'][:]*conv_factor,
			"nvalue": self.dataset.variables['nvalue'][:],
			"latitude": self.dataset.site_latitude,
			"longitude": self.dataset.site_longitude,
			"altitude": float(self.dataset.site_elevation) + float(self.dataset.dataset_intake_ht),
			"elevation": float(self.dataset.site_elevation),
			"intake_height": float(self.dataset.dataset_intake_ht),
			"flask_no": -999.999,
			"ORG_QCflag": [flag.decode() for flag in self.dataset.variables["qc_flag"][:].tolist()],
			"QCflag": -9
		})
		return filename, table