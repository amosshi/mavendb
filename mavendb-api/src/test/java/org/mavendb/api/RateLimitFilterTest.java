package org.mavendb.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The filter alone, with a hand-driven clock. */
class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    private RateLimitFilter filter(int perMinute, int burst, int globalPerSecond, int globalBurst, int maxClients) {
        return new RateLimitFilter(
            new ApiProperties.RateLimit(true, perMinute, burst, globalPerSecond, globalBurst, maxClients),
            this.now::get);
    }

    private MockHttpServletResponse call(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/files/a.jar");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void burstThenRejectedThenRefilled() throws Exception {
        RateLimitFilter filter = filter(60, 3, 1000, 1000, 100);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(filter, "1.1.1.1").getStatus());
        }
        MockHttpServletResponse rejected = call(filter, "1.1.1.1");
        assertEquals(429, rejected.getStatus());
        assertEquals("1", rejected.getHeader("Retry-After"));

        // Another client is not affected
        assertEquals(200, call(filter, "2.2.2.2").getStatus());

        // 60 a minute = one a second
        this.now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertEquals(200, call(filter, "1.1.1.1").getStatus());
    }

    @Test
    void globalLimitProtectsTheServer() throws Exception {
        RateLimitFilter filter = filter(60, 100, 1, 2, 100);
        assertEquals(200, call(filter, "1.1.1.1").getStatus());
        assertEquals(200, call(filter, "2.2.2.2").getStatus());
        assertEquals(429, call(filter, "3.3.3.3").getStatus());
    }

    @Test
    void trackedClientsAreBoundedAndEvicted() throws Exception {
        RateLimitFilter filter = filter(60, 1, 1000, 1000, 2);
        call(filter, "1.1.1.1");
        call(filter, "2.2.2.2");
        // Third and fourth client share the overflow bucket of burst 1
        assertEquals(200, call(filter, "3.3.3.3").getStatus());
        assertEquals(429, call(filter, "4.4.4.4").getStatus());
        assertEquals(3, filter.trackedClients());

        this.now.addAndGet(TimeUnit.MINUTES.toNanos(1));
        filter.evictIdleClients();
        assertEquals(0, filter.trackedClients());
    }
}
