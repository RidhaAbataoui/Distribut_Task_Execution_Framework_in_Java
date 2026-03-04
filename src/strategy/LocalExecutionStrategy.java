package strategy;

import common.*;
import observer.ProgressPublisher;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy Pattern — Local Execution (Version 1 behavior)
 *
 * Executes the task JAR directly on this machine using ProcessBuilder.
 * This is the same logic from Version 1, encapsulated in a strategy.
 *
 * Progress tracking (Observer Pattern):
 * Local tasks emit three progress milestones:
 * 0% → starting
 * 50% → execution underway
 * 100%→ collecting output files
 */
public class LocalExecutionStrategy implements ExecutionStrategy {

    @Override
    public String getStrategyName() {
        return "LOCAL";
    }

    @Override
    public TaskResult execute(Task task, Path workDir, ProgressPublisher publisher) throws Exception {
        TaskRequest request = task.getRequest();

        // ── Phase 1: Saving files ──
        publisher.publish(5, "SETUP", "Saving task files to working directory");

        // ── Save JAR to working directory ──
        Path jarPath = workDir.resolve("task.jar");
        Files.write(jarPath, request.getJarBytes());

        // ── Save input files ──
        List<String> inputFileNames = new ArrayList<>();
        for (FileData fd : request.getInputFiles()) {
            Path filePath = workDir.resolve(fd.getFileName());
            Files.write(filePath, fd.getFileContent());
            inputFileNames.add(fd.getFileName());
        }

        // ── Build command ──
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add("task.jar");

        for (String opt : request.getOptions()) {
            command.add(opt);
        }
        for (String fileName : inputFileNames) {
            command.add(fileName);
        }

        System.out.println("  [LocalStrategy] Executing: " + String.join(" ", command));

        // ── Phase 2: Processing ──
        publisher.publish(30, "COMPUTING", "Launching task JAR process...");

        // ── Execute with ProcessBuilder ──
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        publisher.publish(50, "COMPUTING", "Task process running...");

        StringBuilder consoleOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                consoleOutput.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        // ── Phase 3: Collecting output ──
        publisher.publish(90, "COLLECTING", "Gathering output files...");

        // ── Collect output files ──
        List<FileData> outputFiles = new ArrayList<>();
        File[] filesInDir = workDir.toFile().listFiles();
        if (filesInDir != null) {
            for (File f : filesInDir) {
                String name = f.getName();
                if (!name.equals("task.jar") && !inputFileNames.contains(name)) {
                    byte[] content = Files.readAllBytes(f.toPath());
                    outputFiles.add(new FileData(name, content));
                }
            }
        }

        boolean success = (exitCode == 0);
        publisher.publish(100, "DONE",
                success ? "Task completed successfully." : "Task failed (exit code " + exitCode + ").");
        return new TaskResult(success, consoleOutput.toString(), outputFiles);
    }
}
