--
-- Post-load steps, run after all gav rows are written:
--   1. Build the secondary indexes of gav (deferred so the bulk load only maintains the primary key)
--   2. Refresh the summary tables g (per group) and ga (per group + artifact) from gav
--   3. Re-enable the InnoDB redo log disabled by create.sql
--

USE mavendb;
select concat(now(), ' Started');

-- Both indexes in one ALTER TABLE: built from a single parallel scan of gav
ALTER TABLE gav
ADD INDEX index_gav (group_id, artifact_id, artifact_version),
ADD INDEX index_fname (file_name);
select concat(now(), ' Table gav create index finished');


-- Refresh data for table g
TRUNCATE TABLE g;
INSERT INTO    g (
  group_id,
  artifact_version_counter,
  major_version_counter,
  version_seq_max,
  file_modified_max           )
SELECT
  group_id,
  count(DISTINCT artifact_version) AS artifact_version_counter,
  count(DISTINCT major_version)    AS major_version_counter,
  max(version_seq)                 AS version_seq_max,
  max(file_modified)               AS file_modified_max
FROM gav
GROUP BY group_id
ORDER BY group_id
;
select concat(now(), ' Table g refresh data finished');

-- Refresh data for table ga
TRUNCATE TABLE ga;
INSERT INTO    ga (
  group_id, artifact_id,
  artifact_version_counter,
  major_version_counter,
  version_seq_max,
  file_modified_max            )
SELECT 
  group_id, artifact_id,
  count(DISTINCT artifact_version) AS artifact_version_counter,
  count(DISTINCT major_version)    AS major_version_counter,
  max(version_seq)                 AS version_seq_max,
  max(file_modified)               AS file_modified_max
FROM gav
GROUP BY group_id, artifact_id
ORDER BY group_id, artifact_id
;
select concat(now(), ' Table ga refresh data finished');

-- Re-enable InnoDB redo logging, disabled by create.sql for the bulk load.
--   Without redo, any unclean shutdown (crash, OOM kill, docker stop timeout) corrupts the instance.
--   Enabling it forces a checkpoint, so the loaded data is crash safe from here on.
ALTER INSTANCE ENABLE INNODB REDO_LOG;
select concat(now(), ' InnoDB redo log enabled');
