package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Singleton Pattern — ConfigManager
 *
 * Loads and provides access to external configuration from config.properties.
 * Only one instance exists throughout the application lifecycle.
 */
public class ConfigManager {

    private static ConfigManager instance;
    private Properties properties;
    private static final String DEFAULT_CONFIG_PATH = "config/config.properties";

    /**
     * Private constructor — prevents external instantiation (Singleton).
     */
    private ConfigManager() {
        properties = new Properties();
        loadConfig(DEFAULT_CONFIG_PATH);
    }

    /**
     * Returns the single instance of ConfigManager.
     * Thread-safe using synchronized.
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Loads configuration from the specified file path.
     */
    private void loadConfig(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            properties.load(fis);
            System.out.println("[ConfigManager] Configuration loaded from: " + path);
        } catch (IOException e) {
            System.err.println("[ConfigManager] Warning: Could not load config file: " + path);
            System.err.println("[ConfigManager] Using default values.");
            setDefaults();
        }
    }

    /**
     * Sets default values if config file is not found.
     */
    private void setDefaults() {
        properties.setProperty("server.port", "5000");
        properties.setProperty("server.maxClients", "50");
        properties.setProperty("slave.nodes", "localhost:6001,localhost:6002");
        properties.setProperty("slave.timeout", "10000");
        properties.setProperty("slave.retry", "2");
        properties.setProperty("thread.pool.size", "10");
        properties.setProperty("temp.dir", "tmp");
        properties.setProperty("data.dir", "data");
        properties.setProperty("logs.dir", "logs");
        properties.setProperty("execution.mode", "local");
    }

    // ── Getters ──

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "5000"));
    }

    public int getMaxClients() {
        return Integer.parseInt(properties.getProperty("server.maxClients", "50"));
    }

    public String[] getSlaveNodes() {
        String nodes = properties.getProperty("slave.nodes", "");
        if (nodes.isEmpty())
            return new String[0];
        return nodes.split(",");
    }

    public int getSlaveTimeout() {
        return Integer.parseInt(properties.getProperty("slave.timeout", "10000"));
    }

    public int getSlaveRetry() {
        return Integer.parseInt(properties.getProperty("slave.retry", "2"));
    }

    public int getThreadPoolSize() {
        return Integer.parseInt(properties.getProperty("thread.pool.size", "10"));
    }

    public String getTempDir() {
        return properties.getProperty("temp.dir", "tmp");
    }

    public String getDataDir() {
        return properties.getProperty("data.dir", "data");
    }

    public String getLogsDir() {
        return properties.getProperty("logs.dir", "logs");
    }

    public String getExecutionMode() {
        return properties.getProperty("execution.mode", "local");
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
