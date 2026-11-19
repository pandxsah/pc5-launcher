package com.pc5.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** One launchable app, shaped like a PC5 library entry. */
data class AppEntry(
    val pkg: String,
    val title: String,
    val isGame: Boolean,
    val icon: ImageBitmap?,
    val banner: ImageBitmap?,
    val installedAt: Long,
    val lastOpened: Long,
    val version: String,
    val source: String,
)

class AppRepo(private val ctx: Context) {
    private val pm: PackageManager = ctx.packageManager
    private val prefs = ctx.getSharedPreferences("pc5", Context.MODE_PRIVATE)

    fun load(): List<AppEntry> {
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                val ai = ri.activityInfo.applicationInfo
                val pkg = ai.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                val info = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
                val bannerDrawable = runCatching { ri.activityInfo.loadBanner(pm) ?: pm.getApplicationBanner(pkg) }.getOrNull()
                val installer = runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
                }.getOrNull()
                out += AppEntry(
                    pkg = pkg,
                    title = ri.loadLabel(pm).toString(),
                    isGame = ai.category == ApplicationInfo.CATEGORY_GAME ||
                        @Suppress("DEPRECATION") (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0,
                    icon = runCatching { bitmap(ri.loadIcon(pm), 192, 192) }.getOrNull(),
                    banner = bannerDrawable?.let { runCatching { bitmap(it, 640, 360) }.getOrNull() },
                    installedAt = info?.firstInstallTime ?: 0L,
                    lastOpened = prefs.getLong("last:$pkg", 0L),
                    version = info?.versionName ?: "",
                    source = when {
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> "System"
                        installer == "com.android.vending" -> "Play Store"
                        installer.isNullOrEmpty() -> "Sideloaded"
                        else -> installer
                    },
                )
            }
        }
        // Like PC5: most recently opened or installed first
        return out.sortedByDescending { maxOf(it.lastOpened, it.installedAt) }
    }

    fun launch(app: AppEntry): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(app.pkg) ?: pm.getLaunchIntentForPackage(app.pkg) ?: return false
        prefs.edit().putLong("last:${app.pkg}", System.currentTimeMillis()).apply()
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openDetails(app: AppEntry) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
    fun openSettings() = start(Intent(Settings.ACTION_SETTINGS))

    private fun start(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun bitmap(d: Drawable, w: Int, h: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }
}
