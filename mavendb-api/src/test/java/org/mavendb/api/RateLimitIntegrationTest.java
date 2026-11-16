package org.mavendb.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Tomcat's rate limit filter as wired by {@link RateLimitConfig}: applies to /v1, not to the health check. */
@SpringBootTest(properties = "mavendb.api.rate-limit.per-client-per-minute=2")
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) {
        registry.add("mavendb.api.sqlite.path", () -> TestDatabase.file().toString());
    }

    @Test
    void apiIsLimitedHealthIsNot() throws Exception {
        String url = "/v1/groups/org.apache.commons/artifacts/commons-lang3/latest";
        this.mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(header().string("RateLimit-Policy", containsString("\"per-ip\";q=2;w=60")))
            .andExpect(header().string("RateLimit", containsString("\"per-ip\";r=1")));
        this.mvc.perform(get(url)).andExpect(status().isOk());
        this.mvc.perform(get(url)).andExpect(status().isTooManyRequests());

        for (int i = 0; i < 5; i++) {
            this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
