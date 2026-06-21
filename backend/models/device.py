from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class DeviceCreate(BaseModel):
    device_id: str
    name: str
    location: str
    sensors: list[str]
    description: Optional[str] = None


class DeviceUpdate(BaseModel):
    name: Optional[str] = None
    location: Optional[str] = None
    sensors: Optional[list[str]] = None
    description: Optional[str] = None


class DeviceResponse(BaseModel):
    device_id: str
    name: str
    location: str
    sensors: list[str]
    description: Optional[str] = None
    created_at: datetime
    updated_at: datetime
