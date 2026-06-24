package com.rtsbuilding.rtsbuilding.client.kernel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.input.InputPipeline;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLK (Thin Lifecycle Kernel)——客户端内核，约 80 行有效代码。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>管理所有 {@link FeatureModule} 的生命周期（注册/激活/休眠/注销）</li>
 *   <li>提供统一 {@link EpochClock} 时间源</li>
 *   <li>驱动事件系统：{@link #dispatch(StateEvent)}</li>
 *   <li>统领渲染管线 {@link RenderPipeline} 与输入流水线 {@link InputPipeline}</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * 所有操作均在 Minecraft 主线程（Render Thread）执行，无需同步。
 */
public final class RtsClientKernel {
    private static final Logger LOG = LoggerFactory.getLogger("RTS-Kernel");
    private static final RtsClientKernel INSTANCE = new RtsClientKernel();

    private final EpochClock clock = new EpochClock();
    private final Map<String, FeatureModule> modules = new LinkedHashMap<>();
    private final List<FeatureModule> activeModules = new ArrayList<>();
    private RenderPipeline renderPipeline;
    private InputPipeline inputPipeline;
    private boolean initialized;

    // RTS 区域锚点信息（独立于摄像机模块）
    private double regionAnchorX, regionAnchorY, regionAnchorZ;
    private double regionMaxRadius;
    private boolean regionValid;

    private RtsClientKernel() {}

    public static RtsClientKernel get() {
        return INSTANCE;
    }

    // ======================================================================
    //  Lifecycle
    // ======================================================================

    /** 初始化内核及所有已注册模块。仅在客户端启动时调用一次。 */
    public synchronized void initialize() {
        if (initialized) return;
        this.renderPipeline = new RenderPipeline();
        this.inputPipeline = new InputPipeline();
        for (FeatureModule module : modules.values()) {
            module.init(this);
            activeModules.add(module);
            LOG.debug("Module initialized: {}", module.moduleId());
        }
        this.initialized = true;
        LOG.info("RTS Kernel initialized with {} modules", modules.size());
    }

    /** 注册一个 Feature Module。可在初始化前后随时调用。 */
    public synchronized void register(FeatureModule module) {
        String id = module.moduleId();
        if (modules.containsKey(id)) {
            LOG.warn("Module {} already registered, skipping", id);
            return;
        }
        modules.put(id, module);
        if (initialized) {
            module.init(this);
            activeModules.add(module);
        }
    }

    /** 获取已注册的模块。 */
    @SuppressWarnings("unchecked")
    public <T extends FeatureModule> T module(String id) {
        FeatureModule m = modules.get(id);
        return m == null ? null : (T) m;
    }

    // ======================================================================
    //  Tick
    // ======================================================================

    /** 每客户端 tick 调用一次。由 {@link com.rtsbuilding.rtsbuilding.client.bootstrap.ClientTickHandler} 驱动。 */
    public void tick() {
        if (!initialized) return;
        long now = clock.tick();
        int tickIdx = clock.tickIndex();
        for (FeatureModule module : activeModules) {
            if (module.state() == ModuleState.OFF) continue;
            module.tick(now, tickIdx);
        }
    }

    /** 渲染帧末尾调用。由 {@link com.rtsbuilding.rtsbuilding.client.bootstrap.ClientRenderHandler} 驱动。 */
    public void onRenderFrame(float partialTick, PoseStack poseStack) {
        if (!initialized) return;
        if (renderPipeline != null) {
            renderPipeline.onRenderFrame(partialTick, poseStack);
        }
    }

    // ======================================================================
    //  Event dispatch
    // ======================================================================

    /** 向所有非 OFF 模块广播事件。由网络回调或内部逻辑触发。 */
    public void dispatch(StateEvent event) {
        if (!initialized) return;
        for (FeatureModule module : activeModules) {
            if (module.state() == ModuleState.OFF) continue;
            module.onSessionEvent(event);
        }
    }

    // ======================================================================
    //  State management
    // ======================================================================

    /** 设置模块的激活级别。内核自动维护 activeModules 列表。 */
    public void setModuleState(String moduleId, ModuleState state) {
        FeatureModule m = modules.get(moduleId);
        if (m == null) return;
        ModuleState old = m.state();
        if (old == state) return;
        // 反射更新 state（模块自行管理 state 字段）
        m.onStateChange(state);
        if (old == ModuleState.OFF && state != ModuleState.OFF) {
            if (!activeModules.contains(m)) activeModules.add(m);
        } else if (state == ModuleState.OFF) {
            activeModules.remove(m);
        }
    }

    // ======================================================================
    //  Accessors
    // ======================================================================

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

    // ======================================================================
    //  RTS region info (anchor + boundary radius, independent of camera module)
    // ======================================================================

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

    /** 获取 Minecraft 实例（便利方法）。 */
    public Minecraft mc() {
        return Minecraft.getInstance();
    }
}
