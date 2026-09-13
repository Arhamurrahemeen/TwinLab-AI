# twin_rig.glb (optional upgrade)

The current `TwinView` draws the digital twin with Compose Canvas (2D, stylised) —
it needs **no model file** and always renders.

To upgrade to a real 3D twin later:

1. Add `implementation("io.github.sceneview:sceneview:2.2.1")` back to
   `app/build.gradle.kts`.
2. Drop a `twin_rig.glb` here — a CC0 generator/machine model (Poly Haven,
   Sketchfab CC0) or a 15-min Blender build: a box body + a child node named
   `rotor`. Keep it under ~500 KB.
3. Replace `TwinView`'s Canvas with an `io.github.sceneview.Scene { }`:
   tint the body material from `TwinState.bodyColorArgb`, spin the `rotor`
   node at `TwinState.rotorRpm`, offset the model by `TwinState.shakeAmplitude`,
   tint a ground plane from `TwinState.statusRingArgb`, and show the
   "SIGNAL LOST" overlay when `TwinState.stale`.

`TwinMapping.stateFrom()` and its unit tests do not change — only the renderer.

## Status (2026-09-13)

First real device check: app connected over LAN, dashboard + per-asset live
detail both confirmed updating correctly. **The 2D Compose Canvas twin itself
looks bad on a real phone** (Arham's words: "looks awful") — functional, not
presentable. Confirmed deferred: real 3D twin work (the upgrade path above)
is a future task, not urgent now.
