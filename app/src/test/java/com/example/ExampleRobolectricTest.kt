package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.TabloAiringDetailResponse
import com.example.data.remote.TabloAiringDetailsInner
import com.example.data.remote.TabloApiMapper
import com.example.data.remote.TabloChannelDetailResponse
import com.example.data.remote.TabloChannelInner
import com.example.data.remote.TabloEpisodeInner
import com.example.data.remote.TabloTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric 4.16 supports Android 15 (API 35); compileSdk may remain newer.
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tablo TV", appName)
  }

  @Test
  fun `parseIso8601 parses UTC Z datetimes`() {
    assertEquals(
      TabloTime.parseIso8601("2023-01-03T01:00Z"),
      TabloTime.parseIso8601("2023-01-03T01:00:00Z")
    )
  }

  @Test
  fun `parseIso8601 applies timezone offsets`() {
    assertEquals(
      TabloTime.parseIso8601("2023-01-03T01:00+02:00"),
      TabloTime.parseIso8601("2023-01-02T23:00Z")
    )
    assertEquals(
      TabloTime.parseIso8601("2023-01-03T01:00-02:00"),
      TabloTime.parseIso8601("2023-01-03T03:00Z")
    )
    assertEquals(
      TabloTime.parseIso8601("2023-01-03T01:00-0200"),
      TabloTime.parseIso8601("2023-01-03T03:00Z")
    )
  }

  @Test
  fun `parseIso8601 rejects invalid input`() {
    assertNull(TabloTime.parseIso8601(null))
    assertNull(TabloTime.parseIso8601(""))
    assertNull(TabloTime.parseIso8601("garbage"))
    assertNull(TabloTime.parseIso8601("2023-13-40T99:99Z"))
    assertNull(TabloTime.parseIso8601("2023-01-03 01:00"))
  }

  @Test
  fun `channelFromDetail maps documented channel payload`() {
    val detail = TabloChannelDetailResponse(
      path = "/guide/channels/1000104",
      objectId = 42L,
      channel = TabloChannelInner(
        callSign = "KHQ",
        major = 4,
        minor = 1,
        network = "NBC",
        resolution = "1080i"
      )
    )

    val channel = TabloApiMapper.channelFromDetail("/guide/channels/1000104", detail)

    assertNotNull(channel)
    assertEquals("1000104", channel?.channelId)
    assertEquals("KHQ", channel?.callSign)
    assertEquals("NBC", channel?.network)
    assertEquals("4.1", channel?.displayChannel)
    assertEquals("1080i", channel?.resolution)
  }

  @Test
  fun `airingFromDetail maps documented airing payload with real times`() {
    val detail = TabloAiringDetailResponse(
      path = "/guide/series/episodes/12345",
      episode = TabloEpisodeInner(title = "The Late News"),
      airingDetails = TabloAiringDetailsInner(
        datetime = "2023-01-03T01:00Z",
        duration = 1800L,
        channelPath = "/guide/channels/1000104",
        showTitle = "Nightly News"
      )
    )

    val now = TabloTime.parseIso8601("2023-01-03T01:10Z")!!
    val airing = TabloApiMapper.airingFromDetail("/guide/series/episodes/12345", detail, now)

    assertNotNull(airing)
    assertEquals("1000104", airing?.channelId)
    assertEquals("Nightly News", airing?.title)
    assertEquals("Series", airing?.category)
    assertEquals(1800L, airing?.durationSeconds)
    assertEquals(TabloTime.parseIso8601("2023-01-03T01:00Z"), airing?.startTimeMillis)
    assertTrue(airing?.isLive == true)

    val earlyNow = TabloTime.parseIso8601("2023-01-03T02:00Z")!!
    assertFalse(TabloApiMapper.airingFromDetail("/guide/series/episodes/12345", detail, earlyNow)?.isLive == true)
  }

  @Test
  fun `airingFromDetail infers category from path`() {
    val details = listOf(
      "/guide/movies/airings/99",
      "/guide/sports/events/88",
      "/guide/series/episodes/77",
      "/guide/xyz/44"
    )
    val categories = details.map { path ->
      TabloApiMapper.airingFromDetail(
        path,
        TabloAiringDetailResponse(
          path = path,
          airingDetails = TabloAiringDetailsInner(datetime = "2023-01-03T01:00Z", duration = 1800L, channelPath = "/guide/channels/1")
        ),
        TabloTime.parseIso8601("2023-01-03T01:00Z")!!
      )?.category
    }

    assertEquals("Movies", categories[0])
    assertEquals("Sports", categories[1])
    assertEquals("Series", categories[2])
    assertEquals("Program", categories[3])
  }

  @Test
  fun `multiview layout types have expected slot counts`() {
    assertEquals(1, com.example.model.MultiviewLayoutType.SOLO.maxChannels)
    assertEquals(2, com.example.model.MultiviewLayoutType.HORIZONTAL_2_UP.maxChannels)
    assertEquals(3, com.example.model.MultiviewLayoutType.PRIMARY_1_PLUS_2.maxChannels)
    assertEquals(4, com.example.model.MultiviewLayoutType.PRIMARY_1_PLUS_3.maxChannels)
    assertEquals(4, com.example.model.MultiviewLayoutType.GRID_2X2.maxChannels)
  }
}
