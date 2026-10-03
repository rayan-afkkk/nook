package com.nook.msgapp.ui.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing

/** Progress segments + serif title used across the first-run setup steps. */
@Composable
fun StepHeader(step: Int, total: Int, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = Nook.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Step $step of $total" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0 until total) {
                val color by animateColorAsState(if (i < step) c.accent else c.surfaceRaised, tween(320), label = "step")
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        NText(title, NookType.title, modifier = Modifier.semantics { heading() })
        NText(subtitle, NookType.body, c.textMuted)
    }
}
