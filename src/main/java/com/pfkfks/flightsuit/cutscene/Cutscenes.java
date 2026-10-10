package com.pfkfks.flightsuit.cutscene;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every cutscene script by id (M17). Scripts are code, loaded the first time anything asks - on both sides alike, so
 * the client can follow the server's scene frame by frame. The Dragon Ball story's scenes: planet.dbz.DbzCutscenes.
 */
public final class Cutscenes {
    private static final Map<String, Cutscene> ALL = new LinkedHashMap<>();
    private static boolean loaded;

    private Cutscenes() {
    }

    private static synchronized void load() {
        if (!loaded) {
            loaded = true;
            com.pfkfks.flightsuit.planet.dbz.DbzCutscenes.registerAll();
        }
    }

    public static synchronized void register(Cutscene scene) {
        ALL.put(scene.id(), scene);
    }

    public static @Nullable Cutscene get(String id) {
        load();
        return ALL.get(id);
    }

    public static Collection<String> ids() {
        load();
        return Collections.unmodifiableCollection(ALL.keySet());
    }
}
