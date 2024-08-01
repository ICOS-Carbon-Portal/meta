from dataclasses import dataclass
from typing import Any, TypeAlias
import netCDF4

Dataset: TypeAlias = Any


@dataclass
class TimePeriod:
	start_time: float
	end_time: float

@dataclass
class InstrumentDeployment:
	atc_id: int
	time_period: TimePeriod


class ObspackNetcdf:
	def __init__(self, file_name: str, buffer: bytes):
		self.dataset: Dataset = netCDF4.Dataset(file_name, memory=buffer)

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