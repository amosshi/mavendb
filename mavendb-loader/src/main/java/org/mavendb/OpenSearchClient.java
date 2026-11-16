package org.mavendb;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/**
 * Minimal OpenSearch REST client for the bulk load, on the JDK {@link HttpClient}: no client library needed.
 *
 * <p>Load cycle of one index:
 * <ol>
 *   <li>{@link #recreateIndex(String, String)}: drop the index and create it from <code>db/opensearch/create.json</code>,
 *       tuned for loading: no replicas, refresh disabled, asynchronous translog</li>
 *   <li>{@link #bulk(String, byte[], int, long)}: one {@code _bulk} request per batch, from several writers</li>
 *   <li>{@link #finishLoad(String)}: restore search settings and refresh, so all documents become searchable</li>
 * </ol>
 *
 * <p>Thread safe: {@link HttpClient} is shared by all writers.
 */
final class OpenSearchClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(OpenSearchClient.class.getName());

    /** Generous: a bulk request of a large batch, or a refresh of the whole index, takes minutes on a busy node. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(30);

    /** Compact JSON with plain numbers: {@code 42}, not {@code {"$numberLong": "42"}}. */
    private static final JsonWriterSettings JSON_SETTINGS = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();

    /** Bulk responses list every item; keep only the error flag and failed items, so a good response is tiny. */
    private static final String BULK_FILTER = "?filter_path=errors,items.*.error";

    /**
     * Largest {@code _bulk} request, well below {@code http.max_content_length} (200mb in compose-opensearch.yml,
     * 100mb by default): a batch of 10,000 large documents reached over 200 MB. Smaller requests also spare the node heap.
     */
    static final int MAX_BULK_REQUEST_BYTES = 50 * 1024 * 1024;

    private static final int ERROR_EXCERPT_LENGTH = 1_000;

    private final String baseUrl;
    private final String authorization;
    private final HttpClient http;

    /**
     * Create a client.
     *
     * @param baseUrl Server URL, like {@code http://localhost:9200}
     * @param user User for basic authentication; blank for a server without the security plugin
     * @param password Password for basic authentication
     */
    OpenSearchClient(String baseUrl, String user, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authorization = (user == null || user.isBlank()) ? null
            : "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Encode documents as a {@code _bulk} request body (NDJSON): an index action with the document id, then the source.
     *
     * <p>The {@code _id} field becomes the OpenSearch document id: it is metadata and must not be in the source.
     * Thread safe: called on the conversion threads, so the writer only sends bytes.
     *
     * @param documents Documents with an {@code _id} field; the field is removed from each document
     * @return Request body, UTF-8
     */
    static byte[] toBulkData(List<Document> documents) {
        StringBuilder sb = new StringBuilder(documents.size() * 1_024);
        for (Document document : documents) {
            Object id = document.remove(DatabaseRepository.JSON_FIELD_ID);
            sb.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
            sb.append(document.toJson(JSON_SETTINGS)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Drop the index if it exists, then create it with the given settings and mappings.
     *
     * @param index Index name
     * @param definition Index settings and mappings, JSON
     */
    void recreateIndex(String index, String definition) throws IOException {
        HttpResponse<String> deleted = send(request("/" + index).DELETE(), "DELETE index " + index);
        if (deleted.statusCode() != 200 && deleted.statusCode() != 404) {
            throw failure("DELETE index " + index, deleted);
        }
        expectOk(send(json("/" + index, definition, "PUT"), "PUT index " + index), "PUT index " + index);
        LOG.log(Level.INFO, "OpenSearch index {0} created", index);
    }

    /**
     * Index one batch with a {@code _bulk} request.
     *
     * @param index Index name
     * @param bulkData Request body, see {@link #toBulkData(List)}
     * @param size Number of documents, for logging
     * @param counter Record counter, for logging
     * @throws IOException if the request fails, or any document is rejected
     */
    void bulk(String index, byte[] bulkData, int size, long counter) throws IOException {
        for (int[] range : splitBulk(bulkData, MAX_BULK_REQUEST_BYTES)) {
            HttpRequest.Builder request = request("/" + index + "/_bulk" + BULK_FILTER)
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bulkData, range[0], range[1] - range[0]));
            HttpResponse<String> response = send(request, "bulk");
            expectOk(response, "bulk");

            // OpenSearch answers 200 even when documents are rejected; the errors flag tells
            if (Boolean.TRUE.equals(Document.parse(response.body()).getBoolean("errors"))) {
                throw new IOException("OpenSearch rejected documents of records counter=" + counter
                    + ", batchSize=" + size + ": " + excerpt(response.body()));
            }
        }
    }

    /**
     * Split a bulk body into requests of at most {@code maxBytes}, at document boundaries (every second line).
     *
     * <p>A batch has a fixed number of documents, but documents vary from 1 KB to over 1 MB
     * (a long {@code Export-Package}), so a batch of large documents can pass {@code http.max_content_length}
     * and fail with HTTP 413. A single document larger than {@code maxBytes} is sent alone.
     * Splitting on the raw {@code '\n'} byte is safe: JSON escapes newlines in strings,
     * and no UTF-8 multi-byte sequence contains that byte.
     *
     * @param data Bulk body, see {@link #toBulkData(List)}
     * @param maxBytes Request size limit
     * @return Ranges {@code [start, end)} covering all of {@code data}, in order
     */
    static List<int[]> splitBulk(byte[] data, int maxBytes) {
        List<int[]> ranges = new ArrayList<>();
        int start = 0;
        int lastBoundary = 0;
        boolean actionLine = true;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != '\n') {
                continue;
            }
            actionLine = !actionLine;
            if (!actionLine) {
                continue;   // end of the action line; the document ends after its source line
            }
            int boundary = i + 1;
            if (boundary - start > maxBytes && lastBoundary > start) {
                ranges.add(new int[] {start, lastBoundary});
                start = lastBoundary;
            }
            lastBoundary = boundary;
        }
        if (start < data.length) {
            ranges.add(new int[] {start, data.length});
        }
        return ranges;
    }

    /**
     * Make the loaded index ready for searching: enable refresh and durable translog writes, then refresh.
     *
     * @param index Index name
     */
    void finishLoad(String index) throws IOException {
        String settings = """
            { "index": { "refresh_interval": "1s", "translog.durability": "request" } }""";
        expectOk(send(json("/" + index + "/_settings", settings, "PUT"), "PUT settings"), "PUT settings");
        expectOk(send(request("/" + index + "/_refresh").POST(HttpRequest.BodyPublishers.noBody()), "refresh"), "refresh");
        LOG.log(Level.INFO, "OpenSearch index {0} refreshed", index);
    }

    /**
     * Count the documents of an index.
     *
     * @param index Index name
     * @return Number of documents
     */
    long count(String index) throws IOException {
        HttpResponse<String> response = send(request("/" + index + "/_count").GET(), "count");
        expectOk(response, "count");
        return Document.parse(response.body()).get("count", Number.class).longValue();
    }

    /** Bytes the index takes on disk, primaries and replicas: {@code _all.total.store.size_in_bytes}. */
    long storeSizeBytes(String index) throws IOException {
        HttpResponse<String> response = send(request("/" + index + "/_stats/store").GET(), "stats");
        expectOk(response, "stats");
        Document total = Document.parse(response.body()).get("_all", Document.class).get("total", Document.class);
        return total.get("store", Document.class).get("size_in_bytes", Number.class).longValue();
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.baseUrl + path)).timeout(REQUEST_TIMEOUT);
        if (this.authorization != null) {
            builder.header("Authorization", this.authorization);
        }
        return builder;
    }

    private HttpRequest.Builder json(String path, String body, String method) {
        return request(path)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String what) throws IOException {
        try {
            return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted: OpenSearch " + what, e);
        }
    }

    private static void expectOk(HttpResponse<String> response, String what) throws IOException {
        if (response.statusCode() != 200) {
            throw failure(what, response);
        }
    }

    private static IOException failure(String what, HttpResponse<String> response) {
        return new IOException("OpenSearch " + what + " failed with HTTP " + response.statusCode() + ": " + excerpt(response.body()));
    }

    private static String excerpt(String body) {
        return body.length() <= ERROR_EXCERPT_LENGTH ? body : body.substring(0, ERROR_EXCERPT_LENGTH) + "...";
    }

    @Override
    public void close() {
        this.http.close();
    }
}
