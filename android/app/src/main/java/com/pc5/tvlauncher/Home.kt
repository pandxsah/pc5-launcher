package com.pc5.tvlauncher

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** rem -> Dp / sp, as in tokens.css: 1rem = min(100vh/108, 100vw/192). */
class Rem(private val dpPerRem: Float, private val fontScale: Float) {
    fun dp(v: Float): Dp = (v * dpPerRem).dp
    fun sp(v: Float): TextUnit = (v * dpPerRem / fontScale).sp
}

private enum class Zone { Header, Row, Hub }

interface HomeActions {
    fun launch(app: AppEntry)
    fun details(app: AppEntry)
    fun settings()
    fun power()
    fun sound(name: String)
}

@Composable
fun HomeScreen(apps: List<AppEntry>, homeSignal: Int, actions: HomeActions) {
    BoxWithConstraints(Modifier.fillMaxSize().background(T.bg)) {
        val fontScale = LocalDensity.current.fontScale
        val r = remember(maxWidth, maxHeight) { Rem(minOf(maxHeight.value / 108f, maxWidth.value / 192f), fontScale) }

        val games = apps.filter { it.isGame }
        val others = apps.filterNot { it.isGame }
        var tab by remember { mutableIntStateOf(0) }
        val list = if (tab == 0) games else others
        val focusIdx = remember { mutableStateOf(intArrayOf(0, 0)) }
        var zone by remember { mutableStateOf(Zone.Row) }
        var headerIdx by remember { mutableIntStateOf(0) }
        var hubIdx by remember { mutableIntStateOf(0) }
        val idx = focusIdx.value[tab].coerceIn(0, (list.size - 1).coerceAtLeast(0))
        val current = list.getOrNull(idx)

        fun setIdx(v: Int) { focusIdx.value = focusIdx.value.copyOf().also { it[tab] = v } }

        // No games installed: start on the Apps tab
        LaunchedEffect(games.isEmpty()) { if (games.isEmpty() && tab == 0) tab = 1 }
        // HOME pressed while visible: back to the start of the row
        LaunchedEffect(homeSignal) { if (homeSignal > 0) { zone = Zone.Row; setIdx(0) } }

        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }

        fun onKey(key: Key): Boolean {
            val left = key == Key.DirectionLeft
            val right = key == Key.DirectionRight
            val select = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.ButtonA
            val back = key == Key.Back || key == Key.Escape || key == Key.ButtonB
            if (key == Key.ButtonL1 || key == Key.ButtonR1) {
                val n = if (key == Key.ButtonL1) 0 else 1
                if (n != tab) { tab = n; zone = Zone.Row; actions.sound("tab") } else actions.sound("edge")
                return true
            }
            when (zone) {
                Zone.Row -> when {
                    left -> if (idx > 0) { setIdx(idx - 1); actions.sound("focus") } else actions.sound("edge")
                    right -> if (idx < list.size - 1) { setIdx(idx + 1); actions.sound("focus") } else actions.sound("edge")
                    key == Key.DirectionUp -> { zone = Zone.Header; headerIdx = tab; actions.sound("focus") }
                    key == Key.DirectionDown -> if (current != null) { zone = Zone.Hub; hubIdx = 0; actions.sound("focus") } else actions.sound("edge")
                    select -> current?.let { actions.sound("launch"); actions.launch(it) }
                    key == Key.Menu || key == Key.ButtonStart -> current?.let { actions.sound("select"); actions.details(it) }
                    back -> if (idx != 0) { setIdx(0); actions.sound("back") }
                    else -> return false
                }
                Zone.Header -> when {
                    left -> if (headerIdx > 0) { headerIdx--; actions.sound("focus") } else actions.sound("edge")
                    right -> if (headerIdx < 3) { headerIdx++; actions.sound("focus") } else actions.sound("edge")
                    key == Key.DirectionDown || back -> { zone = Zone.Row; actions.sound(if (back) "back" else "focus") }
                    select -> when (headerIdx) {
                        0, 1 -> { if (tab != headerIdx) { tab = headerIdx; actions.sound("tab") }; zone = Zone.Row }
                        2 -> { actions.sound("select"); actions.settings() }
                        3 -> { actions.sound("select"); actions.power() }
                    }
                    key == Key.DirectionUp -> actions.sound("edge")
                    else -> return false
                }
                Zone.Hub -> when {
                    left -> if (hubIdx > 0) { hubIdx--; actions.sound("focus") } else actions.sound("edge")
                    right -> if (hubIdx < 1) { hubIdx++; actions.sound("focus") } else actions.sound("edge")
                    key == Key.DirectionUp || back -> { zone = Zone.Row; actions.sound(if (back) "back" else "focus") }
                    key == Key.DirectionDown -> actions.sound("edge")
                    select -> current?.let {
                        if (hubIdx == 0) { actions.sound("launch"); actions.launch(it) } else { actions.sound("select"); actions.details(it) }
                    }
                    else -> return false
                }
            }
            return true
        }

        val ringAngle by rememberInfiniteTransition(label = "ring").animateFloat(
            0f, 360f, infiniteRepeatable(tween(T.tRing, easing = LinearEasing), RepeatMode.Restart), label = "angle")

        Box(
            Modifier.fillMaxSize().focusRequester(focus).focusable()
                .onKeyEvent { if (it.type == KeyEventType.KeyDown) onKey(it.key) else it.key == Key.Back }
        ) {
            Backdrop(current?.banner, r)

            // ---------------------------------------------------------------- header: tabs + status
            Row(
                Modifier.offset(r.dp(T.mHead), r.dp(T.headTop)),
                horizontalArrangement = Arrangement.spacedBy(r.dp(T.tabGap)),
            ) {
                listOf("Games", "Apps").forEachIndexed { i, label ->
                    val active = i == tab
                    Box(
                        Modifier.ring(zone == Zone.Header && headerIdx == i, r, 0.8f, ringAngle)
                            .padding(horizontal = r.dp(1.2f))
                    ) {
                        Text(label, r.sp(T.fsTab), if (active) T.text else T.text3,
                            if (active) FontWeight.SemiBold else FontWeight.Light, lineHeight = r.sp(4.4f))
                    }
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(end = r.dp(T.mStatusRight), top = r.dp(T.statusTop)).height(r.dp(T.statusH)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusButton(Icons.Filled.Settings, zone == Zone.Header && headerIdx == 2, r, ringAngle)
                Box(Modifier.width(r.dp(2.4f)))
                StatusButton(Icons.Filled.PowerSettingsNew, zone == Zone.Header && headerIdx == 3, r, ringAngle)
                Box(Modifier.width(r.dp(3.2f)))
                Clock(r)
            }

            // ---------------------------------------------------------------- tile row
            val rowAlpha by animateFloatAsState(if (zone == Zone.Hub) 0f else 1f, tween(T.tHub, easing = T.easeStd), label = "rowA")
            Box(Modifier.offset(y = r.dp(T.rowTop)).fillMaxWidth().height(r.dp(T.tile)).alpha(rowAlpha)) {
                list.forEachIndexed { i, app ->
                    if (i < idx - 3 || i > idx + 18) return@forEachIndexed
                    androidx.compose.runtime.key(app.pkg) {
                        Tile(app, i - idx, r, zone == Zone.Row && i == idx, ringAngle)
                    }
                }
                if (list.isEmpty()) {
                    Box(Modifier.offset(x = r.dp(T.mContent)).size(r.dp(T.tile)).clip(RoundedCornerShape(r.dp(T.tileR))).background(T.tileSys),
                        contentAlignment = Alignment.Center) {
                        androidx.compose.material.icons.Icons.Outlined.SportsEsports.let {
                            androidx.compose.foundation.Image(it, null, Modifier.size(r.dp(T.icTile)), colorFilter = ColorFilter.tint(T.text3))
                        }
                    }
                }
            }

            // Title next to the focused tile
            if (current != null) {
                Crossfade(current.pkg, Modifier.offset(r.dp(T.rowNextX), r.dp(T.rowLabelTop)).alpha(rowAlpha),
                    animationSpec = tween(T.tFocus), label = "label") { _ ->
                    Row(Modifier.height(r.dp(T.rowLabelLh)), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.height(r.dp(T.badgeH)).clip(RoundedCornerShape(r.dp(0.4f))).background(T.btn).padding(horizontal = r.dp(1f)),
                            contentAlignment = Alignment.Center) {
                            Text(if (current.isGame) "GAME" else "APP", r.sp(T.fsSmall), Color(0xFF111111), FontWeight.SemiBold,
                                letterSpacing = 0.06.em)
                        }
                        Box(Modifier.width(r.dp(1.1f)))
                        Text(current.title, r.sp(T.fsBody), T.text, FontWeight.Light)
                    }
                }
            }

            // ---------------------------------------------------------------- hub
            val hubShift by animateFloatAsState(if (zone == Zone.Hub) -22f else 0f, tween(T.tHub, easing = T.easeStd), label = "hub")
            Hub(current, list.isEmpty(), tab, r, hubShift, if (zone == Zone.Hub) hubIdx else -1, ringAngle)
        }
    }
}

