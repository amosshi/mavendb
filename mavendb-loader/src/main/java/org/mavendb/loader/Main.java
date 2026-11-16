package org.mavendb.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;

/**
 * Entrance of the application.
 *
 * @author amosshi
 */
public class Main {

    /**
     * Logger.
     */
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    /** Exit code: success, or help printed. */
    static final int EXIT_OK = 0;

    /**
     * Exit code: a bad command line argument or index folder, nothing was loaded.
     * A failed load exits with code 1, from the uncaught exception.
     */
    static final int EXIT_INVALID_ARGUMENTS = 2;

    /**
     * Configuration file name: <code>config.properties</code>.
     */
    private static final String CONFIG_FILE = "config.properties";

    /**
     * Load the {@link #CONFIG_FILE}.
     */
    private static Properties loadConfig() throws IOException {
        // Get the config file name
        String configFileName = AppPaths.getFileName(AppPaths.DIR_ETC, CONFIG_FILE);

        // Load the Config values
        Properties configValues = new Properties();
        try (BufferedReader br = new BufferedReader(new FileReader(configFileName, StandardCharsets.UTF_8))) {
            configValues.load(br);
        }

        return configValues;
    }

    /**
     * Entrance of the application.
     *
     * @param args the command line arguments
     * @throws IOException Exception
     * @throws SQLException Exception
     */
    public static void main(String[] args) throws IOException, SQLException {
        int exitCode = run(args);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    /**
     * Run the application.
     *
     * <p>A failed load is thrown, not returned: the JVM prints the stack trace and exits with code 1.
     *
     * @param args the command line arguments
     * @return {@link #EXIT_OK}, or {@link #EXIT_INVALID_ARGUMENTS} when nothing was loaded because of a bad argument
     * @throws IOException Exception
     * @throws SQLException Exception
     */
    private static int run(String[] args) throws IOException, SQLException {

        // Log formatter.
        // @see https://stackoverflow.com/questions/194765/how-do-i-get-java-logging-output-to-appear-on-a-single-line
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n");
        LOG.log(Level.INFO, "Started");

        // Help first: the parser rejects a command line without the required -d and -f options
        if (Arrays.asList(args).contains("-" + CommandOptions.OPTION_HELP.getOpt())
            || Arrays.asList(args).contains("--" + CommandOptions.OPTION_HELP.getLongOpt())) {
            Main.printHelp();
            return EXIT_OK;
        }

        CommandLine line;
        try {
            // parse the command line arguments
            line = new DefaultParser().parse(CommandOptions.OPTIONS, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            LOG.log(Level.SEVERE, "Command line parameter parsing failed.", exp);
            Main.printHelp();
            return EXIT_INVALID_ARGUMENTS;
        }

        if (line.hasOption(CommandOptions.OPTION_HELP.getLongOpt())) {
            Main.printHelp();
            return EXIT_OK;
        }

        String dbTypeStringValue = line.getOptionValue(CommandOptions.OPTION_DB_TYPE_LONGOPT);
        if (dbTypeStringValue.isBlank()) {
            LOG.log(Level.SEVERE, "Database type is required");
            Main.printHelp();
            return EXIT_INVALID_ARGUMENTS;
        }

        String dbTypeString = dbTypeStringValue.toUpperCase();
        DatabaseType dbType;
        try {
            dbType = Enum.valueOf(DatabaseType.class, dbTypeString);
        } catch (IllegalArgumentException _) {
            LOG.log(Level.SEVERE, "Unsupported database type: " + dbTypeString);
            Main.printHelp();
            return EXIT_INVALID_ARGUMENTS;
        }

        String logBaseName = Main.setupFileLogging(dbType);

        String reposFolder = line.getOptionValue(CommandOptions.OPTION_REPOS_FOLDER_LONGOPT);
        if (reposFolder.isBlank()) {
            LOG.log(Level.SEVERE, "Repository folder is required");
            Main.printHelp();
            return EXIT_INVALID_ARGUMENTS;
        }

        MvnScanner scanner;
        try {
            scanner = MvnScanner.create(reposFolder, dbType);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "Invalid repository folder: " + reposFolder, e);
            return EXIT_INVALID_ARGUMENTS;
        }

        RunSummary summary = new RunSummary(dbType, reposFolder, logBaseName + ".log");
        try {
            scanner.perform(loadConfig(), summary);
            summary.succeeded();
        } catch (IOException | SQLException | RuntimeException e) {
            summary.failed(e);
            throw e;
        } finally {
            summary.write(Path.of(AppPaths.getFileName(AppPaths.DIR_LOG, logBaseName + ".json")));
        }

        LOG.log(Level.INFO, "Finished");
        return EXIT_OK;
    }

    /**
     * Add a log file handler to the root logger, so all log output also goes to
     * <code>log/&lt;timestamp&gt;-&lt;dbtype&gt;.log</code> next to the jar file.
     *
     * @param dbType Database type, used as part of the log file name
     * @return Base name of the log file, <code>&lt;timestamp&gt;-&lt;dbtype&gt;</code>, shared by the run summary JSON
     * @throws IOException Failed to create the log folder or log file
     */
    private static String setupFileLogging(DatabaseType dbType) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"));
        String logBaseName = timestamp + "-" + dbType.name().toLowerCase();
        String logFileName = AppPaths.getFileName(AppPaths.DIR_LOG, logBaseName + ".log");
        Files.createDirectories(Path.of(AppPaths.getFileName(AppPaths.DIR_LOG, "")));

        FileHandler fileHandler = new FileHandler(logFileName, false);
        fileHandler.setEncoding(StandardCharsets.UTF_8.name());
        fileHandler.setFormatter(new SimpleFormatter());
        Logger.getLogger("").addHandler(fileHandler);

        LOG.log(Level.INFO, "Log file: {0}", logFileName);
        return logBaseName;
    }

