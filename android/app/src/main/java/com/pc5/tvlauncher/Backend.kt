package com.pc5.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Android replacement for the PC5 Python core. Implements the RPC methods the home screen needs
 * (same names and reply shapes as app/couch); everything else answers null so the UI keeps running.
 */
class Backend(private val ctx: Context, private val emit: (String, Any?) -> Unit) {

    private val pm: PackageManager = ctx.packageManager
    private val prefs = ctx.getSharedPreferences("pc5", Context.MODE_PRIVATE)
    private val artCache = ConcurrentHashMap<String, ByteArray>()

    // System tiles whose screens depend on Linux-only features; hidden by default (unhide in the UI if wanted)
    private val defaultHidden = listOf("sys:install", "sys:files", "sys:store", "sys:gallery")

    fun handle(text: String): JSONObject = try {
        val msg = JSONObject(text)
        val method = msg.optString("method")
        val params = msg.optJSONObject("params") ?: JSONObject()
        JSONObject().put("ok", true).put("result", call(method, params) ?: JSONObject.NULL)
    } catch (e: Exception) {
        Log.w(TAG, "RPC failed: $text", e)
        JSONObject().put("ok", false).put("error", e.javaClass.simpleName).put("message", e.message ?: "")
    }

    private fun call(method: String, p: JSONObject): Any? = when (method) {
        "sys.state" -> sysState()
        "sys.log" -> { Log.i(TAG, "[web] " + p.optString("text")); null }
        "sys.firstFrame", "sys.beat" -> null

        "lib.list" -> JSONObject().put("entries", JSONArray(list(p)))
        "lib.get" -> entries().firstOrNull { it.optString("id") == p.optString("id") }
        "lib.setOption" -> { setOption(p); null }

        "home.order" -> homeOrder()
        "home.setOrder" -> { setOrder(p); homeOrder() }
        "home.setHidden" -> { setHidden(p.optString("id"), p.optBoolean("hidden")); emit("home.changed", JSONObject()); homeOrder() }

        "app.launch" -> launch(p.optString("id"))
        "app.focus" -> launch(p.optString("id"))

        "settings.categories", "settings.page" -> { openSystemSettings(Settings.ACTION_SETTINGS); null }
        "power.menu" -> { openSystemSettings(Settings.ACTION_SETTINGS); null }

        else -> { Log.d(TAG, "unsupported on Android: $method"); null }
    }

    fun onResume() {
        emit("app.changed", JSONObject().put("running", JSONArray()))
        emit("home.changed", JSONObject())
    }

    // ------------------------------------------------------------------ state
    private fun sysState() = JSONObject()
        .put("role", "home").put("version", "0.5.1-tv").put("time", System.currentTimeMillis() / 1000)
        .put("foreground", JSONObject.NULL).put("overlay", "hidden").put("running", JSONArray())
        .put("visible", true).put("saver", false)
        .put("setup", JSONObject().put("due", false))
        .put("ui", JSONObject().put("sounds", true).put("soundVolume", 60).put("reduceMotion", false)
            .put("scale", 1).put("clock24h", true).put("liveBackground", true))