// -------------------------------------------------------------------- pieces

@Composable
private fun Text(
    s: String, size: TextUnit, color: Color, weight: FontWeight,
    lineHeight: TextUnit = TextUnit.Unspecified, letterSpacing: TextUnit = TextUnit.Unspecified,
    shadow: Shadow? = null, modifier: Modifier = Modifier,
) = androidx.compose.foundation.text.BasicText(
    s, modifier,
    style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = T.noto, lineHeight = lineHeight,
        letterSpacing = letterSpacing, shadow = shadow),
    maxLines = 1, overflow = TextOverflow.Ellipsis,
)

/** PC5 focus ring: 0.3rem conic gradient, 0.3rem away from the element, slowly turning. */
private fun Modifier.ring(on: Boolean, r: Rem, radiusRem: Float, angle: Float, circle: Boolean = false): Modifier =
    if (!on) this else drawBehind {
        val w = r.dp(T.ringW).toPx()
        val out = r.dp(T.ringGap).toPx() + w / 2
        val cx = size.width / 2; val cy = size.height / 2
        val shader = SweepGradient(cx, cy,
            intArrayOf(T.ringA.toArgb(), T.ringB.toArgb(), T.ringC.toArgb(), T.ringB.toArgb(), T.ringA.toArgb()), null)
        shader.setLocalMatrix(Matrix().apply { setRotate(angle - 90f, cx, cy) })
        val corner = if (circle) (size.minDimension / 2 + out) else r.dp(radiusRem).toPx() + out
        drawRoundRect(ShaderBrush(shader), Offset(-out, -out), Size(size.width + 2 * out, size.height + 2 * out),
            CornerRadius(corner), style = Stroke(w))
    }

