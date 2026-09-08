package ucf.visor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

import ucf.visor.R

// VISOR acronym header. Used across onboarding and login screens.
@Preview
@Composable
fun VisorHeader(
    slogan: String = stringResource(R.string.visor_acronym),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = slogan,
            fontSize = 14.sp
        )
    }
}