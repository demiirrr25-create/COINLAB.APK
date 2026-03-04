package com.coinlab.app.data.remote.firebase

import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.entity.PriceAlertEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v9.6 — Price Alert Firebase Repository
 *
 * Syncs price alerts between local Room DB and Firebase RTDB.
 * Structure:
 *   price_alerts/{userId}/{alertId} → PriceAlertData
 */
@Singleton
class PriceAlertFirebaseRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val priceAlertDao: PriceAlertDao
) {
    private val alertsRef = database.reference.child("price_alerts")

    private fun userAlertsRef() = auth.currentUser?.uid?.let { alertsRef.child(it) }

    /**
     * Push a local alert to Firebase RTDB
     */
    suspend fun syncAlertToFirebase(alert: PriceAlertEntity) {
        val ref = userAlertsRef() ?: return
        val data = mapOf(
            "coinId" to alert.coinId,
            "coinSymbol" to alert.coinSymbol,
            "coinName" to alert.coinName,
            "coinImage" to alert.coinImage,
            "targetPrice" to alert.targetPrice,
            "currency" to alert.currency,
            "isAbove" to alert.isAbove,
            "isActive" to alert.isActive,
            "isTriggered" to alert.isTriggered,
            "createdAt" to alert.createdAt
        )
        ref.child(alert.id.toString()).setValue(data).await()
    }

    /**
     * Delete an alert from Firebase RTDB
     */
    suspend fun deleteAlertFromFirebase(alertId: Long) {
        val ref = userAlertsRef() ?: return
        ref.child(alertId.toString()).removeValue().await()
    }

    /**
     * Sync all local alerts to Firebase (backup)
     */
    suspend fun syncAllToFirebase() {
        val ref = userAlertsRef() ?: return
        val alerts = priceAlertDao.getAllAlertsList()
        val data = mutableMapOf<String, Any>()
        alerts.forEach { alert ->
            data[alert.id.toString()] = mapOf(
                "coinId" to alert.coinId,
                "coinSymbol" to alert.coinSymbol,
                "coinName" to alert.coinName,
                "coinImage" to alert.coinImage,
                "targetPrice" to alert.targetPrice,
                "currency" to alert.currency,
                "isAbove" to alert.isAbove,
                "isActive" to alert.isActive,
                "isTriggered" to alert.isTriggered,
                "createdAt" to alert.createdAt
            )
        }
        ref.setValue(data).await()
    }

    /**
     * Listen to alerts from Firebase (for cross-device sync)
     */
    fun observeFirebaseAlerts(): Flow<List<PriceAlertEntity>> = callbackFlow {
        val ref = userAlertsRef()
        if (ref == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = snapshot.children.mapNotNull { child ->
                    try {
                        PriceAlertEntity(
                            id = child.key?.toLongOrNull() ?: 0L,
                            coinId = child.child("coinId").getValue(String::class.java) ?: "",
                            coinSymbol = child.child("coinSymbol").getValue(String::class.java) ?: "",
                            coinName = child.child("coinName").getValue(String::class.java) ?: "",
                            coinImage = child.child("coinImage").getValue(String::class.java) ?: "",
                            targetPrice = child.child("targetPrice").getValue(Double::class.java) ?: 0.0,
                            currency = child.child("currency").getValue(String::class.java) ?: "USD",
                            isAbove = child.child("isAbove").getValue(Boolean::class.java) ?: true,
                            isActive = child.child("isActive").getValue(Boolean::class.java) ?: true,
                            isTriggered = child.child("isTriggered").getValue(Boolean::class.java) ?: false,
                            createdAt = child.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(alerts)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
