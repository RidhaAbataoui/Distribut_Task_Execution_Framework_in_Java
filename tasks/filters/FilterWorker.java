import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Filter Worker Task — Processes one image chunk for distributed filtering.
 *
 * Usage:
 * java -jar FilterWorker.jar <-gray|-invert|-sepia> chunk_N.png
 */
public class FilterWorker {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar FilterWorker.jar <-gray|-invert|-sepia> <chunk_file>");
            System.exit(1);
        }

        String filter = args[0];
        String chunkFile = args[1];

        try {
            System.out.println("[FilterWorker] Processing chunk: " + chunkFile);
            BufferedImage image = ImageIO.read(new File(chunkFile));

            if (image == null) {
                System.err.println("[FilterWorker] Error: Could not read chunk file: " + chunkFile);
                System.exit(1);
            }

            BufferedImage result;
            switch (filter) {
                case "-gray":
                    result = applyGrayscale(image);
                    break;
                case "-invert":
                    result = applyInvert(image);
                    break;
                case "-sepia":
                    result = applySepia(image);
                    break;
                default:
                    System.err.println("[FilterWorker] Unknown filter: " + filter);
                    System.exit(1);
                    return;
            }

            ImageIO.write(result, "png", new File("Result.png"));
            System.out.println("[FilterWorker] Chunk processed.");

        } catch (Exception e) {
            System.err.println("[FilterWorker] Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static BufferedImage applyGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
                result.setRGB(x, y, new Color(gray, gray, gray).getRGB());
            }
        }
        return result;
    }

    private static BufferedImage applyInvert(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y));
                result.setRGB(x, y, new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue()).getRGB());
            }
        }
        return result;
    }

    private static BufferedImage applySepia(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y));
                int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
                int newR = Math.min(255, (int) (0.393 * r + 0.769 * g + 0.189 * b));
                int newG = Math.min(255, (int) (0.349 * r + 0.686 * g + 0.168 * b));
                int newB = Math.min(255, (int) (0.272 * r + 0.534 * g + 0.131 * b));
                result.setRGB(x, y, new Color(newR, newG, newB).getRGB());
            }
        }
        return result;
    }
}
