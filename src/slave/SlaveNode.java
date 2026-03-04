package slave;

import common.SlaveRegistration;
import config.ConfigManager;

import java.io.*;
import java.net.*;

/**
 * Slave Node — Dynamic Version
 *
 * On startup:
 * 1. Binds to its own port (task listener)
 * 2. Connects to Master's registration port and self-registers
 * 3. Waits for tasks from Master
 *
 * Usage:
 * java -cp build slave.SlaveNode <port>
 * java -cp build slave.SlaveNode 6001
 * java -cp build slave.SlaveNode 6002
 * java -cp build slave.SlaveNode 6003 ← just add more!
 */
public class SlaveNode {

    public static void main(String[] args) {
        ConfigManager config = ConfigManager.getInstance();

        // Slave's own listening port (from argument or default 6001)
        int slavePort = 6001;
        if (args.length > 0) {
            try {
                slavePort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[Slave] Invalid port: " + args[0] + " — using 6001");
            }
        }

        // Master's address (from config)
        String masterHost = config.getProperty("server.host", "localhost");
        int masterRegPort = config.getIntProperty("server.reg.port", 5001); // 5001

        // This slave's own host (what it advertises to master)
        String myHost = config.getProperty("slave.host", "localhost");

        printBanner(slavePort, masterHost, masterRegPort);

        // ── Step 1: Bind our task-listener socket FIRST ──
        try (ServerSocket taskSocket = new ServerSocket(slavePort)) {
            System.out.println("[Slave:" + slavePort + "] Task listener ready.");

            // ── Step 2: Register with Master ──
            register(myHost, slavePort, masterHost, masterRegPort);

            // ── Step 3: Accept tasks from Master (loop) ──
            System.out.println("[Slave:" + slavePort + "] Waiting for tasks...");
            int taskId = 0;
            while (true) {
                Socket connection = taskSocket.accept();
                taskId++;
                System.out.println("[Slave:" + slavePort + "] Task #" + taskId
                        + " received from " + connection.getInetAddress().getHostAddress());
                SlaveHandler handler = new SlaveHandler(connection, taskId);
                handler.start();
            }

        } catch (IOException e) {
            System.err.println("[Slave:" + slavePort + "] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a SlaveRegistration message to the Master's registration port.
     * Retries up to 5 times if the master isn't ready yet.
     */
    private static void register(String myHost, int myPort,
            String masterHost, int masterRegPort) {
        int maxRetries = 5;
        int delayMs = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("[Slave:" + myPort + "] Registering with Master at "
                        + masterHost + ":" + masterRegPort
                        + " (attempt " + attempt + ")...");

                Socket socket = new Socket(masterHost, masterRegPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                out.writeObject(new SlaveRegistration(myHost, myPort));
                out.flush();

                Object ack = in.readObject();
                in.close();
                out.close();
                socket.close();

                if ("REGISTERED".equals(ack)) {
                    System.out.println("[Slave:" + myPort + "] ✓ Successfully registered with Master!");
                    return;
                }

            } catch (Exception e) {
                System.err.println("[Slave:" + myPort + "] Registration attempt " + attempt
                        + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    System.out.println("[Slave:" + myPort + "] Retrying in " + (delayMs / 1000) + "s...");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }

        System.err.println("[Slave:" + myPort + "] ⚠ Could not register with Master. "
                + "Running anyway — will retry when Master becomes available.");
    }

    private static void printBanner(int slavePort, String masterHost, int masterRegPort) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Distributed Task Execution — Slave Node       ║");
        System.out.println("╠══════════════════════════════════════════════════╣");
        System.out.println("║  My port   : " + padRight(String.valueOf(slavePort), 36) + "║");
        System.out.println("║  Master    : " + padRight(masterHost + ":" + masterRegPort + " (reg)", 36) + "║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
