package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;

import java.nio.file.Path;

/**
 * 后台蓝图库扫描器发给客户端 owner 的轻量结果。
 *
 * <p>这里故意不携带 {@code ItemStack}、翻译组件或 {@code Minecraft} 对象。
 * 后台线程只负责文件读取和使用冻结注册表解析 {@link RtsBlueprint}；
 * {@link BlueprintEntry#from(Path, String, RtsBlueprint, String)} 必须留在
 * 客户端 pump 中执行。</p>
 */
record BlueprintLibraryLoadResult(
        long generation,
        Path path,
        String fileName,
        long fileRevision,
        RtsBlueprint blueprint,
        BlueprintMaterialAnalysis materialAnalysis,
        String error,
        boolean complete,
        String scanError) {

    BlueprintLibraryLoadResult {
        fileName = fileName == null ? "" : fileName;
        error = error == null ? "" : error;
        scanError = scanError == null ? "" : scanError;
    }

    static BlueprintLibraryLoadResult entry(
            long generation,
            Path path,
            String fileName,
            long fileRevision,
            RtsBlueprint blueprint) {
        return new BlueprintLibraryLoadResult(
                generation, path, fileName, fileRevision, blueprint,
                BlueprintMaterialAnalysis.analyze(blueprint), "", false, "");
    }

    static BlueprintLibraryLoadResult error(
            long generation,
            Path path,
            String fileName,
            long fileRevision,
            String error) {
        return new BlueprintLibraryLoadResult(
                generation, path, fileName, fileRevision, null, null, error, false, "");
    }

    static BlueprintLibraryLoadResult complete(long generation, String scanError) {
        return new BlueprintLibraryLoadResult(
                generation, null, "", 0L, null, null, "", true, scanError);
    }
}
