# TwinLab Android app

Native Kotlin + Jetpack Compose client for the TwinLab backend:
asset dashboard, per-asset live detail + 3D digital twin, and FCM push alerts
(replacing the old Twilio/WhatsApp channel).

Package `com.omnitex.twinlab` · minSdk 24 · targetSdk 35.

## First-time setup (Arham)

1. **Open `D:\TwinLab_v2\android` in Android Studio.** These source files were
   authored outside the IDE. On first open, Android Studio will:
   - generate the Gradle wrapper JAR + `gradlew`/`gradlew.bat` (this repo only
     ships `gradle/wrapper/gradle-wrapper.properties`),
   - download Gradle 8.9, AGP 8.7.2, and SDK platform 35 if missing,
   - run the first sync.
   If sync fails on "SDK 35 not found", accept the prompt to install it.

2. **Run unit tests** (pure logic — no device needed):
   ```
   gradlew :app:testDebugUnitTest
   ```
   Covers `HealthStatusTest`, `WsMessageParsingTest`, `TwinMappingTest`.

3. **Run the app** on a device/emulator on the **same LAN as the backend**.
   First launch shows the Settings screen — enter `http://<laptop-LAN-IP>:8000`
   (not `localhost`). The backend must be started with
   `uvicorn main:app --host 0.0.0.0 --port 8000` so the phone can reach it.

## Firebase (push notifications — Task 9)

Push is coded but dormant until you add Firebase:

The `firebase-messaging` dependency, the `TwinLabMessagingService`, the manifest
`<service>`, the notification channel and the token-registration call are all
already in place. Only two things are missing:

1. console.firebase.google.com → new project (Spark/free).
2. Add Android app, package `com.omnitex.twinlab` → download
   `google-services.json` → drop it in `android/app/` (gitignored).
3. Uncomment the single `com.google.gms.google-services` line in **both**
   `build.gradle.kts` (root) and `app/build.gradle.kts`. Sync.
4. Project Settings → Service Accounts → generate a private key →
   save on the backend machine → set `FCM_CREDENTIALS_FILE` in `backend/.env`.

Until step 3 is done the app builds and runs fine — FCM is simply inert
(`FirebaseMessaging.getInstance()` calls are wrapped in `runCatching`).

## Layout

```
app/src/main/java/com/omnitex/twinlab/
  MainActivity.kt          nav host + Settings-URL gate + FCM deep-link
  TwinLabApp.kt            Application + AppContainer (manual DI)
  data/                    Models, TwinLabApi (Ktor REST), DeviceSocket (Ktor WS),
                           SettingsRepository (DataStore)
  domain/                  Health.healthStatus(), TwinMapping.stateFrom()
  push/                    TwinLabMessagingService, Notifications
  ui/                      Nav, settings/, assets/, detail/ (+ TwinView), alerts/, common/
app/src/main/assets/twin_rig.glb   3D model for the digital twin
app/src/test/                       JVM unit tests
```
