package com.tsutsen.platformplayer

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.Serializer
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.tsutsen.platformplayer.serializers.FlexibleBooleanSerializer
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.stores.FragmentedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Test
import java.time.OffsetDateTime

class RequireMigrationTests {
    /* THESE TESTS SIMPLY EXIST TO WARN THE DEVELOPER THAT THEIR CHANGE WILL CAUSE A MIGRATION FOR ALL USERS */
    private val serializedSettingsString = "{\"home\":{},\"search\":{},\"subscriptions\":{\"subscriptionsFeedStyle\":0,\"subscriptionsBackgroundUpdateInterval\":3},\"playback\":{\"autoRotate\":0},\"downloads\":{},\"browsing\":{},\"casting\":{},\"logging\":{},\"autoUpdate\":{\"check\":1},\"announcementSettings\":{},\"backup\":{},\"payment\":{\"paymentStatus\": \"Paid\"},\"info\":{}}";

    @Test
    fun testSettingsDeserializing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext;
        StateApp.instance.setGlobalContext(context, CoroutineScope(Dispatchers.Main));

        Assert.assertNotNull(Json { ignoreUnknownKeys = true; this.isLenient = true }.decodeFromString<Settings>(serializedSettingsString));
    }
}