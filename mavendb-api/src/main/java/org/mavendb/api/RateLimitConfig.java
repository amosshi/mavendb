package org.mavendb.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RateLimitFilter} on the API paths only: <code>/actuator/health</code> stays reachable for
 * monitoring. Switched off with <code>mavendb.api.rate-limit.enabled=false</code>.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mavendb.api.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    /**
     * @param props API settings
     * @return The filter, as a bean so its idle-client eviction is scheduled
     */
    @Bean
    RateLimitFilter rateLimitFilter(ApiProperties props) {
        return new RateLimitFilter(props.rateLimit(), System::nanoTime);
    }

    /**
     * @param filter The filter
     * @return Registration on <code>/v1/*</code>, ahead of the other filters
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
