package com.CodeCreature.util;

import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Behavioral tests for {@link FeatureFlags}.
 *
 * <p>FeatureFlags is a static singleton with disk persistence via BsonUtil.
 * Tests mock BsonUtil to avoid filesystem I/O and use reflection to reset
 * the static map between tests, ensuring isolation.</p>
 */
class FeatureFlagsTest {

    private MockedStatic<BsonUtil> bsonUtilMock;

    @BeforeEach
    void setUp() {
        clearStaticState();
        bsonUtilMock = mockStatic(BsonUtil.class);
        // readDocumentNow returns null → no pre-existing file
        bsonUtilMock.when(() -> BsonUtil.readDocumentNow(any(Path.class))).thenReturn(null);
        // writeDocument is a no-op
        bsonUtilMock.when(() -> BsonUtil.writeDocument(any(Path.class), any(BsonDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() {
        bsonUtilMock.close();
        clearStaticState();
    }

    /** Resets the private static {@code flags} map and {@code filePath} between tests. */
    private void clearStaticState() {
        try {
            Field flagsField = FeatureFlags.class.getDeclaredField("flags");
            flagsField.setAccessible(true);
            ((ConcurrentHashMap<?, ?>) flagsField.get(null)).clear();

            Field filePathField = FeatureFlags.class.getDeclaredField("filePath");
            filePathField.setAccessible(true);
            filePathField.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reset FeatureFlags static state", e);
        }
    }

    // ── get() behavior ───────────────────────────────────────────────

    @Nested
    class WhenKeyIsUnregistered {

        @Test
        void getReturnsFalse_failClosed() {
            // Unregistered keys default to false — logging is off unless explicitly enabled
            assertFalse(FeatureFlags.get("nonexistent.key"),
                    "Unregistered flag must return false (fail-closed policy)");
        }
    }

    // ── register() behavior ──────────────────────────────────────────

    @Nested
    class WhenRegisteringFlags {

        @Test
        void registerSetsDefaultValue() {
            FeatureFlags.register("test.flag", false);

            assertFalse(FeatureFlags.get("test.flag"),
                    "Registered flag should return its default value");
        }

        @Test
        void registerDoesNotOverwriteExistingValue() {
            FeatureFlags.register("test.flag", false);
            FeatureFlags.register("test.flag", true);

            assertFalse(FeatureFlags.get("test.flag"),
                    "Second register() must not overwrite the first value");
        }

        @Test
        void registeredValueIsReturnedByGet() {
            FeatureFlags.register("test.enabled", true);
            FeatureFlags.register("test.disabled", false);

            assertTrue(FeatureFlags.get("test.enabled"));
            assertFalse(FeatureFlags.get("test.disabled"));
        }
    }

    // ── set() behavior ───────────────────────────────────────────────

    @Nested
    class WhenSettingFlags {

        @Test
        void setChangesExistingFlagValue() {
            FeatureFlags.register("test.flag", true);

            FeatureFlags.set("test.flag", false);

            assertFalse(FeatureFlags.get("test.flag"),
                    "set() should change the flag to the new value");
        }

        @Test
        void setCreatesUnregisteredKey() {
            FeatureFlags.set("new.flag", false);

            assertFalse(FeatureFlags.get("new.flag"),
                    "set() on an unregistered key should create it with the given value");
        }
    }

    // ── toggle() behavior ────────────────────────────────────────────

    @Nested
    class WhenTogglingFlags {

        @Test
        void toggleFlipsTrueToFalse() {
            FeatureFlags.register("test.flag", true);

            boolean result = FeatureFlags.toggle("test.flag");

            assertFalse(result, "toggle() should flip true → false");
            assertFalse(FeatureFlags.get("test.flag"));
        }

        @Test
        void toggleFlipsFalseToTrue() {
            FeatureFlags.register("test.flag", false);

            boolean result = FeatureFlags.toggle("test.flag");

            assertTrue(result, "toggle() should flip false → true");
            assertTrue(FeatureFlags.get("test.flag"));
        }

        @Test
        void toggleReturnsNewValue() {
            FeatureFlags.register("test.flag", true);

            boolean returned = FeatureFlags.toggle("test.flag");

            assertEquals(FeatureFlags.get("test.flag"), returned,
                    "toggle() return value must match the current flag state");
        }

        @Test
        void toggleOnUnregisteredKeyCreatesWithDefaultFalseThenFlips() {
            // Unregistered keys get computeIfAbsent(k, k -> new AtomicBoolean(false))
            // then immediately toggled → result should be true
            boolean result = FeatureFlags.toggle("unknown.flag");

            assertTrue(result,
                    "toggle() on unregistered key: default=false → flipped to true");
            assertTrue(FeatureFlags.get("unknown.flag"));
        }
    }

    // ── getAll() behavior ────────────────────────────────────────────

    @Nested
    class WhenGettingAllFlags {

        @Test
        void getAllReturnsAllRegisteredFlags() {
            FeatureFlags.register("alpha", true);
            FeatureFlags.register("beta", false);

            Map<String, Boolean> all = FeatureFlags.getAll();

            assertEquals(2, all.size(), "getAll() should contain exactly the registered flags");
            assertTrue(all.get("alpha"));
            assertFalse(all.get("beta"));
        }

        @Test
        void getAllReturnsUnmodifiableMap() {
            FeatureFlags.register("test.flag", true);

            Map<String, Boolean> all = FeatureFlags.getAll();

            assertThrows(UnsupportedOperationException.class,
                    () -> all.put("injected", true),
                    "getAll() map must be unmodifiable");
        }

        @Test
        void getAllReflectsLatestState() {
            FeatureFlags.register("test.flag", true);
            FeatureFlags.set("test.flag", false);

            Map<String, Boolean> all = FeatureFlags.getAll();

            assertFalse(all.get("test.flag"),
                    "getAll() snapshot must reflect the most recent set() value");
        }
    }

    // ── initialize() behavior ────────────────────────────────────────

    @Nested
    class WhenInitializing {

        @Test
        void initializeRegistersAllDefaults() {
            FeatureFlags.initialize(Path.of("test-data"));

            Map<String, Boolean> all = FeatureFlags.getAll();

            // Verify all default flags from registerDefaults() are present
            assertTrue(all.containsKey("logging.global"), "Must register logging.global");
            assertTrue(all.containsKey("logging.plugin"), "Must register logging.plugin");
            assertTrue(all.containsKey("logging.stencil"), "Must register logging.stencil");
            assertTrue(all.containsKey("logging.stencil_book"), "Must register logging.stencil_book");
            assertTrue(all.containsKey("logging.scaling"), "Must register logging.scaling");
            assertTrue(all.containsKey("logging.registry"), "Must register logging.registry");
            assertTrue(all.containsKey("logging.crafting"), "Must register logging.crafting");
            assertTrue(all.containsKey("logging.ingredient_tree"), "Must register logging.ingredient_tree");
            assertTrue(all.containsKey("diagnostics.breakLog"), "Must register diagnostics.breakLog");
        }

        @Test
        void initializeSetsLoggingDefaultsToFalse() {
            FeatureFlags.initialize(Path.of("test-data"));

            assertFalse(FeatureFlags.get("logging.global"), "logging.global default should be false");
            assertFalse(FeatureFlags.get("logging.plugin"), "logging.plugin default should be false");
        }

        @Test
        void initializeSetsDiagnosticsBreakLogToFalse() {
            FeatureFlags.initialize(Path.of("test-data"));

            assertFalse(FeatureFlags.get("diagnostics.breakLog"),
                    "diagnostics.breakLog default should be false");
        }
    }
}
