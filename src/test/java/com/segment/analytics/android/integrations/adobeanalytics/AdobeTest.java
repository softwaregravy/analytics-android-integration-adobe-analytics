package com.segment.analytics.android.integrations.adobeanalytics;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.support.annotation.Nullable;
import com.adobe.mobile.Analytics;
import com.adobe.mobile.Config;
import com.adobe.primetime.va.simple.MediaHeartbeat;
import com.adobe.primetime.va.simple.MediaHeartbeat.MediaHeartbeatDelegate;
import com.adobe.primetime.va.simple.MediaHeartbeatConfig;
import com.adobe.primetime.va.simple.MediaObject;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.android.integrations.adobeanalytics.AdobeIntegration.PlaybackDelegate;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.ScreenPayload;
import com.segment.analytics.integrations.TrackPayload;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.matcher.AssertionMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import static com.segment.analytics.Analytics.LogLevel.NONE;
import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(RobolectricTestRunner.class)
@PrepareForTest({Analytics.class, Config.class, MediaHeartbeat.class})
@org.robolectric.annotation.Config(constants = BuildConfig.class)
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*", "org.json.*"})
public class AdobeTest {

  @Rule public PowerMockRule rule = new PowerMockRule();
  @Mock private MediaHeartbeat heartbeat;
  @Mock private com.segment.analytics.Analytics analytics;
  @Mock private Application context;
  private AdobeIntegration integration;
  private AdobeIntegration.HeartbeatFactory mockHeartbeatFactory = new AdobeIntegration.HeartbeatFactory() {
    @Override
    public MediaHeartbeat get(MediaHeartbeatDelegate delegate, MediaHeartbeatConfig config) {
      return heartbeat;
    }
  };

  @Before
  public void setUp() {
    initMocks(this);
    PowerMockito.mockStatic(Config.class);
    PowerMockito.mockStatic(Analytics.class);
    when(analytics.getApplication()).thenReturn(context);
    integration = new AdobeIntegration(new ValueMap()
        .putValue("heartbeatTrackingServerUrl", "https://www.heartbeatTrackingServerURL.com/"),
        analytics, Logger.with(NONE), mockHeartbeatFactory);
  }

  @Test
  public void factory() {
    assertThat(AdobeIntegration.FACTORY.key()).isEqualTo("Adobe Analytics");
  }

  @Test
  public void initialize() {
    integration = new AdobeIntegration(new ValueMap()
        .putValue("eventsV2", new HashMap<String, Object>())
        .putValue("contextValues", new HashMap<String, Object>())
        .putValue("productIdentifier", "id")
        .putValue("adobeVerboseLogging", true),
        analytics,
        Logger.with(VERBOSE),
        mockHeartbeatFactory);

    verifyStatic();
    Config.setDebugLogging(true);

    assertThat(integration.eventsV2).isEqualTo(new HashMap<String, Object>());
    assertThat(integration.contextValues).isEqualTo(new HashMap<String, Object>());
    assertThat(integration.productIdentifier).isEqualTo("id");
  }

  @Test
  public void initializeWithDefaultArguments() {
    // all default arguments have not yet been defined
  }

  @Test
  public void activityCreate() {
    Activity activity = mock(Activity.class);
    Bundle savedInstanceState = mock(Bundle.class);
    integration.onActivityCreated(activity, savedInstanceState);

    verifyStatic();
    Config.setContext(activity.getApplicationContext());
  }

  @Test
  public void activityPause() {
    Activity activity = mock(Activity.class);
    integration.onActivityPaused(activity);

    verifyStatic();
    Config.pauseCollectingLifecycleData();
  }

  @Test
  public void activityResume() {
    Activity activity = mock(Activity.class);
    integration.onActivityResumed(activity);

    verifyStatic();
    Config.collectLifecycleData(activity);
  }

