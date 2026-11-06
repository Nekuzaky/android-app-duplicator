package dev.nekuz.dualspace

private val APK_ROOTS = listOf("/data/app/", "/mnt/expand/")
private const val MAX_ICON_PX = 256

fun isInstalledApkPath(canonicalPath: String): Boolean =
    canonicalPath.endsWith(".apk") && APK_ROOTS.any { canonicalPath.startsWith(it) }

fun iconSizePx(sizeDp: Int, density: Float): Int =
    (sizeDp * density).toInt().coerceIn(1, MAX_ICON_PX)

fun safeLabel(fallback: String, load: () -> CharSequence?): String =
    try {
        load()?.toString().orEmpty().ifBlank { fallback }
    } catch (t: Throwable) {
        fallback
    }

class InstallGate {
    private var holders = 0

    @get:Synchronized
    val isIdle: Boolean
        get() = holders == 0

    @Synchronized
    fun open() {
        holders++
    }

    @Synchronized
    fun close(): Boolean {
        if (holders > 0) holders--
        return holders == 0
    }
}
