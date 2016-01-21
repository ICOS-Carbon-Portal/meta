ICOS Carbon Portal metadata service
===================================

Metadata service for hosting, mantaining and querying information about things like ICOS stations, people, instruments, etc.
Not ready to be used yet.

Getting started with the front-end part
---------------------------------------
- Install `Node.js` as instructed [here](https://github.com/nodesource/distributions)
- Clone this repository: `git clone git@github.com:ICOS-Carbon-Portal/meta.git`
- `cd meta`
- Install Node.js dependencies: `npm install`
- Now you can run Gulp tasks: `npm run <task>` (see `package.json` for the list of defined tasks)

Getting started with the back-end part
--------------------------------------
- Set up a Docker container with PostgreSQL for RDF log (see the [infrastructure project](https://github.com/ICOS-Carbon-Portal/infrastructure/tree/master/rdflogdb))
- Make a copy of `example.application.conf` file in the project root named `application.conf` and edit it to suit your environment. For some default config values, see `application.conf` in `src/main/resources/`. For deployment, make sure there is a relevant `application.conf` in the JVM's working directory.
- Run sbt
- In the sbt console, run `~re-start` for continuous local rebuilds and server restarts. Alternatively, if the development is done only in the front end part, running `~copyResources` is sufficient but much faster. 

Using the webapp
----------------
To get the authentication cookie from CPuth:
`curl --cookie-jar cookies.txt --data "mail=<user email>&password=<password>" https://cpauth.icos-cp.eu/password/login`
The resulting `cookies.txt` file must be edited if you want to use it for tests against localhost via HTTP.

To test the metadata upload (`upload.json` and `cookies.txt` must be in the current directory):
`curl --cookie cookies.txt -H "Content-Type: application/json" -X POST -d @upload.json localhost:9094/upload`

Alternatively, the CPauth cookie can be supplied manually:
`curl -H "Cookie: <cookie-assignment>" -H "Content-Type: application/json" -X POST -d @upload.json localhost:9094/upload`

