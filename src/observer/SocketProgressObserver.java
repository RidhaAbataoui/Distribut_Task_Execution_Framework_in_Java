package observer;

import common.ProgressUpdate;

import java.io.ObjectOutputStream;

/**
 * Strategy 3 — SocketProgressObserver
 *
 * Observer implementation that pushes each progress event directly
 * to the waiting client via the existing ObjectOutputStream.
 *
 * The Client reads a stream of objects:
 * - ProgressUpdate → display progress bar, keep reading
 * - TaskResult → task complete, display result and exit
 *
 * This keeps the worker completely decoupled from the client:
 * the worker calls publisher.publish(...) and has no idea that
 * the update ends up on a network socket.
 *
 * If the socket breaks mid-task (client disconnects), this observer
 * silently becomes a no-op and unregisters itself from the publisher.
 */
public class SocketProgressObserver implements ProgressObserver {

    private final ObjectOutputStream out;
    private final ProgressPublisher publisher; // needed for self-removal on error
    private volatile boolean active = true;

    /**
     * @param out       the ObjectOutputStream connected to the client socket
     * @param publisher the publisher to unregister from if the socket dies
     */
    public SocketProgressObserver(ObjectOutputStream out, ProgressPublisher publisher) {
        this.out = out;
        this.publisher = publisher;
    }

    @Override
    public void onProgress(ProgressUpdate update) {
        if (!active)
            return;
        try {
            synchronized (out) {
                out.writeObject(update);
                out.flush();
                // Reset the stream to avoid caching identical objects
                out.reset();
            }
        } catch (Exception e) {
            // Client disconnected — stop trying and remove self
            active = false;
            publisher.removeObserver(this);
            System.err.println("[SocketProgressObserver] Client disconnected: " + e.getMessage());
        }
    }
}
