"""
TwinLab WhatsApp alerts — Twilio sandbox integration.
Flat module; called from _persist_alert via a thread executor (sync, never awaited).
"""

import logging

from config import settings

log = logging.getLogger("twinlab.whatsapp")


def send_alert(alert: dict, device_name: str) -> bool:
    """
    Send a bilingual WhatsApp for a fired alert.
    Returns True on success, False otherwise. Never raises.
    """
    if not settings.twilio_account_sid or not settings.alert_whatsapp_to:
        log.info("[WHATSAPP] not configured — skipping")
        return False

    try:
        from twilio.rest import Client
        client = Client(settings.twilio_account_sid, settings.twilio_auth_token)
        client.messages.create(
            from_=settings.twilio_whatsapp_from,
            to=settings.alert_whatsapp_to,
            body=_format_body(alert, device_name),
        )
        log.info(f"[WHATSAPP OK] {alert['device_id']} / {alert['alert_type']}")
        return True
    except Exception as e:
        log.error(f"[WHATSAPP ERROR] {e}")
        return False


def _format_body(alert: dict, device_name: str) -> str:
    if alert["alert_type"] == "fuel_theft":
        drop     = alert.get("drop_litres", 0.0)
        window_s = alert.get("window_s", 300)
        mins     = round(window_s / 60, 1)
        rupees   = round(drop * settings.diesel_price_pkr)
        en = (
            f"⚠️ {device_name} — fuel dropped {drop:.1f}L in {mins} min "
            f"while generator OFF. Suspected theft. Est. loss ~PKR {rupees:,}."
        )
        ur = (
            f"{device_name} — generator BAND honay ke bawajood {mins} min mein "
            f"{drop:.1f}L fuel kam hua. Chori ka shak. Taqreeban PKR {rupees:,} nuqsan."
        )
    else:
        sensor   = alert["sensor"].replace("_", " ")
        value    = alert["value"]
        unit     = alert["unit"]
        detail   = alert.get("detail", "")
        severity = alert["severity"].upper()
        icon     = "\U0001f534" if alert["severity"] == "critical" else "\U0001f7e1"
        en = f"{icon} {device_name} — {sensor} {value}{unit} ({detail}). Severity: {severity}."
        ur = f"{device_name} — {sensor} {value}{unit} ({detail}). Severity: {severity}."

    return f"{en}\n\n{ur}"
