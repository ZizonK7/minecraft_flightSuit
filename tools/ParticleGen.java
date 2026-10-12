import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Particle sprites drawn in code (2026-10-12): the sleeper's "Z" (client/SleepZParticle).
 *
 * Usage: java tools/ParticleGen.java <assets/flightsuit/textures dir>   (writes particle/sleep_z.png only)
 */
public class ParticleGen {
    public static void main(String[] args) throws IOException {
        File dir = new File(args[0], "particle");
        dir.mkdirs();
        ImageIO.write(sleepZ(), "png", new File(dir, "sleep_z.png"));
    }

    /** A bold white "Z" with a soft blue rim, 16x16 on transparent. */
    static BufferedImage sleepZ() {
        int n = 16;
        boolean[][] z = new boolean[n][n];
        for (int x = 3; x <= 12; x++) {
            for (int t = 0; t < 2; t++) {
                z[3 + t][x] = true;
                z[11 + t][x] = true;
            }
        }
        for (int y = 5; y <= 10; y++) {
            // The diagonal from top right to bottom left, two pixels thick.
            int x = 12 - (y - 4) * 9 / 7;
            z[y][x] = true;
            z[y][Math.min(12, x + 1)] = true;
        }
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (z[y][x]) {
                    img.setRGB(x, y, 0xFFFFFFFF);
                    continue;
                }
                boolean rim = false;
                for (int dy = -1; dy <= 1 && !rim; dy++) {
                    for (int dx = -1; dx <= 1 && !rim; dx++) {
                        int yy = y + dy, xx = x + dx;
                        rim = yy >= 0 && yy < n && xx >= 0 && xx < n && z[yy][xx];
                    }
                }
                if (rim) {
                    img.setRGB(x, y, 0xC07FA8FF);
                }
            }
        }
        return img;
    }
}
