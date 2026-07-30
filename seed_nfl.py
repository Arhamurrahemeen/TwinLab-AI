"""
TwinLab NFL/SCAPM seed script — idempotent, upsert-based.
Seeds 5 NFL-flavored devices + matching sim_control docs for the ELXR'26 demo.
Safe to re-run: upserts by device_id, never inserts duplicates.
"""

from datetime import datetime, timezone

import pymongo

MONGO_URI = "mongodb://admin:twinlab123@localhost:27017"
MONGO_DB  = "twinlab"

# ponytail: base_values.temperature is intentionally offset -5 on devices where
# generator_on defaults True, because simulator.py adds +5C when gen_on (its
# "engine running warms things up" model). Without the offset, GEN-01 /
# SHJ-COLD-04 / FSD-STOR-05 baseline noise straddles their max threshold and
# alerts fire on tick 1. Revisit if simulator.py's warm-up model changes.
DEVICES = [
    {
        "device_id": "NFL-SITE-GEN-01",
        "name": "NFL SITE Karachi — Standby Genset 1",
        "location": "SITE Karachi Plant",
        "sensors": ["fuel_level", "load_current", "temperature"],
        "description": None,
        "source": "simulator",
        "thresholds": {
            "temperature":  {"min": None, "max": 40},
            "load_current": {"min": 0, "max": 30},
            "fuel_level":   {"min": 20, "max": None},
        },
        "status": "active",
        "base_values": {"fuel_level": 70, "load_current": 18, "temperature": 30},  # -5 offset (gen_on)
    },
    {
        "device_id": "NFL-SITE-COMP-02",
        "name": "NFL SITE Karachi — Air Compressor 2",
        "location": "SITE Karachi Plant",
        "sensors": ["load_current", "temperature", "accel_x", "accel_y", "accel_z"],
        "description": None,
        "source": "simulator",
        "thresholds": {
            "temperature":  {"min": None, "max": 55},
            "load_current": {"min": None, "max": 25},
        },
        "status": "active",
        "base_values": {"load_current": 15, "temperature": 42},
    },
    {
        "device_id": "NFL-FSD-CHILL-03",
        "name": "NFL Faisalabad — Process Chiller 3",
        "location": "Faisalabad Plant",
        "sensors": ["temperature", "humidity", "load_current"],
        "description": None,
        "source": "simulator",
        "thresholds": {
            "temperature":  {"min": -5, "max": 8},
            "load_current": {"min": None, "max": 20},
        },
        "status": "active",
        "base_values": {"temperature": 2, "humidity": 60, "load_current": 12},
    },
    {
        "device_id": "NFL-SHJ-COLD-04",
        "name": "NFL Sharjah — Cold Storage Unit 4",
        "location": "Sharjah Plant",
        "sensors": ["temperature", "humidity"],
        "description": None,
        "source": "simulator",
        "thresholds": {
            "temperature": {"min": -25, "max": -15},
            "humidity":    {"min": None, "max": 90},
        },
        "status": "active",
        "base_values": {"temperature": -25, "humidity": 65},  # -5 offset (gen_on)
    },
    {
        "device_id": "NFL-FSD-STOR-05",
        "name": "NFL Kunri Sourcing — Red Chili Cold Storage",
        "location": "Kunri (sourced by Faisalabad)",
        "sensors": ["temperature", "humidity"],
        "description": None,
        "source": "simulator",
        "thresholds": {
            "temperature": {"min": 5, "max": 15},
            "humidity":    {"min": None, "max": 65},
        },
        "status": "active",
        "base_values": {"temperature": 5, "humidity": 55},  # -5 offset (gen_on)
    },
]


def _default_ctrl(device_id: str, base_values: dict) -> dict:
    return {
        "device_id":    device_id,
        "generator_on": True,
        "base_values":  base_values,
        "inject": {
            name: {"active": False, "until_ts": 0}
            for name in ("fuel_theft", "overheat", "overload", "offline")
        },
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }


def main():
    mongo = pymongo.MongoClient(MONGO_URI)
    db    = mongo[MONGO_DB]
    now   = datetime.now(timezone.utc)

    for dev in DEVICES:
        device_id   = dev["device_id"]
        base_values = dev.pop("base_values")

        db.devices.update_one(
            {"device_id": device_id},
            {
                "$set": {**dev, "updated_at": now},
                "$setOnInsert": {"created_at": now},
            },
            upsert=True,
        )

        db.sim_control.update_one(
            {"device_id": device_id},
            {"$set": _default_ctrl(device_id, base_values)},
            upsert=True,
        )

        print(f"[SEED] upserted {device_id}")

    print("[OK]")


if __name__ == "__main__":
    main()
