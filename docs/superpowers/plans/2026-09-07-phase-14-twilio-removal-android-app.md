# Phase 14 — Twilio removal + Android app (v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete Twilio from the backend, replace owner alerting with Firebase Cloud Messaging push, and build a native Kotlin/Compose Android app (asset dashboard + 3D digital twin + push alerts) as a LAN client of the existing FastAPI backend.

**Architecture:** The FastAPI backend, MQTT ingestion, InfluxDB, MongoDB, WebSocket bridge and alert engine are unchanged except the alert *transport*: `_persist_alert` stops calling `whatsapp.send_alert` and starts calling `push.send_alert` (FCM, broadcast to all registered device tokens). The Android app is a single-module Gradle project under `android/`, talking to the backend over the LAN (base URL entered in a Settings screen), using Ktor for REST + WebSocket, SceneView for the 3D twin, and Firebase Messaging for push.

**Tech Stack:** Backend — FastAPI, Motor, `firebase-admin`. Android — Kotlin, Jetpack Compose (Material 3), Navigation Compose, Ktor client (OkHttp engine + websockets + content-negotiation), kotlinx.serialization, DataStore Preferences, SceneView (Filament), Firebase Messaging. Tests — JVM unit tests only (`junit4`, `kotlinx-coroutines-test`).

**Spec:** `docs/superpowers/specs/2026-09-07-twinlab-android-app-design.md` — read it alongside this plan.

## Global Constraints

- **Backend: no Python test suite** (CLAUDE.md §6). Backend tasks verify with `curl` / PowerShell `Invoke-RestMethod` + expected output, then commit. Do NOT add `pytest`.
- **Android: JVM unit tests only** for pure logic (status derivation, 3D mapping, WS parsing). No instrumented tests, no Compose UI tests.
- **Flat backend modules stay flat** (CLAUDE.md §5): `push.py` is procedural like the deleted `whatsapp.py` — no classes, no DI.
- **MQTT topic contract unchanged** (CLAUDE.md §4). This plan does not touch ingestion, the simulator, or firmware.
- **Async Motor in the backend, never PyMongo** (CLAUDE.md §5). `push.send_alert` runs in a thread executor and is handed its token list by the async caller — it never opens a Mongo client.
- **Never commit** `backend/.env`, `backend/fcm-service-account.json`, `android/app/google-services.json` (all gitignored by this plan).
- **Android:** `minSdk 24`, `targetSdk 35`, package `com.omnitex.twinlab`.
- **Product name is "TwinLab"** (one word), never "TwinLab AI".
- **Commit after every task** (frequent commits). Commit messages: imperative mood, no `Co-Authored-By` trailer.

## Prerequisite (Arham, manual — not a task)

Firebase is an external dependency for end-to-end testing Tasks 4 and 9. The *code* for those tasks is written and unit-tested without it; the *acceptance check* needs it.

1. Create a Firebase project at console.firebase.google.com (free Spark plan).
2. Add an Android app, package `com.omnitex.twinlab`. Download `google-services.json` → `android/app/google-services.json`.
3. Project Settings → Service Accounts → Generate new private key → save the JSON on the backend machine → set `FCM_CREDENTIALS_FILE` in `backend/.env` to its path.

---

## File Structure

**Backend — created:**
- `backend/push.py` — FCM sender. `send_alert(alert, device, tokens) -> {"sent": int, "invalid": [str]}`. Ports `_format_body` from the deleted `whatsapp.py`.
- `backend/routers/push.py` — `POST /push/register`, `DELETE /push/register/{token}`.

**Backend — modified:**
- `backend/whatsapp.py` — deleted.
- `backend/config.py` — drop 6 `twilio_*`/`alert_whatsapp_to` fields; add `fcm_credentials_file`.
- `backend/main.py` — drop `import whatsapp`; rewire `_persist_alert`; add `GET /alerts`; include the push router.
- `backend/alerts.py` — `whatsapp_sent` → `push_sent` in `_make_alert`.
- `requirements.txt` — drop `twilio`, add `firebase-admin`.
- `backend/.env` — drop `TWILIO_*`/`ALERT_WHATSAPP_TO`, add `FCM_CREDENTIALS_FILE`.
- `frontend/src/components/AlertsPanel.jsx` — `routed_to` "Sent to:" block → `push_sent` "📲 Pushed" badge.
- `.gitignore` — add FCM + Android ignores.
- `CLAUDE.md`, `phase/limitations.md`, `README.md` — Twilio → FCM/app.

**Android — created (`android/`):** standard Gradle layout. Key source files:
- `app/src/main/java/com/omnitex/twinlab/`
  - `MainActivity.kt`, `TwinLabApp.kt` (Application + `AppContainer`)
  - `data/` — `Models.kt`, `TwinLabApi.kt`, `DeviceSocket.kt`, `SettingsRepository.kt`
  - `domain/` — `Health.kt` (`healthStatus()`), `TwinMapping.kt` (`stateFrom()` → `TwinState`)
  - `push/` — `TwinLabMessagingService.kt`, `Notifications.kt`
  - `ui/` — `Nav.kt`, `settings/SettingsScreen.kt` + `SettingsViewModel.kt`, `assets/AssetListScreen.kt` + `AssetListViewModel.kt`, `detail/AssetDetailScreen.kt` + `AssetDetailViewModel.kt` + `TwinView.kt`, `alerts/AlertsScreen.kt` + `AlertsViewModel.kt`, `common/` (gauges, sparkline, status dot)
- `app/src/test/java/com/omnitex/twinlab/` — `HealthStatusTest.kt`, `TwinMappingTest.kt`, `WsMessageParsingTest.kt`
- `app/src/main/assets/twin_rig.glb`

**Docs — created:**
- `phase/phase-14.md` (CLAUDE.md §7).
- `Obsidian Vault/Hackathons/TwinLab/TwinLab_Identity.md` — updated (buyer surface).

---

## Task 1: Remove Twilio from the backend

**Files:**
- Delete: `backend/whatsapp.py`
- Modify: `backend/config.py`, `backend/main.py`, `backend/alerts.py`, `requirements.txt`, `backend/.env`, `frontend/src/components/AlertsPanel.jsx`, `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: alert docs now carry `push_sent: bool` (never `whatsapp_sent`/`routed_to`). `settings` no longer has any `twilio_*` attribute; gains `settings.fcm_credentials_file: str`.

- [ ] **Step 1: Delete the Twilio module**

```bash
git rm backend/whatsapp.py
```

- [ ] **Step 2: Strip Twilio settings, add FCM setting**

`backend/config.py` — replace the twilio block:

```python
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    influx_url: str = "http://localhost:8086"
    influx_token: str = "twinlab-super-secret-token"
    influx_org: str = "twinlab"
    influx_bucket: str = "twinlab"
    mongo_uri: str = "mongodb://admin:twinlab123@localhost:27017"
    mongo_db: str = "twinlab"
    groq_api_key: str = ""
    fcm_credentials_file: str = ""       # path to the Firebase service-account JSON
    diesel_price_pkr: float = 280.0

    model_config = {"env_file": ".env"}


settings = Settings()
```

- [ ] **Step 3: Rename the alert field**

`backend/alerts.py` — in `_make_alert`, change the one line:

```python
        "push_sent":     False,
```

(was `"whatsapp_sent": False`)

- [ ] **Step 4: Rewire `_persist_alert` in `backend/main.py`**

Remove `import whatsapp` (line ~14). Replace the WhatsApp block at the end of `_persist_alert` (the `# Send WhatsApp in a thread` block, ~7 lines) with nothing — the alert is already inserted with `push_sent: False`. The function now ends:

```python
        result = await db.alerts.insert_one(dict(alert))
        await manager.broadcast(alert["device_id"], {**_json_safe(alert), "type": "alert"})
        log.info(
            f"[ALERT] {alert['alert_type']} {alert['severity']} — "
            f"{alert['device_id']}/{alert['sensor']} — {alert['detail']}"
        )
    except Exception as e:
        log.error(f"[ALERT] persist failed: {e}")
```

