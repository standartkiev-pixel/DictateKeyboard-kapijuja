/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.util.Log
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.florisboard.lib.android.clearPrimaryClipAnyApi
import org.florisboard.lib.android.setOrClearPrimaryClip
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.tryOrNull

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        // 1 minute
        private const val INTERVAL = 60 * 1000L
        private const val OWN_CLIP_MARKER = "net.kapijuja.dictate.own_clip"
        private const val TAG = "KapijujaClipboard"

        /**
         * Taken from ClipboardDescription.java from the AOSP
         *
         * Helper to compare two MIME types, where one may be a pattern.
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            val typeLength = desiredType.length
            if (typeLength == 3 && desiredType == "*/*") {
                return true
            }
            val slashpos = desiredType.indexOf('/')
            if (slashpos > 0) {
                if (typeLength == slashpos + 2 && desiredType[slashpos + 1] == '*') {
                    if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                        return true
                    }
                } else if (desiredType == concreteType) {
                    return true
                }
            }
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    // ClipDescription.timestamp changes on a real new copy, including identical text.
    // The old text/URI equality guard suppressed legitimate "copy again" events indefinitely.
    private var primaryClipLastTimestamp = 0L
    /**
     * Clips copied by *another* app, one event per copy, as they reach the history. The keyboard's
     * own copies set [primaryClip] first and are therefore not emitted. Used by Driver Assistant
     * auto-read (issue #7), so it needs no second clipboard listener.
     */
    val externalClipFlow: SharedFlow<ClipboardItem>
        field = MutableSharedFlow<ClipboardItem>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        cleanUpJob = ioScope.launch {
            while (isActive) {
                delay(INTERVAL)
                enforceExpiryDate(currentHistory)
            }
        }
    }

    fun initializeForContext(context: Context) {
        ioScope.launch {
            if (clipHistoryDb == null) {
                clipHistoryDb = ClipboardHistoryDatabase.new(context.applicationContext)
                withContext(Dispatchers.Main) {
                    clipHistoryDao?.getAllAsFlow()?.collect { items ->
                        updateHistory(items)
                    }
                }
            }
        }
    }

    private fun updateHistory(items: List<ClipboardItem>) {
        val itemsSorted = items.sortedByDescending { it.creationTimestampMs }
        val clipHistory = ClipboardHistory(itemsSorted)
        enforceHistoryLimit(clipHistory)
        historyFlow.value = clipHistory
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        primaryClip = item
        // Mark ONLY our own writes. Equality of clip text is not proof that a later copy is ours.
        // Preserve system sensitivity extras when adding the marker.
        val clipData = item?.toClipData(appContext)?.also { data ->
            val desc = data.description
            val extras = desc.extras?.let { PersistableBundle(it) } ?: PersistableBundle()
            extras.putBoolean(OWN_CLIP_MARKER, true)
            desc.extras = extras
        }
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(clipData)
        }
    }

    /**
     * Reattach after the IME becomes visible. This is event-driven (not a polling timer).
     * Reconcile a missed clipboard change once; timestamp dedup prevents replay of an old copy.
     */
    fun refreshClipboardObservation() {
        runCatching {
            systemClipboardManager.removePrimaryClipChangedListener(this)
            systemClipboardManager.addPrimaryClipChangedListener(this)
            onPrimaryClipChanged()
        }.onFailure { Log.w(TAG, "Failed to reattach clipboard listener", it) }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        val autoReadEnabled = prefs.speech.autoReadEnabled.get()
        if (!prefs.clipboard.useInternalClipboard.get() ||
            syncBehavior != ClipboardSyncBehavior.NO_EVENTS || autoReadEnabled
        ) {
            // Android can reject a read while the IME loses access/focus. Never allow that
            // transient exception to kill the clipboard callback or its process.
            val systemPrimaryClip = runCatching { systemClipboardManager.primaryClip }
                .onFailure { Log.w(TAG, "Cannot read clipboard; will retry on next IME event", it) }
                .getOrNull() ?: return
            ioScope.launch {
                try {
                    val isDuplicate = primaryClipLastFromCallbackGuard.withLock {
                        val timestamp = systemPrimaryClip.description.timestamp
                        val duplicate = if (timestamp > 0L) {
                            timestamp == primaryClipLastTimestamp
                        } else {
                            systemPrimaryClip === primaryClipLastFromCallback
                        }
                        primaryClipLastTimestamp = timestamp
                        primaryClipLastFromCallback = systemPrimaryClip
                        duplicate
                    }
                    if (isDuplicate) return@launch
                    if (systemPrimaryClip.itemCount == 0 ||
                        systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }
                    ) {
                        if (syncBehavior.shouldSyncClear) primaryClip = null
                        return@launch
                    }
                    // A genuine new copy by another app must be published even if its text
                    // happens to equal the current internal clipboard text.
                    val isOwnCopy = systemPrimaryClip.description.extras
                        ?.getBoolean(OWN_CLIP_MARKER, false) == true
                    if (isOwnCopy || (!syncBehavior.shouldSyncSet && !autoReadEnabled)) return@launch

                    val internalPrimaryClip = primaryClip
                    val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true
                    val item = if (isEqual) {
                        internalPrimaryClip!!
                    } else {
                        ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = true)
                    }
                    if (syncBehavior.shouldSyncSet && !isEqual) {
                        primaryClip = item
                        insertOrMoveBeginning(item)
                    }
                    // This is the event stream, not the deduplicated history stream.
                    // Re-copying identical text is an intentional new event.
                    externalClipFlow.tryEmit(item)
                } catch (e: Exception) {
                    Log.w(TAG, "Clipboard callback failed; next copy may recover", e)
                }
            }
        }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        insertOrMoveBeginning(item)
        updatePrimaryClip(item)
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        val newData = ClipboardItem.text(newText)
        addNewClip(newData)
    }

    /**
     * Copies a media item (given as a readable content [uri], e.g. a downloaded GIF) into the
     * clipboard history and the system primary clip, so it can be pasted into apps that accept
     * images from the clipboard. Used as a fallback when the target editor does not support the
     * Commit Content API. Returns false if the item could not be built.
     */
    fun copyMediaToClipboard(uri: Uri, mimeType: String): Boolean {
        return try {
            val clip = ClipData(
                ClipDescription("media file", arrayOf(mimeType)),
                ClipData.Item(uri),
            )
            val item = ClipboardItem.fromClipData(appContext, clip, cloneUri = true)
            addNewClip(item)
            true
        } catch (e: Exception) {
            flogError { "Failed to copy media to clipboard: ${e.message}" }
            false
        }
    }

    /**
     * Adds a new item to the clipboard history (if enabled).
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        if (prefs.clipboard.historyEnabled.get()) {
            val historyElement = currentHistory.all.firstOrNull { item ->
                item.type == ItemType.TEXT && item.text == newItem.text && item.isSensitive == newItem.isSensitive
            }
            if (historyElement != null) {
                moveToTheBeginning(
                    oldItem = historyElement,
                    newItem = if (historyElement.isPinned) {
                        newItem.copy(isPinned = true)
                    } else {
                        newItem
                    }
                )
            } else {
                insertClip(newItem)
            }
        }
    }

    private fun enforceHistoryLimit(clipHistory: ClipboardHistory) {
        if (prefs.clipboard.historySizeLimitEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val nToRemove = nonPinnedItems.size - prefs.clipboard.historySizeLimit.get()
            if (nToRemove > 0) {
                val itemsToRemove = nonPinnedItems.asReversed().filterIndexed { n, _ -> n < nToRemove }
                deleteWithMedia(itemsToRemove)
            }
        }
    }

    private fun enforceExpiryDate(clipHistory: ClipboardHistory) {
        val itemsToRemove = mutableSetOf<ClipboardItem>()
        if (prefs.clipboard.historyAutoCleanOldEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val expiryTime = System.currentTimeMillis() - (prefs.clipboard.historyAutoCleanOldAfter.get() * 60 * 1000)
            itemsToRemove.addAll(nonPinnedItems.filter { it.creationTimestampMs < expiryTime })
        }
        if (prefs.clipboard.historyAutoCleanSensitiveEnabled.get()) {
            val sensitiveData = clipHistory.all.filter { it.isSensitive }
            val expiryTime = System.currentTimeMillis() - (prefs.clipboard.historyAutoCleanSensitiveAfter.get() * 1000)
            itemsToRemove.addAll(sensitiveData.filter { it.creationTimestampMs < expiryTime })
        }
        deleteWithMedia(itemsToRemove.toList())
    }

    /**
     * Removes [items] from the history and takes their media files with them.
     *
     * The two automatic clean-ups above used to delete the database row and nothing else, so every
     * image and video that aged or fell out of the size limit left its cloned file behind in the
     * app's own storage: invisible in the panel, unreachable from anywhere, and collected by
     * nothing. With the size limit on by default that is the normal path out of the history, which
     * made it the one leak that grows on its own (issue #316).
     */
    private fun deleteWithMedia(items: List<ClipboardItem>) {
        if (items.isEmpty()) return
        ioScope.launch {
            // Row first, file second. If anything goes wrong between the two, what is left over is an
            // unreferenced file — invisible and cleared by the next full wipe — rather than an entry
            // in the panel whose picture has already been deleted underneath it.
            clipHistoryDao?.delete(items)
            for (item in items) {
                item.close(appContext)
            }
        }
    }

    private fun moveToTheBeginning(oldItem: ClipboardItem, newItem: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.delete(oldItem.id)
            clipHistoryDao?.insert(newItem)
        }
    }

    fun insertClip(item: ClipboardItem) {
        ioScope.launch {
            val id = clipHistoryDao?.insert(item)
            item.id = id ?: 0
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        deleteWithMedia(items)
    }

    /**
     * Clears all unpinned items from the clipboard history.
     *
     * Only the unpinned ones are closed. Closing every item and then deleting only the unpinned rows
     * left a pinned image as an entry whose file had just been deleted underneath it — the one thing
     * pinning is supposed to prevent (issue #316).
     */
    fun clearHistory() {
        ioScope.launch {
            for (item in currentHistory.unpinned) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAllUnpinned()
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        ioScope.launch {
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAll()
        }
    }


    /**
     * Restore the clipboard history from a [List]
     *
     * @param items the [ClipboardItem] list with the new items
     */
    fun restoreHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            val currentHistory = currentHistory.all
            for (item in items) {
                if (!currentHistory.map { it.copy(id = 0) }.contains(item.copy(id = 0))) {
                    insertClip(item.copy(id = 0))
                }
            }
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        ioScope.launch {
            if (onlyIfUnpinned) {
                clipHistoryDao?.deleteIfUnpinned(item.id)
            } else {
                clipHistoryDao?.delete(item.id)
            }
            tryOrNull {
                val uri = item.uri
                if (uri != null) {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    fun pinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = true))
        }
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = false))
        }
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        cleanUpJob.cancel()
    }
}
