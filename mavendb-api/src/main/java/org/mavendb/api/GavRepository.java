package org.mavendb.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * All SQL of the API. Every query is an indexed lookup on the loader's SQLite schema
 * (<code>db/sqlite/create.sql</code> and <code>data-refresh.sql</code> in mavendb-loader).
 */
@Repository
public class GavRepository {

    /** Rows returned at most by a lookup by file name or sha1. */
    static final int MAX_FILE_MATCHES = 100;

    private static final String GAV_COLUMNS = """
        group_id, artifact_id, artifact_version, classifier, packaging, file_extension,
        file_name, sha1, file_size, file_modified
        """;

    private final JdbcClient jdbc;

    GavRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Files with this name; uses index <code>idx_gav_file_name</code>.
     * The same name can exist in several groups, like <code>core-1.0.jar</code>.
     */
    List<Gav> findByFileName(String fileName) {
        return this.jdbc.sql("SELECT " + GAV_COLUMNS + """
                FROM gav WHERE file_name = ?
                ORDER BY group_id, artifact_id, version_seq LIMIT ?
                """)
            .params(fileName, MAX_FILE_MATCHES)
            .query(GavRepository::gav)
            .list();
    }

    /** Files with this sha1; uses index <code>idx_gav_sha1</code>. */
    List<Gav> findBySha1(String sha1) {
        return this.jdbc.sql("SELECT " + GAV_COLUMNS + """
                FROM gav WHERE sha1 = ?
                ORDER BY group_id, artifact_id, version_seq LIMIT ?
                """)
            .params(sha1, MAX_FILE_MATCHES)
            .query(GavRepository::gav)
            .list();
    }

    /** Artifacts of a group, from the summary table <code>ga</code> (index <code>idx_ga</code>). */
    List<Artifact> findArtifacts(String groupId, int limit, int offset) {
        return this.jdbc.sql("""
                SELECT artifact_id, artifact_version_counter, file_modified_max
                FROM ga WHERE group_id = ?
                ORDER BY artifact_id LIMIT ? OFFSET ?
                """)
            .params(groupId, limit, offset)
            .query((rs, i) -> new Artifact(
                rs.getString("artifact_id"),
                rs.getLong("artifact_version_counter"),
                nullableLong(rs, "file_modified_max")))
            .list();
    }

    /**
     * Highest version of an artifact by <code>version_seq</code> (the loader's sortable version number,
     * so 3.10 sorts after 3.9); ties, like 2.0-RC1 and 2.0, go to the newer file. Uses index <code>idx_gav</code>.
     */
    Optional<LatestVersion> findLatest(String groupId, String artifactId) {
        return this.jdbc.sql("""
                SELECT group_id, artifact_id, artifact_version, file_modified
                FROM gav WHERE group_id = ? AND artifact_id = ?
                ORDER BY version_seq DESC, file_modified DESC LIMIT 1
                """)
            .params(groupId, artifactId)
            .query((rs, i) -> new LatestVersion(
                rs.getString("group_id"),
                rs.getString("artifact_id"),
                rs.getString("artifact_version"),
                nullableLong(rs, "file_modified")))
            .optional();
    }

    private static Gav gav(ResultSet rs, int rowNum) throws SQLException {
        return new Gav(
            rs.getString("group_id"),
            rs.getString("artifact_id"),
            rs.getString("artifact_version"),
            rs.getString("classifier"),
            rs.getString("packaging"),
            rs.getString("file_extension"),
            rs.getString("file_name"),
            rs.getString("sha1"),
            nullableLong(rs, "file_size"),
            nullableLong(rs, "file_modified"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
