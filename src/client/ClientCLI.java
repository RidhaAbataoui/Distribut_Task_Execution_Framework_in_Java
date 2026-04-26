package client;

import common.FileData;
import common.ProgressUpdate;
import common.TaskFactory;
import common.TaskRequest;
import common.TaskResult;
import config.ConfigManager;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive Client — Connects ONCE to the Master, then loops for multiple
 * tasks.
 *
 * Workflow:
 * 1. Auto-connect to server (from config or defaults) — ONE connection for the
 * whole session
 * 2. Loop:
 * a. Choose mode: LOCAL or DISTRIBUTED
 * b. Fill in task details step by step
 * c. Send TaskRequest over the persistent socket
 * d. Receive progress updates + TaskResult over the same socket
 * e. Ask if you want to run another task
 * 3. User types 'n' → client closes the connection and exits
 *
 * Each terminal = one client session = one server-side ClientHandler thread
 * (one unique Client ID).
 * Multiple terminals = multiple parallel client sessions.
 */
public class ClientCLI {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        printBanner();

        // ── Auto-read server address from config (or use defaults) ──
        ConfigManager config = ConfigManager.getInstance();
        String host = config.getProperty("server.host", "localhost");
        int port = config.getServerPort();

        // Allow override from command line: ClientCLI <host> <port>
        if (args.length >= 2) {
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        } else if (args.length == 1) {
            host = args[0];
        }

        System.out.println("  Server : " + host + ":" + port);
        System.out.println("  Tip    : Open a new terminal for a parallel client session.");
        System.out.println();