Keep the `device_doc` fetch and the `consumable_reorder` run_hours reset above it — those are unrelated to Twilio.

- [ ] **Step 5: Update dependencies**

`requirements.txt` — remove the `twilio` line. Leave `firebase-admin` out for now (added in Task 4) OR add it now:

```
paho-mqtt==1.6.1
influxdb-client==1.36.1
fastapi
uvicorn[standard]
motor
pydantic-settings
numpy
groq
firebase-admin
```

Then: `.venv\Scripts\pip uninstall -y twilio` and `.venv\Scripts\pip install firebase-admin`.

- [ ] **Step 6: Clean `backend/.env`**

Remove every `TWILIO_*` and `ALERT_WHATSAPP_TO` line. Add:

```
FCM_CREDENTIALS_FILE=./fcm-service-account.json
```

- [ ] **Step 7: Update the frontend alert badge**

`frontend/src/components/AlertsPanel.jsx` — replace lines 75-77:

```jsx
          {a.push_sent && (
            <span className="alert-routed-to">📲 Pushed</span>
          )}
```

- [ ] **Step 8: Add gitignores**

`.gitignore` — under the credentials block:

```
backend/fcm-service-account.json

# Android
android/.gradle/
android/build/
android/app/build/
android/local.properties
android/.idea/
android/app/google-services.json
android/app/release/
android/captures/
*.keystore
```

- [ ] **Step 9: Verify no Twilio remains and the stack runs**

```powershell
# from repo root
Select-String -Path backend/*.py, backend/routers/*.py, requirements.txt -Pattern "twilio|whatsapp" -CaseSensitive:$false
# expect: no matches

docker compose up -d
.venv\Scripts\python ingestion.py            # separate terminal
.venv\Scripts\python simulator.py            # separate terminal
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --port 8000
```

Expected: backend starts, no `ImportError`, no `AttributeError` on `settings`. Trigger an alert:

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/sim/NFL-SITE-GEN-01" -Method Put -ContentType "application/json" -Body '{"inject":{"overheat":{"active":true,"until_ts":9999999999999}}}'
Start-Sleep 15
Invoke-RestMethod -Uri "http://localhost:8000/devices/NFL-SITE-GEN-01/alerts?limit=1"
```

Expected: an alert doc with `"push_sent": false` and no `whatsapp_sent`/`routed_to` keys.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Remove Twilio; alert docs carry push_sent instead of whatsapp_sent"
```

---

## Task 2: Global alert feed endpoint

**Files:**
- Modify: `backend/main.py`

**Interfaces:**
- Consumes: nothing.
- Produces: `GET /alerts?limit=<int>&since=<iso>` → JSON array of alert docs (same shape as `GET /devices/{id}/alerts`), across all devices, `created_at` desc, `created_at` serialised to ISO string.

- [ ] **Step 1: Add `HTTPException` to the FastAPI import**

`backend/main.py` line ~10:

```python
from fastapi import FastAPI, HTTPException
```

- [ ] **Step 2: Add the route**

`backend/main.py` — after the `last_known` route at the bottom:

```python
@app.get("/alerts", tags=["alerts"])
async def all_alerts(limit: int = 50, since: str | None = None):
    """Global alert feed across all devices, newest first."""
    db = get_db()
    query: dict = {}
    if since:
        try:
            query["created_at"] = {"$gte": datetime.fromisoformat(since.replace("Z", "+00:00"))}
        except ValueError:
            raise HTTPException(400, "Invalid 'since' timestamp — use ISO 8601")

    limit = max(1, min(limit, 200))
    docs = (
        await db.alerts.find(query, {"_id": 0})
        .sort("created_at", -1)
        .limit(limit)
        .to_list(length=limit)
    )
    for d in docs:
        if hasattr(d.get("created_at"), "isoformat"):
            d["created_at"] = d["created_at"].isoformat()
    return docs
```

- [ ] **Step 3: Verify**

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/alerts?limit=5"
```

Expected: JSON array (possibly empty if no alerts yet), newest first, each with `device_id`, `severity`, `message_en`, `created_at` as an ISO string.

- [ ] **Step 4: Commit**

```bash
git add backend/main.py
git commit -m "Add GET /alerts global feed endpoint"
```

---

## Task 3: Push-token registration endpoint

**Files:**
- Create: `backend/routers/push.py`
- Modify: `backend/main.py`

**Interfaces:**
- Consumes: `get_db()` from `db.mongo`.
- Produces: `POST /push/register` (body `{"token": str}`, 204) upserts into the `push_tokens` collection (`{token, created_at, last_seen}`, deduped on `token`). `DELETE /push/register/{token}` (204) removes one. Later tasks read `db.push_tokens` for the token list.

- [ ] **Step 1: Create the router**

`backend/routers/push.py`:

```python
import logging
from datetime import datetime, timezone

from fastapi import APIRouter
from pydantic import BaseModel

from db.mongo import get_db

log = logging.getLogger("twinlab.push")
router = APIRouter()


class TokenBody(BaseModel):
    token: str


@router.post("/register", status_code=204)
async def register(body: TokenBody):
    db = get_db()
    now = datetime.now(timezone.utc)
    await db.push_tokens.update_one(
        {"token": body.token},
        {"$set": {"token": body.token, "last_seen": now},
         "$setOnInsert": {"created_at": now}},
        upsert=True,
    )
    log.info(f"[PUSH] token registered ({body.token[:12]}…)")


@router.delete("/register/{token}", status_code=204)
async def unregister(token: str):
    db = get_db()
    await db.push_tokens.delete_one({"token": token})
    log.info(f"[PUSH] token removed ({token[:12]}…)")
```

- [ ] **Step 2: Mount it**

`backend/main.py` — with the other `include_router` calls:

```python
from routers import devices, readings, ws, chat, rul, sim as sim_router
from routers import alerts as alerts_router
from routers import push as push_router
```

```python
app.include_router(push_router.router, prefix="/push", tags=["push"])
```

- [ ] **Step 3: Verify**

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/push/register" -Method Post -ContentType "application/json" -Body '{"token":"test-token-abc"}'
Invoke-RestMethod -Uri "http://localhost:8000/push/register" -Method Post -ContentType "application/json" -Body '{"token":"test-token-abc"}'
docker exec -it $(docker ps -qf "ancestor=mongo:7.0") mongosh -u admin -p twinlab123 --quiet --eval "db.getSiblingDB('twinlab').push_tokens.find().toArray()"
```

Expected: exactly one `push_tokens` doc for `test-token-abc` after two POSTs.

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/push/register/test-token-abc" -Method Delete
```

Expected: doc gone.

- [ ] **Step 4: Commit**

```bash
git add backend/routers/push.py backend/main.py
git commit -m "Add POST/DELETE /push/register and push_tokens collection"
```

---

## Task 4: FCM sender wired into the alert path

**Files:**
- Create: `backend/push.py`
- Modify: `backend/main.py`

**Interfaces:**
- Consumes: `settings.fcm_credentials_file`; `alert` dict and `device` dict (as passed to the deleted `whatsapp.send_alert`); a `list[str]` of tokens.
- Produces: `push.send_alert(alert: dict, device: dict, tokens: list[str]) -> dict` returning `{"sent": int, "invalid": list[str]}`. Never raises. `_persist_alert` uses `sent > 0` to set `push_sent` and deletes `invalid` tokens.

- [ ] **Step 1: Create `backend/push.py`**

```python
"""
TwinLab push alerts — Firebase Cloud Messaging, broadcast to all registered tokens.
Flat module; called from _persist_alert via a thread executor (sync, never awaited).
The caller passes the token list in — this module never opens a Mongo client.
"""

import logging

from config import settings

log = logging.getLogger("twinlab.push")

