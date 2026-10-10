import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Procedural placeholder textures for the M2 blocks and the EDITH glasses (no image tools on this machine,
 * so pixel art is drawn in code). Re-run after changing a palette:
 *
 *   java tools/TextureGen.java src/main/resources/assets/flightsuit/textures
 *
 * With "zeni" after the directory, only the M17 zeni note is drawn (nothing else is overwritten).
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
        if (args.length > 1 && args[1].equals("zeni")) {
            write(zeni(), new File(item, "zeni.png"));
            return;
        }

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
        // The same faces for the shadow step's card swirl (particles live in their own atlas).
        File particle = new File(entity.getParentFile(), "particle");
        particle.mkdirs();
        write(card(0xFFF6F4EE, GOLD, RED), new File(particle, "card_blanche.png"));
        write(card(0xFF1A1620, 0xFF8A4FD8, 0xFFF6F4EE), new File(particle, "card_noir.png"));
        write(masterSword(), new File(item, "master_sword.png"));
        write(mjolnir(), new File(item, "mjolnir.png"));
        write(greenDragonBlade(), new File(item, "green_dragon_blade.png"));
        write(serpentSpear(), new File(item, "serpent_spear.png"));
        write(broadsword(), new File(item, "xiahou_broadsword.png"));
        write(belledSabre(), new File(item, "belled_sabre.png"));
        write(twinSwords(), new File(item, "twin_swords.png"));
        write(yitianSword(), new File(item, "yitian_sword.png"));
        write(guDingDao(), new File(item, "gu_ding_dao.png"));
        write(widowBaton(), new File(item, "widow_baton.png"));
        write(hawkeyeBow(), new File(item, "hawkeye_bow.png"));
        write(shieldPistol(), new File(item, "shield_pistol.png"));
        write(captainShield(), new File(item, "captain_shield.png"));
        write(wuzhuCoin(), new File(item, "wuzhu_coin.png"));
        write(dollar(), new File(item, "dollar.png"));
        write(guideBook(), new File(item, "guide_book.png"));
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

    /**
     * Thor's Mjolnir (display item): drawn in coordinates along the diagonal (u up the handle, v across it), so
     * the head sits square across the end of a handle that runs from the bottom left - steel head with a darker
     * rim and a rune, a leather-wrapped grip, a steel pommel.
     */
    static BufferedImage mjolnir() {
        BufferedImage img = img(16, 16);
        int edge = 0xFF262A32, steel = 0xFFB4BCC8, shade = 0xFF7C8594, light = 0xFFE4E9F0, rune = 0xFF56607A;
        int grip = 0xFF7A4A2C, gripDark = 0xFF4A2A16, band = 0xFFA4ACB8;
        double r2 = Math.sqrt(2.0D);
        double handleV = 16.0D / r2;
        double headU = 4.6D;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double u = (x - y) / r2;
                double v = (x + y + 1) / r2;
                double du = u - headU;
                double dv = v - handleV;
                if (Math.abs(du) <= 2.2D && Math.abs(dv) <= 4.3D) {
                    boolean rim = Math.abs(du) > 1.5D || Math.abs(dv) > 3.6D;
                    int c = rim ? edge : du > 0.6D ? light : du < -0.6D ? shade : steel;
                    if (!rim && Math.abs(dv) < 0.8D && Math.abs(du) < 0.8D) {
                        c = rune;
                    }
                    px(img, x, y, c);
                } else if (Math.abs(dv) <= 0.75D && u >= -9.6D && u < headU - 2.2D) {
                    int c;
                    if (u > headU - 3.2D) {
                        c = band;
                    } else if (u < -8.4D) {
                        c = band;
                    } else {
                        c = ((int) Math.floor(u * 1.4D)) % 2 == 0 ? grip : gripDark;
                    }
                    px(img, x, y, c);
                }
            }
        }
        return img;
    }

    // ---------------------------------------------------------------- weapons (display items, after the M13 test)

    /** Colour for a point at (u along the weapon from the grip at the bottom left, v across it), or 0 for none. */
    interface Along {
        int at(double u, double v);
    }

    /** Paints a weapon lying along the item's diagonal (like Mjolnir): u runs up the handle, v across it. */
    static BufferedImage along(Along painter) {
        BufferedImage img = img(16, 16);
        double r2 = Math.sqrt(2.0D);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double u = (x - y) / r2;
                double v = (x + y + 1) / r2 - 16.0D / r2;
                int c = painter.at(u, v);
                if (c != 0) {
                    px(img, x, y, c);
                }
            }
        }
        return img;
    }

    static final int POLE = 0xFF5A2E1A, POLE_DARK = 0xFF3A1C0E, STEEL = 0xFFDCE3EC, STEEL_SHADE = 0xFF9AA6B6, STEEL_EDGE = 0xFF4A5260;

    /** Guan Yu's Green Dragon Crescent Blade: a long red-brown pole, a broad curved blade, a green dragon at its base. */
    static BufferedImage greenDragonBlade() {
        return along((u, v) -> {
            if (u >= 1.5D && u <= 10.0D) {
                double t = (u - 1.5D) / 8.5D;
                double outer = 0.7D + 2.6D * Math.sin(Math.PI * Math.min(1.0D, t * 1.15D));
                if (v >= -0.8D && v <= outer) {
                    if (u < 3.0D) {
                        return v > 0.6D ? 0xFF2E8A3E : 0xFF1E5A28;
                    }
                    return v > outer - 0.8D ? STEEL_EDGE : v > outer - 1.6D ? STEEL : STEEL_SHADE;
                }
            }
            if (Math.abs(v) <= 0.75D && u >= -10.0D && u < 1.5D) {
                return u < -8.6D ? GOLD : ((int) Math.floor(u * 1.3D)) % 3 == 0 ? POLE_DARK : POLE;
            }
            return 0;
        });
    }

    /** Zhang Fei's Serpent Spear: a long dark pole, a red tassel, a wavy blade like a snake. */
    static BufferedImage serpentSpear() {
        return along((u, v) -> {
            if (u >= 3.8D && u <= 10.8D) {
                double centre = 0.45D * Math.sin((u - 3.8D) * 1.4D);
                double half = u > 9.0D ? 1.0D - (u - 9.0D) * 0.45D : 1.0D;
                if (Math.abs(v - centre) <= half) {
                    return v - centre > 0.35D ? STEEL : v - centre < -0.35D ? STEEL_EDGE : STEEL_SHADE;
                }
            }
            if (u >= 2.6D && u < 4.0D && Math.abs(v) <= 1.6D) {
                return Math.abs(v) > 0.9D ? 0xFFB01E1E : 0xFFD8342A;
            }
            if (Math.abs(v) <= 0.75D && u >= -10.0D && u < 2.6D) {
                return u < -8.8D ? STEEL_SHADE : 0xFF2A2A30;
            }
            return 0;
        });
    }

    /** Xiahou Dun's broadsword: a heavy, broad blade with a dark spine, a gold guard, a wrapped grip. */
    static BufferedImage broadsword() {
        return along((u, v) -> {
            if (u >= -3.6D && u <= 8.8D) {
                double top = u > 6.5D ? 1.6D - (u - 6.5D) * 1.2D : 1.6D;
                if (v >= -1.3D && v <= top) {
                    return v < -0.6D ? STEEL_EDGE : v > top - 0.7D ? STEEL : STEEL_SHADE;
                }
            }
            if (u >= -4.6D && u < -3.6D && Math.abs(v) <= 2.2D) {
                return Math.abs(v) > 1.4D ? GOLD_DARK : GOLD;
            }
            if (Math.abs(v) <= 0.75D && u >= -9.5D && u < -4.6D) {
                return u < -8.6D ? GOLD : ((int) Math.floor(u * 1.5D)) % 2 == 0 ? 0xFF2A3A6A : 0xFF1A2448;
            }
            return 0;
        });
    }

    /** Gan Ning's belled sabre: a slim curved blade, a red grip, and the bell he was known by at its pommel. */
    static BufferedImage belledSabre() {
        return along((u, v) -> {
            if (u >= -4.0D && u <= 9.5D) {
                double centre = 0.012D * (u + 4.0D) * (u + 4.0D) - 0.3D;
                double half = u > 7.5D ? 0.7D - (u - 7.5D) * 0.3D : 0.75D;
                if (Math.abs(v - centre) <= half) {
                    return v - centre > 0.1D ? STEEL : STEEL_SHADE;
                }
            }
            if (u >= -5.0D && u < -4.0D && Math.abs(v) <= 1.5D) {
                return GOLD_DARK;
            }
            if (Math.abs(v) <= 0.75D && u >= -9.0D && u < -5.0D) {
                return 0xFFB52A24;
            }
            double bu = u + 9.6D;
            double bv = v - 1.4D;
            if (bu * bu + bv * bv <= 1.6D) {
                return bv > 0.3D ? GOLD_DARK : GOLD;
            }
            return 0;
        });
    }

    /** Liu Bei's twin swords: two slim straight blades side by side, gold guards, green grips. */
    static BufferedImage twinSwords() {
        return along((u, v) -> {
            for (double off : new double[]{-1.5D, 1.5D}) {
                double w = v - off;
                double tip = off < 0 ? 9.0D : 8.0D;
                if (u >= -3.0D && u <= tip && Math.abs(w) <= (u > tip - 1.2D ? 0.4D : 0.75D)) {
                    return w > 0.0D ? STEEL : STEEL_SHADE;
                }
                if (u >= -4.0D && u < -3.0D && Math.abs(w) <= 1.3D) {
                    return GOLD;
                }
                if (u >= -8.0D && u < -4.0D && Math.abs(w) <= 0.6D) {
                    return u < -7.2D ? GOLD_DARK : 0xFF2E7A3E;
                }
            }
            return 0;
        });
    }

    /** Cao Cao's Yitian sword: a long, fine straight blade, a dark guard set with gold, a blue tassel. */
    static BufferedImage yitianSword() {
        return along((u, v) -> {
            if (u >= -3.0D && u <= 10.2D && Math.abs(v) <= (u > 9.0D ? 0.4D : 0.75D)) {
                return v > 0.0D ? STEEL : STEEL_SHADE;
            }
            if (u >= -4.2D && u < -3.0D && Math.abs(v) <= 1.8D) {
                return Math.abs(v) < 0.6D ? GOLD : 0xFF1E2A5A;
            }
            if (Math.abs(v) <= 0.75D && u >= -8.6D && u < -4.2D) {
                return u < -7.8D ? GOLD : 0xFF14142A;
            }
            if (u >= -10.4D && u < -8.6D && v >= -2.0D && v <= 0.2D) {
                return 0xFF2E5AD8;
            }
            return 0;
        });
    }

    /** Sun Quan's Gu Ding Dao: the Sun family's straight broad blade, a red-and-gold guard (Wu's colours), a red tassel. */
    static BufferedImage guDingDao() {
        return along((u, v) -> {
            if (u >= -3.2D && u <= 8.8D) {
                double half = u > 7.0D ? 1.1D - (u - 7.0D) * 0.6D : 1.1D;
                if (Math.abs(v) <= half) {
                    return v > 0.4D ? STEEL : v < -0.6D ? STEEL_EDGE : STEEL_SHADE;
                }
            }
            if (u >= -4.4D && u < -3.2D && Math.abs(v) <= 1.9D) {
                return Math.abs(v) < 0.7D ? GOLD : 0xFFB52A24;
            }
            if (Math.abs(v) <= 0.75D && u >= -8.8D && u < -4.4D) {
                return u < -8.0D ? GOLD : 0xFF4A1E14;
            }
            if (u >= -10.6D && u < -8.8D && v >= -0.2D && v <= 2.0D) {
                return 0xFFD8342A;
            }
            return 0;
        });
    }

    /** Black Widow's Widow's Bite baton: a short black baton with a glowing blue tip. */
    static BufferedImage widowBaton() {
        return along((u, v) -> {
            if (Math.abs(v) <= 0.8D && u >= -7.0D && u <= 6.0D) {
                if (u > 4.4D) {
                    return v > 0.0D ? CYAN_GLOW : CYAN;
                }
                if (u < -5.4D) {
                    return 0xFF5A5E66;
                }
                return ((int) Math.floor(u * 1.4D)) % 4 == 0 ? 0xFF3A3E46 : 0xFF16181C;
            }
            if (u > 6.0D && u <= 7.0D && Math.abs(v) <= 0.4D) {
                return CYAN_GLOW;
            }
            return 0;
        });
    }

    /**
     * Hawkeye's recurve bow: dark purple limbs curving to the tips, a black grip, a taut string. Laid like the
     * vanilla bow (the limbs bow out to the top left, the string on the diagonal) - its held pose expects that.
     */
    static BufferedImage hawkeyeBow() {
        return along((u, w) -> {
            double v = -w;
            if (Math.abs(u) <= 7.6D) {
                double limb = 2.4D - 0.042D * u * u;
                if (Math.abs(u) > 6.4D) {
                    // Recurve: the tips flick back out.
                    limb += (Math.abs(u) - 6.4D) * 0.5D;
                }
                if (Math.abs(v - limb) <= 0.65D) {
                    return Math.abs(u) < 1.4D ? 0xFF141418 : v > limb ? 0xFF8A4AC8 : 0xFF4E2878;
                }
                double string = -0.1D + 0.0D * u;
                if (Math.abs(u) < 6.8D && Math.abs(v - string) <= 0.35D) {
                    return 0xFFD8D8D0;
                }
            }
            return 0;
        });
    }

    /**
     * A S.H.I.E.L.D. pistol: black slide and grip, a steel barrel tip. Drawn barrel to the left, grip down - held
     * in the hand ("handheld"), the sprite's right side points back, so this way it points ahead, grip down
     * (worked out from how the first two drawings sat in the hand in game).
     */
    static BufferedImage shieldPistol() {
        return flipHorizontally(pistolUpright());
    }

    static BufferedImage flipHorizontally(BufferedImage in) {
        BufferedImage out = img(in.getWidth(), in.getHeight());
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                out.setRGB(in.getWidth() - 1 - x, y, in.getRGB(x, y));
            }
        }
        return out;
    }

    static BufferedImage flipVertically(BufferedImage in) {
        BufferedImage out = img(in.getWidth(), in.getHeight());
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                out.setRGB(x, in.getHeight() - 1 - y, in.getRGB(x, y));
            }
        }
        return out;
    }

    static BufferedImage pistolUpright() {
        BufferedImage img = img(16, 16);
        int black = 0xFF1A1C20, dark = 0xFF2C3036, light = 0xFF4A5058;
        fill(img, 3, 5, 11, 3, black);
        fill(img, 3, 5, 11, 1, light);
        fill(img, 14, 6, 1, 1, STEEL_SHADE);
        fill(img, 4, 8, 4, 6, dark);
        fill(img, 4, 8, 1, 6, black);
        fill(img, 8, 8, 2, 1, black);
        fill(img, 9, 9, 1, 2, black);
        px(img, 5, 10, light);
        px(img, 5, 12, light);
        return img;
    }

    /** Captain's shield, face on: red and white rings, a blue disc with a white star; transparent outside the circle. */
    static BufferedImage captainShield() {
        BufferedImage img = img(16, 16);
        int red = 0xFFC8202A, white = 0xFFF0F0F0, blue = 0xFF1E3C9A, rim = 0xFF8A141C;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x + 0.5D - 8.0D;
                double dy = y + 0.5D - 8.0D;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 8.0D) {
                    continue;
                }
                int c = d > 7.2D ? rim : d > 5.8D ? red : d > 4.4D ? white : blue;
                px(img, x, y, c);
            }
        }
        // The star.
        String[] star = {"..#..", "#####", ".###.", ".#.#."};
        for (int row = 0; row < star.length; row++) {
            for (int col = 0; col < 5; col++) {
                if (star[row].charAt(col) == '#') {
                    px(img, 6 + col, 6 + row, white);
                }
            }
        }
        return img;
    }

    /** A wuzhu coin (the Three Kingdoms' money): a round bronze coin with a square hole and a raised rim. */
    static BufferedImage wuzhuCoin() {
        BufferedImage img = img(16, 16);
        int rim = 0xFF6A4A1E, face = 0xFFB8862E, shine = 0xFFE0B458, dark = 0xFF8A6424;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x + 0.5D - 8.0D;
                double dy = y + 0.5D - 8.0D;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 6.6D) {
                    continue;
                }
                boolean hole = Math.abs(dx) < 1.6D && Math.abs(dy) < 1.6D;
                boolean holeRim = Math.abs(dx) < 2.6D && Math.abs(dy) < 2.6D;
                int c = d > 5.6D ? rim : hole ? 0 : holeRim ? dark : (dx + dy < -3.0D ? shine : face);
                if (c != 0) {
                    px(img, x, y, c);
                }
            }
        }
        return img;
    }

    /** A dollar bill (Hero City's money): a green note with a pale border and a dark oval portrait. */
    static BufferedImage dollar() {
        BufferedImage img = img(16, 16);
        int edge = 0xFF2E5A2A, paper = 0xFF8ABE7A, light = 0xFFC8E8B8, ink = 0xFF2A4A26;
        fill(img, 1, 4, 14, 8, edge);
        fill(img, 2, 5, 12, 6, paper);
        fill(img, 3, 6, 10, 4, light);
        fill(img, 6, 6, 4, 4, ink);
        fill(img, 7, 7, 2, 2, paper);
        px(img, 3, 6, ink);
        px(img, 12, 9, ink);
        return img;
    }

    /** Zeni (Dragon Ball Earth's money, M17): a cream note with an orange band and a red seal. */
    static BufferedImage zeni() {
        BufferedImage img = img(16, 16);
        int edge = 0xFF8A5A1E, paper = 0xFFF2E2B0, band = 0xFFE8962A, seal = 0xFFC0302A, ink = 0xFF6A3A12;
        fill(img, 1, 4, 14, 8, edge);
        fill(img, 2, 5, 12, 6, paper);
        fill(img, 2, 5, 3, 6, band);
        fill(img, 9, 6, 3, 4, seal);
        px(img, 10, 7, paper);
        px(img, 10, 8, paper);
        fill(img, 6, 6, 2, 1, ink);
        fill(img, 6, 9, 2, 1, ink);
        px(img, 13, 10, ink);
        return img;
    }

    /** The guide book: a Ryan-red cover with a gold spine and an arc reactor on the front, pages showing at the edge. */
    static BufferedImage guideBook() {
        BufferedImage img = img(16, 16);
        int dark = 0xFF6A1414, red = 0xFFB8302A, page = 0xFFF2EEDF, pageShade = 0xFFC8C2AE;
        fill(img, 3, 2, 11, 12, dark);
        fill(img, 4, 3, 9, 10, red);
        fill(img, 2, 2, 2, 12, GOLD);
        fill(img, 13, 3, 1, 11, page);
        fill(img, 4, 14, 10, 1, page);
        px(img, 13, 14, pageShade);
        fill(img, 14, 4, 1, 10, pageShade);
        // The arc reactor: a cyan ring, white heart.
        fill(img, 7, 5, 3, 1, CYAN);
        fill(img, 7, 9, 3, 1, CYAN);
        fill(img, 6, 6, 1, 3, CYAN);
        fill(img, 10, 6, 1, 3, CYAN);
        fill(img, 7, 6, 3, 3, 0xFF2A5A6A);
        px(img, 8, 7, 0xFFFFFFFF);
        // Gold title lines under it.
        fill(img, 6, 11, 5, 1, GOLD);
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
