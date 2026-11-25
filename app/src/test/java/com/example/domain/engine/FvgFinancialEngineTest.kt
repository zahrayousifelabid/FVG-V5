package com.example.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.domain.math.BigDecimal

class FvgFinancialEngineTest {

    @Test
    fun `all 8 costing methods compute deterministic results`() {
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
            assertTrue(outcome.methodNote.isNotBlank())
        }

        val absorption = FvgFinancialEngine.calculateCostingByMethod("absorption", baseInput)
        val variable = FvgFinancialEngine.calculateCostingByMethod("variable", baseInput)
        // Absorption includes fixed costs (1000 / 100 = 10.00); Variable excludes fixed costs (800 / 100 = 8.00)
        assertEquals(BigDecimal("10.00"), absorption.coreResult.unitCost)
        assertEquals(BigDecimal("8.00"), variable.coreResult.unitCost)

        val target = FvgFinancialEngine.calculateCostingByMethod("target", baseInput, targetMarginPct = BigDecimal("40"))
        // Price 15 * (1 - 0.40) = 9.00 allowable unit cost; actual unit cost 8.00 -> -1.00 gap
        assertEquals(BigDecimal("9.00"), target.targetAllowableUnitCost)
        assertEquals(BigDecimal("-1.00"), target.targetCostGapPerUnit)
    }

    @Test
    fun `detailed variance decomposes material price usage and labor rate efficiency`() {
        val res = FvgFinancialEngine.computeDetailedVariance(
            stdMaterialQtyPerUnit = BigDecimal("2"),
            stdMaterialPrice = BigDecimal("10"),
            stdLaborHoursPerUnit = BigDecimal("1"),
            stdLaborRate = BigDecimal("20"),
            stdOverheadPerUnit = BigDecimal("5"),
            goodUnits = BigDecimal("10"),
            actualMaterialQty = BigDecimal("22"),
            actualMaterialPrice = BigDecimal("11"),
            actualLaborHours = BigDecimal("9"),
            actualLaborRate = BigDecimal("22"),
            actualOverheadCost = BigDecimal("50")
        )
        // Mat Price Var = (11 - 10) * 22 = 22.00; Mat Usage Var = (22 - 20) * 10 = 20.00
        assertEquals(BigDecimal("22.00"), res.materialPriceVariance)
        assertEquals(BigDecimal("20.00"), res.materialUsageVariance)
        // Lab Rate Var = (22 - 20) * 9 = 18.00; Lab Eff Var = (9 - 10) * 20 = -20.00
        assertEquals(BigDecimal("18.00"), res.laborRateVariance)
        assertEquals(BigDecimal("-20.00"), res.laborEfficiencyVariance)
        assertEquals(BigDecimal("40.00"), res.summary.totalVariance)
    }

    @Test
    fun `financial risk engine evaluates rules explicitly without hallucination`() {
        val highNeg = FvgFinancialEngine.evaluateFinancialRisk(BigDecimal("-5"))
        assertEquals("high", highNeg.level)
        assertEquals("NEGATIVE_MARGIN_RULE", highNeg.ruleName)

        val medInf = FvgFinancialEngine.evaluateFinancialRisk(BigDecimal("18"), BigDecimal("5"))
        assertEquals("medium", medInf.level)

        val lowHealthy = FvgFinancialEngine.evaluateFinancialRisk(BigDecimal("28"), BigDecimal("4"))
        assertEquals("low", lowHealthy.level)
    }

    @Test
    fun `extended scenario runs FX and volume shocks with disclaimer`() {
        val scen = FvgFinancialEngine.runExtendedScenario(
            baseMaterial = BigDecimal("1000"),
            baseLabor = BigDecimal("500"),
            baseOverheadAndOther = BigDecimal("200"),
            baseRevenue = BigDecimal("2500"),
            materialChangePct = BigDecimal("15"),
            laborChangePct = BigDecimal.ZERO,
            overheadChangePct = BigDecimal.ZERO,
            priceChangePct = BigDecimal.ZERO,
            fxChangePct = BigDecimal("10"),
            volumeChangePct = BigDecimal("20")
        )
        assertEquals("Scenario outcome — not a prediction", scen.disclaimer)
        assertTrue(scen.scenarioTotalCost > scen.baseTotalCost)
    }

    @Test
    fun `strict 168h (7-day) trial countdown preserves legacy 72h and ticks monotonically`() {
        assertEquals(168L, FvgFinancialEngine.TRIAL_DURATION_HOURS)
        val baseStart = 1_700_000_000_000L
        val firstOrgEnd = baseStart + FvgFinancialEngine.TRIAL_DURATION_MILLIS
        val secondOrgClaimedEnd = baseStart + 240L * 3600_000L
        val capped = FvgFinancialEngine.computeServerSyncedTrialEndMillis(baseStart, secondOrgClaimedEnd)
        assertNotNull(capped)
        assertEquals(firstOrgEnd, capped!!)

        val active = FvgFinancialEngine.computeMonotonicTrialCountdown(
            trialEndsAtMillis = firstOrgEnd,
            anchorServerNowMillis = baseStart,
            elapsedDeltaMillis = 3600_000L // +1 hour elapsed -> 167 hours left (6d 23h)
        )
        assertFalse(active.isExpired)
        assertEquals(6L, active.days)
        assertEquals(23L, active.hours)
        assertEquals(167L, active.totalRemainingHours)

        // Legacy 72h account preservation without auto-extension
        val legacyEnd = FvgFinancialEngine.computeServerSyncedTrialEndMillis(
            userTrialStartMillis = baseStart,
            orgTrialEndsAtMillis = baseStart + 72L * 3600_000L,
            durationHours = 72L
        )
        assertEquals(baseStart + 72L * 3600_000L, legacyEnd)

        val expired = FvgFinancialEngine.computeMonotonicTrialCountdown(
            trialEndsAtMillis = firstOrgEnd,
            anchorServerNowMillis = baseStart,
            elapsedDeltaMillis = FvgFinancialEngine.TRIAL_DURATION_MILLIS
        )
        assertTrue(expired.isExpired)
        assertEquals(0L, expired.remainingMillis)

        // Missing denominators return null ratio rather than fake zero
        val zeroStatements = FvgFinancialEngine.generateFinancialStatements(emptyList(), emptyList())
        val ratios = FvgFinancialEngine.computeFinancialRatios(zeroStatements)
        assertNull(ratios.first { it.key == "current_ratio" }.value)
    }

    @Test
    fun `3D calculator evaluates precedence parentheses percentages and rejects divide by zero`() {
        assertEquals("14", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("2 + 3 × 4")))
        assertEquals("20", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("(2 + 3) × 4")))
        assertEquals("250", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("1000 × 25%")))
        assertEquals("5", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("15 ÷ 3")))
        assertThrows(ArithmeticException::class.java) {
            FvgFinancialEngine.evaluateCalculatorExpression("10 ÷ 0")
        }
    }

    @Test
    fun `BOM recipe builder computes net and scrap allowance unit material cost`() {
        val bom = FvgFinancialEngine.calculateBomUnitCost(
            listOf(
                FvgFinancialEngine.BomLineInput("Raw Flour", BigDecimal("0.5"), BigDecimal("100"), BigDecimal("10")),
                FvgFinancialEngine.BomLineInput("Sugar", BigDecimal("0.2"), BigDecimal("150"), BigDecimal.ZERO)
            )
        )
        // Net = 50 + 30 = 80.00; Scrap = 10% of 50 = 5.00; Total = 85.00
        assertEquals(BigDecimal("80.00"), bom.netMaterialCostPerUnit)
        assertEquals(BigDecimal("5.00"), bom.scrapAllowanceCostPerUnit)
        assertEquals(BigDecimal("85.00"), bom.effectiveMaterialCostPerUnit)
    }

    @Test
    fun `FX freshness and OCR arithmetic validation detect stale rates and mismatches`() {
        val fresh = FvgFinancialEngine.assessFxRateFreshness("2026-11-18", "approved", todayEpochDays = 20776L)
        assertFalse(fresh.isStale)
        assertFalse(fresh.isUnapproved)

        val staleDraft = FvgFinancialEngine.assessFxRateFreshness("2026-11-01", "draft", todayEpochDays = 20776L)
        assertTrue(staleDraft.isStale)
        assertTrue(staleDraft.isUnapproved)

        val ocrOk = FvgFinancialEngine.validateOcrArithmetic("1000", "50", "150", "1100.00")
        assertTrue(ocrOk.isConsistent)
        assertEquals(BigDecimal("1100.00"), ocrOk.expectedTotal)

        val ocrBad = FvgFinancialEngine.validateOcrArithmetic("1000", "50", "150", "1250.00")
        assertFalse(ocrBad.isConsistent)
        assertEquals(BigDecimal("150.00"), ocrBad.discrepancy)
    }

    @Test
    fun `owner formula validation suite passes all 10 standardized checks and health score works`() {
        val checks = FvgFinancialEngine.runFormulaValidationSuite()
        assertEquals(10, checks.size)
        val failed = checks.filter { !it.passed }
        assertTrue(
            "Failed checks: " + failed.joinToString { "${it.id}(exp=${it.expectedOutput}, act=${it.actualOutput})" },
            failed.isEmpty()
        )

        val health = FvgFinancialEngine.computeFinancialHealthScore(
            stmt = null,
            costSnapshots = emptyList(),
            latestInflationPct = BigDecimal("18")
        )
        assertTrue(health.score in 0..100)
        assertTrue(health.grade in listOf("A", "B", "C", "D"))

        assertTrue(FvgFinancialEngine.isAccountingOrFvgTopic("ما هو هامش الربح في دليل رؤيتك المالي؟"))
        assertFalse(FvgFinancialEngine.isAccountingOrFvgTopic("ما هو انواع الزهور"))
    }

    @Test
    fun `sudan bankak and fawry sms parser and receipt auto-matcher handle arabic digits and duplicates`() {
        val bankakSms = "بنك الخرطوم (بنكك): تم تحويل مبلغ ٦٥٬٠٠٠.٠٠ ج.س رقم العملية BOK-99887766 من السيد: أحمد محمد بتاريخ 2026-11-20"
        val parsedBankak = FvgFinancialEngine.parseSudanTransferSmsOrText(bankakSms)
        assertEquals("bankak", parsedBankak.paymentMethod)
        assertEquals("SDG", parsedBankak.currency)
        assertEquals("65000.00", parsedBankak.amount)
        assertEquals("BOK-99887766", parsedBankak.referenceNo)
        assertEquals("2026-11-20", parsedBankak.transferDate)

        val fawrySms = "Fawry Faisal transfer completed: Amount 48 USD Ref No: FWR-554433 Date 20/11/2026"
        val parsedFawry = FvgFinancialEngine.parseSudanTransferSmsOrText(fawrySms)
        assertEquals("fawry", parsedFawry.paymentMethod)
        assertEquals("USD", parsedFawry.currency)
        assertEquals("48", parsedFawry.amount)
        assertEquals("FWR-554433", parsedFawry.referenceNo)
        assertEquals("2026-11-20", parsedFawry.transferDate)

        val exact = FvgFinancialEngine.evaluatePaymentReceiptMatch(
            parsedAmountStr = "65000.00",
            parsedCurrency = "SDG",
            parsedRef = "BOK-99887766",
            expectedPlanUsd = "25",
            expectedPlanSdg = "65000",
            existingRefs = setOf("OLD-1111")
        )
        assertEquals("exact_match", exact.status)

        val close = FvgFinancialEngine.evaluatePaymentReceiptMatch(
            parsedAmountStr = "66300",
            parsedCurrency = "SDG",
            parsedRef = "BOK-99887799",
            expectedPlanUsd = "25",
            expectedPlanSdg = "65000",
            existingRefs = emptySet()
        )
        assertEquals("close_match", close.status)

        val dup = FvgFinancialEngine.evaluatePaymentReceiptMatch(
            parsedAmountStr = "65000",
            parsedCurrency = "SDG",
            parsedRef = "bok-99887766",
            expectedPlanUsd = "25",
            expectedPlanSdg = "65000",
            existingRefs = setOf("BOK-99887766")
        )
        assertEquals("duplicate_ref", dup.status)

        val mismatch = FvgFinancialEngine.evaluatePaymentReceiptMatch(
            parsedAmountStr = "30000",
            parsedCurrency = "SDG",
            parsedRef = "BOK-112233",
            expectedPlanUsd = "25",
            expectedPlanSdg = "65000",
            existingRefs = emptySet()
        )
        assertEquals("mismatch", mismatch.status)
    }

    @Test
    fun `13-week cash runway stress test detects liquidity depletion under severe shock and survival at baseline`() {
        val healthy = FvgFinancialEngine.compute13WeekCashRunway(
            startingCash = BigDecimal("100000"),
            weeklyBaselineInflow = BigDecimal("35000"),
            weeklyBaselineOutflow = BigDecimal("25000"),
            openReceivables = BigDecimal("20000"),
            openPayables = BigDecimal("8000"),
            fxShockPct = BigDecimal.ZERO,
            salesDropPct = BigDecimal.ZERO,
            arDelayWeeks = 0
        )
        assertTrue(healthy.survives13Weeks)
        assertEquals(14, healthy.runwayWeeks)
        assertEquals("green", healthy.riskLevel)
        assertEquals(13, healthy.weeks.size)

        val stressed40 = FvgFinancialEngine.compute13WeekCashRunway(
            startingCash = BigDecimal("40000"),
            weeklyBaselineInflow = BigDecimal("30000"),
            weeklyBaselineOutflow = BigDecimal("32000"),
            openReceivables = BigDecimal.ZERO,
            openPayables = BigDecimal("4000"),
            fxShockPct = BigDecimal("30"),
            salesDropPct = BigDecimal("40"),
            arDelayWeeks = 2
        )
        assertFalse(stressed40.survives13Weeks)
        assertTrue(stressed40.runwayWeeks in 1..13)
        assertEquals("red", stressed40.riskLevel)
        assertTrue(stressed40.endingCashWeek13 < healthy.endingCashWeek13)
    }

    @Test
    fun `multi-branch consolidation ranks best margin and highest waste while preserving profit on eliminations`() {
        val branchA = FvgFinancialEngine.BranchFinancialInput(
            orgId = "org_a",
            branchName = "Khartoum Factory",
            currency = "SDG",
            revenue = BigDecimal("100000"),
            cogs = BigDecimal("60000"),
            opEx = BigDecimal("10000"),
            scrapLoss = BigDecimal("5000"),
            assets = BigDecimal("200000"),
            liabilities = BigDecimal("80000")
        )
        val branchB = FvgFinancialEngine.BranchFinancialInput(
            orgId = "org_b",
            branchName = "Port Sudan Branch",
            currency = "SDG",
            revenue = BigDecimal("80000"),
            cogs = BigDecimal("40000"),
            opEx = BigDecimal("8000"),
            scrapLoss = BigDecimal("1200"),
            assets = BigDecimal("150000"),
            liabilities = BigDecimal("50000")
        )
        val result = FvgFinancialEngine.consolidateMultiBranchPerformance(
            branches = listOf(branchA, branchB),
            intercompanyRevenueCost = BigDecimal("20000"),
            intercompanyArAp = BigDecimal("15000")
        )
        assertEquals(2, result.branchCount)
        assertEquals(BigDecimal("180000.00"), result.grossGroupRevenue)
        assertEquals(BigDecimal("20000.00"), result.intercompanyEliminated)
        assertEquals(BigDecimal("160000.00"), result.consolidatedRevenue)
        assertEquals(BigDecimal("80000.00"), result.consolidatedCogs)
        // Net profit unchanged at (30k + 32k) = 62,000.00 after equal Rev & COGS elimination
        assertEquals(BigDecimal("62000.00"), result.consolidatedNetProfit)
        assertEquals(BigDecimal("6200.00"), result.totalScrapLoss)
        assertEquals(BigDecimal("335000.00"), result.consolidatedAssets)
        assertEquals(BigDecimal("115000.00"), result.consolidatedLiabilities)
        assertEquals("Port Sudan Branch", result.bestMarginBranch)
        assertEquals("Khartoum Factory", result.highestWasteBranch)
    }
}


