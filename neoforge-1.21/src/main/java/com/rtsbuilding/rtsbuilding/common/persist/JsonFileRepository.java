package com.rtsbuilding.rtsbuilding.common.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonFileRepository implements UiStateRepository {

    private static final Logger LOG = LoggerFactory.getLogger("RTS-UiState");
    private static final Path PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building/ui-state.json");

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @Override
    public UiSnapshot.Global loadGlobal() {
        if (!Files.exists(PATH)) return new UiSnapshot.Global();
        try (var reader = Files.newBufferedReader(PATH)) {
            return gson.fromJson(reader, UiSnapshot.Global.class);
        } catch (Exception e) {
            LOG.warn("Failed to load UI state, using defaults", e);
            return new UiSnapshot.Global();
        }
    }

    @Override
    public void saveGlobal(UiSnapshot.Global global) {
        Path tmp = PATH.resolveSibling("ui-state.json.tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (var w = Files.newBufferedWriter(tmp)) {
                gson.toJson(global, w);
            }
            Files.move(tmp, PATH, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Failed to save UI state", e);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(PATH);
            Files.deleteIfExists(PATH.resolveSibling("ui-state.json.tmp"));
        } catch (IOException ignored) {}
    }
}
