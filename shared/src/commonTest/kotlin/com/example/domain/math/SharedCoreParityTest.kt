package com.example.domain.math

import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.json.FvgJsonObject
import com.example.platform.FvgDateTime
import com.example.platform.FvgSha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedCoreParityTest {

    @Test
    fun bigDecimalArithmeticAndRoundingMatchSpec() {
        assertEquals("123.46", BigDecimal("123.455").setScale(2, RoundingMode.HALF_UP).toPlainString())
        assertEquals("-123.46", BigDecimal("-123.455").setScale(2, RoundingMode.HALF_UP).toPlainString())
        assertEquals("4", BigDecimal("10").divide(BigDecimal("3"), 0, RoundingMode.CEILING).toPlainString())
        assertEquals("0.3333", BigDecimal("1").divide(BigDecimal("3"), 4, RoundingMode.HALF_UP).toPlainString())

        val q = BigDecimal("100").divide(BigDecimal("10"), FvgFinancialEngine.MC)
        assertEquals(0, q.compareTo(BigDecimal("10")))
        assertEquals("10", q.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString())

        assertEquals(BigDecimal("8.00"), BigDecimal("8.000").setScale(2, RoundingMode.HALF_UP))
        assertTrue(BigDecimal("10.50") > BigDecimal("10.49"))
        assertEquals("1000", BigDecimal("1E+3").setScale(0).toPlainString())
    }

    @Test
    fun financialEngineRunsIdenticallyInCommonMain() {
        val baseInput = FvgFinancialEngine.CostingInput(
            directMaterial = BigDecimal("500"),
            directLabor = BigDecimal("200"),
            overhead = BigDecimal("100"),
            fixedCosts = BigDecimal("200"),
            goodUnits = BigDecimal("100"),
            sellingPrice = BigDecimal("15")
        )
        for (method in FvgFinancialEngine.ALL_COSTING_METHODS) {
            val outcome = FvgFinancialEngine.calculateCostingByMethod(
                method = method,
                input = baseInput,
                targetMarginPct = BigDecimal("25"),
                completedEquivUnits = BigDecimal("80")
            )
            assertEquals(method, outcome.method)
            assertTrue(outcome.coreResult.unitCost > BigDecimal.ZERO)
        }
        val absorption = FvgFinancialEngine.calculateCostingByMethod("absorption", baseInput)
        val variable = FvgFinancialEngine.calculateCostingByMethod("variable", baseInput)
        assertEquals("10.00", absorption.coreResult.unitCost.toPlainString())
        assertEquals("8.00", variable.coreResult.unitCost.toPlainString())
    }

    @Test
    fun dateJsonAndSha256WorkDeterministically() {
        assertEquals("2025-03-15", FvgDateTime.todayIsoDate(FvgDateTime.parseIsoDateToEpochMillis("2025-03-15")!!))
        val d1 = FvgDateTime.parseIsoDateToEpochDays("2026-02-28")!!
        val d2 = FvgDateTime.parseIsoDateToEpochDays("2026-03-01")!!
        assertEquals(1L, d2 - d1)

        val obj = FvgJsonObject("""{"org":"FVG \"Guide\"","count":42,"ok":true,"arr":[{"k":"v"}]}""")
        assertEquals("FVG \"Guide\"", obj.optString("org"))
        assertEquals(42L, obj.optLong("count"))
        assertTrue(obj.optBoolean("ok"))
        assertEquals("v", obj.optJSONArray("arr")?.getJSONObject(0)?.optString("k"))
        assertFalse(FvgSha256.digestHex("abc").isBlank())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FvgSha256.digestHex("abc"))
    }
}
