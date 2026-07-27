package com.rtsbuilding.rtsbuilding.api.compat;

import org.jetbrains.annotations.ApiStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.NonExtendable
public final class RtsCompatRegistry {

    private static final List<RtsStorageNetworkProvider> storageProviders = new ArrayList<>();
    private static final List<RtsFluidNetworkProvider> fluidProviders = new ArrayList<>();
    private static final List<RtsBackpackProvider> backpackProviders = new ArrayList<>();
    private static final List<RtsIconResolver> iconResolvers = new ArrayList<>();
    private static final List<RtsQuestIntegration> questIntegrations = new ArrayList<>();

    private RtsCompatRegistry() {}

    public static void register(RtsStorageNetworkProvider provider) {
        storageProviders.add(provider);
    }

    public static void register(RtsFluidNetworkProvider provider) {
        fluidProviders.add(provider);
    }

    public static void register(RtsBackpackProvider provider) {
        backpackProviders.add(provider);
    }

    public static void register(RtsIconResolver resolver) {
        iconResolvers.add(resolver);
    }

    public static void register(RtsQuestIntegration integration) {
        questIntegrations.add(integration);
    }

    public static List<RtsStorageNetworkProvider> getStorageProviders() {
        return Collections.unmodifiableList(storageProviders);
    }

    public static List<RtsFluidNetworkProvider> getFluidProviders() {
        return Collections.unmodifiableList(fluidProviders);
    }

    public static List<RtsBackpackProvider> getBackpackProviders() {
        return Collections.unmodifiableList(backpackProviders);
    }

    public static List<RtsIconResolver> getIconResolvers() {
        return Collections.unmodifiableList(iconResolvers);
    }

    public static List<RtsQuestIntegration> getQuestIntegrations() {
        return Collections.unmodifiableList(questIntegrations);
    }
}
