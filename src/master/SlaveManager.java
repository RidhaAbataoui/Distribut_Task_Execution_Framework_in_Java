package master;

import common.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SlaveManager — Dynamic slave pool.
 *
 * Slaves register themselves at startup via SlaveRegistration.
 * The pool grows/shrinks at runtime — no hardcoded config needed.
 *
 * Thread-safe: uses synchronizedList so multiple ClientHandler
 * threads can read/add/remove slaves concurrently.
 */
public class SlaveManager {

    /**
     * Holds connection info for one registered slave.
     */
    public static class SlaveInfo {
        private final String host;
        private final int port;

        public SlaveInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    // ── Dynamic pool — grows as slaves register ──
    private final List<SlaveInfo> slaves = Collections.synchronizedList(new ArrayList<>());

    private final int timeoutMs;
    private final int retryCount;

    public SlaveManager() {
        // Timeout and retry still come from config
        config.ConfigManager cfg = config.ConfigManager.getInstance();
        this.timeoutMs = cfg.getSlaveTimeout();
        this.retryCount = cfg.getSlaveRetry();

        System.out.println("[SlaveManager] Dynamic pool ready (0 slaves registered so far)");
    }

    // ─────────────────────────────────────────────────────────────
    // Registration API — called by Master's registration thread
    // ─────────────────────────────────────────────────────────────

    /**
     * Adds a newly registered slave to the pool.
     */
    public synchronized void registerSlave(String host, int port) {
        // Avoid duplicates
        for (SlaveInfo s : slaves) {
            if (s.getHost().equals(host) && s.getPort() == port) {
                System.out.println("[SlaveManager] Slave already registered: " + host + ":" + port);
                return;
            }
        }
        slaves.add(new SlaveInfo(host, port));
        System.out.println("[SlaveManager] ✓ Slave registered: " + host + ":" + port
                + "  (pool size: " + slaves.size() + ")");
    }

    /**
     * Removes a slave from the pool (e.g. after it fails).
     */
    public synchronized void unregisterSlave(String host, int port) {
        slaves.removeIf(s -> s.getHost().equals(host) && s.getPort() == port);
        System.out.println("[SlaveManager] Slave removed: " + host + ":" + port
                + "  (pool size: " + slaves.size() + ")");
    }

    /**
     * Returns a snapshot of the current live pool.
     */
    public synchronized List<SlaveInfo> getSlaves() {
        return new ArrayList<>(slaves);
    }

    public synchronized int getAvailableSlaveCount() {
        return slaves.size();
    }

    // ─────────────────────────────────────────────────────────────
    // Work distribution
    // ─────────────────────────────────────────────────────────────

    /**
     * Distributes chunks to slaves in parallel (indexed array for ordering).
     *
     * @param workerJarBytes Worker JAR to send to each slave
     * @param options        Options for the worker JAR
     * @param chunks         One chunk per slave
     * @return Ordered list of results (same order as chunks)
     */
    public List<TaskResult> distributeWork(byte[] workerJarBytes,
            String[] options,
            List<FileData> chunks) {
        List<SlaveInfo> pool = getSlaves(); // snapshot
        int numChunks = chunks.size();
        int numSlaves = Math.min(pool.size(), numChunks);

        if (numSlaves == 0) {
            List<TaskResult> failed = new ArrayList<>();
            for (int i = 0; i < numChunks; i++) {
                failed.add(new TaskResult(false, "No slaves available.", new ArrayList<>()));
            }
            return failed;
        }

        // Indexed array preserves chunk order even when slaves finish at different
        // times
        final TaskResult[] resultArray = new TaskResult[numChunks];
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numChunks; i++) {
            SlaveInfo slave = pool.get(i % numSlaves); // round-robin
            FileData chunk = chunks.get(i);
            final int idx = i;

            Thread t = new Thread(() -> {
                TaskResult result = sendChunkToSlave(slave, workerJarBytes, options, chunk, idx);
                resultArray[idx] = result;
                // If the slave failed, remove it from the pool
                if (!result.isSuccess()) {
                    unregisterSlave(slave.getHost(), slave.getPort());
                }
            });
            threads.add(t);
            t.start();
        }

        // Wait for all threads
        for (Thread t : threads) {
            try {
                t.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Convert to ordered list
        List<TaskResult> results = new ArrayList<>();
        for (int i = 0; i < numChunks; i++) {
            results.add(resultArray[i] != null
                    ? resultArray[i]
                    : new TaskResult(false, "Slave timed out.", new ArrayList<>()));
        }
        return results;
    }

    /**
     * Sends one chunk to one slave with retry logic.
     * Public so DistributedExecutionStrategy can call it with its own
     * threading model (for per-chunk progress tracking).
     */
    public TaskResult sendChunkToSlave(SlaveInfo slave, byte[] workerJarBytes,
            String[] options, FileData chunk, int index) {
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                System.out.println("  [SlaveManager] Chunk " + index
                        + " → " + slave + " (attempt " + attempt + ")");

                Socket socket = new Socket(slave.getHost(), slave.getPort());
                socket.setSoTimeout(timeoutMs);

                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                List<FileData> slaveFiles = new ArrayList<>();
                slaveFiles.add(chunk);
                TaskRequest req = new TaskRequest(workerJarBytes, options, slaveFiles);

                out.writeObject(req);
                out.flush();

                TaskResult result = (TaskResult) in.readObject();

                in.close();
                out.close();
                socket.close();

                System.out.println("  [SlaveManager] Chunk " + index
                        + " ← " + slave + ": " + (result.isSuccess() ? "OK ✓" : "FAILED ✗"));
                return result;

            } catch (Exception e) {
                System.err.println("  [SlaveManager] Chunk " + index
                        + " attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == retryCount) {
                    return new TaskResult(false,
                            "Slave " + slave + " unreachable: " + e.getMessage(),
                            new ArrayList<>());
                }
            }
        }
        return new TaskResult(false, "Slave communication failed.", new ArrayList<>());
    }
}
