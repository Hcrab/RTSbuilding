package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable catalog entry for one installable RTS plugin item.
 *
 * <p>The definition owns balance-facing metadata: the plugin item id, feature
 * gates it enables, uniqueness family, and numeric radius contribution. It does
 * not own player inventory mutation, persistence, networking, or UI layout.
 */
public final class RtsPluginDefinition {
    private final Identifier id;
    private final Identifier itemId;
    private final RtsPluginFamily family;
    private final Set<RtsFeature> features;
    private final int radiusBlocks;
    private final boolean fieldDeployment;

    public RtsPluginDefinition(Identifier id, Identifier itemId, RtsPluginFamily family,
            Set<RtsFeature> features, int radiusBlocks, boolean fieldDeployment) {
        this.id = id;
        this.itemId = itemId;
        this.family = family == null ? RtsPluginFamily.UNIQUE : family;
        this.features = features == null || features.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(features));
        this.radiusBlocks = Math.max(0, radiusBlocks);
        this.fieldDeployment = fieldDeployment;
    }

    public Identifier id() {
        return id;
    }

    public Identifier itemId() {
        return itemId;
    }

    public RtsPluginFamily family() {
        return family;
    }

    public Set<RtsFeature> features() {
        return features;
    }

    public int radiusBlocks() {
        return radiusBlocks;
    }

    public boolean fieldDeployment() {
        return fieldDeployment;
    }

    public boolean enables(RtsFeature feature) {
        return feature != null && features.contains(feature);
    }
}
