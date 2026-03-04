package master;

import common.*;
import config.ConfigManager;
import persistence.TaskPersistence;
import strategy.*;

import java.io.*;
import java.net.*;

/**
 * Master Node — Version 2 (Dynamic Slave Registration)
 *
 * Listens on TWO ports:
 * ► server.port (default 5000) — accepts CLIENT connections
 * ► server.reg.port (default 5001) — accepts SLAVE registrations
 *
 * Slaves connect to port 5001 on startup, register their address,
 * and are added to the live pool. No config needed on Master side.
 *
 * Clients connect to port 5000 and submit tasks normally.
 */
public class Master {

    public static void main(String[] args) {
        ConfigManager config = ConfigManager.getInstance();

        int clientPort = config.getServerPort(); // 5000
        int regPort = config.getIntProperty("server.reg.port", 5001); // 5001

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   Distributed Task Execution — Master Node v2       ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  Client port    : " + padRight(String.valueOf(clientPort), 34) + "║");
        System.out.println("║  Slave reg port : " + padRight(String.valueOf(regPort), 34) + "║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Waiting for slaves to connect on port " + regPort + "...");
        System.out.println("  Then start clients on port " + clientPort);
        System.out.println();

        // ── Shared components ──
        SlaveManager slaveManager = new SlaveManager();
        TaskScheduler scheduler = new TaskScheduler();
        TaskPersistence persistence = new TaskPersistence();
        ExecutionStrategy localStrat = new LocalExecutionStrategy();
        ExecutionStrategy distStrat = new DistributedExecutionStrategy(slaveManager);

        // ── Thread 1: Listen for SLAVE registrations (port 5001) ──
        Thread regThread = new Thread(() -> {
            try (ServerSocket regSocket = new ServerSocket(regPort)) {
                System.out.println("[Master] Slave registration listener started on port " + regPort);
                while (true) {
                    Socket slaveSocket = regSocket.accept();
                    Thread handler = new Thread(() -> handleSlaveRegistration(slaveSocket, slaveManager));
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (IOException e) {
                System.err.println("[Master] Registration listener error: " + e.getMessage());
            }
        });
        regThread.setDaemon(true);
        regThread.start();

        // ── Thread 2 (main): Listen for CLIENT connections (port 5000) ──
        try (ServerSocket clientSocket = new ServerSocket(clientPort)) {
            System.out.println("[Master] Client listener started on port " + clientPort);
            System.out.println("[Master] Ready. Waiting for connections...");

            int clientId = 0;
            while (true) {
                Socket socket = clientSocket.accept();
                clientId++;
                System.out.println("[Master] Client #" + clientId
                        + " connected from " + socket.getInetAddress().getHostAddress()
                        + "  (active slaves: " + slaveManager.getAvailableSlaveCount() + ")");

                ClientHandler handler = new ClientHandler(
                        socket, clientId,
                        localStrat, distStrat,
                        scheduler, persistence);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("[Master] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            persistence.saveSummary();
        }
    }

    /**
     * Handles one slave registration connection.
     * Reads a SlaveRegistration object and adds it to the pool.
     */
    private static void handleSlaveRegistration(Socket slaveSocket, SlaveManager slaveManager) {
        try (
                ObjectInputStream in = new ObjectInputStream(slaveSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(slaveSocket.getOutputStream())) {
            Object msg = in.readObject();
            if (msg instanceof SlaveRegistration) {
                SlaveRegistration reg = (SlaveRegistration) msg;
                slaveManager.registerSlave(reg.getHost(), reg.getPort());
                // ACK back to slave
                out.writeObject("REGISTERED");
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("[Master] Registration error: " + e.getMessage());
        } finally {
            try {
                slaveSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
