package dev.upbooker.android

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dev.upbooker.core.OtpParser
import dev.upbooker.core.OtpSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * Listens for SMS only while a login is in progress. The code is read on the
 * device and passed straight to the login call; it is never stored or logged.
 */
class SmsOtpSource(
    private val context: Context,
    private val keyword: String,
) : OtpSource {
    private var code = CompletableDeferred<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
                .groupBy { it.originatingAddress }
                .forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    OtpParser.extract(sender, body, keyword)?.let { code.complete(it) }
                }
        }
    }

    override fun start() {
        code = CompletableDeferred()
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            // Only the system telephony stack holds BROADCAST_SMS.
            Manifest.permission.BROADCAST_SMS,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override suspend fun await(timeout: Duration): String? =
        withTimeoutOrNull(timeout.toMillis()) { code.await() }

    override fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Not registered.
        }
    }
}