@Composable
private fun Tile(app: AppEntry, d: Int, r: Rem, focused: Boolean, angle: Float) {
    val x = when {
        d == 0 -> T.mContent
        d < 0 -> T.rowPrevX + (d + 1) * T.tilePitch
        else -> T.rowNextX + (d - 1) * T.tilePitch
    }
    val ax by animateFloatAsState(x, tween(T.tRow, easing = T.easeOut), label = "x")
    val sc by animateFloatAsState(if (d == 0) 1f else T.tileScale, tween(T.tRow, easing = T.easeOut), label = "s")
    val ringA by animateFloatAsState(if (focused) 1f else 0f, tween(T.tFocus, easing = T.easeOut), label = "ringA")
    Box(
        Modifier.offset(x = r.dp(ax)).size(r.dp(T.tile))
            .graphicsLayer { scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(0f, 0f) }
            .then(if (ringA > 0.01f) Modifier.alpha(1f).ring(true, r, T.tileR, angle) else Modifier)
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(r.dp(T.tileR))).background(T.tileSys),
            contentAlignment = Alignment.Center,
        ) {
            if (app.banner != null) {
                Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (app.icon != null) {
                Image(app.icon, null, Modifier.size(r.dp(T.icTile)))
            }
        }
    }
}

@Composable
private fun StatusButton(icon: ImageVector, focused: Boolean, r: Rem, angle: Float) {
    Box(Modifier.size(r.dp(T.statusH)).ring(focused, r, 0f, angle, circle = true), contentAlignment = Alignment.Center) {
        Image(icon, null, Modifier.size(r.dp(3.2f)), colorFilter = ColorFilter.tint(T.text))
    }
}

@Composable
private fun Clock(r: Rem) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000L) } }
    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), r.sp(T.fsTab), T.text, FontWeight.Light)
}

@Composable
private fun Backdrop(banner: ImageBitmap?, r: Rem) {
    Box(Modifier.fillMaxSize().drawBehind {
        // --g-art-default plus the three moving light blobs (static here)
        drawRect(Brush.radialGradient(0f to T.defA, .38f to T.defB, .8f to T.bg,
            center = Offset(size.width * .78f, size.height * .38f), radius = size.width * .9f))
        drawCircle(Brush.radialGradient(listOf(T.bdA, Color.Transparent), Offset(size.width * .3f, size.height * .35f), size.width * .3f),
            size.width * .3f, Offset(size.width * .3f, size.height * .35f))
        drawCircle(Brush.radialGradient(listOf(T.bdB, Color.Transparent), Offset(size.width * .7f, size.height * .6f), size.width * .25f),
            size.width * .25f, Offset(size.width * .7f, size.height * .6f))
        drawCircle(Brush.radialGradient(listOf(T.bdC, Color.Transparent), Offset(size.width * .55f, size.height * .2f), size.width * .22f),
            size.width * .22f, Offset(size.width * .55f, size.height * .2f))
    })
    Crossfade(banner, animationSpec = tween(T.tBgIn, easing = T.easeStd), label = "bg") { b ->
        if (b != null) Image(b, null, Modifier.fillMaxSize().blur(r.dp(4f)).alpha(.55f), contentScale = ContentScale.Crop)
    }
    // Dims (--g-art-top/left/bottom/text)
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.verticalGradient(0f to Color(0x8C000000), .24f to Color(0x38000000), .45f to Color.Transparent))
        drawRect(Brush.horizontalGradient(0f to Color(0x80000000), .34f to Color(0x2E000000), .6f to Color.Transparent))
        drawRect(Brush.verticalGradient(.58f to Color.Transparent, .74f to Color(0x33000000), 1f to Color(0xB8000000)))
        drawRect(Brush.radialGradient(0f to Color(0xBD000000), .44f to Color(0x7A000000), .78f to Color.Transparent,
            center = Offset(size.width * .08f, size.height * .82f), radius = size.width * .55f))
    })
}

