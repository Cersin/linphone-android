/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.notifications

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.utils.LinphoneUtils

class NotificationBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "[Notification Broadcast Receiver]"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NotificationsManager.INTENT_NOTIF_ID, 0)
        Log.i(
            "$TAG Got notification broadcast for ID [$notificationId]"
        )

        if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_HANGUP_CALL_NOTIF_ACTION) {
            handleCallIntent(intent)
        } else if (intent.action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION || intent.action == NotificationsManager.INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION) {
            handleChatIntent(context, intent, notificationId)
        }
    }

    private fun handleCallIntent(intent: Intent) {
        val callId = intent.getStringExtra(NotificationsManager.INTENT_CALL_ID)
        if (callId == null) {
            Log.e("$TAG Remote SIP address is null for notification")
            return
        }

        coreContext.postOnCoreThread { core ->
            val call = core.getCallByCallid(callId)
            if (call == null) {
                Log.e("$TAG Couldn't find call from ID [$callId]")
            } else {
                if (intent.action == NotificationsManager.INTENT_ANSWER_CALL_NOTIF_ACTION) {
                    coreContext.answerCall(call)
                } else {
                    if (LinphoneUtils.isCallIncoming(call.state)) {
                        coreContext.declineCall(call)
                    } else {
                        coreContext.terminateCall(call)
                    }
                }
            }
        }
    }

    private fun handleChatIntent(context: Context, intent: Intent, notificationId: Int) {
        val remoteSipAddress = intent.getStringExtra(NotificationsManager.INTENT_REMOTE_ADDRESS)
        if (remoteSipAddress == null) {
            Log.e(
                "$TAG Remote SIP address is null for notification id $notificationId"
            )
            return
        }
        val localIdentity = intent.getStringExtra(NotificationsManager.INTENT_LOCAL_IDENTITY)
        if (localIdentity == null) {
            Log.e(
                "$TAG Local identity is null for notification id $notificationId"
            )
            return
        }

        val reply = getMessageText(intent)?.toString()
        if (intent.action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION) {
            if (reply == null) {
                Log.e("$TAG Couldn't get reply text")
                return
            }
        }

        coreContext.postOnCoreThread { core ->
            val remoteAddress = core.interpretUrl(remoteSipAddress, false)
            if (remoteAddress == null) {
                Log.e(
                    "$TAG Couldn't interpret remote address $remoteSipAddress"
                )
                return@postOnCoreThread
            }

            val localAddress = core.interpretUrl(localIdentity, false)
            if (localAddress == null) {
                Log.e(
                    "$TAG Couldn't interpret local address $localIdentity"
                )
                return@postOnCoreThread
            }

            val room = core.searchChatRoom(null, localAddress, remoteAddress, arrayOfNulls(0))
            if (room == null) {
                Log.e(
                    "$TAG Couldn't find chat room for remote address $remoteSipAddress and local address $localIdentity"
                )
                return@postOnCoreThread
            }

            if (intent.action == NotificationsManager.INTENT_REPLY_MESSAGE_NOTIF_ACTION) {
                val msg = room.createMessageFromUtf8(reply)
                msg.userData = notificationId
                msg.addListener(coreContext.notificationsManager.chatListener)
                msg.send()
                Log.i("$TAG Reply sent for notif id $notificationId")
            } else if (intent.action == NotificationsManager.INTENT_MARK_MESSAGE_AS_READ_NOTIF_ACTION) {
                room.markAsRead()
                if (!coreContext.notificationsManager.dismissChatNotification(room)) {
                    Log.w(
                        "$TAG Notifications Manager failed to cancel notification"
                    )
                    val notificationManager = context.getSystemService(
                        NotificationManager::class.java
                    )
                    notificationManager.cancel(NotificationsManager.CHAT_TAG, notificationId)
                }
            }
        }
    }

    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(NotificationsManager.KEY_TEXT_REPLY)
    }
}
