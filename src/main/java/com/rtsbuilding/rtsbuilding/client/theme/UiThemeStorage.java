package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRegistry;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 受管主题目录、原子导入/导出和活动主题选择的文件边界。 */
public final class UiThemeStorage {
    public static final String THEME_SUFFIX = ".rts-theme.json";
    private static final String ACTIVE_FILE = "active-theme.txt";
    private final Path directory;
    private final UiThemeJsonCodec codec = new UiThemeJsonCodec();

    public UiThemeStorage(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory must not be null");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static UiThemeStorage defaultStorage() { return DefaultHolder.INSTANCE; }
    public Path directory() { return this.directory; }

    public List<String> loadAll(UiThemeRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        List<String> errors = new ArrayList<>();
        try {
            Files.createDirectories(this.directory);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(this.directory, "*" + THEME_SUFFIX)) {
                for (Path file : files) {
                    try {
                        registry.registerOrReplaceUser(this.codec.decode(readBounded(file)));
                    } catch (RuntimeException | IOException failure) {
                        errors.add(file.getFileName() + ": " + failure.getMessage());
                    }
                }
            }
        } catch (IOException failure) {
            errors.add(this.directory + ": " + failure.getMessage());
        }
        return Collections.unmodifiableList(errors);
    }

    public UiThemeDefinition importFile(Path external, UiThemeRegistry registry) throws IOException {
        if (external == null) throw new IllegalArgumentException("external must not be null");
        UiThemeDefinition definition = this.codec.decode(readBounded(external));
        registry.registerOrReplaceUser(definition);
        writeAtomically(themePath(definition.id()), this.codec.encode(definition));
        return definition;
    }

    public Path export(UiThemeDefinition definition) throws IOException {
        Path target = themePath(definition.id());
        writeAtomically(target, this.codec.encode(definition));
        return target;
    }

    /** 将内建 Palette 主题另存为合法 user id，保证再次导入不会覆盖内建主题。 */
    public Path exportUserCopy(UiThemeDefinition source, String userId) throws IOException {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        EnumMap<com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeCoverageCatalog.ComponentFamily,
                Map<com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken,
                        com.rtsbuilding.rtsbuilding.uikit.theme.UiColor>> components =
                new EnumMap<>(com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeCoverageCatalog.ComponentFamily.class);
        components.putAll(source.components());
        return export(new UiThemeDefinition(userId, source.nameKey(), source.author(),
                source.descriptionKey(), source.renderMode(), source.textureSet(), true,
                source.tokens(), components));
    }

    public void saveActiveId(String id) throws IOException {
        if (id == null || id.trim().isEmpty() || id.length() > 128) throw new IllegalArgumentException("invalid active theme id");
        writeAtomically(this.directory.resolve(ACTIVE_FILE), id + System.lineSeparator());
    }

    public void restoreActiveTheme() {
        Path active = this.directory.resolve(ACTIVE_FILE);
        if (!Files.isRegularFile(active)) return;
        try {
            String id = readBounded(active).trim();
            if (UiThemeRuntime.registry().contains(id)) UiThemeRuntime.manager().activate(id);
        } catch (RuntimeException | IOException failure) {
            RtsbuildingMod.LOGGER.warn("读取活动 UI 主题失败，继续使用 Legacy：{}", active, failure);
            UiThemeRuntime.manager().fallBackToLegacy();
        }
    }

    private Path themePath(String id) {
        String safe = id.replace(':', '_').replace('/', '_').replace('\\', '_');
        if (!safe.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("unsafe theme id: " + id);
        Path target = this.directory.resolve(safe + THEME_SUFFIX).toAbsolutePath().normalize();
        if (!target.getParent().equals(this.directory)) throw new IllegalArgumentException("unsafe theme path");
        return target;
    }

    private static String readBounded(Path file) throws IOException {
        if (Files.size(file) > UiThemeJsonCodec.MAX_BYTES) throw new IllegalArgumentException("theme file exceeds 1 MiB");
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static final class DefaultHolder {
        private static final UiThemeStorage INSTANCE = new UiThemeStorage(
                FMLPaths.CONFIGDIR.get().resolve("rtsbuilding").resolve("themes"));
    }
}
