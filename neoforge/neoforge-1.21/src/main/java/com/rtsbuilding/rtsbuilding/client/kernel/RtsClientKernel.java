package com.rtsbuilding.rtsbuilding.client.kernel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.domain.module.ModuleState;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.remote.RemoteMenuModule;
import com.rtsbuilding.rtsbuilding.client.input.InputPipeline;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RtsClientKernel {
    private static final Logger LOG = LoggerFactory.getLogger("RTS-Kernel");
    private static final RtsClientKernel INSTANCE = new RtsClientKernel();

    private final EpochClock clock = new EpochClock();
    private final Map<String, FeatureModule> modules = new LinkedHashMap<>();
    
    private final Map<String, ModuleState> moduleStates = new HashMap<>();
    private RenderPipeline renderPipeline;
    private InputPipeline inputPipeline;
    private boolean initialized;

    
    private double regionAnchorX, regionAnchorY, regionAnchorZ;
    private double regionMaxRadius;
    private boolean regionValid;

    
    
    
    
    
    private static final int REPLAY_BUFFER_SIZE = 32;
    private final StateEvent[] replayBuffer = new StateEvent[REPLAY_BUFFER_SIZE];
    private int replayWriteIndex;

    private RtsClientKernel() {}

    public static RtsClientKernel get() {
        return INSTANCE;
    }

    
    
    

    
    public synchronized void initialize() {
        if (initialized) return;
        this.renderPipeline = new RenderPipeline();
        this.inputPipeline = new InputPipeline();
        for (FeatureModule module : modules.values()) {
            module.init(this);
            moduleStates.put(module.moduleId(), ModuleState.ON);
            LOG.debug("Module initialized: {}", module.moduleId());
        }
        this.initialized = true;
        LOG.info("RTS Kernel initialized with {} modules", modules.size());
    }

    
    public synchronized void register(FeatureModule module) {
        String id = module.moduleId();
        if (modules.containsKey(id)) {
            LOG.warn("Module {} already registered, skipping", id);
            return;
        }
        modules.put(id, module);
        if (initialized) {
            module.init(this);
            moduleStates.put(id, ModuleState.ON);
            
            replayStateTo(module);
        }
    }

    
    private void replayStateTo(FeatureModule module) {
        for (int i = 0; i < REPLAY_BUFFER_SIZE; i++) {
            int idx = (replayWriteIndex - i - 1 + REPLAY_BUFFER_SIZE * 2) % REPLAY_BUFFER_SIZE;
            StateEvent event = replayBuffer[idx];
            if (event == null) continue;
            module.onSessionEvent(event);
        }
    }

    
    @SuppressWarnings("unchecked")
    public <T extends FeatureModule> T module(String id) {
        FeatureModule m = modules.get(id);
        return m == null ? null : (T) m;
    }

    
    @SuppressWarnings("unchecked")
    public <T extends FeatureModule> T module(Class<T> type) {
        for (FeatureModule m : modules.values()) {
            if (type.isInstance(m)) return (T) m;
        }
        return null;
    }

    
    
    

    
    public void tickPre() {
        if (!initialized) return;
        long now = clock.epochMs();
        int tickIdx = clock.tickIndex();
        for (FeatureModule module : modules.values()) {
            if (moduleStates.getOrDefault(module.moduleId(), ModuleState.ON) == ModuleState.OFF) continue;
            module.tickPre(now, tickIdx);
        }
    }

    
    public void tick() {
        if (!initialized) return;
        long now = clock.tick();
        int tickIdx = clock.tickIndex();
        for (Map.Entry<String, FeatureModule> entry : modules.entrySet()) {
            if (moduleStates.getOrDefault(entry.getKey(), ModuleState.ON) == ModuleState.OFF) continue;
            entry.getValue().tick(now, tickIdx);
        }
        
        ensureBuilderScreenOpen();
    }

    
    public void onRenderFrame(float partialTick, PoseStack poseStack) {
        if (!initialized) return;
        if (renderPipeline != null) {
            renderPipeline.onRenderFrame(partialTick, poseStack);
        }
    }

    
    
    

    
    public void dispatch(StateEvent event) {
        if (!initialized) return;
        
        replayBuffer[replayWriteIndex % REPLAY_BUFFER_SIZE] = event;
        replayWriteIndex++;
        for (Map.Entry<String, FeatureModule> entry : modules.entrySet()) {
            if (moduleStates.getOrDefault(entry.getKey(), ModuleState.ON) == ModuleState.OFF) continue;
            entry.getValue().onSessionEvent(event);
        }
        
        handlePostDispatch(event);
    }

    
    
    

    
    private void ensureBuilderScreenOpen() {
        Screen screen = mc().screen;
        if (screen instanceof BuilderScreen || screen instanceof RtsCraftTerminalScreen) return;
        if (screen != null) return;
        CameraModule cam = module(CameraModule.class);
        if (cam != null && cam.getState().isEnabled()) {
            mc().setScreen(new BuilderScreen());
        }
    }

    
    private void closeBuilderScreenIfOpen() {
        if (mc().screen instanceof BuilderScreen) {
            LOG.debug("RTS: Closing BuilderScreen via closeBuilderScreenIfOpen (RTS toggled off)");
            mc().setScreen(null);
        }
    }

    
    private void handlePostDispatch(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (e.enabled()) {
                if (!(mc().screen instanceof BuilderScreen)) {
                    mc().setScreen(new BuilderScreen());
                }
            } else {
                closeBuilderScreenIfOpen();
                
                if (this.renderPipeline != null && this.renderPipeline.linkedStoragePass != null) {
                    this.renderPipeline.linkedStoragePass.clearAnimationState();
                }
            }
        }
    }

    
    
    

    public EpochClock clock() {
        return this.clock;
    }

    public RenderPipeline renderPipeline() {
        return this.renderPipeline;
    }

    public InputPipeline inputPipeline() {
        return this.inputPipeline;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    
    
    

    public void updateRegion(double x, double y, double z, double maxRadius) {
        this.regionAnchorX = x;
        this.regionAnchorY = y;
        this.regionAnchorZ = z;
        this.regionMaxRadius = maxRadius;
        this.regionValid = true;
    }

    public double getRegionAnchorX() { return regionAnchorX; }
    public double getRegionAnchorY() { return regionAnchorY; }
    public double getRegionAnchorZ() { return regionAnchorZ; }
    public double getRegionMaxRadius() { return regionMaxRadius; }
    public boolean isRegionValid() { return regionValid; }

    
    public Minecraft mc() {
        return Minecraft.getInstance();
    }
}
