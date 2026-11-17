# Maven repository to DB

This application will scan all `maven` repos items and store them to database. Supported database
- `MongoDB`
- `OpenSearch`
- `MySQL`
- `MariaDB`
- `PostgreSQL`
- `SQLite`
- `DuckDB`

## Modules

| Module | What it does |
|---|---|
| [mavendb-loader](mavendb-loader/) | Reads the Maven Central index and loads it into the databases above (`dist/` zip, `bin/run.sh`) |
| [mavendb-api](mavendb-api/) | Read-only REST API on the SQLite file built by the loader, for a small public server |

All 3rd party versions are set once, in the `<dependencyManagement>` of the root [pom.xml](pom.xml);
the module poms declare dependencies without versions.

Dependency graphs, from the [javadoc](https://amosshi.github.io/mavendb/apidocs/) on the published site (see [Site](#site)):

<details>
<summary>mavendb-loader: jar graph and module graph</summary>

![Jar graph of mavendb-loader](https://amosshi.github.io/mavendb/apidocs/org/mavendb/loader/doc-files/jar-graph.svg)

![Module graph of mavendb-loader](https://amosshi.github.io/mavendb/apidocs/org/mavendb/loader/doc-files/module-graph.svg)
</details>

<details>
<summary>mavendb-api: jar graph and module graph</summary>

![Jar graph of mavendb-api](https://amosshi.github.io/mavendb/apidocs/org/mavendb/api/doc-files/jar-graph.svg)

![Module graph of mavendb-api](https://amosshi.github.io/mavendb/apidocs/org/mavendb/api/doc-files/module-graph.svg)
</details>

## Prepare Database

### Option: MongoDB

Execute script [compose-mongodb.sh](mavendb-loader/src/main/resources/docker/compose-mongodb.sh)
- `./compose-mongodb.sh`


### Option: OpenSearch

Execute script [compose-opensearch.sh](mavendb-loader/src/main/resources/docker/compose-opensearch.sh)
- `./compose-opensearch.sh`
- A single node without the security plugin: plain HTTP on port `9200`, no login, for local use only
- OpenSearch Dashboards (query UI) on port `5601`
- The host needs `vm.max_map_count` of at least `262144`: `sudo sysctl -w vm.max_map_count=262144`
- The documents go to the index named after the Maven index id (`central`); the mapping is in
  [db/opensearch/create.json](mavendb-loader/src/main/resources/db/opensearch/create.json)

### Option: MySQL

A Docker Compose file has been configured
* [compose-mysql.yml](mavendb-loader/src/main/resources/docker/compose-mysql.yml)

Step 1. Config
- Modify the passwords set in the [.env](mavendb-loader/src/main/resources/docker/.env) file based on security requirements
- Modify the `innodb_buffer_pool_size` in [docker-compose.yml](docker-compose.yml) based on hardware

Step 2. Start
- For `Ubuntu`/`Linux` users
  - [Install Docker](https://docs.docker.com/engine/install/ubuntu/)
  - Execute script [compose-mysql.sh](mavendb-loader/src/main/resources/docker/compose-mysql.sh)
    - `./compose-mysql.sh`
- For MacOS Users
  - [Install Docker Desktop](https://docs.docker.com/desktop/install/mac-install/)
  - Execute script [compose-mysql.sh](mavendb-loader/src/main/resources/docker/compose-mysql.sh)
    - `./compose-mysql.sh`
- For Windows Users
  - [Install Docker Desktop](https://docs.docker.com/desktop/install/windows-install/)
  - Make sure the [docker memory resource limit](https://stackoverflow.com/questions/43460770/docker-windows-container-memory-limit) is bigger than the MySQL `innodb_buffer_pool_size`
    - Example: on a 64GB RM Windows laptop, set `--innodb_buffer_pool_size=24G` will work for maven central scan
  - Execute script [compose-mysql.ps1](mavendb-loader/src/main/resources/docker/compose-mysql.ps1)
    - `powershell -ExecutionPolicy Bypass -File .\compose-mysql.ps1`

### Option: MariaDB

Execute script [compose-mariadb.sh](mavendb-loader/src/main/resources/docker/compose-mariadb.sh)
- `./compose-mariadb.sh`
- It listens on port `3307`, so it can run next to MySQL on `3306`
- Modify the `innodb_buffer_pool_size` in [compose-mariadb.yml](mavendb-loader/src/main/resources/docker/compose-mariadb.yml) based on hardware

### Option: PSQL

Execute script [compose-psql.sh](mavendb-loader/src/main/resources/docker/compose-psql.sh)
- `./compose-psql.sh`

### Option: SQLite

No setup required! SQLite stores everything in a single file.

- Default database file: `mavendb.sqlite` (created in the current directory)
- Configure the path in [config.properties](mavendb-loader/src/main/resources/etc/config.properties):
  ```properties
  org.mavendb.sqlite.url=jdbc:sqlite:/path/to/mavendb.sqlite
  ```

### Option: DuckDB

No setup required! DuckDB is an embedded analytical database: like SQLite, everything is in a single file.

- Default database file: `mavendb.duckdb` (created in the current directory; `run.sh` deletes the previous one first)
- Configure the path in [config.properties](mavendb-loader/src/main/resources/etc/config.properties):
  ```properties
  org.mavendb.duckdb.url=jdbc:duckdb:/path/to/mavendb.duckdb
  ```
- Query it with the [DuckDB CLI](https://duckdb.org/docs/installation/): `duckdb mavendb.duckdb "SELECT * FROM g LIMIT 10"`


## Download Indexes

### Option 1: Download via wget

For maven central, download index files from the following address
- https://repo.maven.apache.org/maven2/.index/
- Nov 2026

```sh
~/develop/mavencentral/repo.maven.apache.org/maven2/.index$ ls -alh
total 4.1G
-rw-rw-r-- 1 4.1G Nov  9 23:12 nexus-maven-repository-index.gz
-rw-r--r-- 1 1.2K Nov  9 22:37 nexus-maven-repository-index.properties
```

### Option 2. Make use of [maven-indexer](https://github.com/apache/maven-indexer)

```sh
git clone https://github.com/apache/maven-indexer.git
```

Edit `BasicUsageExample`

- To keep the files, add one line in BasicUsageExample.perform() after the request is created:
```java
IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, new Java11HttpClient());
updateRequest.setLocalIndexCachePath(Path.of("target/central-gz-cache"));   // keeps the .gz + .properties files
```

```sh
maven-indexer $ mvn clean install -DskipTests
```

```
cd indexer-examples/indexer-examples-basic
indexer-examples-basic $ mvn -Pexamples test
```

It downloads into this module’s target/ folder:
- `target/central-index`: the Lucene index it builds


## Build and Run

Requirements

* OpenJDK `25` or later
* Maven: bundled wrapper `./mvnw` downloads the pinned version (see `.mvn/wrapper/maven-wrapper.properties`)
* `graphviz`: `./build.sh` runs [graphs.sh](graphs.sh), which needs `dot` for the dependency graphs in the javadoc
  * MacOS: `brew install graphviz`
  * Ubuntu: `sudo apt install graphviz -y`

Build the Source Code
* `./build.sh`

How to Run the Tool
* Come to the `mavendb-loader/dist` folder
  * Go to folder `mavendb-<version>-<timestampe>`
* (Optional) Come to the `etc` edit the `config.properties` file
  * The default values has been optimized, well we can still modify it when needed
* Come to the `bin` folder, run either of the following commands

```sh
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ mongodb
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ opensearch
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ mysql
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ mariadb
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ psql
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ sqlite
bin $ ./run.sh file:///home/<username>/develop/mavencentral/repo.maven.apache.org/maven2/.index/ duckdb
```

How to run for all DB

```sh
bin $ ./pipeline.sh
```

## Metadata API (mavendb-api)

Serves the SQLite file of the loader (`run.sh <index> sqlite`), opened read-only.

```sh
java --enable-native-access=ALL-UNNAMED -jar mavendb-api/target/mavendb-api.jar \
     --mavendb.api.sqlite.path=/data/mavendb.sqlite
```

| Request | Answer |
|---|---|
| `GET /v1/files/{fileName}` | GAVs with this file name, like `commons-lang3-3.12.0.jar` (a name can exist in several groups) |
| `GET /v1/sha1/{sha1}` | GAVs with a file of this SHA-1 |
| `GET /v1/groups/{groupId}/artifacts?limit=100&offset=0` | Artifacts of a group, by artifact id; `limit` at most 1000 |
| `GET /v1/groups/{groupId}/artifacts/{artifactId}/latest` | Latest version of an artifact (numeric order: `3.10` after `3.9`) |
| `GET /actuator/health` | Health check, not rate limited |

Not found is `404`, bad input `400`, over the rate limit `429`. Every `/v1` answer has the `RateLimit` and
`RateLimit-Policy` headers, like `RateLimit-Policy: "per-ip";q=60;w=60` and `RateLimit: "per-ip";r=59`:
the quota per window of `w` seconds, and the requests left in the current window.

Settings are in [application.properties](mavendb-api/src/main/resources/application.properties), and can be changed without a rebuild,
for example `--mavendb.api.rate-limit.per-client-per-minute=120` or `MAVENDB_API_RATE_LIMIT_PER_CLIENT_PER_MINUTE=120`.

| Setting | Default | |
|---|---|---|
| `mavendb.api.rate-limit.per-client-per-minute` | `60` | Requests per client IP per one-minute window (Tomcat's built-in `RateLimitFilter`) |
| `mavendb.api.rate-limit.enabled` | `true` | |
| `server.tomcat.threads.max` | `50` | Requests served at the same time, for all clients together |
| `server.tomcat.accept-count` | `100` | Requests waiting for a thread; more are refused |

The API listens on `127.0.0.1:8080`, for a reverse proxy on the same host that terminates HTTPS;
it takes the client IP from `X-Forwarded-For` of that proxy, so the rate limit applies per real client.

## Exeuction Time

The following is the currrent execution time.
- Since maven central artifacts is keep improving, so the runtime will be longer and longer

|  Time    | artifacts count  | Runtime     | DB Type | Notes                                 |
|----------|-----------------:|------------:|---------|---------------------------------------|
| Sep 2023 |    `44,758,974`  |  `5.6` hour | MySQL   | innodb_buffer_pool_size=40G
| Jul 2025 |    `76,619,430`  | `19.1` hour | MySQL   | innodb_buffer_pool_size=100G
| Aug 2025 |    `76,638,341`  | `18.8` hour | MySQL   | `61,164,426` + `6,608,605`

Feb 2026 - `89,587,849` artifacts

| DB Type | Runtime     | Notes                                 |
|---------|------------:|---------------------------------------|
| MySQL   |  `1.7` hour | `4,061,407` + `1,980,766`, 50k batch
| PSQL    |   `37` min  | `1,347,975` + `882,647`, 50k batch
| Mongodb | `26.6` min  | `1,169,402` + `428,064`
| SQLite  | `40.2` min  | `1,317,668` + `479,457`, 50k batch


Nov 2026 - `115,697,728` artifacts
- Baseline

| DB Type | Runtime     | Notes                                 |
|---------|------------:|---------------------------------------|
| MySQL   |  `2.0` hour | `6,214,297` + `1,131,991`
| PSQL    | `38.4` min  | `1,401,990` + `904,862`
| Mongodb | `26.6` min  | `1,242,975` + `353,517`
| SQLite  | `30.3` min  | `1,338,312` + `482,009`, 100k batch

- Full pipeline run with database sizes, 15 Nov 2026 (`mavendb-loader 6.0`, `4d1dc9c`, Java 25, 64 CPUs, 32G heap, 8 converters)
  - Index timestamp `2026-10-22`; `115,697,725` stored, `3` skipped
  - Runtime notes: import ms + refresh ms, writers x batch size
  - DB size: what the database reports for the `mavendb` data, tables and indexes (`database.sizeGiB` in the run summary `log/*.json`)
  - On disk: the docker volume of the container, or the database file (`log/db-size.txt`); includes redo / WAL logs and system data
  - Export: the file `bin/run.sh` writes to `var/` after the load

| DB Type    | Runtime     | Notes                          | DB size      | On disk      | Export                               |
|------------|------------:|--------------------------------|-------------:|-------------:|--------------------------------------|
| MySQL      | `24.6` min  | `785,153+689,346`, 4 x 50k     | `39.7` GiB   | `57.8` GiB   | `3.7` GiB `mavendb-mysql.sql.gz`     |
| MariaDB    | `26.2` min  | `814,317+757,397`, 4 x 50k     | `56.7` GiB   | `65.1` GiB   | `3.7` GiB `mavendb-mariadb.sql.gz`   |
| PSQL       | `19.2` min  | `768,490+383,581`, 4 x 50k     | `45.1` GiB   | `45.2` GiB   | `3.8` GiB `mavendb-psql.sql.gz`      |
| Mongodb    | `17.1` min  | `689,386+339,432`, 4 x 20k     | `12.3` GiB   | `12.5` GiB   | `4.1` GiB `mavendb-mongo.archive.gz` |
| OpenSearch | `19.4` min  | `1,141,584+21,611`, 4 x 10k    | `24.9` GiB   | `21.4` GiB   | `21.4` GiB `mavendb-opensearch-snapshot.tar` (not compressed) |
| SQLite     | `22.5` min  | `828,297+520,170`, 1 x 100k    | `52.0` GiB   | `52.0` GiB   | `7.9` GiB `mavendb.sqlite.zip`       |
| DuckDB     | `11.8` min  | `705,700+3,056`, 4 x 100k      | `10.0` GiB   | `10.0` GiB   | `2.2` GiB `mavendb.duckdb.zip`       |

- Notes on size
  - The row stores (MySQL, MariaDB, PSQL, SQLite) take `45` to `65` GiB; MongoDB (WiredTiger, compressed) and DuckDB (columnar, compressed) about `10` to `12` GiB
  - Every compressed export is `2` to `8` GiB: the size to download or upload, for example to the `mavendb-api` server
  - MySQL and MariaDB report the size from table statistics, an estimate of the used pages: the `.ibd` files also hold
    free space left by the index builds, plus redo, undo and the system tablespace, hence `39.7` GiB reported for `57.8` GiB on disk
  - MySQL DB size is from a second MySQL run the same evening, after a fix to the size query (`25.7` min, `57.9` GiB on disk)
  - SQLite refresh is `150` s longer than before: the index `idx_gav_sha1` for `mavendb-api`

## Access

### Mongo Express

Local Mongo Express: [http://localhost:8081/](http://localhost:8081/)
- Username: `root`
- Password: use the password in [.env](mavendb-loader/src/main/resources/docker/.env) file


### MySQL

Access via DB Adminer: [http://localhost:10191/](http://localhost:10191/)
- Username: `mavendbadmin`, as defined in [.env](mavendb-loader/src/main/resources/docker/.env) file
- Password: use the password in [.env](mavendb-loader/src/main/resources/docker/.env) file

Access via REST API
- Rest API user guide see [php-crud-api#treeql](https://github.com/mevdschee/php-crud-api#treeql-a-pragmatic-graphql)
- Sample: [http://localhost:2080/api.php/records/gav?filter=group_id,eq,org.apache.commons&filter=artifact_id,eq,commons-lang3&size=10](http://localhost:2080/api.php/records/gav?filter=group_id,eq,org.apache.commons&filter=artifact_id,eq,commons-lang3&size=10)
  - `group_id`: `org.apache.commons`
  - `artifact_id`: `commons-lang3`


Access via Docker Shell

- Come into Container
```
host $ sudo docker compose exec -it mavendb-mysql bash
```

- Login to MySQL, use the password defined in [.env](mavendb-loader/src/main/resources/docker/.env) file
```
container bash-5.1# mysql -p
```


### PSQL

Access via DB Adminer: [http://localhost:10192/](http://localhost:10192/)
- Username: `root`
- Password: use the password in [.env](mavendb-loader/src/main/resources/docker/.env) file


## Internal Only

### Max Lengths

Max length of the text fields of maven central repository.

```
sha1=106

groupId=129
artifactId=98
version=118
classifier=67
packaging=113
fileExtension=113
name=486
description=53217

Bundle-Description=2503
Bundle-DocURL=221
Bundle-License=463
Bundle-Name=155
Bundle-SymbolicName=179
Bundle-Version=122

Export-Package=1247534
Export-Service=3529
Import-Package=140582
Require-Bundle=3245

repositoryId=7
```


### Site

Build the site
* `./build.sh`: builds and tests all modules, then stages the site of all modules in `target/staging`
  * `target/staging/index.html`: the parent site, with links to the module sites
  * `target/staging/apidocs/index.html`: the javadoc of all modules
  * The javadoc of mavendb-loader and of mavendb-api embeds the jar graph and the module graph that [graphs.sh](graphs.sh) generates; `./mvnw clean site` alone leaves it out
  * Each package folder has a README.md with the UML diagrams of its classes, for example [org.mavendb.loader](mavendb-loader/src/main/java/org/mavendb/loader/). [graphs.sh](graphs.sh) regenerates them: commit them when a class is added, removed or renamed

Publish site
* After each merge into `main`, the [Site](.github/workflows/site.yml) workflow builds the site and publishes it to the `gh-pages` branch
* The branch holds only the latest site (one commit, force-pushed)
* It can also be started by hand: Actions → Site → Run workflow


### Sample Data

Size

```
indexRepoId=central
indexLastPublished=Thu Jan 15 04:26:25 PST 2026
isIncremental=false
indexRequiredChunkNames=[nexus-maven-repository-index.gz]
chunkName=nexus-maven-repository-index.gz
chunkVersion=1
chunkPublished=Thu Jan 15 04:26:25 PST 2026
Chunk stats:

ALL_GROUPS = 1
ARTIFACT_ADD = 89587846
ROOT_GROUPS = 1
DESCRIPTOR = 1
```

`org.apache.maven.index.reader.Record`

```
record=Record{
  type=ARTIFACT_ADD,
  expanded={
      Key{name='Bundle-License', type=String}=https://www.apache.org/licenses/LICENSE-2.0.txt, 
      Key{name='version', type=String}=17-0.9.2, 
      Key{name='groupId', type=String}=us.ihmc, 
      Key{name='Bundle-Name', type=String}=sourceJar, 
      Key{name='packaging', type=String}=jar, 
      Key{name='description', type=String}=SCS2 Simulation, 
      Key{name='hasJavadoc', type=Boolean}=false, 
      Key{name='sha1', type=String}=8a16ffef75fef5f5c46d4290ef126ac59f71fcf9, 
      Key{name='recordModified', type=Long}=1765382166548, 
      Key{name='fileSize', type=Long}=229462, 
      Key{name='Bundle-Version', type=String}=17-0.9.2, 
      Key{name='fileExtension', type=String}=jar, 
      Key{name='classifier', type=String}=sources, 
      Key{name='name', type=String}=scs2-simulation, 
      Key{name='artifactId', type=String}=scs2-simulation, 
      Key{name='hasSources', type=Boolean}=false, 
      Key{name='hasSignature', type=Boolean}=false, 
      Key{name='fileModified', type=Long}=1657893876000
  }
}
```

```
Key{name='version', type=String}=2.7.15.0, name=version, type=String
Key{name='groupId', type=String}=xyz.opcal.cloud, name=groupId, type=String
Key{name='packaging', type=String}=jar, name=packaging, type=String
Key{name='description', type=String}=logback api for webflux request, name=description, type=String
Key{name='hasJavadoc', type=Boolean}=false, name=hasJavadoc, type=Boolean
Key{name='sha1', type=String}=2cb6eeb2b4e0bd77fd00f661d69b69db4ff098ad, name=sha1, type=String
Key{name='recordModified', type=Long}=1765379584124, name=recordModified, type=Long
Key{name='fileSize', type=Long}=371874, name=fileSize, type=Long
Key{name='fileExtension', type=String}=jar, name=fileExtension, type=String
Key{name='classifier', type=String}=javadoc, name=classifier, type=String
Key{name='name', type=String}=opcal-cloud-commons-logback-webflux, name=name, type=String
Key{name='artifactId', type=String}=opcal-cloud-commons-logback-webflux, name=artifactId, type=String
Key{name='hasSources', type=Boolean}=false, name=hasSources, type=Boolean
Key{name='hasSignature', type=Boolean}=true, name=hasSignature, type=Boolean
Key{name='fileModified', type=Long}=1692943727000, name=fileModified, type=Long
```

```json
{
  "_id": 13535,
  "Bundle-Description": "JaCoCo Core",
  "Bundle-DocURL": "https://www.absa.africa",
  "Bundle-License": "https://www.eclipse.org/legal/epl-2.0/",
  "Bundle-Name": "JaCoCo Core",
  "Bundle-SymbolicName": "za.co.absa.jacoco.org.jacoco.core",
  "Bundle-Version": "0.8.10.202305020106",
  "Export-Package": "org.jacoco.core.internal;x-internal:=true;version=\"0.8.10\",org.jacoco.core.internal.analysis;x-internal:=true;version=\"0.8.10\";uses:=\"org.jacoco.core.analysis,org.jacoco.core.internal.analysis.filter,org.jacoco.core.internal.flow,org.objectweb.asm,org.objectweb.asm.tree\",org.jacoco.core.internal.analysis.filter;x-internal:=true;version=\"0.8.10\";uses:=\"org.objectweb.asm.tree\",org.jacoco.core.internal.data;x-internal:=true;version=\"0.8.10\",org.jacoco.core.internal.flow;x-internal:=true;version=\"0.8.10\";uses:=\"org.jacoco.core.internal.analysis,org.objectweb.asm,org.objectweb.asm.commons,org.objectweb.asm.tree\",org.jacoco.core.internal.instr;x-internal:=true;version=\"0.8.10\";uses:=\"org.jacoco.core.internal.flow,org.jacoco.core.runtime,org.objectweb.asm\",org.jacoco.core;version=\"0.8.10\",org.jacoco.core.analysis;version=\"0.8.10\";uses:=\"org.jacoco.core.data,org.jacoco.core.internal.analysis\",org.jacoco.core.data;version=\"0.8.10\";uses:=\"org.jacoco.core.internal.data\",org.jacoco.core.instr;version=\"0.8.10\";uses:=\"org.jacoco.core.runtime\",org.jacoco.core.runtime;version=\"0.8.10\";uses:=\"org.jacoco.core.data,org.objectweb.asm\",org.jacoco.core.tools;version=\"0.8.10\";uses:=\"org.jacoco.core.data",
  "Import-Package": "org.jacoco.core;version=\"[0.8.10,0.8.11)\",org.jacoco.core.analysis;version=\"[0.8.10,0.8.11)\",org.jacoco.core.data;version=\"[0.8.10,0.8.11)\",org.jacoco.core.internal;version=\"[0.8.10,0.8.11)\",org.jacoco.core.internal.analysis;version=\"[0.8.10,0.8.11)\",org.jacoco.core.internal.analysis.filter;version=\"[0.8.10,0.8.11)\",org.jacoco.core.internal.data;version=\"[0.8.10,0.8.11)\",org.jacoco.core.internal.flow;version=\"[0.8.10,0.8.11)\",org.jacoco.core.internal.instr;version=\"[0.8.10,0.8.11)\",org.jacoco.core.runtime;version=\"[0.8.10,0.8.11)\",org.objectweb.asm;version=\"[9.5.0,9.6)\",org.objectweb.asm.commons;version=\"[9.5.0,9.6)\",org.objectweb.asm.tree;version=\"[9.5.0,9.6)"
}
```