_fb_app = None          # firebase_admin App, initialised lazily
_init_failed = False


def _ensure_app():
    global _fb_app, _init_failed
    if _fb_app is not None or _init_failed:
        return _fb_app
    if not settings.fcm_credentials_file:
        _init_failed = True
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials
        _fb_app = firebase_admin.initialize_app(
            credentials.Certificate(settings.fcm_credentials_file)
        )
        log.info("[PUSH] Firebase initialised")
        return _fb_app
    except Exception as e:
        log.error(f"[PUSH] Firebase init failed: {e}")
        _init_failed = True
        return None


def send_alert(alert: dict, device: dict, tokens: list) -> dict:
    """
    Broadcast one alert to every token. Returns {"sent": int, "invalid": [token,...]}.
    Never raises. `invalid` holds tokens the caller should delete.
    """
    out = {"sent": 0, "invalid": []}
    if not tokens or _ensure_app() is None:
        log.info("[PUSH] not configured or no tokens — skipping")
        return out

    from firebase_admin import messaging

    device_name = device.get("name") or alert["device_id"]
    title = f"{device_name} — {alert['severity'].upper()}"
    body_en, body_ur = _format_body(alert, device_name, device)

    messages = [
        messaging.Message(
            token=t,
            notification=messaging.Notification(title=title, body=body_en),
            data={
                "device_id":  str(alert["device_id"]),
                "alert_type": str(alert["alert_type"]),
                "severity":   str(alert["severity"]),
                "message_ur": body_ur,
            },
            android=messaging.AndroidConfig(priority="high"),
        )
        for t in tokens
    ]

    try:
        resp = messaging.send_each(messages)
    except Exception as e:
        log.error(f"[PUSH] send failed: {e}")
        return out

    for token, r in zip(tokens, resp.responses):
        if r.success:
            out["sent"] += 1
        elif r.exception is not None and type(r.exception).__name__ in (
            "UnregisteredError", "SenderIdMismatchError"
        ):
            out["invalid"].append(token)

    log.info(f"[PUSH] {alert['device_id']}/{alert['alert_type']} — sent {out['sent']}/{len(tokens)}")
    return out


def _format_body(alert: dict, device_name: str, device: dict) -> tuple:
    """Ported from the deleted whatsapp.py. Returns (english, roman_urdu)."""
    if alert["alert_type"] == "fuel_theft":
        drop     = alert.get("drop_litres", 0.0)
        window_s = alert.get("window_s", 300)
        mins     = round(window_s / 60, 1)
        rupees   = round(drop * settings.diesel_price_pkr)
        en = (
            f"⚠️ {device_name} — fuel dropped {drop:.1f}L in {mins} min "
            f"while generator OFF. Suspected theft. Est. loss ~PKR {rupees:,}."
        )
        ur = (
            f"{device_name} — generator BAND honay ke bawajood {mins} min mein "
            f"{drop:.1f}L fuel kam hua. Chori ka shak. Taqreeban PKR {rupees:,} nuqsan."
        )
    elif alert["alert_type"] == "consumable_reorder":
        hours  = alert["value"]
        vendor = device.get("vendor_name") or "vendor"
        en = (
            f"🟠 {device_name} — {hours:.0f}h reached — consumable change due. "
            f"Vendor: {vendor}. Reorder now to avoid over-stock or SLOB accumulation."
        )
        ur = (
            f"{device_name} — {hours:.0f} ghante ho gaye — consumable tabdeeli chahiye. "
            f"Vendor: {vendor}. Abhi order kar dein — zayada stock ya SLOB nuqsaan se bachne ke liye."
        )
    else:
        sensor   = alert["sensor"].replace("_", " ")
        value    = alert["value"]
        unit     = alert["unit"]
        detail   = alert.get("detail", "")
        severity = alert["severity"].upper()
        icon     = "\U0001f534" if alert["severity"] == "critical" else "\U0001f7e1"
        en = f"{icon} {device_name} — {sensor} {value}{unit} ({detail}). Severity: {severity}."
        ur = f"{device_name} — {sensor} {value}{unit} ({detail}). Severity: {severity}."

    return en, ur
```

- [ ] **Step 2: Wire into `_persist_alert`**

`backend/main.py` — add `import push` near `import alerts as alert_engine`. In `_persist_alert`, after the `log.info(f"[ALERT] ...")` line and before the `except`:

```python
        # Push via FCM in a thread (firebase-admin is sync)
        tokens = [
            t["token"] for t in
            await db.push_tokens.find({}, {"_id": 0, "token": 1}).to_list(length=500)
        ]
        if tokens:
            loop = asyncio.get_running_loop()
            res  = await loop.run_in_executor(None, push.send_alert, alert, device_doc, tokens)
            await db.alerts.update_one(
                {"_id": result.inserted_id}, {"$set": {"push_sent": res["sent"] > 0}}
            )
            if res["invalid"]:
                await db.push_tokens.delete_many({"token": {"$in": res["invalid"]}})
```

- [ ] **Step 3: Verify graceful no-op (no Firebase configured)**

With `FCM_CREDENTIALS_FILE` pointing at a non-existent file or empty:

```powershell
# register a fake token, trigger an alert
Invoke-RestMethod -Uri "http://localhost:8000/push/register" -Method Post -ContentType "application/json" -Body '{"token":"fake"}'
Invoke-RestMethod -Uri "http://localhost:8000/sim/NFL-SITE-GEN-01" -Method Put -ContentType "application/json" -Body '{"inject":{"overheat":{"active":true,"until_ts":9999999999999}}}'
Start-Sleep 15
Invoke-RestMethod -Uri "http://localhost:8000/devices/NFL-SITE-GEN-01/alerts?limit=1"
```

Expected: no crash; backend logs `[PUSH] not configured or no tokens — skipping` OR `[PUSH] Firebase init failed`; alert doc has `push_sent: false`.

- [ ] **Step 4: Verify real delivery (needs the Firebase prerequisite + a real device token)**

Once Task 9 is done and the app has registered a real token, repeat the overheat injection. Expected: a notification on the phone within ~10 s; alert doc `push_sent: true`.

- [ ] **Step 5: Commit**

```bash
git add backend/push.py backend/main.py
git commit -m "Add FCM push sender, broadcast alerts to registered tokens"
```

---

## Task 5: Android project scaffold

**Files:**
- Create: `android/` — `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/omnitex/twinlab/MainActivity.kt`, `app/src/main/java/com/omnitex/twinlab/TwinLabApp.kt`, `app/src/main/java/com/omnitex/twinlab/ui/Nav.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/proguard-rules.pro`, `app/src/main/assets/.gitkeep`
- Create (placeholder): `android/app/google-services.json` is NOT created here — Arham drops it in per the prerequisite. Build must tolerate its absence during scaffold by not yet applying the `google-services` plugin (added in Task 9).

**Interfaces:**
- Produces: `com.omnitex.twinlab.TwinLabApp` (Application subclass) exposing `container: AppContainer`. `AppContainer` initially holds nothing; later tasks add `settings`, `api`, factory methods. Navigation routes: `"settings"`, `"assets"`, `"detail/{deviceId}"`, `"alerts"`.

- [ ] **Step 1: Generate the project skeleton in Android Studio**

New Project → "Empty Activity" (Compose) → name `TwinLab`, package `com.omnitex.twinlab`, minSdk 24, Kotlin DSL, save location `D:\TwinLab_v2\android`. Let Gradle sync.

- [ ] **Step 2: Set `app/build.gradle.kts` dependencies**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.omnitex.twinlab"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.omnitex.twinlab"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.github.sceneview:sceneview:2.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

(Firebase deps + the `google-services` plugin are added in Task 9, so the project builds now without `google-services.json`.)

- [ ] **Step 3: `TwinLabApp.kt` + `AppContainer`**

```kotlin
package com.omnitex.twinlab

import android.app.Application

