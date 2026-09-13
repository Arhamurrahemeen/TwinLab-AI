# Phase 17 — Digital twin visual redesign, ported to Compose

## Goal

The 2D digital-twin illustration (`TwinView.kt`) was confirmed working end to end
against a real device (Phase 16 area check) but "looks awful" in person — a flat
rounded-rect body with an oversized fan/asterisk overlapping its own edge. Redesign
it for real visual quality ahead of the NIC/SEIC final rounds (2 of 3 panelists are
non-technical — visuals matter more than usual), keep it 2D/Compose-Canvas (the real
3D twin stays future work per Arham's call), and port the approved design into real
Kotlin — same `TwinState` contract, no `TwinMapping.kt` changes.

## Structure & steps

1. Drafted the redesign as a Claude Design canvas (isometric 3-face shading, neon
   status rim instead of a flat color stripe, a properly-inset 5-blade fan, vent
   slats + rivets for texture) grounded in the app's real brand tokens (`Color.kt`,
   `Type.kt`) and a real device screenshot. Iterated twice on user feedback (a
   geometry bug — the fan overflowed the body — then a full style upgrade from flat
   to isometric-shaded on "right idea, weak execution" feedback).
2. Ported the approved design into `android/app/src/main/java/com/omnitex/twinlab/ui/detail/TwinView.kt`:
   - Same public signature (`TwinView(state: TwinState, modifier: Modifier)`) and the
     same `TwinState` fields consumed (`bodyColorArgb`, `shakeAmplitude`, `rotorRpm`,
     `statusRingArgb`, `stale`) — `TwinMapping.kt` untouched.
   - Three isometric faces (top/left/right) shaded from `bodyColorArgb` with fixed
     per-face darkening factors (light-from-top-left model) instead of a single flat
     fill — temperature is still communicated via color, just shaded properly now.
   - `statusRingArgb` now drives a layered-stroke "neon rim" around the top face (a
     manual glow fake — no `RenderEffect`/blur API, since minSdk 24 predates
     `RenderEffect`'s API 31 floor) plus the fan hub and ambient glow.
   - Fan: 5 tapered blades in a properly-inset grille (was 4 blunt rects overflowing
     the body), spun via the existing `rotorRpm` → duration mapping, squashed
     `scaleY` for the isometric look — transform order (static blade angle → animated
     spin → squash) verified by hand so the spin stays circular before the squash.
   - Dropped the in-graphic "SIGNAL LOST" text overlay — `AssetDetailScreen.kt`
     already prints that exact text right below `TwinView`; the two were stacking on
     screen (confirmed in an earlier screenshot). Stale now just desaturates the rim
     to grey and dims the whole canvas via `Modifier.alpha`.
   - Minor cleanup while rewriting: replaced the original per-frame `Random(...)`
     jitter with a second out-of-phase sine term (same organic look, no per-frame
     allocation).

## Start commands

```powershell
docker compose up -d
.venv\Scripts\python ingestion.py
.venv\Scripts\python simulator.py
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000

# Android — compile/test/build (JAVA_HOME must point at a JDK <= 21; the machine's
# default Android Studio JBR is JDK 25, which this Gradle/Kotlin toolchain can't parse)
cd android
$env:JAVA_HOME = "C:\Users\Arham\.jdks\jbr-21.0.11"
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Expected outcome

- [ ] `compileDebugKotlin` and `testDebugUnitTest` both pass clean, no warnings.
- [ ] Installed on a real device, the twin renders as a shaded isometric unit (not a
      flat box), fan sits fully inside its grille, spins, and its speed still tracks
      `rotorRpm`.
- [ ] Temperature still visibly changes the unit's color; health still visibly
      changes the rim/glow color; vibration still visibly jitters the whole unit.
- [ ] Only one "SIGNAL LOST" appears on screen when stale (not two).

---
## ✅ Actually achieved

All of the above, verified live against a real connected device (`adb`, not just
Android Studio):

- `gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — clean, zero warnings
  (fixed one `quadraticBezierTo` deprecation), all existing `TwinMapping` JVM tests
  still pass untouched.
- `gradlew :app:assembleDebug` + `adb install -r` — installed on the connected
  device (`RMX3830`) without needing Android Studio open.
- Live-verified via `adb shell input tap` + `adb exec-out screencap`: published a
  real MQTT reading (temperature 62°C, above `TL-B49244`'s 55°C threshold) and
  screenshotted the actual rendered twin — isometric shading, contained fan, red
  glow/rim for the critical state, single "SIGNAL LOST" text when stale (confirmed
  the duplicate is gone). A real push notification fired during this same test
  (temperature threshold alert), incidentally re-confirming Phase 14's push path
  still works.
- **Gotcha for next time:** this machine's Android Studio bundles JDK 25 as its
  default JBR, which the pinned Gradle 8.9/AGP 8.7.2/Kotlin toolchain's version
  parser chokes on (`IllegalArgumentException: 25.0.2`). `C:\Users\Arham\.jdks\jbr-21.0.11`
  works — set `JAVA_HOME` to that for any CLI Gradle invocation on this machine.
- Cleaned up test alerts via `POST /sim/reset` afterward.
