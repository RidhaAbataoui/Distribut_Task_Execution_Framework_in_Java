package client;

import common.FileData;
import common.ProgressUpdate;
import common.TaskFactory;
import common.TaskRequest;
import common.TaskResult;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Client application — Version 2 with Progress Tracking
 *
 * Supports two modes:
 *
 * LOCAL mode (V1 compatible):
 * java client.Client <host> <port> local <jar> [options...] -- <files...>
 *
 * DISTRIBUTED mode (V2):
 * java client.Client <host> <port> distributed <masterJar> <workerJar>
 * [options...] -- <files...>
 *
 * Examples:
 * java client.Client localhost 5000 local tasks/Encrypt.jar 3 --
 * tasks/message.txt
 * java client.Client localhost 5000 distributed tasks/MatrixMaster.jar
 * tasks/MatrixWorker.jar -product -- tasks/A.txt tasks/B.txt
 *
 * ── Progress Tracking (Observer Pattern) ──
 * While the Master is executing the task, it sends ProgressUpdate objects
 * through the socket stream. The Client reads each object:
 * - If it is a ProgressUpdate → display bar, keep reading
 * - If it is a TaskResult → task is done, display result
 *
 * This loop replaces the old single-shot readObject() call.
 */
public class Client {

    /** Width of the animated progress bar in the terminal. */
    private static final int BAR_WIDTH = 30;

