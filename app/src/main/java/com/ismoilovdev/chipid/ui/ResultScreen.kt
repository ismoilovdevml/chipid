package com.ismoilovdev.chipid.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismoilovdev.chipid.R
import com.ismoilovdev.chipid.core.Check
import com.ismoilovdev.chipid.core.Countries
import com.ismoilovdev.chipid.core.MasterList
import com.ismoilovdev.chipid.core.combine
import com.ismoilovdev.chipid.reader.ReadReport

@Composable
fun ResultScreen(r: ReadReport, onClose: () -> Unit, onNew: () -> Unit, onDetails: () -> Unit, onExport: () -> Unit) {
    val pa = r.passive
    val signature = combine(pa.dataGroupHashes, pa.sodSignature)
    val verdict = when {
        pa.overall == Check.FAIL || r.chipGenuine == Check.FAIL -> Check.FAIL
        pa.overall == Check.UNKNOWN -> Check.UNKNOWN
        else -> Check.PASS
    }
    val country = ReportModel.signerCountry(r)

    ScreenFrame(
        gap = 18.dp,
        bottom = {
            PrimaryButton(stringResource(R.string.new_check), onClick = onNew)
            GhostButton(stringResource(R.string.technical_details), height = 44.dp, onClick = onDetails)
        },
    ) {
        TopBar(
            stringResource(R.string.result),
            left = { RoundIconButton(Ic.close, stringResource(R.string.close), size = 18.dp, onClick = onClose) },
            right = { RoundIconButton(Ic.export, stringResource(R.string.export_raw), size = 18.dp, onClick = onExport) },
        )
        Banner(verdict, country)
        PersonCard(r)
        r.mrz?.let { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValueTile(stringResource(R.string.field_dob), ReportModel.date(m.dateOfBirth, birth = true), Modifier.weight(1f))
                ValueTile(stringResource(R.string.valid_until), ReportModel.date(m.dateOfExpiry), Modifier.weight(1f))
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
            CheckRow(stringResource(R.string.check_signature), signature)
            HorizontalDivider(color = C.divider)
            CheckRow(
                stringResource(
                    when {
                        r.chipAuth.check == Check.PASS -> R.string.check_clone_ca
                        r.activeAuth?.check == Check.PASS -> R.string.check_clone_aa
                        r.chipGenuine == Check.UNKNOWN -> R.string.check_clone_na
                        else -> R.string.check_clone_ca
                    },
                ),
                r.chipGenuine,
            )
            HorizontalDivider(color = C.divider)
            val cc = pa.dsc?.issuerX500Principal?.let { MasterList.country(it) } ?: r.mrz?.issuingState ?: "?"
            CheckRow(stringResource(R.string.check_chain, cc), pa.cscaChain, onClick = onDetails)
        }
    }
}

@Composable
private fun Banner(verdict: Check, country: String) {
    val (bg, border, fg, sub, icon, title, text) = when (verdict) {
        Check.PASS -> Style(C.successBg, C.successBorder, C.success, C.successText, Ic.check, stringResource(R.string.verdict_pass), stringResource(R.string.verdict_pass_sub, country))
        Check.UNKNOWN -> Style(C.warnBg, C.warnBorder, C.warn, C.warnText, Ic.info, stringResource(R.string.verdict_unknown), stringResource(R.string.verdict_unknown_sub, country))
        else -> Style(C.dangerBg, C.dangerBorder, C.danger, C.dangerText, Ic.alert, stringResource(R.string.verdict_fail), stringResource(R.string.verdict_fail_sub))
    }
    Panel(radius = 20.dp, color = bg, border = BorderStroke(1.dp, border)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(fg), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = bg)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = T.title.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = fg))
                Text(text, style = T.small.copy(color = sub))
            }
        }
    }
}

private data class Style(val bg: Color, val border: Color, val fg: Color, val sub: Color, val icon: ImageVector, val title: String, val text: String)

@Composable
private fun PersonCard(r: ReadReport) {
    var reveal by remember { mutableStateOf(false) }
    val (sur, given) = ReportModel.name(r)
    val face = r.faces.firstOrNull()
    val bitmap = remember(face) { face?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(92.dp).height(118.dp).clip(RoundedCornerShape(12.dp)).background(C.border), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), stringResource(R.string.chip_photo), Modifier.width(92.dp).height(118.dp), contentScale = ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.user, null, Modifier.size(36.dp), tint = C.faint)
                    face?.let { Text(it.mimeType.substringAfter('/'), style = T.tiny.copy(fontSize = 10.sp)) }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.name), style = T.tiny)
            Text("$sur\n$given".trim(), style = T.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp))
            r.mrz?.let { m ->
                Text(stringResource(R.string.nationality, Countries.name(m.nationality)), style = T.small.copy(color = C.text2))
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.show_number)) { reveal = !reveal },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (reveal) m.documentNumber else mask(m.documentNumber), style = T.monoSmall.copy(color = C.text2))
                    Spacer(Modifier.width(6.dp))
                    Icon(Ic.eye, null, Modifier.size(14.dp), tint = C.faint)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String, c: Check, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusIcon(c)
        Text(text, style = T.body.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Ic.chevron, null, Modifier.size(16.dp), tint = C.faint)
    }
}

@Composable
fun DetailsScreen(r: ReadReport, exportMessage: String?, onBack: () -> Unit, onExport: () -> Unit, onWipe: () -> Unit) {
    ScreenFrame(
        gap = 14.dp,
        bottom = {
            exportMessage?.let { Text(it, style = T.tiny.copy(fontFamily = Mono)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.export_raw), icon = Ic.export, onClick = onExport) }
                RoundIconButton(Ic.trash, stringResource(R.string.delete_exports), tint = C.danger, onClick = onWipe)
            }
        },
    ) {
        TopBar(stringResource(R.string.technical_title), left = { RoundIconButton(Ic.back, stringResource(R.string.back), onClick = onBack) })
        ReportModel.sections(r).forEach { s ->
            Panel(padding = 16.dp) {
                Text(s.title.uppercase(), style = T.overline)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.check?.let { Box(Modifier.padding(top = 2.dp)) { StatusIcon(it, 16.dp) } }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.label, style = T.tiny)
                                Text(row.value, style = if (s.mono) T.monoSmall.copy(fontSize = 12.sp, color = C.text2) else T.body.copy(fontSize = 14.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}
