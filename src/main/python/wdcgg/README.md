# Tool to produce data and metadata files formatted for WDCGG processing

Before using the tool, make sure to have the [most recent version](https://gaw.kishou.go.jp/documents/db_list/station) of the [station list](station.csv). Also, change the path on the first line of [`icos_to_wdcgg.py`](icos_to_wdcgg.py) to a Python binary file in an environment where all libraries required to run the tool are available.

To produce the data and metadata files to be delivered to WDCGG, run:

`./icos_to_wdcgg.py {earliest_submission_time} {latest_submission_time} {output_directory}`

where `earliest_submission_time` and `latest_submission_time` are the limits of the time period during which uploaded data objects will be considered, and `output_directory` corresponds to the directory where the files that are created will be saved. Only data objects with specifications "**Obspack CO2 time-series result**", "**Obspack CH4 time-series result**", "**Obspack N2O time-series result**" and "**Obspack CO time-series result**", and which were uploaded during the specified time period, are considered. The script will produce one data file (txt format) for each data object and three `JSON` files in total (containing metadata about datasets, contact persons and organizations respectively).

If some messages stating that "Station XXX is not registered in GAWSIS." appear, check whether these stations are included in the [station list](station.csv) but with a missing `GAW ID`. In such cases, WDCGG IDs for the missing stations can be added to the `additional_entries` dictionary in the  `parse_wdcgg_station_file` function in [`icos_to_wdcgg.py`](icos_to_wdcgg.py). Rerun the `icos_to_wdcgg.py` script after having added the missing stations.

Once all data objects could be processed, check the file containing metadata about organizations and compare the organization codes with the ones in the curated [WDCGG organization list](https://gaw.kishou.go.jp/documents/db_list/organization). Adjust the `ORGANIZATION_CODE_CONVERSION` dictionary in [correct_metadata.py](correct_metadata.py) so that it matches codes currently used in the organization metadata file (keys of the dictionary) to codes provided in the curated list (values of the dictionary). For organizations that are not included in the curated list, use codes that are higher than the highest code used in the curated list. Adjust the `FIRST_NEW_ORGANIZATION_CODE` variable accordingly in [correct_metadata.py](correct_metadata.py). After making these changes, run:

`./correct_metadata.py {path_to_organizations_metadata_file} {path_to_contacts_metadata_file}`

This will produce two additional files with corrected metadata about organizations and contact persons, respectively. Check these two files and manually change whatever typos, wrong acronyms or email addresses, etc. might have been fetched from the ICOS metadata.

As a last step, check whether the `JSON` metadata files match the WDCGG metadata templates. For each one of the three metadata files, run:

`./validate_json.py {path_to_schema_file} {path_to_metadata_file}`

`JSON` schema files are available in [json_schemas](json_schemas). As of 2025-08-25, the schemas to be used are:
- [json_schemas/metadata.json_schema_202506.json](json_schemas/metadata.json_schema_202506.json) for the metadata file about datasets
- [json_schemas/contact.json_schema_202106.json](json_schemas/contact.json_schema_202106.json) for the metadata file about contact persons
- [json_schemas/organization.json_schema_202106.json](json_schemas/organization.json_schema_202106.json) for the metadata file about organizations

If the validation fails, correct the metadata files that do not pass the validation.