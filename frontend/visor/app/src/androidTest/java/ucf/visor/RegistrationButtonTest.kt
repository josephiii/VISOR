/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package ucf.visor

import android.Manifest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that tapping the "Connect my glasses" registration button does not crash the app.
 *
 * The mwdat-core SDK uses FragmentActivity internally for its registration flow
 * (RegistrationManagerImpl.launchIntentForResult), but does not bundle it in its AAR. If
 * androidx.fragment is missing from the app's dependencies, the app crashes at runtime with
 * NoClassDefFoundError when the user taps the register button.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RegistrationButtonTest {

  @get:Rule(order = 0)
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(
          Manifest.permission.BLUETOOTH_CONNECT,
          Manifest.permission.RECORD_AUDIO,
          Manifest.permission.CAMERA,
      )

  @get:Rule(order = 1) val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun clickingConnectMyGlassesDoesNotCrash() {
    val activity = composeTestRule.activity
    // The button lives on the Pair Glasses screen, which is reached after login.
    composeTestRule.waitUntil(15_000) { activity.viewModel.uiState.value.canRegister }
    composeTestRule.runOnUiThread {
      activity.viewModel.home()
      activity.viewModel.hardwarePairing()
    }
    val buttonText = activity.getString(R.string.register_button_title)
    composeTestRule.waitUntil(10_000) {
      composeTestRule.onAllNodes(hasText(buttonText)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText(buttonText).performScrollTo().performClick()

    // Verify the app is still alive after the registration coroutine executes.
    // If FragmentActivity is missing, the app crashes and this assertion fails.
    composeTestRule.onNodeWithText(buttonText).assertExists()
  }
}
