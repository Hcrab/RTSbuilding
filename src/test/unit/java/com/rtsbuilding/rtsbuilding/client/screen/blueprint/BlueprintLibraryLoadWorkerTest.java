package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.io.BlueprintWriters;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.test.MinecraftTestBootstrapExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真正执行 Forge 侧后台文件读取/解压/解析，不启动客户端、服务器、窗口或世界。 */
@ExtendWith(MinecraftTestBootstrapExtension.class)
class BlueprintLibraryLoadWorkerTest {
    @TempDir
    Path folder;

    @Test
    void workerParsesRealNbtPreservesMissingBlocksAndContinuesAfterBadFile() throws Exception {
        RtsBlueprint blueprint = RtsBlueprint.create("sample", "b-valid.nbt",
                BlueprintFormat.VANILLA_NBT, new Vec3i(3, 1, 1), List.of(
                        new RtsBlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(),
                                new CompoundTag()),
                        RtsBlueprintBlock.missing(new BlockPos(2, 0, 0), "example:missing",
                                new CompoundTag())));
        Files.write(folder.resolve("a-broken.nbt"), new byte[] {1, 2, 3});
        BlueprintWriters.writeVanillaStructure(blueprint, folder.resolve("b-valid.nbt"));
        var results = new ArrayBlockingQueue<BlueprintLibraryLoadResult>(2);
        var load = BlueprintLibraryLoadWorker.submit(7, folder,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                new ConcurrentHashMap<>(), results);
        try {
            BlueprintLibraryLoadResult bad = results.poll(5, TimeUnit.SECONDS);
            assertNotNull(bad);
            assertFalse(bad.error().isBlank());
            assertEquals("a-broken.nbt", bad.fileName());
            BlueprintLibraryLoadResult good = results.poll(5, TimeUnit.SECONDS);
            assertNotNull(good);
            assertEquals("", good.error());
            assertEquals(2, good.blueprint().blockCount());
            assertEquals(Blocks.STONE.defaultBlockState(), good.blueprint().blocks().get(0).state());
            assertTrue(good.blueprint().blocks().get(1).isMissingBlock());
            assertNotNull(good.materialAnalysis());
            BlueprintLibraryLoadResult complete = results.poll(5, TimeUnit.SECONDS);
            assertNotNull(complete);
            assertTrue(complete.complete());
            assertEquals(7, complete.generation());
        } finally {
            load.cancel();
        }
    }
}