class AppContainer(app: Application) {
    // filled in by later tasks: settings, api, viewmodel factories
}

class TwinLabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

`AndroidManifest.xml` — set `android:name=".TwinLabApp"` on `<application>`, add `<uses-permission android:name="android.permission.INTERNET" />`.

- [ ] **Step 4: `ui/Nav.kt` — route constants + NavHost skeleton**

```kotlin
package com.omnitex.twinlab.ui

object Routes {
    const val SETTINGS = "settings"
    const val ASSETS = "assets"
    const val DETAIL = "detail/{deviceId}"
    const val ALERTS = "alerts"
    fun detail(deviceId: String) = "detail/$deviceId"
}
```

`MainActivity.kt` — a `NavHost` with four `composable` entries, each rendering a `Text(route)` placeholder for now. Start destination `Routes.ASSETS`.

- [ ] **Step 5: Build and run**

Run on a device/emulator. Expected: app launches, shows "assets" placeholder text, no crash.

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "Scaffold Android app: Gradle, Compose, Ktor, nav skeleton"
```

---

## Task 6: Settings screen + backend URL gate

**Files:**
- Create: `android/app/src/main/java/com/omnitex/twinlab/data/SettingsRepository.kt`, `data/Models.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`
- Modify: `MainActivity.kt` (gate), `TwinLabApp.kt`/`AppContainer` (add `settings`)

**Interfaces:**
- Consumes: `AppContainer` from Task 5.
- Produces:
  - `data class Bounds(val min: Double?, val max: Double?)`
  - `@Serializable data class Device(val device_id: String, val name: String, val location: String = "", val plant: String? = null, val sensors: List<String> = emptyList(), val thresholds: Map<String, Bounds> = emptyMap(), val source: String = "simulator", val status: String = "active", val asset_type: String? = null, val criticality: String = "medium")`
  - `@Serializable data class Reading(val device_id: String = "", val sensor: String, val value: Double, val unit: String = "", val ts: Long = 0)`
  - `@Serializable data class Alert(val device_id: String, val sensor: String = "", val alert_type: String = "threshold", val severity: String = "warning", val value: Double = 0.0, val unit: String = "", val detail: String = "", val message_en: String = "", val message_ur: String = "", val ts: Long = 0, val created_at: String = "", val push_sent: Boolean = false)`
  - `SettingsRepository(context)` with `val baseUrl: Flow<String?>` and `suspend fun setBaseUrl(url: String)`.
  - `AppContainer.settings: SettingsRepository`.

- [ ] **Step 1: `data/Models.kt`** — exactly the `@Serializable` classes listed in Interfaces above, plus:

```kotlin
// Bounds arrives from the backend as {"min": x|null, "max": y|null}; both optional.
```

- [ ] **Step 2: `data/SettingsRepository.kt`**

```kotlin
package com.omnitex.twinlab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("twinlab_settings")
private val BASE_URL = stringPreferencesKey("base_url")

class SettingsRepository(private val context: Context) {
    val baseUrl: Flow<String?> = context.dataStore.data.map { it[BASE_URL] }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[BASE_URL] = url.trimEnd('/') }
    }
}
```

- [ ] **Step 3: Add to `AppContainer`**

```kotlin
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
}
```

- [ ] **Step 4: `ui/settings/SettingsViewModel.kt`**

```kotlin
package com.omnitex.twinlab.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnitex.twinlab.data.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data object Ok : TestResult
    data class Failed(val reason: String) : TestResult
}

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val currentUrl = settings.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _test = MutableStateFlow<TestResult>(TestResult.Idle)
    val test: StateFlow<TestResult> = _test

    fun testAndSave(url: String) = viewModelScope.launch {
        _test.value = TestResult.Testing
        val clean = url.trim().trimEnd('/')
        try {
            val client = HttpClient(OkHttp)
            val resp: HttpResponse = withTimeout(4000) { client.get("$clean/health") }
            client.close()
            if (resp.status.value in 200..299) {
                settings.setBaseUrl(clean)
                _test.value = TestResult.Ok
            } else {
                _test.value = TestResult.Failed("HTTP ${resp.status.value}")
            }
        } catch (e: Exception) {
            _test.value = TestResult.Failed(e.message ?: "unreachable")
        }
    }
}
```

- [ ] **Step 5: `ui/settings/SettingsScreen.kt`**

A Compose screen: an `OutlinedTextField` prefilled from `currentUrl` (hint `http://192.168.1.100:8000`), a "Test & Save" button that calls `vm.testAndSave(text)`, and a status line reflecting `test` (`Testing…` spinner / `✓ Connected` / `✗ <reason>`). On `TestResult.Ok`, call an `onSaved: () -> Unit` callback.

- [ ] **Step 6: Gate in `MainActivity.kt`**

Collect `container.settings.baseUrl` as state. While `null`, force the `SettingsScreen` as start destination (with `onSaved` navigating to `Routes.ASSETS`). While non-null, start at `Routes.ASSETS` and expose Settings via a nav action. Provide a `ViewModelProvider.Factory` in `AppContainer` that builds `SettingsViewModel(settings)`.

- [ ] **Step 7: Build, run, manual check**

Expected: fresh install shows the Settings screen. Enter the laptop URL with the backend running → "✓ Connected" → navigates to the assets placeholder. Kill backend, enter again → "✗ …". Relaunch app → goes straight to assets (URL persisted).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main
git commit -m "Android: models, SettingsRepository, Settings screen + URL gate"
```

---

## Task 7: TwinLabApi + asset list + health status

**Files:**
- Create: `data/TwinLabApi.kt`, `domain/Health.kt`, `ui/assets/AssetListScreen.kt`, `ui/assets/AssetListViewModel.kt`, `ui/common/StatusDot.kt`, `app/src/test/java/com/omnitex/twinlab/HealthStatusTest.kt`
- Modify: `AppContainer` (add `api`, rebuild on base-URL change), `MainActivity` nav

**Interfaces:**
- Consumes: `Device`, `Reading`, `Alert`, `Bounds` (Task 6); `SettingsRepository` (Task 6).
- Produces:
  - `enum class Health { OK, WARNING, CRITICAL, UNKNOWN }`
  - `fun healthStatus(readings: Map<String, Double>, thresholds: Map<String, Bounds>): Health`
  - `class TwinLabApi(private val baseUrl: String)` with: `suspend fun getDevices(): List<Device>`, `suspend fun getLastKnown(id: String): Map<String, Reading>`, `suspend fun getReadings(id: String, sensor: String, limit: Int = 50, rangeHours: Int = 24): List<Reading>`, `suspend fun getAlerts(limit: Int = 50): List<Alert>`, `suspend fun getDeviceAlerts(id: String, limit: Int = 50): List<Alert>`, `suspend fun registerPushToken(token: String)`, `suspend fun unregisterPushToken(token: String)`.
  - `AppContainer.api: StateFlow<TwinLabApi?>` — non-null once a base URL is set, rebuilt when it changes.

- [ ] **Step 1: Write the failing test — `HealthStatusTest.kt`**

```kotlin
package com.omnitex.twinlab

