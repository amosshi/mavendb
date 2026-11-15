package org.mavendb.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits API requests per client IP and for the whole server, so one client cannot use up a small VPS.
 * Over the limit the answer is <code>429 Too Many Requests</code> with a <code>Retry-After</code> header.
 *
 * <p>The client IP is {@link HttpServletRequest#getRemoteAddr()}. Behind a reverse proxy on the same host,
 * <code>server.forward-headers-strategy=native</code> (the default in <code>application.yml</code>) makes it the
 * address from <code>X-Forwarded-For</code>; that header is only trusted from internal addresses.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Shared by the new clients once {@link ApiProperties.RateLimit#maxTrackedClients()} are tracked. */
    static final String OVERFLOW_CLIENT = "*";

    private static final double SECONDS_PER_MINUTE = 60d;

    private final ApiProperties.RateLimit limits;
    private final LongSupplier clock;
    private final TokenBucket global;
    private final ConcurrentMap<String, TokenBucket> clients = new ConcurrentHashMap<>();

    /**
     * @param limits The limits
     * @param clock  Nanosecond clock, {@link System#nanoTime()} outside tests
     */
    RateLimitFilter(ApiProperties.RateLimit limits, LongSupplier clock) {
        this.limits = limits;
        this.clock = clock;
        this.global = new TokenBucket(limits.globalBurst(), limits.globalPerSecond(), clock.getAsLong());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        long now = this.clock.getAsLong();
        TokenBucket client = bucketOf(request.getRemoteAddr(), now);

        long remaining = client.tryConsume(now);
        response.setHeader("X-RateLimit-Limit", Integer.toString(this.limits.perClientPerMinute()));
        if (remaining < 0) {
            reject(response, client.secondsUntilNextToken(now), "rate limit exceeded for your IP address");
            return;
        }
        response.setHeader("X-RateLimit-Remaining", Long.toString(remaining));
        if (this.global.tryConsume(now) < 0) {
            reject(response, this.global.secondsUntilNextToken(now), "server is busy, try again later");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Forget clients whose bucket is full again: they have been idle long enough to start fresh. */
    @Scheduled(fixedDelayString = "PT1M")
    void evictIdleClients() {
        long now = this.clock.getAsLong();
        this.clients.values().removeIf(bucket -> bucket.isFull(now));
    }

    /** Number of tracked client IPs, for tests. */
    int trackedClients() {
        return this.clients.size();
    }

    private TokenBucket bucketOf(String ip, long now) {
        TokenBucket bucket = this.clients.get(ip);
        if (bucket != null) {
            return bucket;
        }
        // Bound the memory: past the limit, new clients share one bucket until idle clients are evicted
        String key = this.clients.size() < this.limits.maxTrackedClients() ? ip : OVERFLOW_CLIENT;
        return this.clients.computeIfAbsent(key, k -> new TokenBucket(
            this.limits.perClientBurst(), this.limits.perClientPerMinute() / SECONDS_PER_MINUTE, now));
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds, String message)
        throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}");
    }
}
