package com.ismoilovdev.chipid.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismoilovdev.chipid.R
import com.ismoilovdev.chipid.core.Check
import com.ismoilovdev.chipid.reader.TrustStore

@Composable
fun AboutScreen(trust: TrustStore?, onBack: () -> Unit) {
    val context = LocalContext.current
    // Read from the installed package, not asserted: proves what the OS will enforce.
    val requested = remember {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
    }
    val noNetwork = Manifest.permission.INTERNET !in requested

    ScreenFrame(gap = 14.dp) {
        TopBar(stringResource(R.string.about_title), left = { RoundIconButton(Ic.back, stringResource(R.string.back), onClick = onBack) })
        Block(stringResource(R.string.about_privacy)) {
            Line(if (noNetwork) Check.PASS else Check.FAIL, stringResource(if (noNetwork) R.string.priv_network_ok else R.string.priv_network_bad))
            Line(Check.PASS, stringResource(R.string.priv_camera))
            Line(Check.PASS, stringResource(R.string.priv_memory))
            Line(Check.PASS, stringResource(R.string.priv_backup))
        }
        Block(stringResource(R.string.about_trust)) {
            if (trust == null) {
                Text(stringResource(R.string.loading), style = T.small)
            } else {
                Line(if (trust.bundledTrusted) Check.PASS else Check.FAIL, stringResource(if (trust.bundledTrusted) R.string.trust_pinned_ok else R.string.trust_pinned_bad))
                Text(stringResource(R.string.trust_summary, trust.anchors.size, trust.countries), style = T.body.copy(fontSize = 14.sp))
                if (trust.userAddedCount > 0) Line(Check.UNKNOWN, stringResource(R.string.trust_user_added, trust.userAddedCount))
                trust.sources.forEach { Text(it, style = T.monoSmall.copy(fontSize = 11.sp, color = C.muted)) }
                Text(stringResource(R.string.trust_add), style = T.tiny)
            }
        }
        Block(stringResource(R.string.about_how)) {
            Text(stringResource(R.string.about_how_text), style = T.small.copy(color = C.text2))
        }
        Block(stringResource(R.string.about_licenses)) {
            Text(stringResource(R.string.about_licenses_text), style = T.small)
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Panel(padding = 16.dp) {
        Text(title.uppercase(), style = T.overline)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Line(c: Check, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusIcon(c, 16.dp)
        Text(text, style = T.body.copy(fontSize = 14.sp))
    }
}
