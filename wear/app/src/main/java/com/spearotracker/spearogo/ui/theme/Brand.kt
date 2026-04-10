package com.spearotracker.spearogo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spearotracker.spearogo.models.Verdict

object Brand {

    object Colors {
        // Base
        val background = Color(0xFF000000)
        val primary = Color(0xFF0077B6)      // OceanBlue
        val secondary = Color(0xFF00B4D8)    // Teal

        // Text
        val textPrimary = Color(0xFFFFFFFF)
        val textSecondary = Color(0xFF6B7D8E)

        // Verdicts
        val go = Color(0xFF2ECC71)
        val maybe = Color(0xFFF39C12)
        val sketchy = Color(0xFFE67E22)
        val noGo = Color(0xFFE74C3C)

        // Semantic aliases
        val safe = go
        val caution = maybe
        val warning = sketchy
        val danger = noGo
        val accent = secondary

        fun forVerdict(verdict: Verdict): Color = when (verdict) {
            Verdict.GO -> go
            Verdict.MAYBE -> maybe
            Verdict.SKETCHY -> sketchy
            Verdict.NO_GO -> noGo
        }
    }

    object Typography {
        val verdictLabel = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black)
        val dataValue = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val scoreNumber = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
        val timeDisplay = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val periodTime = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
        val personalityCopy = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
        val sectionHeader = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        val itemLabel = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        val unit = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)
        val caption = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)
    }

    object Spacing {
        val page = 12.dp
        val section = 10.dp
        val item = 6.dp
        val micro = 2.dp
    }

    object Kerning {
        val sectionHeader = 2.sp
        val itemLabel = 1.sp
        val unitLabel = 0.5.sp
    }

    object Radius {
        val card = 12.dp
        val chip = 8.dp
        val badge = 6.dp
        val pill = 100.dp
    }

    object Opacity {
        const val ringTrack = 0.30f
        const val cardFill = 0.04f
        const val borderLine = 0.08f
        const val disabled = 0.35f
    }

    object Ring {
        val size = 58.dp
        val strokeWidth = 5.dp
    }
}
