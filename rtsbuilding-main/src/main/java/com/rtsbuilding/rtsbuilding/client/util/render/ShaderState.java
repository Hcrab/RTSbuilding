package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Shader pack (光影) detection utility.
 *
 * <p>Detects whether an external shader pack is currently active (Iris / OptiFine)
 * via reflection plus config-file inspection, so the module has no hard
 * dependency on either loader.
 * Shader packs take over the world rendering pipeline and turn the mod's
 * unlit overlay draws (boundary walls, wireframes, filled faces) into lit,
 * shadow-sampled geometry, which makes them look like dark/shadow artifacts.
 * Rendering passes can query this state to adapt their behaviour.</p>
 */
public final class ShaderState {

    private ShaderState() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cache period before re-checking the shader state (ms). */
    private static final long CACHE_MS = 1000;

    private static long lastCheck;
    private static boolean cachedActive;
    private static boolean cacheValid;

    /**
     * @return {@code true} if an external shader pack (Iris or OptiFine) is active.
     */
    public static boolean isShaderPackActive() {
        long now = System.currentTimeMillis();
        if (!cacheValid || now - lastCheck > CACHE_MS) {
            boolean active = detect();
            if (active != cachedActive) {
                LOGGER.info("[RTS-Building] Shader pack detection: {} (Iris API: {}, iris.properties: {}, OptiFine: {})",
                    active ? "ACTIVE" : "inactive",
                    irisApiActive(), irisPropertiesActive(), optifineActive());
            }
            cachedActive = active;
            lastCheck = now;
            cacheValid = true;
        }
        return cachedActive;
    }

    /**
     * @return {@code true} if Iris is present and a shader pack is in use.
     */
    public static boolean isIrisActive() {
        if (!isShaderPackActive()) return false;
        return isIrisPresent();
    }

    private static boolean irisPresent;

    private static boolean isIrisPresent() {
        if (irisPresent) return true;
        try {
            Class.forName("net.irisshaders.iris.api.v1.IrisApi");
            irisPresent = true;
        } catch (Throwable ignored) {
            irisPresent = false;
        }
        return irisPresent;
    }

    private static boolean detect() {
        return irisApiActive() || irisPropertiesActive() || optifineActive();
    }

    /**
     * Iris runtime API: net.irisshaders.iris.api.v1.IrisApi.getInstance().isShaderPackInUse()
     */
    private static boolean irisApiActive() {
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v1.IrisApi");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            Object active = apiClass.getMethod("isShaderPackInUse").invoke(instance);
            return Boolean.TRUE.equals(active);
        } catch (Throwable ignored) {
            // Iris not present or API changed
            return false;
        }
    }

    /**
     * Iris config file: config/iris.properties
     *
     * <p>Does not depend on Iris classes being loadable, so it keeps working
     * even if reflection fails. Iris writes this file on every launch:</p>
     * <pre>
     * enableShaders=true
     * shaderPackName=ComplementaryReimagined_r5.3.1.zip
     * </pre>
     * An empty pack name or {@code (internal)} means "no pack selected".
     */
    private static boolean irisPropertiesActive() {
        try {
            Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
            Path props = configDir.resolve("iris.properties");
            if (!Files.isRegularFile(props)) return false;
            List<String> lines = Files.readAllLines(props);
            boolean enableShaders = true;
            String packName = null;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("enableShaders=")) {
                    enableShaders = Boolean.parseBoolean(trimmed.substring("enableShaders=".length()));
                } else if (trimmed.startsWith("shaderPackName=")) {
                    packName = trimmed.substring("shaderPackName=".length());
                }
            }
            return enableShaders && packName != null
                && !packName.isEmpty()
                && !"(internal)".equals(packName)
                && !"off".equalsIgnoreCase(packName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * OptiFine: optifine.Config.isShaders()
     */
    private static boolean optifineActive() {
        try {
            Class<?> configClass = Class.forName("optifine.Config");
            Object active = configClass.getMethod("isShaders").invoke(null);
            return Boolean.TRUE.equals(active);
        } catch (Throwable ignored) {
            // OptiFine not present
            return false;
        }
    }
}