import com.omnitex.twinlab.data.Bounds
import com.omnitex.twinlab.domain.Health
import com.omnitex.twinlab.domain.healthStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusTest {
    private val thr = mapOf(
        "temperature" to Bounds(min = null, max = 40.0),
        "load_current" to Bounds(min = 0.0, max = 30.0),
        "humidity" to Bounds(min = 10.0, max = 85.0),
    )

    @Test fun noReadings_isUnknown() =
        assertEquals(Health.UNKNOWN, healthStatus(emptyMap(), thr))

    @Test fun allWithinBounds_isOk() =
        assertEquals(Health.OK, healthStatus(mapOf("temperature" to 35.0, "load_current" to 18.0), thr))

    @Test fun temperatureOverMax_isCritical() =
        assertEquals(Health.CRITICAL, healthStatus(mapOf("temperature" to 96.0), thr))

    @Test fun loadCurrentOverMax_isCritical() =
        assertEquals(Health.CRITICAL, healthStatus(mapOf("load_current" to 55.0), thr))

    @Test fun humidityOverMax_isWarning() =
        assertEquals(Health.WARNING, healthStatus(mapOf("humidity" to 95.0), thr))

    @Test fun sensorWithoutThreshold_isIgnored() =
        assertEquals(Health.OK, healthStatus(mapOf("vibration" to 9.9), thr))
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.omnitex.twinlab.HealthStatusTest"`
Expected: FAIL — `healthStatus` / `Health` unresolved.

- [ ] **Step 3: Implement `domain/Health.kt`**

```kotlin
package com.omnitex.twinlab.domain

import com.omnitex.twinlab.data.Bounds

enum class Health { OK, WARNING, CRITICAL, UNKNOWN }

private val CRITICAL_SENSORS = setOf("temperature", "load_current")

/** Mirrors backend/alerts.py evaluate(): a reading past max/min on a critical
 *  sensor → CRITICAL, on any other → WARNING; no readings → UNKNOWN. */
fun healthStatus(readings: Map<String, Double>, thresholds: Map<String, Bounds>): Health {
    if (readings.isEmpty()) return Health.UNKNOWN
    var worst = Health.OK
    for ((sensor, value) in readings) {
        val b = thresholds[sensor] ?: continue
        val breached = (b.max != null && value > b.max) || (b.min != null && value < b.min)
        if (!breached) continue
        val sev = if (sensor in CRITICAL_SENSORS) Health.CRITICAL else Health.WARNING
        if (sev == Health.CRITICAL) return Health.CRITICAL
        worst = Health.WARNING
    }
    return worst
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.omnitex.twinlab.HealthStatusTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Implement `data/TwinLabApi.kt`**

```kotlin
package com.omnitex.twinlab.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class TwinLabApi(private val baseUrl: String) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    suspend fun getDevices(): List<Device> = client.get("$baseUrl/devices").body()
    suspend fun getLastKnown(id: String): Map<String, Reading> = client.get("$baseUrl/devices/$id/last-known").body()
    suspend fun getReadings(id: String, sensor: String, limit: Int = 50, rangeHours: Int = 24): List<Reading> =
        client.get("$baseUrl/devices/$id/readings?sensor=$sensor&limit=$limit&range_hours=$rangeHours").body()
    suspend fun getAlerts(limit: Int = 50): List<Alert> = client.get("$baseUrl/alerts?limit=$limit").body()
    suspend fun getDeviceAlerts(id: String, limit: Int = 50): List<Alert> =
        client.get("$baseUrl/devices/$id/alerts?limit=$limit").body()

    suspend fun registerPushToken(token: String) {
        client.post("$baseUrl/push/register") { contentType(ContentType.Application.Json); setBody(mapOf("token" to token)) }
    }
    suspend fun unregisterPushToken(token: String) { client.delete("$baseUrl/push/register/$token") }

    fun close() = client.close()
}
```

Note: `getLastKnown` returns `/devices/{id}/last-known` which is `{sensor: {value, unit, ts}}` — matches `Map<String, Reading>` given `Reading`'s defaulted `device_id`/`sensor`.

- [ ] **Step 6: `AppContainer` — reactive api**

```kotlin
class AppContainer(app: Application) {
    private val scope = MainScope()
    val settings = SettingsRepository(app)
    val api: StateFlow<TwinLabApi?> = settings.baseUrl
        .map { url -> url?.let { TwinLabApi(it) } }
        .stateIn(scope, SharingStarted.Eagerly, null)
}
```

- [ ] **Step 7: `ui/assets/AssetListViewModel.kt`**

```kotlin
class AssetListViewModel(private val apiFlow: StateFlow<TwinLabApi?>) : ViewModel() {
    data class Row(val device: Device, val readings: Map<String, Double>, val health: Health)
    sealed interface UiState {
        data object Loading : UiState
        data class Content(val groups: Map<String, List<Row>>) : UiState   // key = plant/location
        data class Error(val msg: String) : UiState
    }
    val state: StateFlow<UiState>  // MutableStateFlow, starts Loading

    // on init and every 5s while active: api.getDevices() (cache), then api.getLastKnown(id) per device,
    // build Row(device, readings, healthStatus(readings, device.thresholds)), group by (device.plant ?: device.location),
    // emit Content. On exception emit Error(message).
    fun refresh()
}
```

Implement the poll with a `viewModelScope.launch { while (isActive) { load(); delay(5000) } }` started in `init`, cancelled with the ViewModel.

- [ ] **Step 8: `ui/assets/AssetListScreen.kt` + `ui/common/StatusDot.kt`**

`StatusDot(health: Health)` → a small colored circle (OK green, WARNING amber, CRITICAL red, UNKNOWN grey).
`AssetListScreen` → `Scaffold` with a top bar (title "TwinLab", actions: Alerts, Settings), body is a `LazyColumn` of plant group headers + device rows (`StatusDot` + name + key reading summary). Row click → `onOpen(deviceId)`. Pull-to-refresh calls `vm.refresh()`.

- [ ] **Step 9: Wire nav in `MainActivity`** — `AssetListScreen` at `Routes.ASSETS`, click → `nav.navigate(Routes.detail(id))`, actions → `Routes.ALERTS` / `Routes.SETTINGS`. Add the `AssetListViewModel` to the `AppContainer` factory.

- [ ] **Step 10: Build, run, manual check**

Expected: with backend + simulator running, the list shows the seeded NFL devices + `TL-01`, grouped by plant, status dots reflecting live values, refreshing every ~5 s.

- [ ] **Step 11: `ui/alerts/AlertsScreen.kt` + `ui/alerts/AlertsViewModel.kt` (global alert feed — spec §5.4)**

```kotlin
class AlertsViewModel(private val apiFlow: StateFlow<TwinLabApi?>) : ViewModel() {
    sealed interface UiState {
        data object Loading : UiState
        data class Content(val alerts: List<Alert>) : UiState
        data class Error(val msg: String) : UiState
    }
    val state: StateFlow<UiState>   // MutableStateFlow(Loading); load api.getAlerts(100) on init + refresh()
    fun refresh()
}
```

`AlertsScreen` — `Scaffold` (top bar "Alerts", back), body `LazyColumn` of alert cards: severity color strip, `message_en` primary, `message_ur` secondary, relative time from `created_at`, `📲` if `push_sent`. Pull-to-refresh → `vm.refresh()`. Card click → `onOpen(alert.device_id)`.

- [ ] **Step 12: Wire `Routes.ALERTS`** in `MainActivity` (`composable(Routes.ALERTS){ AlertsScreen(...) }`), reachable from the asset-list top-bar action and from a notification tap that carries no `deviceId`. Add `AlertsViewModel` to the `AppContainer` factory.

- [ ] **Step 13: Build, run, manual check**

Expected: Alerts action from the asset list opens a feed of recent alerts across all devices, newest first; tapping one opens that device's detail screen.

- [ ] **Step 14: Commit**

```bash
git add android/app
git commit -m "Android: TwinLabApi, health status (+tests), asset list + alerts screens"
```

---

## Task 8: DeviceSocket + asset detail (no 3D yet)

**Files:**
- Create: `data/DeviceSocket.kt`, `ui/detail/AssetDetailScreen.kt`, `ui/detail/AssetDetailViewModel.kt`, `ui/common/Gauge.kt`, `ui/common/Sparkline.kt`, `app/src/test/java/com/omnitex/twinlab/WsMessageParsingTest.kt`
- Modify: `AppContainer` factory, `MainActivity` nav

**Interfaces:**
- Consumes: `TwinLabApi` (Task 7), `SettingsRepository.baseUrl`, `Reading`/`Alert` (Task 6), `healthStatus` (Task 7).
- Produces:
  - `sealed interface WsMessage { data class Live(val reading: Reading) : WsMessage; data class AlertMsg(val alert: Alert) : WsMessage }`
  - `fun parseWsMessage(json: String): WsMessage?` — returns `AlertMsg` if the object has `"type":"alert"`, else `Live`, else `null`.
  - `class DeviceSocket(private val baseUrl: String, private val deviceId: String)` with `fun messages(): Flow<WsMessage>` — connects to `ws(s)://<host>/ws/<deviceId>`, emits parsed messages, reconnects after 3 s on close/error.
  - `AssetDetailViewModel` exposing `StateFlow<DetailUiState>` where `DetailUiState` carries `device: Device?`, `readings: Map<String,Double>`, `history: Map<String, List<Reading>>`, `alerts: List<Alert>`, `health: Health`, `lastMsgAgeMs: Long`.

