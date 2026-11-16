package org.mavendb.loader;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/**
 * Machine readable summary of one execution, written as <code>log/&lt;timestamp&gt;-&lt;dbtype&gt;.json</code>
 * next to the text log of the same run.
 *
 * <p>Written for failed runs too, with {@code status} {@code FAILED} and the error, so every run leaves a record.
 * All durations are in milliseconds. Filled in on the main thread only.
 */
final class RunSummary {

    private static final Logger LOG = Logger.getLogger(RunSummary.class.getName());

    private static final JsonWriterSettings JSON_SETTINGS = JsonWriterSettings.builder()
        .outputMode(JsonMode.RELAXED)
        .indent(true)
        .build();

    private static final double BYTES_PER_GIB = 1024d * 1024 * 1024;

    private final DatabaseType dbType;
    private final String reposFolder;
    private final String logFile;
    private final OffsetDateTime startTime = OffsetDateTime.now();

    private String status = "RUNNING";
    private String error;
    private String databaseUrl;
    private Long databaseSizeBytes;

    private final Document index = new Document();
    private final List<Document> indexChunks = new ArrayList<>();
    private Instant indexTimestamp;
    private final Document settings = new Document();
    private final Document durations = new Document();
    private final Document tableRows = new Document();
    private Map<String, Integer> maxFieldLengths = Map.of();

    private long recordsScanned;
    private long recordsStored;

    /**
     * Start the summary of a run.
     *
     * @param dbType Database type of the run
     * @param reposFolder Index folder (URI) given on the command line
     * @param logFile Name of the text log file of the same run
     */
    RunSummary(DatabaseType dbType, String reposFolder, String logFile) {
        this.dbType = dbType;
        this.reposFolder = reposFolder;
        this.logFile = logFile;
    }

    /**
     * Mask credentials in a connection URL: the user info of {@code scheme://user:password@host}
     * and {@code password=} parameters.
     */
    static String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(//)[^/@]*@", "$1***@")
                  .replaceAll("(?i)(password=)[^&;]*", "$1***");
    }

    void setDatabaseUrl(String url) {
        this.databaseUrl = maskUrl(url);
    }

    /** Index metadata, as reported by the index reader, like {@code indexId} and {@code lastPublished}. */
    void putIndex(String key, Object value) {
        this.index.put(key, value);
    }

    /**
     * Publish time of the Maven index data, from the index properties.
     *
     * @param published Publish time, may be null
     */
    void setIndexTimestamp(Instant published) {
        this.indexTimestamp = published;
    }

    /**
     * One chunk of the index, in read order: the full chunk, then the incremental ones.
     *
     * @param name Chunk file name
     * @param version Chunk format version
     * @param timestamp Chunk publish time, may be null
     */
    void addIndexChunk(String name, int version, Instant timestamp) {
        this.indexChunks.add(new Document("name", name)
            .append("version", version)
            .append("timestamp", timestamp == null ? null : timestamp.toString()));
    }

    /** Effective settings of the run, like {@code batchSize} and {@code writers}. */
    void putSetting(String key, Object value) {
        this.settings.put(key, value);
    }

    /** Size of the loaded database as the database reports it, see {@code DatabaseRepository#databaseSizeBytes}. */
    void setDatabaseSizeBytes(long bytes) {
        this.databaseSizeBytes = bytes;
    }

    private Document database() {
        Document db = new Document("url", this.databaseUrl);
        if (this.databaseSizeBytes != null) {
            db.append("sizeBytes", this.databaseSizeBytes)
              .append("sizeGiB", Math.round(this.databaseSizeBytes * 10.0 / BYTES_PER_GIB) / 10.0);
        }
        return db;
    }

    /** Duration of one phase, like {@code createSchema}, {@code import} and {@code refresh}. */
    void putDuration(String phase, long millis) {
        this.durations.put(phase, millis);
    }

    /** Row count of a table after the refresh. */
    void putTableRows(String table, long rows) {
        this.tableRows.put(table, rows);
    }

    void setRecords(long scanned, long stored) {
        this.recordsScanned = scanned;
        this.recordsStored = stored;
    }

