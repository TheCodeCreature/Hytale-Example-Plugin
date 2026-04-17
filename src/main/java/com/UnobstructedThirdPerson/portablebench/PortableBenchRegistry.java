package com.UnobstructedThirdPerson.portablebench;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry mapping item IDs to their portable bench configurations.
 * Thread-safe — configs are registered during plugin setup and read during interactions.
 */
public final class PortableBenchRegistry {

    private static final Map<String, PortableBenchConfig> CONFIGS = new ConcurrentHashMap<>();

    private PortableBenchRegistry() {
    }

    public static void register(String itemId, PortableBenchConfig config) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be null or blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        CONFIGS.put(itemId, config);
    }

    public static PortableBenchConfig getConfig(String itemId) {
        return itemId == null ? null : CONFIGS.get(itemId);
    }

    public static boolean hasConfig(String itemId) {
        return itemId != null && CONFIGS.containsKey(itemId);
    }

    public static int size() {
        return CONFIGS.size();
    }

    public static void clear() {
        CONFIGS.clear();
    }
}
