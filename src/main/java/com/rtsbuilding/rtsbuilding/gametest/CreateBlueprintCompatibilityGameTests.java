package com.rtsbuilding.rtsbuilding.gametest;

import java.lang.reflect.Method;

/**
 * Create 蓝图兼容矩阵的条件入口。
 *
 * <p>Create 没有官方 Minecraft 1.12.2 版本，因此主移植不能声称原矩阵已经运行。若维护者
 * 选择某个非官方 backport，需显式传入 backport 标志类和服务端夹具类：</p>
 * <ul>
 *   <li>{@code -Drtsbuilding.create112.markerClass=<backport marker>}</li>
 *   <li>{@code -Drtsbuilding.create112.runnerClass=<fixture class>}</li>
 * </ul>
 * <p>夹具类必须提供 {@code public static boolean run()}，并负责真实世界中的 vault 控制器重建、
 * belt 运行时拓扑清理以及方块实体 NBT 检查。缺任一条件都会明确失败。</p>
 */
public final class CreateBlueprintCompatibilityGameTests {
    public static final String MARKER_PROPERTY = "rtsbuilding.create112.markerClass";
    public static final String RUNNER_PROPERTY = "rtsbuilding.create112.runnerClass";
    public static final String[] SCENARIOS = {
            "createVaultBlueprintRebuildsControllerAtPlacement",
            "createBeltBlueprintDropsStaleRuntimeTopology"
    };

    private CreateBlueprintCompatibilityGameTests() {}

    public static void main(String[] args) throws Exception {
        String markerName = System.getProperty(MARKER_PROPERTY, "").trim();
        if (markerName.isEmpty()) {
            throw new IllegalStateException("BLOCKED Create 1.12 matrix: Create 无官方 1.12.2 版本；"
                    + "必须明确选择 backport 并设置 " + MARKER_PROPERTY);
        }
        requireClass(markerName, "指定的 Create 1.12 backport 标志类不存在");
        String runnerName = System.getProperty(RUNNER_PROPERTY, "").trim();
        if (runnerName.isEmpty()) {
            throw new IllegalStateException("BLOCKED Create 1.12 matrix: 未设置真实服务端夹具 " + RUNNER_PROPERTY);
        }
        Class<?> runner = requireClass(runnerName, "指定的 Create 1.12 测试夹具不存在");
        Method method = runner.getMethod("run");
        Object value = method.invoke(null);
        if (!(value instanceof Boolean) || !((Boolean) value).booleanValue()) {
            throw new AssertionError("FAILED Create 1.12 external matrix: fixture reported failure");
        }
        for (String scenario : SCENARIOS) System.out.println("PASS " + scenario + " (explicit Create 1.12 backport)");
    }

    private static Class<?> requireClass(String name, String message) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, CreateBlueprintCompatibilityGameTests.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new ClassNotFoundException("BLOCKED Create 1.12 matrix: " + message + " [" + name + "]", missing);
        }
    }
}
