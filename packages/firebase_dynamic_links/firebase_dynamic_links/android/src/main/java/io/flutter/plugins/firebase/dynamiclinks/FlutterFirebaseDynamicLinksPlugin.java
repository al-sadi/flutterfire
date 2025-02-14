// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.firebase.dynamiclinks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.PluginRegistry.NewIntentListener;
import io.flutter.plugins.firebase.core.FlutterFirebasePlugin;
import io.flutter.plugins.firebase.core.FlutterFirebasePluginRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class FlutterFirebaseDynamicLinksPlugin
    implements FlutterFirebasePlugin,
        FlutterPlugin,
        ActivityAware,
        MethodCallHandler,
        NewIntentListener {
  private final AtomicReference<Activity> activity = new AtomicReference<>(null);

  private MethodChannel channel;
  @Nullable private BinaryMessenger messenger;

  private static final String METHOD_CHANNEL_NAME = "plugins.flutter.io/firebase_dynamic_links";

  private void initInstance(BinaryMessenger messenger) {
    channel = new MethodChannel(messenger, METHOD_CHANNEL_NAME);
    channel.setMethodCallHandler(this);
    FlutterFirebasePluginRegistry.registerPlugin(METHOD_CHANNEL_NAME, this);

    this.messenger = messenger;
  }

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    initInstance(binding.getBinaryMessenger());
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    channel = null;
    messenger = null;
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activity.set(binding.getActivity());
    binding.addOnNewIntentListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    detachToActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    activity.set(binding.getActivity());
    binding.addOnNewIntentListener(this);
  }

  private void detachToActivity() {
    activity.set(null);
  }

  @Override
  public void onDetachedFromActivity() {
    detachToActivity();
  }

  static FirebaseDynamicLinks getDynamicLinkInstance(@Nullable Map<String, Object> arguments) {
    if (arguments != null) {
      String appName = (String) arguments.get(Constants.APP_NAME);
      if (appName != null) {
        FirebaseApp app = FirebaseApp.getInstance(appName);
        return FirebaseDynamicLinks.getInstance(app);
      }
    }

    return FirebaseDynamicLinks.getInstance();
  }

  @Override
  public boolean onNewIntent(Intent intent) {
    getDynamicLinkInstance(null)
        .getDynamicLink(intent)
        .addOnSuccessListener(
            pendingDynamicLinkData -> {
              Map<String, Object> dynamicLink =
                  Utils.getMapFromPendingDynamicLinkData(pendingDynamicLinkData);
              if (dynamicLink != null) {
                channel.invokeMethod("FirebaseDynamicLink#onLinkSuccess", dynamicLink);
              }
            })
        .addOnFailureListener(
            exception ->
                channel.invokeMethod(
                    "FirebaseDynamicLink#onLinkError", Utils.getExceptionDetails(exception)));
    return false;
  }

  @Override
  public void onMethodCall(MethodCall call, @NonNull final MethodChannel.Result result) {
    Task<?> methodCallTask;
    FirebaseDynamicLinks dynamicLinks = getDynamicLinkInstance(call.arguments());

    switch (call.method) {
      case "FirebaseDynamicLinks#buildLink":
        String url = buildLink(call.arguments());
        result.success(url);
        return;
      case "FirebaseDynamicLinks#buildShortLink":
        DynamicLink.Builder urlBuilder = setupParameters(call.arguments());
        methodCallTask = buildShortLink(urlBuilder, call.arguments());
        break;
      case "FirebaseDynamicLinks#getDynamicLink":
      case "FirebaseDynamicLinks#getInitialLink":
        methodCallTask = getDynamicLink(dynamicLinks, call.argument("url"));
        break;
      default:
        result.notImplemented();
        return;
    }

    methodCallTask.addOnCompleteListener(
        task -> {
          if (task.isSuccessful()) {
            result.success(task.getResult());
          } else {
            Exception exception = task.getException();
            result.error(
                Constants.DEFAULT_ERROR_CODE,
                exception != null ? exception.getMessage() : null,
                io.flutter.plugins.firebase.dynamiclinks.Utils.getExceptionDetails(exception));
          }
        });
  }

  private String buildLink(Map<String, Object> arguments) {
    DynamicLink.Builder urlBuilder = setupParameters(arguments);

    return urlBuilder.buildDynamicLink().getUri().toString();
  }

  private Task<Map<String, Object>> buildShortLink(
      DynamicLink.Builder urlBuilder, @Nullable Map<String, Object> arguments) {
    return Tasks.call(
        cachedThreadPool,
        () -> {
          Integer suffix = 1;
          Integer shortDynamicLinkPathLength = (Integer) arguments.get("shortLinkType");
          if (shortDynamicLinkPathLength != null) {
            switch (shortDynamicLinkPathLength) {
              case 0:
                suffix = ShortDynamicLink.Suffix.UNGUESSABLE;
                break;
              case 1:
                suffix = ShortDynamicLink.Suffix.SHORT;
                break;
              default:
                break;
            }
          }

          Map<String, Object> result = new HashMap<>();
          ShortDynamicLink shortLink;
          if (suffix != null) {
            shortLink = Tasks.await(urlBuilder.buildShortDynamicLink(suffix));
          } else {
            shortLink = Tasks.await(urlBuilder.buildShortDynamicLink());
          }

          List<String> warnings = new ArrayList<>();

          for (ShortDynamicLink.Warning warning : shortLink.getWarnings()) {
            warnings.add(warning.getMessage());
          }

          result.put("url", shortLink.getShortLink().toString());
          result.put("warnings", warnings);
          result.put("previewLink", shortLink.getPreviewLink().toString());

          return result;
        });
  }

  private Task<Map<String, Object>> getDynamicLink(
      FirebaseDynamicLinks dynamicLinks, @Nullable String url) {
    return Tasks.call(
        cachedThreadPool,
        () -> {
          PendingDynamicLinkData pendingDynamicLink;

          if (url != null) {
            pendingDynamicLink = Tasks.await(dynamicLinks.getDynamicLink(Uri.parse(url)));
          } else {
            // If there's no activity or initial Intent, then there's no initial dynamic link.
            if (activity.get() == null || activity.get().getIntent() == null) {
              return null;
            }
            pendingDynamicLink =
                Tasks.await(dynamicLinks.getDynamicLink(activity.get().getIntent()));
          }

          return Utils.getMapFromPendingDynamicLinkData(pendingDynamicLink);
        });
  }

  private DynamicLink.Builder setupParameters(Map<String, Object> arguments) {
    DynamicLink.Builder dynamicLinkBuilder = getDynamicLinkInstance(arguments).createDynamicLink();

    String uriPrefix = (String) arguments.get("uriPrefix");
    String link = (String) arguments.get("link");

    dynamicLinkBuilder.setDomainUriPrefix(uriPrefix);
    dynamicLinkBuilder.setLink(Uri.parse(link));

    Map<String, Object> androidParameters =
        (Map<String, Object>) arguments.get("androidParameters");
    if (androidParameters != null) {
      String packageName = valueFor("packageName", androidParameters);
      String fallbackUrl = valueFor("fallbackUrl", androidParameters);
      Integer minimumVersion = valueFor("minimumVersion", androidParameters);

      DynamicLink.AndroidParameters.Builder builder =
          new DynamicLink.AndroidParameters.Builder(packageName);

      if (fallbackUrl != null) builder.setFallbackUrl(Uri.parse(fallbackUrl));
      if (minimumVersion != null) builder.setMinimumVersion(minimumVersion);

      dynamicLinkBuilder.setAndroidParameters(builder.build());
    }

    Map<String, Object> googleAnalyticsParameters =
        (Map<String, Object>) arguments.get("googleAnalyticsParameters");
    if (googleAnalyticsParameters != null) {
      String campaign = valueFor("campaign", googleAnalyticsParameters);
      String content = valueFor("content", googleAnalyticsParameters);
      String medium = valueFor("medium", googleAnalyticsParameters);
      String source = valueFor("source", googleAnalyticsParameters);
      String term = valueFor("term", googleAnalyticsParameters);

      DynamicLink.GoogleAnalyticsParameters.Builder builder =
          new DynamicLink.GoogleAnalyticsParameters.Builder();

      if (campaign != null) builder.setCampaign(campaign);
      if (content != null) builder.setContent(content);
      if (medium != null) builder.setMedium(medium);
      if (source != null) builder.setSource(source);
      if (term != null) builder.setTerm(term);

      dynamicLinkBuilder.setGoogleAnalyticsParameters(builder.build());
    }

    Map<String, Object> iosParameters = (Map<String, Object>) arguments.get("iosParameters");
    if (iosParameters != null) {
      String bundleId = valueFor("bundleId", iosParameters);
      String appStoreId = valueFor("appStoreId", iosParameters);
      String customScheme = valueFor("customScheme", iosParameters);
      String fallbackUrl = valueFor("fallbackUrl", iosParameters);
      String ipadBundleId = valueFor("ipadBundleId", iosParameters);
      String ipadFallbackUrl = valueFor("ipadFallbackUrl", iosParameters);
      String minimumVersion = valueFor("minimumVersion", iosParameters);

      DynamicLink.IosParameters.Builder builder = new DynamicLink.IosParameters.Builder(bundleId);

      if (appStoreId != null) builder.setAppStoreId(appStoreId);
      if (customScheme != null) builder.setCustomScheme(customScheme);
      if (fallbackUrl != null) builder.setFallbackUrl(Uri.parse(fallbackUrl));
      if (ipadBundleId != null) builder.setIpadBundleId(ipadBundleId);
      if (ipadFallbackUrl != null) builder.setIpadFallbackUrl(Uri.parse(ipadFallbackUrl));
      if (minimumVersion != null) builder.setMinimumVersion(minimumVersion);

      dynamicLinkBuilder.setIosParameters(builder.build());
    }

    Map<String, Object> itunesConnectAnalyticsParameters =
        (Map<String, Object>) arguments.get("itunesConnectAnalyticsParameters");
    if (itunesConnectAnalyticsParameters != null) {
      String affiliateToken = valueFor("affiliateToken", itunesConnectAnalyticsParameters);
      String campaignToken = valueFor("campaignToken", itunesConnectAnalyticsParameters);
      String providerToken = valueFor("providerToken", itunesConnectAnalyticsParameters);

      DynamicLink.ItunesConnectAnalyticsParameters.Builder builder =
          new DynamicLink.ItunesConnectAnalyticsParameters.Builder();

      if (affiliateToken != null) builder.setAffiliateToken(affiliateToken);
      if (campaignToken != null) builder.setCampaignToken(campaignToken);
      if (providerToken != null) builder.setProviderToken(providerToken);

      dynamicLinkBuilder.setItunesConnectAnalyticsParameters(builder.build());
    }

    Map<String, Object> navigationInfoParameters =
        (Map<String, Object>) arguments.get("navigationInfoParameters");
    if (navigationInfoParameters != null) {
      Boolean forcedRedirectEnabled = valueFor("forcedRedirectEnabled", navigationInfoParameters);

      DynamicLink.NavigationInfoParameters.Builder builder =
          new DynamicLink.NavigationInfoParameters.Builder();

      if (forcedRedirectEnabled != null) builder.setForcedRedirectEnabled(forcedRedirectEnabled);

      dynamicLinkBuilder.setNavigationInfoParameters(builder.build());
    }

    Map<String, Object> socialMetaTagParameters =
        (Map<String, Object>) arguments.get("socialMetaTagParameters");
    if (socialMetaTagParameters != null) {
      String description = valueFor("description", socialMetaTagParameters);
      String imageUrl = valueFor("imageUrl", socialMetaTagParameters);
      String title = valueFor("title", socialMetaTagParameters);

      DynamicLink.SocialMetaTagParameters.Builder builder =
          new DynamicLink.SocialMetaTagParameters.Builder();

      if (description != null) builder.setDescription(description);
      if (imageUrl != null) builder.setImageUrl(Uri.parse(imageUrl));
      if (title != null) builder.setTitle(title);

      dynamicLinkBuilder.setSocialMetaTagParameters(builder.build());
    }

    return dynamicLinkBuilder;
  }

  private static <T> T valueFor(String key, Map<String, Object> map) {
    @SuppressWarnings("unchecked")
    T result = (T) map.get(key);
    return result;
  }

  @Override
  public Task<Map<String, Object>> getPluginConstantsForFirebaseApp(FirebaseApp firebaseApp) {
    return Tasks.call(cachedThreadPool, () -> null);
  }

  @Override
  public Task<Void> didReinitializeFirebaseCore() {
    return Tasks.call(
        cachedThreadPool,
        () -> {
          return null;
        });
  }
}
