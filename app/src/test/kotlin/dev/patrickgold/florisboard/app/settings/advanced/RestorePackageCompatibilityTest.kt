/*
 * Copyright (C) 2026 The Dictate Kapijuja Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.app.settings.advanced

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RestorePackageCompatibilityTest : FunSpec({
    test("Kapijuja and upstream Dictate backups are compatible") {
        Restore.isCompatiblePackage("net.kapijuja.dictate") shouldBe true
        Restore.isCompatiblePackage("net.kapijuja.dictate.debug") shouldBe true
        Restore.isCompatiblePackage("net.devemperor.dictate") shouldBe true
        Restore.isCompatiblePackage("net.devemperor.dictate.beta") shouldBe true
    }

    test("unrelated application backups remain third party") {
        Restore.isCompatiblePackage("org.example.keyboard") shouldBe false
        Restore.isCompatiblePackage("") shouldBe false
    }
})
