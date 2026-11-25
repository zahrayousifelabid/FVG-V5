package com.example.data.sync

import com.example.data.model.OfflineQueuedMutation
import com.example.data.model.OfflineSyncConflict
import com.example.domain.json.FvgJsonArray
import com.example.domain.json.FvgJsonObject
import com.example.platform.FvgConnectivityMonitor
import com.example.platform.FvgKeyValueStore
import com.example.platform.currentTimeMillis
import com.example.platform.fvgRandomUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists offline mutations per user workspace, monitors connectivity via platform bridge,
 * and surfaces timestamp conflicts when remote records changed while offline.
 */
class OfflineSyncManager(
    private val prefs: FvgKeyValueStore,
    private val connectivity: FvgConnectivityMonitor
) {
    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    private val _queue = MutableStateFlow(loadQueueFromPrefs())
    val queue: StateFlow<List<OfflineQueuedMutation>> = _queue.asStateFlow()

    private val _conflicts = MutableStateFlow(loadConflictsFromPrefs())
    val conflicts: StateFlow<List<OfflineSyncConflict>> = _conflicts.asStateFlow()

    fun unregister() {
        connectivity.dispose()
    }

    fun refreshConnectivity(): Boolean = connectivity.refreshConnectivity()

    fun enqueue(
        orgId: String,
        operationType: String,
        targetCollection: String,
        targetDocId: String,
        baseServerUpdatedAtMillis: Long,
        title: String,
        payload: FvgJsonObject
    ): OfflineQueuedMutation {
        val item = OfflineQueuedMutation(
            id = "oq_${fvgRandomUuid().replace("-", "").take(12)}",
            orgId = orgId,
            operationType = operationType,
            targetCollection = targetCollection,
            targetDocId = targetDocId,
            baseServerUpdatedAtMillis = baseServerUpdatedAtMillis,
            title = title,
            payloadJson = payload.toString(),
            createdAtMillis = currentTimeMillis()
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
        val raw = prefs.getStringOrNull("queue_json") ?: return emptyList()
        return runCatching {
            val arr = FvgJsonArray(raw)
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
        val arr = FvgJsonArray()
        list.forEach { m ->
            arr.put(
                FvgJsonObject().apply {
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
        prefs.putString("queue_json", arr.toString())
    }

    private fun loadConflictsFromPrefs(): List<OfflineSyncConflict> {
        val raw = prefs.getStringOrNull("conflicts_json") ?: return emptyList()
        return runCatching {
            val arr = FvgJsonArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val mo = o.optJSONObject("mutation") ?: continue
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
        val arr = FvgJsonArray()
        list.forEach { c ->
            val m = c.mutation
            arr.put(
                FvgJsonObject().apply {
                    put("id", c.id)
                    put("serverUpdatedAtMillis", c.serverUpdatedAtMillis)
                    put("serverSummary", c.serverSummary)
                    put("localSummary", c.localSummary)
                    put(
                        "mutation",
                        FvgJsonObject().apply {
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
        prefs.putString("conflicts_json", arr.toString())
    }
}
