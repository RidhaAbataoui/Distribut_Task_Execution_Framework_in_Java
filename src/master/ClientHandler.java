package master;

import common.*;
import observer.*;
import persistence.TaskPersistence;
import strategy.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;

/**
 * Handles a SINGLE client connection in its own thread.
 *
 * Updated for persistent sessions (v3):
 * - The client connects ONCE and can send MULTIPLE TaskRequests over the same
 * socket.
 * - This handler loops, reading one TaskRequest at a time, executing it,
 * sending
 * back progress + result, then looping again — until the client disconnects.
 * - Each task gets its own progress publisher (Observer Pattern).
 * - The client ID is fixed for the whole session, so the user always knows
 * which
 * terminal they are in.
 */
public class ClientHandler extends Thread {

    private final Socket socket;
    private final int clientId;
    private final ExecutionStrategy localStrategy;
    private final ExecutionStrategy distributedStrategy;
    private final TaskScheduler scheduler;
    private final TaskPersistence persistence;

    public ClientHandler(Socket socket, int clientId,
            ExecutionStrategy localStrategy,
            ExecutionStrategy distributedStrategy,
            TaskScheduler scheduler,
            TaskPersistence persistence) {
        this.socket = socket;
        this.clientId = clientId;
        this.localStrategy = localStrategy;
        this.distributedStrategy = distributedStrategy;
        this.scheduler = scheduler;
        this.persistence = persistence;
    }

    @Override
    public void run() {
        log("Session started — waiting for tasks...");

        // ── Create the streams ONCE for the whole session ──
        // (Matches the client which also creates streams once.)
        try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            int taskCount = 0;

            // ── Session loop: handle as many tasks as the client sends ──
            while (true) {
                TaskRequest request;
                try {
                    // Blocks until the client sends a new TaskRequest (or disconnects)
                    Object obj = in.readObject();
                    if (!(obj instanceof TaskRequest)) {
                        log("Unexpected object received — ignoring.");
                        continue;
                    }
                    request = (TaskRequest) obj;
                } catch (EOFException | java.net.SocketException e) {
                    // Client closed the connection (said 'n' to "Run another task?")
                    log("Client disconnected after " + taskCount + " task(s). Session closed.");
                    break;
                }

                taskCount++;
                log("Task #" + taskCount + " received — mode: " + request.getExecutionMode()
                        + ", priority: " + request.getPriority()
                        + ", files: " + request.getInputFiles().size());

                // ── Wrap in Task ──
                Task task = new Task(request);
                log("Task created: " + task.getTaskId());

                // ── Submit to priority queue (non-blocking) ──
                scheduler.submitTask(task);
                persistence.saveTask(task);

                // ── Wait for an execution slot (BLOCKS if slaves are all busy) ──
                log("Waiting for available execution slot...");
                try {
                    scheduler.acquireSlot();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted while waiting for slot.");
                    break;
                }

                Path workDir = null; // Declare outside try-finally for cleanup
                try {
                    // ── Get next highest-priority task from queue ──
                    Task nextTask = scheduler.getNextTask();
                    if (nextTask == null) {
                        scheduler.releaseSlot(); // Release slot if no task found
                        log("Scheduler returned null — skipping.");
                        continue;
                    }

                    nextTask.setStatus(Task.STATUS_RUNNING);
                    persistence.updateTask(nextTask);

                    // ── Create temp working directory ──
                    workDir = Files.createTempDirectory("task_" + nextTask.getTaskId() + "_");
                    log("Working directory: " + workDir);

                    // ── Select strategy ──
                    ExecutionStrategy strategy = nextTask.getRequest().isDistributed()
                            ? distributedStrategy
                            : localStrategy;
                    log("Executing task #" + taskCount + " with strategy: " + strategy.getStrategyName());

                    // ── Observer Pattern: progress tracking ──
                    ProgressPublisher publisher = new ProgressPublisher();
                    publisher.addObserver(new ConsoleProgressObserver());

                    String logsDir = config.ConfigManager.getInstance().getLogsDir();
                    String logFile = logsDir + "/" + nextTask.getTaskId() + "-progress.log";
                    publisher.addObserver(new LogFileProgressObserver(logFile));

                    SocketProgressObserver socketObs = new SocketProgressObserver(out, publisher);
                    publisher.addObserver(socketObs);

                    log("Progress observers registered (console, log-file, socket).");

                    // ── Execute ──
                    TaskResult result;
                    try {
                        result = strategy.execute(nextTask, workDir, publisher);
                        nextTask.setStatus(result.isSuccess() ? Task.STATUS_COMPLETED : Task.STATUS_FAILED);
                    } catch (Exception e) {
                        result = new TaskResult(false, "Execution error: " + e.getMessage(), null);
                        nextTask.setStatus(Task.STATUS_FAILED);
                        publisher.publish(100, "FAILED", "Execution error: " + e.getMessage());
                    }

                    // ── Store result and persist ──
                    nextTask.setResult(result);
                    persistence.updateTask(nextTask);
                    persistence.saveSummary();

                    // ── Send final TaskResult back to client ──
                    // Must synchronize on `out` because SocketProgressObserver
                    // also writes to the same stream (synchronized on `out`).
                    // Without this, the 100% ProgressUpdate and the TaskResult
                    // can interleave and corrupt the ObjectOutputStream.
                    synchronized (out) {
                        out.reset(); // clear serialization cache before each response
                        out.writeObject(result);
                        out.flush();
                    }

                    log("Task #" + taskCount + " [" + task.getTaskId() + "] → " + nextTask.getStatus());

                } finally {
                    // ── Release the slot so the next waiting task can proceed ──
                    scheduler.releaseSlot();
                    if (workDir != null)
                        cleanupDir(workDir);
                }

                // ── Loop back and wait for the next TaskRequest ──
            }

        } catch (Exception e) {
            log("Session error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            log("Connection closed.");
        }
    }

    private void cleanupDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            /* ignore */ }
                    });
        } catch (IOException e) {
            // ignore
        }
    }

    private void log(String message) {
        System.out.println("[Client #" + clientId + "] " + message);
    }
}
