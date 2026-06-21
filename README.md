# TwinLab Phase 1 — MQTT + Storage Setup

## Folder structure
twinlab/
├── docker-compose.yml        # Mosquitto + InfluxDB + MongoDB
├── mosquitto/
│   └── config/
│       └── mosquitto.conf    # Broker config
├── ingestion.py              # Subscribes MQTT → writes InfluxDB
├── simulator.py              # Fake sensor data (no hardware needed)
├── test_mqtt.py              # Listener to verify broker is working
└── requirements.txt

## Step 1 — Start all services
docker compose up -d

## Step 2 — Install Python deps
pip install -r requirements.txt

## Step 3 — Verify MQTT (two terminals)
# Terminal 1 — listen
python test_mqtt.py

# Terminal 2 — publish fake data
python simulator.py

## Step 4 — Start ingestion (writes to InfluxDB)
python ingestion.py

## Step 5 — Verify InfluxDB
Open http://localhost:8086
Login: admin / twinlab123
Go to Data Explorer → bucket: twinlab → measurement: sensor_reading

## MQTT topic structure
twinlab/device/{device_id}/sensor/{sensor_name}

Examples:
  twinlab/device/dev-001/sensor/temperature
  twinlab/device/dev-001/sensor/humidity
  twinlab/device/dev-001/sensor/accel_x

## Service ports
  MQTT broker:  localhost:1883
  InfluxDB UI:  localhost:8086
  MongoDB:      localhost:27017
