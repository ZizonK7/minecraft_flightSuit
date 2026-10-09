package com.pfkfks.flightsuit.suit;

/**
 * One wearable suit design (character x class). A suit type owns four armor pieces and a capsule.
 * Copies of the same design are allowed - see DESIGN.md "중복 허용".
 */
public enum SuitType {
    RYAN_MK1("ryan_mk1", "RYAN MARK 1");

    private final String id;
    private final String hudName;

    SuitType(String id, String hudName) {
        this.id = id;
        this.hudName = hudName;
    }

    public String id() {
        return id;
    }

    public String hudName() {
        return hudName;
    }
}
