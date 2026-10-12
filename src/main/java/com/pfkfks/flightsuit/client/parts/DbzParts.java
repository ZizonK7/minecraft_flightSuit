package com.pfkfks.flightsuit.client.parts;

import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * M18 which 3D parts each Dragon Ball character wears (DbzPartsLayer): the parts model and the groups of it to show.
 * The group names are the contract between the models (tools/ModelGen.java parts_*) and this table - a model
 * without a listed group simply draws nothing for it.
 */
public final class DbzParts {
    public enum Set_ {
        SAIYAN, NAMEKIAN, FRIEZA, CELL_BUU, MARVEL
    }

    /** A character's parts: which model, which of its groups. */
    public record Wear(Set_ set, Set<String> groups) {
    }

    private static final Map<DbzCharacter, Wear> WEAR = new EnumMap<>(DbzCharacter.class);

    static {
        // Saiyan tails (SaiyanParts: "tail").
        for (DbzCharacter who : new DbzCharacter[]{DbzCharacter.RADITZ, DbzCharacter.VEGETA, DbzCharacter.GOHAN_KID}) {
            WEAR.put(who, new Wear(Set_.SAIYAN, Set.of("tail")));
        }
        // Namekians and the other head parts (NamekianParts: "antennae", "ears", "horns", "spikes").
        WEAR.put(DbzCharacter.PICCOLO, new Wear(Set_.NAMEKIAN, Set.of("antennae", "ears")));
        WEAR.put(DbzCharacter.DENDE, new Wear(Set_.NAMEKIAN, Set.of("antennae", "ears")));
        WEAR.put(DbzCharacter.GINYU, new Wear(Set_.NAMEKIAN, Set.of("horns")));
        WEAR.put(DbzCharacter.DODORIA, new Wear(Set_.NAMEKIAN, Set.of("spikes")));
        // Frieza (FriezaParts: "horns", "pads", "tail", "tail_final").
        WEAR.put(DbzCharacter.FRIEZA, new Wear(Set_.FRIEZA, Set.of("horns", "pads", "tail")));
        WEAR.put(DbzCharacter.FRIEZA_FINAL, new Wear(Set_.FRIEZA, Set.of("tail_final")));
        // Cell and Buu (CellBuuParts).
        WEAR.put(DbzCharacter.CELL_IMPERFECT, new Wear(Set_.CELL_BUU, Set.of("crest_imperfect", "wings", "tail")));
        WEAR.put(DbzCharacter.CELL_SEMI, new Wear(Set_.CELL_BUU, Set.of("crest_semi", "wings")));
        WEAR.put(DbzCharacter.CELL, new Wear(Set_.CELL_BUU, Set.of("crest", "wings")));
        WEAR.put(DbzCharacter.CELL_JR, new Wear(Set_.CELL_BUU, Set.of("crest_jr", "wings")));
        WEAR.put(DbzCharacter.MAJIN_BUU, new Wear(Set_.CELL_BUU, Set.of("antenna_fat")));
        WEAR.put(DbzCharacter.SUPER_BUU, new Wear(Set_.CELL_BUU, Set.of("antenna_super")));
        WEAR.put(DbzCharacter.SUPER_BUU_ABSORBED, new Wear(Set_.CELL_BUU, Set.of("antenna_super")));
        WEAR.put(DbzCharacter.KID_BUU, new Wear(Set_.CELL_BUU, Set.of("antenna_kid")));
        // The Doom saga (MarvelParts): capes, and Talokan's headdress.
        WEAR.put(DbzCharacter.DOCTOR_DOOM, new Wear(Set_.MARVEL, Set.of("cape_doom")));
        WEAR.put(DbzCharacter.DOOMBOT, new Wear(Set_.MARVEL, Set.of("cape_doom")));
        WEAR.put(DbzCharacter.DOOM_GOD_EMPEROR, new Wear(Set_.MARVEL, Set.of("cape_god")));
        WEAR.put(DbzCharacter.MAGNETO, new Wear(Set_.MARVEL, Set.of("cape_magneto")));
        WEAR.put(DbzCharacter.LATVERIAN_WITCH, new Wear(Set_.MARVEL, Set.of("cape_witch")));
        WEAR.put(DbzCharacter.TALOKAN_WARRIOR, new Wear(Set_.MARVEL, Set.of("feathers")));
    }

    private DbzParts() {
    }

    public static @Nullable Wear of(DbzCharacter who) {
        return WEAR.get(who);
    }
}
