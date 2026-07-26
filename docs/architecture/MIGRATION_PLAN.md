# 迁移计划：从当前架构 → v2 架构

> 基于 `client.md` 中定义的六边形架构，采用 **Strangler Fig 模式**逐步替换，保持每步可编译、可运行。

---

## 迁移策略总览

```
Phase 1     Phase 2         Phase 3            Phase 4             Phase 5         Phase 6
──────      ──────          ──────              ──────              ──────          ──────
零破坏创建   组合根搭建       双向桥接             逐模块迁移           Screen拆解      旧代码清理
                                                                    
domain/     CompositionRoot  bootstrap→双向       overlay(stub)      ScreenCoordinator 删kernel
application/ ModuleManager   新旧内核并存          blueprint/stub     PanelRegistry     删FeatureModule
                           Port 实现创建          plugin              面板引用迁移      删空目录
                                                 progression                            添加 @Nullable
                                                 remote
                                                 workflow
                                                 mining
                                                 building
                                                 storage
                                                 camera
```

---

## Phase 1 — 零破坏创建 Domain 层

> **目标：** 创建 `domain/` 包，定义纯接口，不修改现有代码。
> **约束：** 必须 0 编译错误，0 运行时行为变化。

### 步骤

#### 1.1 创建模块能力接口

| # | 文件路径 | 内容 |
|---|---------|------|
| 1 | `client/domain/module/ModuleId.java` | `record ModuleId(String id, String displayName)` |
| 2 | `client/domain/module/ModuleState.java` | `enum ModuleState { ON, OFF, ERROR }` |
| 3 | `client/domain/module/capability/Tickable.java` | `interface Tickable { void tick(Clock clock); }` |
| 4 | `client/domain/module/capability/EventReactive.java` | `interface EventReactive { void onEvent(StateEvent event); }` |
| 5 | `client/domain/module/capability/NetworkPush.java` | `interface NetworkPush<T extends Packet> { ... }` |
| 6 | `client/domain/module/capability/RenderFrameAware.java` | `interface RenderFrameAware { void onRenderFrame(float partialTick); }` |

#### 1.2 创建事件体系

| # | 文件路径 | 内容 |
|---|---------|------|
| 7 | `client/domain/event/StateEvent.java` | `sealed interface StateEvent` 含现有所有事件类型 |
| 8 | `client/domain/event/EventBus.java` | `interface EventBus { void publish(StateEvent); void subscribe(Consumer<StateEvent>); }` |

#### 1.3 创建时间抽象

| # | 文件路径 | 内容 |
|---|---------|------|
| 9 | `client/domain/time/Clock.java` | `interface Clock { long epochMs(); float partialTick(); }` |

#### 1.4 迁移纯数据 Record

将 `record/` 下的纯数据类移动到 `domain/state/`：
- `FluidEntry.java`
- `StorageEntry.java`
- `LinkedStorageEntry.java`
- `CraftFeedbackInfo.java`
- `WorkflowProgress.java`

**做法：** 在原位置保留 `@Deprecated` 转发类，指向 `domain/state/` 新类。
或直接全局替换 import（纯 record，无副作用）。

#### ✅ Phase 1 验证标准

```
./gradlew compileJava  →  BUILD SUCCESSFUL
运行时没有任何行为变化
```

---

## Phase 2 — 创建 Application 层 + 组合根

> **目标：** 创建 `application/` 服务和 `CompositionRoot`。新旧并行，CompositionRoot 被创建但**尚未被任何代码调用**。

### 步骤

#### 2.1 创建 Port 接口

| # | 文件 | 依赖 |
|---|------|------|
| 10 | `client/application/port/GameTickPort.java` | domain/ |
| 11 | `client/application/port/NetworkPort.java` | domain/ |
| 12 | `client/application/port/RenderFramePort.java` | domain/ |
| 13 | `client/application/port/InputPort.java` | domain/ |
| 14 | `client/application/port/KeyMappingPort.java` | domain/ |

#### 2.2 创建 Application Service

