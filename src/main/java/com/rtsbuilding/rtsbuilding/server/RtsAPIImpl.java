package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.server.api.*;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsBindingsAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsBlueprintAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsCraftingAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsFluidAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsInteractionAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsLifecycleAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsMiningAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsPlacementAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsSessionQueryAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsStorageQueryAPIImpl;
import com.rtsbuilding.rtsbuilding.server.api.impl.RtsTransferAPIImpl;

/**
 * RtsAPI 的默认实现——将所有调用委托给领域服务???
 *
 * <p>第三方模组不应直接引用此???
 * <p>各子 API 的实现分散在 {@code api/impl/} 包中各自独立的文件里??
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
    private final RtsLifecycleAPIImpl lifecycleApi = new RtsLifecycleAPIImpl();
    private final RtsSessionQueryAPIImpl sessionApi = new RtsSessionQueryAPIImpl();

    private RtsAPIImpl() {
    }

    /** 初始??API 并注册到 {@link RtsAPI}??*/
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
    public RtsLifecycleAPI lifecycle() { return lifecycleApi; }

    @Override
    public RtsSessionQueryAPI sessions() { return sessionApi; }
}
