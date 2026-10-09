import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Procedural placeholder textures for the M2 blocks and the EDITH glasses (no image tools on this machine,
 * so pixel art is drawn in code). Re-run after changing a palette:
 *
 *   java tools/TextureGen.java src/main/resources/assets/flightsuit/textures
 */
public class TextureGen {
    // Palette.
    static final int METAL_DARK = 0xFF2E3238, METAL = 0xFF454B53, METAL_LIGHT = 0xFF6B737D, EDGE = 0xFF1B1E22;
    static final int GOLD = 0xFFE0AE3A, GOLD_DARK = 0xFFA97E22;
    static final int RED = 0xFFB52A24;
    static final int CYAN = 0xFF5FE3FF, CYAN_DARK = 0xFF2A8FA8, CYAN_GLOW = 0xFFB8F4FF;
    static final int SOLAR = 0xFF1C2E5C, SOLAR_LINE = 0xFF3A5A9E, SOLAR_SHINE = 0xFF6F8FD0, SILVER = 0xFFB9C0C8;
    static final int ORANGE = 0xFFFF8A1E, ORANGE_HOT = 0xFFFFD25A;

    public static void main(String[] args) throws IOException {
        File root = new File(args[0]);
        File block = new File(root, "block");
        File item = new File(root, "item");
        File armor = new File(root, "models/armor");
        block.mkdirs();
        item.mkdirs();
        armor.mkdirs();

        write(stationTop(), new File(block, "suit_station_top.png"));
        write(stationSide(), new File(block, "suit_station_side.png"));
        write(plate(METAL_DARK), new File(block, "suit_station_bottom.png"));
        write(stationPlatformTop(), new File(block, "station_platform_top.png"));
        write(stationFrame(), new File(block, "station_frame.png"));
        write(solarTop(), new File(block, "solar_panel_top.png"));
        write(solarSide(), new File(block, "solar_panel_side.png"));
        write(generatorFront(false), new File(block, "generator_front.png"));
        write(generatorFront(true), new File(block, "generator_front_on.png"));
        write(generatorSide(), new File(block, "generator_side.png"));
        write(generatorTop(), new File(block, "generator_top.png"));
        write(batterySide(), new File(block, "battery_side.png"));
        write(batteryTop(), new File(block, "battery_top.png"));
        write(glassesArmor(), new File(armor, "edith_glasses.png"));
        write(glassesIcon(), new File(item, "edith_glasses.png"));

        File entity = new File(root, "entity");
        entity.mkdirs();
        write(cleanerDockTop(), new File(block, "cleaner_dock_top.png"));
        write(cleanerDockSide(), new File(block, "cleaner_dock_side.png"));
        write(cleanerRobotIcon(), new File(item, "cleaner_robot.png"));
        write(carCapsuleIcon(), new File(item, "hover_car_capsule.png"));
        write(stationArm(), new File(entity, "station_arm.png"));
        write(card(0xFFF6F4EE, GOLD, RED), new File(entity, "card_blanche.png"));
        write(card(0xFF1A1620, 0xFF8A4FD8, 0xFFF6F4EE), new File(entity, "card_noir.png"));
        write(masterSword(), new File(item, "master_sword.png"));
        write(hallSide(false), new File(block, "village_hall_side.png"));
        write(hallSide(true), new File(block, "village_hall_front.png"));
        write(hallTop(), new File(block, "village_hall_top.png"));
        write(hallStoneSide(false), new File(block, "village_hall_2_side.png"));
        write(hallStoneSide(true), new File(block, "village_hall_2_front.png"));
        write(hallStoneTop(), new File(block, "village_hall_2_top.png"));
        write(blueprintIcon(), new File(item, "blueprint.png"));
        write(storageSide(), new File(block, "station_storage_side.png"));
        write(storageTop(), new File(block, "station_storage_top.png"));
    }

    static final int STONE = 0xFF8E8E8E, STONE_DARK = 0xFF666666, STONE_LIGHT = 0xFFAAAAAA, MORTAR = 0xFF55524E;