| # | 文件 | 说明 |
|---|------|------|
| 15 | `client/application/service/EventBusImpl.java` | 实现 domain/EventBus，线程安全 |
| 16 | `client/application/service/ModuleManager.java` | 替代 RtsClientKernel 的模块管理 | 
| 17 | `client/application/service/SessionService.java` | RTS 开关会话管理 |
| 18 | `client/application/service/ScreenCoordinator.java` | 屏幕打开/关闭逻辑 |

`ModuleManager` 需要接收现有模块的适配（Phase 3），所以要设计为可接受旧 FeatureModule 和新能力接口的混合模式：

```java
// Phase 2 的 ModuleManager 设计
public final class ModuleManager {
    // 同时接受新旧两种模块
    public void registerNew(String id, Object module);          // 新能力接口模块
    public void registerLegacy(String id, FeatureModule module); // 旧模块 → 适配器包装
}
```

#### 2.3 创建 CompositionRoot（静默，不启动）

| # | 文件 | 说明 |
|---|------|------|
| 19 | `client/infrastructure/di/CompositionRoot.java` | 构建对象图但 `init()` 暂不执行 |

CompositionRoot 先只构建 `EventBusImpl`、`Clock` 实现等无副作用的组件。模块管理和端口实现延迟到 Phase 3。

#### ✅ Phase 2 验证标准

```
./gradlew compileJava  →  BUILD SUCCESSFUL
CompositionRoot 存在但未初始化，运行时无变化
```

---

## Phase 3 — Bootstrap 双向桥接

> **目标：** 让 CompositionRoot 初始化，bootstrap 事件同时发给新旧两个系统。
> **约束：** 旧系统继续全功能运行，新系统收到事件但不产生业务效果（模块尚未迁移）。

### 步骤

#### 3.1 实现基础设施适配器

| # | 文件 | 说明 |
|---|------|------|
| 20 | `client/infrastructure/bootstrap/ClientTickBridge.java` | `ClientTickEvent` → `GameTickPort` |
| 21 | `client/infrastructure/bootstrap/ClientRenderBridge.java` | `RenderFrameEvent` → `RenderFramePort` |
| 22 | `client/infrastructure/network/adapter/ClientNetworkAdapter.java` | `NetworkPort` 实现 |
| 23 | `client/infrastructure/network/adapter/ServerNetworkAdapter.java` | C2S 发送 |

#### 3.2 创建 Minecraft 版基础设施适配器

| # | 文件 |
|---|------|
| 24 | `client/infrastructure/time/MinecraftClock.java` | 基于 `Minecraft.getPartialTick()` 实现 Clock |
| 25 | `client/infrastructure/input/adapter/MinecraftInputAdapter.java` | 键盘/鼠标 → InputPort |

#### 3.3 修改 bootstrap 入口

修改 `RtsClientBootstrap.java`：

```java
// 在 mod constructor 中初始化 CompositionRoot
public RtsClientBootstrap() {
    CompositionRoot.init();       // ← 新增
    // 旧代码不变...
}
```

修改 `ClientTickHandler.java`：

```java
@SubscribeEvent
public void onPreTick(ClientTickEvent.Pre event) {
    CompositionRoot.get().tickPort().onTickPre();  // ← 新增
    // 旧代码不变...
}
```

同理修改 `ClientRenderHandler.java`。

#### 3.4 验证接口与旧模块的兼容性

运行测试，确保 `ModuleManager.tick()` 不会影响旧模块（因为旧模块尚未注册到新系统）。

#### ✅ Phase 3 验证标准

```
./gradlew compileJava  →  BUILD SUCCESSFUL
游戏可正常启动，RTS 功能完整
新系统收到事件但新模块列表为空，无副作用
```

---

## Phase 4 — 逐模块迁移

> **目标：** 将 10 个 FeatureModule 逐一迁移到新能力接口体系。
> **顺序：** 从最简单的 stub 开始到最复杂的 CameraModule 结束。

### 4.1 OverlayModule（stub，1 次提交）

