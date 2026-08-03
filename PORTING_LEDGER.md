# Forge 1.19.2 搬运台账

## 冻结基线

- 产品与架构权威：NeoForge 1.21.1 `1.1.6-patch2`，提交 `5a7ca132a4f2dd136a0b5084eaaea3ea9680509f`。
- 实际强搬来源：Forge 1.20.1 `1.1.6-patch2`，提交 `a7e52d5f612fef41b70f1ea2d0fc234cd9693a0c`。
- 目标平台：Minecraft 1.19.2、Forge 43.5.0、Mojang official mappings、Java 17。
- 临时登陆分支：`codex/forge-1.19.2-landing`；登陆期间不推送，不修改 `main` 或 `forge-1.20.1`。
- 完成边界：追平 `1.1.6-patch2`；不顺带吸收 Sable、PR #140、PR #127、Fabric 或 NeoForge 26.1 后续实现。

## 端口台账

| ID | 域 | 旧 API / 根因 | 1.19.2 替代契约 | 预计影响 | 状态 | 验证 |
|---|---|---|---|---|---|---|
| B001 | 构建 | Minecraft 1.20.1 / Forge 47.4.16 | Minecraft 1.19.2 / Forge 43.5.0 | Gradle、元数据、运行任务 | 已完成 | `tasks` 与 `compileClasspath` 解析通过 |
| A001 | 纯核心 / UI | 共享源码可能泄漏平台 API | Core / Kit / Preview 原样复用并保持 Java 8 字节码 | UI Core、UI Kit、headless preview | 已完成 | Core/Kit 测试、66 场景快照、376 个 class 字节码通过 |
| C001 | GUI | `GuiGraphics` 在 1.19.2 不存在 | 单一 `RtsGuiContext` 桥接到 `PoseStack` / `GuiComponent` | 生产 UI、tooltip、scissor、文本框 | 自动化与基础运行通过，待手感验收 | 完整 `build`、UI Core/Kit、66 场景快照、四语资源与样式债验证通过；客户端完成资源重载、声音与纹理图集初始化并进入专服世界 |
| C002 | 数据 | 新版 ItemStack / 数据接口差异 | `RtsStackDataAccess` 统一 NBT 读写与 fail-closed 校验；`RtsItemStacks` 保留复制后的 NBT/耐久/能力数据 | 蓝图、插件、任务、UI 状态 | 自动化通过，待存档实测 | 722 项 JVM 测试全绿，含 codec 往返、损坏/超限输入、任务持久化与跨处理器 NBT 变体提取；待旧存档启动验证 |
| B002 | 网络 | Forge 1.20.1 SimpleChannel/API 差异 | 1.19.2 SimpleChannel，显式 discriminator、C2S/S2C 方向和主线程入队 | 全部 C2S/S2C 消息 | 真实握手通过，待重连/过期回复实机验收 | 网络所有权/方向契约通过；客户端直连本地专服，双方确认 modded handshake，玩家完成登录和进服，未出现协议、方向或解码错误 |
| B003 | 能力 | 1.20.1 Capability 调用差异 | `ForgeCapabilities` + `LazyOptional` 生命周期 | linked storage、工具、流体、能量 | 核心 GameTest 通过，待第三方组合 | linked chest、自动储存、工具耐久、流体与回填相关 GameTest 通过；待 AE2/RS/机械组合与失效实测 |
| C003 | 渲染 | 世界渲染阶段和缓冲生命周期差异 | 1.19.2 渲染事件 + 私有 buffer + 状态成对恢复；实心盒显式写入六面顶点 | Ghost、选择框、范围剔除 | 原版基础运行通过，待优化模组/视觉验收 | 渲染契约、私有 buffer 生命周期、Skeleton 压力场景与完整 `build` 通过；原版客户端完成纹理图集初始化并进入专服世界，专服无客户端类加载崩溃 |
| D001 | 可选兼容 | JEI/Jade/Create 目标 API 不同 | 核心稳定后逐个恢复，不排除源码 | compat 包 | 缺失依赖运行通过，待组合启动 | JEI/Jade/Create 源码均参与 `compileJava`；未安装可选模组时客户端与专服均成功启动，Mixin 缺失目标仅为可选警告；本地无可用 1.19.2 Create/完整 JEI 运行 JAR，组合测试保留 |
| C004 | 注册表访问 | 1.19.2 不存在 `BuiltInRegistries`，创造页签也尚未注册表化 | `RtsBuiltInRegistries` 委托旧 `Registry` 并提供只读页签视图 | 81 个物品、方块、流体、声音与分类调用文件 | 已完成 | 编译错误 425→346→0；全树 `compileJava` 通过 |
| C005 | 资源标签 | patch2 软替换标签含 1.20 新方块 | 1.19.2 标签只列该版本实际存在的方块，代码 fallback 保留跨版本语义 | 蓝图软替换数据包加载 | 已完成 | 干净专服重载不再报告 `torchflower` / `pitcher_plant` / `pink_petals` 缺失 |
| C006 | 聚合存储 | 跨处理器批量提取会在首个结果后继续仅按物品 ID 抽取 | 首个真实结果锁定本次提取的完整 NBT/耐久/能力变体 | AE2/RS/BD/链接容器中的高价值物品 | 已完成 | 回归测试证明第二个不同 NBT 变体留在原处理器中；工具租约、真实堆叠回填、流体 simulate/execute 与 51 项 GameTest 一并审计通过 |

