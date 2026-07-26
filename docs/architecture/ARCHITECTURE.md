# RTS Building — 客户端架构设计 v2

> 基于六边形架构（Hexagonal Architecture）+ 依赖反转原则，面向可测试性、可维护性、模块隔离性设计。

---

## 1. 总览

```
┌────────────────────────────────────────────────────────────────────┐
│                        bootstrap/                                  │
│  (NeoForge @SubscribeEvent → 唯一入口，仅做事件桥接)               │
└─────────────────────────┬──────────────────────────────────────────┘
                          │ 创建
                          ▼
┌────────────────────────────────────────────────────────────────────┐
│                     composition/ (组合根)                          │
│  CompositionRoot.java — 显式构造所有依赖，贯穿整个对象图           │
│  无 @Inject / 无 ServiceLoader / 无反射，全手动 DI                │
└──────┬──────────────────────┬──────────────────────┬───────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌─────────────┐  ┌────────────────────┐  ┌──────────────────────────┐
│  domain/    │  │  application/      │  │  infrastructure/         │
│  (纯业务)   │  │  (应用服务/用例)   │  │  (Minecraft/平台适配器)  │
│             │  │                    │  │                          │
│ ModuleSpec  │  │ ModuleManager      │  │ NeoForgeEventBridge      │
│ Event       │  │ EventBus           │  │ MinecraftNetworkAdapter  │
│ State       │  │ SessionService     │  │ MinecraftRenderAdapter   │
│ Clock       │  │ ScreenCoordinator  │  │ MinecraftInputAdapter    │
└──────┬──────┘  └─────┬──────────────┘  └──────────┬───────────────┘
       │                │                            │
       └────────────────┴────────────────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │  presentation/       │
                    │  (Screen/Panel/UI)   │
                    │  BuilderScreen       │
                    │  Panels/Overlays     │
                    │  EventDispatcher     │
                    └──────────────────────┘
```

### 依赖方向（严格单向）

```
domain/  ←  application/  ←  infrastructure/
                               ↑
                          presentation/
```

