package master;

import common.Task;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Task Scheduler — Blocking, Single-Slot Priority Queue
 *
 * Design:
 * - Uses a PriorityBlockingQueue so tasks are ordered by priority.
 * - Uses a Semaphore with EXACTLY 1 permit (binary semaphore).
 *
 * Why only 1 slot?
 * Each DISTRIBUTED task uses ALL available slaves simultaneously.
 * Allowing concurrent tasks would have two tasks fighting over the
 * same slaves at the same time → connection failures → slaves get
 * unregistered → the next client finds 0 slaves and fails.
 *
 * With 1 slot:
 * - Only ONE task executes at a time (local or distributed).
 * - It gets exclusive access to the full slave pool.
 * - When it finishes, the slot is released and the next
 * highest-priority task proceeds.
 * - Priority order and FIFO fairness are still fully preserved.
 *
 * Workflow for each ClientHandler:
 * 1. submitTask() → adds task to the priority queue (non-blocking)
 * 2. acquireSlot() → BLOCKS until the current task finishes
 * 3. getNextTask() → takes the highest-priority pending task
 * (may be a different client's task if higher priority — by design)
 * 4. releaseSlot() → called after execution, unblocks the next waiter
 */
public class TaskScheduler {

    private final PriorityBlockingQueue<Task> taskQueue;
    // Binary semaphore — only ONE task runs at a time.
    private final Semaphore slots;

    public TaskScheduler() {
        this.taskQueue = new PriorityBlockingQueue<>();
        this.slots = new Semaphore(1, true); // fair = FIFO when equal priority
    }

    /**
     * No-op: the slot count is always 1 regardless of slave count.
     * Kept so Master.handleSlaveRegistration() can still call it
     * without compile errors, but it does nothing.
     */
    public void setSlotCount(int count) {
        // Intentionally a no-op.
        // One slot = one task at a time = no slave sharing conflicts.
        System.out.println("[Scheduler] Slave count updated to " + count
                + " (slot stays at 1 — exclusive access per task).");
    }
    // ─────────────────────────────────────────────────────────────
    // Task lifecycle — called by ClientHandler
    // ─────────────────────────────────────────────────────────────

    /**
     * Adds a task to the priority queue.
     * Non-blocking — returns immediately.
     */
    public void submitTask(Task task) {
        taskQueue.add(task);
        System.out.println("[Scheduler] Task queued: " + task.getTaskId()
                + " (priority=" + task.getRequest().getPriority()
                + ", queue size=" + taskQueue.size() + ")");
    }

    /**
     * BLOCKS until an execution slot is available.
     * A slot becomes free when another task calls releaseSlot().
     * Use this BEFORE getNextTask() to guarantee orderly dispatch.
     */
    public void acquireSlot() throws InterruptedException {
        System.out.println("[Scheduler] Waiting for execution slot... ("
                + slots.availablePermits() + " slot(s) free)");
        slots.acquire();
        System.out.println("[Scheduler] Slot acquired. (" + slots.availablePermits() + " remaining)");
    }

    /**
     * Releases an execution slot.
     * MUST be called after the task finishes (success or failure).
     */
    public void releaseSlot() {
        slots.release();
        System.out.println("[Scheduler] Slot released. (" + slots.availablePermits() + " now free)");
    }

    /**
     * Retrieves and removes the highest-priority task from the queue.
     * Should be called AFTER acquireSlot().
     * Returns the highest-priority task (may not be the caller's own task
     * if a higher-priority task arrived in the meantime — this is by design).
     */
    public Task getNextTask() {
        Task task = taskQueue.poll();
        if (task != null) {
            System.out.println("[Scheduler] Dispatching task: " + task.getTaskId()
                    + " (priority=" + task.getRequest().getPriority()
                    + ", remaining in queue: " + taskQueue.size() + ")");
        }
        return task;
    }

    /**
     * Returns the number of tasks currently waiting in the queue.
     */
    public int getPendingCount() {
        return taskQueue.size();
    }

    /**
     * Checks if there are pending tasks.
     */
    public boolean hasPendingTasks() {
        return !taskQueue.isEmpty();
    }
}
