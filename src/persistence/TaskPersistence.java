package persistence;

import common.Task;
import config.ConfigManager;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Persistence — TaskPersistence
 *
 * Saves task history to JSON files in the data/ directory.
 * Allows recovery after restart and provides persistent logs.
 *
 * Uses ConcurrentHashMap for thread-safe task storage.
 */
public class TaskPersistence {

    private final Map<String, Task> taskMap; // Map<TaskID, Task>
    private final String dataDir;
    private final SimpleDateFormat dateFormat;

    public TaskPersistence() {
        this.taskMap = new ConcurrentHashMap<>(); // Thread-safe map
        this.dataDir = ConfigManager.getInstance().getDataDir();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(dataDir));
        } catch (IOException e) {
            System.err.println("[Persistence] Could not create data directory: " + e.getMessage());
        }
    }

    /**
     * Registers a task in the map and persists it.
     */
    public synchronized void saveTask(Task task) {
        taskMap.put(task.getTaskId(), task);
        persistToFile(task);
        System.out.println("[Persistence] Task saved: " + task.getTaskId());
    }

    /**
     * Updates an existing task's status and persists.
     */
    public synchronized void updateTask(Task task) {
        taskMap.put(task.getTaskId(), task);
        persistToFile(task);
    }

    /**
     * Retrieves a task by ID.
     */
    public Task getTask(String taskId) {
        return taskMap.get(taskId);
    }

    /**
     * Returns all stored tasks.
     */
    public Collection<Task> getAllTasks() {
        return taskMap.values();
    }

    /**
     * Returns the number of stored tasks.
     */
    public int getTaskCount() {
        return taskMap.size();
    }

    /**
     * Persists a single task to a JSON file.
     */
    private void persistToFile(Task task) {
        String fileName = dataDir + "/task_" + task.getTaskId() + ".json";
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            pw.println("{");
            pw.println("  \"taskId\": \"" + task.getTaskId() + "\",");
            pw.println("  \"status\": \"" + task.getStatus() + "\",");
            pw.println("  \"priority\": " + task.getRequest().getPriority() + ",");
            pw.println("  \"executionMode\": \"" + task.getRequest().getExecutionMode() + "\",");
            pw.println("  \"createdAt\": \"" + dateFormat.format(new Date(task.getCreatedAt())) + "\",");

            if (task.getCompletedAt() > 0) {
                pw.println("  \"completedAt\": \"" + dateFormat.format(new Date(task.getCompletedAt())) + "\",");
                pw.println("  \"durationMs\": " + (task.getCompletedAt() - task.getCreatedAt()) + ",");
            }

            if (task.getResult() != null) {
                pw.println("  \"success\": " + task.getResult().isSuccess() + ",");
                String console = task.getResult().getConsoleOutput();
                if (console != null) {
                    // Escape special JSON characters
                    console = console.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\t", "\\t");
                    pw.println("  \"consoleOutput\": \"" + console + "\",");
                }
                if (task.getResult().getOutputFiles() != null) {
                    pw.println("  \"outputFileCount\": " + task.getResult().getOutputFiles().size());
                } else {
                    pw.println("  \"outputFileCount\": 0");
                }
            } else {
                pw.println("  \"result\": null");
            }

            pw.println("}");
        } catch (IOException e) {
            System.err.println("[Persistence] Error saving task " + task.getTaskId() + ": " + e.getMessage());
        }
    }

    /**
     * Saves a summary of all tasks to tasks.json.
     */
    public synchronized void saveSummary() {
        String fileName = dataDir + "/tasks.json";
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            pw.println("[");
            Iterator<Task> it = taskMap.values().iterator();
            while (it.hasNext()) {
                Task task = it.next();
                pw.println("  {");
                pw.println("    \"taskId\": \"" + task.getTaskId() + "\",");
                pw.println("    \"status\": \"" + task.getStatus() + "\",");
                pw.println("    \"priority\": " + task.getRequest().getPriority() + ",");
                pw.println("    \"mode\": \"" + task.getRequest().getExecutionMode() + "\",");
                pw.println("    \"created\": \"" + dateFormat.format(new Date(task.getCreatedAt())) + "\"");
                pw.print("  }");
                if (it.hasNext())
                    pw.println(",");
                else
                    pw.println();
            }
            pw.println("]");
            System.out.println("[Persistence] Summary saved: " + taskMap.size() + " task(s)");
        } catch (IOException e) {
            System.err.println("[Persistence] Error saving summary: " + e.getMessage());
        }
    }
}
