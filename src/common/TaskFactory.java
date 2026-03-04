package common;

import java.util.List;

/**
 * Factory Method Pattern — TaskFactory
 *
 * Problem:
 * Creating different kinds of TaskRequest objects (LOCAL vs DISTRIBUTED)
 * requires knowing which constructor to call, what fields to set, and what
 * validation to apply. Spreading this logic across the codebase leads to
 * duplication and errors.
 *
 * Solution:
 * Centralize creation in a dedicated factory. Callers use descriptive
 * methods (createLocalTask, createDistributedTask) without knowing the
 * internal structure of TaskRequest.
 *
 * Benefits:
 * - Single place to apply validation / default values
 * - Callers are decoupled from the constructor signature
 * - Easy to extend: add createHighPriorityTask(), createBatchTask(), etc.
 * - Makes unit testing easier (mock the factory)
 *
 * Design Pattern: Factory Method (GoF Creational Patterns)
 *
 * Where used:
 * - ClientCLI → calls TaskFactory when building requests interactively
 * - Client → calls TaskFactory when parsing CLI arguments
 * - Unit tests → use TaskFactory to produce test fixtures
 */
public class TaskFactory {

    // Private constructor — this is a utility class, not meant to be instantiated
    private TaskFactory() {
    }

    /**
     * Creates a LOCAL TaskRequest (Version 1 compatible).
     *
     * @param jarBytes   compiled JAR bytes
     * @param options    command-line options for the JAR
     * @param inputFiles input files to process
     * @return a ready-to-send LOCAL TaskRequest
     * @throws IllegalArgumentException if jarBytes is null or empty
     */
    public static TaskRequest createLocalTask(byte[] jarBytes,
            String[] options,
            List<FileData> inputFiles) {
        if (jarBytes == null || jarBytes.length == 0) {
            throw new IllegalArgumentException("TaskFactory: JAR bytes must not be null or empty.");
        }
        if (options == null) {
            options = new String[0];
        }
        if (inputFiles == null) {
            inputFiles = new java.util.ArrayList<>();
        }
        return new TaskRequest(jarBytes, options, inputFiles);
    }

    /**
     * Creates a DISTRIBUTED TaskRequest (Version 2).
     *
     * @param masterJarBytes JAR bytes for the master (split/merge)
     * @param workerJarBytes JAR bytes for the worker (processes one chunk)
     * @param options        command-line options for the slave workers
     * @param inputFiles     input files to be split and distributed
     * @param priority       task priority (0 = normal, higher = more urgent)
     * @return a ready-to-send DISTRIBUTED TaskRequest
     * @throws IllegalArgumentException if either JAR is null or empty
     */
    public static TaskRequest createDistributedTask(byte[] masterJarBytes,
            byte[] workerJarBytes,
            String[] options,
            List<FileData> inputFiles,
            int priority) {
        if (masterJarBytes == null || masterJarBytes.length == 0) {
            throw new IllegalArgumentException("TaskFactory: Master JAR bytes must not be null or empty.");
        }
        if (workerJarBytes == null || workerJarBytes.length == 0) {
            throw new IllegalArgumentException("TaskFactory: Worker JAR bytes must not be null or empty.");
        }
        if (options == null) {
            options = new String[0];
        }
        if (inputFiles == null) {
            inputFiles = new java.util.ArrayList<>();
        }
        if (priority < 0) {
            priority = 0;
        }
        return new TaskRequest(masterJarBytes, workerJarBytes, options, inputFiles, priority);
    }

    /**
     * Convenience factory: creates a high-priority distributed task (priority =
     * 10).
     * Useful for urgent batch jobs that must jump the queue.
     */
    public static TaskRequest createUrgentDistributedTask(byte[] masterJarBytes,
            byte[] workerJarBytes,
            String[] options,
            List<FileData> inputFiles) {
        return createDistributedTask(masterJarBytes, workerJarBytes, options, inputFiles, 10);
    }
}
