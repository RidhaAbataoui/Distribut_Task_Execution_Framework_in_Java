package strategy;

import common.*;
import master.SlaveManager;
import observer.ProgressPublisher;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Strategy Pattern — Distributed Execution (Version 2)
 *
 * Workflow:
 * 1. Parse options: MASTER_OPTS separated from slave options
 * 2. Run master JAR with "split <numSlaves> <files>" → produces chunks
 * 3. Send worker JAR + each chunk to a slave (parallel)
 * 4. Collect results in order
 * 5. Run master JAR with "merge <result files>" → final output
 *
 * Observer Pattern — Progress Tracking:
 * Each phase publishes progress to the ProgressPublisher.
 * The publisher notifies all attached observers (console, log, socket).
 * This class does NOT know who is listening — loose coupling by design.
 *
 * Progress breakdown:
 * 0% → starting
 * 5% → files saved
 * 10% → split phase starting
 * 20% → split done
 * 20%–80% → each slave completing (proportional)
 * 85% → merge phase starting
 * 100% → done
 *
 * Options format from CLI:
 * options[0] = "MASTER_OPTS:<master options string>" (from interactive CLI)
 * options[1..] = slave options
 *
 * For backward compatible API:
 * If no MASTER_OPTS prefix found, all options are passed as slave options,
 * and slave count comes from slaveManager.getAvailableSlaveCount().
 */
public class DistributedExecutionStrategy implements ExecutionStrategy {

    private final SlaveManager slaveManager;

    public DistributedExecutionStrategy(SlaveManager slaveManager) {
        this.slaveManager = slaveManager;
    }

    @Override
    public String getStrategyName() {
        return "DISTRIBUTED";
    }

