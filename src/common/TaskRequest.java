package common;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a task to be executed remotely.
 *
 * Supports two modes:
 * - LOCAL (V1): jarBytes only
 * - DISTRIBUTED (V2): masterJarBytes + workerJarBytes
 */
public class TaskRequest implements Serializable {
    private static final long serialVersionUID = 2L;

    // ── V1: Local execution ──
    private byte[] jarBytes;

    // ── V2: Distributed execution ──
    private byte[] masterJarBytes;
    private byte[] workerJarBytes;

    // ── Common fields ──
    private String[] options;
    private List<FileData> inputFiles;
    private int priority; // higher = more urgent
    private String executionMode; // "local" or "distributed"

    /**
     * Constructor for LOCAL execution (V1 compatible).
     */
    public TaskRequest(byte[] jarBytes, String[] options, List<FileData> inputFiles) {
        this.jarBytes = jarBytes;
        this.options = options;
        this.inputFiles = inputFiles;
        this.priority = 0;
        this.executionMode = "local";
    }

    /**
     * Constructor for DISTRIBUTED execution (V2).
     */
    public TaskRequest(byte[] masterJarBytes, byte[] workerJarBytes,
            String[] options, List<FileData> inputFiles, int priority) {
        this.masterJarBytes = masterJarBytes;
        this.workerJarBytes = workerJarBytes;
        this.options = options;
        this.inputFiles = inputFiles;
        this.priority = priority;
        this.executionMode = "distributed";
    }

    // ── Getters ──

    public byte[] getJarBytes() {
        return jarBytes;
    }

    public byte[] getMasterJarBytes() {
        return masterJarBytes;
    }

    public byte[] getWorkerJarBytes() {
        return workerJarBytes;
    }

    public String[] getOptions() {
        return options;
    }

    public List<FileData> getInputFiles() {
        return inputFiles;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public boolean isDistributed() {
        return "distributed".equals(executionMode);
    }
}
