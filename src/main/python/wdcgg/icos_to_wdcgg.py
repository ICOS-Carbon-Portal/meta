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


def write_json_to_file(json_object: list[dict[str, Any]], file_path: str) -> None:
	with open(file_path, "w") as file:
		file.write(json.dumps(json_object))


if __name__ == "__main__":
	submission_window = SubmissionWindow(str_to_datetime(sys.argv[1]), str_to_datetime(sys.argv[2]))
	output_dir = Path(sys.argv[3])
	if not output_dir.exists(): output_dir.mkdir(parents=True)
	gawsis_to_wdcgg_station_id = parse_wdcgg_station_file()
	sparql_query = obspack_time_series_query(submission_window)
	dobj_urls = run_sparql_select_query_single_param(sparql_query, str)
	wdcgg_metadata_client = WdcggMetadataClient(submission_window, gawsis_to_wdcgg_station_id)
	for dobj_url in dobj_urls:
		dobj_meta = get_dobj_info(dobj_url)
		if dobj_meta is None: continue
		wdcgg_metadata_client.dobj_metadata(dobj_meta)
	metadata_file_path = os.path.join(output_dir, "wdcgg_metadata.json")
	contacts_file_path = os.path.join(output_dir, "wdcgg_contacts.json")
	orgs_file_path = os.path.join(output_dir, "wdcgg_organizations.json")
	write_json_to_file(wdcgg_metadata_client.metadata, metadata_file_path)
	write_json_to_file(wdcgg_metadata_client.contacts, contacts_file_path)
	write_json_to_file(wdcgg_metadata_client.organizations, orgs_file_path)