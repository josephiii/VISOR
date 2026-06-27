 package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui
 import android.R
 import androidx.compose.ui.graphics.Color
 import androidx.compose.foundation.background
 import androidx.compose.foundation.border
 import androidx.compose.foundation.layout.Arrangement
 import androidx.compose.foundation.layout.Column
 import androidx.compose.foundation.layout.fillMaxSize
 import androidx.compose.foundation.layout.padding
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.material3.Button
 import androidx.compose.runtime.Composable
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.tooling.preview.Preview
 import androidx.compose.material3.Card
 import androidx.compose.material3.TextField
 import androidx.compose.material3.Text
 import androidx.compose.runtime.mutableStateOf
 import androidx.compose.runtime.remember
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.draw.blur
 import androidx.compose.ui.unit.dp
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.setValue
 import okhttp3.OkHttpClient
 import okhttp3.Request
 import okio.IOException
 import org.json.JSONObject
 import okhttp3.MediaType.Companion.toMediaType
 import okhttp3.RequestBody.Companion.toRequestBody


 @Preview(showBackground = true)

 @Composable
 fun LoginScreen(
  modifier: Modifier = Modifier
 ){
  var email by remember { mutableStateOf("") }
  var pass by remember {mutableStateOf("")}
  //Screen Flexbox
  Column(
   modifier = Modifier.fillMaxSize().background(Color.White),
   verticalArrangement = Arrangement.Center,
   horizontalAlignment = Alignment.CenterHorizontally

  ) {
   Text("MIRA AI")
   Text("Where we ... [slogan]")
    Card( modifier = Modifier.background(Color.White).padding(horizontal = 35.dp, vertical =35.dp).border(2.dp, Color.Gray,
     RoundedCornerShape(8.dp)), ){
     Column(verticalArrangement = Arrangement.spacedBy(32.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 20.dp)) {
      Text("Login", modifier= Modifier.padding(5.dp))
      TextField(value = email, placeholder = {Text("email@email.com")} ,onValueChange = {newValue -> email = newValue}, modifier = Modifier.padding(horizontal = 55.dp))
      TextField(value = pass, placeholder = {Text("Password")},onValueChange = {newValue -> pass = newValue}, modifier = Modifier.padding(horizontal = 55.dp))
      Button(onClick = {}) {
       Text("Login")
      }
     }
    }
   Text("New users register here")
   Button(onClick = {}){
    Text("Sign up")
   }

  }
 }
 private suspend fun loginUser(email: String, password: String): String{
  //
  val apiBase ="http://127.0.0.1:8000"
  val payload = JSONObject().put("email", email).put("password", password)
  val req = Request.Builder().url("$apiBase/login").post(payload.toString().toRequestBody("application/json".toMediaType())
  ).build()
  val client = OkHttpClient()
  client.newCall(req).execute().use(){ response ->
   if(response.isSuccessful){
    return "User logged in"
   }
   else{
    //Throw error and show error message
    return "Error logging user in" //+ errorMessage
   }
  }
 }
