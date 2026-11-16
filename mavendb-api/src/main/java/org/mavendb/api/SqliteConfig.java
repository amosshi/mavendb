package org.mavendb.api;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * Read-only connection pool on the SQLite file. The file is replaced as a whole when the data is refreshed,
 * the API never writes to it.
 */
@Configuration(proxyBeanMethods = false)
public class SqliteConfig {

    /** Created by Spring. */
    public SqliteConfig() {
        // Nothing to set up
    }

    /**
     * The pool used by {@link GavRepository}.
     *
     * @param props API settings
     * @return Read-only pooled data source
     */
    @Bean
    public DataSource dataSource(ApiProperties props) {
        ApiProperties.Sqlite sqlite = props.sqlite();
        Path file = Path.of(sqlite.path());
        if (!Files.isRegularFile(file)) {
            // Read-only open would fail on the first query anyway; fail at startup with a clear message instead
            throw new IllegalStateException("SQLite file not found: " + file.toAbsolutePath()
                + " (set mavendb.api.sqlite.path or MAVENDB_API_SQLITE_PATH)");
        }

        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setCacheSize(-sqlite.cacheSizeKib());
        config.setPragma(SQLiteConfig.Pragma.MMAP_SIZE, Long.toString(sqlite.mmapSize()));

        SQLiteDataSource sqliteDs = new SQLiteDataSource(config);
        sqliteDs.setUrl("jdbc:sqlite:" + file.toAbsolutePath());

        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("mavendb-sqlite");
        pool.setDataSource(sqliteDs);
        pool.setMaximumPoolSize(sqlite.poolSize());
        pool.setMinimumIdle(1);
        // Hikari sets this flag on every new connection; sqlite-jdbc only accepts the value it was opened with
        pool.setReadOnly(true);
        return pool;
    }
}
