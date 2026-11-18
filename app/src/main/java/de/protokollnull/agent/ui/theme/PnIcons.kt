package de.protokollnull.agent.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Eigene Linien-Icons (24er Raster) im taktischen Stil. */
object PnIcons {
    private fun build(name: String, vararg paths: String, filled: Set<Int> = emptySet()): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEachIndexed { i, d ->
            if (i in filled) b.addPath(addPathNodes(d), fill = SolidColor(Color.White))
            else b.addPath(
                addPathNodes(d), stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Hq = build("hq", "M3 10.5 12 4l9 6.5", "M5 9.5V20h14V9.5", "M10 20v-5h4v5")
    val Target = build("target", "M12 3v3M12 18v3M3 12h3M18 12h3", "M12 6.5a5.5 5.5 0 1 0 0 11 5.5 5.5 0 1 0 0-11z", "M12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 1 0 0-3z", filled = setOf(2))
    val Grid = build("grid", "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z")
    val People = build("people", "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 1 0 0 7z", "M2.5 20c.6-3.6 3.2-5.5 6.5-5.5s5.9 1.9 6.5 5.5", "M16 4.4a3.3 3.3 0 0 1 0 6.3", "M17.5 14.7c2.2.5 3.6 2.3 4 5.3")
    val Profile = build("profile", "M12 3 4 6v5.5c0 4.6 3.3 8.3 8 9.5 4.7-1.2 8-4.9 8-9.5V6z", "M12 8.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5z", "M8 17c.9-1.6 2.3-2.4 4-2.4s3.1.8 4 2.4")
    val Lock = build("lock", "M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3", "M12 14.5v2")
    val Bolt = build("bolt", "M13 2.5 5 13.5h6l-1 8 8-11h-6z")
    val Pulse = build("pulse", "M3 12h4l2-5 4 10 2-5h6")
    val Heart = build("heart", "M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z")
    val Coin = build("coin", "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 1 0 0-17z", "M14.8 9.2c-.5-.9-1.5-1.4-2.8-1.4-1.6 0-2.7.8-2.7 2 0 2.8 5.6 1.4 5.6 4.3 0 1.2-1.2 2.1-2.9 2.1-1.4 0-2.5-.6-2.9-1.6M12 6.3v1.5M12 16.2v1.5")
    val Clock = build("clock", "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 1 0 0-17z", "M12 7.5V12l3 2")
    val Chevron = build("chevron", "M9 5l7 7-7 7")
    val Back = build("back", "M15 5l-7 7 7 7")
    val Star = build("star", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8L3.5 9.7l5.9-.9z")
    val Shield = build("shield", "M12 3 4.5 6v5.5c0 4.4 3.1 8 7.5 9.5 4.4-1.5 7.5-5.1 7.5-9.5V6z", "M9 12l2 2 4-4")
    val Eye = build("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z")
    val Chat = build("chat", "M4 5h16v11H9l-5 4z", "M8 9.5h8M8 12.5h5")
    val Search = build("search", "M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 1 0 0-13z", "M15.5 15.5 20 20")
    val Bed = build("bed", "M3 18V7M3 14h18v4M21 14v-2.5A2.5 2.5 0 0 0 18.5 9H11v5", "M7 9.5a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 1 0 0-3.2z")
    val Food = build("food", "M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10", "M17 3c-2 1.5-3 4-3 7h3v11")
    val Fist = build("fist", "M7 10V7.5a1.5 1.5 0 0 1 3 0V10M10 9V6.5a1.5 1.5 0 0 1 3 0V9M13 9V7a1.5 1.5 0 0 1 3 0v3M16 10V8.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7h-1a6 6 0 0 1-6-6v-2.5A2.5 2.5 0 0 1 7.5 10H11")
    val Cross = build("cross", "M9.5 4h5v5.5H20v5h-5.5V20h-5v-5.5H4v-5h5.5z")
    val Box = build("box", "M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5z", "M3.5 7.5 12 12l8.5-4.5M12 12v9")
    val Radar = build("radar", "M12 3a9 9 0 1 0 9 9", "M12 7a5 5 0 1 0 5 5", "M12 12l6.5-6.5")
    val Chip = build("chip", "M7 7h10v10H7z", "M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4")
    val Brain = build("brain", "M9 4.5A3 3 0 0 0 6 7.5 3 3 0 0 0 4 10.3a3 3 0 0 0 1 4.7A3 3 0 0 0 8 19.5a2.5 2.5 0 0 0 4-1V6a2.3 2.3 0 0 0-3-1.5z", "M15 4.5A3 3 0 0 1 18 7.5a3 3 0 0 1 2 2.8 3 3 0 0 1-1 4.7 3 3 0 0 1-3 4.5 2.5 2.5 0 0 1-4-1")
    val Crown = build("crown", "M4 17.5 3 7.5l5 4 4-6 4 6 5-4-1 10z", "M4 20.5h16")
    val Archive = build("archive", "M3.5 4.5h17v4h-17z", "M5 8.5V20h14V8.5", "M10 12h4")
    val Door = build("door", "M6 21V4h10v17", "M16 6h2v15M3 21h18", "M13 12.5h.01")
    val City = build("city", "M3 21V9l5-3v15M8 21V4h8v17M16 21V10l5 2v9", "M11 8h2M11 11h2M11 14h2")
    val Gear = build("gear", "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z", "M19.4 13a7.6 7.6 0 0 0 0-2l2-1.5-2-3.4-2.3.9a7.3 7.3 0 0 0-1.8-1L15 3.5h-4l-.4 2.5a7.3 7.3 0 0 0-1.7 1l-2.4-.9-2 3.4 2 1.5a7.6 7.6 0 0 0 0 2l-2 1.5 2 3.4 2.4-.9a7.3 7.3 0 0 0 1.7 1l.4 2.5h4l.4-2.5a7.3 7.3 0 0 0 1.7-1l2.4.9 2-3.4z")
    val Save = build("save", "M5 4h11l3 3v13H5z", "M8 4v5h7V4M8 20v-6h8v6")
    val Bag = build("bag", "M5 8h14l-1 12H6z", "M9 8V6.5a3 3 0 0 1 6 0V8")
    val Spark = build("spark", "M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M18.4 5.6l-2.8 2.8M8.4 15.6l-2.8 2.8")
    val Flag = build("flag", "M5 21V4M5 4h11l-2 4 2 4H5")
    val Calendar = build("calendar", "M4 6h16v14H4z", "M4 10h16M8 3.5v4M16 3.5v4", "M8 14h2M12 14h2M16 14h.01")
    val Skull = build("alert", "M12 3.5 2.5 20h19z", "M12 10v4.5M12 17.5h.01")
    val Play = build("play", "M7 4.5v15L19.5 12z", filled = setOf(0))
    val Plus = build("plus", "M12 5v14M5 12h14")
    val Music = build("music", "M9 18V6l11-2v12", "M6.5 15.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5zM17.5 13.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5z")

    fun forRoom(id: String): ImageVector = when (id) {
        "lobby" -> Door; "dorm" -> Bed; "canteen" -> Food; "city" -> City; "training" -> Fist
        "range" -> Target; "medical" -> Cross; "armory" -> Box; "ops" -> Radar; "tech" -> Chip
        "psych" -> Brain; "director" -> Crown; "archives" -> Archive; else -> Hq
    }

    fun forStage(type: String): ImageVector = when (type) {
        "search" -> Search; "observe" -> Eye; "tactical" -> Target; "narrative" -> Chat; else -> Chat
    }

    fun forMission(type: String): ImageVector = when (type) {
        "investigation" -> Search; "observation" -> Eye; "tactical" -> Target; "rescue" -> Cross
        "undercover" -> Profile; "dialogue", "social" -> Chat; "training" -> Fist; "management" -> Crown
        "story" -> Star; else -> Flag
    }
}

@Composable
fun PnIcon(icon: ImageVector, tint: Color = Pn.Accent, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}
