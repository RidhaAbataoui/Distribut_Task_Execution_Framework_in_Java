package observer;

import common.ProgressUpdate;

/**
 * Observer Pattern — ProgressObserver (Observer interface)
 *
 * Any component that wants to react to task progress must implement
 * this interface. The Worker (Subject) does NOT know who implements it.
 *
 * Examples of implementations:
 * - ConsoleProgressObserver → prints to stdout
 * - LogFileProgressObserver → writes to a log file
 * - SocketProgressObserver → sends update through a socket to the client
 *
 * This keeps the system loosely coupled: adding a new observer
 * (e.g. a dashboard or metrics collector) requires zero changes to
 * the computation logic.
 */
public interface ProgressObserver {

    /**
     * Called whenever progress changes.
     *
     * @param update the ProgressUpdate event (percent, phase, message)
     */
    void onProgress(ProgressUpdate update);
}
