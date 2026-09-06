# TwinLab — Twilio removal + Android app (v1)

**Date:** 2026-09-07
**Status:** approved design, pending implementation plan
**Owner:** Arham

---

## 1. Goal

Two tracks, one spec because they share the alert path:

1. **Remove Twilio completely** from the backend. The WhatsApp/SMS alert transport is deleted, not replaced in kind.
2. **Build a native Android app** (Kotlin + Jetpack Compose) that is the TwinLab client for the asset owner and the maintenance/ops head: live asset dashboard, a 3D "digital twin" per asset, and push-notification alerts that replace the WhatsApp channel.

The Android app reaches the existing FastAPI + WebSocket backend over the LAN; its base URL is entered in an app Settings screen. Alerts reach the phone via Firebase Cloud Messaging (FCM).

### Positioning note

**Deliberate pitch decision (Arham, 2026-09-07):** the buyer surface shifts from "Roman Urdu WhatsApp" to **TwinLab's own app — notifications + monitoring**. `Obsidian Vault/Hackathons/TwinLab/TwinLab_Identity.md` §5–§7 and `CLAUDE.md` §1 (which still say the owner "receives WhatsApp alerts only") must be updated to match: the app is the buyer surface, the owner installs it. That documentation update is part of this work.

Role-based alert routing (owner / maintenance head / vendor / supply-chain lead) is a **future feature**, not v1 — v1 push is broadcast to every registered device.

---

## 2. Scope

### In scope — v1

