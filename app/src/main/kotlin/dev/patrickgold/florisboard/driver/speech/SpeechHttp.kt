/*
 * Copyright (C) 2026 Kapijuja contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.driver.speech

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** HTTP plumbing shared by the cloud speech sources. Nothing here ever logs a body or a header. */
internal object SpeechHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
    }

    /**
     * Executes [request] and hands the response to [block], cancelling the HTTP call the moment the
     * calling coroutine is cancelled, so a stop also closes the socket instead of letting a paid
     * stream run to its end in the background.
     */
    suspend fun <T> execute(request: Request, block: suspend (Response) -> T): T = withContext(Dispatchers.IO) {
        val call: Call = client.newCall(request)
        val finished = AtomicBoolean(false)
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) call.cancel()
            }
        }
        try {
            call.execute().use { response -> block(response) }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw SpeechEngineException("Network error: ${e.javaClass.simpleName}", billingMayApply = true, cause = e)
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }

    /** Streams the body of a successful [response] to [sink] in blocks, as the bytes arrive. */
    suspend fun streamBody(response: Response, sink: suspend (ByteArray, Int) -> Unit) {
        val body = response.body ?: throw SpeechEngineException("Empty response", billingMayApply = true)
        body.byteStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) sink(buffer, n)
            }
        }
    }

    /** Throws a user-presentable error for a non-2xx [response], with a short provider message. */
    fun requireSuccess(provider: String, response: Response) {
        if (response.isSuccessful) return
        val detail = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
            .replace(Regex("\\s+"), " ")
            .take(200)
        val hint = when (response.code) {
            400 -> "request rejected"
            401 -> "invalid API key"
            403 -> "key has no access (permissions or billing)"
            404 -> "model, voice or region not found"
            429 -> "rate or billing limit reached"
            in 500..599 -> "temporary server error"
            else -> "error"
        }
        throw SpeechEngineException(
            "$provider HTTP ${response.code}: $hint" + if (detail.isNotEmpty()) " — $detail" else "",
            billingMayApply = response.code >= 500,
        )
    }
}
