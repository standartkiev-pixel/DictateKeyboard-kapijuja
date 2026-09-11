/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DictateSmartbarCountdownColorTest : FunSpec({
    test("countdown advances from active through warning to imminent timeout") {
        transcriptionCountdownColor(Color.White, 1f) shouldBe Color(0xFF64B5F6)
        transcriptionCountdownColor(Color.White, 0.25f) shouldBe Color(0xFFFFCA5C)
        transcriptionCountdownColor(Color.White, 0.10f) shouldBe Color(0xFFFF6B6B)
    }

    test("countdown uses darker contrast colors on a light surface") {
        transcriptionCountdownColor(Color.Black, 1f) shouldBe Color(0xFF1565C0)
        transcriptionCountdownColor(Color.Black, 0.25f) shouldBe Color(0xFF8A5A00)
        transcriptionCountdownColor(Color.Black, 0.10f) shouldBe Color(0xFFB71C1C)
    }
})
