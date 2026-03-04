package common;

import java.io.Serializable;
import java.util.UUID;

/**
 * Wraps a TaskRequest with metadata for tracking and scheduling.
 * Implements Comparable for PriorityQueue ordering.
 */
public class Task implements Comparable<Task>, Serializable {
    private static final long serialVersionUID = 1L;

    // ── Status constants ──
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private String taskId;
    private TaskRequest request;
    private String status;
    private long createdAt;
    private long completedAt;
    private TaskResult result;

    public Task(TaskRequest request) {
        this.taskId = UUID.randomUUID().toString().substring(0, 8);
        this.request = request;
        this.status = STATUS_PENDING;
        this.createdAt = System.currentTimeMillis();
        this.completedAt = 0;
    }

    // ── PriorityQueue: higher priority = processed first ──
    @Override
    public int compareTo(Task other) {
        return Integer.compare(other.request.getPriority(), this.request.getPriority());
    }

    // ── Getters and Setters ──

    public String getTaskId() {
        return taskId;
    }

    public TaskRequest getRequest() {
        return request;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        if (STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status)) {
            this.completedAt = System.currentTimeMillis();
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public TaskResult getResult() {
        return result;
    }

    public void setResult(TaskResult result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "Task{id=" + taskId + ", status=" + status
                + ", priority=" + request.getPriority()
                + ", mode=" + request.getExecutionMode() + "}";
    }
}
