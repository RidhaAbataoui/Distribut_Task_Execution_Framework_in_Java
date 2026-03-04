package strategy;

import common.Task;
import common.TaskResult;
import observer.ProgressPublisher;

import java.nio.file.Path;

/**
 * Strategy Pattern — ExecutionStrategy
 *
 * Defines how a task is executed.
 * Implementations:
 * - LocalExecutionStrategy (V1): execute on this machine
 * - DistributedExecutionStrategy (V2): distribute to slaves
 *
 * The optional ProgressPublisher allows strategies to emit progress
 * events using the Observer Pattern without knowing who is listening.
 */
public interface ExecutionStrategy {

    /**
     * Executes the given task using a provided progress publisher.
     *
     * @param task      The task to execute
     * @param workDir   The temporary working directory for file I/O
     * @param publisher The progress publisher (observers already attached)
     * @return The result of the execution
     * @throws Exception if execution fails
     */
    TaskResult execute(Task task, Path workDir, ProgressPublisher publisher) throws Exception;

    /**
     * Backward-compatible default: executes without progress tracking.
     */
    default TaskResult execute(Task task, Path workDir) throws Exception {
        return execute(task, workDir, new ProgressPublisher());
    }

    /**
     * Returns the name of this strategy for logging.
     */
    String getStrategyName();
}
