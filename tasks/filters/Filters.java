import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Image Filter Task
 *
 * Usage:
 * java -jar Filters.jar -gray myimage.png
 * java -jar Filters.jar -invert myimage.png
 * java -jar Filters.jar -sepia myimage.png
 *
 * Output: Result.png
 */
public class Filters {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar Filters.jar <-gray|-invert|-sepia> <image_file>");
            System.exit(1);
        }

        String filter = args[0];
        String inputFile = args[1];

        try {
            System.out.println("Loading image: " + inputFile);
            BufferedImage image = ImageIO.read(new File(inputFile));

            if (image == null) {
                System.err.println("Error: Could not read image file: " + inputFile);
                System.exit(1);
            }

            BufferedImage result;

            switch (filter) {
                case "-gray":
                    System.out.println("Applying grayscale filter...");
                    result = applyGrayscale(image);
                    break;
                case "-invert":
                    System.out.println("Applying invert filter...");
                    result = applyInvert(image);
                    break;
                case "-sepia":
                    System.out.println("Applying sepia filter...");
                    result = applySepia(image);
                    break;
                default:
                    System.err.println("Unknown filter: " + filter);
                    System.exit(1);
                    return;
            }

            ImageIO.write(result, "png", new File("Result.png"));
            System.out.println("Result written to Result.png");
            System.out.println("Image size: " + result.getWidth() + "x" + result.getHeight());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
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
                Color grayColor = new Color(gray, gray, gray);
                result.setRGB(x, y, grayColor.getRGB());
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
                Color inverted = new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue());
                result.setRGB(x, y, inverted.getRGB());
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
