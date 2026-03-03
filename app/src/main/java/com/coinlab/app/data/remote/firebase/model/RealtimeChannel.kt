package com.coinlab.app.data.remote.firebase.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * v8.9 — Firebase Realtime Database channel model.
 * Path: community/channels/{channelId}
 */
@IgnoreExtraProperties
data class RealtimeChannel(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val memberCount: Int = 0,
    val members: Map<String, Boolean> = emptyMap() // userId → true
) {
    /** Convert members map to list of user IDs. */
    fun membersList(): List<String> = members.keys.toList()

    constructor() : this(id = "")
}
