package observer;

import common.ProgressUpdate;

import java.util.ArrayList;
import java.util.List;

/**
 * Observer Pattern — ProgressPublisher (Subject / Observable)
 *
 * This is the central hub that:
 * 1) holds the list of registered ProgressObserver instances
 * 2) lets the execution strategy (or any component) publish
 * progress updates with a single call to publish()
 *
 * The publisher does NOT know what each observer does with the update.
 * This is intentional — it keeps the computation logic decoupled from
 * the notification mechanism.
 *
 * Strategy Pattern connection:
 * Different observer implementations represent different strategies for
 * handling progress (console, log file, socket, GUI, …).
 * They can be swapped or combined without touching the publisher.
 */
public class ProgressPublisher {

    private final List<ProgressObserver> observers = new ArrayList<>();

    /**
     * Registers a new observer.
     *
     * @param observer the component that wants to receive progress events
     */
    public synchronized void addObserver(ProgressObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Removes a previously registered observer.
     *
     * @param observer the component to remove
     */
    public synchronized void removeObserver(ProgressObserver observer) {
        observers.remove(observer);
    }

    /**
     * Publishes a progress event to ALL registered observers.
     * Called by the execution strategy each time progress advances.
     *
     * @param percent 0–100
     * @param phase   e.g. "SPLIT", "COMPUTING", "MERGE"
     * @param message human-readable detail
     */
    public void publish(int percent, String phase, String message) {
        ProgressUpdate update = new ProgressUpdate(percent, phase, message);
        List<ProgressObserver> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(observers);
        }
        for (ProgressObserver obs : snapshot) {
            try {
                obs.onProgress(update);
            } catch (Exception e) {
                // One broken observer must not interrupt the others
                System.err.println("[ProgressPublisher] Observer error: " + e.getMessage());
            }
        }
    }
}
