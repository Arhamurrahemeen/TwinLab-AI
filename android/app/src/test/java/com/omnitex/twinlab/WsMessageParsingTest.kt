package com.omnitex.twinlab

import com.omnitex.twinlab.data.WsMessage
import com.omnitex.twinlab.data.parseWsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WsMessageParsingTest {
    // shape from backend/main.py _on_mqtt_message broadcast
    private val liveJson =
        """{"device_id":"TL-01","sensor":"temperature","value":42.5,"unit":"C","ts":1734000000000}"""

    // shape from backend/main.py _persist_alert: _json_safe(alert) + {"type":"alert"}
    private val alertJson =
        """{"type":"alert","device_id":"TL-01","sensor":"temperature","alert_type":"threshold","severity":"critical","value":96.1,"unit":"C","detail":"above max 40","message_en":"hot","message_ur":"garam","ts":1734000000000,"created_at":"2026-09-07T10:00:00+00:00","push_sent":false}"""

    @Test fun parsesLiveReading() {
        val m = parseWsMessage(liveJson)
        assertTrue(m is WsMessage.Live)
        assertEquals(42.5, (m as WsMessage.Live).reading.value, 0.001)
        assertEquals("temperature", m.reading.sensor)
    }

    @Test fun parsesAlert() {
        val m = parseWsMessage(alertJson)
        assertTrue(m is WsMessage.AlertMsg)
        assertEquals("critical", (m as WsMessage.AlertMsg).alert.severity)
        assertEquals("TL-01", m.alert.device_id)
    }

    @Test fun alertWithIntegerValueStillParses() {
        val m = parseWsMessage(alertJson.replace("96.1", "96"))
        assertTrue(m is WsMessage.AlertMsg)
        assertEquals(96.0, (m as WsMessage.AlertMsg).alert.value, 0.001)
    }

    @Test fun garbageReturnsNull() = assertNull(parseWsMessage("not json"))

    @Test fun emptyReturnsNull() = assertNull(parseWsMessage(""))
}
