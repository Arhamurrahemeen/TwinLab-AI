"""
TwinLab sensor simulator — registry-driven
Reads active simulator devices from MongoDB and publishes their sensors.
No device is hardcoded. Register a device with source=simulator to start publishing.
"""

import json
import math
import random
import time

import paho.mqtt.client as mqtt
import pymongo

MQTT_HOST  = "localhost"
MQTT_PORT  = 1883
MONGO_URI  = "mongodb://admin:twinlab123@localhost:27017"
MONGO_DB   = "twinlab"
REFRESH_S  = 30   # seconds between device-list reloads from Mongo


def _sensor_value(sensor: str, t: int) -> tuple:
    """Return (value, unit) for a given sensor name at tick t."""
    if sensor == "temperature":
        return round(24.5 + 3 * math.sin(t * 0.1) + random.uniform(-0.3, 0.3), 2), "C"
    if sensor == "humidity":
        return round(55.0 + 5 * math.cos(t * 0.07) + random.uniform(-1, 1), 2), "%"
    if sensor == "accel_x":
        return round(random.uniform(-0.05, 0.05), 4), "g"
    if sensor == "accel_y":
        return round(random.uniform(-0.05, 0.05), 4), "g"
    if sensor == "accel_z":
        return round(1.0 + random.uniform(-0.02, 0.02), 4), "g"
    if sensor == "gyro_x":
        return round(random.uniform(-0.5, 0.5), 3), "deg/s"
    if sensor == "gyro_y":
        return round(random.uniform(-0.5, 0.5), 3), "deg/s"
    if sensor == "gyro_z":
        return round(random.uniform(-0.5, 0.5), 3), "deg/s"
    if sensor == "fuel_level":
        return round(70.0 + 5 * math.sin(t * 0.05) + random.uniform(-0.5, 0.5), 2), "L"
    if sensor == "load_current":
        return round(18.0 + 4 * math.sin(t * 0.08) + random.uniform(-1, 1), 2), "A"
    # unknown sensor — generic oscillating value
    return round(50.0 + 10 * math.sin(t * 0.1) + random.uniform(-1, 1), 2), ""


def _load_devices(col):
    """Return list of {device_id, sensors} for active simulator devices."""
    try:
        docs = list(col.find(
            {"source": "simulator", "status": "active"},
            {"_id": 0, "device_id": 1, "sensors": 1},
        ))
        return docs
    except Exception as e:
        print(f"[ERROR] Mongo query failed: {e}")
        return []


def _log_devices(devices):
    if not devices:
        print("[SIM] No active simulator devices in registry. Waiting...")
        return
    for d in devices:
        print(f"[SIM] Tracking '{d['device_id']}' → sensors: {d.get('sensors', [])}")


# ── Connect ──────────────────────────────────────────────────
mqtt_client = mqtt.Client()
mqtt_client.connect(MQTT_HOST, MQTT_PORT)
mqtt_client.loop_start()

mongo = pymongo.MongoClient(MONGO_URI)
col   = mongo[MONGO_DB]["devices"]

print("[TwinLab Simulator] Starting — registry-driven mode")
devices      = _load_devices(col)
last_refresh = time.time()
_log_devices(devices)

t = 0
while True:
    # Reload device list on schedule
    if time.time() - last_refresh >= REFRESH_S:
        fresh = _load_devices(col)
        if fresh != devices:
            devices = fresh
            print(f"[SIM] Registry refreshed — {len(devices)} active simulator device(s)")
            _log_devices(devices)
        last_refresh = time.time()

    # Publish one reading per sensor per device
    for device in devices:
        device_id = device["device_id"]
        for sensor in device.get("sensors", []):
            value, unit = _sensor_value(sensor, t)
            topic   = f"twinlab/device/{device_id}/sensor/{sensor}"
            payload = json.dumps({"value": value, "unit": unit, "ts": int(time.time() * 1000)})
            mqtt_client.publish(topic, payload)
            print(f"[SIM] {topic} -> {value} {unit}")

    t += 1
    time.sleep(1)
