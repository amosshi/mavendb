package org.mavendb.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under <code>mavendb.api</code>; defaults are in <code>application.yml</code>.
 *
 * @param sqlite    The SQLite file and its connection settings
 * @param rateLimit Request limits, per client IP and for the whole server
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
     * Token bucket limits. A client may send a burst of requests, then is refilled at the steady rate.
     *
     * @param enabled            Switch the limits off, for example behind a proxy that limits already
     * @param perClientPerMinute Steady rate per client IP
     * @param perClientBurst     Requests a client IP may send at once
     * @param globalPerSecond    Steady rate for all clients together: protects the server
     * @param globalBurst        Requests all clients together may send at once
     * @param maxTrackedClients  Client IPs tracked at most; more clients share one bucket
     */
    public record RateLimit(boolean enabled, int perClientPerMinute, int perClientBurst,
                            int globalPerSecond, int globalBurst, int maxTrackedClients) {
    }
}
