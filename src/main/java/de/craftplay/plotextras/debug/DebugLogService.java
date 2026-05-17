package de.craftplay.plotextras.debug;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class DebugLogService {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String PACKAGE_PREFIX = "de.craftplay.plotextras";
    private static final int RECENT_RECORD_LIMIT = 1024;

    private final JavaPlugin plugin;
    private final Logger rootLogger = Logger.getLogger("");
    private final Handler handler = new DebugFileHandler();
    private final Object lock = new Object();
    private final Queue<Long> recentSequences = new ArrayDeque<>();
    private final Set<Long> recentSequenceSet = new HashSet<>();

    private Settings settings = Settings.defaults();
    private BufferedWriter writer;
    private File currentFile;
    private boolean attached;

    public DebugLogService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startFromDiskConfig(final String reason) {
        final File configFile = new File(plugin.getDataFolder(), "config.yml");
        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        applySettings(readSettings(configuration), true, reason);
    }

    public void reloadFromPluginConfig(final boolean rotateFile, final String reason) {
        applySettings(readSettings(plugin.getConfig()), rotateFile, reason);
    }

    public void debug(final String message) {
        if (!isEnabled() || !settings.captureInternalDebug()) {
            return;
        }
        writeDirect("DEBUG", "[Internal] " + message, null);
    }

    public void lifecycle(final String message) {
        if (!isEnabled()) {
            return;
        }
        writeDirect("INFO", message, null);
    }

    public void logThrowable(final String message, final Throwable throwable) {
        if (!isEnabled()) {
            return;
        }
        writeDirect("SEVERE", message, throwable);
    }

    public boolean isEnabled() {
        synchronized (lock) {
            return writer != null;
        }
    }

    public void close(final String reason) {
        synchronized (lock) {
            if (writer != null) {
                writeLineLocked(formatDirectLine("INFO", reason));
            }
            closeLocked();
            detachHandlersLocked();
        }
    }

    private void applySettings(final Settings nextSettings, final boolean rotateFile, final String reason) {
        synchronized (lock) {
            settings = nextSettings;
            handler.setLevel(settings.minimumLevel());
            if (!settings.enabled()) {
                if (writer != null) {
                    writeLineLocked(formatDirectLine("INFO", "Debuglog disabled by config."));
                }
                detachHandlersLocked();
                closeLocked();
                return;
            }

            detachHandlersLocked();
            if (writer == null || rotateFile) {
                closeLocked();
                openLocked(reason);
            } else {
                writeLineLocked(formatDirectLine("INFO", reason));
            }

            attachHandlersLocked();
        }
    }

    private void openLocked(final String reason) {
        final File folder = resolveFolder(settings.folder());
        try {
            Files.createDirectories(folder.toPath());
            currentFile = createLogFile(folder);
            writer = Files.newBufferedWriter(currentFile.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            writeLineLocked("=== CraftplayPlotExtras Debuglog ===");
            writeLineLocked("Created: " + LINE_TIME_FORMAT.format(ZonedDateTime.now()));
            writeLineLocked("Reason: " + reason);
            writeLineLocked("Plugin: " + plugin.getDescription().getFullName());
            writeLineLocked("Server: " + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
            writeLineLocked("Minimum level: " + settings.minimumLevel().getName());
            writeLineLocked("Capture server exceptions: " + settings.captureServerExceptions());
            writeLineLocked("Capture internal debug: " + settings.captureInternalDebug());
            writeLineLocked("====================================");
        } catch (final IOException exception) {
            writer = null;
            currentFile = null;
            plugin.getLogger().log(Level.WARNING, "Could not open debuglog file in " + folder.getPath() + ".", exception);
        }
    }

    private File resolveFolder(final String configuredFolder) {
        final File folder = new File(configuredFolder == null || configuredFolder.isBlank() ? "debuglog" : configuredFolder);
        if (folder.isAbsolute()) {
            return folder;
        }
        return new File(plugin.getDataFolder(), folder.getPath());
    }

    private File createLogFile(final File folder) {
        final String timestamp = FILE_TIME_FORMAT.format(ZonedDateTime.now());
        final String prefix = settings.filePrefix().isBlank() ? "debug" : settings.filePrefix();
        File file = new File(folder, prefix + "-" + timestamp + ".log");
        int counter = 2;
        while (file.exists()) {
            file = new File(folder, prefix + "-" + timestamp + "-" + counter + ".log");
            counter++;
        }
        return file;
    }

    private void attachHandlersLocked() {
        if (writer == null || attached) {
            return;
        }
        plugin.getLogger().addHandler(handler);
        if (settings.captureServerExceptions()) {
            rootLogger.addHandler(handler);
        }
        attached = true;
        plugin.getLogger().info("Debuglog enabled. Writing to " + currentFile.getPath());
    }

    private void detachHandlersLocked() {
        if (!attached) {
            return;
        }
        plugin.getLogger().removeHandler(handler);
        rootLogger.removeHandler(handler);
        attached = false;
    }

    private void closeLocked() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
            writer.close();
        } catch (final IOException ignored) {
            // Nothing useful can be done while the plugin is shutting down.
        } finally {
            writer = null;
            currentFile = null;
            recentSequences.clear();
            recentSequenceSet.clear();
        }
    }

    private void publish(final LogRecord record) {
        if (record == null) {
            return;
        }
        synchronized (lock) {
            if (writer == null || !isRelevant(record) || !rememberSequence(record.getSequenceNumber())) {
                return;
            }
            writeLineLocked(formatRecord(record));
            if (record.getThrown() != null && settings.includeStacktraces()) {
                writeLineLocked(stacktrace(record.getThrown()));
            }
        }
    }

    private boolean isRelevant(final LogRecord record) {
        if (record.getLevel().intValue() < settings.minimumLevel().intValue()) {
            return false;
        }

        final String loggerName = record.getLoggerName();
        if (containsPluginMarker(loggerName) || (loggerName != null && loggerName.startsWith(PACKAGE_PREFIX))) {
            return true;
        }

        if (containsPluginMarker(record.getMessage())) {
            return true;
        }

        return settings.captureServerExceptions() && stacktraceContainsPlugin(record.getThrown());
    }

    private boolean containsPluginMarker(final String value) {
        if (value == null) {
            return false;
        }
        final String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains(plugin.getName().toLowerCase(Locale.ROOT))
                || normalized.contains("craftplayplotextras")
                || normalized.contains("craftplay-plot-extras");
    }

    private boolean stacktraceContainsPlugin(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            for (final StackTraceElement element : current.getStackTrace()) {
                if (element.getClassName().startsWith(PACKAGE_PREFIX)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean rememberSequence(final long sequence) {
        if (recentSequenceSet.contains(sequence)) {
            return false;
        }
        recentSequenceSet.add(sequence);
        recentSequences.add(sequence);
        while (recentSequences.size() > RECENT_RECORD_LIMIT) {
            final Long removed = recentSequences.poll();
            if (removed != null) {
                recentSequenceSet.remove(removed);
            }
        }
        return true;
    }

    private String formatRecord(final LogRecord record) {
        final ZonedDateTime time = Instant.ofEpochMilli(record.getMillis()).atZone(ZoneId.systemDefault());
        final String source = record.getSourceClassName() == null
                ? record.getLoggerName()
                : record.getSourceClassName();
        return "[" + LINE_TIME_FORMAT.format(time) + "] ["
                + record.getLevel().getName() + "] ["
                + source + "] " + formatMessage(record);
    }

    private String formatMessage(final LogRecord record) {
        final String message = record.getMessage();
        if (message == null) {
            return "";
        }
        final Object[] parameters = record.getParameters();
        if (parameters == null || parameters.length == 0) {
            return message;
        }
        try {
            return MessageFormat.format(message, parameters);
        } catch (final IllegalArgumentException ignored) {
            return message;
        }
    }

    private void writeDirect(final String level, final String message, final Throwable throwable) {
        synchronized (lock) {
            if (writer == null) {
                return;
            }
            writeLineLocked(formatDirectLine(level, message));
            if (throwable != null && settings.includeStacktraces()) {
                writeLineLocked(stacktrace(throwable));
            }
        }
    }

    private String formatDirectLine(final String level, final String message) {
        return "[" + LINE_TIME_FORMAT.format(ZonedDateTime.now()) + "] [" + level + "] " + message;
    }

    private void writeLineLocked(final String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (final IOException exception) {
            closeLocked();
            plugin.getLogger().log(Level.WARNING, "Could not write to debuglog file.", exception);
        }
    }

    private String stacktrace(final Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private Settings readSettings(final FileConfiguration configuration) {
        final boolean enabled = configuration.getBoolean("debug-log.enabled", false);
        final String folder = configuration.getString("debug-log.folder", "debuglog");
        final String filePrefix = configuration.getString("debug-log.file-prefix", "debug");
        final Level minimumLevel = parseLevel(configuration.getString("debug-log.minimum-level", "INFO"));
        final boolean includeStacktraces = configuration.getBoolean("debug-log.include-stacktraces", true);
        final boolean captureServerExceptions = configuration.getBoolean("debug-log.capture-server-exceptions", true);
        final boolean captureInternalDebug = configuration.getBoolean("debug-log.capture-internal-debug", false);
        return new Settings(enabled, folder, filePrefix, minimumLevel, includeStacktraces, captureServerExceptions, captureInternalDebug);
    }

    private Level parseLevel(final String configuredLevel) {
        if (configuredLevel == null || configuredLevel.isBlank()) {
            return Level.INFO;
        }
        try {
            return Level.parse(configuredLevel.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return Level.INFO;
        }
    }

    private record Settings(
            boolean enabled,
            String folder,
            String filePrefix,
            Level minimumLevel,
            boolean includeStacktraces,
            boolean captureServerExceptions,
            boolean captureInternalDebug
    ) {

        private static Settings defaults() {
            return new Settings(false, "debuglog", "debug", Level.INFO, true, true, false);
        }
    }

    private final class DebugFileHandler extends Handler {

        private DebugFileHandler() {
            setLevel(Level.INFO);
        }

        @Override
        public void publish(final LogRecord record) {
            DebugLogService.this.publish(record);
        }

        @Override
        public void flush() {
            synchronized (lock) {
                if (writer == null) {
                    return;
                }
                try {
                    writer.flush();
                } catch (final IOException ignored) {
                    // The next write will close the writer and report the problem.
                }
            }
        }

        @Override
        public void close() {
            DebugLogService.this.close("Debuglog handler closed.");
        }
    }
}
