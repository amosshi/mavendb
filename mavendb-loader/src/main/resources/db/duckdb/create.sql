-- DuckDB database schema for mavendb
--
-- gav is bulk loaded with the DuckDB Appender (see DatabaseRepository.storeDuckDB), which appends by position:
-- the gav columns must stay in the order of DatabaseRepository.GAV_COLUMNS.
--
-- No primary key and no indexes: each would be maintained during the load, and DuckDB's analytical scans
-- (columnar, parallel, with min/max zone maps) do not need them.

CREATE OR REPLACE TABLE gav (
  seqid                     BIGINT  NOT NULL,

  major_version             INTEGER,
  version_seq               BIGINT  NOT NULL,

  record_modified           BIGINT,
  file_modified             BIGINT,
  file_size                 BIGINT,

  has_signature             BOOLEAN,
  has_sources               BOOLEAN,
  has_javadoc               BOOLEAN,

  sha1                      VARCHAR,

  group_id                  VARCHAR NOT NULL,
  artifact_id               VARCHAR NOT NULL,
  artifact_version          VARCHAR NOT NULL,

  classifier                VARCHAR,
  packaging                 VARCHAR,
  file_extension            VARCHAR,

  file_name                 VARCHAR,

  name                      VARCHAR,
  description               VARCHAR,

  bundle_description        VARCHAR,
  bundle_docurl             VARCHAR,
  bundle_license            VARCHAR,
  bundle_name               VARCHAR,
  bundle_symbolicname       VARCHAR,
  bundle_version            VARCHAR,
  export_package            VARCHAR,
  import_package            VARCHAR,
  require_bundle            VARCHAR,
  export_service            VARCHAR,

  json                      JSON
);

CREATE OR REPLACE TABLE g (
  group_id                  VARCHAR NOT NULL,
  artifact_version_counter  BIGINT,
  major_version_counter     BIGINT,
  version_seq_max           BIGINT,
  file_modified_max         BIGINT
);

CREATE OR REPLACE TABLE ga (
  group_id                  VARCHAR NOT NULL,
  artifact_id               VARCHAR NOT NULL,
  artifact_version_counter  BIGINT,
  major_version_counter     BIGINT,
  version_seq_max           BIGINT,
  file_modified_max         BIGINT
);
