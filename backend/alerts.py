"""
TwinLab alert engine — threshold evaluation + fuel-theft rule.
Called synchronously from the MQTT handler thread; async helpers run on the main event loop.
"""

import asyncio
import logging
import time
from collections import deque
from datetime import datetime, timezone

from config import settings

log = logging.getLogger("twinlab.alerts")

# ── Config ───────────────────────────────────────────────────
COOLDOWN_S     = 600    # suppress re-fires of the same alert within 10 min
THEFT_DROP_L   = 5.0    # litres lost in window to trigger theft
THEFT_WINDOW_S = 300    # sliding window for theft detection (5 min)
OFF_AMPS       = 2.0    # load_current below this means generator is off
CACHE_TTL_S    = 60     # how often to reload thresholds from Mongo

# Max-breach on these sensors → critical; everything else → warning
_CRITICAL_MAX = {"load_current", "temperature"}

# ── Module-level state (GIL-safe for CPython dict reads/replacements) ───
_threshold_cache: dict = {}   # {device_id: {sensor: {"min": v|None, "max": v|None}}}
_cache_loaded_at: float = 0.0

_cooldown: dict = {}          # {(device_id, sensor, alert_type): fired_ts_ms}
_fuel_buf: dict = {}          # {device_id: deque of (ts_ms, fuel_level)}


# ── Cache ────────────────────────────────────────────────────

async def refresh_cache() -> None:
    """Reload all device thresholds from Mongo."""
    global _threshold_cache, _cache_loaded_at
    from db.mongo import get_db
    try:
        db  = get_db()
        docs = await db.devices.find(
            {}, {"_id": 0, "device_id": 1, "thresholds": 1}
        ).to_list(length=500)
        _threshold_cache = {d["device_id"]: d.get("thresholds") or {} for d in docs}
        _cache_loaded_at = time.time()
        log.debug(f"[alerts] cache refreshed — {len(_threshold_cache)} devices")
    except Exception as e:
        log.error(f"[alerts] cache refresh failed: {e}")


async def cache_loop() -> None:
    """Background task: keep threshold cache fresh."""
    while True:
        await refresh_cache()
        await asyncio.sleep(CACHE_TTL_S)


# ── Helpers ──────────────────────────────────────────────────

def _severity(sensor: str, side: str) -> str:
    """side = 'min' | 'max'"""
    if side == "max" and sensor in _CRITICAL_MAX:
        return "critical"
    return "warning"


def _make_alert(
    device_id, sensor, alert_type, severity, value, unit, detail,
    drop_litres: float = 0.0, window_s: float = 0.0,
) -> dict:
    ts = int(time.time() * 1000)
    if alert_type == "fuel_theft":
        mins   = round(window_s / 60, 1)
        rupees = round(drop_litres * settings.diesel_price_pkr)
        msg_en = (
            f"⚠️ {device_id} — fuel dropped {drop_litres:.1f}L in {mins} min "
            f"while generator OFF. Suspected theft. Est. loss ~PKR {rupees:,}."
        )
        msg_ur = (
            f"{device_id} — generator BAND honay ke bawajood {mins} min mein "
            f"{drop_litres:.1f}L fuel kam hua. Chori ka shak. Taqreeban PKR {rupees:,} nuqsan."
        )
    elif severity == "critical":
        msg_en = f"CRITICAL: {device_id} — {sensor} = {value} {unit} ({detail})."
        msg_ur = f"KHATRNAK: {device_id} — {sensor} = {value} {unit} ({detail})."
    else:
        msg_en = f"WARNING: {device_id} — {sensor} = {value} {unit} ({detail})."
        msg_ur = f"KHABARDAR: {device_id} — {sensor} = {value} {unit} ({detail})."

    return {
        "device_id":     device_id,
        "sensor":        sensor,
        "alert_type":    alert_type,
        "severity":      severity,
        "value":         value,
        "unit":          unit,
        "detail":        detail,
        "message_en":    msg_en,
        "message_ur":    msg_ur,
        "drop_litres":   drop_litres,
        "window_s":      window_s,
        "ts":            ts,
        "whatsapp_sent": False,
        "created_at":    datetime.now(timezone.utc),
    }


def _in_cooldown(device_id: str, sensor: str, alert_type: str) -> bool:
    key  = (device_id, sensor, alert_type)
    last = _cooldown.get(key, 0)
    return (time.time() * 1000 - last) < COOLDOWN_S * 1000


def _set_cooldown(device_id: str, sensor: str, alert_type: str) -> None:
    _cooldown[(device_id, sensor, alert_type)] = time.time() * 1000


# ── Public API ───────────────────────────────────────────────

def evaluate(
    device_id: str,
    sensor: str,
    value: float,
    unit: str,
    last_known: dict,
) -> dict | None:
    """
    Evaluate one MQTT reading. Returns an alert dict if one should fire, else None.
    Called synchronously from the MQTT thread — must never block or await.
    """
    thresholds = _threshold_cache.get(device_id, {})

    # ── Threshold rule ───────────────────────────────────────
    bounds = thresholds.get(sensor)
    if bounds:
        min_v = bounds.get("min")
        max_v = bounds.get("max")

        if min_v is not None and value < min_v:
            if not _in_cooldown(device_id, sensor, "threshold"):
                _set_cooldown(device_id, sensor, "threshold")
                return _make_alert(
                    device_id, sensor, "threshold",
                    _severity(sensor, "min"),
                    value, unit, f"below min {min_v}",
                )

        elif max_v is not None and value > max_v:
            if not _in_cooldown(device_id, sensor, "threshold"):
                _set_cooldown(device_id, sensor, "threshold")
                return _make_alert(
                    device_id, sensor, "threshold",
                    _severity(sensor, "max"),
                    value, unit, f"above max {max_v}",
                )

    # ── Fuel-theft rule ──────────────────────────────────────
    if sensor == "fuel_level":
        buf    = _fuel_buf.setdefault(device_id, deque())
        ts_now = int(time.time() * 1000)
        buf.append((ts_now, value))

        # Evict readings outside the window
        cutoff = ts_now - THEFT_WINDOW_S * 1000
        while buf and buf[0][0] < cutoff:
            buf.popleft()

        if len(buf) >= 2:
            drop = buf[0][1] - value   # positive = fuel decreased
            if drop >= THEFT_DROP_L:
                dev_known = last_known.get(device_id, {})
                lc_entry  = dev_known.get("load_current")
                lc        = lc_entry["value"] if lc_entry else None
                gen_off   = lc is None or lc < OFF_AMPS

                if gen_off and not _in_cooldown(device_id, "fuel_level", "fuel_theft"):
                    _set_cooldown(device_id, "fuel_level", "fuel_theft")
                    return _make_alert(
                        device_id, "fuel_level", "fuel_theft", "critical",
                        value, unit,
                        f"dropped {drop:.1f} L in {THEFT_WINDOW_S // 60} min while generator off",
                        drop_litres=drop,
                        window_s=float(THEFT_WINDOW_S),
                    )

    return None
