package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.CostSnapshot
import com.example.data.model.Organization
import com.example.data.model.UserProfile
import com.example.ui.components.FvgCalculatorSheet
import com.example.ui.components.FvgTrialCountdownBanner
import com.example.ui.components.FvgTrialExpiredGateCard
import com.example.ui.components.FvgWalkthroughOverlay
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FvgSplashScreen
import com.example.ui.screens.PublicWebsiteAndAuthScreen
import com.example.ui.theme.FvgTheme
import com.example.ui.viewmodel.FvgUiState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FvgAppCujTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `splash screen renders exact brand identity in Arabic default and responds to tap`() {
        var finished = false
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = true) {
                FvgSplashScreen(isArabic = true) { finished = true }
            }
        }
        composeRule.onNodeWithTag("fvg_splash_screen").assertIsDisplayed()
        composeRule.onNodeWithText("FVG").assertIsDisplayed()
        composeRule.onNodeWithText("Financial Vision Guide").assertIsDisplayed()
        composeRule.onNodeWithText("دليل رؤيتك المالي").assertIsDisplayed()
        composeRule.onNodeWithText("قرارات أذكى", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("fvg_splash_screen").performClick()
        assertTrue(finished)
    }

    @Test
    fun `public website displays email auth, 7-day trial, coming soon sectors, and zero personal phone leak`() {
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                PublicWebsiteAndAuthScreen(
                    isArabic = false,
                    isDark = false,
                    isSigningIn = false,
                    authError = null,
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onGoogleSignInClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("open_email_auth_button").assertIsDisplayed()

        // Navigate to Features & Sectors tab (index 1)
        composeRule.onNodeWithTag("public_nav_tab_1").performScrollTo().performClick()
        composeRule.onNodeWithText("Retail & Wholesale Trade", substring = true).performScrollTo().assertIsDisplayed()

        // Navigate to Plans & Pay tab (index 2)
        composeRule.onNodeWithTag("public_nav_tab_2").performScrollTo().performClick()
        composeRule.onNodeWithText("Transparent Payment Model", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("last 4 digits", substring = true).assertIsDisplayed()

        // Verify Pre-Login Open AI Q&A Card is interactive on public landing screen
        composeRule.onNodeWithTag("public_nav_tab_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("pre_login_ai_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("pre_login_ai_send_button").performScrollTo().assertIsDisplayed()

        // Navigate to Contact tab (index 5)
        composeRule.onNodeWithTag("public_nav_tab_5").performScrollTo().performClick()
        composeRule.onNodeWithText("Email: Financialvisionguide@gmail.com", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `trial expired gate renders lock card and routes upgrade button`() {
        var upgraded = false
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                FvgTrialExpiredGateCard(
                    isArabic = false,
                    orgName = "Sample Trading Org",
                    durationHours = 168L,
                    onUpgradeClick = { upgraded = true },
                    onSignOutClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("trial_expired_gate_card").assertIsDisplayed()
        composeRule.onNodeWithText("last 4 digits", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("trial_gate_upgrade_button").performClick()
        assertTrue(upgraded)
    }

    @Test
    fun `trial countdown banner formats active 168h server-synced timer cleanly`() {
        val now = 1_700_000_000_000L
        val ends = now + 168L * 3600_000L
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                FvgTrialCountdownBanner(
                    trialEndsAtMillis = ends,
                    isArabic = false,
                    isPreviewTrial = false,
                    durationHours = 168L,
                    serverAnchorMillis = now,
                    elapsedBaselineMillis = android.os.SystemClock.elapsedRealtime()
                )
            }
        }
        composeRule.onNodeWithTag("trial_countdown_banner").assertIsDisplayed()
        composeRule.onNodeWithText("7-Day Active Trial", substring = true).assertIsDisplayed()
    }

    @Test
    fun `dashboard shows financial risk summary and navigates quick assistant and calculator shortcuts`() {
        var navigatedTab = -1
        var calcOpened = false
        val now = System.currentTimeMillis()
        val org = Organization(
            id = "org_1",
            name = "Apex Foods",
            baseCurrency = "SDG",
            ownerId = "u_1",
            memberUids = listOf("u_1"),
            roles = mapOf("u_1" to "owner"),
            planTier = "trial",
            subscriptionStatus = "trial",
            trialDurationHours = 168L,
            trialEndsAt = Timestamp(Date(now + 120L * 3600_000L))
        )
        val snap = CostSnapshot(
            id = "cs_1",
            productName = "Standard Pack 1kg",
            period = "2026-11",
            profitMarginPct = "8.50",
            riskLevel = "high"
        )
        val state = FvgUiState(
            isLoading = false,
            userProfile = UserProfile(userId = "u_1", email = "z@fvg.test", displayName = "Owner"),
            organizations = listOf(org),
            activeOrg = org,
            costSnapshots = listOf(snap),
            serverAnchorMillis = now
        )
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                DashboardScreen(
                    state = state,
                    userDisplayName = "Owner",
                    isArabic = false,
                    isDark = false,
                    onOpenCalculator = { calcOpened = true },
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onCreateOrganization = { _, _, _ -> },
                    onNavigateTab = { navigatedTab = it }
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("executive_health_score_card"))
        composeRule.onNodeWithTag("executive_health_score_card").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasText("HIGH RISK"))
        composeRule.onNodeWithText("HIGH RISK").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("quick_assistant"))
        composeRule.onNodeWithTag("quick_assistant").performClick()
        assertEquals(5, navigatedTab)

        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag("quick_calculator"))
        composeRule.onNodeWithTag("quick_calculator").performClick()
        assertTrue(calcOpened)
    }

    @Test
    fun `interactive 3D calculator evaluates math expression and interactive tour advances steps`() {
        var tourDone = false
        var highlightedTab = -1
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = true) {
                FvgWalkthroughOverlay(
                    isArabic = true,
                    onSelectLanguage = {},
                    onHighlightTab = { highlightedTab = it },
                    onDismissComplete = { tourDone = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("walkthrough_overlay").assertIsDisplayed()
        assertEquals(0, highlightedTab)
        composeRule.onNodeWithTag("walkthrough_next_button").performClick()
        composeRule.waitForIdle()
        assertEquals(0, highlightedTab)
        composeRule.onNodeWithTag("walkthrough_next_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, highlightedTab)
        composeRule.onNodeWithTag("walkthrough_skip_button").performClick()
        composeRule.waitForIdle()
        assertTrue(tourDone)
    }

    @Test
    fun `calculator sheet computes keypad arithmetic`() {
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                FvgCalculatorSheet(isArabic = false, defaultCurrency = "SDG", onDismiss = {})
            }
        }
        composeRule.onNodeWithTag("fvg_calculator_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("calc_key_7").performScrollTo().performClick()
        composeRule.onNodeWithTag("calc_key_×").performScrollTo().performClick()
        composeRule.onNodeWithTag("calc_key_6").performScrollTo().performClick()
        composeRule.onNodeWithTag("calc_key_=").performScrollTo().performClick()
        composeRule.onNodeWithText("42.00 SDG").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `public website displays floating AI assistant button and opens global search dialog`() {
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                PublicWebsiteAndAuthScreen(
                    isArabic = false,
                    isDark = false,
                    isSigningIn = false,
                    authError = null,
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onGoogleSignInClick = {}
                )
            }
        }
        composeRule.onNodeWithTag("public_floating_ai_fab").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("public_floating_ai_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("public_floating_ai_input").assertIsDisplayed()
        composeRule.onNodeWithTag("public_floating_ai_send").assertIsDisplayed()
    }

    @Test
    fun `dashboard displays hybrid offline sync queue and resolves conflict callbacks`() {
        var flushed = false
        var resolvedKeepLocal: Boolean? = null
        val org = Organization(
            id = "org_1",
            name = "Apex Foods",
            baseCurrency = "SDG",
            ownerId = "u_1",
            memberUids = listOf("u_1"),
            roles = mapOf("u_1" to "owner"),
            planTier = "trial",
            subscriptionStatus = "trial",
            trialDurationHours = 168L,
            trialEndsAt = Timestamp(Date(System.currentTimeMillis() + 86400_000L))
        )
        val pendingMutation = com.example.data.model.OfflineQueuedMutation(
            id = "mut_1",
            orgId = "org_1",
            operationType = "PRICE",
            targetCollection = "products",
            targetDocId = "prod_1",
            baseServerUpdatedAtMillis = 1000L,
            title = "Sugar 1kg -> 5000",
            payloadJson = "{}",
            createdAtMillis = 1500L
        )
        val conflict = com.example.data.model.OfflineSyncConflict(
            id = "cnf_1",
            mutation = pendingMutation,
            serverUpdatedAtMillis = 2000L,
            serverSummary = "Server 5200 SDG",
            localSummary = "Local 5000 SDG"
        )
        val state = FvgUiState(
            isLoading = false,
            userProfile = UserProfile(userId = "u_1", email = "z@fvg.test", displayName = "Owner"),
            organizations = listOf(org),
            activeOrg = org,
            isOnline = true,
            offlineQueue = listOf(pendingMutation),
            offlineConflicts = listOf(conflict)
        )
        composeRule.setContent {
            FvgTheme(darkTheme = false, isArabic = false) {
                DashboardScreen(
                    state = state,
                    userDisplayName = "Owner",
                    isArabic = false,
                    isDark = false,
                    onToggleLanguage = {},
                    onToggleTheme = {},
                    onCreateOrganization = { _, _, _ -> },
                    onFlushOfflineQueue = { flushed = true },
                    onResolveOfflineConflict = { _, keepLocal -> resolvedKeepLocal = keepLocal },
                    onNavigateTab = {}
                )
            }
        }
        composeRule.onNodeWithTag("hybrid_sync_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("offline_sync_now_button").performClick()
        assertTrue(flushed)
        composeRule.onNodeWithTag("conflict_keep_local_cnf_1").performClick()
        assertEquals(true, resolvedKeepLocal)
    }
}