1. 创建 `client/domain/state/OverlayState.java`
2. 创建 `client/infrastructure/module/overlay/OverlayModule.java`（仅实现 `EventReactive`）
3. 在 `CompositionRoot` 中注册
4. 旧的 `module/overlay/OverlayModule.java` 标记 `@Deprecated`

### 4.2 BlueprintModule（stub）

同上，仅实现 `EventReactive`。

### 4.3 PluginModule

1. 迁移现有 `PluginModule` 逻辑
2. 实现 `EventReactive` + `NetworkPush`
3. S2C 网络处理从 `RtsClientNetworkHandlers` 逐步移到 `PluginModule.onPacket()`

### 4.4 ProgressionModule

1. 实现 `EventReactive` + `NetworkPush`
2. 直接在 `onPacket()` 中处理 S2C 数据，不再过 `RtsClientNetworkHandlers`

### 4.5 RemoteMenuModule

1. 实现 `Tickable` + `EventReactive`
2. 移动 `tick()` 逻辑

### 4.6 WorkflowModule

1. 实现 `EventReactive` + `NetworkPush`

### 4.7 MiningModule

1. 创建 `client/domain/state/MiningState.java`（从现有 `MiningState.java` 提取纯状态）
2. 实现 `EventReactive`

### 4.8 BuildingModule

1. 创建 `client/domain/state/BuildingState.java`
2. 实现 `EventReactive`

### 4.9 StorageModule

1. 创建 `client/domain/state/StorageState.java`
2. 实现 `Tickable` + `EventReactive` + `NetworkPush`
3. 移动自动刷新逻辑到 `tick()`

### 4.10 CameraModule（最复杂）

1. 创建 `client/domain/state/CameraState.java`
2. 实现 `Tickable` + `EventReactive` + `NetworkPush` + `RenderFrameAware`
3. 将 `CameraInputLayer` 中的 kernel 查找改为构造函数注入

### 每个模块的迁移步骤模板

```
Module Xxx 迁移清单：
[ ] 创建 client/domain/state/XxxState.java（纯 record）
[ ] 创建 client/infrastructure/module/xxx/XxxModule.java
[ ] 实现对应能力接口（Tickable / EventReactive / NetworkPush / RenderFrameAware）
[ ] 将 S2C 处理逻辑从 RtsClientNetworkHandlers 移到 onPacket()
[ ] 将 tick 逻辑从 RtsClientKernel.tick() 移到此模块
[ ] 在 CompositionRoot 中注册
[ ] 编译验证
[ ] 运行时功能验证
[ ] 旧模块标记 @Deprecated
```

### ✅ Phase 4 验证标准

```
./gradlew compileJava  →  BUILD SUCCESSFUL
每个模块迁移后，对应功能正常
无回归
```

---

## Phase 5 — Screen / Presentation 迁移

> **目标：** 将 `screen/` 拆解为 `presentation/` + 基础设施层，剥离 BuilderScreen 中的全局状态。

### 步骤

#### 5.1 提取 ScreenCoordinator

```java
// presentation/screen/ScreenCoordinator.java
public final class ScreenCoordinator {
    // 从 BuilderScreen 提取的屏幕生命周期逻辑
    public void openBuilderScreen();
    public void closeBuilderScreen();
    public void toggleRtsMode();
    public boolean isBuilderScreenOpen();
}
```

#### 5.2 提取 BuilderScreen 面板构造

将 BuilderScreen 构造函数中的面板创建逻辑移到 `PanelRegistry`：

```java
// presentation/layout/PanelRegistry.java (增强版)
public final class PanelRegistry {
    private final List<RtsPanelApi> panels = new ArrayList<>();
    
    public PanelRegistry(ScreenCoordinator coordinator, ModuleManager moduleManager) {
        // 面板在此注册，不再在 BuilderScreen 构造函数中
        register(new TopBarPanel(moduleManager));
        register(new LeftSidebarPanel(moduleManager));
        register(new DownSidebarPanel(moduleManager, coordinator));
        // ...
    }
}
```

