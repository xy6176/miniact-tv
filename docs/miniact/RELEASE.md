# 发布维护

公开仓库： https://github.com/xy6176/miniact-tv

当前公开预览标签为 `v1.2.9.2`，安装包版本为 `1.2.9.2 / 68`。`v1.2.9.1` 和 `v1.2.9.1-preview.1` 是此前的导入与预览快照。后续发布使用递增的版本码和对应源码标签。

## 发布文件

- `MiniAct-1.2.9.2-68.apk`：用原签名构建的安装包。应用内更新按 `MiniAct-<版本>-<版本码>.apk` 识别附件。
- `MiniAct-1.2.9.2-source.zip`：公开仓库对应标签的源码快照。
- `build-info.json`：源代码提交、版本、构建方式及公开签名证书摘要。
- `SHA256SUMS.txt`：APK、源码快照和构建信息的完整性摘要。

根目录`RELEASE_NOTES.md`用于Release正文。检查并上传上述产物，标记为Pre-release；正式公开后通过未登录访问核对源码、README图片、下载入口和附件摘要。

## 签名

发布私钥不进入仓库或附件。交接APK的公开证书SHA-256：`651997c2c67c8740662ab5fa265aa0d8e32b64567ae9c400ea529e1e45ccbfd1`。

当前本机构建环境的既有签名证书与交接APK一致；它是本地开发签名，不能声称已建立硬件保护的正式发布签名体系。后续更新需保管该私钥以保持覆盖安装；其他开发者或CI默认生成的开发签名通常不同。

## 自动构建与旧配置

`.github/workflows/build.yml`只构建开发APK，不创建Release、不使用发布Secrets。自动构建附件不保证能覆盖正式下载的APK。

旧上游工作流保存在 `docs/upstream/release-workflow.yml.reference` 供参考。MiniAct 的更新入口直接读取公开 GitHub Releases 的附件元数据和校验摘要。

构建方式见[BUILD](BUILD.md)，功能验证见[VALIDATION](VALIDATION.md)。
