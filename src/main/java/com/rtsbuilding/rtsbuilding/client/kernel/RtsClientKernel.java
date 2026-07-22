package com.rtsbuilding.rtsbuilding.client.kernel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.input.InputPipeline;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.remote.RemoteMenuModule;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rtsbuilding.rtsbuilding.client.domain.module.ModuleState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TLK (Thin Lifecycle Kernel)——客户端内核，约 100 行有效代码。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>管理所有 {@link FeatureModule} 的生命周期（注册/激活/休眠/注销）</li>
 *   <li>提供统一 {@link EpochClock} 时间源</li>
 *   <li>驱动事件系统：{@link #dispatch(StateEvent)} 并维护历史事件队列供延迟注册的模块重放</li>
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
    /** 模块激活级别映射——Kernel 是状态唯一管理者 */
    private final Map<String, ModuleState> moduleStates = new HashMap<>();
    private RenderPipeline renderPipeline;
    private InputPipeline inputPipeline;
    private boolean initialized;

    // RTS 区域锚点信息（独立于摄像机模块）
    private double regionAnchorX, regionAnchorY, regionAnchorZ;
    private double regionMaxRadius;
    private boolean regionValid;

    // ======================================================================
    //  事件回溯（State Replay）
    // ======================================================================
    // 事件历史环形缓冲区——延迟注册的模块可通过 replayState() 回放历史事件，
    // 防止错过模块注册前已分发的事件。
    private static final int REPLAY_BUFFER_SIZE = 32;
    private final StateEvent[] replayBuffer = new StateEvent[REPLAY_BUFFER_SIZE];
    private int replayWriteIndex;

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
            moduleStates.put(module.moduleId(), ModuleState.ON);
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
            moduleStates.put(id, ModuleState.ON);
            // 为新注册的模块重放历史事件，确保不会错过注册前已分发的事件
            replayStateTo(module);
        }
    }

    /** 向指定模块重放历史事件（环形缓冲区中的所有已记录事件）。 */
    private void replayStateTo(FeatureModule module) {
        for (int i = 0; i < REPLAY_BUFFER_SIZE; i++) {
            int idx = (replayWriteIndex - i - 1 + REPLAY_BUFFER_SIZE * 2) % REPLAY_BUFFER_SIZE;
            StateEvent event = replayBuffer[idx];
            if (event == null) continue;
            module.onSessionEvent(event);
        }
    }

    /** 获取已注册的模块（字符串 ID 查找）。 */
    @SuppressWarnings("unchecked")
    public <T extends FeatureModule> T module(String id) {
        FeatureModule m = modules.get(id);
        return m == null ? null : (T) m;
    }

    /**
     * 按类型查找模块——编译期类型安全，无需强制转型。
     * <p>遍历所有模块找到第一个匹配类型的实例。9 个模块的遍历开销可忽略不计。</p>
     */
    @SuppressWarnings("unchecked")
    public <T extends FeatureModule> T module(Class<T> type) {
        for (FeatureModule m : modules.values()) {
            if (type.isInstance(m)) return (T) m;
        }
        return null;
    }

    // ======================================================================
    //  Tick
    // ======================================================================

    /** 每 tick Pre 阶段调用（对应 ClientTickEvent.Pre，在 aiStep() 之前）。 */
    public void tickPre() {
        if (!initialized) return;
        long now = clock.epochMs();
        int tickIdx = clock.tickIndex();
        for (FeatureModule module : modules.values()) {
            if (moduleStates.getOrDefault(module.moduleId(), ModuleState.ON) == ModuleState.OFF) continue;
            module.tickPre(now, tickIdx);
        }
    }

    /** 每 tick Post 阶段调用（对应 ClientTickEvent.Post，在 aiStep() 之后）。 */
    public void tick() {
        if (!initialized) return;
        long now = clock.tick();
        int tickIdx = clock.tickIndex();
        for (Map.Entry<String, FeatureModule> entry : modules.entrySet()) {
            if (moduleStates.getOrDefault(entry.getKey(), ModuleState.ON) == ModuleState.OFF) continue;
            entry.getValue().tick(now, tickIdx);
        }
        // 后处理：确保 RTS 摄像机活跃时 BuilderScreen 保持打开
        ensureBuilderScreenOpen();
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
        // 记录到事件历史缓冲区（供延迟注册的模块重放）
        replayBuffer[replayWriteIndex % REPLAY_BUFFER_SIZE] = event;
        replayWriteIndex++;
        for (Map.Entry<String, FeatureModule> entry : modules.entrySet()) {
            if (moduleStates.getOrDefault(entry.getKey(), ModuleState.ON) == ModuleState.OFF) continue;
            entry.getValue().onSessionEvent(event);
        }
        // 后处理：事件分发后管理全局副作用（如关闭 BuilderScreen）
        handlePostDispatch(event);
    }

    // ======================================================================
    //  Screen lifecycle management
    // ======================================================================

    /**
     * 当 RTS 摄像机活跃时确保 BuilderScreen 保持打开。
     * <p>替代原散布在 CameraModule 中的屏幕生命周期管理代码，
     * 将 UI 生命周期提升到内核层级统一管理。</p>
     *
     * <p>如果远程菜单模块报告有容器菜单打开，则跳过 BuilderScreen 的恢复，
     * 避免 BuilderScreen 与容器菜单争夺屏幕焦点。</p>
     */
    private void ensureBuilderScreenOpen() {
        if (mc().screen instanceof BuilderScreen) return;
        // 远程菜单打开时不覆盖容器 GUI
        RemoteMenuModule rmm = module(RemoteMenuModule.class);
        if (rmm != null && rmm.isRemoteMenuOpen()) return;
        CameraModule cam = module(CameraModule.class);
        if (cam != null && cam.getState().isEnabled()) {
            mc().setScreen(new BuilderScreen());
        }
    }

    /**
     * 关闭当前打开的 BuilderScreen。
     * <p>由 {@link #handlePostDispatch} 在收到服务器关闭事件时调用。</p>
     */
    private void closeBuilderScreenIfOpen() {
        if (mc().screen instanceof BuilderScreen) {
            mc().setScreen(null);
        }
    }

    /**
     * 事件分发后处理——管理全局副作用。
     * <p>当前处理：</p>
     * <ul>
     *   <li>{@code RtsToggled(true)} → 立即打开 BuilderScreen（消除一帧延迟导致的视觉闪烁）</li>
     *   <li>{@code RtsToggled(false)} → 关闭 BuilderScreen</li>
     * </ul>
     *
     * <p>注意：screen 打开/关闭与摄像机状态的生效是同一帧完成的，
     * 因为 dispatch 在网络处理器 {@code enqueueWork} 中同步执行。</p>
     */
    private void handlePostDispatch(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (e.enabled()) {
                if (!(mc().screen instanceof BuilderScreen)) {
                    mc().setScreen(new BuilderScreen());
                }
            } else {
                closeBuilderScreenIfOpen();
                // RTS 关闭时清理存储绑定动画状态，防止跨会话内存泄漏
                if (this.renderPipeline != null && this.renderPipeline.linkedStoragePass != null) {
                    this.renderPipeline.linkedStoragePass.clearAnimationState();
                }
            }
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
