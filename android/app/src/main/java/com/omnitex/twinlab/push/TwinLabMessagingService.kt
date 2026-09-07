package com.omnitex.twinlab.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.omnitex.twinlab.TwinLabApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TwinLabMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                (application as TwinLabApp).container.api.value?.registerPushToken(token)
            }
        }
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: "TwinLab alert"
        val body = msg.notification?.body ?: msg.data["message_ur"] ?: ""
        showAlertNotification(this, title, body, msg.data["device_id"])
    }
}
