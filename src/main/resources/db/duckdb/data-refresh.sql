--
-- Post-load steps, run after all gav rows are written:
--   Refresh the summary tables g (per group) and ga (per group + artifact) from gav.
--
-- DuckDB runs each aggregate as a parallel hash aggregate over the columnar gav table: no index is needed.
-- g and ga are written sorted by their keys, so their min/max zone maps make lookups by group_id cheap.
--
-- Status lines: DuckDB's now() is the start time of the current transaction, and the script runs in one
-- transaction. Each step is committed and a new transaction started, so the following now() shows when it finished.
--

SELECT now() || ' Started';

-- Refresh data for table g
DELETE FROM g;
INSERT INTO g
SELECT
  group_id,
  count(DISTINCT artifact_version) AS artifact_version_counter,
  count(DISTINCT major_version)    AS major_version_counter,
  max(version_seq)                 AS version_seq_max,
  max(file_modified)               AS file_modified_max
FROM gav
GROUP BY group_id
ORDER BY group_id;
COMMIT; BEGIN TRANSACTION;
SELECT now() || ' Table g refresh data finished';

-- Refresh data for table ga
DELETE FROM ga;
INSERT INTO ga
SELECT
  group_id,
  artifact_id,
  count(DISTINCT artifact_version) AS artifact_version_counter,
  count(DISTINCT major_version)    AS major_version_counter,
  max(version_seq)                 AS version_seq_max,
  max(file_modified)               AS file_modified_max
FROM gav
GROUP BY group_id, artifact_id
ORDER BY group_id, artifact_id;
COMMIT; BEGIN TRANSACTION;
SELECT now() || ' Table ga refresh data finished';

-- Write the data to the database file and empty the WAL, so the file is complete for the zip in run.sh
CHECKPOINT;
COMMIT; BEGIN TRANSACTION;
SELECT now() || ' Checkpoint finished';
