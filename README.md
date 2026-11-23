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

当前处于启动与内容接入阶段。`app` 的原生横屏启动页读取随包样例，点按「检查启动」可查看内容版本与位置。`game-core` 提供动作入口、带内容版本的样例状态和配置校验。单槽存档由 T03 接续。[任务总览](docs/task-overview.md#当前开工顺序)

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
| JVM 测试 | JUnit `4.13.2` |

使用 JDK 17，将 `JAVA_HOME` 指向 JDK 根目录。安装 `platforms;android-36`、`build-tools;35.0.0` 和 `platform-tools`，将 `ANDROID_HOME` 指向 SDK，或在本机 `local.properties` 设置 `sdk.dir`。[Android SDK 工具](https://developer.android.com/tools/sdkmanager)

在项目根目录运行以下 PowerShell 命令。

```powershell
.\gradlew.bat --version
.\gradlew.bat :game-core:validateContent --console=plain
.\gradlew.bat :game-core:test :app:assembleDebug --console=plain
.\gradlew.bat :game-core:test :app:assembleDebug --rerun-tasks --console=plain
```

Android 构建会执行 `:game-core:validateContent`。遇到无效配置时，构建输出给出 JSON 字段路径和原因。测试报告位于 `game-core/build/reports/tests/test/index.html`，Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。[随包样例与校验](docs/architecture.md#随包样例与校验)

同时连接多台设备时，在 `adb` 子命令前加 `-s` 与设备序列号。以下命令使用 `ANDROID_HOME`，若只配置了 `sdk.dir`，将 `$adb` 指向该 SDK 下的 `platform-tools/adb.exe`。

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb devices -l
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.baijin.game/.MainActivity
```

打开后确认是横屏。首次点按「检查启动」应显示「样例内容 v1，当前位置 小镇起点」。再次点按查看同一位置。退到后台再返回，反馈仍在。用 `& $adb shell am force-stop com.baijin.game` 结束进程后重新启动，检查按钮可以再次使用。[T02 复验记录](docs/task-overview.md#t02-复验记录)

## 项目文档

| 文档 | 用途 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | AI 协作入口、项目技能、任务边界和完成口径 |
| [术语表](GLOSSARY.md) | 白金玩法中的约定用语 |
| [玩法范围](docs/gameplay.md) | 产品范围、完整版本内容和游戏内进度 |
| [玩法模块索引](docs/gameplay-rules.md) | 六个玩法模块的规则、数值和验收入口 |
| [任务总览](docs/task-overview.md) · [生成提示词](docs/task-overview-prompt.md) | 开工顺序、内容前置、交付物和完成条件 |
| [任务与经济](docs/modules/quests/README.md) | 任务流程、累计领赏、奖励表和补给预算 |
| [物品与装备](docs/modules/items.md) | [装备目录](docs/modules/equipment.md)、商贩、交易、穿戴与掉落规则 |
| [NPC 人物索引](docs/modules/npcs.md) | 按地点查阅项目角色和掌机版逐人资料 |
| [NC2000 白金版参考](docs/reference/nc2000.md) | 掌机版任务、装备与商贩、换物、秘笈和信物的资料 |
| [模块边界与架构](docs/architecture.md) | 输入、游戏状态、内容配置、横屏界面和存档的归属 |
| [验收规则](docs/acceptance.md) | 完整版本验收、JVM 测试、构建与真机检查 |
