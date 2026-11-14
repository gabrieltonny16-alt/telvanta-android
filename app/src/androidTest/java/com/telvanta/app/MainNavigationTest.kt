package com.telvanta.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun launchLocalWorkspace() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SecureSessionStore(context).enableLocalWorkspace()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeLocalWorkspace() {
        scenario?.close()
        SecureSessionStore(context).clear()
    }

    @Test
    fun drawerAndBottomNavigationOpenTheirExpectedScreens() {
        composeRule.onNodeWithText("DASHBOARD").assertIsDisplayed()

        composeRule.onNodeWithText("≡").performClick()
        composeRule.onNodeWithText("Customers").performClick()
        composeRule.onNodeWithText("CUSTOMERS").assertIsDisplayed()

        composeRule.onNodeWithText("≡").performClick()
        composeRule.onNodeWithText("Signature").performClick()
        composeRule.onNodeWithText("SIGNATURE").assertIsDisplayed()

        composeRule.onNodeWithText("≡").performClick()
        composeRule.onNodeWithText("Offers").performClick()
        composeRule.onNodeWithText("OFFERS").assertIsDisplayed()

        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.onNodeWithText("TRANSACTION HISTORY").assertIsDisplayed()

        composeRule.onNodeWithText("Wallet").performClick()
        composeRule.onNodeWithText("WALLET").assertIsDisplayed()
    }
}
