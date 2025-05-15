# Tool to streamline the insertion of ICOS metadata in a FAIR Data Point

A [FAIR Data Point](https://specs.fairdatapoint.org/fdp-specs-v1.0.html) (FDP) is a "metadata service that provides access to metadata following the FAIR principles". The [FDP demonstrator](https://fdpdemo.envri.eu/) created for the ENVRI-Hub NEXT project is based on the [FDP reference implementation](https://fairdatapoint.readthedocs.io/en/latest/index.html), which proposes both a GUI and API endpoints allowing the users to retrieve, create, update and query metadata from the FDP. In particular, adding metadata to the FDP can be done by `POST`-ing the content of a `Turtle` file to the appropriate endpoint.

One issue that was noticed with the FDP reference implementation is that it is impossible to `POST` a `Turtle` file containing metadata about several `Catalog`s, `Dataset`s and/or `Distribution`s. A separate file has to be produced for each entity that needs to be added and `POST`-ed individually. The tool described here enables to add metadata about a selection of ICOS L2 datasets to the FDP. The set of ICOS datasets whose metadata is added to the FDP and the list of properties describing each dataset is defined in a `SPARQL` `CONSTRUCT` query. In the current FDP demonstrator (as of 2025-05-15), the metadata was constructed with the [secondEcvDemoImport.rq](resources/secondEcvDemoImport.rq) query.

## Adding metadata about ICOS datasets to the FDP

Modifying the metadata stored in the FDP requires to be logged in, so an account must first have been created.

According to the DCAT vocabulary, `Dataset`s are listed in a `Catalog`. Therefore, a `Catalog` must exist before adding the metadata about the selection of ICOS datasets to the FDP. Creating a `Catalog` can be done either via the GUI or by `POST`-ing a `Turtle` description of the `Catalog` to the `/catalog` endpoint of the FDP, as described [here](https://fairdatapoint.readthedocs.io/en/latest/usage/api-usage.html#creating-metadata). Once the `Catalog` exists, the metadata about the ICOS datasets is added to the FDP by running the command line:

`./fdp.sc uploadL2ICOS --host {host name} --catalog {catalog ID} --limit {maximum number of datasets to add to the FDP}`

where the catalog ID is the ID generated automatically when the `Catalog` was created and that is included in the URL of the `Catalog`.

## Deleting all Datasets in a given Catalog

This functionality should be used for testing purpose only. Once `Dataset`s have been deleted, they cannot be recovered and need to be reuploaded.

Use the command line:

`./fdp.sc deleteAllDatasets --host {host name} --catalog {catalog ID}`

## Other usages of the tools

The current tool also allows to add metadata about a single `Dataset` to a given `Catalog` using the command line:

`./fdp.sc uploadDatasetFromFile --host {host name} --catalog {catalog ID} --inputTtlFile {path to Turtle file}`