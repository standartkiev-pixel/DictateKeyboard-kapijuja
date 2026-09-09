/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package dev.patrickgold.florisboard.dictate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RecoveryFileNameTest : FunSpec({
    test("recovery filename round-trips process-death metadata") {
        val name = DictateController.recoveryFileName(
            createdAtMs = 1_789_000_123_456L,
            seconds = 137L,
            wasLive = true,
            extension = "OGG",
        )

        name shouldBe "dictate_recovery_1789000123456_137_1.ogg"
        DictateController.recoveryFileMeta(name) shouldBe DictateController.RecoveryFileMeta(
            createdAtMs = 1_789_000_123_456L,
            seconds = 137L,
            wasLive = true,
        )
    }

    test("malformed recovery filenames are ignored") {
        DictateController.recoveryFileMeta("dictate_recovery_bad_12_0.wav") shouldBe null
        DictateController.recoveryFileMeta("dictate_recovery_1789000123456_-1_0.wav") shouldBe null
        DictateController.recoveryFileMeta("dictate_recovery_1789000123456_12_2.wav") shouldBe null
        DictateController.recoveryFileMeta("dictate_cancelled_1789000123456_12_0.wav") shouldBe null
    }
})
