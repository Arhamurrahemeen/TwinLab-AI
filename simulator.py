"""
TwinLab sensor simulator
Publishes fake DHT22 + MPU6050 data so you can test without hardware
"""

import json
import time
import math
import random
import paho.mqtt.client as mqtt

MQTT_HOST  = "localhost"
MQTT_PORT  = 1883
DEVICE_ID  = "dev-001"

client = mqtt.Client()
client.connect(MQTT_HOST, MQTT_PORT)
client.loop_start()

def publish(sensor, value, unit):
    topic   = f"twinlab/device/{DEVICE_ID}/sensor/{sensor}"
    payload = json.dumps({
        "value": round(value, 3),
        "unit":  unit,
        "ts":    int(time.time() * 1000)
    })
    client.publish(topic, payload)
    print(f"[SIM] {topic} -> {value} {unit}")

print(f"[TwinLab Simulator] Publishing as device '{DEVICE_ID}'")

t = 0
while True:
    # DHT22 — temperature & humidity
    temp     = 24.5 + 3 * math.sin(t * 0.1) + random.uniform(-0.3, 0.3)
    humidity = 55.0 + 5 * math.cos(t * 0.07) + random.uniform(-1, 1)
    publish("temperature", temp, "C")
    publish("humidity",    humidity, "%")

    # MPU6050 — accelerometer (simulates gentle vibration)
    accel_x = random.uniform(-0.05, 0.05)
    accel_y = random.uniform(-0.05, 0.05)
    accel_z = 1.0 + random.uniform(-0.02, 0.02)
    publish("accel_x", accel_x, "g")
    publish("accel_y", accel_y, "g")
    publish("accel_z", accel_z, "g")

    # Gyroscope
    gyro_x = random.uniform(-0.5, 0.5)
    gyro_y = random.uniform(-0.5, 0.5)
    gyro_z = random.uniform(-0.5, 0.5)
    publish("gyro_x", gyro_x, "deg/s")
    publish("gyro_y", gyro_y, "deg/s")
    publish("gyro_z", gyro_z, "deg/s")

    t += 1
    time.sleep(1)
