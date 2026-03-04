package slave;

import common.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles a single task from the Master on the slave side.
 *
 * Workflow:
 * 1. Receive TaskRequest (worker JAR + chunk files)
 * 2. Save to temp directory
 * 3. Execute via ProcessBuilder
 * 4. Collect output files
 * 5. Send TaskResult back to Master
 */
public class SlaveHandler extends Thread {

    private final Socket socket;
    private final int taskId;

    public SlaveHandler(Socket socket, int taskId) {
        this.socket = socket;
        this.taskId = taskId;
    }

    @Override
    public void run() {
        log("Processing task...");

        try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            // ── Step 1: Receive TaskRequest ──
            TaskRequest request = (TaskRequest) in.readObject();
            log("Received JAR (" + request.getJarBytes().length + " bytes) + "
                    + request.getInputFiles().size() + " file(s)");

            // ── Step 2: Save files to temp directory ──
            Path workDir = Files.createTempDirectory("slave_task_" + taskId + "_");
            log("Working directory: " + workDir);

            Path jarPath = workDir.resolve("task.jar");
            Files.write(jarPath, request.getJarBytes());

            List<String> inputFileNames = new ArrayList<>();
            for (FileData fd : request.getInputFiles()) {
                Path filePath = workDir.resolve(fd.getFileName());
                Files.write(filePath, fd.getFileContent());
                inputFileNames.add(fd.getFileName());
            }

            // ── Step 3: Build and execute command ──
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

            log("Executing: " + String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder consoleOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consoleOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log("Process exited with code: " + exitCode);

            // ── Step 4: Collect output files ──
            List<FileData> outputFiles = new ArrayList<>();
            File[] filesInDir = workDir.toFile().listFiles();
            if (filesInDir != null) {
                for (File f : filesInDir) {
                    String name = f.getName();
                    if (!name.equals("task.jar") && !inputFileNames.contains(name)) {
                        byte[] content = Files.readAllBytes(f.toPath());
                        outputFiles.add(new FileData(name, content));
                        log("Output file: " + name + " (" + content.length + " bytes)");
                    }
                }
            }

            // ── Step 5: Send result back to Master ──
            boolean success = (exitCode == 0);
            TaskResult result = new TaskResult(success, consoleOutput.toString(), outputFiles);
            out.writeObject(result);
            out.flush();

            log("Result sent. Task " + (success ? "SUCCEEDED ✓" : "FAILED ✗"));

            // Cleanup
            cleanupDir(workDir);

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
            log("Connection closed.");
        }
    }

    private void cleanupDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            /* ignore */ }
                    });
        } catch (IOException e) {
            // ignore
        }
    }

    private void log(String message) {
        System.out.println("[Slave Task #" + taskId + "] " + message);
    }
}
