# Phase 14 — Twilio removal + TwinLab Android app (v1)

## Goal
Retire Twilio/WhatsApp as the alert transport and make TwinLab's own **native
Android app** the buyer surface: asset dashboard, per-asset live detail + digital
twin, and push-notification alerts via Firebase Cloud Messaging. Backend keeps its
alert *engine* unchanged — only the transport swaps (`whatsapp.send_alert` →
`push.send_alert`, FCM broadcast to every registered device token).

## Structure & steps

**Backend (`backend/`, no test suite — verify with `curl` / `Invoke-RestMethod`):**
1. Delete `whatsapp.py`; strip 6 `twilio_*`/`alert_whatsapp_to` fields from
   `config.py`, add `fcm_credentials_file`. `alerts.py` `_make_alert`:
   `whatsapp_sent` → `push_sent`. `main.py` `_persist_alert`: drop the WhatsApp
   executor block. `requirements.txt` twilio → `firebase-admin`. `.env` cleaned.
   `AlertsPanel.jsx`: "Sent to: …" → "📲 Pushed" badge.
2. `GET /alerts?limit=&since=` — global alert feed (inline in `main.py`).
3. `routers/push.py` — `POST /push/register` + `DELETE /push/register/{token}`,
   `push_tokens` collection (upsert, deduped on token).
4. `push.py` — `send_alert(alert, device, tokens) -> {"sent", "invalid"}`,
   lazy `firebase_admin` init, `messaging.send_each`, `_format_body` ported from
   `whatsapp.py` (returns EN + Roman-Urdu). Wired into `_persist_alert`: fetch
   tokens → thread executor → set `push_sent` → prune invalid tokens.

**Android (`android/`, single Gradle module, package `com.omnitex.twinlab`,
minSdk 24 / targetSdk 35, JVM unit tests only):**
5. Scaffold — Gradle KTS, Compose (Material 3), Ktor client, kotlinx.serialization,
   DataStore, nav skeleton, `TwinLabApp` + manual-DI `AppContainer`.
6. `data/Models.kt`, `SettingsRepository` (DataStore), Settings screen +
   backend-URL gate (`/health` probe, persisted; app starts here until set).
7. `TwinLabApi` (Ktor REST), `domain/Health.healthStatus()` (+ `HealthStatusTest`),
   asset list (poll `last-known` every 5 s, grouped by plant/location, status dot)
   + global Alerts screen (`GET /alerts`).
8. `DeviceSocket` (Ktor WS to `/ws/{id}`, 3 s reconnect) + `parseWsMessage()`
   (+ `WsMessageParsingTest`), asset detail (gauges, sparklines, live health,
   "SIGNAL LOST" after 15 s, recent alerts).
9. FCM — `TwinLabMessagingService`, notification channel + deep-link
   (tap → asset detail), token registered on start + `onNewToken`,
   `POST_NOTIFICATIONS` prompt on API 33+.
10. `domain/TwinMapping.stateFrom()` → `TwinState` (+ `TwinMappingTest`);
    `ui/detail/TwinView` renders the twin (body colour ← temperature, rotor spin
    ← load/vibration, jitter ← vibration, health ring, stale overlay).
11. This doc + `CLAUDE.md` / `limitations.md` / `README.md` / Obsidian identity.

## Start commands

```powershell
# backend must bind all interfaces so the phone can reach it
docker compose up -d
.venv\Scripts\python ingestion.py          # separate terminal
.venv\Scripts\python simulator.py          # separate terminal
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000
cd frontend; npm run dev                    # dashboard unchanged

# Android
#  open D:\TwinLab_v2\android in Android Studio → let it generate the Gradle
#  wrapper + sync → Run on a device on the SAME LAN.
#  First launch: enter http://<laptop-LAN-IP>:8000 in the Settings screen.
gradlew :app:testDebugUnitTest              # HealthStatus / WsMessageParsing / TwinMapping
```

## Expected outcome
- No `twilio`/`whatsapp` reference in `backend/*.py`, `requirements.txt`.
  Injecting an overheat alert → alert doc has `push_sent` (false with no Firebase),
  no `whatsapp_sent`/`routed_to`. Backend never crashes when FCM is unconfigured.
