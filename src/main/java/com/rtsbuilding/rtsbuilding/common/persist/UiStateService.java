package com.rtsbuilding.rtsbuilding.common.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class UiStateService {

    private static final Logger LOG = LoggerFactory.getLogger("RTS-UiState");
    private static final long FLUSH_DEBOUNCE_MS = 800;

    private final JsonFileRepository fileRepo = new JsonFileRepository();
    private UiSnapshot current = UiSnapshot.defaults();
    private boolean dirty;
    private long lastFlushTime;

    // ── Lifecycle ──

    public void onWorldLoad() {
        current = UiSnapshot.defaults();
        UiSnapshot.Global loaded = fileRepo.loadGlobal();
        if (loaded != null) current.global = loaded;
        LOG.debug("UI state loaded from disk");
    }

    public void onWorldUnload() {
        flush();
        current = UiSnapshot.defaults();
        LOG.debug("UI state flushed, session cleared");
    }

    // ── Snapshot access ──

    public UiSnapshot current() {
        return current;
    }

    // ── Apply/Collect (stateless, uses caller-provided property list) ──

    public static void applyAll(UiSnapshot snap, List<UiProperty> properties) {
        for (UiProperty prop : properties) {
            try {
                prop.applyToRuntime(snap);
            } catch (Exception e) {
                LOG.warn("Failed to apply property: {}", prop.key(), e);
            }
        }
    }

    public static void collectAll(UiSnapshot snap, List<UiProperty> properties) {
        for (UiProperty prop : properties) {
            try {
                prop.applyToSnapshot(snap);
            } catch (Exception e) {
                LOG.warn("Failed to collect property: {}", prop.key(), e);
            }
        }
    }

    // ── Dirty / Flush ──

    public void markDirty() {
        dirty = true;
    }

    public void tick() {
        if (!dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastFlushTime < FLUSH_DEBOUNCE_MS) return;
        flush();
    }

    public void flush() {
        try {
            fileRepo.saveGlobal(current.global);
            dirty = false;
            lastFlushTime = System.currentTimeMillis();
        } catch (Exception e) {
            LOG.error("Failed to flush UI state", e);
        }
    }
}
