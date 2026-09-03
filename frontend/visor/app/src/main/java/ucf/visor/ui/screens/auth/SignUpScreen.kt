package ucf.visor.ui.screens.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ucf.visor.R
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.VisorHeader
import ucf.visor.ui.components.VisorTextField
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable
fun SignUpScreen(
    viewModel: VisorViewModel,
    onSignUpClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmedPassword by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {

        VisorHeader()

        Card(
            modifier = Modifier
                .padding(
                    horizontal = 35.dp,
                    vertical = 20.dp
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CutCornerShape(8.dp)
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 20.dp)
            ) {

                Text(
                    text = stringResource(R.string.sign_up_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                )

                VisorTextField(
                    value = username,
                    label = stringResource(R.string.username_label),
                    onValueChange = { username = it },
                )

                VisorTextField(
                    value = email,
                    label = stringResource(R.string.email_label),
                    onValueChange = { email = it },
                )

                VisorTextField(
                    value = password,
                    label = stringResource(R.string.password_label),
                    onValueChange = { password = it },
                    isPassword = true
                )

                VisorTextField(
                    value = confirmedPassword,
                    label = stringResource(R.string.confirm_password_label),
                    onValueChange = { confirmedPassword = it },
                    isPassword = true
                )

                VisorButton(
                    text = stringResource(R.string.sign_up_title),
                    onClick = {
                        onSignUpClick()
//                        scope.launch {
//                            val result = registerUser(email, password, username, confirmedPassword)
//                        }
                    }
                )


            }
        }
        Text(stringResource(R.string.already_have_account))

        VisorButton(
            text = stringResource(R.string.login_title),
            width = 0.5f,
            onClick = {
                onLoginClick()
            }
        )
    }
}

private suspend fun registerUser(
    email: String,
    password: String,
    username: String,
    confirmedPassword: String
): String {
    // Confirm email is valid email and unique
    val emailRegex = Regex("""^\w+@\w+\.\w+$""")
    if (!(emailRegex.matches(email))) {
        return "Invalid email"
    }
    // Unique check requires db

    // Check that password and confirm pw match
    if (password != confirmedPassword) {
        return "The passwords do not match"
    }
    val apiBase = "http://127.0.0.1:8000"
    val payload =
        JSONObject().put("email", email).put("username", username).put("password", password)
    val req = Request.Builder().url("$apiBase/register").post(
        payload.toString().toRequestBody("application/json".toMediaType())
    ).build()
    val client = OkHttpClient()
    client.newCall(req).execute().use() { response ->
        if (response.isSuccessful) {
            return "User registered"
            //Navigate user to the login page (with information pre-filled )
        } else {
            //Throw error and show error message
            return "Error registering user" //+ errorMessage
        }
    }
}