    public static void main(String[] args) {
        if (args.length < 4) {
            printUsage();
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String mode = args[2]; // "local" or "distributed"

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   Distributed Task Execution Client v2  (Progress Tracking) ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        try {
            TaskRequest request;

            if ("distributed".equalsIgnoreCase(mode)) {
                request = buildDistributedRequest(args);
            } else {
                request = buildLocalRequest(args);
            }

            System.out.println("[Client] Connecting to " + host + ":" + port + "...");
            System.out.println("[Client] Mode: " + request.getExecutionMode().toUpperCase());

            // ── Connect and send ──
            Socket socket = new Socket(host, port);
            System.out.println("[Client] Connected!");

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(request);
            out.flush();
            System.out.println("[Client] Task sent — waiting for progress updates...\n");

            // ── Receive objects from the stream ──
            // The stream contains N ProgressUpdate objects followed by one TaskResult.
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            TaskResult result = null;

            while (true) {
                Object obj = in.readObject();

                if (obj instanceof ProgressUpdate) {
                    // Display the progress bar for each update
                    renderProgress((ProgressUpdate) obj);

                } else if (obj instanceof TaskResult) {
                    // Final result received — exit the loop
                    result = (TaskResult) obj;
                    break;
                }
            }

            // Ensure progress bar ends on 100% visually
            System.out.println(); // newline after the last progress bar

            System.out.println();
            System.out.println("═══════════════════════════════════════════");
            System.out.println(result != null && result.isSuccess()
                    ? "  ✓ Task completed SUCCESSFULLY"
                    : "  ✗ Task FAILED");
            System.out.println("═══════════════════════════════════════════");

            if (result != null && result.getConsoleOutput() != null
                    && !result.getConsoleOutput().isEmpty()) {
                System.out.println("\n[Console Output]:");
                System.out.println(result.getConsoleOutput());
            }

            // ── Save output files ──
            Path outputDir = Paths.get("results");
            Files.createDirectories(outputDir);

            if (result != null && result.getOutputFiles() != null
                    && !result.getOutputFiles().isEmpty()) {
                System.out.println("[Client] Saving " + result.getOutputFiles().size() + " output file(s):");
                for (FileData fd : result.getOutputFiles()) {
                    Path outputPath = outputDir.resolve(fd.getFileName());
                    Files.write(outputPath, fd.getFileContent());
                    System.out.println("  → " + outputPath + " (" + fd.getFileContent().length + " bytes)");
                }
            } else {
                System.out.println("[Client] No output files received.");
            }

            in.close();
            out.close();
            socket.close();
            System.out.println("\n[Client] Done.");

        } catch (Exception e) {
            System.err.println("[Client] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Renders a progress bar to the console for one ProgressUpdate.
     *
     * Example output:
     * [COMPUTING ] [████████████░░░░░░░░░░░░░░░░░░] 40% Distributing chunks...
     */
    private static void renderProgress(ProgressUpdate update) {
        int filled = (int) Math.round(update.getPercent() / 100.0 * BAR_WIDTH);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_WIDTH; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        // \r rewrites the current line for animation effect;
        // we also pad with spaces to overwrite any leftover chars from a longer line.
        String line = String.format("\r  [%-10s] [%s] %3d%%  %-40s",
                update.getPhase(),
                bar.toString(),
                update.getPercent(),
                update.getMessage());
        System.out.print(line);
        System.out.flush();

        // When done, add a newline so the final 100% line is preserved
        if (update.getPercent() >= 100) {
            System.out.println();
        }
    }

    /**
     * Builds a LOCAL TaskRequest (V1 compatible).
     * Format: ... local <jar> [options...] -- <files...>
     */
    private static TaskRequest buildLocalRequest(String[] args) throws IOException {
        String jarPath = args[3];

        List<String> options = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        boolean parsingFiles = false;

        for (int i = 4; i < args.length; i++) {
            if (args[i].equals("--")) {
                parsingFiles = true;
                continue;
            }
            if (parsingFiles)
                filePaths.add(args[i]);
            else
                options.add(args[i]);
        }

        byte[] jarBytes = Files.readAllBytes(Paths.get(jarPath));
        System.out.println("[Client] JAR: " + jarPath + " (" + jarBytes.length + " bytes)");

        List<FileData> inputFiles = readFiles(filePaths);

        // Factory Method Pattern: centralised creation with validation
        return TaskFactory.createLocalTask(jarBytes, options.toArray(new String[0]), inputFiles);
    }

    /**
     * Builds a DISTRIBUTED TaskRequest (V2).
     * Format: ... distributed <masterJar> <workerJar> [options...] -- <files...>
     */
    private static TaskRequest buildDistributedRequest(String[] args) throws IOException {
        String masterJarPath = args[3];
        String workerJarPath = args[4];

        List<String> options = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();
        boolean parsingFiles = false;

        for (int i = 5; i < args.length; i++) {
            if (args[i].equals("--")) {
                parsingFiles = true;
                continue;
            }
            if (parsingFiles)
                filePaths.add(args[i]);
            else
                options.add(args[i]);
        }

        byte[] masterJarBytes = Files.readAllBytes(Paths.get(masterJarPath));
        byte[] workerJarBytes = Files.readAllBytes(Paths.get(workerJarPath));
        System.out.println("[Client] Master JAR: " + masterJarPath + " (" + masterJarBytes.length + " bytes)");
        System.out.println("[Client] Worker JAR: " + workerJarPath + " (" + workerJarBytes.length + " bytes)");

        List<FileData> inputFiles = readFiles(filePaths);

        // Factory Method Pattern: centralised creation with validation
        int priority = 0; // default priority
        return TaskFactory.createDistributedTask(
                masterJarBytes, workerJarBytes,
                options.toArray(new String[0]), inputFiles, priority);
    }

    /**
     * Reads files from disk into FileData objects.
     */
    private static List<FileData> readFiles(List<String> filePaths) throws IOException {
        List<FileData> files = new ArrayList<>();
        for (String filePath : filePaths) {
            Path p = Paths.get(filePath);
            byte[] content = Files.readAllBytes(p);
            files.add(new FileData(p.getFileName().toString(), content));
            System.out.println("[Client] Input file: " + p.getFileName() + " (" + content.length + " bytes)");
        }
        return files;
    }

    private static void printUsage() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   Distributed Task Execution Client v2  (Progress Tracking) ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Usage:");
        System.out.println();
        System.out.println("  LOCAL mode (V1):");
        System.out.println("    java client.Client <host> <port> local <jar> [options...] -- <files...>");
        System.out.println();
        System.out.println("  DISTRIBUTED mode (V2):");
        System.out.println(
                "    java client.Client <host> <port> distributed <masterJar> <workerJar> [options...] -- <files...>");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java client.Client localhost 5000 local tasks/Encrypt.jar 3 -- tasks/message.txt");
        System.out.println(
                "  java client.Client localhost 5000 distributed tasks/MatrixMaster.jar tasks/MatrixWorker.jar -product -- tasks/A.txt tasks/B.txt");
    }
}
