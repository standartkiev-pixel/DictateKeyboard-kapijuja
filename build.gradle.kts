/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

import java.security.MessageDigest
import java.util.Base64

plugins {
    alias(libs.plugins.agp.application) apply false
    alias(libs.plugins.agp.library) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mikepenz.aboutlibraries) apply false
}

// Pinned public prerelease test identity (see .github/test-signing/README.md).
//
// Every `.debug` APK — on CI, in Android Studio or from a plain `./gradlew assembleDebug` on any
// machine — must carry the same certificate, otherwise Android refuses to update an installed test
// build (INSTALL_FAILED_UPDATE_INCOMPATIBLE) and the user has to uninstall and lose local data.
// The machine's own ~/.android/debug.keystore is therefore never used for Kapijuja debug builds.
//
// CI may still pass an already decoded copy through KAPIJUJA_PRERELEASE_DEBUG_KEYSTORE; otherwise the
// keystore is decoded from the repository into build/. Either way its checksum must match, and a
// mismatch fails the build instead of silently producing an APK that cannot update.
val kapijujaPrereleaseKeystoreSha256 = "100a4ef0a4d5d2c702e5b9828f6fd71acb051fd61fc85c98ba14379cfd4700d2"
val kapijujaPrereleaseKeystore: File = run {
    val envPath = System.getenv("KAPIJUJA_PRERELEASE_DEBUG_KEYSTORE")?.takeIf { it.isNotBlank() }
    val keystore = if (envPath != null) {
        file(envPath)
    } else {
        val decoded = Base64.getMimeDecoder().decode(
            file(".github/test-signing/kapijuja-prerelease-debug.keystore.b64").readText().trim()
        )
        layout.buildDirectory.file("kapijuja-signing/prerelease-debug.keystore").get().asFile.also { target ->
            if (!target.isFile || !target.readBytes().contentEquals(decoded)) {
                target.parentFile.mkdirs()
                target.writeBytes(decoded)
            }
        }
    }
    check(keystore.isFile) { "Kapijuja prerelease keystore not found: $keystore" }
    val actual = MessageDigest.getInstance("SHA-256").digest(keystore.readBytes())
        .joinToString("") { "%02x".format(it) }
    check(actual == kapijujaPrereleaseKeystoreSha256) {
        "Kapijuja prerelease keystore checksum mismatch ($actual). Refusing to sign with a different identity."
    }
    keystore
}
extra["kapijujaPrereleaseKeystore"] = kapijujaPrereleaseKeystore
