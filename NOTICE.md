# 派生来源与第三方声明

## 当前 MiniAct 版本（2026-11-03）

MiniAct 直接基于 HGTV v1.2.9.1，基线提交 `eb83a403a7a84edef35ab2fe1a3f689fc9e9d20d`，继续按 GPL-3.0-only 发布。当前应用名 MiniAct、applicationId `dev.miniact.nativeapp`、namespace `com.hgtv.star`；当前源码仓库为 https://github.com/xy6176/miniact-tv 。

本次公开导入保留 2026-11-03 工程的 Android 源码与资源，整理构建入口、文档及展示素材。派生修改包括电视页面与四主题、横屏优先推荐体验、默认精选启动、账号收藏刷新、方向焦点保护及长剧选集。完整边界见 `docs/miniact/PROVENANCE.md`。

以下原有归属及历史修改声明保留；其中旧品牌、包名、仓库和主题数量描述各自历史阶段，不代表当前 MiniAct 配置。

## 1. 派生来源：N3urda/hongguoTV（GPL-3.0）

本仓库是 [N3urda/hongguoTV](https://github.com/N3urda/hongguoTV) 的 fork，**自 2026-10-06 起在上游基础上修改**，
按 GPL-3.0-only 派生。上游代码的著作权归其作者（N3urda、ermilylu 等），派生部分归本仓库维护者。
仓库根的 `LICENSE` 与上游同一份文本，未作改动。

本版相对上游的改动（GPL-3.0 §5(a) 要求的修改声明）：

- 移除 React Native 电视版、iOS 工程与 Node / Docker 内容服务，只保留 Android TV 原生客户端；
- 可构建的 Gradle 工程从 `kotlin-tv/` 移到 `android/`，仓库根只留文档与工具；
- 包名 `com.hongguotv.nativeapp` → `com.hgtv.star`，应用名改为「红果TV」；
- 重做界面（新拟态与十四套材质主题）、发布流程与产物命名。

派生基线为上游 `main` 的 `eea73e6`（`fix: release updater TLS connections off the main thread`，2026-09-23），
此后上游无新提交。逐项改动见 [变更记录](docs/CHANGELOG.md)。

## 2. 第三方依赖声明

`core` 中内容请求签名、Spade URL / key 处理、MP4 CENC 处理是以下 GPL-3.0 源码的 Kotlin 移植：

- drpys, `spider/js/红果果[短].js`
- https://github.com/woshishiq1/drpys/tree/22261adfa31435e3b3ef5a730b8a2a75bcaf1715
- 本版改动：完整 Kotlin 实现；签名只选择已验证的 hash branch 0；加密媒体通过 Media3 DataSource 直接读取；增加样本边界检查、子样本处理和 AVC 原始编码恢复。

本仓库的 Android TV 原生应用源码和派生实现以 GPL-3.0-only 发布，许可证全文见 `LICENSE`
（与仓库根目录 `LICENSE` 同一份文本）。源码仓库：https://github.com/pan8664716/HGTV ，
发布 APK 时同时提供对应源码、Gradle 包装器和构建说明。APK 不包含 Node、React Native、JavaScript 引擎或该 JavaScript 源文件。开发期用于逐行核对的上游 JS 参考实现只在本机使用，不随本仓库分发，也不参与应用构建或运行。

其他依赖：

- ZXing core（本机生成手机输入与更新二维码）：https://github.com/zxing/zxing — Apache-2.0
- Kotlin 标准库：https://github.com/JetBrains/kotlin — Apache-2.0
- AndroidX Media3：https://github.com/androidx/media — Apache-2.0
- OkHttp / Okio：https://github.com/square/okhttp / https://github.com/square/okio — Apache-2.0
- Bouncy Castle SM3：https://github.com/bcgit/bc-java — MIT
- Android desugar_jdk_libs：https://github.com/google/desugar_jdk_libs — GPL-2.0 with Classpath Exception
- org.json（仅 JVM 工具，APK 使用 Android 内置实现）：https://github.com/stleary/JSON-java — 公有领域声明
- JUnit（仅测试）：https://github.com/junit-team/junit4 — EPL-1.0
- Microsoft Fluent Emoji（弹幕表情图，`app/src/main/assets/danmaku_emoji/`，已转为 128px WebP）：
  https://github.com/microsoft/fluentui-emoji — MIT，许可证全文随图放在
  `app/src/main/assets/danmaku_emoji/LICENSE.txt`

应用图标与电视横幅（`app_icon.xml`、`tv_banner.png` 及其派生位图）自本仓库创建起维护，并在上游基础上重绘（2026-10-12 改为扁平白底）。这里的应用不是内容平台官方客户端。

应用内「设置 → 开源许可」弹窗展示的就是本文件的信息摘要：GPL-3.0-only 的许可范围、
源码仓库 `pan8664716/HGTV`、上面的第三方组件清单，
以及「本机记录只保存在这台设备上」这一条边界。改这里时同步改 `MainActivity.licenseText()`。

## MiniAct 派生改动（2026-10-31）

本地派生以 HGTV v1.2.9.1 为基线；调整品牌、主题、导航、搜索与默认遥控交互，隔离会话存储。包名 dev.miniact.nativeapp。原作者与第三方声明保留，继续使用 GPL-3.0-only。详见 MINIACT.md。


## MiniAct 首页与筛选重构（2026-11-01）

新增可回收的电视首页、轻量海报绘制及筛选工具栏，改动说明见 MINIACT.md。新增界面依赖 AndroidX RecyclerView 1.3.0（The Android Open Source Project，Apache-2.0）；版本与许可信息来自该组件发布 POM。


## MiniAct 控件调整（2026-11-01）

重构精选操作区、搜索栏与通用按钮材质，新增原创手机轮廓矢量图标；继续使用 GPL-3.0-only，详见 MINIACT.md。


## MiniAct 焦点与片单（2026-11-01）

调整按钮状态材质，重构片单菜单与遥控焦点路径。沿用 GPL-3.0-only，修改说明见 MINIACT.md。


## MiniAct 播放选项（2026-11-01）

新增电视播放侧面板与原创细线矢量图标，调整长按事件兼容及分级返回，继续使用 GPL-3.0-only。详见 MINIACT.md。


## MiniAct 产品体验修正（2026-11-01）

改进搜索与遥控输入、选集与播放浮层、自动连播时的设置保持、继续观看大字号布局及操作反馈；新增搜索排序与并发逻辑检查。沿用 GPL-3.0-only，完整改动与验证边界见 MINIACT.md。


## MiniAct 视觉一致性（2026-11-01）

统一控件材质与选集状态表达，调整选集布局、登录输入框和首页查看全部卡片；继续遵循GPL-3.0-only，详见MINIACT.md。

2026-11-02：MiniAct 修改推荐默认启动、可选横屏排序、偏好切换与快照隔离，并调整界面提示用语；仍为 GPL-3.0-only 衍生版本。

2026-11-02：MiniAct 全局电视界面调整，新增统一视觉参数、原创导航线形图标、横幅比例适配及轻量内容卡片，继续遵循 GPL-3.0-only。参考图仅用于说明设计方向，不作为平台剧照或封面分发。

本轮后续：修复继续观看比例与背景场景隔离，新增右侧内嵌选集页，调整播放面板透明度。

2026-11-02：统一浏览栏目的中性背景与导航玻璃，移除首页精选海报对浏览背景的染色；详情及播放浮层保持场景隔离。继续遵循 GPL-3.0-only。

2026-11-02：新增原创原生绘制的固定光照环境，调整导航与播放浮层的玻璃边缘、透射层次及控件焦点色。沿用 GPL-3.0-only，无新增第三方素材或依赖。

2026-11-02：首页精选改为大图布局，读取平台横版封面字段，使用真实封面的裁切与渐隐回退；调整分层标题、观看与详情入口及焦点恢复。无新增第三方图片或依赖，沿用GPL-3.0-only。

2026-11-02：重构详情与播放收藏入口及账号同步确认流程，增加同步失败、延迟可见、会话隔离等自动检查；调整浏览背景与玻璃层的亮度关系。沿用GPL-3.0-only，无新增第三方依赖。

2026-11-02：新增MiniAct四种原生主题及主题偏好保存，统一背景、玻璃与控件配色，缩小低配设备的首页大图合成缓存。光照纹理由原生绘图生成，无新增第三方素材或依赖；继续遵循GPL-3.0-only。兼容性核查与真机范围见对应交付说明。

2026-11-03：改进账号收藏刷新、内容方向焦点保护与详情选集路径；新增播放选集的浏览状态、分段导航和数字定位。无新增第三方依赖，沿用GPL-3.0-only；具体修改和验证范围见MINIACT.md及对应交付记录。
