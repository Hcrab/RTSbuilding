package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.*;

/**
 * {@link RtsAPI} 的默认实现——将所有调用委托给领域服务层。
 *
 * <p>第三方附加模组不应直接引用此类。
 * <p>每个子 API 的实现都位于 {@code api/impl/} 目录下的各自文件中。
 */
public final class RtsAPIImpl implements RtsAPI {

    private static final RtsAPIImpl INSTANCE = new RtsAPIImpl();

    private final RtsStorageQueryAPIImpl storageApi = new RtsStorageQueryAPIImpl();
    private final RtsBlueprintAPIImpl blueprintApi = new RtsBlueprintAPIImpl();
    private final RtsPlacementAPIImpl placementApi = new RtsPlacementAPIImpl();
    private final RtsInteractionAPIImpl interactionApi = new RtsInteractionAPIImpl();
    private final RtsMiningAPIImpl miningApi = new RtsMiningAPIImpl();
    private final RtsTransferAPIImpl transferApi = new RtsTransferAPIImpl();
    private final RtsCraftingAPIImpl craftingApi = new RtsCraftingAPIImpl();
    private final RtsFluidAPIImpl fluidApi = new RtsFluidAPIImpl();
    private final RtsBindingsAPIImpl bindingsApi = new RtsBindingsAPIImpl();
    private final RtsSessionQueryAPIImpl sessionApi = new RtsSessionQueryAPIImpl();

    private RtsAPIImpl() {
    }

    /** 初始化 API 并通过 {@link RtsAPI#setImplementation(RtsAPI)} 注册它。 */
    public static void init() {
        RtsAPI.setImplementation(INSTANCE);
    }

    @Override
    public RtsStorageQueryAPI storage() { return storageApi; }

    @Override
    public RtsBlueprintAPI blueprint() { return blueprintApi; }

    @Override
    public RtsPlacementAPI placement() { return placementApi; }

    @Override
    public RtsInteractionAPI interaction() { return interactionApi; }

    @Override
    public RtsMiningAPI mining() { return miningApi; }

    @Override
    public RtsTransferAPI transfer() { return transferApi; }

    @Override
    public RtsCraftingAPI crafting() { return craftingApi; }

    @Override
    public RtsFluidAPI fluids() { return fluidApi; }

    @Override
    public RtsBindingsAPI bindings() { return bindingsApi; }

    @Override
    public RtsSessionQueryAPI sessions() { return sessionApi; }
}
