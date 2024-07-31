#!/home/jonathan-schenk/miniconda3/envs/data/bin/python

import sys
import json
import jsonschema

if __name__ == "__main__":
	json_schema_file = sys.argv[1]
	json_file = sys.argv[2]

	with open(json_schema_file, "r") as json_schema_f:
		json_schema = json.loads(json_schema_f.read())

	validator = jsonschema.Draft7Validator(json_schema)

	with open(json_file, "r") as json_f:
		json_content = json.loads(json_f.read())

	validator.validate(json_content)

	# If the validation fails, a ValidationError is raised. Therefore,
	# the next message is only printed if the validation is successful.
	print("Validation was successful.")