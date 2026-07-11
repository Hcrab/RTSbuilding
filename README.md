
# RTS Building: Build From Above

**Language / 语言:** [English](#english) | [中文](#chinese)

Join the Discord to chat and provide feedback: https://discord.gg/9Pw6vZfAm

License: LGPL-3.0-only for the current source tree and releases starting with
1.0.3-beta / 0.0.3Beta. Copyright (C) 2026 JerryLunar. Earlier public
releases remain under the license terms that accompanied those releases.

---

<a id="english"></a>

## English

### Build like an RTS, directly from above

![Preview](https://i.postimg.cc/L89yH82V/gan-guo-zhan-shi.gif)

Tired of running back and forth between chests while building?

**RTS Building: Build From Above** adds an RTS-style building panel that lets you
plan, place, mine, and manage materials from a top-down view. Instead of
constantly moving your player body around your base, you can build more like you
are managing a strategy game.

It is designed for complex modpacks, large bases, skyblock platforms, factory
layouts, and any situation where building from the player's first-person view
starts to feel slow or repetitive.

### Link chests, select blocks, and place them from above

![Chest Scan and Build](https://i.postimg.cc/WzTCR63L/chestscan-and-build.gif)

Scan nearby storage, choose blocks from a large building panel, and place them
directly from above. The mod helps reduce the constant "open chest, grab block,
walk over, place block, repeat" loop.

### Interact with the world normally

![Interact](https://i.postimg.cc/sgTh5VS4/interact.gif)

Even while using the top-down building interface, you can still interact with
blocks and the world in a natural way. The goal is to make large-scale building
smoother and less exhausting.

### A massive hotbar for your short-term memory

![Massive Hotbar](https://i.postimg.cc/XJn04kr2/image.png)

Keep more blocks and tools visible at once, so you do not have to constantly
remember where every material is stored or keep swapping items in and out of
your regular hotbar.

### Current status

This mod is currently in **beta**, but it is playable.

I have mainly developed and tested it in **ATM 10 To the Sky**, and at this
point I already cannot play without it.

#### Minecraft 1.21.1

- The most complete version.
- Used extensively in my ATM10 To the Sky playthrough.
- Core features are mostly stable and robust.
- Survival balance is still being tested.
- A skill tree system for unlocking functions is available and can be enabled in
  the config.

#### Minecraft 1.20.1

- Similar to the 1.21.1 version.
- Theoretically, it should function almost the same as the 1.21.1 version.
- It has received some light gameplay testing on adventure modpacks like Closing
  Song, but not as much as 1.21.1.

### Contributors and credits

**Hcrab** is the project author and primary maintainer, responsible for the
overall design direction, core gameplay, major feature implementation, releases,
and long-term maintenance.

Special thanks to the following contributors:

- **Yiran**: core developer and lead Artist. Helped with structural refactors, parts of the UI
  implementation, community management, and systematizing bug reporting and todo
  tracking.
- **凌墨问**: core community collaborator. Helped with community outreach,
  feedback collection, and the project journal website.
- **卓清婉**: major code contributor. Helped resolve critical bugs, contributed
  compatibility and quality-of-life pull requests, and assisted with todo
  management.
- **可怜Bot**: major code contributor. Helped investigate and review critical
  bugs, shaped bug-fix approaches, and submitted multiple fix pull requests.
- **[ReConstruction-127](https://github.com/ReConstruction-127)**: Lead Artist, assisted in creating most of the mod’s textures.
- **[Eternal-Snowstorm](https://github.com/Eternal-Snowstorm)**: helped
  organize the issue templates and provided guidance on licensing.

### Branches and source builds

- `main`: Minecraft 1.21.1 / NeoForge.
- `forge-1.20.1`: Minecraft 1.20.1 / Forge.

To build from source, install Java 21 and run:

```bash
./gradlew build --no-daemon --no-configuration-cache
```

On Windows:

```powershell
.\gradlew.bat build --no-daemon --no-configuration-cache
```

---

<a id="chinese"></a>

## 中文

### 像 RTS 一样，从上帝视角建造

![展示](https://i.postimg.cc/L89yH82V/gan-guo-zhan-shi.gif)

受够了建造时在箱子之间来回翻材料吗？

**RTS Building: Build From Above** 添加了一个类似即时战略游戏的建造面板，
让你可以从俯视视角规划、放置、挖掘和管理材料。你不再需要为了放几块方块就
不停移动玩家本体、在各种箱子之间翻找材料，而是可以像在管理一款策略游戏一样
建造基地。

这个模组很适合大型整合包、复杂基地、空岛平台、工厂布局，以及任何第一人称
建造开始变得繁琐的场景。

### 连接箱子，选择方块，然后从上方放置

![箱子扫描与建造](https://i.postimg.cc/WzTCR63L/chestscan-and-build.gif)

扫描附近的储物箱，从大型建造面板中选择方块，然后直接从俯视视角放置。它的
目标是减少“开箱子、拿方块、走过去、放方块、再回来”的重复流程。

### 仍然可以正常与世界交互

![交互](https://i.postimg.cc/sgTh5VS4/interact.gif)

即使在使用俯视建造界面时，你也可以自然地与方块和世界进行交互。这个模组的
目标是让大规模建造变得更加顺手、轻松。

### 一个超大的快捷栏，拯救你的短期记忆

![大型快捷栏](https://i.postimg.cc/XJn04kr2/image.png)

一次显示更多方块和工具，减少反复记住材料放在哪里、反复切换快捷栏物品的麻烦。

### 当前进度

这个模组目前仍处于 **Beta 阶段**，但已经可以正常游玩。

我主要是在 **ATM 10 To the Sky** 中开发和测试它的。现在对我来说，这个模组已经
属于“装上就回不去了”。

#### Minecraft 1.21.1

- 当前最完整的版本。
- 已在我的 ATM10 To the Sky 游玩过程中大量使用。
- 核心功能整体已经比较稳定。
- 生存平衡仍在测试中。
- 可以在配置文件中启用技能树，用来逐步解锁功能。

#### Minecraft 1.20.1

- 功能上与 1.21.1 版本类似。
- 理论上应当与 1.21.1 版本表现基本一致。
- 进行过少量游玩测试，比如玩了一阵子落幕曲，但还没有像 1.21.1 那样充分在
  科技整合包中测试。

### 贡献者与鸣谢

**Hcrab** 是项目作者与主要维护者，负责整体设计方向、核心玩法、主要功能实现、
版本发布与长期维护。

特别感谢以下贡献者：

- **Yiran**：核心开发者。协助项目结构重构、核心美术、部分 UI 实现与社区管理，并推动
  bug 反馈与 todo 跟踪流程的系统化。
- **凌墨问**：核心社区协作者。协助社区宣传、收集社区反馈，并开发项目记录网页。
- **卓清婉**：重要代码贡献者。多次参与关键 bug 攻坚，提交兼容性与 QoL 改进
  PR，并协助管理 todo。
- **可怜Bot**：重要代码贡献者。参与关键 bug 排查与审查，协助梳理整体修复
  思路，并多次提交修复 PR。
- **[ReConstruction-127](https://github.com/ReConstruction-127)**：核心美术、协助绘制大部分的模组贴图
- **[Eternal-Snowstorm](https://github.com/Eternal-Snowstorm)**：协助统合 issue
  template，并在 license 相关事项上提供指导。

### 分支与源码构建

- `main`：Minecraft 1.21.1 / NeoForge。
- `forge-1.20.1`：Minecraft 1.20.1 / Forge。

如果要从源码构建，请安装 Java 21，然后运行：

```powershell
.\gradlew.bat build --no-daemon --no-configuration-cache
```

Linux/macOS:

```bash
./gradlew build --no-daemon --no-configuration-cache
```
