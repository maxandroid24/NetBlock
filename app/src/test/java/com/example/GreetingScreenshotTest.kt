package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NetBlockDatabase
import com.example.data.repository.BlockedAppRepository
import com.example.presentation.ui.OnboardingScreen
import com.example.presentation.viewmodel.NetBlockViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
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

    @get:Rule 
    val composeTestRule = createComposeRule()

    private lateinit var db: NetBlockDatabase
    private lateinit var repository: BlockedAppRepository
    private lateinit var viewModel: NetBlockViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NetBlockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BlockedAppRepository(db.blockedAppDao())
        viewModel = NetBlockViewModel(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun greeting_screenshot() {
        composeTestRule.setContent { 
            MyApplicationTheme { 
                OnboardingScreen(viewModel = viewModel) 
            } 
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
