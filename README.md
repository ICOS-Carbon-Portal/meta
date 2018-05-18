# ICOS Carbon Portal metadata service


Metadata service for hosting, mantaining and querying information about things like ICOS stations, people, instruments, archived data objects, etc.
It is deployed to **https://meta.icos-cp.eu/** with different services accessible via different paths:

- [/labeling/](https://meta.icos-cp.eu/labeling/): ICOS Station Labeling Step 1 web application
- [/edit/stationentry/](https://meta.icos-cp.eu/edit/stationentry/): provisional station information entry app for ICOS Head Office
- [/edit/cpmeta/](https://meta.icos-cp.eu/edit/cpmeta/): editing app for Carbon Portal's metadata
- [/sparqlclient/](https://meta.icos-cp.eu/sparqlclient/): GUI for running SPARQL queries against Carbon Portal's metadata database (RDF store)
- (example) [/objects/OPun_V09Pcat5jomRRF-5o0H](https://meta.icos-cp.eu/objects/OPun_V09Pcat5jomRRF-5o0H): landing pages for data objects registered with Carbon Portal
- (example) [/ontologies/cpmeta/DataObjectSpec](https://meta.icos-cp.eu/ontologies/cpmeta/DataObjectSpec): "landing pages" for metadata-schema concepts from Carbon Portal's ontologies
- **/upload**: the HTTP API to upload metadata packages for data object registration (see below)

Additionally, this repository contains code for the following visualization web apps (deployed as static pages):

- [Map of the provisional ICOS stations](https://static.icos-cp.eu/share/stations/)
- [Table with the provisional station info](https://static.icos-cp.eu/share/stations/table.html)

---
## Data object registration and upload instructions

This section describes the complete, general 2-step workflow for registering and uploading a data object to the Carbon Portal for archival, PID minting and possibly for being served by various data services.

### Authentication

Before you begin, make sure with the Carbon Portal's (CP) technical staff that the service is configured to accept your kind of data objects, and that there is a user account associated with the uploads you are going to make. Log in to [CPauth](https://cpauth.icos-cp.eu/) with this account using the username/password provided to you. You will be redirected to a page showing, among other things, your API token. This token is what your software must use to authenticate itself against CP services. It has validity period of 100000 seconds (about 27.8 hours).

Alternatively, the authentication token can be fetched in an automation-friendly way by HTTP-POSTing the username and password as HTML form fields `mail` and `password` to **https://cpauth.icos-cp.eu/password/login**. For example, using a popular command-line tool `curl`, it can be done as follows:

`$ curl --cookie-jar cookies.txt --data "mail=<user email>&password=<password>" https://cpauth.icos-cp.eu/password/login`

The resulting `cookies.txt` file will then contain the authentication cookie token, which can be automatically resent during later requests. (Note for developers: the file must be edited if you want to use it for tests against `localhost`).

Naturally, instead of `curl`, one can automate this process (as well as all the next steps) using any other HTTP-capable tool or programming language.

### Registering the metadata package

The first step of the 2-step upload workflow is preparing and uploading a metadata package for your data object. The package is a JSON document whose exact content depends on the data object's ICOS data level. For example, for L0 and L1 the metadata has the following format:

```json
{
	"submitterId": "ATC",
	"hashSum": "7e14552660931a5bf16f86ad6984f15df9b13efb5b3663afc48c47a07e7739c6",
	"fileName": "L0test.csv",
	"specificInfo": {
		"station": "http://meta.icos-cp.eu/resources/stations/AS_SMR",
		"acquisitionInterval": {
			"start": "2008-09-01T00:00:00.000Z",
			"stop": "2008-12-31T23:59:59.999Z"
		},
		"instrument": "http://meta.icos-cp.eu/resources/instruments/ATC_181",
		"samplingHeight": 54.8,
		"production": {
			"creator": "http://meta.icos-cp.eu/resources/people/Lynn_Hazan",
			"contributors": [],
			"hostOrganization": "http://meta.icos-cp.eu/resources/organizations/ATC",
			"comment": "free text",
			"creationDate": "2017-12-01T12:00:00.000Z"
		}
	},
	"objectSpecification": "http://meta.icos-cp.eu/resources/cpmeta/atcCo2NrtDataObject",
	"isNextVersionOf": "MAp1ftC4mItuNXH3xmAe7jZk",
	"preExistingDoi": "10.1594/PANGAEA.865618"
}
```

Clarifications:

- `submitterId` will be provided by the CP's technical people. This is not the same as username for logging in with CPauth.
- `hashSum` is so-called SHA256 hashsum. It can be easily computed from command line using `sha256sum` tool on most Unix-based systems.
- `fileName` is required but can be freely chosen by you. Every data object is stored and distributed as a single file.
- `station` is CP's URL representing the station that acquired the data. The lists of stations can be found for example here: [ATC](https://meta.icos-cp.eu/ontologies/cpmeta/AS), [ETC](https://meta.icos-cp.eu/ontologies/cpmeta/ES), [OTC](https://meta.icos-cp.eu/ontologies/cpmeta/OS).
- `objectSpecification` has to be prepared and provided by CP, but with your help. It must be specific to every kind of data object that you want to upload. Please get in touch with CP about it.
- `acquisitionInterval` (optional) is the temporal interval during which the actual measurement was performed. Required for data objects that do not get ingested completely by CP (i.e. with parsing and internal binary representation to support previews).
- `instrument` (optional) is the URL of the metadata entity representing the instrument used to perform the measurement resulting in this data object.
- `samplingHeight` (optional) is the height of the sampling (e.g. height of inlets for gas collection) in meters.
- `production` (optional) is production provenance object. It is desirable for data levels 1 and higher.
- `creator` can be an organization or a person URL.
- `contributors` must be present but can be empty. Can contain organization or people URLs.
- `hostOrganization` is optional.
- `comment` is an optional free text.
- `creationDate` is an ISO 8601 time stamp.
- `isNextVersionOf` is optional. It should be used if you are uploading a new version of a data object that is already present. The value is the SHA256 hashsum of the older data object. Both hex- and base64url representations are accepted, in either complete (32-byte) or shortened (18-byte) versions.
- `preExistingDoi` (optional) allows specifying a DOI for the data object, for example if it is also hosted elsewhere and already has a preferred DOI, or if a dedicated DOI has been minted for the object before uploading it to CP.
- `nRows` is required for some specifications where the files will be parsed and ingested for preview.

In HTTP protocol terms, the metadata package upload is performed by HTTP-POSTing its contents to `https://meta.icos-cp.eu/upload` with `application/json` content type and the authentication cookie. For example, using `curl` (`metaPackage.json` and `cookies.txt` must be in the current directory), it can be done as follows:

`$ curl --cookie cookies.txt -H "Content-Type: application/json" -X POST -d @metaPackage.json https://meta.icos-cp.eu/upload`

Alternatively, the CPauth cookie can be supplied explicitly:

`$ curl -H "Cookie: <cookie-assignment>" -H "Content-Type: application/json" -X POST -d @metaPackage.json https://meta.icos-cp.eu/upload`

### Uploading the data object
Uploading the data object itself is a simple step performed against the CP's Data service **https://data.icos-cp.eu/**.
Proceed with the upload as instructed [here](https://github.com/ICOS-Carbon-Portal/data#instruction-for-uploading-icos-data-objects)


### Creating a static collection
Carbon Portal supports creation of static collections with constant lists of immutable data objects or other static collections. The process of creating a static collection is similar to step 1 of data object upload. Here are the expected contents of the metadata package for it:
```json
{
	"submitterId": "ATC",
	"title": "Test collection",
	"description": "Optional collection description",
	"members": ["https://meta.icos-cp.eu/objects/G6PjIjYC6Ka_nummSJ5lO8SV", "https://meta.icos-cp.eu/objects/sdfRNhhI5EN_BckuQQfGpdvE"],
	"isNextVersionOf": "CkSE78VzQ3bmHBtkMLt4ogJy",
	"preExistingDoi": "10.18160/VG28-H2QA"
}
```
The fields are either self-explanatory, or have the same meaning as for the data object upload.

As with data object uploads, this metadata package must be HTTP-POSTed to `https://meta.icos-cp.eu/upload` with `application/json` content type and the CP authentication cookie. The server will reply with landing page of the collection. The last segment of the landing page's URL is collections ID that is obtained by SHA-256-hashsumming of the alphabetically sorted list of members' hashsums (it is base64url representations of the hashsums that are sorted, but it is binary values that contribute to the collections' hashsum).

### Administrative API for RDF updates
Intended for internal use at Carbon Portal.
All the updates need to go through the RDF logs, therefore SPARQL UPDATE protocol could not be used directly. Instead, one needs to HTTP POST a SPARQL CONSTRUCT query, that will produce the triples that need to be inserted/retracted, to a URL of the form:

`https://meta.icos-cp.eu/admin/<insert | delete>/<instance-server id>` ,

where `instance-server id` is the id of the instance server that will be affected by the change, as specified in `meta`'s config file.

To be allowed to perform the operation, one needs to be a on the `adminUsers` list in the config (`cpmeta.sparql.adminUsers`). Here is a `curl` example of the API usage:

`curl --upload-file sparql.rq -H "Cookie: cpauthToken=<the token>" https://meta.icos-cp.eu/admin/delete/sitescsv?dryRun=true`

The output will show the resulting changes. If `dryRun` is `true`, no actual changes are performed, only the outcome is shown.

---

## Information for developers

### Getting started with the front-end part

- Install `Node.js` as instructed [here](https://github.com/nodesource/distributions)
- Clone this repository: `git clone git@github.com:ICOS-Carbon-Portal/meta.git`
- `cd meta`
- Install Node.js dependencies: `npm install`
- Now you can run Gulp tasks: `npm run <task>` (see `package.json` for the list of defined tasks)

### Getting started with the back-end part

- Set up a Docker container with PostgreSQL for RDF log (see the [infrastructure project](https://github.com/ICOS-Carbon-Portal/infrastructure/tree/master/rdflogdb))
- Make a copy of `example.application.conf` file in the project root named `application.conf` and edit it to suit your environment. For some default config values, see `application.conf` in `src/main/resources/`. For deployment, make sure there is a relevant `application.conf` in the JVM's working directory.
- Run sbt
- In the sbt console, run `~reStart` for continuous local rebuilds and server restarts. Alternatively, if the development is done only in the front end part, running `~copyResources` is sufficient but much faster.

### Miscellaneous recipes
#### Autorendering README.md to HTML for preview on file change
Make sure that Python is available, and `python-markdown` and `inotify-tools` packages are installed on your Linux system.
Then you can run:

`$ while inotifywait -e close_write README.md; do python -m markdown README.md > README.html; done`
