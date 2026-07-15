package com.CodeCreature.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized debug logging utility gated by {@link FeatureFlags}.
 *
 * <p>Every log call checks two flags before producing output:
 * <ol>
 *   <li>{@code logging.global} — master switch for all debug logging</li>
 *   <li>{@code logging.<subsystem>} — per-subsystem switch</li>
 * </ol>
 * Both must be {@code true} for the message to be emitted.</p>
 *
 * <p>This class does <b>not</b> own any toggle state — all state lives in
 * {@link FeatureFlags}. It is purely a convenience layer that reads flags
 * and delegates to the appropriate logging backend.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   DebugLogger.log(Subsystem.STENCIL, Level.INFO, "Placed stencil at " + pos);
 *   DebugLogger.log(Subsystem.SCALING, Level.FINE, () -&gt; expensiveString());
 *   DebugLogger.chat(playerRef, Subsystem.PLUGIN, "§a[Debug] Active");
 * </pre>
 */
public final class DebugLogger {

    private static final Logger LOGGER = Logger.getLogger("DebugLogger");
    private static final HytaleLogger HYTALE_LOGGER = HytaleLogger.forEnclosingClass();

    private DebugLogger() {
        // Static utility — no instances
    }

    /**
     * Subsystems that can be independently toggled for debug logging.
     *
     * <p>Each value maps to a {@link FeatureFlags} key of the form
     * {@code logging.<name>} where {@code <name>} is the lowercase
     * enum name.</p>
     */
    public enum Subsystem {
        /** Core plugin lifecycle (setup, events, teardown). */
        PLUGIN,
        /** Stencil stencil placement, sync, and visuals. */
        STENCIL,
        /** Stencil book UI, recipes, affordability, particle loop and pick-stencil. */
        STENCIL_BOOK,
        /** Drop scaling and placement cost scaling. */
        SCALING,
        /** Recipe and resource type registries. */
        REGISTRY,
        /** Crafting recipe resolution and mutation. */
        CRAFTING,
        /** Ingredient tree grid and cost calculation. */
        INGREDIENT_TREE;

        /**
         * Returns the {@link FeatureFlags} key for this subsystem.
         *
         * @return key in the form {@code "logging.<lowercase_name>"}
         *         (e.g. {@code "logging.STENCIL_BOOK"})
         */
        public String flagKey() {
            return "logging." + this.name().toLowerCase();
        }
    }

    /**
     * Logs a message via {@code java.util.logging.Logger} if both the global
     * and subsystem flags are enabled.
     *
     * @param sub     the subsystem originating the message
     * @param level   JUL log level (e.g. {@link Level#INFO}, {@link Level#FINE})
     * @param message the message to log
     */
    public static void log(Subsystem sub, Level level, String message) {
        if (!isEnabled(sub)) return;
        LOGGER.log(level, message);
    }

    /**
     * Lazy logging variant — the supplier is only evaluated if logging is
     * enabled. Use for {@link Level#FINE} and below to avoid unnecessary
     * string construction.
     *
     * @param sub      the subsystem originating the message
     * @param level    JUL log level
     * @param supplier deferred message supplier
     */
    public static void log(Subsystem sub, Level level, Supplier<String> supplier) {
        if (!isEnabled(sub)) return;
        LOGGER.log(level, supplier);
    }

    /**
     * Logs via {@link HytaleLogger} if both the global and subsystem flags
     * are enabled.
     *
     * <p>Use this variant at call sites that were already using
     * {@code HytaleLogger} to maintain consistent log formatting.</p>
     *
     * @param sub    the subsystem originating the message
     * @param format format string (SLF4J-style {@code {}} placeholders)
     * @param args   format arguments
     */
    public static void logHytale(Subsystem sub, String format, Object... args) {
        if (!isEnabled(sub)) return;
        HYTALE_LOGGER.atInfo().log(format, args);
    }

    /**
     * Sends a chat message to a player if both the global and subsystem
     * flags are enabled.
     *
     * <p>Thread-safe: {@code playerRef.sendMessage()} is safe to call from
     * any thread per the codebase threading model.</p>
     *
     * @param playerRef the player to message
     * @param sub       the subsystem originating the message
     * @param message   the raw message string (may include {@code §} color codes)
     */
    public static void chat(PlayerRef playerRef, Subsystem sub, String message) {
        if (!isEnabled(sub)) return;
        playerRef.sendMessage(Message.raw(message));
    }

    /**
     * Checks whether debug logging is enabled for the given subsystem.
     *
     * <p>Returns {@code true} only if <b>both</b>:
     * <ul>
     *   <li>{@code FeatureFlags.get("logging.global")} is {@code true}</li>
     *   <li>{@code FeatureFlags.get(sub.flagKey())} is {@code true}</li>
     * </ul>
     *
     * @param sub the subsystem to check
     * @return {@code true} if logging is active for this subsystem
     */
    public static boolean isEnabled(Subsystem sub) {
        return FeatureFlags.get("logging.global") && FeatureFlags.get(sub.flagKey());
    }

    /**
     * Emits the current logging gate status for a subsystem without requiring
     * any feature flags to be enabled.
     *
     * <p>Use this for startup diagnostics so missing logs can be traced to flag
     * state instead of missing call sites.</p>
     *
     * @param sub     subsystem to inspect
     * @param context short context label (e.g. class or flow name)
     */
    public static void logGateStatus(Subsystem sub, String context) {
        Objects.requireNonNull(sub, "sub");
        String safeContext = context == null ? "unspecified" : context;
        boolean globalEnabled = FeatureFlags.get("logging.global");
        boolean subsystemEnabled = FeatureFlags.get(sub.flagKey());
        boolean enabled = globalEnabled && subsystemEnabled;

        LOGGER.log(Level.INFO, () ->
                "[Stencil Crafting][Diagnostics] Logging gate: context='" + safeContext +
                "' subsystem='" + sub.name() + "' global=" + globalEnabled +
                " subsystem_flag=" + subsystemEnabled + " enabled=" + enabled);
    }
}