#### 5.3 包迁移

```
screen/event/dispatcher/EventDispatcher.java   →  presentation/event/EventDispatcher.java
screen/layout/*                                 →  presentation/layout/*
screen/panel/*                                  →  presentation/panel/*
screen/standalone/BuilderScreen.java            →  presentation/screen/BuilderScreen.java
screen/standalone/RtsCraftTerminalScreen.java   →  presentation/screen/RtsCraftTerminalScreen.java
screen/state/RtsScreenUiStateManager.java       →  presentation/state/RtsScreenUiStateManager.java
```

**做法：** 保留原路径 `@Deprecated` 转发类，或一次性 `git mv` + 全局替换 import。

#### 5.4 静态度量类改造

| 当前 | 改为 |
|------|------|
| `RtsClientPacketGateway.sendXxx()` | `networkPort.send(packet)` |
| `RtsClientPathfinding.staticMethod()` | `PathfindingService` 实例 |
| `formatAmount()` | 已迁移到 `GridSlotRenderer` |

### ✅ Phase 5 验证标准

```
./gradlew compileJava  →  BUILD SUCCESSFUL
BuilderScreen 减少 300+ 行
所有面板正常工作
```

---

## Phase 6 — 旧代码清理

> **目标：** 删除所有不再需要的旧类。

### 清理清单

| # | 删除/修改 | 文件 |
|---|-----------|------|
| 1 | 删除 | `kernel/RtsClientKernel.java` |
| 2 | 删除 | `kernel/FeatureModule.java`（接口） |
| 3 | 删除 | `kernel/ModuleIds.java` |
| 4 | 删除 | `module/perf/` |
| 5 | 删除旧 import | 所有旧模块的旧接口导入 |
| 6 | 添加 @Nullable | 所有可能返回 null 的方法 |
| 7 | 重命名 | `config/RtsThemeManager.java` → `util/color/ColorMath.java` |
| 8 | 整理 | 确保 `domain/` 不含任何 Minecraft import |
| 9 | 删除 | `kernel/ModuleState.java` → 由 `domain/ModuleState.java` 替代 |
| 10 | 删除 | `kernel/EpochClock.java` → 由 `domain/time/Clock.java` + `MinecraftClock` 替代 |

### ✅ Phase 6 验证标准

```
./gradlew compileJava  →  BUILD SUCCESSFUL
无 deprecated 引用
所有模块通过能力接口注册
游戏全功能正常运行
```

---

## 项目文件变更总表

| Phase | 新建 | 修改 | 删除 |
|-------|------|------|------|
| 1 | 9 个 domain 接口 + 5 个 state record | 0 | 0 |
| 2 | 6 个 port 接口 + 4 个 service + 1 个 CompositionRoot | 0 | 0 |
| 3 | 6 个 adapter + 3 个 bridge | 3 bootstrap 文件 | 0 |
| 4 | ~20 个模块文件 | ~10 个旧模块标记 @Deprecated | 0 |
| 5 | ScreenCoordinator + PanelRegistry 增强 | BuilderScreen、所有 screen 包 import | 旧 screen 路径 |
| 6 | 0 | 0 | ~10 个旧内核/模块文件 |

**总计：新建 ~50 个文件，修改 ~30 个文件，删除 ~10 个文件**

---

## 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| Phase 3 双向桥接导致事件重复处理 | 中 | 高 | 在旧 kernel 侧加 `if (newSystemHandled) return;` 守卫 |
| 模块迁移时旧系统仍处理相同事件 | 高 | 中 | 新模块注册成功后，旧模块立即标记 `@Deprecated`，事件不再分发给旧模块 |
| BuilderScreen 抽取 ScreenCoordinator 时遗漏内部状态 | 中 | 高 | 逐字段审查 BuilderScreen，确保状态迁移完整 |
| 组合根初始化时机与 NeoForge 事件顺序冲突 | 低 | 高 | `CompositionRoot.init()` 在 `@Mod` 构造器中调用，早于任何事件 |
