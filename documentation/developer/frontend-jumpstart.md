# Frontend Jumpstart

The purpose of this document is to provide some useful tips and tricks on getting started with development of the frontend for VISOR.

### Screen Workflow

Within `VisorLayout.kt`, we use an Observer pattern via `LaunchedEffects` based on the observed uiState

### Screen Padding and the Navigation Bar

VISOR draws edge-to-edge, and `VisorLayout`'s `Scaffold` already keeps every screen clear of the status bar, the system navigation bar and VISOR's own navigation bar (its `bottomBar`). So a screen should:

- **Not** add `navigationBarsPadding()` or `statusBarsPadding()` itself. That is how the band of empty space above the navigation bar kept coming back: the same inset applied twice. (`VisorLayout` now consumes those insets, so a stray one does nothing, but it does not belong there.)
- Put its **horizontal** padding **before** `scrollIndicator(...)`: the scroll bar is drawn just past the column's right edge, inside that padding. Padding added after it puts the bar off the screen.
- Put its **vertical** padding **inside** `verticalScroll(...)`, so content scrolls all the way to the navigation bar instead of vanishing short of it.

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
        .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
        .verticalScroll(scrollState)
        .padding(vertical = 24.dp),
) { /* ... */ }
```

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