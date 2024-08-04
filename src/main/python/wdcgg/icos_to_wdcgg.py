#!/home/jonathan-schenk/miniconda3/envs/data/bin/python

import sys
import os
from pathlib import Path
from datetime import datetime
import re
import json
from typing import Any
from wdcgg_metadata import WdcggMetadataClient, get_dobj_info
from sparql import SubmissionWindow, run_sparql_select_query_single_param, obspack_time_series_query


def parse_wdcgg_station_file() -> dict[str, str]:
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
	return {line[1]: line[0] for line in lines}


def str_to_datetime(dt: str) -> datetime:
	if re.match(r"\d{4}-\d{2}-\d{2}", dt):
		return datetime.strptime(dt, "%Y-%m-%d")
	elif re.match(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}", dt):
		return datetime.strptime(dt, "%Y-%m-%dT%H:%M:%S")
	else:
		raise ValueError(
			f"Date {dt} does not follow either of the two formats YYYY-MM-DD"
			"or YYYY-MM-DDThh:mm:ss"
		)


def write_json_to_file(json_object: list[dict[str, Any]], out_dir: Path, file_path: str) -> None:
	with open(os.path.join(out_dir, file_path), "w") as file:
		file.write(json.dumps(json_object))


if __name__ == "__main__":
	submission_window = SubmissionWindow(str_to_datetime(sys.argv[1]), str_to_datetime(sys.argv[2]))
	out_dir = Path(sys.argv[3])
	if not out_dir.exists(): out_dir.mkdir(parents=True)
	gawsis_to_wdcgg_station_id = parse_wdcgg_station_file()
	wdcgg_metadata_client = WdcggMetadataClient(submission_window, gawsis_to_wdcgg_station_id)
	sparql_query = obspack_time_series_query(submission_window)
	dobj_urls = run_sparql_select_query_single_param(sparql_query, str)
	for dobj_url in dobj_urls:
		dobj_meta = get_dobj_info(dobj_url)
		if dobj_meta is None: continue
		wdcgg_metadata_client.dobj_metadata(dobj_meta)
	write_json_to_file(wdcgg_metadata_client.metadata, out_dir, "wdcgg_metadata.json")
	write_json_to_file(wdcgg_metadata_client.contacts, out_dir, "wdcgg_contacts.json")
	write_json_to_file(wdcgg_metadata_client.organizations, out_dir, "wdcgg_organizations.json")