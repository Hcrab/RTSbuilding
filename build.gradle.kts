plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// 该移植工作树继承自 1.12 分支，Git describe 会把旧标签和临时分支名拼进产物。
// 明确给首个 GTNH alpha 一个可识别版本；CI/发布时仍可用 -PrtsReleaseVersion 覆盖。
version = providers.gradleProperty("rtsReleaseVersion")
    .orElse("1.1.6-1.7.10-gtnh-alpha.1")
    .get()

minecraft {
    // 通过 -PrtsClientSmoke=true 启用无人值守真客户端烟测。普通 runClient 不受影响。
    if (providers.gradleProperty("rtsClientSmoke").orNull.equals("true", ignoreCase = true)) {
        val report = layout.buildDirectory
            .file("reports/rtsbuilding/client-startup-smoke.txt")
            .get().asFile.absolutePath
        extraRunJvmArguments.addAll(
            "-Drtsbuilding.clientStartupSmoke=true",
            "-Drtsbuilding.clientStartupSmokeReport=$report",
        )
    }
}

sourceSets {
    named("main") {
        java.srcDirs("src/main/java", "src/uiCore/java", "src/uiKit/java")
        // GTNH 使用 NEI；JEI API 与其专用 mixin 在 1.7.10 根本不存在。
        // 首版先隔离旧实现，后续由独立 NEI 适配器接回配方转移与 overlay 行为。
        java.exclude(
            "com/rtsbuilding/rtsbuilding/compat/jei/**",
            // Refined Storage 不存在于 GTNH；首版由 AE2/GT 存储适配承担主线验证。
            "com/rtsbuilding/rtsbuilding/compat/refinedstorage/**",
            "com/rtsbuilding/rtsbuilding/mixin/RecipeRegistryOverlayTransferMixin.java",
        )
    }
}

// 移植阶段必须看到完整 API 断层，而不是被 javac 默认的 100 条错误截断。
tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    // 首轮完整账本已经归档；后续用 2000 条滚动窗口避免诊断序列化挤爆 Gradle 堆。
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000", "-Xmaxwarns", "2000"))
}

// main/uiCore/uiKit 被合并进同一个编译源集；源码包只保留每个逻辑路径的一份副本。
tasks.named<org.gradle.jvm.tasks.Jar>("sourcesJar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