    // ------------------------------------------------------------------ library
    private fun launchables(): List<ResolveInfo> {
        val seen = HashSet<String>()
        val out = ArrayList<ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                out.add(ri)
            }
        }
        return out
    }

    private fun isGame(ai: ApplicationInfo): Boolean =
        (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) ||
            (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    private fun entries(): List<JSONObject> {
        val hidden = hiddenSet()
        val renamed = JSONObject(prefs.getString("titles", "{}")!!)
        val favs = JSONObject(prefs.getString("favorites", "{}")!!)
        return launchables().map { ri ->
            val ai = ri.activityInfo.applicationInfo
            val pkg = ai.packageName
            val id = "android:$pkg"
            val title = renamed.optString(id).ifEmpty { ri.loadLabel(pm).toString() }
            val game = isGame(ai)
            val added = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime / 1000.0 }.getOrDefault(0.0)
            val last = prefs.getLong("last:$id", 0L)
            val art = JSONObject().put("icon", "/art/icon/$pkg.png")
            if (hasBanner(ri)) art.put("hero", "/art/banner/$pkg.png")
            JSONObject()
                .put("id", id).put("title", title).put("kind", if (game) "game" else "app")
                .put("source", "android").put("origin", "android").put("badge", "")
                .put("sort_title", title.lowercase()).put("art", art)
                .put("available", true).put("installed", true)
                .put("tags", JSONArray(if (game) listOf("games-row") else emptyList<String>()))
                .put("last_played", if (last > 0) last / 1000.0 else JSONObject.NULL)
                .put("added", added).put("playtime", 0)
                .put("hidden", hidden.contains(id)).put("favorite", favs.optBoolean(id))
                .put("options", JSONObject(prefs.getString("opt:$id", "{}")!!))
                .put("running", false)
        }
    }

    private fun list(p: JSONObject): List<JSONObject> {
        val kind = p.optString("kind")
        val filter = p.optString("filter").ifEmpty { "all" }
        val query = p.optString("query").lowercase().trim()
        var items = entries()
        if (kind.isNotEmpty()) items = items.filter { it.optString("kind") == kind }
        items = if (filter == "hidden") items.filter { it.optBoolean("hidden") } else items.filterNot { it.optBoolean("hidden") }
        if (filter == "favorites") items = items.filter { it.optBoolean("favorite") }
        if (query.isNotEmpty()) items = items.filter { it.optString("title").lowercase().contains(query) }
        val sort = p.optString("sort").ifEmpty { if (filter == "recent") "recent" else "title" }
        items = when {
            sort == "recent" -> items.sortedByDescending {
                maxOf(it.optDouble("last_played", 0.0).let { v -> if (v.isNaN()) 0.0 else v }, it.optDouble("added", 0.0))
            }
            sort == "added" -> items.sortedByDescending { it.optDouble("added", 0.0) }
            else -> items.sortedBy { it.optString("sort_title") }
        }
        val limit = p.optInt("limit", 0)
        return if (limit > 0) items.take(limit) else items
    }

    private fun setOption(p: JSONObject) {
        val id = p.optString("id")
        val key = p.optString("key")
        when (key) {
            "title" -> edit("titles") { it.put(id, p.optString("value")) }
            "favorite" -> edit("favorites") { it.put(id, p.optBoolean("value")) }
            "hidden" -> setHidden(id, p.optBoolean("value"))
            else -> {
                val o = JSONObject(prefs.getString("opt:$id", "{}")!!).put(key, p.opt("value"))
                prefs.edit().putString("opt:$id", o.toString()).apply()
            }
        }
        emit("lib.changed", JSONObject().put("id", id))
    }

    private fun edit(key: String, fn: (JSONObject) -> Unit) {
        val o = JSONObject(prefs.getString(key, "{}")!!)
        fn(o)
        prefs.edit().putString(key, o.toString()).apply()
    }

    // ------------------------------------------------------------------ home order / hidden
    private fun hiddenSet(): MutableSet<String> {
        val raw = prefs.getString("hidden", null) ?: return defaultHidden.toMutableSet()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
    }

    private fun setHidden(id: String, hidden: Boolean) {
        val s = hiddenSet()
        if (hidden) s.add(id) else s.remove(id)
        prefs.edit().putString("hidden", JSONArray(s.toList()).toString()).apply()
    }

    private fun homeOrder(): JSONObject {
        val o = JSONObject(prefs.getString("order", "{}")!!)
        o.put("hidden", JSONArray(hiddenSet().toList()))
        return o
    }

    private fun setOrder(p: JSONObject) {
        val o = JSONObject(prefs.getString("order", "{}")!!)
        for (k in p.keys()) o.put(k, p.get(k))
        prefs.edit().putString("order", o.toString()).apply()
    }

    // ------------------------------------------------------------------ launching
    private fun launch(id: String): JSONObject {
        val pkg = id.removePrefix("android:")
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            ?: return JSONObject().put("ok", false)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        prefs.edit().putLong("last:$id", System.currentTimeMillis()).apply()
        ctx.startActivity(intent)
        return JSONObject().put("ok", true)
    }

    private fun openSystemSettings(action: String) {
        runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ------------------------------------------------------------------ art: /art/icon/<pkg>.png, /art/banner/<pkg>.png
    private fun hasBanner(ri: ResolveInfo): Boolean =
        ri.activityInfo.banner != 0 || ri.activityInfo.applicationInfo.banner != 0

    val artHandler = WebViewAssetLoader.PathHandler { path ->
        val parts = path.removeSuffix(".png").split("/")
        if (parts.size != 2) return@PathHandler null
        val (type, pkg) = parts
        val bytes = artCache.getOrPut(path) {
            val d: Drawable = runCatching {
                if (type == "banner") pm.getApplicationBanner(pkg) ?: pm.getApplicationIcon(pkg)
                else pm.getApplicationIcon(pkg)
            }.getOrNull() ?: return@PathHandler null
            png(d, if (type == "banner") 640 else 256, if (type == "banner") 360 else 256)
        }
        WebResourceResponse("image/png", null, ByteArrayInputStream(bytes))
    }

    private fun png(d: Drawable, w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    companion object { const val TAG = "PC5" }
}
