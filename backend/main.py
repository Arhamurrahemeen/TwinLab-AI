import asyncio
import json
import logging
import threading
import time
from contextlib import asynccontextmanager

import paho.mqtt.client as mqtt
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

import alerts as alert_engine
import whatsapp
from config import settings
from db.influx import close_influx
from db.mongo import close_mongo, connect_mongo, get_db
from routers import devices, readings, ws, chat, rul, sim as sim_router
from routers import alerts as alerts_router
from routers.ws import manager

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
log = logging.getLogger("twinlab")

_loop: asyncio.AbstractEventLoop = None

# Last-known reading per device+sensor — survives InfluxDB/MQTT gaps
_last_known: dict = {}  # {device_id: {sensor: {value, unit, ts}}}


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
        # Update last-known cache
        _last_known.setdefault(device_id, {})[sensor_name] = {
            "value": data["value"],
            "unit": data["unit"],
            "ts": data["ts"],
        }
        if _loop:
            asyncio.run_coroutine_threadsafe(manager.broadcast(device_id, data), _loop)
            alert = alert_engine.evaluate(device_id, sensor_name, data["value"], data["unit"], _last_known)
            if alert:
                asyncio.run_coroutine_threadsafe(_persist_alert(alert), _loop)
    except Exception as e:
        log.error(f"[MQTT] WS push error: {e}")


async def _persist_alert(alert: dict) -> None:
    try:
        db = get_db()

        # Resolve device name for WhatsApp body
        device_doc  = await db.devices.find_one({"device_id": alert["device_id"]}, {"name": 1})
        device_name = device_doc["name"] if device_doc else alert["device_id"]

        result = await db.alerts.insert_one(dict(alert))
        await manager.broadcast(alert["device_id"], {**alert, "type": "alert"})
        log.info(
            f"[ALERT] {alert['alert_type']} {alert['severity']} — "
            f"{alert['device_id']}/{alert['sensor']} — {alert['detail']}"
        )

        # Send WhatsApp in a thread (Twilio SDK is sync)
        loop = asyncio.get_running_loop()
        sent = await loop.run_in_executor(None, whatsapp.send_alert, alert, device_name)
        if sent:
            await db.alerts.update_one(
                {"_id": result.inserted_id},
                {"$set": {"whatsapp_sent": True}},
            )
    except Exception as e:
        log.error(f"[ALERT] persist failed: {e}")


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
    asyncio.create_task(alert_engine.cache_loop())
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

app.include_router(devices.router,       prefix="/devices", tags=["devices"])
app.include_router(readings.router,      prefix="/devices", tags=["readings"])
app.include_router(alerts_router.router, prefix="/devices", tags=["alerts"])
app.include_router(rul.router,           prefix="/devices", tags=["rul"])
app.include_router(sim_router.router,    prefix="/sim",     tags=["sim-control"])
app.include_router(chat.router,          tags=["chat"])
app.include_router(ws.router,            tags=["websocket"])


@app.get("/health", tags=["system"])
async def health():
    return {"status": "ok"}


@app.get("/devices/{device_id}/last-known", tags=["system"])
async def last_known(device_id: str):
    return _last_known.get(device_id, {})
