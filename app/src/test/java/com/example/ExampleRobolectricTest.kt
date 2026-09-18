package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TabloRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tablo TV", appName)
  }

  @Test
  fun `test TabloRepository loads channels and airings`() = runBlocking {
    val repository = TabloRepository()
    val channels = repository.getMockChannels()
    assertTrue(channels.isNotEmpty())
    assertEquals(7, channels.size)

    val airings = repository.getMockGuideAirings()
    assertTrue(airings.isNotEmpty())

    val demoDevice = repository.getDemoDevice()
    assertNotNull(demoDevice)
    assertEquals(4, demoDevice.tunerCount)
  }
}