- [ ] **Step 1: Write the failing test — `WsMessageParsingTest.kt`**

```kotlin
package com.omnitex.twinlab

import com.omnitex.twinlab.data.WsMessage
import com.omnitex.twinlab.data.parseWsMessage
import org.junit.Assert.*
import org.junit.Test

class WsMessageParsingTest {
    // shape from backend/main.py _on_mqtt_message broadcast
    private val liveJson = """{"device_id":"TL-01","sensor":"temperature","value":42.5,"unit":"C","ts":1734000000000}"""
    // shape from backend/main.py _persist_alert: _json_safe(alert) + {"type":"alert"}
    private val alertJson = """{"type":"alert","device_id":"TL-01","sensor":"temperature","alert_type":"threshold","severity":"critical","value":96.1,"unit":"C","detail":"above max 40","message_en":"...","message_ur":"...","ts":1734000000000,"created_at":"2026-09-07T10:00:00+00:00"}"""

    @Test fun parsesLiveReading() {
        val m = parseWsMessage(liveJson)
        assertTrue(m is WsMessage.Live)
        assertEquals(42.5, (m as WsMessage.Live).reading.value, 0.001)
    }

    @Test fun parsesAlert() {
        val m = parseWsMessage(alertJson)
        assertTrue(m is WsMessage.AlertMsg)
        assertEquals("critical", (m as WsMessage.AlertMsg).alert.severity)
    }

    @Test fun garbageReturnsNull() = assertNull(parseWsMessage("not json"))
}
```

- [ ] **Step 2: Run it, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.omnitex.twinlab.WsMessageParsingTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `data/DeviceSocket.kt`**

```kotlin
package com.omnitex.twinlab.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

sealed interface WsMessage {
    data class Live(val reading: Reading) : WsMessage
    data class AlertMsg(val alert: Alert) : WsMessage
}

private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseWsMessage(json: String): WsMessage? = try {
    val obj = lenient.parseToJsonElement(json).jsonObject
    if (obj["type"]?.jsonPrimitive?.contentOrNull == "alert")
        WsMessage.AlertMsg(lenient.decodeFromJsonElement(Alert.serializer(), obj))
    else
        WsMessage.Live(lenient.decodeFromJsonElement(Reading.serializer(), obj))
} catch (e: Exception) { null }

class DeviceSocket(private val baseUrl: String, private val deviceId: String) {
    private val client = HttpClient(OkHttp) { install(WebSockets) }

    fun messages(): Flow<WsMessage> = flow {
        val wsUrl = baseUrl.replaceFirst("http", "ws").trimEnd('/') + "/ws/$deviceId"
        while (true) {
            try {
                client.webSocket(wsUrl) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) parseWsMessage(frame.readText())?.let { emit(it) }
                    }
                }
            } catch (e: Exception) { /* fall through to reconnect */ }
            delay(3000)
        }
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.omnitex.twinlab.WsMessageParsingTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: `ui/detail/AssetDetailViewModel.kt`**

```kotlin
class AssetDetailViewModel(
    private val deviceId: String,
    private val apiFlow: StateFlow<TwinLabApi?>,
    private val baseUrlFlow: StateFlow<String?>,
) : ViewModel() {
    data class DetailUiState(
        val device: Device? = null,
        val readings: Map<String, Double> = emptyMap(),
        val history: Map<String, List<Reading>> = emptyMap(),
        val alerts: List<Alert> = emptyList(),
        val health: Health = Health.UNKNOWN,
        val lastMsgAgeMs: Long = Long.MAX_VALUE,
        val error: String? = null,
    )
    val state: StateFlow<DetailUiState>  // MutableStateFlow(DetailUiState())

    // init:
    //  - load device (from api.getDevices().find { it.device_id == deviceId }), api.getDeviceAlerts(deviceId),
    //    and api.getReadings(deviceId, sensor) for each of device.sensors → history
    //  - collect DeviceSocket(baseUrl, deviceId).messages():
    //      Live  → readings[sensor]=value; recompute health; lastMsg = now
    //      AlertMsg → prepend to alerts
    //  - a 1s ticker updates lastMsgAgeMs = now - lastMsg
}
```

- [ ] **Step 6: `ui/common/Gauge.kt` + `Sparkline.kt`**

`Gauge(label, value, unit, health)` — a labelled numeric readout with a colored bar. `Sparkline(points: List<Double>)` — a `Canvas` polyline. Both plain Compose, no libraries.

- [ ] **Step 7: `ui/detail/AssetDetailScreen.kt`**

`Scaffold` (top bar = device name + back). Body `LazyColumn`:
1. a placeholder `Box` where the 3D twin goes (Task 10 fills it) — for now a `Text("3D twin — Task 10")`.
2. health status chip (from `state.health`, `state.lastMsgAgeMs` → show "SIGNAL LOST" if `> 15000`).
3. `Gauge` per current reading.
4. `Sparkline` per sensor from `state.history`.
5. "Recent alerts" list from `state.alerts`.

- [ ] **Step 8: Wire nav** — `composable(Routes.DETAIL)` reads `deviceId` arg, builds `AssetDetailViewModel` via the factory. Add to `AppContainer` factory.

- [ ] **Step 9: Build, run, manual check**

Expected: tap a device → detail screen, gauges update live from the WS, sparklines render, alerts list populated. Kill backend → "SIGNAL LOST" after ~15 s; restart → recovers.

- [ ] **Step 10: Commit**

```bash
git add android/app
git commit -m "Android: DeviceSocket + WS parsing (+tests), asset detail screen"
```

---

## Task 9: Firebase Messaging — push notifications

**Files:**
- Create: `push/TwinLabMessagingService.kt`, `push/Notifications.kt`
- Modify: `android/build.gradle.kts` (google-services classpath), `android/app/build.gradle.kts` (plugin + Firebase deps), `AndroidManifest.xml`, `MainActivity` (permission + token registration + deep-link handling), `AppContainer`

**Interfaces:**
- Consumes: `TwinLabApi.registerPushToken` (Task 7); `Routes.detail` (Task 5).
- Produces: FCM token POSTed to `/push/register` on `onNewToken` and on app start. Notifications on channel `"twinlab_alerts"`. Tapping a notification opens `MainActivity` with an intent extra `"deviceId"` → nav to `Routes.detail(id)`.

- [ ] **Step 1: Add the Firebase prerequisite check**

Confirm `android/app/google-services.json` exists (Arham drops it in). If absent, stop and ask.

- [ ] **Step 2: Gradle wiring**

`android/build.gradle.kts` (project) — add to `plugins { }` or `buildscript`:
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```
`android/app/build.gradle.kts` — add plugin + deps:
```kotlin
plugins {
    // ...existing
    id("com.google.gms.google-services")
}
dependencies {
    // ...existing
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-messaging")
}
```

- [ ] **Step 3: `push/Notifications.kt`**

