package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.models.MatchItem
import com.example.ui.components.MatchCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleMatch = MatchItem(
      id = "test_1",
      homeTeam = "Botafogo SP",
      homeLogoUrl = "",
      awayTeam = "Atlético GO",
      awayLogoUrl = "",
      championship = "Brasileirão Série B",
      time = "19:30",
      dateTag = "HOJE",
      detailUrl = "",
      isLiveNow = true
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MatchCard(
          match = sampleMatch,
          onClick = {},
          onFavoriteClick = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

