/*
 * Copyright (C) 2026 The Dictate Kapijuja Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.dictate.data.prefs

import dev.patrickgold.florisboard.dictate.DictatePromptsLayout
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DictatePromptLayoutMigrationTest : FunSpec({
    test("the forced always-on row is restored to the compact prompt panel") {
        DictateLegacyMigrator.shouldRestorePromptsPanel(
            rowMigrationApplied = true,
            currentLayout = DictatePromptsLayout.ROW,
        ) shouldBe true
    }

    test("fresh panel installs and already-corrected choices are left untouched") {
        DictateLegacyMigrator.shouldRestorePromptsPanel(
            rowMigrationApplied = false,
            currentLayout = DictatePromptsLayout.PANEL,
        ) shouldBe false
        DictateLegacyMigrator.shouldRestorePromptsPanel(
            rowMigrationApplied = true,
            currentLayout = DictatePromptsLayout.PANEL,
        ) shouldBe false
    }
})
