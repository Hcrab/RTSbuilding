package com.rtsbuilding.rtsbuilding.gametest;

import java.lang.reflect.Method;

/**
 * Mekanism Tools 1.12.2 外部矩阵入口。
 *
 * <p>旧 Mekanism Tools 的启动、注册表和假玩家生命周期必须由真实 Forge 服务端夹具提供。
 * 通过 {@code -Drtsbuilding.mekanism112.runnerClass=<class>} 指定夹具类；该类必须提供
 * {@code public static boolean run()}。未提供依赖或夹具时本入口明确失败，不把缺模组算作通过。</p>
 */
public final class MekanismToolsCompatibilityGameTests {
    public static final String REQUIRED_MOD_CLASS = "mekanism.tools.common.MekanismTools";
    public static final String RUNNER_PROPERTY = "rtsbuilding.mekanism112.runnerClass";
    public static final String SCENARIO = "osmiumPaxelAreaDestroyMinesStoneAndUnderwaterStone";

    private MekanismToolsCompatibilityGameTests() {}

    public static void main(String[] args) throws Exception {
        requireClass(REQUIRED_MOD_CLASS,
                "Mekanism Tools 1.12.2 未在测试 classpath；外部矩阵未执行");
        String runnerName = System.getProperty(RUNNER_PROPERTY, "").trim();
        if (runnerName.isEmpty()) {
            throw new IllegalStateException("BLOCKED " + SCENARIO
                    + ": 已检测到 Mekanism Tools，但未提供 Forge 1.12 服务端夹具属性 " + RUNNER_PROPERTY);
        }
        Class<?> runner = requireClass(runnerName, "指定的 Mekanism 1.12 测试夹具不存在");
        Method method = runner.getMethod("run");
        Object value = method.invoke(null);
        if (!(value instanceof Boolean) || !((Boolean) value).booleanValue()) {
            throw new AssertionError("FAILED " + SCENARIO + ": 外部夹具报告失败");
        }
        System.out.println("PASS " + SCENARIO + " (Mekanism Tools 1.12 external matrix)");
    }

    private static Class<?> requireClass(String name, String message) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, MekanismToolsCompatibilityGameTests.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new ClassNotFoundException("BLOCKED " + SCENARIO + ": " + message + " [" + name + "]", missing);
        }
    }
}
