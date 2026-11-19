package com.pc5.tvlauncher

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font

/** Values from app/web/css/tokens.css (1rem = 1/108 of the screen height). */
object T {
    // Colours
    val text = Color(0xFFFFFFFF)
    val text2 = Color(0xB8FFFFFF)        // .72
    val text3 = Color(0x73FFFFFF)        // .45
    val bg = Color(0xFF0B0C12)
    val tileSys = Color(0xDB1B2024)      // rgba(27,32,36,.86)
    val card = Color(0xFF22252A)
    val chip = Color(0xD9172027)
    val btn = Color(0xFFFFFFFF)
    val btnText = Color(0xFF292929)
    val btn2 = Color(0xB83E424D)         // rgba(62,66,77,.72)
    val mark = Color(0x29FFFFFF)         // rgba(255,255,255,.16)
    val ok = Color(0xFF1ED760)
    val ringA = Color(0xFFF2DDD2)
    val ringB = Color(0xFFA9ABC4)
    val ringC = Color(0xFFFFFFFF)
    val bdA = Color(0x944A5C96)          // rgba(74,92,150,.58)
    val bdB = Color(0x5C9C7870)          // rgba(156,120,112,.36)
    val bdC = Color(0x61307086)          // rgba(48,112,134,.38)
    val defA = Color(0xFF2C3346)
    val defB = Color(0xFF191D2B)

    // Sizes (rem)
    const val mHead = 9.6f
    const val mContent = 17.2f
    const val mStatusRight = 8.8f
    const val headTop = 4.1f
    const val statusTop = 3.8f
    const val statusH = 4.8f
    const val tabGap = 8.5f
    const val rowTop = 12.6f
    const val tile = 16.8f
    const val tileScale = 0.63095f
    const val tilePitch = 11.4f
    const val tileR = 2.5f
    const val rowPrevX = 5f
    const val rowNextX = 35.7f
    const val icTile = 7.2f
    const val rowLabelTop = 24.4f
    const val rowLabelLh = 3.8f
    const val badgeH = 3.2f
    const val hubTop = 61.8f
    const val hubSubTop = 16.6f
    const val hubActionsTop = 22.8f
    const val hubCardsTop = 36.4f
    const val hubLogoH = 15.2f
    const val btnH = 7.2f
    const val btnW = 33.4f
    const val btnGap = 1.6f
    const val cardW = 50.4f
    const val cardH = 15f
    const val cardGap = 3.2f
    const val cardR = 1.2f
    const val ringW = 0.3f
    const val ringGap = 0.3f

    // Font sizes (rem)
    const val fsLogo = 8.4f
    const val fsTab = 3.4f
    const val fsBody = 3f
    const val fsButton = 2.8f
    const val fsCaption = 2.2f
    const val fsSmall = 1.8f
    const val fsValue = 2.4f

    // Motion
    val easeOut = CubicBezierEasing(.2f, .8f, .2f, 1f)
    val easeStd = CubicBezierEasing(.4f, 0f, .2f, 1f)
    const val tRow = 250
    const val tFocus = 150
    const val tTab = 300
    const val tHub = 300
    const val tRing = 4000
    const val tBgIn = 600

    val noto = FontFamily(
        Font(R.font.noto_sans, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
        Font(R.font.noto_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.noto_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )
}
