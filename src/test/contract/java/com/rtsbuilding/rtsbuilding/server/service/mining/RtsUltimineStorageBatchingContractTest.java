package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsUltimineStorageBatchingContractTest {
    @Test
    void ultimineDropAbsorptionBatchesStorageWorkPerTick() throws IOException {
        String processor = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsUltimineProcessor.java"));
        String absorber = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsDropAbsorber.java"));

        assertTrue(processor.contains("dropsToAbsorb")
                        && processor.contains("absorbMinedDropsBatch(player, session, dropsToAbsorb)"),
                "连锁挖掘每 tick 应先收集本 tick 破坏目标，再批量吸收掉落物。");
        assertFalse(processor.contains("absorbMinedDropsImmediately(player, session, target)"),
                "连锁挖掘处理队列时不能对每个目标重新解析储存并单点吸收。");
        assertTrue(absorber.contains("RtsStorageTickService.INSTANCE.getStorage(player)")
                        && absorber.contains("aggregate.insert(stack, false)")
                        && absorber.contains("absorbMinedDropsBatch"),
                "掉落吸收器应优先复用聚合储存缓存，并提供批量入口。");
    }

    @Test
    void ultimineMidBatchDirtyMarkDoesNotForceSynchronousStorageRefresh() throws IOException {
        String dropAbsorber = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsDropAbsorber.java"));
        String drainBody = methodBody(dropAbsorber, "public static int drainDropBuffer");

        assertTrue(dropAbsorber.contains("RtsStorageTickService.INSTANCE.alert(player.getUUID())"),
                "缓存写入储存后只应唤醒下一次储存 tick。");
        assertFalse(drainBody.contains("forceRefresh("),
                "缓存写回热路径不得同步刷新全部 linked storage。");
        assertFalse(drainBody.contains("afterModification("),
                "缓存写回热路径不得绕道触发同步 forceRefresh。");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int brace = source.indexOf('{', start);
        if (brace < 0) return "";
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') depth++;
            if (ch == '}' && --depth == 0) return source.substring(brace, i + 1);
        }
        return source.substring(brace);
    }
}