@Composable
private fun Hub(app: AppEntry?, empty: Boolean, tab: Int, r: Rem, shift: Float, hubFocus: Int, angle: Float) {
    Box(Modifier.offset(r.dp(T.mContent), r.dp(T.hubTop + shift)).padding(end = r.dp(T.mContent)).fillMaxWidth()) {
        if (app == null) {
            Text(if (empty && tab == 0) "No games installed" else "No apps", r.sp(T.fsLogo * .7f), T.text, FontWeight.SemiBold)
            return@Box
        }
        val shadow = Shadow(Color(0x80000000), Offset(0f, 4f), 24f)
        Box(Modifier.height(r.dp(T.hubLogoH)).widthIn(max = r.dp(110f)), contentAlignment = Alignment.BottomStart) {
            Text(app.title, r.sp(T.fsLogo), T.text, FontWeight.SemiBold, lineHeight = r.sp(9.6f), letterSpacing = 0.01.em, shadow = shadow)
        }
        Text(lastOpened(app), r.sp(T.fsBody), T.text2, FontWeight.Light,
            shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 4f), modifier = Modifier.offset(y = r.dp(T.hubSubTop)))

        // Actions
        Row(Modifier.offset(y = r.dp(T.hubActionsTop)), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.ring(hubFocus == 0, r, 3.6f, angle).width(r.dp(T.btnW)).height(r.dp(T.btnH))
                    .clip(RoundedCornerShape(r.dp(3.6f))).background(T.btn),
                contentAlignment = Alignment.Center,
            ) { Text(if (app.isGame) "Play" else "Open", r.sp(T.fsButton), T.btnText, FontWeight.SemiBold) }
            Box(Modifier.width(r.dp(T.btnGap)))
            Box(
                Modifier.ring(hubFocus == 1, r, 0f, angle, circle = true).size(r.dp(T.btnH)).clip(CircleShape).background(T.btn2),
                contentAlignment = Alignment.Center,
            ) { Image(Icons.Filled.MoreHoriz, null, Modifier.size(r.dp(3.6f)), colorFilter = ColorFilter.tint(T.text)) }
        }
        // Chip on the right
        Row(
            Modifier.align(Alignment.TopEnd).offset(y = r.dp(T.hubActionsTop + 0.8f)).height(r.dp(5.6f))
                .clip(RoundedCornerShape(r.dp(0.8f))).background(T.chip).padding(horizontal = r.dp(1.6f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(Icons.Outlined.Schedule, null, Modifier.size(r.dp(2.6f)), colorFilter = ColorFilter.tint(T.text2))
            Box(Modifier.width(r.dp(1.2f)))
            Text("Installed", r.sp(T.fsValue), T.text2, FontWeight.Light)
            Box(Modifier.width(r.dp(1.2f)))
            Text(dateText(app.installedAt), r.sp(T.fsValue), T.text, FontWeight.SemiBold)
        }
        // Info cards
        Row(Modifier.offset(y = r.dp(T.hubCardsTop)), horizontalArrangement = Arrangement.spacedBy(r.dp(T.cardGap))) {
            listOf("Source" to app.source, "Version" to app.version.ifEmpty { "–" }, "Package" to app.pkg).forEach { (k, v) ->
                Column(
                    Modifier.width(r.dp(T.cardW)).height(r.dp(T.cardH)).clip(RoundedCornerShape(r.dp(T.cardR))).background(T.card)
                        .padding(horizontal = r.dp(2.4f), vertical = r.dp(2f)),
                ) {
                    Text(k, r.sp(T.fsCaption), T.text2, FontWeight.Light)
                    Box(Modifier.height(r.dp(0.8f)))
                    Text(v, r.sp(T.fsValue), T.text, FontWeight.Normal)
                }
            }
        }
    }
}

private fun lastOpened(app: AppEntry): String =
    if (app.lastOpened <= 0) "Not opened yet" else "Last opened: " + dayText(app.lastOpened)

private fun dayText(t: Long): String {
    val c = Calendar.getInstance(); val then = Calendar.getInstance().apply { timeInMillis = t }
    val days = ((startOfDay(c) - startOfDay(then)) / 86_400_000L).toInt()
    return when (days) {
        0 -> "today"; 1 -> "yesterday"
        in 2..6 -> "$days days ago"
        else -> dateText(t)
    }
}

private fun dateText(t: Long): String = if (t <= 0) "–" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
