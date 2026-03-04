package common;

import java.io.Serializable;
import java.util.List;

/**
 * Represents the result of a task execution.
 * Contains success status, console output, and any generated output files.
 */
public class TaskResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String consoleOutput;
    private List<FileData> outputFiles;

    public TaskResult(boolean success, String consoleOutput, List<FileData> outputFiles) {
        this.success = success;
        this.consoleOutput = consoleOutput;
        this.outputFiles = outputFiles;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getConsoleOutput() {
        return consoleOutput;
    }

    public List<FileData> getOutputFiles() {
        return outputFiles;
    }
}
