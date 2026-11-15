package org.mavendb.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "mavendb.api.rate-limit.enabled=false")
@AutoConfigureMockMvc
class GavControllerTest {

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) {
        registry.add("mavendb.api.sqlite.path", () -> TestDatabase.file().toString());
    }

    @Test
    void fileNameGivesGav() throws Exception {
        this.mvc.perform(get("/v1/files/commons-lang3-3.10.jar"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].groupId").value("org.apache.commons"))
            .andExpect(jsonPath("$[0].artifactId").value("commons-lang3"))
            .andExpect(jsonPath("$[0].version").value("3.10"));
    }

    @Test
    void sameFileNameInTwoGroups() throws Exception {
        this.mvc.perform(get("/v1/files/core-1.0.jar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].groupId").value("com.example.one"))
            .andExpect(jsonPath("$[1].groupId").value("com.example.two"));
    }

    @Test
    void unknownFileIsNotFound() throws Exception {
        this.mvc.perform(get("/v1/files/nothing-1.0.jar")).andExpect(status().isNotFound());
    }

    @Test
    void sha1GivesGavAndIgnoresCase() throws Exception {
        this.mvc.perform(get("/v1/sha1/00000000000000000000000000000000000000A1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].artifactId").value("commons-text"));
    }

    @Test
    void badSha1IsBadRequest() throws Exception {
        this.mvc.perform(get("/v1/sha1/xyz")).andExpect(status().isBadRequest());
    }

    @Test
    void groupGivesArtifacts() throws Exception {
        this.mvc.perform(get("/v1/groups/org.apache.commons/artifacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].artifactId").value("commons-lang3"))
            .andExpect(jsonPath("$[0].versionCount").value(2))
            .andExpect(jsonPath("$[1].artifactId").value("commons-text"));
    }

    @Test
    void artifactsArePaged() throws Exception {
        this.mvc.perform(get("/v1/groups/org.apache.commons/artifacts?limit=1&offset=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].artifactId").value("commons-text"));
        // Past the end: an empty page, not "group not found"
        this.mvc.perform(get("/v1/groups/org.apache.commons/artifacts?offset=10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        this.mvc.perform(get("/v1/groups/org.apache.commons/artifacts?limit=1001"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        this.mvc.perform(get("/v1/groups/org.nothing/artifacts")).andExpect(status().isNotFound());
    }

    @Test
    void latestSortsVersionsNumerically() throws Exception {
        // 3.10 is newer than 3.9, although "3.9" > "3.10" as text
        this.mvc.perform(get("/v1/groups/org.apache.commons/artifacts/commons-lang3/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("3.10"));
    }

    @Test
    void unknownArtifactHasNoLatest() throws Exception {
        this.mvc.perform(get("/v1/groups/org.apache.commons/artifacts/nothing/latest"))
            .andExpect(status().isNotFound());
    }

    @Test
    void tooLongIdIsBadRequest() throws Exception {
        this.mvc.perform(get("/v1/groups/" + "g".repeat(GavController.MAX_ID_LENGTH + 1) + "/artifacts"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void healthIsUp() throws Exception {
        this.mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