- `GET /alerts` returns a global feed, newest first, `created_at` ISO strings.
- `POST /push/register` twice with one token → exactly one `push_tokens` doc.
- Android app: Settings gate → asset list (live status dots, 5 s poll) → asset
  detail (gauges update from the WebSocket, sparklines, SIGNAL LOST on backend
  kill) → digital twin animates from live readings. Alerts screen lists all
  devices' alerts. 3 JVM unit test classes pass.
- With Firebase configured (Arham's manual prereq): overheat injection →
  notification on the phone within ~10 s → tap opens that asset; alert doc
  `push_sent: true`.

---
## ✅ Actually achieved

**Backend (Tasks 1–4) — done, committed, syntax-checked; runtime verification is
Arham's (the repo `.venv` is broken — points at a dead Python path from a machine
migration).**
- `8548fb1` Twilio removed — `whatsapp.py` deleted, `config.py` down to
  `fcm_credentials_file`, `alerts.py` → `push_sent`, `_persist_alert` WhatsApp
  block gone, `requirements.txt` → `firebase-admin`, `.env` cleaned (the leaked
  Twilio SID/token lines are gone), `AlertsPanel.jsx` → "📲 Pushed".
- `68a605f` `GET /alerts` global feed.
- `553e5bf` `routers/push.py` + `push_tokens`.
- `7b17e78` `push.py` FCM sender wired into `_persist_alert` (graceful no-op
  when `fcm_credentials_file` is empty/missing — logs and returns
  `{"sent":0,"invalid":[]}`).
- `contacts` / `vendor_whatsapp` device fields **left in place** — dormant
  Optional fields; removing them means touching `EditDevice.jsx` + `seed_nfl.py`
  + retesting the web app for no functional gain.

**Android (Tasks 5–10) — all source written, committed per task, NOT compiled**
(no Android SDK/Gradle in this environment; Arham builds in Android Studio):
- `66d6e9d` scaffold · `d5510a6` models + Settings gate · `e81a5d5` API + health
  (+tests) + asset list + alerts · `8d91f00` DeviceSocket (+tests) + detail ·
  `1988af9` FCM · `3f86cd0` TwinMapping (+tests) + TwinView.

**Deviations from plan:**
- **SceneView / `twin_rig.glb` dropped for v1.** `TwinView` is a Compose-Canvas
  twin (2D, stylised) driven by the same `TwinMapping.stateFrom()` + tests —
  it needs no model asset, no Filament init, and always renders. Filament/GLTF is
  the documented upgrade (`android/app/src/main/assets/README-twin-model.md`),
  `TwinMapping` and its tests do not change when it lands.
- **`google-services` Gradle plugin left commented**; the `firebase-messaging`
  AAR, the service, the manifest entry and token registration are all wired.
  App builds and runs without `google-services.json` — FCM is inert
  (`FirebaseMessaging.getInstance()` calls wrapped in `runCatching`). Arham
  uncomments one line in each build file after adding the JSON.
- **`GET /devices/{id}/readings` returns `ts` as an ISO string** (unlike the
  epoch-ms live feed), so the Android side has a separate `HistoryPoint` type for
  history vs `Reading` for live/last-known. Backend unchanged.
- Kotlin toolchain pinned: AGP 8.7.2, Gradle 8.9, Kotlin 2.0.21, Compose BOM
  2024.09.03. `compileSdk`/`targetSdk` 35 (plan-locked) — Android Studio pulls
  SDK 35 on first sync (the machine has platform 37 / build-tools 36).
- Added `.idea/` to root `.gitignore` (an IDE dir appeared after `/add-dir`).

**Not done in this phase (needs Arham / a decision):**
- Firebase project creation + `google-services.json` + service-account JSON —
  manual prereq for end-to-end push (Tasks 4 & 9 acceptance checks).
- Rebuild `.venv` and run the backend verification `curl`s.
- Flash Phase 13 firmware and confirm `TL-01` on the app.
- Role-based alert routing is explicitly **v2** (owner/maintenance/vendor split);
  v1 broadcasts every alert to every registered token.
