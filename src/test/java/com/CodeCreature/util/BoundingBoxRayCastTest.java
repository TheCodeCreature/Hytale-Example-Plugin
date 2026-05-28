package com.CodeCreature.util;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.iterator.BlockIterator;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoundingBoxRayCastTest {

    @Test
    void getTargetBlockFromEntityThrowsWhenStoreHasNoWorldContext() {
        Ref<EntityStore> ref = mock(Ref.class);
        ComponentAccessor<EntityStore> store = mock(ComponentAccessor.class, RETURNS_DEEP_STUBS);
        Transform look = mock(Transform.class);

        when(look.getPosition()).thenReturn(new Vector3d(1.0, 2.0, 3.0));
        when(look.getDirection()).thenReturn(new Vector3d(0.0, 0.0, 1.0));

        try (MockedStatic<TargetUtil> targetUtil = mockStatic(TargetUtil.class)) {

            targetUtil.when(() -> TargetUtil.getLook(ref, store)).thenReturn(look);
            assertThrows(NullPointerException.class,
                    () -> BoundingBoxRayCast.getTargetBlock(ref, 8.0, store));
            targetUtil.verify(() -> TargetUtil.getLook(ref, store));
        }
    }

    @Test
    void getTargetBlockFromRayReturnsNullWhenOnlyAirIsTraversed() {
        World world = mock(World.class);
        BlockType airBlock = mock(BlockType.class);
        when(airBlock.getId()).thenReturn("Empty");
        when(world.getBlockType(2, 64, 3)).thenReturn(airBlock);

        try (MockedStatic<BlockIterator> blockIterator = mockStatic(BlockIterator.class)) {
            blockIterator.when(() -> BlockIterator.iterate(
                    anyDouble(), anyDouble(), anyDouble(),
                    anyDouble(), anyDouble(), anyDouble(),
                    anyDouble(), any()))
                    .thenAnswer(invocation -> {
                        Object visitor = invocation.getArgument(7);
                        invokeVisitor(visitor, 2, 64, 3);
                        return null;
                    });

            Vector3i result = BoundingBoxRayCast.getTargetBlock(world, 0.0, 64.0, 0.0, 1.0, 0.0, 0.0, 8.0);

            assertNull(result);
            verify(world).getBlockType(2, 64, 3);
        }
    }

    private static void invokeVisitor(Object visitor, int x, int y, int z) throws Exception {
        Method method = Arrays.stream(visitor.getClass().getMethods())
                .filter(m -> m.getParameterCount() == 9 && m.getReturnType() == boolean.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not locate BlockIterator callback"));

        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[9];
        int[] values = {x, y, z, 0, 0, 0, 0, 0, 0};

        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = convert(values[i], parameterTypes[i]);
        }

        method.invoke(visitor, args);
    }

    private static Object convert(int value, Class<?> type) {
        if (type == int.class || type == Integer.class) return value;
        if (type == float.class || type == Float.class) return (float) value;
        if (type == double.class || type == Double.class) return (double) value;
        if (type == long.class || type == Long.class) return (long) value;
        return value;
    }
}