```kotlin
package com.omnitex.twinlab.push

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.omnitex.twinlab.MainActivity
import com.omnitex.twinlab.R

const val ALERT_CHANNEL = "twinlab_alerts"

fun ensureChannel(ctx: Context) {
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        val ch = NotificationChannel(ALERT_CHANNEL, "TwinLab Alerts", NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

fun showAlertNotification(ctx: Context, title: String, body: String, deviceId: String?) {
    ensureChannel(ctx)
    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("deviceId", deviceId)
    }
    val pi = PendingIntent.getActivity(
        ctx, deviceId?.hashCode() ?: 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val n = NotificationCompat.Builder(ctx, ALERT_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title).setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true).setContentIntent(pi).build()
    NotificationManagerCompat.from(ctx).notify(deviceId?.hashCode() ?: 1, n)
}
```

- [ ] **Step 4: `push/TwinLabMessagingService.kt`**

```kotlin
package com.omnitex.twinlab.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.omnitex.twinlab.TwinLabApp
import kotlinx.coroutines.*

class TwinLabMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            (application as TwinLabApp).container.api.value?.registerPushToken(token)
        }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: "TwinLab alert"
        val body  = msg.notification?.body ?: msg.data["message_ur"] ?: ""
        showAlertNotification(this, title, body, msg.data["device_id"])
    }
}
```

- [ ] **Step 5: Manifest**

`AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
...
<service
    android:name=".push.TwinLabMessagingService"
    android:exported="false">
    <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
</service>
```

- [ ] **Step 6: MainActivity — permission + token + deep-link**

- On create (API ≥ 33): request `POST_NOTIFICATIONS` with `rememberLauncherForActivityResult`; app proceeds regardless of the answer.
- After a base URL is set: `FirebaseMessaging.getInstance().token.addOnSuccessListener { scope.launch { container.api.value?.registerPushToken(it) } }`.
- Read `intent.getStringExtra("deviceId")` on create and in `onNewIntent`; if present, `nav.navigate(Routes.detail(id))`.

- [ ] **Step 7: Build, run, manual check (needs Firebase prerequisite done)**

Expected: app starts, logcat shows a token, `push_tokens` in Mongo gains that token. Inject an overheat alert (backend) → notification appears on the phone (foreground and background). Tap it → opens that asset's detail screen.

- [ ] **Step 8: Commit**

```bash
git add android/
git commit -m "Android: FCM push notifications, token registration, deep-link"
```

---

## Task 10: 3D digital twin

**Files:**
- Create: `domain/TwinMapping.kt`, `ui/detail/TwinView.kt`, `app/src/main/assets/twin_rig.glb`, `app/src/test/java/com/omnitex/twinlab/TwinMappingTest.kt`
- Modify: `ui/detail/AssetDetailScreen.kt` (replace the placeholder box), `AssetDetailViewModel` (expose `TwinState`)

**Interfaces:**
- Consumes: `DetailUiState.readings`, `device.thresholds`, `DetailUiState.lastMsgAgeMs` (Task 8); `healthStatus` (Task 7).
- Produces:
  - `data class TwinState(val bodyColorArgb: Int, val shakeAmplitude: Float, val rotorRpm: Float, val statusRingArgb: Int, val stale: Boolean)`
  - `object TwinMapping { fun stateFrom(readings: Map<String, Double>, thresholds: Map<String, Bounds>, lastMsgAgeMs: Long): TwinState }`
  - Tunable constants (top of `TwinMapping.kt`): `SHAKE_GAIN`, `SHAKE_MAX`, `TEMP_COLOR_MIN`, `TEMP_COLOR_MAX`, `RUNNING_RPM`, `IDLE_RPM`, `RUN_EPS`, `STALE_MS`.

- [ ] **Step 1: Write the failing test — `TwinMappingTest.kt`**

```kotlin
package com.omnitex.twinlab

import com.omnitex.twinlab.data.Bounds
import com.omnitex.twinlab.domain.TwinMapping
import org.junit.Assert.*
import org.junit.Test

class TwinMappingTest {
    private val thr = mapOf("temperature" to Bounds(null, 40.0), "load_current" to Bounds(0.0, 30.0))

    @Test fun coolTemp_isGreenish() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 10.0), thr, 0)
        // green channel dominates
        assertTrue(android.graphics.Color.green(s.bodyColorArgb) > android.graphics.Color.red(s.bodyColorArgb))
    }

    @Test fun hotTemp_isReddish() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 60.0), thr, 0)
        assertTrue(android.graphics.Color.red(s.bodyColorArgb) > android.graphics.Color.green(s.bodyColorArgb))
    }

    @Test fun highVibration_shakesButClamped() {
        val s = TwinMapping.stateFrom(mapOf("vibration" to 999.0), thr, 0)
        assertEquals(TwinMapping.SHAKE_MAX, s.shakeAmplitude, 0.0001f)
    }

    @Test fun noVibration_noShake() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 20.0), thr, 0)
        assertEquals(0f, s.shakeAmplitude, 0.0001f)
    }

    @Test fun runningWhenLoadCurrentHigh() {
        val s = TwinMapping.stateFrom(mapOf("load_current" to 18.0), thr, 0)
        assertEquals(TwinMapping.RUNNING_RPM, s.rotorRpm, 0.01f)
    }

    @Test fun staleWhenOld() {
        val s = TwinMapping.stateFrom(mapOf("temperature" to 20.0), thr, TwinMapping.STALE_MS + 1)
        assertTrue(s.stale)
    }
}
```

> `android.graphics.Color` static methods (`red`/`green`/`blue`/`argb`) are available in JVM unit tests when `testOptions.unitTests.isReturnDefaultValues = false` and the `android.graphics.Color` shadow is present — if the test runner returns 0 for these, switch the assertions to compare against a `TwinMapping`-exposed `fun channels(argb): Triple<Int,Int,Int>` helper implemented with bit-shifts. Decide during implementation; keep the *intent* (cool=green, hot=red).

- [ ] **Step 2: Run it, verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.omnitex.twinlab.TwinMappingTest"`
Expected: FAIL — `TwinMapping` unresolved.

- [ ] **Step 3: Implement `domain/TwinMapping.kt`**

```kotlin
package com.omnitex.twinlab.domain

import com.omnitex.twinlab.data.Bounds

data class TwinState(
    val bodyColorArgb: Int,
    val shakeAmplitude: Float,   // metres of jitter
    val rotorRpm: Float,
    val statusRingArgb: Int,
    val stale: Boolean,
)

object TwinMapping {
    // --- tunable knobs (guesses until real sensor magnitudes are known) ---
    const val SHAKE_GAIN = 0.01f
    const val SHAKE_MAX = 0.05f
    const val TEMP_COLOR_MIN = 20.0    // fallback band when a sensor has no threshold
    const val TEMP_COLOR_MAX = 80.0
    const val RUNNING_RPM = 900f
    const val IDLE_RPM = 60f
    const val RUN_EPS = 0.02          // vibration above this ⇒ "running"
    const val STALE_MS = 15_000L

    private fun lerpColor(t: Float): Int {
        val c = t.coerceIn(0f, 1f)
        val r = (c * 255).toInt()
        val g = ((1 - c) * 200 + 55).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x30
    }

    fun stateFrom(readings: Map<String, Double>, thresholds: Map<String, Bounds>, lastMsgAgeMs: Long): TwinState {
        val temp = readings["temperature"]
        val band = thresholds["temperature"]
        val lo = band?.min ?: TEMP_COLOR_MIN
        val hi = band?.max ?: TEMP_COLOR_MAX
        val bodyColor = if (temp == null) 0xFF888888.toInt()
            else lerpColor(((temp - lo) / (hi - lo)).toFloat())

        val vib = readings["vibration"] ?: 0.0
        val shake = (vib * SHAKE_GAIN).toFloat().coerceIn(0f, SHAKE_MAX)

        val rpm = when {
            (readings["load_current"] ?: 0.0) > 2.0 -> RUNNING_RPM
            vib > RUN_EPS -> RUNNING_RPM
            else -> IDLE_RPM
        }

        val ring = when (healthStatus(readings, thresholds)) {
            Health.CRITICAL -> 0xFFE53935.toInt()
            Health.WARNING  -> 0xFFFFB300.toInt()
            Health.OK       -> 0xFF43A047.toInt()
            Health.UNKNOWN  -> 0xFF9E9E9E.toInt()
        }

        return TwinState(bodyColor, shake, rpm, ring, lastMsgAgeMs > STALE_MS)
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.omnitex.twinlab.TwinMappingTest"`
Expected: PASS (6 tests). If the `android.graphics.Color` assertions return 0, apply the fallback from Step 1's note.