        // ── Connect ONCE — this socket stays open for the whole session ──
        System.out.print("  Connecting to server... ");
        try (
                Socket socket = new Socket(host, port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            System.out.println("✓ Connected! (session open — multiple tasks supported)");
            System.out.println();

            // ── Task loop — reuse the same socket/streams ──
            while (true) {
                boolean submitted = showTaskMenu(out, in);

                if (submitted) {
                    System.out.println();
                    System.out.print("  Run another task? [Y/n]: ");
                    String again = scanner.nextLine().trim().toLowerCase();
                    if (again.equals("n") || again.equals("no")) {
                        System.out.println("\n  Goodbye! ✓");
                        break;
                    }
                    System.out.println();
                } else {
                    System.out.println("\n  Exiting.");
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("\n  [ERROR] Could not connect to Master at " + host + ":" + port);
            System.err.println("          Ensure the Master is running first.");
        } catch (Exception e) {
            System.err.println("\n  [ERROR] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Main task selection menu
    // ─────────────────────────────────────────────────────────────

    private static boolean showTaskMenu(ObjectOutputStream out, ObjectInputStream in) {
        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│   Select execution mode:                 │");
        System.out.println("│                                          │");
        System.out.println("│   [1] LOCAL       – single server        │");
        System.out.println("│   [2] DISTRIBUTED – master + slaves      │");
        System.out.println("│   [0] Exit                               │");
        System.out.println("└──────────────────────────────────────────┘");
        System.out.print("  Choice: ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("0") || choice.isEmpty())
            return false;

        try {
            TaskRequest request;
            if (choice.equals("2")) {
                request = buildDistributedRequest();
            } else {
                request = buildLocalRequest();
            }

            sendAndReceive(out, in, request);
            return true;

        } catch (Exception e) {
            System.err.println("\n  [ERROR] " + e.getMessage());
            e.printStackTrace();
            return true; // allow user to try again
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOCAL task builder
    // ─────────────────────────────────────────────────────────────

    private static TaskRequest buildLocalRequest() throws IOException {
        System.out.println();
        System.out.println("  ── LOCAL Task ──────────────────────────────");

        System.out.println("  Give me the jar file of the task:");
        String jarPath = askFilePath("  > ");
        byte[] jarBytes = Files.readAllBytes(Paths.get(jarPath));
        System.out.println("    ✓ " + jarPath + " loaded (" + humanSize(jarBytes.length) + ")");

        System.out.println();
        System.out.println("  Give the task options (space-separated, or press Enter for none):");
        System.out.print("  > ");
        String optLine = scanner.nextLine().trim();
        String[] options = optLine.isEmpty() ? new String[0] : optLine.split("\\s+");

        List<FileData> files = askForFiles();

        System.out.println("  ────────────────────────────────────────────");
        return TaskFactory.createLocalTask(jarBytes, options, files);
    }

    // ─────────────────────────────────────────────────────────────
    // DISTRIBUTED task builder
    // ─────────────────────────────────────────────────────────────

    private static TaskRequest buildDistributedRequest() throws IOException {
        System.out.println();
        System.out.println("  ── DISTRIBUTED Task ────────────────────────");

        // Master JAR
        System.out.println("  Give me the jar file of the Master task:");
        String masterPath = askFilePath("  > ");
        byte[] masterJarBytes = Files.readAllBytes(Paths.get(masterPath));
        System.out.println("    ✓ Master JAR: " + masterPath + " (" + humanSize(masterJarBytes.length) + ")");

        // Master options
        System.out.println();
        System.out.println("  Give the Master options (e.g. number of slaves: 2):");
        System.out.print("  > ");
        String masterOptLine = scanner.nextLine().trim();

        // Slave JAR
        System.out.println();
        System.out.println("  Give me the jar file of the Slave task:");
        String workerPath = askFilePath("  > ");
        byte[] workerJarBytes = Files.readAllBytes(Paths.get(workerPath));
        System.out.println("    ✓ Slave JAR: " + workerPath + " (" + humanSize(workerJarBytes.length) + ")");

        // Slave options
        System.out.println();
        System.out.println("  Give the slave options (e.g. -gray  |  -product  |  3):");
        System.out.print("  > ");
        String slaveOptLine = scanner.nextLine().trim();
        String[] slaveOptions = slaveOptLine.isEmpty() ? new String[0] : slaveOptLine.split("\\s+");

        // Priority
        System.out.println();
        System.out.println("  Task priority (0 = normal, higher = more urgent) [0]:");
        System.out.print("  > ");
        String priStr = scanner.nextLine().trim();
        int priority = 0;
        try {
            priority = Integer.parseInt(priStr);
        } catch (NumberFormatException ignored) {
        }

        // Input files
        List<FileData> files = askForFiles();

        // Pack options: MASTER_OPTS prefix + slave options
        List<String> allOptions = new ArrayList<>();
        if (!masterOptLine.isEmpty()) {
            allOptions.add("MASTER_OPTS:" + masterOptLine);
        }
        for (String s : slaveOptions)
            allOptions.add(s);

        System.out.println("  ────────────────────────────────────────────");
        return TaskFactory.createDistributedTask(
                masterJarBytes, workerJarBytes,
                allOptions.toArray(new String[0]),
                files, priority);
    }

    // ─────────────────────────────────────────────────────────────
    // Send TaskRequest and read back progress + result
    // Uses the SAME streams for the whole session — no reconnect!
    // ─────────────────────────────────────────────────────────────

    private static void sendAndReceive(ObjectOutputStream out, ObjectInputStream in,
            TaskRequest request) throws Exception {
        System.out.println();
        System.out.println("  Sending task to server...");

        // ObjectOutputStream.reset() clears the serialization cache so the server
        // always deserializes the new object (important when re-using a stream).
        out.reset();
        out.writeObject(request);
        out.flush();

        System.out.println("  ✓ Task sent. Waiting for progress...");
        System.out.println();

        TaskResult result = null;

        // ── Read mixed-type stream: ProgressUpdate* then TaskResult ──
        while (true) {
            Object obj = in.readObject();

            if (obj instanceof ProgressUpdate) {
                renderProgress((ProgressUpdate) obj);

            } else if (obj instanceof TaskResult) {
                result = (TaskResult) obj;
                break;
            }
        }

        System.out.println(); // newline after progress bar

        if (result != null) {
            printResult(result);
        }
    }

    /** Renders an animated progress bar on the current line using \r. */
    private static final int CLI_BAR_WIDTH = 30;

    private static void renderProgress(ProgressUpdate update) {
        int filled = (int) Math.round(update.getPercent() / 100.0 * CLI_BAR_WIDTH);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < CLI_BAR_WIDTH; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        String line = String.format("\r  [%-10s] [%s] %3d%%  %-40s",
                update.getPhase(),
                bar.toString(),
                update.getPercent(),
                update.getMessage());
        System.out.print(line);
        System.out.flush();
        if (update.getPercent() >= 100) {
            System.out.println();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private static String askFilePath(String prompt) {
        while (true) {
            System.out.print(prompt);
            String path = scanner.nextLine().trim();
            if (Files.exists(Paths.get(path)))
                return path;
            System.out.println("    ✗ File not found: \"" + path + "\" — please try again.");
        }
    }

    private static List<FileData> askForFiles() throws IOException {
        System.out.println();
        System.out.println("  Give me the list of the files");
        System.out.println("  (one path per line — press Enter on empty line when done):");

        List<FileData> files = new ArrayList<>();
        while (true) {
            System.out.print("  > ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty())
                break;

            Path p = Paths.get(line);
            if (!Files.exists(p)) {
                System.out.println("    ✗ Not found: " + line);
                continue;
            }
            byte[] content = Files.readAllBytes(p);
            files.add(new FileData(p.getFileName().toString(), content));
            System.out.println("    ✓ " + p.getFileName() + " (" + humanSize(content.length) + ")");
        }

        if (files.isEmpty())
            System.out.println("  (no input files)");
        return files;
    }

    private static void printResult(TaskResult result) {
        if (result.isSuccess()) {
            System.out.println("  ╔══════════════════════════════════════╗");
            System.out.println("  ║   ✓  Task completed SUCCESSFULLY     ║");
            System.out.println("  ╚══════════════════════════════════════╝");
        } else {
            System.out.println("  ╔══════════════════════════════════════╗");
            System.out.println("  ║   ✗  Task FAILED                     ║");
            System.out.println("  ╚══════════════════════════════════════╝");
        }

        String console = result.getConsoleOutput();
        if (console != null && !console.isEmpty()) {
            System.out.println();
            System.out.println("  [Output]:");
            System.out.println("  ─────────────────────────────────────");
            for (String line : console.split("\n")) {
                System.out.println("  " + line);
            }
            System.out.println("  ─────────────────────────────────────");
        }

        if (result.getOutputFiles() != null && !result.getOutputFiles().isEmpty()) {
            try {
                Path outputDir = Paths.get("results");
                Files.createDirectories(outputDir);
                System.out.println();
                System.out.println("  [Result files saved to results/]:");
                for (FileData fd : result.getOutputFiles()) {
                    Path out = outputDir.resolve(fd.getFileName());
                    Files.write(out, fd.getFileContent());
                    System.out.println("    → " + out + "  (" + humanSize(fd.getFileContent().length) + ")");
                }
            } catch (IOException e) {
                System.err.println("  [WARN] Could not save files: " + e.getMessage());
            }
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════╗");
        System.out.println("  ║  Distributed Task Execution — Interactive Client v2  ║");
        System.out.println("  ║  One connection per session → multiple tasks         ║");
        System.out.println("  ╚══════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