    @Override
    public TaskResult execute(Task task, Path workDir, ProgressPublisher publisher) throws Exception {
        TaskRequest request = task.getRequest();
        int totalSlaves = slaveManager.getAvailableSlaveCount();

        publisher.publish(0, "INIT", "Initializing distributed task...");

        if (totalSlaves == 0) {
            publisher.publish(100, "FAILED", "No slaves available.");
            return new TaskResult(false, "No slaves available for distributed execution.", new ArrayList<>());
        }

        // ── Parse options: separate master options from slave options ──
        String masterOptsStr = "";
        List<String> slaveOptionsList = new ArrayList<>();

        for (String opt : request.getOptions()) {
            if (opt.startsWith("MASTER_OPTS:")) {
                masterOptsStr = opt.substring("MASTER_OPTS:".length()).trim();
            } else {
                slaveOptionsList.add(opt);
            }
        }

        // Determine how many slaves to use
        int slaveCount = totalSlaves;
        String[] masterOptParts = masterOptsStr.isEmpty() ? new String[0] : masterOptsStr.split("\\s+");
        if (masterOptParts.length > 0) {
            try {
                slaveCount = Math.min(Integer.parseInt(masterOptParts[0].trim()), totalSlaves);
            } catch (NumberFormatException e) {
                // Not a number — keep default
            }
        }

        System.out
                .println("  [DistributedStrategy] Using " + slaveCount + " slave(s) of " + totalSlaves + " available");
        System.out.println("  [DistributedStrategy] Slave options: " + slaveOptionsList);

        // ── Step 1: Save master JAR, worker JAR, input files ──
        publisher.publish(5, "SETUP", "Saving JARs and input files to working directory...");

        Path masterJarPath = workDir.resolve("master.jar");
        Files.write(masterJarPath, request.getMasterJarBytes());

        Path workerJarPath = workDir.resolve("worker.jar");
        Files.write(workerJarPath, request.getWorkerJarBytes());

        List<String> inputFileNames = new ArrayList<>();
        for (FileData fd : request.getInputFiles()) {
            Path filePath = workDir.resolve(fd.getFileName());
            Files.write(filePath, fd.getFileContent());
            inputFileNames.add(fd.getFileName());
        }

        // ── Step 2: Run master JAR to SPLIT input ──
        publisher.publish(10, "SPLIT", "Running master JAR to split input into " + slaveCount + " chunk(s)...");
        System.out.println("  [DistributedStrategy] Phase 1: Splitting input...");

        List<String> splitCommand = new ArrayList<>();
        splitCommand.add("java");
        splitCommand.add("-jar");
        splitCommand.add("master.jar");
        splitCommand.add("split");
        splitCommand.add(String.valueOf(slaveCount));

        // Forward operation flags (e.g. -sum, -product)
        for (String opt : slaveOptionsList) {
            if (opt.startsWith("-")) {
                splitCommand.add(opt);
                break;
            }
        }

        for (String f : inputFileNames) {
            splitCommand.add(f);
        }

        String splitOutput = runProcess(splitCommand, workDir);
        System.out.println("  [DistributedStrategy] Split output:\n" + splitOutput.trim());

        publisher.publish(20, "SPLIT", "Split complete — chunks ready for distribution.");

        // ── Step 3: Collect chunk files ──
        List<FileData> chunks = new ArrayList<>();
        File[] allFilesInDir = workDir.toFile().listFiles();
        if (allFilesInDir != null) {
            java.util.Arrays.sort(allFilesInDir, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : allFilesInDir) {
                if (f.getName().startsWith("chunk_")) {
                    byte[] content = Files.readAllBytes(f.toPath());
                    chunks.add(new FileData(f.getName(), content));
                }
            }
        }

        if (chunks.isEmpty()) {
            publisher.publish(100, "FAILED", "Master JAR produced no chunks.");
            return new TaskResult(false,
                    "Master JAR 'split' produced no chunk files.\n" + splitOutput, new ArrayList<>());
        }

        System.out.println("  [DistributedStrategy] Created " + chunks.size() + " chunk(s)");

        // ── Step 4: Distribute chunks to slaves in parallel ──
        System.out.println("  [DistributedStrategy] Phase 2: Distributing to slaves...");
        publisher.publish(25, "COMPUTING", "Distributing " + chunks.size() + " chunk(s) to slaves...");

        String[] slaveOptions = slaveOptionsList.toArray(new String[0]);

        // ── Progress counter: each completed slave advances the bar 20%→80% ──
        final int numChunks = chunks.size();
        final AtomicInteger completedChunks = new AtomicInteger(0);

        List<TaskResult> slaveResults = distributeWithProgress(
                request.getWorkerJarBytes(), slaveOptions, chunks,
                publisher, completedChunks, numChunks);

        // ── Step 5: Save slave results for merging ──
        publisher.publish(85, "MERGE", "All slaves done. Merging results...");
        System.out.println("  [DistributedStrategy] Phase 3: Merging results...");

        List<String> resultFileNames = new ArrayList<>();
        int resultIndex = 0;
        for (TaskResult sr : slaveResults) {
            if (sr.isSuccess() && sr.getOutputFiles() != null) {
                for (FileData fd : sr.getOutputFiles()) {
                    String ext = getExtension(fd.getFileName());
                    String resultName = "result_" + resultIndex + (ext.isEmpty() ? ".txt" : ext);
                    Files.write(workDir.resolve(resultName), fd.getFileContent());
                    resultFileNames.add(resultName);
                    resultIndex++;
                }
            }
        }

        // ── Step 6: Run master JAR to MERGE results ──
        List<String> mergeCommand = new ArrayList<>();
        mergeCommand.add("java");
        mergeCommand.add("-jar");
        mergeCommand.add("master.jar");
        mergeCommand.add("merge");
        for (String f : resultFileNames) {
            mergeCommand.add(f);
        }

        String mergeOutput = runProcess(mergeCommand, workDir);
        System.out.println("  [DistributedStrategy] Merge output:\n" + mergeOutput.trim());

        publisher.publish(95, "MERGE", "Merge complete. Collecting output files...");

        // ── Step 7: Collect final output files ──
        List<String> knownNonOutput = new ArrayList<>(inputFileNames);
        knownNonOutput.addAll(resultFileNames);

        List<FileData> outputFiles = new ArrayList<>();
        File[] finalFiles = workDir.toFile().listFiles();
        if (finalFiles != null) {
            for (File f : finalFiles) {
                String name = f.getName();
                if (name.equals("master.jar") || name.equals("worker.jar")
                        || name.startsWith("chunk_")
                        || knownNonOutput.contains(name)) {
                    continue;
                }
                byte[] content = Files.readAllBytes(f.toPath());
                outputFiles.add(new FileData(name, content));
                System.out.println("  [DistributedStrategy] Output file: " + name
                        + " (" + content.length + " bytes)");
            }
        }

        // ── Build console summary ──
        StringBuilder fullOutput = new StringBuilder();
        fullOutput.append("=== SPLIT PHASE ===\n").append(splitOutput);
        fullOutput.append("=== SLAVE RESULTS ===\n");
        for (int i = 0; i < slaveResults.size(); i++) {
            fullOutput.append("Slave ").append(i).append(": ")
                    .append(slaveResults.get(i).isSuccess() ? "OK ✓" : "FAILED ✗").append("\n");
            String co = slaveResults.get(i).getConsoleOutput();
            if (co != null && !co.isEmpty()) {
                fullOutput.append(co);
            }
        }
        fullOutput.append("=== MERGE PHASE ===\n").append(mergeOutput);

        publisher.publish(100, "DONE", "Task completed successfully.");
        return new TaskResult(true, fullOutput.toString(), outputFiles);
    }

