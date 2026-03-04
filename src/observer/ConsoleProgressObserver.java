package observer;

import common.ProgressUpdate;

/**
 * Strategy 1 — ConsoleProgressObserver
 *
 * Observer implementation that renders progress as a visual
 * progress bar directly on the server console (stdout).
 *
 * Example output:
 * [SPLIT ] [████████░░░░░░░░░░░░] 40% Splitting input into chunks
 *
 * This is useful for local monitoring and debugging when running
 * the Master in a terminal.
 */
public class ConsoleProgressObserver implements ProgressObserver {

    private static final int BAR_WIDTH = 20;

    @Override
    public void onProgress(ProgressUpdate update) {
        String bar = buildBar(update.getPercent());
        System.out.printf("[%-8s] [%s] %3d%%  %s%n",
                update.getPhase(),
                bar,
                update.getPercent(),
                update.getMessage());
    }

    /**
     * Builds a text progress bar of fixed width.
     * Filled portion uses █, empty portion uses ░.
     */
    private String buildBar(int percent) {
        int filled = (int) Math.round(percent / 100.0 * BAR_WIDTH);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BAR_WIDTH; i++) {
            sb.append(i < filled ? '█' : '░');
        }
        return sb.toString();
    }
}
