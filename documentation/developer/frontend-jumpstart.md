# Frontend Jumpstart

The purpose of this document is to provide some useful tips and tricks on getting started with development of the frontend for VISOR.

### VISOR App Static Resources

The way Android projects are set up, we can store all of our application's static resources within the `VISOR/frontend/visor/app/src/main/res/` directory. 

For example, we can store any static strings in `strings.xml` located within `VISOR/frontend/visor/app/src/main/res/values`. Many of our screens use and refer to these static strings (we do this to streamline future localization endeavors). 

```kotlin
//...
import androidx.compose.ui.res.stringResource // R.string.* will be of type int if not (refers to the id from strings.xml)
import ucf.visor.R // R refers to our VISOR app's resources
//...

// Example Text component.
Text(  
    text = stringResource(R.string.example_string),  // <- R.string.<string name> 
)
```

or if we want to create a drawable icons:

```kotlin
// Example Icon component.
Icon(  
    painter = painterResource(id = R.drawable.example_drawable), // <- R.drawable.<drawable name> 
)
```

> More information about `R` and our app's static resources can be found in this [stack overflow answer](https://stackoverflow.com/questions/63333247/whats-r-in-kotlin-android-studio) its linked Android documentation.

### Preview for Components

- Depending on the size of the project, and since last a build was completed, creating a previewable function may take some time. Nevertheless, this is a very useful UI debug tool. 
- Composable functions can be previewed using the `@Preview` notation (right above `@Composable`), so long that they do not take in any parameters (*exception: default parameters*). If the composable function has parameters, create a second composable function (a wrapper) to eliminate that parameter. Below is the example provided through [Androids Compose Tutorial](https://developer.android.com/develop/ui/compose/tutorial):  

```kotlin
import androidx.compose.ui.tooling.preview.Preview

// The Non-"Previewable" compose function. 
@Composable
fun MessageCard(name: String) {
    Text(text = "Hello $name!")
}

// Solution:
@Preview
@Composable
fun PreviewMessageCard() {
    MessageCard("Android")
}
```