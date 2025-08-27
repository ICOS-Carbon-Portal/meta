#!/home/jonathan-schenk/miniconda3/envs/data/bin/python

import sys
import os
from pathlib import Path
from datetime import datetime
import json
import warnings
from typing import Any
from wdcgg_metadata import WdcggMetadataClient, get_dobj_info
from obspack_netcdf import ObspackNetcdf
from sparql import SubmissionWindow, run_sparql_select_query_single_param, obspack_time_series_query


def parse_wdcgg_station_file() -> dict[str, dict[str, str]]:
	"""Parse the WDCGG station file to produce a conversion dictionary
	from GAWSIS station IDs to WDCGG station IDs.
	
	Returns
	-------
	A dictionary where the keys correspond to GAWSIS station IDs and the values
	correspond to WDCGG station IDs.
	"""
	
	wdcgg_station_file = "station.csv"
	with open(wdcgg_station_file, "r") as f:
		lines = f.read().split("\n")[1:-1]
	lines = [[val.strip('"') for val in line.split(",")] for line in lines]
	lookup_dict = {line[1]: {"4-digit": line[0], "wdcgg-id": line[2]} for line in lines}
	additional_entries = {
		"KRE": {"4-digit": "6379", "wdcgg-id": "KRE6379"},
		"SAC": {"4-digit": "6423", "wdcgg-id": "SAC6423"}
	}
	for gaw_id, wdcgg_ids in additional_entries.items():
		if gaw_id not in lookup_dict.keys():
			lookup_dict[gaw_id] = wdcgg_ids
	return lookup_dict


def write_json_to_file(json_object: list[dict[str, Any]], out_dir: Path, file_path: str) -> None:
	with open(os.path.join(out_dir, file_path), "w") as file:
		file.write(json.dumps(json_object))


if __name__ == "__main__":
	submission_window = SubmissionWindow(
		datetime.fromisoformat(sys.argv[1]), datetime.fromisoformat(sys.argv[2]))
	out_dir = Path(sys.argv[3])
	if not out_dir.exists(): out_dir.mkdir(parents=True)
	gawsis_to_wdcgg_station_id = parse_wdcgg_station_file()
	wdcgg_metadata_client = WdcggMetadataClient(submission_window)
	sparql_query = obspack_time_series_query(submission_window)
	dobj_urls = run_sparql_select_query_single_param(sparql_query, str)
	for dobj_url in dobj_urls:
		dobj_meta = get_dobj_info(dobj_url)
		if dobj_meta is None: continue
		print(dobj_meta.file_name)
		if dobj_meta.station.id not in gawsis_to_wdcgg_station_id.keys():
			warnings.warn(f"Station {dobj_meta.station.id} is not registered in GAWSIS.")
			wdcgg_station_id = "-"
			old_wdcgg_station_id = ""
		else:
			wdcgg_station_id = gawsis_to_wdcgg_station_id[dobj_meta.station.id]["wdcgg-id"]
			old_wdcgg_station_id = gawsis_to_wdcgg_station_id[dobj_meta.station.id]["4-digit"]
		netcdf_data = ObspackNetcdf(dobj_url)
		data_file, data_table = netcdf_data.wdcgg_data_table(wdcgg_station_id)
		data_table.to_csv(os.path.join(out_dir, data_file), sep=" ", index=False)
		wdcgg_metadata_client.dobj_metadata(dobj_meta, netcdf_data, old_wdcgg_station_id)
	write_json_to_file(wdcgg_metadata_client.metadata, out_dir, "wdcgg_metadata.json")
	write_json_to_file(wdcgg_metadata_client.contacts, out_dir, "wdcgg_contacts.json")
	write_json_to_file(wdcgg_metadata_client.organizations, out_dir, "wdcgg_organizations.json")