package com.pfkfks.flightsuit.suit;

/** Continuous movement pose, decided by the server and broadcast so every client draws the same body pose and thruster fire. */
public enum FlightPose {
    NONE(""),
    HOVER("hover"),
    BOOST("boost"),
    THRUST("hover");

    private final String animationName;

    FlightPose(String animationName) {
        this.animationName = animationName;
    }

    public String animationName() {
        return animationName;
    }

    public static FlightPose byId(int id) {
        FlightPose[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
