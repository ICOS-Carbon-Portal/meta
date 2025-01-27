# ICOS Carbon Portal metadata service

Metadata service for hosting, mantaining and querying information about things like ICOS stations, people, instruments, archived data objects, etc.
It is deployed to **https://meta.icos-cp.eu/** with different services accessible via different paths:

- [/uploadgui/](https://meta.icos-cp.eu/uploadgui/): web application for data/document object upload and collection creation (see instructions for manual upload below). 
- [/labeling/](https://meta.icos-cp.eu/labeling/): ICOS Station Labeling Step 1 web application
- [/edit/stationentry/](https://meta.icos-cp.eu/edit/stationentry/): provisional station information entry app for ICOS Head Office
- [/edit/labeling/](https://meta.icos-cp.eu/edit/labeling/): administrative interface for station labeling metadata
- [/edit/cpmeta/](https://meta.icos-cp.eu/edit/cpmeta/): editing app for Carbon Portal's metadata
- [/edit/otcentry/](https://meta.icos-cp.eu/edit/otcentry/): editing app for OTC's metadata (used for ICOS metadata flow, testing mode for now)
- [/edit/icosmeta/](https://meta.icos-cp.eu/edit/icosmeta/): viewing app for the test ICOS metadata produced by the metadata flow machinery
- [/sparqlclient/](https://meta.icos-cp.eu/sparqlclient/): GUI for running SPARQL queries against Carbon Portal's metadata database (RDF store)
- (example) [/objects/OPun_V09Pcat5jomRRF-5o0H](https://meta.icos-cp.eu/objects/OPun_V09Pcat5jomRRF-5o0H): landing pages for data objects registered with Carbon Portal
- (example) [/ontologies/cpmeta/DataObjectSpec](https://meta.icos-cp.eu/ontologies/cpmeta/DataObjectSpec): "landing pages" for metadata-schema concepts from Carbon Portal's ontologies
- **/upload**: the HTTP API to upload metadata packages for data object registration (see below)

---

## Upload instructions (manual)

Manual uploads of data/document objects and collection creation can be performed using [UploadGUI](https://meta.icos-cp.eu/uploadgui/) web app. Users need permissions and prior design of data object specifications in collaboration with the CP. Metadata of existing objects and collections can be updated later, using the same app.

---

## Upload instructions (scripting)

This section describes the complete, general 2-step workflow for registering and uploading a data object to the Carbon Portal for archival, PID minting and possibly for being served by various data services.

### Authentication

Before you begin, make sure with the Carbon Portal's (CP) technical staff that the service is configured to accept your kind of data objects, and that there is a user account associated with the uploads you are going to make.
Log in to [CPauth](https://cpauth.icos-cp.eu/) with this account.
You will be redirected to a page showing, among other things, your API token.
This token is what your software must use to authenticate itself against CP services.
It has validity period of 100000 seconds (about 27.8 hours).

Alternatively, the authentication token can be fetched in an automation-friendly way by HTTP-POSTing the username and password as HTML form fields `mail` and `password` to **https://cpauth.icos-cp.eu/password/login**. For example, using a popular command-line tool `curl` on Linux, it can be done as follows:

`$ curl --cookie-jar cookies.txt --data "mail=user_email&password=user_password" https://cpauth.icos-cp.eu/password/login`

(please note that both email and the password strings must be URL-encoded, at least when they contain special characters, such as e.g. `+`, `$`, `&`, or spaces; encoding can be done for example using `encodeURIComponent()` function of any Web browser's Javascript console)

The resulting `cookies.txt` file will then contain the authentication cookie token, which can be automatically resent during later requests. (Note for developers: the file must be edited if you want to use it for tests against `localhost`).

Naturally, instead of `curl`, one can automate this process (as well as all the next steps) using any other HTTP-capable tool or programming language.

### Registering the metadata package

The first step of the 2-step upload workflow is preparing and uploading a metadata package for your data object. The package is a JSON document whose exact content depends on the kind of data object. There are two specific kinds that are recognized: station-specific time series data objects and spatiotemporal data objects (which may optionally also be station-specific). For the former kind, the metadata has the following format:

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
			"creationDate": "2017-12-01T12:00:00.000Z",
			"sources": ["utw3ah9Fo7_Sp7BN5i8z2vbK"],
			"documentation": "_Vb_c34v0nfTA_fG0kiIAmXM"
		}
	},
	"objectSpecification": "http://meta.icos-cp.eu/resources/cpmeta/atcCo2NrtDataObject",
	"isNextVersionOf": "MAp1ftC4mItuNXH3xmAe7jZk",
	"preExistingDoi": "10.1594/PANGAEA.865618",
	"references": {
		"keywords": ["CO2", "meteo"],
		"licence": "https://creativecommons.org/publicdomain/zero/1.0/",
		"moratorium": "2018-03-01T00:00:00Z",
		"duplicateFilenameAllowed": false
	}
}
```

For the spatiotemporal data objects, the metadata package has the same general structure, but `specificInfo` property differs, and should look as follows:

```json
{
	"title": "JenaCarboScopeRegional inversion results for EUROCOM",
	"description": "JenaCarboScopeRegional inverse modelling estimates of European CO2 fluxes for 2006-2015 as part of the EUROCOM inversion...",
	"spatial": "http://meta.icos-cp.eu/resources/latlonboxes/europeLatLonBoxIngos",
	"temporal": {
		"interval": {
			"start": "2006-01-01T00:00:00Z",
			"stop": "2015-12-31T00:00:00Z"
		},
		"resolution": "monthly"
	},
	"production": {
		//same as for station-specific time series
	},
	"forStation": "http://meta.icos-cp.eu/resources/stations/AS_SMR",
	"samplingHeight": 50.5,
	"customLandingPage": "http://www.bgc-jena.mpg.de/CarboScope/?ID=s99_v3.7",
	"variables": ["co2flux_land", "co2flux_ocean"]
}
```

Clarifications:

- `submitterId` will be provided by the CP's technical people. This is not the same as username for logging in with CPauth.
- `hashSum` is so-called SHA256 hashsum. It can be easily computed from command line using `sha256sum` tool on most Unix-based systems. It's a 32-byte binary sequence, and must be represented as a string property, containing either hex or base64 encoding.
- `fileName` is required but can be freely chosen by you. Every data object is stored and distributed as a single file.
- `specificInfo` for station-specific time series objects
	- `station` is CP's URL representing the station that acquired the data. The lists of stations can be found for example here: [ATC](https://meta.icos-cp.eu/ontologies/cpmeta/AS), [ETC](https://meta.icos-cp.eu/ontologies/cpmeta/ES), [OTC](https://meta.icos-cp.eu/ontologies/cpmeta/OS).
	- `acquisitionInterval` (optional) is the temporal interval during which the actual measurement was performed. Required for data objects that do not get ingested completely by CP (i.e. with parsing and internal binary representation to support previews).
	- `instrument` (optional) is the URL of the metadata entity representing the instrument used to perform the measurement resulting in this data object.
	- `samplingHeight` (optional) is the height of the sampling (e.g. height of inlets for gas collection) in meters.
	- `production` (optional) is production provenance object. It is desirable for data levels 1 and higher.
		- `creator` can be an organization or a person URL.
		- `contributors` must be present but can be empty. Can contain organization or people URLs.
		- `hostOrganization` is optional.
		- `comment` is an optional free text.
		- `creationDate` is an ISO 8601 time stamp.
		- `sources` (optional) is an array of source data objects, that the current one was produced from, referred to as hashsums. Both hex- and base64url representations are accepted, in either complete (32-byte) or shortened (18-byte) versions.
		- `documentation` (optional) hashsum of a document object containing information specific to production of this data object.
	- `nRows` is the number of data rows (the total number of rows minus the number of header rows) and is required for some specifications where the files will be parsed and ingested for preview.
- `specificInfo` for spatiotemporal objects
	- `title` is a required string.
	- `description` is an optional string.
	- `spatial` is either a lat/lon bounding box, or a string with url reference to a reusable spacial coverage object, or a string with GeoJSON representation of the object-specific geo-coverage; in the first case, it's an object with the following properties:
		- `min` containing numeric `lat` and `lon` (WGS84).
		- `max` containing numeric `lat` and `lon` (WGS84).
		- `label` is a optional string to describe the spacial coverage.
	- `temporal` is the temporal coverage.
		- `interval` containing `start` and `stop` timestamps.
		- `resolution` (optional) is a string indicating the resolution of the dataset.
	- `production` (required) is identical to `production` for station-specific time series.
	- `forStation` (optional) is a url of a station the data object is related to.
	- `samplingHeight` (optional) floating-point sampling height in meters. Will typically refer to a simulation parameter, not an experimental sampling height.
	- `customLandingPage` (optional) is a url linking to the data hosted somewhere else.
	- `variables` (optional) is a list of strings with variable names. Needed to make the variables previewable. The variables must have been earlier defined in the corresponding dataset specification.
- `objectSpecification` has to be prepared and provided by CP, but with your help. It must be specific to every kind of data object that you want to upload. Please get in touch with CP about it.
- `isNextVersionOf` is optional. It should be used if you are uploading a new version of a data object(s) that is(are) already present. The value is the SHA256 hashsum of the older data object (or an array of the hashsums, if they are more than one). Both hex- and base64url representations are accepted, in either complete (32-byte) or shortened (18-byte) versions.
- `preExistingDoi` (optional) allows specifying a DOI for the data object, for example if it is also hosted elsewhere and already has a preferred DOI, or if a dedicated DOI has been minted for the object before uploading it to CP.
- `references` (optional) JSON object with additional "library-like" information; the list of its properties is planned to grow in the future.
	- `keywords` (optional) an array of strings to be used as keywords specific to this particular object. Please note that CP metadata allows specifying keywords also on the data object specification (data type) level, and on the project level. Keywords common to all data objects of a certain data type should be associated directly with the corresponding specification (this is done by CP staff on request from the data uploaders).
	- `licence` (optional) URL that identifies a licence for the data object. If not specified, the licence associated with the data object specification ("data type") is used. If no licence is associated with a data object spec, then the licence associated with the project (that the spec is associated with) is used. Finally, if no licence is associated with the project, then CP (or SITES) data licence is used by default (depending on the ENVRI). The licence URL must come from a list of supported licences, for example:
		+ http://meta.icos-cp.eu/ontologies/cpmeta/icosLicence (ICOS default)
		+ https://meta.fieldsites.se/ontologies/sites/sitesLicence (SITES default)
		+ https://creativecommons.org/publicdomain/zero/1.0/ (CC0 1.0 Public Domain)
		+ (the up-to-date list of supported licences can be obtained using [SPARQL client](https://meta.icos-cp.eu/sparqlclient/) and query `select * where{?licence a <http://purl.org/dc/terms/LicenseDocument>}`)
	- `moratorium` (optional) is an ISO 8601 timestamp with the desired publication time in the future (instead of the moment of data upload). The data object will be prevented from being downloaded until the moratorium expires.
	- `duplicateFilenameAllowed` (optional) boolean flag signalling upload of (potentially) duplicate-filename data/doc object without deprecating the existing object(s) with the same filename (and the same [object format](https://meta.icos-cp.eu/ontologies/cpmeta/ObjectFormat), in case of data objects).
	- `autodeprecateSameFilenameObjects` (optional) boolean flag requesting that all the existing non-deprecated data/doc objects with the same filename (and the same [object format](https://meta.icos-cp.eu/ontologies/cpmeta/ObjectFormat), in case of data objects), as the one being uploaded, will be automatically deprecated by this upload.
	- `partialUpload` (optional) boolean flag signalling that the data/doc object being uploaded is expected to be a part of a group, together deprecating a single other object; if the flag is set to 'true', then the deprecated single object must be specified in the `isNextVersionOf` property of this metadata package.

In HTTP protocol terms, the metadata package upload is performed by HTTP-POSTing its contents to `https://meta.icos-cp.eu/upload` with `application/json` content type and the authentication cookie. For example, using `curl` (`metaPackage.json` and `cookies.txt` must be in the current directory), it can be done as follows:

`$ curl --cookie cookies.txt -H "Content-Type: application/json" -X POST -d @metaPackage.json https://meta.icos-cp.eu/upload`

Alternatively, the CPauth cookie can be supplied explicitly:

`$ curl -H "Cookie: <cookie-assignment>" -H "Content-Type: application/json" -X POST -d @metaPackage.json https://meta.icos-cp.eu/upload`

### Uploading the data object

Uploading the data object itself is a simple step performed against the CP's Data service **https://data.icos-cp.eu/**.
Proceed with the upload as instructed [here](https://github.com/ICOS-Carbon-Portal/data#instruction-for-uploading-icos-data-objects)

### Uploading document objects

In addition to data objects who have properties as data level, data object specification, acquisition and production provenance, there is a use case for uploading supplementary materials like pdf documents with hardware specifications, methodology descriptions, policies and other reference information.
To provide for this, CP supports upload of document objects.
The upload procedure is completely analogous to data object uploads, the only difference being the absence of `specificInfo` and `objectSpecification` properties in the metadata package.

### Creating a static collection

Carbon Portal supports creation of static collections with constant lists of immutable data objects or other static collections. The process of creating a static collection is similar to step 1 of data object upload. Here are the expected contents of the metadata package for it:
```json
{
	"submitterId": "ATC",
	"title": "Test collection",
	"description": "Optional collection description",
	"members": ["https://meta.icos-cp.eu/objects/G6PjIjYC6Ka_nummSJ5lO8SV", "https://meta.icos-cp.eu/objects/sdfRNhhI5EN_BckuQQfGpdvE"],
	"isNextVersionOf": "CkSE78VzQ3bmHBtkMLt4ogJy",
	"preExistingDoi": "10.18160/VG28-H2QA",
	"documentation": "_Vb_c34v0nfTA_fG0kiIAmXM",
	"coverage": "http://meta.icos-cp.eu/resources/latlonboxes/europeLatLonBoxIngos"
}
```
The fields are either self-explanatory, or have the same meaning as for the data object upload. If no spatial coverage is provided, it will instead be calculated from the spatial coverage of the members of the collection.

As with data object uploads, this metadata package must be HTTP-POSTed to `https://meta.icos-cp.eu/upload` with `application/json` content type and the CP authentication cookie. The server will reply with landing page of the collection. The last segment of the landing page's URL is collections ID that is obtained by SHA-256-hashsumming of the alphabetically sorted list of members' hashsums (it is base64url representations of the hashsums that are sorted, but it is binary values that contribute to the collections' hashsum).

### Reconstructing upload-metadata packages of existing objects/collections

When scripting uploads of multiple objects, it can be convenient to use an upload-metadata package of an existing object as an example or a template. The reconstructed package can be fetched using the following request:

`curl https://meta.icos-cp.eu/dtodownload?uri=<langing page URL>`

In bash shell, one can also format the JSON after fetching, as in this example:

`curl https://meta.icos-cp.eu/dtodownload?uri=https://meta.icos-cp.eu/objects/n7cB5kS4U1E5A3mXKtEUCF9s | python3 -m json.tool`

---

## Accessing the metadata

Carbon Portal stores its metadata in an [RDF store](https://en.wikipedia.org/wiki/Triplestore) (also called triplestore), where every metadata entity is represented with a URL.
All of these URLs are resolvable and can be visited using Web browsers and other HTTP client software.
Examples of the kinds of metadata entities include data objects, document objects, collections, organizations, people, research stations, dataset specifications, variables, acquisition/creation/submission provenance objects, etc.

### Data objects

Carbon Portal's data objects have a well-defined separation between data (the binary content of the object, viewed as a constant sequence of bytes and identified using its SHA-256 hashsum) and metadata (all the other information about the object, which existed or could have existed at the time of object creation). Examples of data object metadata include file name, size in bytes, research station, sampling height, previous/next versions, etc.

The most basic and user-friendly way of accessing data object's metadata is visiting its landing page (example: <https://meta.icos-cp.eu/objects/_fJ8Skpz_lvMnAOfsRApZojG>) using a Web browser, and then possibly explore it further by navigating to the links therein. Additionally, every data object has a metadata view (see [example](https://data.icos-cp.eu/portal/#%7B%22route%22%3A%22metadata%22%2C%22id%22%3A%22_fJ8Skpz_lvMnAOfsRApZojG%22%7D)) in the [portal app](https://data.icos-cp.eu/portal/).

Apart from metadata access methods intended for human consumption, CP offers a way of accessing data object metadata programmatically. All the metadata is published using CC0 licence, which means that no licence acceptance is needed, and all metadata access can be performed anonymously.

Programmatic access to individual data objects' metadata is performed by sending HTTP GET request to the landing page, specifying the desired metadata format using HTTP content negotiation. For example, it is possible to download most of the data object's metadata displayed on its landing page, as a single JSON object, using command-line tool `curl` like so:

`curl -H "Accept: application/json" https://meta.icos-cp.eu/objects/qZzevJN69j6rRPKxTbJZckDf`

Other supported content types are intended for fetching different serializations of RDF metadata: `application/xml` or `application/rdf+xml` for RDF/XML, and `text/plain` or `text/turtle` for RDF/Turtle.

### Other metadata entities

Same principles and approaches to metadata access apply to document objects, collections, organizations, people, data types, variables, etc. However, the list of supported content types and richness of the corresponding metadata representations and HTML landing pages may vary.

---

## Advanced metadata access with SPARQL

CP metadata service responds to arbitrary queries in W3C-standardized RDF query language [SPARQL](https://www.w3.org/TR/sparql11-query/) sent to its SPARQL endpoint `https://meta.icos-cp.eu/sparql`. Interaction with the endpoint can either be done programmatically (using CP's [`icoscp_core` Python library](https://github.com/ICOS-Carbon-Portal/data/tree/master/src/main/python/icoscp_core#advanced-metadata-access-sparql) or any other programming language and library suitable for SPARQL or plain HTTPS access) or through our (semi-) user-friendly [SPARQL client app](https://meta.icos-cp.eu/sparqlclient/). Writing the queries requires familiarity with [the query language](https://www.w3.org/TR/sparql11-query/) and with the CP metadata model. The latter is [formally expressed](https://github.com/ICOS-Carbon-Portal/meta/blob/master/src/main/resources/owl/cpmeta.owl) using OWL &mdash; W3C-standard ontology language.

The OWL ontology can be opened for convenient exploration by an editor such as [Protégé](https://protege.stanford.edu/), but for an introductory overview is it helpful to first inspect [the slides](https://github.com/ICOS-Carbon-Portal/infrastructure/wiki/Architecture-info#data-object-metadata) related to the data object metadata. Additionally, the OWL model can be discovered through SPARQL (because it is represented using RDF and is accessible through the SPARQL endpoint, too). Finally, it is possible to discover the metadata model empirically through the SPARQL endpoint, too, by examining various metadata entities with exploratory queries. However, one should keep in mind that metadata for data objects or stations of different types can have different "shape", and that some of the properties can be optional.

### Examples of exploratory queries
To discover direct metadata properties of a data object (or any other RDF resource), one can run a query of the form

```sparql
select * where{
	<https://meta.icos-cp.eu/objects/r8V_G6LQHV5isDk0l9tyiVAt> ?p ?o .
}
```
The (arbitrary) variable names `p` and `o` stand for "property" and "object", the last two components of the subject-predicate-object triples that RDF consists of.
The output from this query contains much less elements than the landing page of the data object, because many of the properties are themselves resources with properties, and can be explored further using the same type of query.

The above query has an inverse, looking for metadata entities for whom a given resource is a value of a property:

```sparql
select * where{
	?s ?p <https://meta.icos-cp.eu/objects/r8V_G6LQHV5isDk0l9tyiVAt> .
}
limit 1000
```
Running it reveals that our example object has a single (at the time of writing this) "user"&mdash;the collection it is a part of.
(In the future, the object may get referenced by its new version, making the number of "users" two).
In general, one should be conscious when running this type of query, as some of the resources have a very large number of "users" (hence the `limit` clause).

A possibility to obtain the results of both of the queries above in one go is to run a more concise query
```sparql
describe <https://meta.icos-cp.eu/objects/r8V_G6LQHV5isDk0l9tyiVAt>
```
(make sure to switch the return type to "TSV or Turtle"!)

To continue exploration of the data object metadata, we could for example examine its specification (called "data type" in our user interfaces):

```sparql
select * where{
	<http://meta.icos-cp.eu/resources/cpmeta/etcL2Meteosens> ?p ?o .
}
```
or, alternatively, the data object specification can be looked up and examined with a single query:
```sparql
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select * where{
	<https://meta.icos-cp.eu/objects/r8V_G6LQHV5isDk0l9tyiVAt> cpmeta:hasObjectSpec ?spec .
	?spec ?p ?o .
}
```

We discover that our data object specification is related to project `http://meta.icos-cp.eu/resources/projects/icos` via predicate `cpmeta:hasAssociatedProject`. We may get interested in listing all the data object specifications associated with ICOS project, which we could achieve with the following query:

```sparql
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select ?theme ?dataLevel ?icosSpec ?specLabel where{
	?icosSpec cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos> ;
		rdfs:label ?specLabel ;
		cpmeta:hasDataLevel ?dataLevel ;
		cpmeta:hasDataTheme ?theme .
}
order by ?theme ?dataLevel ?specLabel
```
In this query we also extract a few interesting properties for the data object specifications, specify the order of columns in the result, and request specific sorting.

### Queries used by the portal app

The main data object search interface ([portal app](https://data.icos-cp.eu/portal/))
uses a list of [queries under the hood](https://data.icos-cp.eu/portal/#%7B%22filterCategories%22%3A%7B%22project%22%3A%5B%22icos%22%5D%2C%22level%22%3A%5B2%5D%7D%2C%22tabs%22%3A%7B%22searchTab%22%3A1%7D%7D) (see the *SPARQL queries* panel and the panel-heading of the Search result list; the latter contains a small button redirecting to the search query for the data object list). The links/buttons will open a new browser window with a SPARQL client app with respective query pre-filled.

Of particular interest are two queries: *Statistics of data object origins* and the data object list query (the small button).
These two queries are dynamic, reflecting the state of the filters, selections, sorting, and paging (when applicable) of the portal app interface.
They can be used as inspiration and a starting point for writing more customized queries.

### Other queries
To further demonstrate some of the possibilities that are accessible via SPARQL, the [client app](https://meta.icos-cp.eu/sparqlclient/) has a list of pre-defined queries to choose from.

### Summary

We recognize that even for technical external users the threshold to writing SPARQL queries can be rather high, and therefore invite them to get in touch with us, should they have metadata-query needs not covered by our user-friendly products.


---

## Metadata flow (for ICOS ATC and ICOS Cities mid- and low cost sensor networks)

Authentication with a pre-configured data portal account is required. The authentication mechanism is the same as for data object upload.

### ATC

The CSV tables with ATC metadata are to be pushed as payloads of HTTP POST requests to URLs of the form

`https://meta.icos-cp.eu/upload/atcmeta/<tableName>`

where `<tableName>` is a name used to distinguish different tables, for example "roles", "stations", "instruments", "instrumentsLifecycle", etc.

### ICOS Cities mid- and low cost sensor networks

The URL to POST metadata files to is of the form

`https://citymeta.icos-cp.eu/upload/midLowCost<city>/<tableName>`

where `<city>` is a city (e.g. `Zurich`, `Paris`, `Munich`) and `<tableName>` is the name of a metadata table (e.g. `sites`). For example, to upload with `curl`:

`$ curl -X POST --data-binary "@zuerich_sites.csv" https://citymeta.icos-cp.eu/upload/midLowCostZurich/sites --cookie "cpauthToken=..."`



---

## Administrative API for RDF updates

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

- Set up a Docker container with PostgreSQL for RDF log (see the [infrastructure project](https://github.com/ICOS-Carbon-Portal/infrastructure?tab=readme-ov-file#rdflog))
- Make a copy of `example.application.conf` file in the project root named `application.conf` and edit it to suit your environment. For some default config values, see `application.conf` in `src/main/resources/`. For deployment, make sure there is a relevant `application.conf` in the JVM's working directory.
- Run sbt
- In the sbt console, run `~reStart` for continuous local rebuilds and server restarts. Alternatively, if the development is done only in the front end part, running `~copyResources` is sufficient but much faster.


### Setting up authentication/authorization for the Handle.net client HandleNetClient
Handle.net servers use two-way TLS.

#### Client side
- Generate a public/private key pair:

`$ openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:4096`

- Convert the private key to PKCS8 binary format:

`$ openssl pkcs8 -topk8 -outform DER -in private_key.pem -out private_key.der -nocrypt`

- Extract the public key from the key pair (output to X.509 binary format):

`$ openssl rsa -pubout -in private_key.pem -outform DER -out public_key.der`

- Convert the public key from X.509 format to the format used by Handle.net server software. This can be accomplished with the help of `HandleNetClient.getHandleNetKeyBytes` method, from Scala REPL. The obtained byte array should simply be written to a file, for example `handleClientPubKey.bin`.

- Make sure the contents of `handleClientPubKey.bin` file are published as the value of `HS_PUBKEY` type at an index that is claimed to describe an administrator of your Handle prefix. For example, it could be record 300 of [0.NA/11676](https://hdl.handle.net/0.NA/11676) or record 300 of [11676/ADMIN](https://hdl.handle.net/11676/ADMIN). This operation must be done by someone who already has the admin rights for the prefix.
- Generate a self-signed certificate using the private key from the previous steps. Only `CN` value should be provided, and it must identify the `HS_PUBKEY` record in the Handle system, for example as `300:11676/ADMIN`:

`$ openssl req -keyform DER -key private_key.der -new -x509 -days 15000 -out handleClientCert.pem`

#### Server side
By default, Handle.net server software comes with self-signed SSL certificates with `CN=anonymous`.
This does not work for Java, therefore it is necessary to get the administrators of the Handle server (which you are going to use) to replace the default with a self-signed certificate with a `CN` equal to the actual domain name of the server.
After that the server certificate needs to be fetched (to be used later as a trusted cert), for example:

`$ openssl s_client -showcerts -connect epic.pdc.kth.se:8000 < /dev/null 2> /dev/null | openssl x509 -outform PEM > server_cert.pem`

#### Testing with curl
curl has the possibility of disabling server certificate validation with `-k` command-line option. The following example should create/overwrite handle with suffix `<suffix>` (use actual desired suffix) by `HTTP-PUT`ing JSON file `payload.json` into a handle:

`$ curl -v -k --cert handleClientCert.pem --key private_key.pem -H 'Authorization: Handle clientCert="true"' -H "Content-Type: application/json" --upload-file payload.json https://epic.pdc.kth.se:8000/api/handles/11676/<suffix>?overwrite=true`

 `payload.json` is expected to contain a JSON object with array of handle values as `values` property. For more details on the HTTP API see [documentation](https://handle.net/hnr_documentation.html). To examine handle values, run, for example

`$ curl -k https://epic.pdc.kth.se:8000/api/handles/11676/<suffix> | python -m json.tool`

#### Deployment
- When deploying `meta`, make sure that the client private key, certificate, and the server certificate files are copied to the production environment, and that the config parameters for the Handle client provide correct paths to them.

### Miscellaneous recipes

#### Restoring RDFLog database from pg_dump

`cat dump.sqlc | docker exec -i rdflogdb_rdflogdb_1 pg_restore -c -U postgres -d postgres --format=c > /dev/null`

#### Autorendering README.md to HTML for preview on file change
Make sure that Python is available, and `python-markdown` and `inotify-tools` packages are installed on your Linux system.
Then you can run:

`$ while inotifywait -e close_write README.md; do python -m markdown README.md > README.html; done`

#### SHA-256 sum in base64
`$ sha256sum <filename> | awk '{print $1;}' | xxd -r -p | base64`
