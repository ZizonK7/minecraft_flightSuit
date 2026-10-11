import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The golden Shenron for Trunks' Dragon Fist (M18 feedback): shenron.png with every green texel turned gold (hue to
 * amber, keeping its light and shade), the reds (eyes) and the pale belly left alone, saved as shenron_gold.png.
 *
 * Usage: java tools/GoldDragon.java <repo root>
 */
public class GoldDragon {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".", "src/main/resources/assets/flightsuit/textures/entity/dbz");
        BufferedImage in = ImageIO.read(new File(dir, "shenron.png"));
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                int argb = in.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                float[] hsb = Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
                float hue = hsb[0] * 360.0F;
                int rgb;
                if (hue >= 60.0F && hue <= 180.0F && hsb[1] > 0.15F) {
                    // Greens (scales, belly plates, horns' shade): amber-gold, brighter overall.
                    float bright = Math.min(1.0F, hsb[2] * 1.15F + 0.1F);
                    rgb = Color.HSBtoRGB(44.0F / 360.0F, Math.min(0.95F, hsb[1] * 0.9F + 0.2F), bright);
                } else if (hsb[1] < 0.15F) {
                    // Greys and whites (belly, teeth): warm cream.
                    rgb = Color.HSBtoRGB(45.0F / 360.0F, 0.25F, Math.min(1.0F, hsb[2] * 1.05F + 0.05F));
                } else {
                    rgb = argb;
                }
                out.setRGB(x, y, (argb & 0xFF000000) | (rgb & 0x00FFFFFF));
            }
        }
        ImageIO.write(out, "png", new File(dir, "shenron_gold.png"));
        System.out.println("shenron_gold.png " + out.getWidth() + "x" + out.getHeight());
    }
}
