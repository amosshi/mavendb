package org.mavendb.api;

import org.apache.catalina.filters.RateLimitFilter;
import org.apache.catalina.util.ExactRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Limits requests per client IP with Tomcat's built-in {@link RateLimitFilter}, so one client cannot use up a
 * small VPS. Over the limit the answer is <code>429 Too Many Requests</code>.
 *
 * <p>The client IP is the request's remote address. Behind a reverse proxy on the same host,
 * <code>server.forward-headers-strategy=native</code> (in <code>application.yml</code>) makes Tomcat take it from
 * <code>X-Forwarded-For</code>, before this filter runs; that header is only trusted from internal addresses.
 *
 * <p>Only the API paths are limited: <code>/actuator/health</code> stays reachable for monitoring.
 * The number of requests served at the same time is capped by <code>server.tomcat.threads.max</code>.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mavendb.api.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    /** Created by Spring. */
    public RateLimitConfig() {
        // Nothing to set up
    }

    /** Length of a counting window, in seconds. */
    static final int WINDOW_SECONDS = 60;

    /**
     * @param props API settings
     * @return Tomcat's rate limit filter on <code>/v1/*</code>, ahead of the other filters
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(ApiProperties props) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter());
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addInitParameter("bucketDuration", Integer.toString(WINDOW_SECONDS));
        registration.addInitParameter("bucketRequests", Integer.toString(props.rateLimit().perClientPerMinute()));
        // The default FastRateLimiter rounds the window up to a power of two (64 s); keep it at exactly one minute
        registration.addInitParameter("rateLimitClassName", ExactRateLimiter.class.getName());
        // RateLimit and RateLimit-Policy headers (IETF draft): the client sees its quota and the requests left
        registration.addInitParameter("exposeHeaders", "true");
        // Name of the policy in those headers: RateLimit-Policy: "per-ip";q=60;w=60
        registration.addInitParameter("policyName", "per-ip");
        return registration;
    }
}
