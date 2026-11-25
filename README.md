# 白金游戏

白金是项目代号。项目目标是一款 Android 原生横屏、简体中文、离线单机的原创武侠 RPG。玩家分配角色四维，通过委托积累潜能，拜师习武，再在文字回合战中检验成长。地图、角色、门派、任务、文本和视听内容采用原创设计。

## 技术基线

| 范围 | 约定 |
| --- | --- |
| 平台 | Android 手机，横屏触控 |
| 语言与构建 | Kotlin、Gradle Wrapper |
| 界面 | 自定义 Android `View` 用 `Canvas` 绘制地图。菜单、对话、角色状态和日志用 Android 原生控件 |
| 工程模块 | `app` 承接 Android 界面、输入、资源和存档。纯 Kotlin/JVM 模块 `game-core` 负责游戏规则 |
| 内容与进度 | 随应用打包的 JSON 内容配置，应用私有目录内的单槽版本化存档 |

## 当前阶段

T19「师门接引与备战开放」已完成。拜师并习得两项入门武学后，在师父的「师门安排」依次接取师门接引、确认试武安排、交付。交付一次获得轻木棍、20 铜钱、基本棍法和校场棍法，并首次运用校场棍法。随后可在背包的「装备」页穿戴，也可到镇西裁缝店找中年妇人买布帽、行脚衣、布护腕和软底布鞋，四件共 20 铜钱。当前写入存档 v9，内容版本 3，兼容旧结构和旧地图。[T19 验收](docs/task-overview.md#t19-验收) · [开局护具货单](docs/modules/items.md#交易)

装备外观样板已覆盖轻木棍、布帽、行脚衣、布护腕和软底布鞋。男女主角在探索地图与装备页立像上显示当前穿戴的样板装备，图层随朝向和步态变化。[装备外观](docs/art/player-walk.md#装备外观)

T10「横屏文字切磋页面」已完成。和校场陪练对话，点「切磋 → 开始切磋」即可交手。战斗页显示双方气血和内力，支持分段播放、显示整回合、回看日志和回到最新。胜利确认战利品后回到交战地点。被放过时直接返回，死亡时可读取战前存档。[T10 验收](docs/task-overview.md#t10-验收)

T09「战斗提交、战利品与战败恢复」已完成。单槽存档写入结构 v8、内容版本 2，兼容 v1～v7。「继续游戏」直接恢复最新保存的完整回合、待领战利品或死亡结果。[T09 验收](docs/task-overview.md#t09-验收)

T08「开局回合与伤害结算」已完成。内核可用建角后的角色发起校场切磋，结算玩家普攻、陪练应对、双方气血和中文日志。[战斗规则](docs/modules/combat.md#战斗规则) · [T08 验收](docs/task-overview.md#t08-验收)

T07「两派入门与初次运用」已完成。到校场西侧与师父交谈，打开「拜师与请教」，可择青衡门或渡川门，学习基本拳脚和守线掌或回潮拳。初学本门拳法时自动运用，右上角「武学」显示门派、潜能、已学等级、运用和徒手普攻威力。[开局武学](docs/modules/martial-arts.md#开局) · [T07 验收](docs/task-overview.md#t07-验收)

T06「NPC 对话与开局委托」已完成。到 NPC 的地图交互格打开对话，可分别查看事务和闲谈话题。与老婆婆对话会随机派一件家务，做完即发奖。师父发布校场口信，右上角「任务」显示目标和需要交付的对象。[委托流程](docs/modules/quests/design.md#开局任务) · [T06 验收](docs/task-overview.md#t06-验收)

主角家已改为错开布局的主卧、客房、堂屋和灶房。东门接堂屋，主卧床边到街上仍是 5 步。[家中格图](docs/modules/world/player-home.md) · [室内美术](docs/art/player-home.md)

镇东主街从西入口通到武馆，过桥后分向药摊、衙门和村长家。镇西强化家门桥头与南井站位，衙门围墙和未开放出口的木栅标记与现有通行格对应。[小镇街巷](docs/modules/world/town.md#镇东街巷)

T05「开局背包与吃喝」已完成。小镇地图已按现有 NC2000 图片修正：镇西 20 × 12，连通镇西与镇东的水道、四处桥位、四栋建筑入口及家中、老婆婆家、豆腐店、裁缝店的独立室内使用现有手绘素材复现。床边从东门出屋需 5 步，到老婆婆屋内面前共 19 步，可花 4 铜钱买口粮；南井南侧提供饮水。[地图与迁移](docs/modules/world/town.md) · [图片依据](docs/reference/nc2000-west-layout.md) · [本轮验收](docs/task-overview.md#镇西布局修正验收)。镇西已补充门前器物、晾衣、布摊、晒粮和菜畦，并增加店招、檐下灯笼、豆腐案、柴棚、货车、花草、鸡与猫；水纹、衣物、炉灶和动物有轻微环境动态。[日常细节](docs/art/town-well.md#镇西的日常细节)。河道已补齐道路和树旁的缺段，边缘进出水口延伸至画面外。[河道接通验收](docs/task-overview.md#小镇河道连通修正)。主角移动已按「走路.png」补齐男女四向三帧步态，保持当前画风。[四向行走](docs/art/player-walk.md)。

所有计划内的玩法和委托按同一个完整版本开发。真机移动、中文日志和重启读档用于开发过程检查。完整验收覆盖从建角到原创主线通关，也覆盖任务表里的支线、探索和结算。全部内容完成并通过完整验收后，再考虑部署上线。[玩法范围](docs/gameplay.md) · [验收规则](docs/acceptance.md)

## 本地构建与安装

| 工具或依赖 | 工程配置与复验版本 |
| --- | --- |
| JDK 与 JVM | Gradle Daemon 和两模块工具链固定为 Java 17，字节码目标 JVM 17。本机使用 Azul Zulu `17.0.21.1+2-LTS` |
| Gradle | Wrapper `8.13`，配置发行包 SHA-256 |
| Kotlin | Gradle 插件及标准库 `2.2.21` |
| Android Gradle Plugin | `8.11.1` |
| Android SDK | `compileSdk` / `targetSdk` 36，`minSdk` 26。复验使用 Android 36 平台包修订版 2、Build Tools `35.0.0`、Command-Line Tools `22.0`、Platform Tools `37.0.3` |
| JSON | Kotlin 序列化插件 `2.2.21`，`kotlinx-serialization-json` `1.9.0` |
| 存档提交 | AndroidX Core `1.17.0` 的 `AtomicFile` |
| JVM 测试 | JUnit `4.13.2` |

使用 JDK 17，将 `JAVA_HOME` 指向 JDK 根目录。安装 `platforms;android-36`、`build-tools;35.0.0` 和 `platform-tools`，将 `ANDROID_HOME` 指向 SDK，或在本机 `local.properties` 设置 `sdk.dir`。[Android SDK 工具](https://developer.android.com/tools/sdkmanager)

在项目根目录运行以下 PowerShell 命令。

```powershell
.\gradlew.bat --version
.\gradlew.bat :game-core:validateContent --console=plain
.\gradlew.bat :game-core:test :app:testDebugUnitTest :app:assembleDebug --console=plain
.\gradlew.bat :app:lintDebug --console=plain
```

Android 构建会执行 `:game-core:validateContent`。遇到无效配置时，构建输出给出 JSON 字段路径和原因。内核和存档的 JVM 测试报告分别位于 `game-core/build/reports/tests/test/index.html` 和 `app/build/reports/tests/testDebugUnitTest/index.html`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。[随包样例与校验](docs/architecture.md#随包样例与校验)

同时连接多台设备时，在 `adb` 子命令前加 `-s` 与设备序列号。以下命令使用 `ANDROID_HOME`，若只配置了 `sdk.dir`，将 `$adb` 指向该 SDK 下的 `platform-tools/adb.exe`。

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb devices -l
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.baijin.game/.MainActivity
```

首次打开后点「新游戏」，选择性别，手动分完 12 点或点「均衡分配」，再点「开始游戏」。用左下摇杆从床边向南三步、向东两步到小镇街上，再向西一步回屋。拨住摇杆逐格走，松手或穿门后停止。打开「人物」查看四维和相貌，点「打坐」后游戏时间增加 4。用 `& $adb shell am force-stop com.baijin.game` 结束进程并重新启动，点「继续游戏」核对同一场景、位置与读数。已有存档时，先同意覆盖并完成建角才会写入新档。[存档验证](docs/acceptance.md#存档验证)

## 本机 Android 模拟器

本机已安装 Android 官方模拟器，AVD 为 `baijin_t01_api36`，系统为 Android 16 / API 36。SDK 和 AVD 位于项目 `.tmp/T01/` 下。以下 PowerShell 命令打开可交互窗口；窗口关闭后游戏存档仍由 AVD 保留。已运行该 AVD 时直接使用现有窗口。

```powershell
$env:ANDROID_AVD_HOME = (Resolve-Path '.tmp/T01/avd').Path
$env:ANDROID_USER_HOME = (Resolve-Path '.tmp/T01/android-user').Path
Start-Process -FilePath (Resolve-Path '.tmp/T01/sdk/emulator/emulator.exe').Path -WindowStyle Normal -ArgumentList @('-avd', 'baijin_t01_api36', '-port', '5558', '-no-snapshot', '-gpu', 'swiftshader_indirect')
```

T05 至 T07、T09 至 T10 的开发验收覆盖模拟器 16:9 与 20:9。手机触控仍待单独验证。设备连接后按上方命令加 `-s emulator-5558` 安装和启动调试包。

## 项目文档

| 文档 | 用途 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | AI 协作入口、项目技能、任务边界和完成口径 |
| [术语表](GLOSSARY.md) | 白金玩法中的约定用语 |
| [玩法范围](docs/gameplay.md) | 产品范围、完整版本内容和游戏内进度 |
| [玩法模块索引](docs/gameplay-rules.md) | 六个玩法模块的规则、数值和验收入口 |
| [任务总览](docs/task-overview.md) · [生成提示词](docs/task-overview-prompt.md) | 开工顺序、内容前置、交付物和完成条件 |
| [任务与经济](docs/modules/quests/README.md) | 任务派发、奖励结算和补给预算 |
| [物品与装备](docs/modules/items.md) | [装备目录](docs/modules/equipment.md)、商贩、交易、穿戴与掉落规则 |
| [NPC 人物索引](docs/modules/npcs.md) | 按地点查阅项目角色和掌机版逐人资料 |
| [小镇井边美术参考](docs/art/town-well.md) | 概念原图、地图与人物画法、探索界面及生图参考 |
| [NC2000 白金版参考](docs/reference/nc2000.md) | 掌机版任务、装备与商贩、换物、秘笈和信物的资料 |
| [模块边界与架构](docs/architecture.md) | 输入、游戏状态、内容配置、横屏界面和存档的归属 |
| [验收规则](docs/acceptance.md) | 完整版本验收、JVM 测试、构建与真机检查 |
