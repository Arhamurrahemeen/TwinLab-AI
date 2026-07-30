"""
TwinLab WhatsApp alerts — Twilio sandbox integration, role-based routing.
Flat module; called from _persist_alert via a thread executor (sync, never awaited).
"""

import logging

from config import settings

log = logging.getLogger("twinlab.whatsapp")

# (severity, alert_type) -> roles to notify
ROUTING = {
    ("critical", "threshold"):          ["owner", "maintenance_head"],
    ("warning",  "threshold"):          ["maintenance_head"],
    ("critical", "fuel_theft"):         ["owner", "maintenance_head"],
    ("critical", "consumable_reorder"): ["vendor", "supply_chain_lead"],
    ("warning",  "consumable_reorder"): ["vendor"],
}
DEFAULT_ROLES = ["owner"]


def send_alert(alert: dict, device: dict) -> dict:
    """
    Route one alert to N recipients based on (severity, alert_type).
    Returns {"sent": [roles...], "failed": [{"role", "error"}...]}. Never raises.
    """
    results = {"sent": [], "failed": []}
    if not settings.twilio_account_sid:
        log.info("[WHATSAPP] not configured — skipping")
        return results

    roles    = ROUTING.get((alert["severity"], alert["alert_type"]), DEFAULT_ROLES)
    contacts = [c for c in device.get("contacts", []) if c.get("role") in roles]
    if not contacts:
        if not settings.alert_whatsapp_to:
            log.info("[WHATSAPP] no matching contacts and no fallback recipient — skipping")
            return results
        contacts = [{"role": "owner", "name": "default", "whatsapp": settings.alert_whatsapp_to}]

    device_name = device.get("name") or alert["device_id"]
    body = _format_body(alert, device_name, device)

    try:
        from twilio.rest import Client
        client = Client(settings.twilio_account_sid, settings.twilio_auth_token)
    except Exception as e:
        log.error(f"[WHATSAPP ERROR] Twilio client init failed: {e}")
        results["failed"] = [{"role": c["role"], "error": str(e)} for c in contacts]
        return results

    for c in contacts:
        try:
            client.messages.create(
                from_=settings.twilio_whatsapp_from,
                to=c["whatsapp"],
                body=f"[→ {c['role']}: {c.get('name', c['role'])}]\n\n{body}",
            )
            log.info(f"[WHATSAPP OK] {alert['device_id']} / {alert['alert_type']} -> {c['role']}")
            results["sent"].append(c["role"])
        except Exception as e:
            log.error(f"[WHATSAPP ERROR] {c['role']}/{c.get('whatsapp')}: {e}")
            results["failed"].append({"role": c["role"], "error": str(e)})

    return results


def _format_body(alert: dict, device_name: str, device: dict) -> str:
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
    elif alert["alert_type"] == "consumable_reorder":
        hours  = alert["value"]
        vendor = device.get("vendor_name") or "vendor"
        en = (
            f"🟠 {device_name} — {hours:.0f}h reached — consumable change due. "
            f"Vendor: {vendor}. Reorder now to avoid over-stock or SLOB accumulation."
        )
        ur = (
            f"{device_name} — {hours:.0f} ghante ho gaye — consumable tabdeeli chahiye. "
            f"Vendor: {vendor}. Abhi order kar dein — zayada stock ya SLOB nuqsaan se bachne ke liye."
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
