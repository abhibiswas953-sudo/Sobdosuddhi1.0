package com.sobdosuddhi.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SobdosuddhiUiInstrumentedTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun fullBengaliNavigationAndOfflineProofreadingUiWorksOnDevice() {
    composeRule.waitUntil(timeoutMillis = 3_000L) {
      runCatching {
        composeRule.onNodeWithText("নতুন লেখা").assertIsDisplayed()
        true
      }.getOrDefault(false)
    }

    composeRule.onNodeWithText("নতুন লেখা").performClick()
    composeRule.onNodeWithText("সংরক্ষণ").assertIsDisplayed()

    composeRule.onNodeWithText("প্রুফরিডিং").performClick()
    composeRule.onNodeWithText("উন্নত বাংলা প্রুফরিডিং ইঞ্জিন").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("ফিরুন").performClick()

    composeRule.onNodeWithText("আমার পান্ডুলিপি").performClick()
    composeRule.onNodeWithText("ইতিহাস").performClick()
    composeRule.onNodeWithText("সেটিংস").performClick()
    composeRule.onNodeWithText("হোম").performClick()
    composeRule.onNodeWithText("শব্দশুদ্ধি ১.০").assertIsDisplayed()
  }
}
