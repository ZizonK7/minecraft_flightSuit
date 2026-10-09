package com.pfkfks.flightsuit.suit;

/**
 * One wearable suit design (character x class). A suit type owns four armor pieces and a capsule.
 * Copies of the same design are allowed - see DESIGN.md "중복 허용".
 */
public enum SuitType {
    RYAN_MK1("ryan_mk1", "RYAN MARK 1", SuitClass.STANDARD, SuitArmorMaterial.MARK_1),
    RYAN_MK2("ryan_mk2", "CHOONSIK MARK 2 STEALTH", SuitClass.STEALTH, SuitArmorMaterial.MARK_2);

    private final String id;
    private final String hudName;
    private final SuitClass suitClass;
    private final SuitArmorMaterial material;

    SuitType(String id, String hudName, SuitClass suitClass, SuitArmorMaterial material) {
        this.id = id;
        this.hudName = hudName;
        this.suitClass = suitClass;
        this.material = material;
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
}
