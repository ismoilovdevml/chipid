package com.ismoilovdev.chipid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismoilovdev.chipid.core.Check

@Composable
fun RoundIconButton(icon: ImageVector, description: String, tint: Color = C.text, size: Dp = 20.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(C.control)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(size), tint = tint) }
}

@Composable
fun TopBar(title: String, left: (@Composable () -> Unit)?, right: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp)) { left?.invoke() }
        Text(title, style = T.nav, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.width(44.dp)) { right?.invoke() }
    }
}

@Composable
fun StepBar(step: Int, of: Int = 3) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(of) { i ->
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < step) C.primary else C.track))
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) C.primary else C.track)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) C.onPrimary else C.muted
        icon?.let { Icon(it, null, Modifier.size(20.dp), tint = color); Spacer(Modifier.width(10.dp)) }
        Text(text, style = T.title.copy(color = color))
    }
}

@Composable
fun GhostButton(text: String, icon: ImageVector? = null, height: Dp = 48.dp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = C.text2); Spacer(Modifier.width(8.dp)) }
        Text(text, style = T.body.copy(color = C.text2, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    color: Color = C.surface,
    border: BorderStroke? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.fillMaxWidth().clip(shape).background(color).let { if (border != null) it.border(border, shape) else it }.padding(padding),
        content = content,
    )
}

@Composable
fun IconTile(icon: ImageVector, bg: Color, tint: Color, box: Dp = 52.dp, iconSize: Dp = 26.dp, radius: Dp = 14.dp) {
    Box(Modifier.size(box).clip(RoundedCornerShape(radius)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun LockNote(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.lock, null, Modifier.size(16.dp).padding(top = 1.dp), tint = C.success)
        Text(text, style = T.tiny)
    }
}

@Composable
fun ValueTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = C.text, compact: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(if (compact) 12.dp else 14.dp)).background(if (compact) C.surface2 else C.surface)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = T.tiny.copy(fontSize = if (compact) 11.sp else 12.sp))
        Text(value, style = (if (compact) T.monoSmall else T.mono).copy(color = valueColor), maxLines = 1)
    }
}

/** Status dot used in technical lists. */
@Composable
fun StatusIcon(c: Check, size: Dp = 18.dp) {
    val (icon, tint) = when (c) {
        Check.PASS -> Ic.checkThin to C.success
        Check.FAIL -> Ic.close to C.danger
        Check.UNKNOWN -> Ic.info to C.warn
        Check.SKIPPED -> Ic.info to C.faint
    }
    Icon(icon, c.name, Modifier.size(size), tint = tint)
}

/** Full-screen layout: scrollable body, pinned bottom actions, design gutters. */
@Composable
fun ScreenFrame(
    bg: Color = C.bg,
    gap: Dp = 20.dp,
    scroll: Boolean = true,
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(bg).systemBarsPadding().imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
    ) {
        // Screens are laid out to fit one phone screen; scrolling is only a fallback for small
        // displays or large font scales.
        val body = Modifier.weight(1f).let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
        Column(body, verticalArrangement = Arrangement.spacedBy(gap), content = content)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = bottom)
    }
}

@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))

@Composable
fun ColumnScope.Grow() = Spacer(Modifier.weight(1f))
