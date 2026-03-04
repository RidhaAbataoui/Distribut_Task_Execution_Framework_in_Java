package master;

import common.Task;

import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Task Scheduler — Uses PriorityQueue<Task>
 *
 * Schedules tasks by priority (higher priority = processed first).
 * Thread-safe with ReentrantLock.
 */
public class TaskScheduler {

    private final PriorityQueue<Task> taskQueue; // PriorityQueue collection
    private final ReentrantLock lock;

    public TaskScheduler() {
        this.taskQueue = new PriorityQueue<>();
        this.lock = new ReentrantLock();
    }

    /**
     * Adds a task to the priority queue.
     */
    public void submitTask(Task task) {
        lock.lock();
        try {
            taskQueue.add(task);
            System.out.println("[Scheduler] Task queued: " + task.getTaskId()
                    + " (priority=" + task.getRequest().getPriority()
                    + ", queue size=" + taskQueue.size() + ")");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the highest-priority task.
     * Returns null if the queue is empty.
     */
    public Task getNextTask() {
        lock.lock();
        try {
            Task task = taskQueue.poll();
            if (task != null) {
                System.out.println("[Scheduler] Dispatching task: " + task.getTaskId()
                        + " (priority=" + task.getRequest().getPriority() + ")");
            }
            return task;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of pending tasks.
     */
    public int getPendingCount() {
        lock.lock();
        try {
            return taskQueue.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if there are pending tasks.
     */
    public boolean hasPendingTasks() {
        lock.lock();
        try {
            return !taskQueue.isEmpty();
        } finally {
            lock.unlock();
        }
    }
}