    /**
     * Distributes chunks to slaves in parallel and tracks per-chunk progress.
     * Progress advances from 25% to 80% proportionally as each slave finishes.
     */
    private List<TaskResult> distributeWithProgress(
            byte[] workerJarBytes,
            String[] options,
            List<FileData> chunks,
            ProgressPublisher publisher,
            AtomicInteger completedChunks,
            int numChunks) {

        List<SlaveManager.SlaveInfo> pool = slaveManager.getSlaves();
        int numSlaves = Math.min(pool.size(), numChunks);

        if (numSlaves == 0) {
            List<TaskResult> failed = new ArrayList<>();
            for (int i = 0; i < numChunks; i++) {
                failed.add(new TaskResult(false, "No slaves available.", new ArrayList<>()));
            }
            return failed;
        }

        final TaskResult[] resultArray = new TaskResult[numChunks];
        List<Thread> threads = new ArrayList<>();

        int timeoutMs = config.ConfigManager.getInstance().getSlaveTimeout();

        for (int i = 0; i < numChunks; i++) {
            SlaveManager.SlaveInfo slave = pool.get(i % numSlaves);
            FileData chunk = chunks.get(i);
            final int idx = i;

            Thread t = new Thread(() -> {
                publisher.publish(
                        25 + (int) ((double) idx / numChunks * 10),
                        "COMPUTING",
                        "Sending chunk " + (idx + 1) + "/" + numChunks + " to slave " + slave);

                TaskResult result = slaveManager.sendChunkToSlave(slave, workerJarBytes, options, chunk, idx);
                resultArray[idx] = result;

                int done = completedChunks.incrementAndGet();
                int pct = 25 + (int) ((double) done / numChunks * 55); // 25%→80%
                String statusMsg = "Slave " + slave + " finished chunk " + (idx + 1) + "/" + numChunks
                        + (result.isSuccess() ? " ✓" : " ✗");
                publisher.publish(pct, "COMPUTING", statusMsg);

                if (!result.isSuccess()) {
                    slaveManager.unregisterSlave(slave.getHost(), slave.getPort());
                }
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            try {
                t.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        List<TaskResult> results = new ArrayList<>();
        for (int i = 0; i < numChunks; i++) {
            results.add(resultArray[i] != null
                    ? resultArray[i]
                    : new TaskResult(false, "Slave timed out.", new ArrayList<>()));
        }
        return results;
    }

    /**
     * Runs an external process and returns its merged stdout+stderr output.
     */
    private String runProcess(List<String> command, Path workDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();
        return output.toString();
    }

    /**
     * Returns the file extension including the dot (e.g. ".png"), or "" if none.
     */
    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }
}
