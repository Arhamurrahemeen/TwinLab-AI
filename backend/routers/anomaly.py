import logging
import re

import numpy as np
from fastapi import APIRouter, HTTPException, Query
from sklearn.ensemble import IsolationForest

from config import settings
from db.influx import get_query_api

log = logging.getLogger("twinlab.anomaly")
router = APIRouter()

_SAFE_ID = re.compile(r'^[\w\-]+$')


def _validate_id(value: str, label: str) -> str:
    if not _SAFE_ID.match(value):
        raise HTTPException(400, f"Invalid {label} format")
    return value


@router.get("/{device_id}/anomalies")
async def get_anomalies(
    device_id: str,
    sensor: str = Query(..., description="Sensor name e.g. temperature"),
    limit: int = Query(50, ge=10, le=500),
    range_hours: int = Query(24, ge=1, le=168),
):
    _validate_id(device_id, "device_id")
    _validate_id(sensor, "sensor")

    flux = f"""
from(bucket: "{settings.influx_bucket}")
  |> range(start: -{range_hours}h)
  |> filter(fn: (r) => r._measurement == "sensor_reading")
  |> filter(fn: (r) => r.device_id == "{device_id}")
  |> filter(fn: (r) => r.sensor == "{sensor}")
  |> tail(n: {limit})
"""
    try:
        tables = get_query_api().query(flux, org=settings.influx_org)
    except Exception as e:
        log.error(f"[InfluxDB] Query failed: {e}")
        raise HTTPException(500, "InfluxDB query failed")

    records = []
    for table in tables:
        for record in table.records:
            records.append({
                "ts": record.get_time().isoformat(),
                "value": record.get_value(),
                "unit": record.values.get("unit", ""),
                "sensor": sensor,
                "device_id": device_id,
            })

    if len(records) < 20:
        for r in records:
            r["is_anomaly"] = False
        return records

    values = np.array([r["value"] for r in records]).reshape(-1, 1)
    model = IsolationForest(contamination=0.03, random_state=42)
    model.fit(values)

    # Use anomaly score rather than the hard predict label.
    # score_samples returns negative scores; more negative = more anomalous.
    # We only flag points that are clear outliers (score < mean - 2*std).
    scores = model.score_samples(values)
    threshold = scores.mean() - 2 * scores.std()

    for r, score in zip(records, scores):
        r["is_anomaly"] = bool(score < threshold)

    return records
