package common;

import java.io.Serializable;

/**
 * ProgressUpdate — Serializable message sent through the socket stream.
 *
 * The Master sends a series of ProgressUpdate objects to the Client
 * while the task is running, followed by the final TaskResult.
 *
 * The Client reads objects from the stream: if it gets a ProgressUpdate
 * it displays the progress; if it gets a TaskResult the task is done.
 */
public class ProgressUpdate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int percent; // 0 – 100
    private final String phase; // e.g. "SPLIT", "COMPUTING", "MERGE"
    private final String message; // human-readable detail

    public ProgressUpdate(int percent, String phase, String message) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.phase = phase;
        this.message = message;
    }

    public int getPercent() {
        return percent;
    }

    public String getPhase() {
        return phase;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ProgressUpdate{" + percent + "%, phase=" + phase + ", msg=" + message + "}";
    }
}