- [ ] **Step 5: Add the model asset**

Obtain `twin_rig.glb` — a CC0 machine model (Poly Haven / Sketchfab CC0) or a 15-min Blender build: a box body + a named `rotor` cylinder on the front face. < 500 KB. Place at `app/src/main/assets/twin_rig.glb`. Commit it (it's a source asset, not a build output).

- [ ] **Step 6: `ui/detail/TwinView.kt`**

```kotlin
@Composable
fun TwinView(state: TwinState, modifier: Modifier = Modifier) {
    // io.github.sceneview.Scene { ... }
    //  - engine, load "twin_rig.glb" from assets once (remember)
    //  - onFrame: set body material baseColorFactor from state.bodyColorArgb;
    //             offset model node position by a random vec * state.shakeAmplitude;
    //             rotate the "rotor" child node by state.rotorRpm/60 * frameDeltaSeconds * 360;
    //             a ring/plane node under the model tinted state.statusRingArgb
    //  - if state.stale: overlay a "SIGNAL LOST" Text and desaturate
    //  - camera: fixed distance, slow auto-rotate; enable pinch + drag
    //  - wrap Scene load in try/catch → on failure emit a callback so the screen shows card-only
}
```

- [ ] **Step 7: Wire into the detail screen**

`AssetDetailViewModel` — add `val twin: StateFlow<TwinState>` derived from `state` via `TwinMapping.stateFrom(readings, device?.thresholds ?: emptyMap(), lastMsgAgeMs)`.
`AssetDetailScreen` — replace the Task 8 placeholder `Box` with `TwinView(twinState)`; on a `TwinView` load-failure callback, hide it and keep the gauges/card.

- [ ] **Step 8: Build, run, manual check**

Expected: detail screen shows the rotating 3D rig. Inject overheat → body reddens + shakes, status ring goes red, within the WS latency. Kill backend → "SIGNAL LOST" overlay. Force a model-load failure (rename the asset) → screen still shows gauges + card.

- [ ] **Step 9: Commit**

```bash
git add android/app
git commit -m "Android: 3D digital twin — TwinMapping (+tests), SceneView TwinView"
```

---

## Task 11: Documentation

**Files:**
- Create: `phase/phase-14.md`
- Modify: `CLAUDE.md`, `phase/limitations.md`, `README.md`, `Obsidian Vault/Hackathons/TwinLab/TwinLab_Identity.md`

**Interfaces:** none (docs).

- [ ] **Step 1: `phase/phase-14.md`** — follow the CLAUDE.md §7 template. Goal (Twilio out, FCM in, Android app v1). Structure & steps (this plan's task list, condensed). Start commands (the §D commands from the spec incl. `uvicorn --host 0.0.0.0`, plus `open android/ in Android Studio`). Expected outcome (the spec §8 acceptance checks). Leave the `## ✅ Actually achieved` section as a stub comment.

- [ ] **Step 2: `CLAUDE.md`**
- §1: the owner "receives WhatsApp alerts only" → "receives push notifications on the TwinLab Android app". Add one line: the Android app is the buyer surface.
- §2 tech table: add an "App" row — native Kotlin + Compose, `android/`, Ktor, SceneView, FCM. Change "Alerts" row: drop Twilio/63007, → "FCM push to the TwinLab Android app, bilingual + rupee-anchored".
- §2 AI policy paragraph: unchanged.
- §3: add `--host 0.0.0.0` to the uvicorn command; add an "Android" subsection (open in Android Studio, `google-services.json`, Settings URL).
- §6: add "Don't reintroduce Twilio / WhatsApp sending — the alert transport is FCM push now."
- §8 roadmap table: add Phase 14 row (Twilio removal + Android app v1), status ✅ once "Actually achieved" is written.
- footer "Last updated".

- [ ] **Step 3: `phase/limitations.md`** — remove / archive the Twilio 63007 section (the error dies with Twilio). Add a short note: push delivery depends on Google Play services + internet on the device; latency is seconds, not guaranteed instant; the in-app WebSocket alert is immediate.

- [ ] **Step 4: `README.md`** — wherever it says WhatsApp alerting, → "push notifications via the TwinLab Android app". Add a one-line "Android app" bullet pointing at `android/`.

- [ ] **Step 5: `TwinLab_Identity.md`** (Obsidian vault) — §5 one-line pitch and §6 "Is:" bullet: "bilingual WhatsApp-first alerting" → "bilingual push alerting via the TwinLab app". §7 claim #4: "Roman Urdu WhatsApp is the first-class buyer surface" → "The TwinLab app (Roman Urdu push + monitoring) is the buyer surface". Add a revision note at the top dated 2026-09-07.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md phase/ README.md
git commit -m "Docs: Phase 14 — Twilio out, FCM push + Android app; update identity/pitch surface"
```

(Commit the Obsidian vault file separately if it's a different repo — check `git -C "<vault path>" status`.)

---

## Self-Review

**Spec coverage:**
- §2.1 Twilio removal → Task 1 ✓
- §2.1 `push.py` FCM → Task 4 ✓
- §2.1 `/push/register` → Task 3 ✓
- §2.1 `GET /alerts` → Task 2 ✓
- §2.1 `push_sent` rename + `AlertsPanel.jsx` → Task 1 ✓
- §2.1 docs → Task 11 ✓
- §5.1 project scaffold → Task 5 ✓
- §5.2 dependencies → Task 5 (+ Firebase in Task 9) ✓
- §5.3 `SettingsRepository` → Task 6 ✓; `TwinLabApi` → Task 7 ✓; `DeviceSocket` → Task 8 ✓; models → Task 6 ✓
- §5.4 Settings screen → Task 6 ✓; Asset list → Task 7 ✓; Asset detail → Task 8 ✓; Alerts screen → **GAP, see below**
- §5.5 status derivation → Task 7 ✓
- §5.6 FCM → Task 9 ✓
- §6 3D twin → Task 10 ✓
- §7 tests → Tasks 7, 8, 10 ✓
- §8 acceptance → covered by per-task manual checks + Task 11 phase doc

**Gap found & fixed:** the spec's §5.4 **Alerts screen** (global feed) had no task on first draft — added as Task 7 Steps 11–14 (its ViewModel uses the same `apiFlow` and the `/alerts` endpoint from Task 2).

**Placeholder scan:** no "TBD"/"handle errors appropriately" — error handling is spelled out (Error states in each UiState, `try/catch` shown, `parseWsMessage` returns null). The `TwinView.kt` and Compose screen bodies are described as structured comment-specs with exact inputs/outputs rather than full pixel code — acceptable per "skilled developer, knows the toolset"; every *interface* they consume/produce is concrete.

**Type consistency:** `TwinLabApi` method names identical across Tasks 7/8/9. `Health` enum values (`OK/WARNING/CRITICAL/UNKNOWN`) consistent Tasks 7/10. `WsMessage.Live`/`WsMessage.AlertMsg` consistent Tasks 8/10. `TwinState` fields (`bodyColorArgb`, `shakeAmplitude`, `rotorRpm`, `statusRingArgb`, `stale`) consistent Task 10 test ↔ impl ↔ TwinView. `push.send_alert(alert, device, tokens)` signature consistent Task 4 ↔ `main.py` call.
