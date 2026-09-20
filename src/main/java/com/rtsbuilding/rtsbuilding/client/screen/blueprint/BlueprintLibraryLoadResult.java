package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;

import java.nio.file.Path;

/**
 * 后台蓝图库任务发给客户端 owner 的轻量结果。
 * 后台不携带 ItemStack、翻译组件或 Minecraft 对象；这些对象只在 client pump 创建。
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

    static BlueprintLibraryLoadResult entry(long generation, Path path, String fileName,
            long fileRevision, RtsBlueprint blueprint) {
        return new BlueprintLibraryLoadResult(generation, path, fileName, fileRevision,
                blueprint, BlueprintMaterialAnalysis.analyze(blueprint), "", false, "");
    }

    static BlueprintLibraryLoadResult error(long generation, Path path, String fileName,
            long fileRevision, String error) {
        return new BlueprintLibraryLoadResult(generation, path, fileName, fileRevision,
                null, null, error, false, "");
    }

    static BlueprintLibraryLoadResult complete(long generation, String scanError) {
        return new BlueprintLibraryLoadResult(generation, null, "", 0L, null, null,
                "", true, scanError);
    }
}
