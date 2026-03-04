package observer;

import common.ProgressUpdate;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Strategy 2 — LogFileProgressObserver
 *
 * Observer implementation that writes each progress event to a
 * rotating log file (one per task). Useful for:
 * - post-mortem analysis
 * - dashboards that tail the log
 * - metrics collection
 *
 * Log format:
 * 2026-03-04T21:05:12 [SPLIT ] 20% Splitting input into 3 chunks
 */
public class LogFileProgressObserver implements ProgressObserver {

    private final String logFilePath;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * @param logFilePath absolute or relative path to the log file
     *                    (created/appended if it exists)
     */
    public LogFileProgressObserver(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    @Override
    public void onProgress(ProgressUpdate update) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFilePath, true))) {
            pw.printf("%s [%-8s] %3d%%  %s%n",
                    LocalDateTime.now().format(FMT),
                    update.getPhase(),
                    update.getPercent(),
                    update.getMessage());
        } catch (IOException e) {
            System.err.println("[LogFileProgressObserver] Cannot write to log: " + e.getMessage());
        }
    }
}
