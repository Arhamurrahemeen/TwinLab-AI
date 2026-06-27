from datetime import datetime
from typing import Any, Dict, Optional
from pydantic import BaseModel


class DeviceCreate(BaseModel):
    device_id: str
    name: str
    location: str
    sensors: list[str]
    description: Optional[str] = None
    source: str = "simulator"
    thresholds: Dict[str, Any] = {}
    status: str = "active"


class DeviceUpdate(BaseModel):
    name: Optional[str] = None
    location: Optional[str] = None
    sensors: Optional[list[str]] = None
    description: Optional[str] = None
    source: Optional[str] = None
    thresholds: Optional[Dict[str, Any]] = None
    status: Optional[str] = None


class DeviceResponse(BaseModel):
    device_id: str
    name: str
    location: str
    sensors: list[str]
    description: Optional[str] = None
    source: str = "simulator"
    thresholds: Dict[str, Any] = {}
    status: str = "active"
    created_at: datetime
    updated_at: datetime
