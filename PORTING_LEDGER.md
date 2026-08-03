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
| C001 | GUI | `GuiGraphics` 在 1.19.2 不存在 | 单一 `RtsGuiContext` 桥接到 `PoseStack` / `GuiComponent` | 生产 UI、tooltip、scissor、文本框 | 待处理 | UI Core/Kit 测试、快照、实机 |
| C002 | 数据 | 新版 ItemStack / 数据接口差异 | `RtsStackDataAccess` 统一 NBT 读写与 fail-closed 校验 | 蓝图、插件、任务、UI 状态 | 待处理 | 往返、损坏输入、旧存档 |
| B002 | 网络 | Forge 1.20.1 SimpleChannel/API 差异 | 1.19.2 SimpleChannel，显式 discriminator 和主线程入队 | 全部 C2S/S2C 消息 | 待处理 | 编解码边界、权限、重连 |
| B003 | 能力 | 1.20.1 Capability 调用差异 | `ForgeCapabilities` + `LazyOptional` 生命周期 | linked storage、工具、流体、能量 | 待处理 | simulate/execute、回写、失效 |
| C003 | 渲染 | 世界渲染阶段和缓冲生命周期差异 | 1.19.2 渲染事件 + 私有 buffer + 状态成对恢复 | Ghost、选择框、范围剔除 | 待处理 | 原版、优化模组、专服 |
| D001 | 可选兼容 | JEI/Jade/Create 目标 API 不同 | 核心稳定后逐个恢复，不排除源码 | compat 包 | 待处理 | 缺失依赖启动、组合测试 |

## 阶段状态

- 阶段 0：锚点、工作树、目标范围已确认；首个可审计提交准备中。
- 阶段 1：构建坐标、资源元数据、JEI/Jade API 坐标均已降级；`gradlew tasks` 与 `compileClasspath` 依赖解析通过。
- 阶段 2—10：尚未开始。

## 人类实机验收保留项

- 原版客户端完整新玩家路径与真实操作手感。
- 生存/创造下小、中、超大任务及 Ctrl+Z / Ctrl+Y。
- 保存退出、重进恢复、多人远程储存和第三方菜单。
- 高 Minecraft GUI scale × RTS UI scale、四语、低/高分辨率。
- Create、JEI、Jade、常见优化模组组合。