    /** Stage 2 hall (마을): stone brick courses with a timber lintel; the front keeps the red banner. */
    static BufferedImage hallStoneSide(boolean front) {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, STONE);
        for (int row = 0; row < 4; row++) {
            int y = row * 4;
            fill(img, 0, y + 3, 16, 1, MORTAR);
            fill(img, 0, y, 16, 1, STONE_LIGHT);
            int offset = row % 2 == 0 ? 0 : 4;
            for (int x = offset; x < 16; x += 8) {
                fill(img, x, y, 1, 3, MORTAR);
            }
            fill(img, (offset + 6) % 16, y + 1, 1, 2, STONE_DARK);
        }
        fill(img, 0, 0, 16, 2, BEAM);
        if (front) {
            fill(img, 4, 3, 8, 10, RED);
            border(img, 4, 3, 8, 10, 0xFF7E1B17);
            fill(img, 4, 13, 2, 1, RED);
            fill(img, 10, 13, 2, 1, RED);
            for (int r = 0; r < 3; r++) {
                fill(img, 7 - r, 5 + r, 2 + r * 2, 1, GOLD);
            }
            fill(img, 6, 8, 4, 3, GOLD);
            fill(img, 7, 9, 2, 2, GOLD_DARK);
        }
        return img;
    }

    static BufferedImage hallStoneTop() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, STONE);
        border(img, 0, 0, 16, 16, MORTAR);
        fill(img, 3, 3, 10, 10, STONE_LIGHT);
        border(img, 3, 3, 10, 10, STONE_DARK);
        fill(img, 7, 6, 2, 4, GOLD);
        return img;
    }

    /** Blueprint: a rolled-out blue sheet with a white house drawn on it. */
    static BufferedImage blueprintIcon() {
        BufferedImage img = img(16, 16);
        int paper = 0xFF2F6FB8, dark = 0xFF1D4A82, line = 0xFFE8F2FF;
        fill(img, 2, 3, 12, 10, paper);
        border(img, 2, 3, 12, 10, dark);
        fill(img, 1, 3, 1, 10, 0xFFD9C9A3);
        fill(img, 14, 3, 1, 10, 0xFFD9C9A3);
        for (int r = 0; r < 3; r++) {
            px(img, 7 - r, 5 + r, line);
            px(img, 8 + r, 5 + r, line);
        }
        fill(img, 5, 8, 1, 3, line);
        fill(img, 10, 8, 1, 3, line);
        fill(img, 5, 10, 6, 1, line);
        px(img, 8, 9, line);
        return img;
    }

    static final int WOOD = 0xFFA8794A, WOOD_DARK = 0xFF7A5430, WOOD_LIGHT = 0xFFC49463, BEAM = 0xFF4A3220;

    /** Village hall: plank wall in a dark timber frame; the front carries a red banner with a gold house. */
    static BufferedImage hallSide(boolean front) {
        BufferedImage img = img(16, 16);
        for (int y = 0; y < 16; y++) {
            fill(img, 0, y, 16, 1, y % 4 == 3 ? WOOD_DARK : (y % 4 == 0 ? WOOD_LIGHT : WOOD));
        }
        // Plank seams, staggered per row.
        for (int row = 0; row < 4; row++) {
            px(img, row % 2 == 0 ? 5 : 10, row * 4 + 1, WOOD_DARK);
            px(img, row % 2 == 0 ? 5 : 10, row * 4 + 2, WOOD_DARK);
        }
        border(img, 0, 0, 16, 16, BEAM);
        fill(img, 0, 0, 16, 2, BEAM);
        if (front) {
            fill(img, 4, 3, 8, 10, RED);
            fill(img, 4, 13, 2, 1, RED);
            fill(img, 10, 13, 2, 1, RED);
            border(img, 4, 3, 8, 10, 0xFF7E1B17);
            // House: roof triangle and body.
            for (int r = 0; r < 3; r++) {
                fill(img, 7 - r, 5 + r, 2 + r * 2, 1, GOLD);
            }
            fill(img, 6, 8, 4, 3, GOLD);
            fill(img, 7, 9, 2, 2, GOLD_DARK);
        }
        return img;
    }

    static BufferedImage hallTop() {
        BufferedImage img = img(16, 16);
        for (int x = 0; x < 16; x++) {
            fill(img, x, 0, 1, 16, x % 4 == 3 ? WOOD_DARK : (x % 4 == 0 ? WOOD_LIGHT : WOOD));
        }
        border(img, 0, 0, 16, 16, BEAM);
        fill(img, 7, 1, 2, 14, BEAM);
        return img;
    }

    /** Mark 4's Master Sword (display item): steel blade up to the top right, purple guard, gold gem and pommel. */
    static BufferedImage masterSword() {
        BufferedImage img = img(16, 16);
        int edge = 0xFF3B4150, steel = 0xFFEEF3F8, shade = 0xFFA7B4C6;
        int guard = 0xFF5146B8, guardDark = 0xFF2E2878, grip = 0xFF2B3A86;
        for (int r = 0; r <= 9; r++) {
            px(img, 14 - r, r, edge);
            px(img, 15 - r, r, steel);
            px(img, 16 - r, r, shade);
            px(img, 17 - r, r, edge);
        }
        for (int k = -3; k <= 3; k++) {
            px(img, 5 + k, 10 + k, guard);
            px(img, 6 + k, 10 + k, guardDark);
        }
        px(img, 5, 10, GOLD);
        px(img, 6, 10, GOLD_DARK);
        px(img, 4, 11, grip);
        px(img, 3, 12, grip);
        px(img, 2, 13, grip);
        px(img, 1, 14, GOLD);
        px(img, 2, 14, GOLD_DARK);
        px(img, 1, 13, GOLD_DARK);
        return img;
    }

    static void px(BufferedImage img, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c);
        }
    }

    static BufferedImage cleanerDockTop() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, 0xFFD9DCDD);
        border(img, 0, 0, 16, 16, 0xFF9DA2A4);
        // Charging contacts and a status LED.
        fill(img, 4, 3, 2, 6, GOLD);
        fill(img, 10, 3, 2, 6, GOLD);
        img.setRGB(7, 12, CYAN);
        img.setRGB(8, 12, CYAN);
        return img;
    }

    static BufferedImage cleanerDockSide() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, 0xFFBFC3C5);
        fill(img, 0, 0, 16, 1, 0xFFE6E8E9);
        fill(img, 0, 1, 16, 1, 0xFF8C9194);
        return img;
    }

    static BufferedImage cleanerRobotIcon() {
        BufferedImage img = img(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Octagon: a square with the corners cut.
                int dx = Math.abs(x * 2 - 15), dy = Math.abs(y * 2 - 15);
                if (dx <= 13 && dy <= 13 && dx + dy <= 20) {
                    boolean rim = dx >= 12 || dy >= 12 || dx + dy >= 18;
                    img.setRGB(x, y, rim ? 0xFFA9ADAA : 0xFFFBFBF8);
                }
            }
        }
        fill(img, 5, 2, 6, 2, 0xFF101416);   // front intake
        fill(img, 6, 7, 4, 3, 0xFF767A78);   // top button
        img.setRGB(4, 5, 0xFF2A2E30);
        img.setRGB(11, 5, 0xFF2A2E30);
        return img;
    }

    static BufferedImage carCapsuleIcon() {
        BufferedImage img = img(16, 16);
        int outline = 0xFF3A2A12, yellow = 0xFFF2C94C, yellowDark = 0xFFC79A2A, white = 0xFFF4F1E8, whiteDark = 0xFFC9C5BA;
        for (int y = 4; y <= 11; y++) {
            for (int x = 1; x <= 14; x++) {
                double cx = x < 5 ? 5 : (x > 10 ? 10 : x);
                double d = Math.hypot(x - cx, y - 7.5);
                if (d > 4.0) continue;
                int c = d > 3.2 ? outline : (x <= 7 ? (y > 8 ? whiteDark : white) : (y > 8 ? yellowDark : yellow));
                img.setRGB(x, y, c);
            }
        }
        img.setRGB(4, 5, 0xFFFFFFFF);
        img.setRGB(11, 7, RED);   // the little capsule number dot
        img.setRGB(11, 8, RED);
        return img;
    }

    static BufferedImage plate(int base) {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, base);
        border(img, 0, 0, 16, 16, EDGE);
        // Rivets.
        for (int[] p : new int[][]{{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
            img.setRGB(p[0], p[1], METAL_LIGHT);
        }
        return img;
    }

    static BufferedImage stationTop() {
        BufferedImage img = plate(METAL);
        // Hazard border.
        for (int i = 1; i < 15; i++) {
            int c = (i / 2) % 2 == 0 ? GOLD : EDGE;
            img.setRGB(i, 1, c);
            img.setRGB(i, 14, c);
            img.setRGB(1, i, c);
            img.setRGB(14, i, c);
        }
        // Charging ring.
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d > 4.2 && d < 5.4) img.setRGB(x, y, CYAN);
                else if (d > 3.4 && d <= 4.2) img.setRGB(x, y, CYAN_DARK);
                else if (d < 1.6) img.setRGB(x, y, CYAN_GLOW);
            }
        }
        return img;
    }

    static BufferedImage stationSide() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, METAL_DARK);
        fill(img, 0, 0, 16, 1, METAL_LIGHT);
        fill(img, 0, 15, 16, 1, EDGE);
        // The platform model only shows the top 4 rows of this texture (uv 0-4); keep the stripe there.
        fill(img, 0, 1, 16, 1, GOLD);
        for (int x = 2; x < 16; x += 4) {
            img.setRGB(x, 2, CYAN);
        }
        fill(img, 0, 3, 16, 1, GOLD_DARK);
        return img;
    }

    /** The platform around the center: deck plating with a hazard edge on the outside. */
    static BufferedImage stationPlatformTop() {
        BufferedImage img = plate(METAL);
        for (int i = 4; i < 12; i++) {
            img.setRGB(i, 7, METAL_DARK);
            img.setRGB(7, i, METAL_DARK);
        }
        for (int x = 3; x < 13; x += 3) {
            img.setRGB(x, 3, CYAN_DARK);
            img.setRGB(x, 12, CYAN_DARK);
        }
        return img;
    }

    /** Pillars and beams: dark girder with a gold service stripe and bolts. */
    static BufferedImage stationFrame() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, METAL_DARK);
        fill(img, 0, 0, 1, 16, EDGE);
        fill(img, 15, 0, 1, 16, EDGE);
        fill(img, 6, 0, 4, 16, METAL);
        fill(img, 7, 0, 2, 16, GOLD_DARK);
        for (int y = 2; y < 16; y += 4) {
            img.setRGB(3, y, METAL_LIGHT);
            img.setRGB(12, y, METAL_LIGHT);
        }
        return img;
    }

    /** Robot arm plating (tinted per part by the renderer): light panel with seams. */
    static BufferedImage stationArm() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, 0xFFE4E7EB);
        border(img, 0, 0, 16, 16, 0xFF9AA1AA);
        fill(img, 0, 7, 16, 1, 0xFFB7BDC4);
        img.setRGB(3, 3, 0xFF9AA1AA);
        img.setRGB(12, 12, 0xFF9AA1AA);
        return img;
    }

    /** Phantom's thrown card (10x14 in a 16x16, rest transparent): face, border, and a diamond pip. */
    static BufferedImage card(int face, int trim, int pip) {
        BufferedImage img = img(16, 16);
        fill(img, 3, 1, 10, 14, face);
        border(img, 3, 1, 10, 14, trim);
        border(img, 4, 2, 8, 12, face);
        for (int dy = -3; dy <= 3; dy++) {
            int half = 2 - Math.abs(dy) * 2 / 3;
            fill(img, 8 - half, 8 + dy, half * 2, 1, pip);
        }
        img.setRGB(5, 3, pip);
        img.setRGB(10, 12, pip);
        return img;
    }

    static BufferedImage solarTop() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, SOLAR);
        for (int i = 0; i < 16; i++) {
            if (i % 5 == 0) {
                for (int j = 0; j < 16; j++) {
                    img.setRGB(i, j, SOLAR_LINE);
                    img.setRGB(j, i, SOLAR_LINE);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            img.setRGB(2 + i, 2 + i, SOLAR_SHINE);
            img.setRGB(7 + i, 7 + i, SOLAR_SHINE);
        }
        border(img, 0, 0, 16, 16, SILVER);
        return img;
    }

    static BufferedImage solarSide() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, METAL);
        fill(img, 0, 10, 16, 1, SILVER);
        fill(img, 0, 15, 16, 1, EDGE);
        return img;
    }

    static BufferedImage generatorFront(boolean lit) {
        BufferedImage img = plate(METAL);
        fill(img, 3, 4, 10, 8, EDGE);
        for (int y = 5; y < 11; y += 2) {
            for (int x = 4; x < 12; x++) {
                img.setRGB(x, y, lit ? (x % 3 == 0 ? ORANGE_HOT : ORANGE) : METAL_DARK);
            }
        }
        fill(img, 3, 13, 10, 1, RED);
        return img;
    }

    static BufferedImage generatorSide() {
        BufferedImage img = plate(METAL);
        for (int y = 4; y < 12; y += 2) {
            fill(img, 3, y, 10, 1, METAL_DARK);
        }
        fill(img, 0, 7, 1, 2, GOLD);
        fill(img, 15, 7, 1, 2, GOLD);
        return img;
    }

    static BufferedImage generatorTop() {
        BufferedImage img = plate(METAL_DARK);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (Math.hypot(x - 7.5, y - 7.5) < 4.5 && (x + y) % 2 == 0) img.setRGB(x, y, EDGE);
            }
        }
        return img;
    }

    static BufferedImage batterySide() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, METAL_DARK);
        fill(img, 0, 0, 16, 2, METAL_LIGHT);
        fill(img, 0, 14, 16, 2, METAL_LIGHT);
        fill(img, 5, 3, 6, 10, EDGE);
        for (int y = 4; y < 12; y += 2) {
            fill(img, 6, y, 4, 1, y < 6 ? CYAN_GLOW : CYAN);
        }
        fill(img, 1, 2, 1, 12, GOLD_DARK);
        fill(img, 14, 2, 1, 12, GOLD_DARK);
        return img;
    }

    /** Station storage: dark vault plating, a cyan hologram readout band and a gold-rimmed lock seam. */
    static BufferedImage storageSide() {
        BufferedImage img = img(16, 16);
        fill(img, 0, 0, 16, 16, METAL);
        border(img, 0, 0, 16, 16, EDGE);
        fill(img, 1, 1, 14, 1, METAL_LIGHT);
        // Hologram readout: a dark screen with glowing item rows.
        fill(img, 2, 3, 12, 6, EDGE);
        for (int y = 4; y < 8; y += 2) {
            fill(img, 3, y, 4, 1, CYAN);
            fill(img, 8, y, 2, 1, CYAN_DARK);
            fill(img, 11, y, 2, 1, y == 4 ? CYAN_GLOW : CYAN);
        }
        // Lower drawer with the lock seam.
        fill(img, 2, 10, 12, 1, METAL_DARK);
        fill(img, 2, 13, 12, 1, METAL_DARK);
        fill(img, 7, 11, 2, 2, GOLD);
        img.setRGB(7, 11, GOLD_DARK);
        return img;
    }

    static BufferedImage storageTop() {
        BufferedImage img = plate(METAL_DARK);
        border(img, 3, 3, 10, 10, GOLD_DARK);
        fill(img, 6, 6, 4, 4, EDGE);
        fill(img, 7, 7, 2, 2, CYAN_GLOW);
        return img;
    }

    static BufferedImage batteryTop() {
        BufferedImage img = plate(METAL_LIGHT);
        fill(img, 3, 6, 4, 4, RED);
        fill(img, 9, 6, 4, 4, EDGE);
        img.setRGB(4, 7, 0xFFFFFFFF);
        img.setRGB(5, 7, 0xFFFFFFFF);
        img.setRGB(10, 7, 0xFFFFFFFF);
        img.setRGB(11, 7, 0xFFFFFFFF);
        return img;
    }

    /**
     * 32x32, laid out for SuitArmorModels#createGlasses box UVs:
     * (0,0) brow bar, (0,4) lenses, (0,8) bridge, (0,12) temples.
     */
    static BufferedImage glassesArmor() {
        BufferedImage img = img(32, 32);
        int frame = 0xFF26262B, frameHi = 0xFF4A4A52, lens = 0xFF1F5C66;
        fill(img, 0, 0, 24, 3, frame);
        fill(img, 1, 1, 9, 1, frameHi);
        fill(img, 0, 4, 10, 4, lens);
        img.setRGB(1, 5, CYAN);       // HUD glint
        img.setRGB(2, 5, CYAN_GLOW);
        fill(img, 0, 8, 8, 3, frame);
        fill(img, 0, 12, 14, 7, frame);
        return img;
    }

    static BufferedImage glassesIcon() {
        BufferedImage img = img(16, 16);
        int frame = 0xFF26262B, lens = 0xFF1F5C66;
        fill(img, 1, 6, 14, 1, frame);
        for (int lx : new int[]{2, 9}) {
            border(img, lx, 6, 5, 4, frame);
            fill(img, lx + 1, 7, 3, 2, lens);
            img.setRGB(lx + 1, 7, CYAN);
        }
        img.setRGB(0, 6, frame);
        img.setRGB(15, 6, frame);
        img.setRGB(0, 5, frame);
        img.setRGB(15, 5, frame);
        return img;
    }

    static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static void fill(BufferedImage img, int x, int y, int w, int h, int c) {
        for (int j = y; j < y + h; j++) {
            for (int i = x; i < x + w; i++) {
                img.setRGB(i, j, c);
            }
        }
    }

    static void border(BufferedImage img, int x, int y, int w, int h, int c) {
        for (int i = x; i < x + w; i++) {
            img.setRGB(i, y, c);
            img.setRGB(i, y + h - 1, c);
        }
        for (int j = y; j < y + h; j++) {
            img.setRGB(x, j, c);
            img.setRGB(x + w - 1, j, c);
        }
    }

    static void write(BufferedImage img, File file) throws IOException {
        ImageIO.write(img, "png", file);
        System.out.println("wrote " + file);
    }
}