    void setMaxFieldLengths(Map<String, Integer> maxFieldLengths) {
        this.maxFieldLengths = new LinkedHashMap<>(maxFieldLengths);
    }

    void succeeded() {
        this.status = "SUCCESS";
    }

    void failed(Throwable e) {
        this.status = "FAILED";
        this.error = e.getClass().getName() + ": " + e.getMessage();
    }

    /**
     * Build the JSON document. Field order is the order a reader looks for things: outcome, timing, volume, details.
     */
    Document toDocument() {
        OffsetDateTime endTime = OffsetDateTime.now();
        long totalMillis = Duration.between(this.startTime, endTime).toMillis();

        Document doc = new Document()
            .append("startTime", this.startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .append("endTime", endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .append("status", this.status);
        if (this.error != null) {
            doc.append("error", this.error);
        }
        doc.append("dbType", this.dbType.name().toLowerCase());

        // When the Maven Central index data was published, and how old it was when this run started
        doc.append("indexTimestamp", this.indexTimestamp == null ? null : this.indexTimestamp.toString());
        if (this.indexTimestamp != null) {
            double ageDays = Duration.between(this.indexTimestamp, this.startTime.toInstant()).toMinutes() / 1_440.0;
            doc.append("indexAgeDays", Math.round(ageDays * 10) / 10.0);
        }

        Document durationsMs = new Document("total", totalMillis);
        durationsMs.putAll(this.durations);
        doc.append("durationsMs", durationsMs)
           .append("totalMinutes", Math.round(totalMillis / 6_000.0) / 10.0);

        Document records = new Document()
            .append("scanned", this.recordsScanned)
            .append("stored", this.recordsStored)
            .append("skipped", this.recordsScanned - this.recordsStored);
        Long importMillis = this.durations.getLong("import");
        if (importMillis != null && importMillis > 0) {
            records.append("importPerSecond", Math.round(this.recordsStored * 1000.0 / importMillis));
        }
        doc.append("records", records);
        if (!this.tableRows.isEmpty()) {
            doc.append("tableRows", this.tableRows);
        }

        doc.append("index", new Document("source", this.reposFolder).append("details", this.index).append("chunks", this.indexChunks))
           .append("database", database())
           .append("settings", this.settings)
           .append("maxFieldLengths", new Document(new LinkedHashMap<>(this.maxFieldLengths)))
           .append("build", buildInfo())
           .append("environment", environment())
           .append("logFile", this.logFile);
        return doc;
    }

    String toJson() {
        return toDocument().toJson(JSON_SETTINGS);
    }

    /**
     * Write the summary; a failure is logged, never thrown, so it cannot hide the outcome of the run.
     *
     * @param file Target JSON file
     */
    void write(Path file) {
        try {
            Files.writeString(file, toJson() + System.lineSeparator(), StandardCharsets.UTF_8);
            LOG.log(Level.INFO, "Run summary: {0}", file);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to write the run summary " + file, e);
        }
    }

    /** Version, git revision and build time from the manifest of mavendb-loader.jar; empty when not run from the jar. */
    private static Document buildInfo() {
        Document build = new Document();
        String codeSource = RunSummary.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (!codeSource.endsWith(".jar")) {
            return build;
        }
        try (JarFile jar = new JarFile(codeSource)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return build;
            }
            Attributes versions = manifest.getAttributes("Versions");
            if (versions != null) {
                build.append("version", versions.getValue("Implementation-Version"))
                     .append("gitRevision", versions.getValue("Implementation-SCM-Revision"))
                     .append("gitBranch", versions.getValue("Implementation-SCM-Branch"));
            }
            build.append("buildTime", manifest.getMainAttributes().getValue("Build-Time"));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Cannot read the jar manifest", e);
        }
        return build;
    }

    private static Document environment() {
        Runtime runtime = Runtime.getRuntime();
        return new Document()
            .append("javaVersion", Runtime.version().toString())
            .append("javaVm", System.getProperty("java.vm.name"))
            .append("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"))
            .append("availableProcessors", runtime.availableProcessors())
            .append("maxHeapBytes", runtime.maxMemory())
            .append("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
    }
}
