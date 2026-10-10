package com.pfkfks.flightsuit.suit;

/**
 * One wearable suit design (character x class). A suit type owns four armor pieces and a capsule.
 * Copies of the same design are allowed - see DESIGN.md "중복 허용".
 */
public enum SuitType {
    RYAN_MK1("ryan_mk1", "RYAN MARK 1", SuitClass.STANDARD, SuitArmorMaterial.MARK_1),
    RYAN_MK2("ryan_mk2", "CHOONSIK MARK 2 STEALTH", SuitClass.STEALTH, SuitArmorMaterial.MARK_2),
    PHANTOM_MK3("phantom_mk3", "PHANTOM MARK 3", SuitClass.PHANTOM, SuitArmorMaterial.MARK_3),
    HERO_MK4("hero_mk4", "HERO OF TWILIGHT MARK 4", SuitClass.HERO, SuitArmorMaterial.MARK_4),
    /** Trunks (the user's skin) with 3D hair, collar and the sword on his back - its own model (TrunksSuitModel). */
    TRUNKS_MK5("trunks_mk5", "TRUNKS MARK 5", SuitClass.SWORDSMAN, SuitArmorMaterial.MARK_5, 1.0F, true),
    /** Built on the Hulk's frame (HulkbusterModel); a full set makes its wearer 1.5x as big. */
    HULKBUSTER_MK44("hulkbuster_mk44", "HULKBUSTER MARK 44", SuitClass.HULKBUSTER, SuitArmorMaterial.MARK_44, 1.5F, true),
    /** Won by beating Thanos (DESIGN.md 4-16 최종전 보상); no recipe. */
    NANO_MK50("nano_mk50", "NANOTECH MARK 50", SuitClass.STANDARD, SuitArmorMaterial.MARK_50);

    private final String id;
    private final String hudName;
    private final SuitClass suitClass;
    private final SuitArmorMaterial material;
    private final float size;
    private final boolean ownModel;

    SuitType(String id, String hudName, SuitClass suitClass, SuitArmorMaterial material) {
        this(id, hudName, suitClass, material, 1.0F, false);
    }

    SuitType(String id, String hudName, SuitClass suitClass, SuitArmorMaterial material, float size, boolean ownModel) {
        this.id = id;
        this.hudName = hudName;
        this.suitClass = suitClass;
        this.material = material;
        this.size = size;
        this.ownModel = ownModel;
    }

    public String id() {
        return id;
    }

    public String hudName() {
        return hudName;
    }

    public SuitClass suitClass() {
        return suitClass;
    }

    public SuitArmorMaterial material() {
        return material;
    }

    /** How much bigger than its wearer the full suit is (SuitSize): 1 for everything but the Hulkbuster. */
    public float size() {
        return size;
    }

    /**
     * True for suits with a model of their own (SuitModel: one texture, textures/models/armor/<id>.png) rather
     * than a skin split into four per-slot textures.
     */
    public boolean ownModel() {
        return ownModel;
    }
}
