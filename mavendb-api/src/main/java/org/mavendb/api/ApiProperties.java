package org.mavendb.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under <code>mavendb.api</code>; defaults are in <code>application.yml</code>.
 *
 * @param sqlite    The SQLite file and its connection settings
 * @param rateLimit Request limit per client IP
 */
@ConfigurationProperties(prefix = "mavendb.api")
public record ApiProperties(Sqlite sqlite, RateLimit rateLimit) {

    /**
     * SQLite settings.
     *
     * @param path         The database file built by mavendb-loader; opened read-only
     * @param poolSize     Number of open connections
     * @param cacheSizeKib Page cache per connection, in KiB
     * @param mmapSize     Bytes of the file to memory map (SQLite caps it at about 2 GB)
     */
    public record Sqlite(String path, int poolSize, int cacheSizeKib, long mmapSize) {
    }

    /**
     * Requests per client IP, counted in fixed one-minute windows by Tomcat's
     * {@link org.apache.catalina.filters.RateLimitFilter}.
     *
     * @param enabled            Switch the limit off, for example when the reverse proxy limits already
     * @param perClientPerMinute Requests a client IP may send per minute
     */
    public record RateLimit(boolean enabled, int perClientPerMinute) {
    }
}
