package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.model.OfflineQueuedMutation
import com.example.data.model.OfflineSyncConflict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists offline mutations in SharedPreferences per user workspace, monitors
 * real network reachability via ConnectivityManager (requires ACCESS_NETWORK_STATE),
 * and surfaces timestamp conflicts when remote records changed while offline.
 */
class OfflineSyncManager(
    context: Context,
    private val userId: String
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("fvg_offline_sync_$userId", Context.MODE_PRIVATE)
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _queue = MutableStateFlow(loadQueueFromPrefs())
    val queue: StateFlow<List<OfflineQueuedMutation>> = _queue.asStateFlow()

    private val _conflicts = MutableStateFlow(loadConflictsFromPrefs())
    val conflicts: StateFlow<List<OfflineSyncConflict>> = _conflicts.asStateFlow()

    private var callbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val usable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = usable || checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }
    }

    init {
        registerConnectivityCallback()
    }

    private fun registerConnectivityCallback() {
        val cm = connectivityManager ?: return
        if (callbackRegistered) return
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            callbackRegistered = true
        }
    }

    fun unregister() {
        val cm = connectivityManager ?: return
        if (!callbackRegistered) return
        runCatching {
            cm.unregisterNetworkCallback(networkCallback)
            callbackRegistered = false
        }
    }

    fun refreshConnectivity(): Boolean {
        val current = checkCurrentConnectivity()
        _isOnline.value = current
        return current
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        return runCatching {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    fun enqueue(
        orgId: String,
        operationType: String,
        targetCollection: String,
        targetDocId: String,
        baseServerUpdatedAtMillis: Long,
        title: String,
        payload: JSONObject
    ): OfflineQueuedMutation {
        val item = OfflineQueuedMutation(
            id = "oq_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            orgId = orgId,
            operationType = operationType,
            targetCollection = targetCollection,
            targetDocId = targetDocId,
            baseServerUpdatedAtMillis = baseServerUpdatedAtMillis,
            title = title,
            payloadJson = payload.toString(),
            createdAtMillis = System.currentTimeMillis()
        )
        val updated = _queue.value + item
        _queue.value = updated
        saveQueueToPrefs(updated)
        return item
    }

    fun removeFromQueue(id: String) {
        val updated = _queue.value.filterNot { it.id == id }
        _queue.value = updated
        saveQueueToPrefs(updated)
    }

    fun addConflict(
        mutation: OfflineQueuedMutation,
        serverUpdatedAtMillis: Long,
        serverSummary: String,
        localSummary: String
    ): OfflineSyncConflict {
        val conflict = OfflineSyncConflict(
            id = "cnf_${mutation.id}",
            mutation = mutation,
            serverUpdatedAtMillis = serverUpdatedAtMillis,
            serverSummary = serverSummary,
            localSummary = localSummary
        )
        val updated = _conflicts.value.filterNot { it.mutation.id == mutation.id } + conflict
        _conflicts.value = updated
        saveConflictsToPrefs(updated)
        return conflict
    }

    fun removeConflict(conflictId: String) {
        val updated = _conflicts.value.filterNot { it.id == conflictId }
        _conflicts.value = updated
        saveConflictsToPrefs(updated)
    }

    private fun loadQueueFromPrefs(): List<OfflineQueuedMutation> {
        val raw = prefs.getString("queue_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        OfflineQueuedMutation(
                            id = o.optString("id"),
                            orgId = o.optString("orgId"),
                            operationType = o.optString("operationType"),
                            targetCollection = o.optString("targetCollection"),
                            targetDocId = o.optString("targetDocId"),
                            baseServerUpdatedAtMillis = o.optLong("baseServerUpdatedAtMillis", 0L),
                            title = o.optString("title"),
                            payloadJson = o.optString("payloadJson"),
                            createdAtMillis = o.optLong("createdAtMillis", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveQueueToPrefs(list: List<OfflineQueuedMutation>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("orgId", m.orgId)
                    put("operationType", m.operationType)
                    put("targetCollection", m.targetCollection)
                    put("targetDocId", m.targetDocId)
                    put("baseServerUpdatedAtMillis", m.baseServerUpdatedAtMillis)
                    put("title", m.title)
                    put("payloadJson", m.payloadJson)
                    put("createdAtMillis", m.createdAtMillis)
                }
            )
        }
        prefs.edit().putString("queue_json", arr.toString()).apply()
    }

    private fun loadConflictsFromPrefs(): List<OfflineSyncConflict> {
        val raw = prefs.getString("conflicts_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val mo = o.getJSONObject("mutation")
                    val m = OfflineQueuedMutation(
                        id = mo.optString("id"),
                        orgId = mo.optString("orgId"),
                        operationType = mo.optString("operationType"),
                        targetCollection = mo.optString("targetCollection"),
                        targetDocId = mo.optString("targetDocId"),
                        baseServerUpdatedAtMillis = mo.optLong("baseServerUpdatedAtMillis", 0L),
                        title = mo.optString("title"),
                        payloadJson = mo.optString("payloadJson"),
                        createdAtMillis = mo.optLong("createdAtMillis", 0L)
                    )
                    add(
                        OfflineSyncConflict(
                            id = o.optString("id"),
                            mutation = m,
                            serverUpdatedAtMillis = o.optLong("serverUpdatedAtMillis", 0L),
                            serverSummary = o.optString("serverSummary"),
                            localSummary = o.optString("localSummary")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveConflictsToPrefs(list: List<OfflineSyncConflict>) {
        val arr = JSONArray()
        list.forEach { c ->
            val m = c.mutation
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("serverUpdatedAtMillis", c.serverUpdatedAtMillis)
                    put("serverSummary", c.serverSummary)
                    put("localSummary", c.localSummary)
                    put(
                        "mutation",
                        JSONObject().apply {
                            put("id", m.id)
                            put("orgId", m.orgId)
                            put("operationType", m.operationType)
                            put("targetCollection", m.targetCollection)
                            put("targetDocId", m.targetDocId)
                            put("baseServerUpdatedAtMillis", m.baseServerUpdatedAtMillis)
                            put("title", m.title)
                            put("payloadJson", m.payloadJson)
                            put("createdAtMillis", m.createdAtMillis)
                        }
                    )
                }
            )
        }
        prefs.edit().putString("conflicts_json", arr.toString()).apply()
    }
}
