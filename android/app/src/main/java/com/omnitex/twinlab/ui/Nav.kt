package com.omnitex.twinlab.ui

object Routes {
    const val SETTINGS = "settings"
    const val ASSETS = "assets"
    const val DETAIL = "detail/{deviceId}"
    const val ALERTS = "alerts"

    fun detail(deviceId: String) = "detail/$deviceId"
}
