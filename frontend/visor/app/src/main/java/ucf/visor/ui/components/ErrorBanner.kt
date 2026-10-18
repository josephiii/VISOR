package ucf.visor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.data.VoiceCategory

/**
 * Something that went wrong, such as a session that couldn't start, held at
 * the top of the screen until the user dismisses it. It used to be a snackbar,
 * which covered what was under it (the modes on Home) and was gone after
 * about four seconds: too soon to read three lines under a magnifier.
 *
 * It is shown as [ErrorMessage] shows an error, and said aloud with [talk] as
 * it appears. Takes no room while there is no [message].
 */
@Composable
fun ErrorBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    talk: ((String, VoiceCategory) -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        ErrorMessage(
            message = message,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
            talk = talk,
        )
        if (message != null) {
            SwitchButton(
                label = stringResource(R.string.dismiss),
                outlined = true,
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
    }
}