    /**
     * Print out help information to command line.
     */
    @SuppressWarnings("java:S106") // Standard outputs should not be used directly to log anything -- Help info need come to System.out
    private static void printHelp() throws IOException {
        String jarFilename = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
        // No "Since" column: the options carry no version information
        HelpFormatter.builder().setShowSince(false).get()
            .printHelp(String.format("java -jar %s [args...]", jarFilename), null, CommandOptions.OPTIONS, null, false);
    }

    /**
     * Command line options.
     */
    static final class CommandOptions {

        /**
         * Command line options.
         */
        private static final Options OPTIONS = new Options();

        /**
         * Command line option: Maven Repos name long name format.
         */
        private static final String OPTION_REPOS_FOLDER_LONGOPT = "reposfolder";

        /**
         * Command line option: Database type long name format.
         */
        private static final String OPTION_DB_TYPE_LONGOPT = "dbtype";

        /**
         * Command line option: Maven Repos name to scan, like central, spring.
         */
        private static final Option OPTION_DB_TYPE = Option.builder("d")
            .longOpt(OPTION_DB_TYPE_LONGOPT)
            .hasArg()
            .desc("Database type, like " + DatabaseType.names() + ".")
            .required()
            .get();
        /**
         * Command line option: Maven Repos name to scan, like central, spring.
         */
        private static final Option OPTION_REPOS_FOLDER = Option.builder("f")
            .longOpt(OPTION_REPOS_FOLDER_LONGOPT)
            .hasArg()
            .desc("Maven Repos folder to scan, like central, spring; the folder will match to the config file at etc/repos-<the name>.properties.")
            .required()
            .get();
        /**
         * Command line option: print help information.
         */
        private static final Option OPTION_HELP = Option.builder("h")
            .longOpt("help")
            .hasArg(false)
            .desc("Printout help information")
            .get();

        private CommandOptions() {
        }

        static {
            OPTIONS.addOption(OPTION_DB_TYPE);
            OPTIONS.addOption(OPTION_REPOS_FOLDER);
            OPTIONS.addOption(OPTION_HELP);
        }
    }
}
