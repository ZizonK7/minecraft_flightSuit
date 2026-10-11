import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Front and back of 64x64 player skins side by side, 4x, for a look before they go in (M18: the suit skins drawn
 * by SuitSkinGen - DbzSkinGen has the same for the Dragon Ball cast).
 *
 * Usage: java tools/SkinPreview.java <out.png> <skin.png>...
 */
public class SkinPreview {
    // Base box, overlay box: {u, v} of the front face; the back face sits 16 right of the front for the head/body
    // (8 wide + 8 side) and 8 right for limbs (4 wide + 4 side). Rows: head, body, right arm, left arm, right leg, left leg.
    private static final int[][] BASE = {{8, 8, 8, 8}, {20, 20, 8, 12}, {44, 20, 4, 12}, {36, 52, 4, 12}, {4, 20, 4, 12}, {20, 52, 4, 12}};
    private static final int[][] OVER = {{40, 8, 8, 8}, {20, 36, 8, 12}, {44, 36, 4, 12}, {52, 52, 4, 12}, {4, 36, 4, 12}, {4, 52, 4, 12}};
    private static final int[][] AT = {{4, 0}, {4, 8}, {0, 8}, {12, 8}, {4, 20}, {8, 20}};

    public static void main(String[] args) throws IOException {
        int scale = 4;
        int cell = 18;
        int n = args.length - 1;
        BufferedImage sheet = new BufferedImage(n * cell * 2 * scale, 34 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x2A2E33));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < n; i++) {
            BufferedImage skin = ImageIO.read(new File(args[i + 1]));
            for (int view = 0; view < 2; view++) {
                g.drawImage(flat(skin, view == 1), i * cell * 2 * scale + view * cell * scale + scale, scale, 16 * scale, 32 * scale, null);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }

    private static BufferedImage flat(BufferedImage s, boolean back) {
        BufferedImage img = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        for (int[][] layer : new int[][][]{BASE, OVER}) {
            for (int k = 0; k < layer.length; k++) {
                int[] r = layer[k];
                int u = back ? r[0] + r[2] + (k == 0 || k == 1 ? 8 : 4) : r[0];
                int x0 = AT[k][0];
                if (back && k >= 2) {
                    x0 = AT[k % 2 == 0 ? k + 1 : k - 1][0];
                }
                for (int j = 0; j < r[3]; j++) {
                    for (int i = 0; i < r[2]; i++) {
                        int argb = s.getRGB(u + i, r[1] + j);
                        if ((argb >>> 24) != 0) {
                            img.setRGB(x0 + i, AT[k][1] + j, argb);
                        }
                    }
                }
            }
        }
        return img;
    }
}
