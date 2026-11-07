// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.hgtv.star.architecture.AppExecutors
import com.hgtv.star.core.RequestScope
import com.hgtv.star.tv.TvDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MiniActUpdateFileProvider : FileProvider()

private class UpdateProblem(message: String) : IOException(message)

private data class UpdateRelease(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val url: String,
    val size: Long,
    val digest: String
)

private object MiniActUpdates {
    private const val REPOSITORY = "xy6176/miniact-tv"
    private const val MAX_METADATA_BYTES = 1024L * 1024
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private val apkName = Regex("""MiniAct-(\d+(?:\.\d+){2,3})-(\d+)\.apk""")
    private val releaseTag = Regex("""v[0-9][0-9A-Za-z.\-]{0,63}""")
    private val sha256 = Regex("""sha256:[a-f0-9]{64}""")

    // 独立使用系统的 HTTPS 证书校验，更新请求也无需携带内容账号信息。
    private val http by lazy {
        OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
    }

    fun check(): UpdateRelease? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPOSITORY/releases?per_page=20")
            .header("Accept", "application/vnd.github+json")
            .build()
        val releases = read(request) { body ->
            val source = body.source()
            if (source.request(MAX_METADATA_BYTES + 1)) throw UpdateProblem("版本信息过大，请从发布页查看。")
            JSONArray(source.readUtf8())
        }
        var newest: UpdateRelease? = null
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            if (!releaseTag.matches(tag)) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                val name = asset.optString("name")
                val match = apkName.matchEntire(name) ?: continue
                val versionCode = match.groupValues[2].toLongOrNull() ?: continue
                if (versionCode <= (newest?.versionCode ?: BuildConfig.VERSION_CODE.toLong())) continue
                if (asset.optString("state") != "uploaded") continue
                val url = "https://github.com/$REPOSITORY/releases/download/$tag/$name"
                if (asset.optString("browser_download_url") != url) continue
                newest = UpdateRelease(
                    versionName = match.groupValues[1],
                    versionCode = versionCode,
                    notes = release.optString("body", "").take(1000).trim(),
                    url = url,
                    size = asset.optLong("size"),
                    digest = asset.optString("digest")
                )
            }
        }
        if (newest != null && (newest.size !in 1..MAX_APK_BYTES || !sha256.matches(newest.digest))) {
            throw UpdateProblem("新版缺少有效的校验信息，请稍后重试。")
        }
        return newest
    }

    fun download(context: Context, release: UpdateRelease): File {
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw UpdateProblem("无法创建更新缓存。")
        val expiredAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        directory.listFiles()?.filter { it.lastModified() < expiredAt }?.forEach { it.delete() }
        val file = File.createTempFile("MiniAct-", ".apk", directory)
        var verified = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            read(Request.Builder().url(release.url).build()) { body ->
                val announcedSize = body.contentLength()
                if (announcedSize >= 0 && announcedSize != release.size) {
                    throw UpdateProblem("安装包大小不符，请重试。")
                }
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    val input = body.byteStream()
                    var written = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > release.size) throw UpdateProblem("安装包大小不符，请重试。")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                    if (written != release.size) throw UpdateProblem("安装包下载不完整，请重试。")
                }
            }
            val actualDigest = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
            if (actualDigest != release.digest) throw UpdateProblem("安装包校验失败，请重新下载。")
            verifyPackage(context, file, release)
            verified = true
            return file
        } finally {
            if (!verified) file.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, file: File, release: UpdateRelease) {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = manager.getPackageInfo(context.packageName, flags)
        val incoming = manager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateProblem("无法读取安装包，或电视系统版本不兼容。")
        val incomingCode = PackageInfoCompat.getLongVersionCode(incoming)
        if (incoming.packageName != context.packageName || incoming.versionName != release.versionName ||
            incomingCode != release.versionCode || incomingCode <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateProblem("安装包与发布版本不符。")
        }
        val currentSigners = signers(installed)
        if (currentSigners.isEmpty() || currentSigners != signers(incoming)) {
            throw UpdateProblem("安装包签名与当前 MiniAct 不一致。")
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<Signature> = if (Build.VERSION.SDK_INT >= 28) {
        info.signingInfo?.apkContentsSigners.orEmpty().toSet()
    } else info.signatures.orEmpty().toSet()

    private fun <T> read(request: Request, consume: (ResponseBody) -> T): T =
        RequestScope.execute(http.newCall(request)) { response ->
            if (!response.isSuccessful) throw UpdateProblem("GitHub 返回 HTTP ${response.code}，请稍后重试。")
            consume(response.body ?: throw UpdateProblem("GitHub 返回了空响应，请稍后重试。"))
        }
}

internal fun MainActivity.checkMiniActUpdate(anchor: View) {
    if (Build.VERSION.SDK_INT < 21) {
        tvTools.info("检查更新", "请从 GitHub Releases 下载安装包，再用电视的安装器覆盖安装。", anchor)
        return
    }
    runUpdateTask(anchor, "正在检查更新", "正在连接 GitHub…", MiniActUpdates::check) { release ->
        if (release == null) {
            tvTools.info("检查更新", "当前版本 ${BuildConfig.VERSION_NAME}，没有发现更新的公开版本。", anchor)
        } else {
            val dialog = TvDialog(this, "发现新版本 ${release.versionName}")
                .message("当前版本 ${BuildConfig.VERSION_NAME}。覆盖安装会保留本机记录。")
            if (release.notes.isNotBlank()) dialog.message(release.notes)
            dialog.button("下载更新") {
                runUpdateTask(anchor, "正在下载更新", "正在下载并校验安装包…", {
                    MiniActUpdates.download(applicationContext, release)
                }) { file -> showUpdateInstaller(anchor, file) }
            }
            dialog.button("稍后")
            tvTools.show(dialog, anchor)
        }
    }
}

private fun MainActivity.showUpdateInstaller(anchor: View, file: File) {
    val dialog = TvDialog(this, "更新已下载")
        .message("请在系统界面确认覆盖安装。如需授权安装应用，允许后返回这里，再选择「安装更新」。")
    dialog.button("安装更新", keepOpen = true) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } else {
                val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    clipData = ClipData.newRawUri("MiniAct APK", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                dialog.dismiss()
            }
        } catch (_: ActivityNotFoundException) {
            tvTools.info("无法打开系统安装器", "请从 GitHub Releases 下载 APK，用电视支持的方式覆盖安装。", anchor)
        } catch (_: SecurityException) {
            tvTools.info("电视限制了安装", "请检查电视的应用安装权限，或手动覆盖安装。", anchor)
        }
    }
    dialog.button("关闭")
    tvTools.show(dialog, anchor)
}

private fun <T> MainActivity.runUpdateTask(
    anchor: View,
    title: String,
    message: String,
    task: () -> T,
    onSuccess: (T) -> Unit
) {
    val scope = RequestScope()
    val dialog = TvDialog(this, title).message(message)
    dialog.button("取消")
    dialog.setOnDismissExtra(scope::cancel)
    tvTools.show(dialog, anchor)
    appScope.launch {
        val result = withContext(AppExecutors.network.dispatcher) { runCatching { scope.run(task) } }
        if (!dialog.isShowing || isFinishing || isDestroyed) return@launch
        dialog.dismiss()
        result.fold(onSuccess = onSuccess, onFailure = { error ->
            tvTools.info("更新未完成", (error as? UpdateProblem)?.message
                ?: "请确认电视能访问 GitHub，再试一次。", anchor)
        })
    }
}