## 阶段状态

- 阶段 0：锚点、工作树、目标范围已确认；首个可审计提交准备中。
- 阶段 1：构建坐标、资源元数据、JEI/Jade API 坐标均已降级；`gradlew tasks` 与 `compileClasspath` 依赖解析通过。
- 阶段 2：UI Core / Kit 隔离、单元测试、headless 快照与 Java 8 字节码验证全部通过，无需建立 1.19.2 分叉实现。
- 阶段 3：注册表、旧式创造栏、方块状态 NBT、ItemStack 复制、实体/方块查询与对象注册差异已适配；完整生产源码和 GameTest 源码均通过 `compileJava`。
- 阶段 4：生产 GUI、文本框、按钮、配方与语言接口已接入 1.19.2 契约；最终干净构建的完整 JVM 测试 722/722 通过（在原 720 项基础上新增网络方向与跨处理器 NBT 变体回归）。
- 阶段 5：完整 `build` 已通过，包括重混淆 JAR、3 项性能债门禁、UI 隔离、66 场景快照、四语/图标资源、376 个 Java 8 UI class 校验。
- 阶段 6：干净专用服务端已到达 `Done`，通过本地 RCON 正常 `stop`，三维度保存完成，Gradle 运行任务成功退出；未发生客户端类加载崩溃。
- 阶段 7：1.19.2 GameTestServer 玩家资料缓存脚手架已补齐；51/51 必需 GameTest 全部通过并正常保存关闭，覆盖建造、破坏、储存、工具、漏斗、蓝图、多人隔离、任务持久化与三次历史。
- 阶段 8：原版客户端已完成资源重载、OpenAL、纹理图集初始化，并直连本地专服进入世界；关闭客户端后服务端正常处理离线，再经 RCON 保存三维度并成功退出。
- 阶段 9：真实 Forge modded handshake 已通过，客户端/服务端均无协议、方向、编码或解码错误；重连、过期回复与多人长时间交互仍保留为人类实机验收。
- 阶段 10：缺失可选依赖的客户端/专服启动已通过；本地没有匹配 1.19.2 的 Create 与完整 JEI 运行 JAR，第三方组合测试不伪造结论，保留到测试包验收。

## 人类实机验收保留项

- 原版客户端完整新玩家路径与真实操作手感。
- 生存/创造下小、中、超大任务及 Ctrl+Z / Ctrl+Y。
- 保存退出、重进恢复、多人远程储存和第三方菜单。
- 高 Minecraft GUI scale × RTS UI scale、四语、低/高分辨率。
- Create、JEI、Jade、常见优化模组组合。
