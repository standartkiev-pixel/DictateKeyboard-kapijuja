/*
 * Copyright (C) 2026 Kapijuja contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests the credential records that the unfiltered JetPref application-settings backup
 * exports/imports. Fake test keys only; real keys must never enter source control or logs.
 */
class BackupCredentialsContractTest : FunSpec({
    test("dictation provider keyring serializer retains API keys and model choices") {
        val example = ProviderAccounts.Empty
            .put(ProviderAccount(providerId = "openai", apiKey = "test-openai-key", transcriptionModel = "gpt-4o-transcribe"))
            .put(ProviderAccount(providerId = "deepgram", apiKey = "test-deepgram-key"))
        val restored = ProviderAccounts.Serializer.deserialize(ProviderAccounts.Serializer.serialize(example))
        restored.getOrEmpty("openai").apiKey shouldBe "test-openai-key"
        restored.getOrEmpty("deepgram").apiKey shouldBe "test-deepgram-key"
        restored.getOrEmpty("openai").transcriptionModel shouldBe "gpt-4o-transcribe"
    }

    test("speech provider credentials are ordinary JetPref settings included in full backup") {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        // These are not transient UI-only values, external key stores or install-only constants.
        // BackupScreen exports the whole FlorisPreferenceStore when its default checkbox is ON.
        prefs.speech.openAiApiKey.get() shouldBe ""
        prefs.speech.elevenApiKey.get() shouldBe ""
        prefs.speech.inworldApiKey.get() shouldBe ""
        prefs.speech.azureKey.get() shouldBe ""
        prefs.speech.googleApiKey.get() shouldBe ""
        prefs.speech.xaiApiKey.get() shouldBe ""
    }
})
