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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismoilovdev.chipid.R
import com.ismoilovdev.chipid.core.AccessInput
import com.ismoilovdev.chipid.reader.AccessKey
import java.time.LocalDate

/** Shows stored digits "15031990" as "15.03.1990"; the dots are never part of the value. */
private object DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = buildString {
            d.forEachIndexed { i, c ->
                append(c)
                if ((i == 1 || i == 3) && i < d.length - 1) append('.')
            }
        }
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(out.length)

            override fun transformedToOriginal(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(out), map)
    }
}

private enum class State { EMPTY, OK, ERROR }

/** Prefill comes from a camera scan the user wants to correct. */
@Composable
fun ManualScreen(
    docType: DocType,
    prefill: AccessKey.Mrz?,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onSubmit: (AccessKey) -> Unit,
) {
    // Plain remember, not rememberSaveable: key material must not enter the saved-state bundle.
    var useCan by remember { mutableStateOf(docType == DocType.ID_CARD && prefill == null) }
    var doc by remember { mutableStateOf(prefill?.documentNumber.orEmpty()) }
    var dob by remember { mutableStateOf(prefill?.dateOfBirth?.let { toDigits(it, birth = true) }.orEmpty()) }
    var exp by remember { mutableStateOf(prefill?.dateOfExpiry?.let { toDigits(it, birth = false) }.orEmpty()) }
    var can by remember { mutableStateOf("") }

    val docOk = AccessInput.normalizeDocumentNumber(doc) != null
    val dobDate = AccessInput.toLocalDate(dob)
    val dobState = when {
        dob.length < 8 -> State.EMPTY
        dobDate == null || dobDate.isAfter(LocalDate.now()) -> State.ERROR
        else -> State.OK
    }
    val expState = when {
        exp.length < 8 -> State.EMPTY
        AccessInput.toLocalDate(exp) == null -> State.ERROR
        else -> State.OK
    }
    val key: AccessKey? = if (useCan) {
        AccessInput.normalizeCan(can)?.let { AccessKey.Can(it) }
    } else if (docOk && dobState == State.OK && expState == State.OK) {
        AccessKey.Mrz(AccessInput.normalizeDocumentNumber(doc)!!, AccessInput.toYymmdd(dob)!!, AccessInput.toYymmdd(exp)!!)
    } else null

    ScreenFrame(
        bottom = {
            LockNote(stringResource(R.string.key_note))
            PrimaryButton(stringResource(R.string.continue_), enabled = key != null) { key?.let(onSubmit) }
        },
    ) {
        TopBar(
            stringResource(R.string.enter_manually),
            left = { RoundIconButton(Ic.back, stringResource(R.string.back), onClick = onBack) },
            right = { RoundIconButton(Ic.camera, stringResource(R.string.open_camera), tint = C.primary, onClick = onCamera) },
        )

        if (docType == DocType.ID_CARD) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.surface).padding(4.dp)) {
                Segment(stringResource(R.string.mode_can), useCan, Modifier.weight(1f)) { useCan = true }
                Segment(stringResource(R.string.mode_mrz), !useCan, Modifier.weight(1f)) { useCan = false }
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.surface).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DocThumbnail(highlightMrz = !useCan)
            Text(
                stringResource(
                    when {
                        useCan -> R.string.hint_can
                        docType == DocType.PASSPORT -> R.string.hint_passport
                        else -> R.string.hint_id_mrz
                    },
                ),
                style = T.small.copy(color = C.text2),
            )
        }

        if (useCan) {
            Field(
                label = stringResource(R.string.field_can), value = can, onChange = { can = it.filter(Char::isDigit).take(6) },
                state = if (can.length == 6) State.OK else State.EMPTY, helper = stringResource(R.string.helper_can),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                big = true,
            )
        } else {
            Field(
                label = stringResource(if (docType == DocType.PASSPORT) R.string.field_passport_number else R.string.field_document_number),
                value = doc, onChange = { doc = AccessInput.documentDigits(it) },
                state = when {
                    doc.isEmpty() -> State.EMPTY
                    docOk -> State.OK
                    else -> State.ERROR
                },
                helper = stringResource(R.string.helper_doc),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                big = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                    label = stringResource(R.string.field_dob), value = dob, onChange = { dob = AccessInput.dateDigits(it) },
                    state = dobState, placeholder = "DD.MM.YYYY", mask = DateMask, modifier = Modifier.weight(1f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                Field(
                    label = stringResource(R.string.field_expiry), value = exp, onChange = { exp = AccessInput.dateDigits(it) },
                    state = expState, placeholder = "DD.MM.YYYY", mask = DateMask, modifier = Modifier.weight(1f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
            Text(
                when {
                    dobState == State.ERROR -> stringResource(R.string.err_dob)
                    expState == State.ERROR -> stringResource(R.string.err_date)
                    else -> stringResource(R.string.helper_dates)
                },
                style = T.tiny.copy(color = if (dobState == State.ERROR || expState == State.ERROR) C.danger else C.muted),
                modifier = Modifier.padding(top = 0.dp),
            )
        }
    }
}

private fun toDigits(yymmdd: String, birth: Boolean): String = ReportModel.date(yymmdd, birth).replace(".", "")

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) C.selectedBg else C.surface)
            .border(BorderStroke(1.dp, if (selected) C.primary else C.surface), RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.label.copy(color = if (selected) C.primary else C.text2)) }
}

@Composable
private fun DocThumbnail(highlightMrz: Boolean) {
    Column(
        Modifier.width(96.dp).height(66.dp).clip(RoundedCornerShape(8.dp)).background(C.paper).padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.width(18.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(C.paperInk))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.paperInk))
                Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (highlightMrz) C.paperInk else C.warn))
                Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.paperInk))
            }
        }
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(2.dp))
                .border(1.5.dp, if (highlightMrz) C.primary else C.paperInk, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    state: State,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier,
    helper: String? = null,
    placeholder: String? = null,
    mask: VisualTransformation = VisualTransformation.None,
    big: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        state == State.ERROR -> C.danger
        focused -> C.primary
        else -> C.fieldBorder
    }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = T.label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = keyboard,
            visualTransformation = mask,
            cursorBrush = SolidColor(C.primary),
            textStyle = T.mono.copy(fontSize = if (big) 18.sp else 16.sp, letterSpacing = if (big) 0.08.em else 0.em),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(shape).background(if (focused) C.selectedBg else C.surface)
                        .border(BorderStroke(2.dp, borderColor), shape).padding(horizontal = if (big) 16.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = T.mono.copy(fontSize = 16.sp, color = C.faint))
                        inner()
                    }
                    if (state == State.OK) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Ic.checkThin, null, Modifier.size(20.dp), tint = C.success)
                    }
                }
            },
        )
        helper?.let { Text(it, style = T.tiny) }
    }
}
