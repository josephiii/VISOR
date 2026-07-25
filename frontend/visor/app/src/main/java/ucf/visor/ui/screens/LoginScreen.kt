package ucf.visor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ucf.visor.wearables.WearablesViewModel

@Composable
fun LoginScreen(
    viewModel: WearablesViewModel,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // Screen Flexbox
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        Text(
            "VISOR",
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,

            )
        Text(
            "The Visual Intelligence Systems for Ocular Rehabilitation",
            fontSize = 20.sp,
            textAlign = TextAlign.Justify
        )
        Card {
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp)
            ) {
                Text(
                    "Login",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(5.dp)
                )
                TextField(
                    value = email,
                    placeholder = { Text("email@email.com") },
                    onValueChange = { newValue -> email = newValue },
                    modifier = Modifier.padding(horizontal = 55.dp)
                )
                TextField(
                    value = pass,
                    placeholder = { Text("Password") },
                    onValueChange = { newValue -> pass = newValue },
                    modifier = Modifier.padding(horizontal = 55.dp)
                )
                Button(onClick = {
                    scope.launch {
                    }
                }) {
                    Text("Login")
                }
            }
        }
        Text("New users register here")
        Button(onClick = {
            // Navigate to register
        }) {
            Text("Sign up")
        }

    }
}
