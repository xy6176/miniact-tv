# 构建、签名与版本

## 环境

JDK17，Android SDK平台36，Gradle Wrapper8.14.3，AGP8.11.0，Kotlin2.1.20。安装SDK及构建工具，配置JAVA_HOME和ANDROID_HOME；首次构建需要下载公开依赖。无需MiniAct服务器或真实用户登录凭据。

仓库根目录：

```sh
bash tools/build-miniact.sh
```

该脚本固定当前交付版本环境变量1.2.9.1/67后执行`:app:assembleDebug`。输出`android/app/build/outputs/apk/debug/app-debug.apk`，debug构建仍关闭应用调试。工程Gradle文件的历史回退值是1.2.9/66，直接运行而不设置环境变量会得到不同版本标识；不据此自动修改发布版本。

等价命令：

```sh
HGTV_VERSION_NAME=1.2.9.1 HGTV_VERSION_CODE=67 bash android/gradlew -p android :app:assembleDebug -Pkotlin.compiler.execution.strategy=in-process --max-workers=1
```

需要节省主机内存时，可加`-Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'`。干净环境需要正常下载依赖，不应直接复制本机专用离线SDK补丁。

## 签名

发布资料不包含私钥、签名配置和密码。未配置delivery签名时，debug构建回落到本机debug签名，**不能保证覆盖已安装的交付APK**。后续更新需要维护者保管并沿用相应私钥；仓库公开源码本身不提供签名身份。

工程支持`HGTV_KEYSTORE`、`HGTV_STORE_PASSWORD`、`HGTV_KEY_ALIAS`、`HGTV_KEY_PASSWORD`环境变量，或本机`android/keystore.properties`。只在本机/受保护的构建环境配置，不写进公开文件。版本名称与code本轮均未改动。

## 检查

按项目约定，功能测试与设备验收由用户进行。以下命令供维护者自行选择，不表示本轮已经执行。

相关逻辑用例可按范围运行，例如：

```sh
bash android/gradlew -p android :core:test --tests '*AccountCredentialsTest' --tests '*LowEndPlaybackTest' --tests '*PlaybackDegradationTest'
```

工程继承了Live探测测试。全量运行前阅读测试启用条件，避免把需要联网或账号环境的探测当作离线单元测试。

## 发布配置

旧HGTV发布工作流只留在`docs/upstream/release-workflow.yml.reference`供理解，未放到活动工作流目录。其原包名、产物命名、元数据和签名配置不适用于直接发布MiniAct。当前自动构建仅生成开发APK，不自动创建Release；发布流程见[发布指南](RELEASE.md)。
