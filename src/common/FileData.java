package common;

import java.io.Serializable;

/**
 * Holds the name and content of a file to be transferred over the network.
 * Used for both input files (client → server) and result files (server → client).
 */
public class FileData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileName;
    private byte[] fileContent;

    public FileData(String fileName, byte[] fileContent) {
        this.fileName = fileName;
        this.fileContent = fileContent;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getFileContent() {
        return fileContent;
    }
}
