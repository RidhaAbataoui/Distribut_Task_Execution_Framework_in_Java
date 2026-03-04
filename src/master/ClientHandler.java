package master;

import common.*;
import observer.*;
import persistence.TaskPersistence;
import strategy.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;

/**
 * Handles a single client connection in its own thread.
 *
 * Updated for Progress Tracking (Version 3 feature):
 * - Creates a ProgressPublisher (Subject in Observer Pattern)
 * - Attaches three observers:
 * 1. ConsoleProgressObserver → server-side progress bar on stdout
 * 2. LogFileProgressObserver → per-task log file in logs/
 * 3. SocketProgressObserver → real-time push to the connected client
 * - Passes the publisher to the strategy's execute() call
 *
 * The strategy publishes progress events without knowing who receives them.
 * This is the core benefit of the Observer Pattern: loose coupling.
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
        log("Handling connection...");

        try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            // ── Step 1: Receive TaskRequest ──
            TaskRequest request = (TaskRequest) in.readObject();
            log("Task received — mode: " + request.getExecutionMode()
                    + ", priority: " + request.getPriority()
                    + ", files: " + request.getInputFiles().size());

            // ── Step 2: Wrap in Task (adds ID, status, timestamps) ──
            Task task = new Task(request);
            log("Task created: " + task.getTaskId());

            // ── Step 3: Submit to scheduler (PriorityQueue) ──
            scheduler.submitTask(task);

            // ── Step 4: Persist as PENDING ──
            persistence.saveTask(task);

            // ── Step 5: Get next task from scheduler ──
            Task nextTask = scheduler.getNextTask();
            if (nextTask != null) {
                nextTask.setStatus(Task.STATUS_RUNNING);
                persistence.updateTask(nextTask);

                // ── Step 6: Create temp working directory ──
                Path workDir = Files.createTempDirectory("task_" + nextTask.getTaskId() + "_");
                log("Working directory: " + workDir);

                // ── Step 7: Select strategy based on client's request mode ──
                ExecutionStrategy strategy;
                if (nextTask.getRequest().isDistributed()) {
                    strategy = distributedStrategy;
                } else {
                    strategy = localStrategy;
                }
                log("Executing with strategy: " + strategy.getStrategyName());

                // ── Step 8: Set up Observer Pattern for progress tracking ──
                ProgressPublisher publisher = new ProgressPublisher();

                // Observer 1: console progress bar on the Master's stdout
                publisher.addObserver(new ConsoleProgressObserver());

                // Observer 2: append-only log file per task (logs/<taskId>-progress.log)
                String logsDir = config.ConfigManager.getInstance().getLogsDir();
                String logFile = logsDir + "/" + nextTask.getTaskId() + "-progress.log";
                publisher.addObserver(new LogFileProgressObserver(logFile));

                // Observer 3: real-time push to the connected client over the socket
                SocketProgressObserver socketObs = new SocketProgressObserver(out, publisher);
                publisher.addObserver(socketObs);

                log("Progress observers registered (console, log-file, socket).");

                // ── Step 9: Execute with progress publisher ──
                TaskResult result;
                try {
                    result = strategy.execute(nextTask, workDir, publisher);
                    nextTask.setStatus(result.isSuccess() ? Task.STATUS_COMPLETED : Task.STATUS_FAILED);
                } catch (Exception e) {
                    result = new TaskResult(false, "Execution error: " + e.getMessage(), null);
                    nextTask.setStatus(Task.STATUS_FAILED);
                    publisher.publish(100, "FAILED", "Execution error: " + e.getMessage());
                }

                // ── Step 10: Store result and persist ──
                nextTask.setResult(result);
                persistence.updateTask(nextTask);
                persistence.saveSummary();

                // ── Step 11: Send final TaskResult to client ──
                // The client reads objects from the stream:
                // ProgressUpdate → display progress bar, keep reading
                // TaskResult → done!
                out.writeObject(result);
                out.flush();

                log("Task " + nextTask.getTaskId() + " → " + nextTask.getStatus());

                // Cleanup
                cleanupDir(workDir);
            }

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
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