**Backend:**
- Delete `backend/whatsapp.py`, the `twilio` dependency, and all `twilio_*` / `alert_whatsapp_to` settings.
- New `backend/push.py` — FCM sender (broadcast to all registered tokens).
- New `backend/routers/push.py` — `POST /push/register`, `DELETE /push/register/{token}`.
- New endpoint `GET /alerts?limit=` (global alert feed — the app's Alerts screen needs it; per-device `GET /devices/{id}/alerts` already exists).
- `alerts.py` / `main.py`: `whatsapp_sent` field → `push_sent`; `routed_to` → `pushed_to`.
- Docs: `CLAUDE.md`, `phase/limitations.md`, `README.md`, `TwinLab_Identity.md`.

**Android app** (`android/` folder in this repo, single Gradle module):
- **Settings / first-run** — backend base URL, `GET /health` test, DataStore persistence, gates app entry.
- **Asset list** — `GET /devices` grouped by plant, live status via polling `GET /devices/{id}/last-known` (~5 s), pull-to-refresh.
- **Asset detail** — 3D twin + live gauges (`/ws/{device_id}`) + sparklines (`/devices/{id}/readings`) + threshold-derived health status + this asset's recent alerts.
- **Alerts** — global feed (`GET /alerts`), FCM deep-link target.
- **3D digital twin** — one generic `.glb` machine rig, data-driven (colour / shake / spin / status ring / stale state), SceneView.
- **FCM** — `FirebaseMessagingService`, token registration, notification channel, deep-link, `POST_NOTIFICATIONS` runtime permission.
- JVM unit tests for pure logic (status derivation, 3D mapping, WS parsing).

### Out of scope — v1 (deferred to v2)

- Groq chat (`/chat`) in the app.
- RUL panel (`/devices/{id}/rul`) in the app.
- Role-based alert routing (v1 push = broadcast to every token). The `ROUTING` map in the deleted `whatsapp.py` is not reimplemented.
- Device registration / editing from the app (done via the web dashboard / API; app is read-only on the registry).
- Per-asset-type 3D models (v1 = one generic rig; `asset_type` ignored).
- Auth on any backend endpoint (none exists anywhere in the backend today).
- Offline cache / Room, Hilt, multi-module, tablet layouts, AR, Play Store signing/CI, crash reporting, analytics.
- Removing the `contacts` / `vendor_whatsapp` fields from the device model (left as unused `Optional` fields; removing them means touching `frontend/src/components/EditDevice.jsx` and retesting the web app for no functional gain).

---

## 3. Architecture

```
┌─────────────┐   MQTT    ┌──────────────┐  WS /ws/{id}   ┌──────────────┐
│ ESP32 TL-01 │──────────▶│              │◀───────────────│              │
│ / simulator │           │  FastAPI     │  REST          │ Android app  │
└─────────────┘           │  backend     │◀───────────────│ (Kotlin +    │
                          │              │  /devices      │  Compose)    │
                          │  _persist_   │  /readings     │              │
                          │  alert()     │  /alerts       │              │
                          └──────┬───────┘  /push/register└──────▲───────┘
                                 │ push.send_alert()             │
                                 ▼                               │ FCM push
                          ┌──────────────┐   HTTP v1   ┌──────────┴───────┐
                          │ Firebase FCM │────────────▶│ Google Play svcs │
                          └──────────────┘             └──────────────────┘
```

**Unchanged:** MQTT topic contract, ingestion, InfluxDB, MongoDB, the WS bridge, the alert engine (`alerts.py` `evaluate` / `evaluate_run_hours`), the web frontend (except doc strings).

**Changed in the alert path:** `_persist_alert` in `backend/main.py` currently calls `whatsapp.send_alert` via `loop.run_in_executor`. It will call `push.send_alert` the same way (firebase-admin `messaging.send` is sync).

---

## 4. Backend detail

### 4.1 Twilio removal

| File | Change |
|---|---|
| `backend/whatsapp.py` | delete |
| `backend/config.py` | remove `twilio_account_sid`, `twilio_auth_token`, `twilio_channel`, `twilio_whatsapp_from`, `twilio_sms_from`, `alert_whatsapp_to`; add `fcm_credentials_file: str = ""` |
| `backend/main.py` | remove `import whatsapp`; in `_persist_alert`, replace the whatsapp executor block with the push executor block; set `push_sent: bool`; drop `routed_to` (broadcast push has no routing info) |
| `backend/alerts.py` | `_make_alert`: `"whatsapp_sent": False` → `"push_sent": False` |
| `frontend/src/components/AlertsPanel.jsx` | replace the `a.routed_to` "Sent to:" block with a `a.push_sent` "📲 Pushed" badge |
| `requirements.txt` | remove `twilio`; add `firebase-admin` |
| `backend/.env` / `.env.example` | remove `TWILIO_*`, `ALERT_WHATSAPP_TO`; add `FCM_CREDENTIALS_FILE` |
| `.gitignore` | add `backend/fcm-service-account.json` |

Existing alert docs in Mongo with `whatsapp_sent` are not migrated (demo data; the field just stops being read). The web frontend's `AlertsPanel.jsx` reads `whatsapp_sent` — update it to `push_sent` (one file, cosmetic badge).

### 4.2 `backend/push.py`

Flat module, mirrors the deleted `whatsapp.py` shape:

```
send_alert(alert: dict, device: dict) -> dict
  # returns {"sent": <int count>, "failed": <int count>}; never raises
  # - if not settings.fcm_credentials_file or firebase init fails: log, return zeros
  # - body = _format_body(alert, device_name, device)   # ported verbatim from whatsapp.py (EN + UR)
  # - tokens = [t["token"] for t in db.push_tokens.find()]   (sync pymongo? no — see note)
  # - messaging.send_each([Message(token=t, notification=Notification(title, body), data={"device_id": ...}) ...])
  # - prune tokens that come back UNREGISTERED / INVALID_ARGUMENT
```

**Mongo access note:** `push.send_alert` runs in a thread executor (like `whatsapp.send_alert` did), so it cannot use the async Motor client. Two options — decide in the plan:
- (a) pass the token list in from `_persist_alert` (which is async and has `get_db()`), keep `push.py` pure I/O to FCM.
- (b) give `push.py` its own sync `pymongo` client (the simulator already does this per CLAUDE.md §5).

Recommend **(a)** — smaller, no second Mongo client, and token pruning results are returned to `_persist_alert` to apply.

`_format_body` is lifted from `whatsapp.py` unchanged (the fuel_theft / consumable_reorder / threshold EN+UR text). Notification title = `"<device name> — <severity>"`, body = the EN line; UR line in the `data` payload for the app to show.

### 4.3 `backend/routers/push.py`

```
POST   /push/register        body {token: str}       upsert into push_tokens (unique on token), {token, created_at, last_seen}
DELETE /push/register/{token}                         remove one token
```

New collection `push_tokens`. No auth (consistent with the rest of the backend).

### 4.4 `GET /alerts`

New route (in `routers/alerts.py` or a small addition to `main.py`):
```
GET /alerts?limit=50&since=<iso>   ->   [alert, ...]  sorted created_at desc, across all devices
```
Same shape as `GET /devices/{id}/alerts` minus the device filter.

### 4.5 CORS / host

`main.py` CORS is already `allow_origins=["*"]`. Uvicorn must run with `--host 0.0.0.0` for the phone to connect — document in CLAUDE.md §3 and the run commands.

---

## 5. Android app detail

### 5.1 Project

- `android/` — single module `app`. `com.omnitex.twinlab` (confirm package in plan).
- `minSdk 24`, `targetSdk 35`, Kotlin, Compose BOM, Material 3.
- Single `MainActivity`, Navigation Compose, no fragments.
- No Hilt — a hand-built `AppContainer` (holds `TwinLabApi`, `SettingsRepository`, constructs ViewModels via a factory).

### 5.2 Dependencies

| Purpose | Artifact |
|---|---|
| Compose | `androidx.compose:compose-bom`, `material3`, `navigation-compose`, `lifecycle-viewmodel-compose` |
| HTTP + WS | `io.ktor:ktor-client-okhttp`, `ktor-client-websockets`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` |
| JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` |
| 3D | `io.github.sceneview:sceneview` |
| Push | `com.google.firebase:firebase-bom`, `firebase-messaging`; `com.google.gms:google-services` plugin |
| Settings | `androidx.datastore:datastore-preferences` |
| Test | `junit4`, `kotlinx-coroutines-test` (JVM only) |

### 5.3 Data layer

- **`SettingsRepository`** — DataStore Preferences. `baseUrl: Flow<String?>`, `setBaseUrl(String)`.
- **`TwinLabApi`** — Ktor `HttpClient`. The `AppContainer` rebuilds the client whenever `SettingsRepository.baseUrl` emits a new value (simpler than threading a per-request base URL through every call). Methods:
  - `getDevices(): List<Device>`
  - `getLastKnown(id): Map<String, Reading>`
  - `getReadings(id, sensor, limit, rangeHours): List<Reading>`
  - `getAlerts(limit): List<Alert>`
  - `getDeviceAlerts(id, limit): List<Alert>`
  - `registerPushToken(token)` / `unregisterPushToken(token)`
- **`DeviceSocket(baseUrl, deviceId)`** — Ktor `webSocketSession` to `/ws/{deviceId}`. Exposes `messages: Flow<WsMessage>` where `WsMessage = Reading | AlertMsg` (discriminate on presence of `type == "alert"`, matching `main.py._json_safe` + `manager.broadcast`). Reconnect: 3 s fixed delay on close, matching `frontend/src/hooks/useDeviceSocket.js`.
- **Models** (`@Serializable`): `Device` (device_id, name, location, plant, sensors, thresholds, source, status, asset_type, criticality), `Reading` (device_id, sensor, value, unit, ts), `Alert` (device_id, sensor, alert_type, severity, value, unit, message_en, message_ur, detail, ts, created_at).

### 5.4 Screens & ViewModels

| Screen | ViewModel | Sources |
|---|---|---|
| Settings | `SettingsViewModel` | `SettingsRepository`, `GET /health` |
| Asset list | `AssetListViewModel` | `getDevices()` once + poll `getLastKnown()` per device every 5 s; derive status from `thresholds` |
| Asset detail | `AssetDetailViewModel` | `DeviceSocket` flow (live), `getReadings()` (sparklines), `getDeviceAlerts()`; emits `AssetDetailUiState` including `TwinState` |
| Alerts | `AlertsViewModel` | `getAlerts()` + refresh on FCM broadcast / pull |

All ViewModels expose `StateFlow<UiState>` with `Loading / Content / Error` variants. Navigation: `list → detail/{deviceId}`, `alerts`, `settings`. Deep-link `twinlab://asset/{deviceId}` from notifications.

### 5.5 Status derivation (tested unit)

`fun healthStatus(readings: Map<String, Double>, thresholds: Map<String, Bounds>): Health` where `Health = OK | WARNING | CRITICAL | UNKNOWN`. Mirrors `backend/alerts.py` `evaluate` semantics: a reading past `max` or below `min` on `temperature` / `load_current` → CRITICAL, other sensors → WARNING, no readings → UNKNOWN. Fixture-tested against sample payloads.

### 5.6 FCM

- `TwinLabMessagingService : FirebaseMessagingService`
  - `onNewToken(token)` → `TwinLabApi.registerPushToken(token)` (best-effort; retry on next app start).
  - `onMessageReceived(msg)` → build a notification on channel `twinlab_alerts` (importance HIGH), title/body from `msg.notification`, `PendingIntent` → `twinlab://asset/{msg.data["device_id"]}`.
- On app start (after base URL is set): fetch current token, `registerPushToken`.
- `POST_NOTIFICATIONS` runtime permission requested on first launch (API 33+); app still works if denied (in-app alerts only).
- `AndroidManifest`: the service, the deep-link intent-filter, `google-services.json` wired via the plugin.

---

## 6. 3D digital twin detail

### 6.1 Asset

`android/app/src/main/assets/twin_rig.glb` — one stylised machine: skid/body box + a front rotor/fan mesh (named node, e.g. `rotor`) that can spin about its axis. Source: a CC0 model or a 30-minute Blender build. Target < 500 KB. `asset_type` is not used to pick a model in v1.

### 6.2 Mapping (tested unit)

`object TwinMapping { fun stateFrom(readings: Map<String, Double>, thresholds: Map<String, Bounds>, lastMsgAgeMs: Long): TwinState }`

```
data class TwinState(
  val bodyColor: Color,        // green→amber→red by temperature within its threshold band; grey if absent
  val shakeAmplitude: Float,   // meters; vibration * SHAKE_GAIN, clamped to SHAKE_MAX; 0 if calm/absent
  val rotorRpm: Float,         // from load_current if present; else RUNNING_RPM if vibration>RUN_EPS; else IDLE_RPM
  val statusRingColor: Color,  // from healthStatus()
  val stale: Boolean,          // lastMsgAgeMs > STALE_MS
)
```

Tunable constants at the top of the file: `SHAKE_GAIN`, `SHAKE_MAX`, `TEMP_COLOR_MIN`, `TEMP_COLOR_MAX` (fallback band when no thresholds), `RUNNING_RPM`, `IDLE_RPM`, `RUN_EPS`, `STALE_MS`. These are guesses until real sensor magnitudes are seen — expect to retune once TL-01 has run.

### 6.3 Rendering

- SceneView `Scene` composable in the detail screen. Load `twin_rig.glb` once.
- `onFrame` callback: read the latest `TwinState` from the ViewModel; set body material `baseColorFactor` to `bodyColor`; offset model position by a per-frame random vector scaled to `shakeAmplitude`; advance `rotor` rotation by `rotorRpm/60 * frameDeltaSeconds * 360°`; set the status-ring node colour; apply a desaturating material + show a "SIGNAL LOST" overlay when `stale`.
- Camera: fixed distance, slow continuous auto-rotate, pinch-zoom + drag enabled.
- Lifecycle: SceneView paused when the detail screen is not `RESUMED`.

### 6.4 Fallback

If the model fails to load or SceneView throws, the detail screen renders the 2D health card + gauges + sparklines only, and logs the failure. The 3D view is additive — status is always available without it.

---

## 7. Testing

JVM unit tests only (`android/app/src/test/`), VitalSense-style:

- `TwinMappingTest` — fixture `Map` inputs → assert `TwinState` fields (colour endpoints, shake clamp, rpm branches, stale flag).
- `HealthStatusTest` — threshold breach cases → assert `Health`.
- `WsMessageParsingTest` — sample JSON strings from `main.py` (a reading, an alert) → assert correct `WsMessage` subtype and fields.
- Backend: no test suite per CLAUDE.md §6. Manual acceptance (§8).

No instrumented tests, no Compose UI tests, no screenshot tests.

---

## 8. Acceptance checks

**Backend:**
- `grep -ri twilio backend/ requirements.txt` → zero hits.
- Backend starts with no `TWILIO_*` env vars and no error.
- `POST /push/register {token:"test"}` → `push_tokens` has the doc; re-POST same token → still one doc.
- `GET /alerts?limit=5` → returns recent alerts across devices.
- Injecting an alert (sim-control overheat) with a real FCM token registered → notification arrives on the phone within ~10 s; alert doc has `push_sent: true`.
- Injecting an alert with **no** tokens registered → no error, `push_sent: false`, backend logs "no tokens".

**Android:**
- Fresh install → Settings gate → enter laptop URL → Test passes → asset list loads, grouped by plant, status dots reflect live data.
- Asset detail → 3D model visible and spinning; gauges update live from the WS; sparklines render.
- sim-control inject overheat on that asset → within a cooldown window: push notification (app backgrounded), 3D body turns red + shakes, alert appears in the feed and the asset's alert list.
- Tap the notification → opens that asset's detail screen.
- Kill the backend → detail screen shows "SIGNAL LOST", list status dots go stale; backend back → recovers without app restart.
- Deny notification permission → app still runs, in-app alerts still work.
- Model load forced to fail → detail screen still shows card + gauges.

---

## 9. Work order (for the plan)

1. Backend: Twilio removal (delete, config, wiring, docs). Verify stack still runs and alerts still persist (just no send).
2. Backend: `push.py` + `routers/push.py` + `GET /alerts` + `push_sent` rename. Verify with `curl` + a manually-inserted token (FCM send will fail gracefully without a real token).
3. Android: project scaffold, dependencies, `AppContainer`, models, `SettingsRepository`, Settings screen + `/health` gate.
4. Android: `TwinLabApi` + asset list (poll `last-known`) + `healthStatus` + tests.
5. Android: `DeviceSocket` + asset detail (gauges, sparklines, alerts section) + WS parsing tests.
6. Android: FCM service, token registration, notification channel, deep-link, permission.
7. Android: SceneView integration, `twin_rig.glb`, `TwinMapping` + tests, wire to detail ViewModel, fallback.
8. Firebase project setup (Arham, parallel — needed before steps 2 and 6 can be end-to-end tested).
9. Docs: CLAUDE.md, limitations.md, README, TwinLab_Identity.md. Phase doc `phase/phase-14.md` per CLAUDE.md §7.

---

## 10. Decisions carried into the plan

Resolved here so the plan doesn't re-litigate them:

- **Package name:** `com.omnitex.twinlab`.
- **`push.py` Mongo access:** option (a) — `_persist_alert` (async, already holds `get_db()`) passes the token list in and applies the pruning results. `push.py` stays pure FCM I/O, no second Mongo client.
- **Ktor client lifecycle:** `AppContainer` rebuilds the `HttpClient` when `baseUrl` changes.
- **Phase doc:** one `phase/phase-14.md` covering both tracks (Twilio removal is the first, small step of the same phase).
- **`twin_rig.glb`:** search Poly Haven / Sketchfab CC0 first; build a primitive rig in Blender only if nothing fits. Model file committed to the repo either way.