- **domain/**：零外部依赖（纯 Java）
- **application/**：依赖 domain/
- **infrastructure/**：依赖 application/ + domain/，依赖 Minecraft/NeoForge
- **presentation/**：依赖 application/ + domain/ + infrastructure/（部分）

---

## 2. 包结构与职责

### 2.1 `domain/` — 纯业务内核（零 Minecraft 依赖）

```
domain/
├── module/
│   ├── ModuleSpec.java              ← 模块唯一标识（ModuleId + 显示名）
│   ├── ModuleState.java             ← ON / OFF / ERROR
│   └── capability/
│       ├── Tickable.java            ← 可接收 tick 回调
│       ├── EventReactive.java       ← 可接收 StateEvent
│       ├── NetworkPush.java         ← 可接收 S2C 推送
│       └── RenderFrameAware.java    ← 可接收帧渲染前回调
│
├── event/
│   ├── StateEvent.java              ← sealed 事件记录基类
│   └── EventBus.java                ← 事件总线接口（非实现）
│
├── time/
│   └── Clock.java                   ← 时间源接口（domain 侧抽象）
│
└── state/
    ├── CameraState.java             ← pure record
    ├── BuildingState.java           ← pure record
    ├── StorageState.java            ← pure record
    ├── MiningState.java             ← pure record
    └── ...
```

**关键原则：**
- `domain/` 不导入任何 Minecraft / NeoForge / LWJGL 类
- 所有状态为 `record`，不可变
- 所有接口定义只声明需要的动作，无冗余方法
- 可直接在 JUnit 中测试，无需 mock Minecraft

### 2.2 `application/` — 应用层服务与用例

```
application/
├── service/
│   ├── ModuleManager.java           ← 模块注册/生命周期/查询
│   ├── EventBusImpl.java            ← EventBus 实现
│   ├── SessionService.java          ← 会话生命周期（RTS toggle on/off）
│   ├── ScreenCoordinator.java       ← 屏幕打开/关闭决策
│   └── ModuleStateStore.java        ← 模块状态持久化
│
├── port/
│   ├── GameTickPort.java            ← 游戏 tick 抽象（Infra 实现）
│   ├── RenderFramePort.java         ← 渲染帧抽象
│   ├── NetworkPort.java             ← 网络发送抽象
│   ├── InputPort.java               ← 输入事件抽象
│   └── KeyMappingPort.java          ← 按键注册抽象
│
└── dto/
    ├── ModuleSnapshot.java          ← 模块运行时快照
    └── ScreenState.java             ← UI 状态快照
```

**关键原则：**
- `application/` 只引用 `domain/`，不引用 Minecraft 类型
- `Port` 接口定义抗锯齿边界（Anti-Corruption Layer），由 `infrastructure/` 实现
- 所有服务通过构造函数注入依赖

### 2.3 `infrastructure/` — Minecraft/平台适配器

```
infrastructure/
├── di/
│   └── CompositionRoot.java         ← 组合根：构建完整对象图
│
├── bootstrap/
│   ├── RtsClientBootstrap.java      ← NeoForge @Mod / @SubscribeEvent 入口
│   ├── ClientTickBridge.java        ← ClientTickEvent → GameTickPort
│   ├── ClientRenderBridge.java      ← RenderFrameEvent → RenderFramePort
│   └── ClientInputBridge.java       ← ScreenEvent → InputPort
│
├── input/
│   ├── InputPipeline.java
│   ├── InputLayer.java
│   ├── layer/
│   │   ├── CameraInputLayer.java
│   │   └── OverlayLayer.java
│   └── adapter/
│       └── MinecraftInputAdapter.java  ← Minecraft 键盘/鼠标 → InputPort
│
├── network/
│   ├── adapter/
│   │   ├── ClientNetworkAdapter.java   ← NetworkPort 实现
│   │   └── ServerNetworkAdapter.java
│   ├── S2CHandlers.java
│   └── C2SGateway.java
│
├── render/
│   ├── RenderPipeline.java
│   ├── RenderPass.java
│   └── pass/
│       ├── BoundaryPass.java
│       ├── BoxSelectionPass.java
│       ├── EntitySelectHighlightPass.java
│       ├── InteractionTargetPass.java
│       ├── LinkedStoragePass.java
│       └── LocateMarkerPass.java
│
├── module/                          ← FeatureModule 的实现
│   ├── camera/
│   │   ├── CameraModule.java        ← 实现 Tickable + EventReactive
│   │   ├── FreeCameraMode.java
│   │   ├── PlayerOrbitCameraMode.java
│   │   ├── CameraPoseComputer.java
│   │   └── CameraViewManager.java
│   ├── building/
│   ├── storage/
│   ├── mining/
│   ├── remote/
│   ├── workflow/
│   ├── blueprint/
│   ├── plugin/
│   ├── progression/
│   └── overlay/
│
├── pathfinding/
│   └── RtsClientPathfinding.java
│
├── compat/
│   └── RtsClientRemoteMenuCompat.java
│
└── util/
    ├── render/
    │   └── SpriteRenderer.java
    ├── animate/
    │   └── AnimationEngine.java
    └── theme/
        └── ThemeManager.java
```

**关键原则：**
- `infrastructure/` 反向依赖 `application/`（通过 Port 接口），不直接被 application 引用
- 所有 Minecraft/NeoForge 依赖仅在此层出现
- 模块实现可访问 Minecraft API，但领域状态保持在 `domain/` 中

### 2.4 `presentation/` — UI 框架

```
presentation/
├── screen/
│   ├── BuilderScreen.java
│   ├── RtsCraftTerminalScreen.java
│   └── RtsUiScaleFrame.java
│
├── event/
│   ├── EventDispatcher.java
│   └── model/                       ← sealed InputEvent 体系
│
├── layout/
│   ├── PanelRegistry.java
│   ├── RenderLayer.java
│   ├── PanelLayouts.java
│   ├── BottomPanelLayoutTypes.java
│   └── CategoryTypes.java
│
├── panel/
│   ├── base/
│   │   ├── api/RtsPanelApi.java
│   │   ├── window/RtsPanel.java
│   │   ├── window/RtsFloatingWindowLayer.java
│   │   ├── component/ScrollBar.java
│   │   ├── component/CollapsibleSection.java
│   │   ├── component/EdgeResizeHandler.java
│   │   ├── popup/BasePopup.java
│   │   └── overlay/DownOverlayLayer.java
│   ├── topbar/
│   ├── leftbar/
│   ├── rightbar/
│   ├── downbar/
│   ├── select/
│   ├── color/
│   ├── gear/
│   ├── container/
│   ├── component/                   ← 可复用控件
│   └── handler/                     ← 跨面板行为
│
└── state/
    └── RtsScreenUiStateManager.java
```

---

## 3. 核心交互流程

### 3.1 模块生命周期

```
CompositionRoot.init()
  │
  ├── new ModuleManager(EventBusImpl, Clock, List<ModuleSpec>)
  │     └── ModuleManager.register(spec)         // 按 spec 注册
  │
  └── ModuleManager.initializeAll()
        └── for each module:
              if (module instanceof Tickable m)  →  tickableRegistry.add(m)
              if (module instanceof EventReactive m) → eventBus.subscribe(m)
              if (module instanceof NetworkPush m) → networkPort.add(m)
              if (module instanceof RenderFrameAware m) → renderPort.add(m)
```

### 3.2 Server → Client 数据流

```
S2C Packet → ClientNetworkAdapter   (infrastructure/network/adapter)
  │
  ├── networkPort.onPacket(packet)   (application/port/NetworkPort)
  │
  └── EventBusImpl.publish(event)    (application/service/EventBusImpl)
        └── for each EventReactive module:
              module.onEvent(event)
```

### 3.3 Client → Server 数据流

```
Panel / Handler / InputLayer          (presentation / infrastructure)
  │
  ├── module.someAction(command)      (infrastructure/module/xxx)
  │
  ├── networkPort.send(payload)       (application/port/NetworkPort)
  │
  └── ServerNetworkAdapter.send()     (infrastructure/network/adapter)
```

### 3.4 输入流

```
Minecraft Mouse/Key Event
  → ClientInputBridge                  (infrastructure/bootstrap)
    → inputPort.onInput(event)        (application/port/InputPort)
      → InputPipeline.dispatch()      (infrastructure/input)
```

---

## 4. 关键接口设计

### 4.1 模块能力分离（取代单一 FeatureModule）

```java
// domain/module/capability/Tickable.java
public interface Tickable {
    void tick(Clock clock);
}

// domain/module/capability/EventReactive.java
public interface EventReactive {
    void onEvent(StateEvent event);
}

// domain/module/capability/NetworkPush.java
public interface NetworkPush<T extends S2CPacket> {
    Class<T> packetType();
    void onPacket(T packet);
}

// domain/module/capability/RenderFrameAware.java
public interface RenderFrameAware {
    void onRenderFrame(float partialTick);
}
```

**好处：**
- 模块只需实现自己需要的接口
- `ModuleManager` 根据 instanceOf 选择性调用，消除空实现
- 单元测试时只需 mock 单个接口

### 4.2 端口与适配器

```java
// application/port/GameTickPort.java
public interface GameTickPort {
    void onTickPre();
    void onTickPost();
}

// application/port/NetworkPort.java
public interface NetworkPort {
    <T extends C2SPacket> void send(T packet);
    <T extends S2CPacket> void registerHandler(Class<T> type, NetworkPush<T> handler);
}

// application/port/RenderFramePort.java
public interface RenderFramePort {
    void registerRenderPass(RenderFrameAware pass);
    void onRenderFrame(float partialTick);
}
```

### 4.3 模块管理器

```java
// application/service/ModuleManager.java
public final class ModuleManager {
    private final List<ModuleSpec> specs;
    private final EventBus eventBus;
    private final Clock clock;
    private final Map<ModuleId, Object> modules = new HashMap<>();

    // 按能力分类注册，避免无差别遍历
    private final List<Tickable> tickables = new ArrayList<>();
    private final List<EventReactive> eventReactives = new ArrayList<>();
    private final Map<Class<?>, List<NetworkPush<?>>> networkHandlers = new HashMap<>();
    private final List<RenderFrameAware> renderAware = new ArrayList<>();

    public <T> T module(Class<T> type) { ... }
    public void tick() { for (var m : tickables) m.tick(clock); }
}
```

---

## 5. 组合根完整示例

```java
// infrastructure/di/CompositionRoot.java
public final class CompositionRoot {

    private static CompositionRoot INSTANCE;

    private final ModuleManager moduleManager;
    private final EventBus eventBus;
    private final GameTickPort tickPort;
    private final NetworkPort networkPort;
    private final RenderFramePort renderFramePort;
    private final ScreenCoordinator screenCoordinator;

    public CompositionRoot() {
        // 1. Domain 与基础设施无关的对象
        this.eventBus = new EventBusImpl();
        this.clock = new MinecraftClock();  // infrastructure 实现

        // 2. 基础设施端口（Minecraft 版实现）
        this.tickPort = new MinecraftTickAdapter(this);
        this.networkPort = new MinecraftNetworkAdapter();
        this.renderFramePort = new MinecraftRenderAdapter();

        // 3. 模块列表（通过 spec 注册，比接口更灵活）
        List<ModuleSpec> specs = List.of(
            ModuleSpec.of("camera", CameraModule::new),
            ModuleSpec.of("building", BuildingModule::new),
            ModuleSpec.of("storage", StorageModule::new),
            ModuleSpec.of("mining", MiningModule::new),
            ModuleSpec.of("remote", RemoteMenuModule::new),
            ModuleSpec.of("workflow", WorkflowModule::new),
            ModuleSpec.of("blueprint", BlueprintModule::new),
            ModuleSpec.of("plugin", PluginModule::new),
            ModuleSpec.of("progression", ProgressionModule::new),
            ModuleSpec.of("overlay", OverlayModule::new)
        );

        // 4. 应用层服务
        this.moduleManager = new ModuleManager(eventBus, clock, specs, networkPort);
        this.screenCoordinator = new ScreenCoordinator(moduleManager);

        // 5. 启动
        this.moduleManager.initializeAll();
    }

    public static CompositionRoot get() { return INSTANCE; }
    public static void init() { INSTANCE = new CompositionRoot(); }

    // 端口提供给 bootstrap 使用
    public GameTickPort tickPort() { return tickPort; }
    public NetworkPort networkPort() { return networkPort; }
    public RenderFramePort renderFramePort() { return renderFramePort; }
    public ScreenCoordinator screenCoordinator() { return screenCoordinator; }
}
```

**此模式的优势：**
- 完整对象图在 `CompositionRoot` 中显式可见
- 没有 `@Inject`、没有反射、没有隐藏依赖
- 换实现时只需修改 `CompositionRoot`
- 测试时可创建单独 `TestCompositionRoot`

---

## 6. 测试策略

```
src/
├── test/
│   ├── java/
│   │   ├── domain/                    ← 纯单元测试，无需 mock
│   │   │   ├── event/EventBusImplTest.java
│   │   │   └── time/ClockTest.java
│   │   │
│   │   ├── application/               ← 单元测试，mock Port 接口
│   │   │   ├── ModuleManagerTest.java
│   │   │   └── ScreenCoordinatorTest.java
│   │   │
│   │   └── infrastructure/            ← 集成测试
│   │       └── composition/CompositionRootTest.java
```

### 分层测试示例

```java
// domain/ — 纯逻辑，零 mock
class EventBusImplTest {
    @Test void dispatches_to_subscribers() {
        var bus = new EventBusImpl();
        var captured = new AtomicReference<StateEvent>();
        bus.subscribe(e -> captured.set(e));
        bus.publish(new StateEvent.RtsToggled(true));
        assertEquals(true, ((StateEvent.RtsToggled)captured.get()).active());
    }
}

// application/ — port 接口可 mock
class ModuleManagerTest {
    @Test void only_ticks_tickable_modules() {
        var eventBus = mock(EventBus.class);
        var clock = mock(Clock.class);
        var nonTick = mock(EventReactive.class);    // 仅事件响应
        var tickable = mock(Tickable.class);         // 可 tick

        var mgr = new ModuleManager(eventBus, clock,
            List.of(ModuleSpec.of("a", () -> nonTick),
                    ModuleSpec.of("b", () -> tickable)));
        mgr.initializeAll();
        mgr.tick();

        verify(tickable).tick(clock);
        verifyNoInteractions(nonTick);  // ← 关键：非 Tickable 不受 tick 影响
    }
}
```

---

## 7. 与当前架构的差异对比

| 维度 | 当前架构 | v2 架构 |
|------|---------|---------|
| 依赖注入 | Singleton 服务定位器 | 显式组合根 + 构造函数注入 |
| 模块接口 | 单 `FeatureModule` 含 6 方法 | 4 个能力接口，按需实现 |
| 端口抽象 | 无 | `GameTickPort` / `NetworkPort` / `RenderFramePort` / `InputPort` |
| Minecraft 耦合 | 贯穿所有包 | 仅限 `infrastructure/` |
| 测试覆盖 | 0 | 可分层测试 |
| 模块注册 | 10 个硬编码 `registerModule()` | `List.of(ModuleSpec.of(...))`，1 行 1 模块 |
| BuilderScreen | ~866 行，做面板构造+渲染编排+全局状态 | 面板构造移至 `PanelRegistry`，状态移至 `ScreenCoordinator` |
| 静态方法类 | `RtsClientPacketGateway`、`RtsClientPathfinding` | 改为实例服务，通过 Port 注入 |

---

## 8. 迁移路径（4 阶段）

```
Phase 1 — 零破坏性引入（可并行）
  1. 创建 domain/ 包，定义纯接口，不修改现有代码
  2. 将现有 record 类迁移到 domain/
  3. 定义 Port 接口

Phase 2 — 组合根 + 模块管理器
  4. 创建 application/ 包，实现 ModuleManager（适配旧 FeatureModule）
  5. 创建 CompositionRoot（可新旧并存）
  6. 将 ModuleSpec 注册方式引入

Phase 3 — 逐模块迁移
  7. 每个模块逐步改为实现能力接口
  8. BuilderScreen 拆出 ScreenCoordinator
  9. 静态度量类改为实例服务

Phase 4 — 清理
  10. 删除 RtsClientKernel（不再需要）
  11. 删除空目录 module/perf/
  12. 添加缺失 @Nullable 注解
```
