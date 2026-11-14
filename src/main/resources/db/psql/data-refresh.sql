-- ============================================================
-- Correct data records
--
-- The whole script runs in one transaction, so now() would print the
-- same time on every status line: use clock_timestamp() instead.
-- ============================================================

SET search_path TO mavendb;

-- Sorts and hash tables of the aggregates below are far bigger than the 64MB default:
-- keep them in memory instead of spilling to disk. Each parallel worker can use this much.
SET work_mem = '1GB';

SELECT clock_timestamp() || ' Started' AS status;

-- ============================================================
-- Aggregates for tables g and ga
--
-- PostgreSQL never runs the SELECT of an INSERT ... SELECT in parallel,
-- but it does for CREATE TABLE ... AS. So aggregate into a staging table
-- first, then insert the small result in key order.
--
-- COLLATE "C" for grouping and counting distinct values: compares bytes
-- instead of calling the slow locale (en_US.utf8) compare function. The
-- database collation is deterministic (equal only when the bytes are equal),
-- so the groups and counts are exactly the same. The final ORDER BY uses the
-- default collation, so rows are inserted in the same order as before.
--
-- Runs before the gav indexes exist, so the planner picks parallel scans.
-- ============================================================

DROP TABLE IF EXISTS g_stage;

CREATE UNLOGGED TABLE g_stage AS
SELECT
    group_id COLLATE "C"                         AS group_id,
    count(DISTINCT artifact_version COLLATE "C") AS artifact_version_counter,
    count(DISTINCT major_version)                AS major_version_counter,
    max(version_seq)                             AS version_seq_max,
    max(file_modified)                           AS file_modified_max
FROM gav
GROUP BY 1;

SELECT clock_timestamp() || ' Table g_stage created' AS status;

DROP TABLE IF EXISTS ga_stage;

CREATE UNLOGGED TABLE ga_stage AS
SELECT
    group_id    COLLATE "C"                      AS group_id,
    artifact_id COLLATE "C"                      AS artifact_id,
    count(DISTINCT artifact_version COLLATE "C") AS artifact_version_counter,
    count(DISTINCT major_version)                AS major_version_counter,
    max(version_seq)                             AS version_seq_max,
    max(file_modified)                           AS file_modified_max
FROM gav
GROUP BY 1, 2;

SELECT clock_timestamp() || ' Table ga_stage created' AS status;

-- ============================================================
-- Indexes of gav
-- ============================================================

CREATE INDEX index_gav    ON gav (group_id, artifact_id, artifact_version);

SELECT clock_timestamp() || ' Table gav create index index_gav finished' AS status;

CREATE INDEX index_fname  ON gav (file_name);

SELECT clock_timestamp() || ' Table gav create index index_fname finished' AS status;

-- ============================================================
-- Fix Tomcat zip.sha512 → zip
-- ============================================================

UPDATE gav
SET file_extension = 'zip'
WHERE group_id = 'org.apache.tomcat'
  AND artifact_id = 'tomcat'
  AND file_extension = 'zip.sha512';

SELECT clock_timestamp() || ' Table gav Fix org.apache.tomcat/tomcat/zip.sha512 finished' AS status;

-- ============================================================
-- Refresh table: g
-- ============================================================

TRUNCATE TABLE g;

INSERT INTO g (
    group_id,
    artifact_version_counter,
    major_version_counter,
    version_seq_max,
    file_modified_max
)
SELECT
    group_id,
    artifact_version_counter,
    major_version_counter,
    version_seq_max,
    file_modified_max
FROM g_stage
ORDER BY group_id COLLATE "default";

DROP TABLE g_stage;

CREATE INDEX index_group_id_left1 ON g (group_id_left1);
CREATE INDEX index_group_id_left2 ON g (group_id_left2);
CREATE INDEX index_group_id_left3 ON g (group_id_left3);
CREATE INDEX index_group_id_left4 ON g (group_id_left4);

SELECT clock_timestamp() || ' Table g refresh data finished' AS status;

-- ============================================================
-- Refresh table: ga
-- ============================================================

TRUNCATE TABLE ga;

INSERT INTO ga (
    group_id,
    artifact_id,
    artifact_version_counter,
    major_version_counter,
    version_seq_max,
    file_modified_max
)
SELECT
    group_id,
    artifact_id,
    artifact_version_counter,
    major_version_counter,
    version_seq_max,
    file_modified_max
FROM ga_stage
ORDER BY group_id COLLATE "default", artifact_id COLLATE "default";

DROP TABLE ga_stage;

SELECT clock_timestamp() || ' Table ga refresh data finished' AS status;
