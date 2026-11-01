package ucf.visor.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ucf.visor.R

/**
 * Entry for a code sent by email, a box per digit, under a label saying what
 * goes in it. The boxes share the width, rather than being 48dp each, and grow
 * with the text. Below them, how many digits are in so far, so a full code is
 * never sent without the user knowing.
 *
 * TODO: add SMS/Email Retriever API
 */
@Composable
fun AuthCodeInput(
    codeLength: Int = 6,
    onCodeComplete: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.code_label, codeLength)

    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
    BasicTextField(
        value = code,
        onValueChange = { value ->
            if (value.length <= codeLength) {
                code = value
                if (code.length == codeLength) {
                    keyboardController?.hide()
                    onCodeComplete(code)
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = label },
        decorationBox = {
            // The boxes share the width, rather than being 48dp each: six of
            // those are wider than a narrow phone's card.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(codeLength) { index ->
                    val char = code.getOrNull(index)?.toString() ?: ""
                    val isFocused = code.length == index

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CutCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    )

    Text(
        stringResource(R.string.code_progress, code.length, codeLength),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
