import asyncio
import json
import logging
import threading
import time
from contextlib import asynccontextmanager

import paho.mqtt.client as mqtt
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from db.influx import close_influx
from db.mongo import close_mongo, connect_mongo
from routers import devices, readings, ws
from routers.ws import manager

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
log = logging.getLogger("twinlab")

_loop: asyncio.AbstractEventLoop = None


def _parse_topic(topic: str):
    parts = topic.split("/")
    if len(parts) == 5 and parts[1] == "device" and parts[3] == "sensor":
        return parts[2], parts[4]
    return None, None


def _on_mqtt_message(client, userdata, msg):
    device_id, sensor_name = _parse_topic(msg.topic)
    if not device_id:
        return
    try:
        payload = json.loads(msg.payload.decode())
        data = {
            "device_id": device_id,
            "sensor": sensor_name,
            "value": float(payload["value"]),
            "unit": payload.get("unit", ""),
            "ts": payload.get("ts", int(time.time() * 1000)),
        }
        if _loop:
            asyncio.run_coroutine_threadsafe(manager.broadcast(device_id, data), _loop)
    except Exception as e:
        log.error(f"[MQTT] WS push error: {e}")


def _start_mqtt():
    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            log.info("[MQTT] Connected, subscribing to twinlab/#")
            client.subscribe("twinlab/#")
        else:
            log.error(f"[MQTT] Connect failed rc={rc}")

    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = _on_mqtt_message
    client.connect(settings.mqtt_host, settings.mqtt_port, keepalive=60)
    client.loop_forever()


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _loop
    _loop = asyncio.get_running_loop()
    await connect_mongo()
    threading.Thread(target=_start_mqtt, daemon=True, name="mqtt-subscriber").start()
    log.info("[TwinLab] Backend started")
    yield
    close_influx()
    await close_mongo()
    log.info("[TwinLab] Backend stopped")


app = FastAPI(title="TwinLab API", version="0.2.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(devices.router, prefix="/devices", tags=["devices"])
app.include_router(readings.router, prefix="/devices", tags=["readings"])
app.include_router(ws.router, tags=["websocket"])


@app.get("/health", tags=["system"])
async def health():
    return {"status": "ok"}
