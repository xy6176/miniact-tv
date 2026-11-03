# Android、电视与低配适配

截至2026-11-02：有兼容实现，真机矩阵尚不完整。

| 范围 | 证据与结论 |
| --- | --- |
| Android4.3及更早 | 不支持，低于minSdk19 |
| Android4.4–5.1 | 声明可安装，含Multidex与旧API兼容处理；无真机验收 |
| Android6–10 | 在声明兼容范围；Android9的TCL只测安装与启动，其余未逐版本验证 |
| Android11 | Vidda X2026上完成主要产品功能、主题、播放与收藏检查 |
| Android12及以后 | 尚未真机覆盖，不承诺全部厂商系统正常 |
| 非Android电视 | 不能直接安装APK |

工程minSdk19、target29、compile36，Media3固定1.4.1。固定版本的[官方源码](https://github.com/androidx/media/blob/1.4.1/constants.gradle)声明minSdk19；当前新版Media3的最低版本不能直接用于判断这个旧版本。compile36不等于Android16实测通过。

APK没有携带`.so`库，不受单一打包CPU架构限制；媒体解码依然依赖系统。当前视频使用HEVC/H.265，软件模式仅选择系统已有软件解码器，没有独立FFmpeg视频解码库。[ExoPlayer格式说明](https://developer.android.com/media/media3/exoplayer/supported-formats)说明默认解码能力取决于平台。老电视可能能安装但不能播放。

## 低配策略

- 已知物理内存≤2.5GB或应用堆≤96MB时进入整机低配策略；内存未知时使用系统标记与堆限制回退。内存判断不代替CPU/GPU实测。
- 低配减少动画、阴影位图等装饰；可手动减少动画/透明效果。
- 图片低内存策略使用RGB565、8–32MB缓存上限及3个工作线程；它与整机低配判断独立。
- 首页大图合成宽度低配640px、普通960px；滚动复用缓存。
- 光照图192×108，主题材质不实时全屏模糊。
- 探测HEVC解码能力后选择默认清晰度，保留用户手动选档。

现有Vidda约4GB物理内存，日志lowEnd=false，不构成1–2GB真机验收。TCL本轮未测性能。27项低配/降级逻辑检查通过也不能证明每台低配电视流畅。建议后续补齐1GB/2GB、不同SoC、旧系统和新版系统矩阵。
