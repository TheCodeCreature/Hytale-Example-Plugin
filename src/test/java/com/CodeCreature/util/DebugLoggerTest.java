package com.CodeCreature.util;

import com.CodeCreature.util.DebugLogger.Subsystem;
import com.hypixel.hytale.server.core.util.BsonUtil;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Behavioral tests for {@link DebugLogger}.
 *
 * <p>DebugLogger is a thin gating layer on top of {@link FeatureFlags}.
 * Tests focus on the {@code isEnabled()} contract and the {@code Subsystem}
 * enum's flag key mapping — NOT on log output or chat messages.</p>
 */
class DebugLoggerTest {

    private MockedStatic<BsonUtil> bsonUtilMock;

    @BeforeEach
    void setUp() {
        clearFeatureFlagsState();
        bsonUtilMock = mockStatic(BsonUtil.class);
        bsonUtilMock.when(() -> BsonUtil.readDocumentNow(any(Path.class))).thenReturn(null);
        bsonUtilMock.when(() -> BsonUtil.writeDocument(any(Path.class), any(BsonDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        FeatureFlags.initialize(Path.of("test-data"));
    }

    @AfterEach
    void tearDown() {
        bsonUtilMock.close();
        clearFeatureFlagsState();
    }

    private void clearFeatureFlagsState() {
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

    // ── Subsystem.flagKey() ──────────────────────────────────────────

    @Nested
    class SubsystemFlagKey {

        @Test
        void pluginSubsystemKey() {
            assertEquals("logging.plugin", Subsystem.PLUGIN.flagKey());
        }

        @Test
        void stencilSubsystemKey() {
            assertEquals("logging.stencil", Subsystem.STENCIL.flagKey());
        }

        @Test
        void blueprintBookSubsystemKey() {
            assertEquals("logging.blueprint_book", Subsystem.BLUEPRINT_BOOK.flagKey());
        }

        @Test
        void scalingSubsystemKey() {
            assertEquals("logging.scaling", Subsystem.SCALING.flagKey());
        }

        @Test
        void registrySubsystemKey() {
            assertEquals("logging.registry", Subsystem.REGISTRY.flagKey());
        }

        @Test
        void craftingSubsystemKey() {
            assertEquals("logging.crafting", Subsystem.CRAFTING.flagKey());
        }

        @Test
        void ingredientTreeSubsystemKey() {
            assertEquals("logging.ingredient_tree", Subsystem.INGREDIENT_TREE.flagKey());
        }

        @Test
        void allSubsystemsProduceLoggingPrefixedKeys() {
            for (Subsystem sub : Subsystem.values()) {
                assertTrue(sub.flagKey().startsWith("logging."),
                        sub.name() + ".flagKey() must start with 'logging.'");
                assertEquals("logging." + sub.name().toLowerCase(), sub.flagKey(),
                        sub.name() + ".flagKey() must be lowercase enum name");
            }
        }
    }

    // ── isEnabled() behavior ─────────────────────────────────────────

    @Nested
    class WhenBothGlobalAndSubsystemAreOn {

        @BeforeEach
        void enableAll() {
            FeatureFlags.set("logging.global", true);
            for (Subsystem sub : Subsystem.values()) {
                FeatureFlags.set(sub.flagKey(), true);
            }
        }

        @Test
        void isEnabledReturnsTrue() {
            assertTrue(DebugLogger.isEnabled(Subsystem.STENCIL),
                    "isEnabled() should be true when both global and subsystem flags are on");
        }

        @Test
        void isEnabledReturnsTrueForAllSubsystems() {
            for (Subsystem sub : Subsystem.values()) {
                assertTrue(DebugLogger.isEnabled(sub),
                        "isEnabled(" + sub.name() + ") should be true when explicitly enabled");
            }
        }
    }

    @Nested
    class WhenGlobalFlagIsOff {

        @BeforeEach
        void disableGlobal() {
            FeatureFlags.set("logging.global", false);
        }

        @Test
        void isEnabledReturnsFalse() {
            assertFalse(DebugLogger.isEnabled(Subsystem.STENCIL),
                    "isEnabled() must be false when logging.global is off");
        }

        @Test
        void isEnabledReturnsFalseForAllSubsystems() {
            for (Subsystem sub : Subsystem.values()) {
                assertFalse(DebugLogger.isEnabled(sub),
                        "isEnabled(" + sub.name() + ") must be false when global is off");
            }
        }
    }

    @Nested
    class WhenSubsystemFlagIsOff {

        @BeforeEach
        void enableAllThenDisableStencil() {
            FeatureFlags.set("logging.global", true);
            for (Subsystem sub : Subsystem.values()) {
                FeatureFlags.set(sub.flagKey(), true);
            }
            FeatureFlags.set("logging.stencil", false);
        }

        @Test
        void isEnabledReturnsFalseForThatSubsystem() {
            assertFalse(DebugLogger.isEnabled(Subsystem.STENCIL),
                    "isEnabled() must be false when subsystem flag is off");
        }

        @Test
        void isEnabledReturnsTrueForOtherSubsystems() {
            // Other subsystems are still on
            assertTrue(DebugLogger.isEnabled(Subsystem.PLUGIN),
                    "isEnabled(PLUGIN) should still be true when only STENCIL is off");
        }
    }

    @Nested
    class WhenBothGlobalAndSubsystemAreOff {

        @Test
        void isEnabledReturnsFalse() {
            FeatureFlags.set("logging.global", false);
            FeatureFlags.set("logging.scaling", false);

            assertFalse(DebugLogger.isEnabled(Subsystem.SCALING),
                    "isEnabled() must be false when both flags are off");
        }
    }
}
