package common;

import java.io.Serializable;

/**
 * Sent by a Slave to the Master on startup to register itself.
 * The Master adds this slave to its dynamic pool.
 */
public class SlaveRegistration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;

    public SlaveRegistration(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
