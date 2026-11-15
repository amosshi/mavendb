package org.mavendb.api;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Metadata queries, version 1. Lookups by key only: no free text search, so every request is an index lookup.
 */
@RestController
@RequestMapping("/v1")
public class GavController {

    /** Longest accepted group or artifact id; the schema allows 256 (longest in Maven Central: 129). */
    static final int MAX_ID_LENGTH = 256;
    /** Longest accepted file name. */
    static final int MAX_FILE_NAME_LENGTH = 512;
    /** Default and largest page of {@link #artifacts}. */
    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 1000;

    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    /** The data only changes when the SQLite file is replaced, so clients and proxies may cache answers. */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final GavRepository repository;

    GavController(GavRepository repository) {
        this.repository = repository;
    }

    /** Which GAV has this file, like <code>commons-lang3-3.12.0.jar</code>. */
    @GetMapping("/files/{fileName}")
    ResponseEntity<List<Gav>> byFileName(@PathVariable String fileName) {
        checkLength("fileName", fileName, MAX_FILE_NAME_LENGTH);
        return found(this.repository.findByFileName(fileName));
    }

    /** Which GAV has a file with this SHA-1. */
    @GetMapping("/sha1/{sha1}")
    ResponseEntity<List<Gav>> bySha1(@PathVariable String sha1) {
        String normalized = sha1.toLowerCase(Locale.ROOT);
        if (!SHA1.matcher(normalized).matches()) {
            throw badRequest("sha1 must be 40 hex characters");
        }
        return found(this.repository.findBySha1(normalized));
    }

    /** All artifacts of a group, sorted by artifact id, in pages. */
    @GetMapping("/groups/{groupId}/artifacts")
    ResponseEntity<List<Artifact>> artifacts(@PathVariable String groupId,
                                             @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit,
                                             @RequestParam(defaultValue = "0") int offset) {
        checkLength("groupId", groupId, MAX_ID_LENGTH);
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw badRequest("limit must be 1 to " + MAX_PAGE_SIZE);
        }
        if (offset < 0) {
            throw badRequest("offset must not be negative");
        }
        List<Artifact> page = this.repository.findArtifacts(groupId, limit, offset);
        // An empty first page means the group does not exist; an empty later page is just the end of the list
        return offset == 0 ? found(page) : ResponseEntity.ok().cacheControl(CACHE).body(page);
    }

    /** The latest version of an artifact. */
    @GetMapping("/groups/{groupId}/artifacts/{artifactId}/latest")
    ResponseEntity<LatestVersion> latest(@PathVariable String groupId, @PathVariable String artifactId) {
        checkLength("groupId", groupId, MAX_ID_LENGTH);
        checkLength("artifactId", artifactId, MAX_ID_LENGTH);
        return this.repository.findLatest(groupId, artifactId)
            .map(v -> ResponseEntity.ok().cacheControl(CACHE).body(v))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "artifact not found"));
    }

    private static <T> ResponseEntity<List<T>> found(List<T> rows) {
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }
        return ResponseEntity.ok().cacheControl(CACHE).body(rows);
    }

    private static void checkLength(String name, String value, int max) {
        if (value.length() > max) {
            throw badRequest(name + " is longer than " + max + " characters");
        }
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
