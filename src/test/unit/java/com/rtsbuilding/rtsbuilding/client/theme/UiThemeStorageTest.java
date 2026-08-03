package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeBuiltins;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportUsesManagedDirectoryAndLoadKeepsBadFilesIsolated() throws Exception {
        UiThemeStorage storage = new UiThemeStorage(temporaryDirectory.resolve("themes"));
        UiThemeDefinition palette = UiThemeBuiltins.nordCommand();
        Path exported = storage.export(palette);
        assertEquals(storage.directory(), exported.getParent());
        assertTrue(Files.size(exported) > 0L);

        Files.writeString(storage.directory().resolve("broken.rts-theme.json"), "{broken");
        UiThemeRegistry registry = new UiThemeRegistry();
        assertEquals(2, storage.loadAll(registry).size());
        // 内置 ID 不能作为用户文件重新注册，因此合法内置导出也被严格拒绝。
        assertFalse(registry.contains(palette.id()));
    }

    @Test
    void oversizedExternalImportFailsBeforeJsonParsing() throws Exception {
        UiThemeStorage storage = new UiThemeStorage(temporaryDirectory.resolve("themes"));
        Path huge = temporaryDirectory.resolve("huge.json");
        Files.write(huge, new byte[UiThemeJsonCodec.MAX_BYTES + 1]);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> storage.importFile(huge, new UiThemeRegistry()))
                .getMessage().contains("1 MiB"));
    }

    @Test
    void builtInPaletteExportsAsReloadableUserCopy() throws Exception {
        UiThemeStorage storage = new UiThemeStorage(temporaryDirectory.resolve("themes"));
        storage.exportUserCopy(UiThemeBuiltins.nordCommand(), "user:nord_copy");
        UiThemeRegistry registry = UiThemeBuiltins.createRegistry();
        assertTrue(storage.loadAll(registry).isEmpty());
        assertTrue(registry.contains("user:nord_copy"));
    }
}
