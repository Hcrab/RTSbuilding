package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.blueprint.RtsBlueprint;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仓储的内存索引必须稳定排序，并按文件名替换捕获保存结果。
 */
class BlueprintLibraryRepositoryTest {
    @Test
    void addOrReplaceSortsCaseInsensitivelyAndFindsByFileName() {
        BlueprintLibraryRepository repository = new BlueprintLibraryRepository();
        RtsBlueprint zeta = emptyBlueprint("zeta");
        RtsBlueprint alpha = emptyBlueprint("alpha");

        repository.addOrReplace(Path.of("Zeta.nbt"), zeta);
        repository.addOrReplace(Path.of("alpha.nbt"), alpha);

        assertEquals(2, repository.size());
        assertEquals("alpha.nbt", repository.get(0).fileName());
        assertEquals("Zeta.nbt", repository.get(1).fileName());
        assertSame(alpha, repository.findByFileName("alpha.nbt").blueprint());
        assertEquals(1, repository.indexOfFileName("Zeta.nbt"));
        assertNull(repository.findByFileName(null));
        assertEquals(-1, repository.indexOfFileName("missing.nbt"));
    }

    @Test
    void addOrReplaceRemovesThePreviousEntryWithTheSameFileName() {
        BlueprintLibraryRepository repository = new BlueprintLibraryRepository();
        RtsBlueprint first = emptyBlueprint("first");
        RtsBlueprint replacement = emptyBlueprint("replacement");

        repository.addOrReplace(Path.of("same.nbt"), first);
        repository.addOrReplace(Path.of("same.nbt"), replacement);

        assertEquals(1, repository.size());
        assertSame(replacement, repository.get(0).blueprint());
        assertEquals(List.of(repository.get(0)), repository.copyEntries());
    }

    @Test
    void invalidSaveResultDoesNotMutateTheRepository() {
        BlueprintLibraryRepository repository = new BlueprintLibraryRepository();

        repository.addOrReplace(null, emptyBlueprint("ignored"));
        repository.addOrReplace(Path.of("ignored.nbt"), null);

        assertTrue(repository.isEmpty());
    }

    private static RtsBlueprint emptyBlueprint(String name) {
        return RtsBlueprint.create(
                name,
                name + ".nbt",
                BlueprintFormat.VANILLA_NBT,
                Vec3i.ZERO,
                List.of());
    }
}
