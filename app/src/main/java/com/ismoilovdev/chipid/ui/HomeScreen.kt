package com.ismoilovdev.chipid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismoilovdev.chipid.R

enum class DocType { PASSPORT, ID_CARD }

@Composable
fun HomeScreen(docType: DocType, onSelect: (DocType) -> Unit, onStart: () -> Unit, onSettings: () -> Unit) {
    ScreenFrame(gap = 20.dp, bottom = { HomeFooter(onStart) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.primary), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chipid_mark), null, Modifier.size(24.dp), tint = C.onPrimary)
            }
            Spacer(Modifier.width(10.dp))
            Text("ChipID", style = T.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Grow()
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onSettings), contentAlignment = Alignment.Center) {
                Icon(Ic.info, stringResource(R.string.about_title), Modifier.size(22.dp), tint = C.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_title), style = T.h1)
            Text(stringResource(R.string.home_subtitle), style = T.bodyMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DocOption(Ic.passport, stringResource(R.string.doc_passport), stringResource(R.string.doc_passport_sub), docType == DocType.PASSPORT) { onSelect(DocType.PASSPORT) }
            DocOption(Ic.idCard, stringResource(R.string.doc_id), stringResource(R.string.doc_id_sub), docType == DocType.ID_CARD) { onSelect(DocType.ID_CARD) }
        }
        Panel {
            Text(stringResource(R.string.how_it_works).uppercase(), style = T.overline)
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(R.string.how_1, R.string.how_2, R.string.how_3).forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(C.iconBg), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = T.small.copy(color = C.primary, fontWeight = FontWeight.Bold))
                        }
                        Text(stringResource(s), style = T.body)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFooter(onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.lock, null, Modifier.size(16.dp), tint = C.success)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.never_leaves), style = T.small)
        }
        PrimaryButton(stringResource(R.string.start_scanning), icon = Ic.scan, onClick = onStart)
    }
}

@Composable
private fun DocOption(icon: ImageVector, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) C.selectedBg else C.surface)
            .border(BorderStroke(2.dp, if (selected) C.primary else C.border), shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(icon, if (selected) C.iconBg else C.iconBgMuted, if (selected) C.primary else C.text2)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = T.title)
            Text(sub, style = T.small)
        }
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(C.primary), contentAlignment = Alignment.Center) {
                Icon(Ic.check, null, Modifier.size(14.dp), tint = C.onPrimary)
            }
        } else {
            Box(Modifier.size(24.dp).border(2.dp, C.outline, CircleShape))
        }
    }
}
