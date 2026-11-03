# 发布维护

公开仓库： https://github.com/xy6176/miniact-tv

当前计划以`v1.2.9.1`标签发布电视体验预览，安装包保持`1.2.9.1 / 67`。应用代码未因本次公开资料整理而改变版本。后续修改应按项目规则维护版本、标签和对应源码，不用同一公开版本反复覆盖不同应用实现。

## 发布文件

- `MiniAct-1.2.9.1.apk`：11月3日交接包中的已签名APK。
- `MiniAct-1.2.9.1-source.zip`：本次公开仓库完整源码快照，不含`.git`、构建缓存和私钥。
- `build-info.json`：源代码提交、版本、构建方式及公开签名证书摘要。
- `SHA256SUMS.txt`：APK、源码快照和构建信息的完整性摘要。

根目录`RELEASE_NOTES.md`用于Release正文。检查并上传上述产物，标记为Pre-release；正式公开后通过未登录访问核对源码、README图片、下载入口和附件摘要。

## 签名

发布私钥不进入仓库或附件。交接APK的公开证书SHA-256：`651997c2c67c8740662ab5fa265aa0d8e32b64567ae9c400ea529e1e45ccbfd1`。

当前本机构建环境的既有签名证书与交接APK一致；它是本地开发签名，不能声称已建立硬件保护的正式发布签名体系。后续更新需保管该私钥以保持覆盖安装；其他开发者或CI默认生成的开发签名通常不同。

## 自动构建与旧配置

`.github/workflows/build.yml`只构建开发APK，不创建Release、不使用发布Secrets。自动构建附件不保证能覆盖正式下载的APK。

旧上游工作流保存在`docs/upstream/release-workflow.yml.reference`，不执行；旧更新元数据与旧校验和不作为本项目发布文件。应用未因本次发布新增自动更新服务。

构建方式见[BUILD](BUILD.md)，功能验证见[VALIDATION](VALIDATION.md)。
