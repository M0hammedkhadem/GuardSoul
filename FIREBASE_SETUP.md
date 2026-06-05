# Firebase Production Setup Guide

This document explains how to wire GuardSoul to a real Firebase project before publishing to Google Play.

## Step 1: Create the Firebase project

1. Go to https://console.firebase.google.com
2. Create a new project named `guardsoul-prod`
3. Add an Android app with package name `com.agon.app`
4. Download the `google-services.json` and replace the placeholder at `app/google-services.json`
5. The current placeholder is sufficient for development; replace before release.

## Step 2: Enable Authentication

In the Firebase Console:

1. Go to **Authentication → Sign-in method**
2. Enable **Email/Password**
3. Enable **Google** (provide SHA-1 from your signing key)
4. **Anonymous** is enabled by default

Get the SHA-1 from your upload keystore:

```bash
keytool -list -v -keystore <your-upload-keystore>.jks -alias <key-alias>
```

Add both SHA-1 and SHA-256 to the Firebase Android app config.

## Step 3: Set up Firestore

1. Go to **Firestore Database → Create database → Production mode**
2. Choose region (europe-west3 for EU users, us-central1 for US)
3. Add security rules:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

4. Add the following indexes (auto-created on first query):
   - Collection `users` → field `updatedAt` (Descending)
   - Collection `users` → field `subscription.updatedAt` (Descending)

## Step 4: Enable Crashlytics

1. Go to **Crashlytics → Get started**
2. Build and run the app once
3. Force a test crash: `adb shell am crash com.agon.app`
4. Verify the crash appears in the Firebase dashboard

## Step 5: Enable Analytics

1. Go to **Analytics → Dashboard**
2. Enable Google Analytics for Firebase
3. Enable Google Signals if you want demographic data
4. Define custom events (or rely on the built-in ones in `AnalyticsManager`):
   - `onboarding_step`
   - `paywall_viewed`
   - `subscription_started`
   - `feature_used`
   - `block_triggered`
   - `nsfw_detected`

## Step 6: Cloud Messaging (FCM)

For accountability-partner email alerts (Pro/Premium):

1. Go to **Cloud Messaging → Get started**
2. Upload the FCM server key to your SMTP relay (Mailgun/SendGrid)
3. The current `GuardianFcmService` already registers the device token

## Step 7: Replace the placeholder google-services.json

The current file at `app/google-services.json` contains only `YOUR_PROJECT_NUMBER` placeholders. Replace with the real one downloaded in Step 1 before submitting to Play Console. Otherwise:

- `FirebaseAuth.signInAnonymously()` will fail
- `Firestore` queries will return PERMISSION_DENIED
- `Crashlytics` events won't reach the dashboard
- `Analytics` events won't be sent

## Step 8: Configure In-App Review

The In-App Review API doesn't need Firebase — it talks directly to Google Play Services. Make sure the device has Play Services 19.8.31+.

## Step 9: Configure Play Billing

Before publishing:

1. Go to **Google Play Console → Monetize → Products → In-app products**
2. Create four subscription products:
   - `guardsoul_pro_monthly` — Pro Monthly
   - `guardsoul_pro_yearly` — Pro Yearly
   - `guardsoul_premium_monthly` — Premium Monthly
   - `guardsoul_premium_yearly` — Premium Yearly
3. Set prices per region (or use "Set globally")
4. Add a 7-day free trial to the yearly SKUs (optional)
5. Activate the products

The SKU IDs are wired into `BuildConfig` in `app/build.gradle.kts`. To override per build type, add to `productFlavors`:

```kotlin
flavorDimensions += "billing"
productFlavors {
    create("dev") {
        dimension = "billing"
        buildConfigField("String", "SKU_PRO_MONTHLY", "\"dev_pro_monthly\"")
    }
    create("prod") {
        dimension = "billing"
        buildConfigField("String", "SKU_PRO_MONTHLY", "\"guardsoul_pro_monthly\"")
    }
}
```

## Step 10: Sign the release build

The release build needs an upload keystore:

```bash
keytool -genkey -v -keystore guardsoul-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias guardsoul
```

Then create `keystore.properties` (NOT committed to git):

```
storeFile=../guardsoul-upload.jks
storePassword=YOUR_PASSWORD
keyAlias=guardsoul
keyPassword=YOUR_PASSWORD
```

Then `.\gradlew.bat assembleRelease` will produce a signed APK at `app/build/outputs/apk/release/app-release.apk`.

## Step 11: Submit to Play Console

1. Go to **Release → Production → Create new release**
2. Upload `app-release.apk` (or use the AAB if you generate one: `.\gradlew.bat bundleRelease`)
3. Fill in "What's new" with the 1.0.0 changelog
4. Roll out to **Internal testing** first, then **Closed beta**, then **Production**

## Verification checklist

After deploying:

- [ ] Cold launch on a fresh device reaches the Onboarding screen
- [ ] UMP consent dialog shows in EU/UK on first launch (test with VPN or debug geography)
- [ ] Tapping "Continue as guest" creates an anonymous Firebase Auth user
- [ ] Tapping "Subscribe" on the Pro card opens the Play Billing sheet
- [ ] After subscribing, the tier badge in Account screen reads "PRO"
- [ ] Crashlytics receives a test crash
- [ ] Analytics receives a `paywall_viewed` event when opening Upgrade screen
- [ ] Cloud sync roundtrip works (enable in Account, change a setting on device A, observe on device B)