  @Test
  public void track() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Testing Event")
        .build()
    );

    verifyStatic();
    Analytics.trackAction("Adobe Testing Event", null);
  }

  @Test
  public void trackWithContextValues() {
    integration.eventsV2 = new HashMap<>();
    integration.eventsV2.put("Testing Event", "Adobe Testing Event");
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("testing", "myapp.testing.Testing");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Testing Event")
        .properties(new Properties()
            .putValue("testing", "testing value"))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    verifyStatic();
    Analytics.trackAction("Adobe Testing Event", contextData);
  }

  @Test
  public void trackOrderCompleted() {
    integration.productIdentifier = "name";
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("testing", "myapp.testing");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Order Completed")
        .properties(new Properties()
            .putOrderId("A5744855555")
            .putValue("testing", "test!")
            .putProducts(new Product("123", "ABC", 10.0)
                .putName("shoes")
                .putValue("category", "athletic")
                .putValue("quantity", 2)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing", "test!");
    contextData.put("purchaseid", "A5744855555");
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "purchase");
    verifyStatic();
    Analytics.trackAction("purchase", contextData);
  }

  @Test
  public void trackProductAdded() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Added")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "scAdd");
    verifyStatic();
    Analytics.trackAction("scAdd", contextData);
  }

  @Test
  public void trackProductRemoved() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Removed")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "scRemove");
    verifyStatic();
    Analytics.trackAction("scRemove", contextData);
  }

  @Test
  public void trackProductViewed() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Viewed")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0");
    contextData.put("&&events", "prodView");
    verifyStatic();
    Analytics.trackAction("prodView", contextData);
  }

  @Test
  public void trackEcommerceEventWithProductId() {
    integration.productIdentifier = "id";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Viewed")
        .properties(new Properties()
            .putValue("productId", "XYZ")
            .putSku("ABC")
            .putPrice(10.0)
            .putName("shoes")
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;XYZ;2;20.0");
    contextData.put("&&events", "prodView");
    verifyStatic();
    Analytics.trackAction("prodView", contextData);
  }

  @Test
  public void trackCheckoutStarted() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Checkout Started")
        .properties(new Properties()
            .putProducts(new Product("123", "ABC", 10.0)
                    .putName("shoes")
                    .putValue("category", "athletic")
                    .putValue("quantity", 2),
                new Product("456", "DEF", 20.0)
                    .putName("jeans")
                    .putValue("category", "casual")
                    .putValue("quantity", 1)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0,casual;jeans;1;20.0");
    contextData.put("&&events", "scCheckout");
    verifyStatic();
    Analytics.trackAction("scCheckout", contextData);
  }

  @Test
  public void trackCartViewed() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Cart Viewed")
        .properties(new Properties()
            .putProducts(new Product("123", "ABC", 10.0)
                    .putName("shoes")
                    .putValue("category", "athletic")
                    .putValue("quantity", 2),
                new Product("456", "DEF", 20.0)
                    .putName("jeans")
                    .putValue("category", "casual")
                    .putValue("quantity", 1)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", "athletic;shoes;2;20.0,casual;jeans;1;20.0");
    contextData.put("&&events", "scView");
    verifyStatic();
    Analytics.trackAction("scView", contextData);
  }

  @Test
  public void trackEcommerceWhenProductNameIsNotSet() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Removed")
        .properties(new Properties()
            .putSku("ABC")
            .putPrice(10.0)
            .putCategory("athletic")
            .putValue("quantity", 2))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&events", "scRemove");

    verifyStatic();
    Analytics.trackAction("scRemove", contextData);
  }

  @Test
  public void trackEcommerceEventWithNoProperties() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Product Added")
        .properties(new Properties())
        .build()
    );

    verifyStatic();
    Analytics.trackAction("scAdd", null);
  }

  @Test
  public void trackPurchaseEventToTestDefaults() {
    integration.productIdentifier = "sku";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Order Completed")
        .properties(new Properties()
            .putProducts(new Product("123", "ABC", 0)))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&products", ";ABC;1;0.0");
    contextData.put("&&events", "purchase");
    verifyStatic();
    Analytics.trackAction("purchase", contextData);
  }

  @Test
  public void trackPurchaseWithoutProducts() {
    integration.productIdentifier = "name";

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Order Completed")
        .properties(new Properties()
            .putOrderId("123456"))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("&&events", "purchase");
    contextData.put("purchaseid", "123456");
    verifyStatic();
    Analytics.trackAction("purchase", contextData);
  }

  @Test
  public void videoPlaybackDelegatePlay() throws Exception {
    PlaybackDelegate playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    Thread.sleep(2000);
    assertThat(playbackDelegate.getCurrentPlaybackTime()).isEqualTo(2.0);
  }

  @Test
  public void videoPlaybackDelegatePaused() throws Exception {
    PlaybackDelegate playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    playbackDelegate.pausePlayhead();
    Double firstPlayheadPosition = playbackDelegate.getCurrentPlaybackTime();
    Thread.sleep(2000);
    assertThat(playbackDelegate.getCurrentPlaybackTime())
        .isEqualTo(firstPlayheadPosition);
  }

  @Test
  public void videoPlaybackDelegatePlayAndPause() throws Exception {
    PlaybackDelegate playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    playbackDelegate.pausePlayhead();
    Thread.sleep(1000);
    playbackDelegate.unPausePlayhead();
    Thread.sleep(3000);
    assertThat(playbackDelegate.getCurrentPlaybackTime()).isEqualTo(3.0);
  }

  @Test
  public void trackVideoPlaybackStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("random metadata", "adobe.random");

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Playback Started")
        .properties(new Properties()
            .putValue("title", "You Win or You Die")
            .putValue("contentAssetId", "123")
            .putValue("totalLength", 100D)
            .putValue("assetId", "123")
            .putValue("program", "Game of Thrones")
            .putValue("season", "1")
            .putValue("episode", "7")
            .putValue("genre", "fantasy")
            .putValue("channel", "HBO")
            .putValue("airdate", "2011")
            .putValue("livestream", false)
            .putValue("random metadata", "something super random"))
        .build()
    );

    Map<String, String> standardVideoMetadata = new HashMap<>();
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ASSET_ID, "123");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SHOW, "Game of Thrones");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SEASON, "1");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.EPISODE, "7");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.GENRE, "fantasy");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.NETWORK, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE, "2011");
    standardVideoMetadata
        .put(MediaHeartbeat.VideoMetadataKeys.STREAM_FORMAT, MediaHeartbeat.StreamType.VOD);

    Map<String, String> videoMetadata = new HashMap<>();
    videoMetadata.put("adobe.random", "something super random");

    // create a media object; values can be null
    MediaObject mediaInfo = MediaHeartbeat.createMediaObject(
        "You Win or You Die",
        "123",
        100D,
        MediaHeartbeat.StreamType.VOD
    );

    mediaInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardVideoMetadata, standardVideoMetadata);

    verify(heartbeat).trackSessionStart(isEqualToComparingFieldByFieldRecursively(mediaInfo),
        eq(videoMetadata));
    assertThat(integration.playbackDelegate).isNotNull();
  }

  @Test
  public void trackVideoPlaybackPaused() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Paused");
    assertThat(integration.playbackDelegate.isPaused).isTrue();
    verify(heartbeat).trackPause();
  }

  @Test
  public void trackVideoPlaybackResumed() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Resumed");
    assertThat(integration.playbackDelegate.isPaused).isFalse();
    verify(heartbeat).trackPlay();
  }

  @Test
  public void trackVideoContentStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("title", "adobe.title");

    newVideoSession();

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Content Started")
        .properties(new Properties()
            .putValue("title", "You Win or You Die")
            .putValue("contentAssetId", "123")
            .putValue("totalLength", 100D)
            .putValue("startTime", 10D)
            .putValue("indexPosition", 1L)
            .putValue("position", 35)
            .putValue("season", "1")
            .putValue("program", "Game of Thrones")
            .putValue("episode", "7")
            .putValue("genre", "fantasy")
            .putValue("channel", "HBO")
            .putValue("airdate", "2011")
            .putValue("publisher", "HBO")
            .putValue("rating", "MA"))
        .build()
    );

    Map<String, String> videoMetadata = new HashMap<>();
    videoMetadata.put("adobe.title", "You Win or You Die");

    MediaObject mediaChapter = MediaHeartbeat.createChapterObject(
        "You Win or You Die",
        1L,
        100D,
        10D
    );

    Map <String, String> standardVideoMetadata = new HashMap<>();
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ASSET_ID, "123");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SHOW, "Game of Thrones");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.SEASON, "1");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.EPISODE, "7");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.GENRE, "fantasy");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.NETWORK, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.FIRST_AIR_DATE, "2011");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.ORIGINATOR, "HBO");
    standardVideoMetadata.put(MediaHeartbeat.VideoMetadataKeys.RATING, "MA");

    mediaChapter.setValue(MediaHeartbeat.MediaObjectKey.StandardVideoMetadata,
        standardVideoMetadata);

    assertThat(integration.playbackDelegate.getCurrentPlaybackTime()).isEqualTo(35.0);
    verify(heartbeat).trackPlay();
    verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.ChapterStart),
        isEqualToComparingFieldByFieldRecursively(mediaChapter),
        eq(videoMetadata));
  }

  @Test
  public void trackVideoContentComplete() {
    newVideoSession();
    heartbeatTestFixture("Video Content Completed");
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.ChapterComplete, null, null);
    verify(heartbeat).trackComplete();
  }

  @Test
  public void trackVideoPlaybackComplete() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Completed");
    verify(heartbeat).trackSessionEnd();
  }

  @Test
  public void trackVideoBufferStarted() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Buffer Started");
    assertThat(integration.playbackDelegate.isPaused).isTrue();
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.BufferStart, null, null);
  }

  @Test
  public void trackVideoBufferComplete() {
    newVideoSession();
    heartbeatTestFixture("Video Playback Buffer Completed");
    assertThat(integration.playbackDelegate.isPaused).isFalse();
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.BufferComplete, null, null);
  }

  @Test
  public void trackVideoSeekStarted() {
    newVideoSession();
    heartbeatSeekFixture("Video Playback Seek Started", null);
    assertThat(integration.playbackDelegate.isPaused).isTrue();
    assertThat(integration.playbackDelegate.getCurrentPlaybackTime()).isZero();
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.SeekStart, null, null);
  }

  @Test
  public void trackVideoSeekComplete() {
    newVideoSession();
    double first = integration.playbackDelegate.getCurrentPlaybackTime();
    heartbeatSeekFixture("Video Playback Seek Completed", 50L);
    assertThat(integration.playbackDelegate.isPaused).isFalse();
    assertThat(integration.playbackDelegate.getCurrentPlaybackTime()).isEqualTo(first + 50);
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.SeekComplete, null, null);
  }

  @Test
  public void trackVideoAdBreakStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("contextValue", "adobe.context.value");

    newVideoSession();

    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Ad Break Started")
        .properties(new Properties()
            .putValue("title",
                "Car Commercial") // Should this be pre-roll, mid-roll or post-roll instead?
            .putValue("startTime", 10D)
            .putValue("indexPosition", 1L)
            .putValue("contextValue", "value"))
        .build()
    );

    MediaObject mediaAdBreakInfo = MediaHeartbeat.createAdBreakObject(
        "Car Commercial",
        1L,
        10D
    );

    Map<String, String> adBreakMetadata = new HashMap<>();
    adBreakMetadata.put("adobe.context.value", "value");
    
    verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.AdBreakStart),
        isEqualToComparingFieldByFieldRecursively(mediaAdBreakInfo), eq(adBreakMetadata));
  }

  @Test
  public void trackVideoAdBreakCompleted() {
    newVideoSession();
    heartbeatTestFixture("Video Ad Break Completed");
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdBreakComplete, null, null);
  }

  @Test
  public void trackVideoAdStarted() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("title", "adobe.title");

    newVideoSession();
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Ad Started")
        .properties(new Properties()
            .putValue("title", "Car Commercial")
            .putValue("assetId", "123")
            .putValue("totalLength", 10D)
            .putValue("indexPosition", 1L)
            .putValue("publisher", "Lexus"))
        .build()
    );

    MediaObject mediaAdInfo = MediaHeartbeat.createAdObject(
        "Car Commercial",
        "123",
        1L,
        10D
    );

    Map<String, String> adMetadata = new HashMap<>();
    adMetadata.put("adobe.title", "Car Commercial");

    Map<String, String> standardAdMetadata = new HashMap<>();
    standardAdMetadata.put(MediaHeartbeat.AdMetadataKeys.ADVERTISER, "Lexus");
    mediaAdInfo.setValue(MediaHeartbeat.MediaObjectKey.StandardAdMetadata, standardAdMetadata);

    verify(heartbeat).trackEvent(eq(MediaHeartbeat.Event.AdStart),
        isEqualToComparingFieldByFieldRecursively(mediaAdInfo), eq(adMetadata));
  }

  @Test
  public void trackVideoAdSkipped() {
    newVideoSession();
    heartbeatTestFixture("Video Ad Skipped");
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdSkip, null, null);
  }

  @Test
  public void trackVideoAdCompleted() {
    newVideoSession();
    heartbeatTestFixture("Video Ad Completed");
    verify(heartbeat).trackEvent(MediaHeartbeat.Event.AdComplete, null, null);
  }

  @Test
  public void trackVideoPlaybackInterrupted() throws Exception {
    integration.playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    heartbeatTestFixture("Video Playback Interrupted");
    Double first = integration.playbackDelegate.getCurrentPlaybackTime();
    Thread.sleep(2000L);
    assertThat(integration.playbackDelegate.getCurrentPlaybackTime()).isEqualTo(first);
  }

  @Test
  public void trackVideoQualityUpdated() {
    integration.playbackDelegate = new AdobeIntegration.PlaybackDelegate();
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Quality Updated")
        .properties(new Properties()
            .putValue("bitrate", 12000)
            .putValue("startupTime", 1)
            .putValue("fps", 50)
            .putValue("droppedFrames", 1))
        .build()
    );

    MediaObject expectedMediaObject = MediaHeartbeat.createQoSObject(
        12000L,
        1D,
        50D,
        1L
    );

    assertThat(integration.playbackDelegate.qosData)
        .isEqualToComparingFieldByField(expectedMediaObject);
  }

  @Test
  public void identify() {
    integration.identify(new IdentifyPayload.Builder()
        .userId("123")
        .traits(new Traits())
        .build());

    verifyStatic();
    Config.setUserIdentifier("123");
  }

  @Test
  public void identifyWithNoUserId() {
    integration.identify(new IdentifyPayload.Builder()
        .userId("123")
        .traits(new Traits())
        .build());

    verifyStatic(Mockito.times(0));
    Config.setUserIdentifier(null);
  }

  @Test
  public void screen() {
    integration.screen(new ScreenPayload.Builder()
        .userId("123")
        .name("Viewed a Screen")
        .build()
    );

    verifyStatic();
    Analytics.trackState("Viewed a Screen", null);
  }

  @Test
  public void screenWithContextValues() {
    integration.contextValues = new HashMap<>();
    integration.contextValues.put("testing", "myapp.testing.Testing");

    integration.screen(new ScreenPayload.Builder()
        .userId("123")
        .name("Viewed a Screen")
        .properties(new Properties()
            .putValue("testing", "testing value"))
        .build()
    );

    Map<String, Object> contextData = new HashMap<>();
    contextData.put("myapp.testing.Testing", "testing value");
    verifyStatic();
    Analytics.trackState("Viewed a Screen", contextData);
  }

  @Test
  public void group() {
  }

  @Test
  public void flush() {
    integration.flush();
    verifyStatic();
    Analytics.sendQueuedHits();
  }

  @Test
  public void reset() {
    integration.reset();
    verifyStatic();
    Config.setUserIdentifier(null);
  }

  private void newVideoSession() {
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event("Video Playback Started")
        .properties(new Properties()
            .putValue("title", "You Win or You Die")
            .putValue("sessionId", "123")
            .putValue("totalLength", 100D)
            .putValue("assetId", "123")
            .putValue("program", "Game of Thrones")
            .putValue("season", "1")
            .putValue("episode", "7")
            .putValue("genre", "fantasy")
            .putValue("channel", "HBO")
            .putValue("airdate", "2011")
            .putValue("livestream", false))
        .build()
    );
  }

  private void heartbeatTestFixture(String eventName) {
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event(eventName)
        .build()
    );
  }

  private void heartbeatSeekFixture(String eventName, @Nullable Long seekPosition) {
    integration.track(new TrackPayload.Builder()
        .userId("123")
        .event(eventName)
        .properties(new Properties()
            .putValue("seekPosition", (seekPosition != null ? seekPosition : 0))
        )
        .build()
    );
  }

  private static <T> T isEqualToComparingFieldByFieldRecursively(final T expected) {
    return argThat(new AssertionMatcher<T>() {
      @Override
      public void assertion(T actual) throws AssertionError {
        assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
      }
    });
  }
}
