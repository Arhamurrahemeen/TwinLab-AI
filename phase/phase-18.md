# Phase 18 — App-wide theme refresh + launcher icon fix

## Goal

Arham's read on the Android app: white background, inconsistent font, "looks bad."
Give it a consistent, soothing theme across every screen without adding screens or
taps — plus zoom out the launcher icon, which sits too tight against its adaptive-icon
mask on his device.

## Structure & steps

1. Drafted the theme as a Claude Design mockup (asset list + alerts, the two screens
   carrying the most content) before touching code. Key finding while mocking: the
   English and Roman Urdu alert strings are near-duplicates by design (`alerts.py`'s
   `_make_alert` — same template, first word swapped: WARNING/KHABARDAR,
   CRITICAL/KHATRNAK), which is a real contributor to the "inconsistent" feel. Fixed
   the presentation (small "UR" tag, de-emphasized), left the actual bilingual copy
   alone — rewriting real Roman Urdu translations is a backend content change, out of
   scope here.
2. Ported into Compose:
   - `Color.kt`: background `#F6F9FA` → `#F1F6F5` (warmer, less stark-white).
   - New `ui/common/TopBar.kt` (`TwinLabTopBar`): shared app bar with a soft drop
     shadow (`Modifier.shadow`, replacing the flat white-on-white look) and explicit
     navy/teal icon tinting instead of default M3 grey. Applied to all four screens
     (asset list, alerts, detail, settings) — one shared composable, not four
     divergent `TopAppBar` calls.
   - `StatusDot.kt`: added a soft translucent glow ring around each status dot,
     echoing the digital twin's neon-rim status language from Phase 17.
   - `AssetListScreen.kt`: device rows are now `Card`s (rounded, soft shadow)
     instead of flat `Row` + `HorizontalDivider`.
   - `AlertsScreen.kt`: full card redesign — a colored severity icon chip (▲ warning
     / ⊗ critical) + pill badge + device id, the message, a thin divider, the Roman
     Urdu line under a small "UR" tag instead of an equal-weight second paragraph,
     and a proper send icon + "Pushed to device" instead of the 📲 emoji.
3. Launcher icon: both `ic_launcher_foreground.png` (all 5 density buckets) had the
   mark filling ~66% of the 108dp safe canvas with no zoom-out margin — scaled the
   artwork to 80%, recentered, via a one-off Pillow script (not committed, just run
   once against the PNGs in place).

## Start commands

```powershell
docker compose up -d
.venv\Scripts\python ingestion.py
.venv\Scripts\python simulator.py
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000

cd android
$env:JAVA_HOME = "C:\Users\Arham\.jdks\jbr-21.0.11"
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Expected outcome

- [ ] All four screens share the same top bar treatment (shadow, tinted icons, Lora
      title).
- [ ] Asset list and alerts render as elevated cards on the warmer background, not
      flat rows on white.
- [ ] Launcher icon has visible breathing room on a real device home screen.
- [ ] No new screens, no new taps — same `Routes` as before.

---
## ✅ Actually achieved

All of the above, verified live on the connected device (`adb install` + screenshot,
not just Android Studio):

- Clean `compileDebugKotlin` + `testDebugUnitTest` (one fixed import — `RectangleShape`
  is `androidx.compose.ui.graphics`, not `...foundation.shape`).
- Screenshotted all four real screens post-install: asset list and alerts both show
  the card treatment, glow-ring status dots, and tinted icons exactly as mocked;
  detail and settings pick up the shared top bar and background automatically since
  they route through the same `TwinLabTopBar`/theme tokens — no separate mockup was
  needed for those two.
- Launcher icon re-verified visually — noticeably more margin now.
- One environment note carried over from Phase 17: `adb install -r` on this device
  intermittently drops the saved backend URL (DataStore), landing on "Could not load
  assets: No backend configured" after install — re-entering Settings → Test & Save
  fixes it immediately. Not an app bug as far as could be determined this session;
  just a reminder for next time so it isn't mistaken for one.
