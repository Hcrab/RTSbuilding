package com.rtsbuilding.rtsbuilding.gametest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class FabricGameTestEntrypointContractTest {
    @Test
    void everyRegisteredGameTestEntrypointHasAPublicNoArgConstructor() throws Exception {
        List<Class<?>> entrypoints = List.of(
                RtsServerGameTests.class,
                RtsBatchStorageGameTests.class,
                RtsConvenienceDestroyGameTests.class,
                RtsCrossDimensionStorageGameTests.class,
                RtsRemoteMenuGameTests.class,
                RtsSmartFillGameTests.class,
                RtsOptimizationSuiteGameTests.class,
                CreateBlueprintCompatibilityGameTests.class,
                MekanismToolsCompatibilityGameTests.class);

        for (Class<?> entrypoint : entrypoints) {
            Constructor<?> constructor = entrypoint.getDeclaredConstructor();
            assertTrue(
                    Modifier.isPublic(entrypoint.getModifiers()),
                    () -> entrypoint.getName() + " must remain a public entrypoint class");
            assertTrue(
                    Modifier.isPublic(constructor.getModifiers()),
                    () -> entrypoint.getName() + " must expose a public no-arg constructor");
        }
    }
}
