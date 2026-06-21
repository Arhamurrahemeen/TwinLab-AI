"""
Quick test: subscribe and print everything arriving on twinlab/#
Run this in one terminal, simulator.py in another
"""

import paho.mqtt.client as mqtt

def on_connect(client, userdata, flags, rc):
    print(f"[TEST] Connected (rc={rc}). Listening on twinlab/#")
    client.subscribe("twinlab/#")

def on_message(client, userdata, msg):
    print(f"  {msg.topic}  -->  {msg.payload.decode()}")

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message
client.connect("localhost", 1883)
print("[TEST] Starting listener... (Ctrl+C to stop)")
client.loop_forever()
