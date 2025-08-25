#!/usr/bin/python3

import sys
import json
from pathlib import Path

# Dictionary to update before running the script by comparing the organization metadata
# produced by the main script with the list of organization available at:
# https://gaw.kishou.go.jp/documents/db_list/organization
# For organizations that are not included in the WDCGG list, start with a higher value than
# the highest one already in use in the list.
ORGANIZATION_CODE_CONVERSION = {
	'1': '54',
	'2': '177',
	'3': '37',
	'4': '19',
	'5': '178',
	'6': '157',
	'7': '179',
	'8': '3',
	'9': '180',
	'10': '181',
	'11': '24',
	'12': '182',
	'13': '183',
	'14': '25',
	'15': '64',
	'16': '45',
	'17': '77',
	'18': '184',
	'19': '185',
	'20': '186',
	'21': '187',
	'22': '71',
	'23': '188',
	'24': '189'
}

# Code of the first organization that is not included in the WDCGG list (see link above).
FIRST_NEW_ORGANIZATION_CODE = 177

# Known contact errors in ICOS metadata.
CONTACT_CORRECTIONS: dict[str, dict[str, str]] = {
	"ps_email": {
		# The following email corrections were mentioned by Okajima Shingo
		# in four different emails on 2024-09-25.
		"j.m.pichon@opgc.fr": "pichon@opgc.univ-bpclermont.fr",
		"crl@nilu.no": "chris.lunder@nilu.no",
		"s.odoherty@bris.ac.uk": "s.odoherty@bristol.ac.uk",
		"leuenberger@climate.unibe.ch": "markus.leuenberger@unibe.ch"
	}
}

def correct_organizations(path_to_org_metadata: Path) -> None:
	with open(path_to_org_metadata, "r") as original_file:
		orgs = json.load(original_file)
	orgs_to_keep: list[dict[str, str]] = []
	for org in orgs:
		new_org_code = ORGANIZATION_CODE_CONVERSION[org["or_organization_code"]]
		if int(new_org_code) >= FIRST_NEW_ORGANIZATION_CODE:
			org["or_organization_code"] = new_org_code
			orgs_to_keep.append(org)
	orgs_to_keep = sorted(orgs_to_keep, key=lambda org: (org["or_organization_code"]))
	path_to_new_file = Path(path_to_org_metadata.parent, f"corrected_{path_to_org_metadata.name}")
	save_content_to_file(path_to_new_file, orgs_to_keep)

def correct_contacts(path_to_contact_metadata: Path) -> None:
	with open(path_to_contact_metadata, "r") as original_file:
		contacts = json.load(original_file)
	for contact in contacts:
		contact["or_organization_code"] = ORGANIZATION_CODE_CONVERSION[contact["or_organization_code"]]
		for key, corrections in CONTACT_CORRECTIONS.items():
			if contact[key] in corrections.keys():
				contact[key] = corrections[contact[key]]
	path_to_new_file = Path(path_to_contact_metadata.parent, f"corrected_{path_to_contact_metadata.name}")
	save_content_to_file(path_to_new_file, contacts)

def save_content_to_file(path_to_file: Path, content: list[dict[str, str]]) -> None:
	save_file = "y"
	if path_to_file.exists():
		save_file = input(f"File {path_to_file} already exists. Overwrite? (y/n) ")
	if save_file in ["y", "yes", "Yes"]:
		with open(path_to_file, "w") as corrected_file:
			json.dump(content, corrected_file)

if __name__ == "__main__":
	correct_organizations(Path(sys.argv[1]))
	correct_contacts(Path(sys.argv[2]))