package com.ismoilovdev.chipid.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ismoilovdev.chipid.R
import com.ismoilovdev.chipid.reader.ReadProgress
import com.ismoilovdev.chipid.reader.Stage

@Composable
fun NfcScreen(
    docType: DocType,
    progress: ReadProgress?,
    error: String?,
    nfcEnabled: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onHelp: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    ScreenFrame(gap = 16.dp, bottom = { GhostButton(stringResource(R.string.where_is_chip), onClick = onHelp) }) {
        TopBar(
            stringResource(R.string.step_of, 2, 3),
            left = { RoundIconButton(Ic.close, stringResource(R.string.cancel), size = 18.dp, onClick = onCancel) },
        )
        StepBar(2)
        Illustration(reading = progress != null && error == null)

        val title = when {
            error != null -> R.string.nfc_failed
            progress != null -> R.string.nfc_hold_still
            else -> R.string.nfc_hold_doc
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = T.h3, textAlign = TextAlign.Center)
            Text(
                stringResource(if (docType == DocType.PASSPORT) R.string.nfc_sub_passport else R.string.nfc_sub_id),
                style = T.bodyMuted, textAlign = TextAlign.Center,
            )
        }

        if (!nfcEnabled) {
            Panel(color = C.warnBg, border = BorderStroke(1.dp, C.warnBorder), padding = 16.dp) {
                Text(stringResource(R.string.nfc_off), style = T.body.copy(color = C.warnText))
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.open_nfc_settings), onClick = onOpenNfcSettings)
            }
        }

        error?.let {
            Panel(color = C.dangerBg, border = BorderStroke(1.dp, C.dangerBorder), padding = 16.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Ic.alert, null, Modifier.size(20.dp), tint = C.danger)
                    Text(it, style = T.body.copy(color = C.dangerText))
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.try_again), onClick = onRetry)
            }
        }

        val fraction by animateFloatAsState(progress?.fraction ?: 0f, tween(250), label = "progress")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reading_data), style = T.small.copy(color = C.text2))
                Grow()
                Text("${(fraction * 100).toInt()}%", style = T.monoSmall.copy(color = C.primary))
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.track2)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (error != null) C.danger else C.primary))
            }
        }

        Panel(padding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val current = progress?.stage
                listOf(
                    Stage.FOUND to R.string.stage_found,
                    Stage.CHANNEL to R.string.stage_channel,
                    Stage.DATA to R.string.stage_data,
                    Stage.VERIFY to R.string.stage_verify,
                ).forEach { (stage, label) ->
                    val state = when {
                        current == null -> 0
                        stage.ordinal < current.ordinal -> 2
                        stage == current -> if (error != null) 3 else 1
                        else -> 0
                    }
                    StageRow(stringResource(label), state)
                }
            }
        }
    }
}

/** 0 pending, 1 active, 2 done, 3 failed. */
@Composable
private fun StageRow(text: String, state: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            2 -> Box(Modifier.size(24.dp).clip(CircleShape).background(C.success), contentAlignment = Alignment.Center) {
                Icon(Ic.check, null, Modifier.size(14.dp), tint = C.successBg)
            }
            1 -> Spinner()
            3 -> Box(Modifier.size(24.dp).clip(CircleShape).background(C.danger), contentAlignment = Alignment.Center) {
                Icon(Ic.close, null, Modifier.size(12.dp), tint = C.dangerBg)
            }
            else -> Box(Modifier.size(24.dp).border(2.dp, C.outline, CircleShape))
        }
        Text(
            text,
            style = T.body.copy(
                fontWeight = if (state == 1) FontWeight.SemiBold else FontWeight.Normal,
                color = if (state == 0) C.muted else C.text,
            ),
        )
    }
}

@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    Canvas(Modifier.size(24.dp).rotate(angle)) {
        val w = 3.dp.toPx()
        drawCircle(C.track2, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
        drawArc(C.primary, -90f, 270f, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
    }
}

@Composable
private fun Illustration(reading: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "scale")
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((200 * if (reading) pulse else 1f).dp).border(1.dp, C.ring1, CircleShape))
        Box(Modifier.size((150 * if (reading) pulse else 1f).dp).border(1.dp, C.ring2, CircleShape))
        Box(Modifier.offset(y = 32.dp).rotate(-6f).size(160.dp, 108.dp).clip(RoundedCornerShape(12.dp)).background(C.paper))
        Box(
            Modifier.offset(y = (-12).dp).size(84.dp, 158.dp).clip(RoundedCornerShape(20.dp)).background(C.control)
                .border(3.dp, C.outline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.nfc, null, Modifier.size(36.dp), tint = C.primary) }
    }
}

@Composable
fun HelpContent(docType: DocType, onClose: () -> Unit) {
    ScreenFrame(bottom = { PrimaryButton(stringResource(R.string.got_it), onClick = onClose) }) {
        TopBar(stringResource(R.string.where_is_chip_title), left = { RoundIconButton(Ic.close, stringResource(R.string.close), size = 18.dp, onClick = onClose) })
        listOf(
            R.string.help_1 to R.string.help_1_sub,
            R.string.help_2 to R.string.help_2_sub,
            R.string.help_3 to R.string.help_3_sub,
            R.string.help_4 to R.string.help_4_sub,
        ).forEach { (t, s) ->
            Panel(padding = 16.dp) {
                Text(stringResource(t), style = T.title.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(s), style = T.small)
            }
        }
        if (docType == DocType.ID_CARD) Text(stringResource(R.string.help_id), style = T.small, modifier = Modifier.padding(horizontal = 4.dp))
    }
}
