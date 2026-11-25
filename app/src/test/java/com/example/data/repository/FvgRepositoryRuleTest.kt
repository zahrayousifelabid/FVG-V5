package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.engine.FvgFinancialEngine
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.domain.math.BigDecimal
import java.util.Date
import java.util.UUID

class FvgRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun randomEmail(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}@fvg.test"

    @Test
    fun `unauthenticated user cannot observe organizations and receives PERMISSION_DENIED`() = runBlocking {
        auth.signOut()
        val repo = FvgRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeOrganizations("anonymous_uid").first()
            }
        } catch (e: Exception) {
            captured = e
        }
        val fsErr = captured?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for unauthenticated list", fsErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr!!.code)
    }

    @Test
    fun `createOrganization initializes immutable 168h (7-day) trial and blocks trial extension tampering`() = runBlocking {
        val aliceEmail = randomEmail("alice")
        val aliceUid = signInTestUser(aliceEmail)
        val repo = FvgRepository(firestore)

        withTimeout(OP_TIMEOUT_MS) {
            repo.ensureUserProfile(aliceUid, aliceEmail, "FVG Business Owner").getOrThrow()
        }
        val orgId = withTimeout(OP_TIMEOUT_MS) {
            repo.createOrganization(aliceUid, aliceEmail, "FVG Test Co", "SDG", "manufacturing").getOrThrow()
        }

        val orgSnap = withTimeout(OP_TIMEOUT_MS) {
            firestore.collection("organizations").document(orgId).get().await()
        }
        assertEquals(168L, orgSnap.getLong("trialDurationHours"))
        assertEquals("trial", orgSnap.getString("subscriptionStatus"))
        assertNotNull(orgSnap.getTimestamp("trialStartedAt"))
        assertNotNull(orgSnap.getTimestamp("trialEndsAt"))

        // Tampering with trialEndsAt must fail with PERMISSION_DENIED
        var tamperErr: FirebaseFirestoreException? = null
        try {
            withTimeout(OP_TIMEOUT_MS) {
                firestore.collection("organizations").document(orgId).update(
                    mapOf("trialEndsAt" to Timestamp(Date(System.currentTimeMillis() + 30L * 86400_000L)))
                ).await()
            }
        } catch (e: Exception) {
            tamperErr = e.unwrapFirestoreException()
        }
        assertNotNull("Expected rule denial when extending trialEndsAt", tamperErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, tamperErr!!.code)
    }

    @Test
    fun `cross-organization isolation blocks outsider from reading org or products`() = runBlocking {
        val aliceEmail = randomEmail("owner")
        val bobEmail = randomEmail("outsider")
        val aliceUid = signInTestUser(aliceEmail)
        val repo = FvgRepository(firestore)

        val orgId = withTimeout(OP_TIMEOUT_MS) {
            repo.createOrganization(aliceUid, aliceEmail, "Isolated Factory", "SDG").getOrThrow()
        }
        val org = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeOrganizations(aliceUid).first { list -> list.any { it.id == orgId } }
                .first { it.id == orgId }
        }

        withTimeout(OP_TIMEOUT_MS) {
            repo.recordProductAndPriceObservation(
                org = org,
                actorUid = aliceUid,
                actorEmail = aliceEmail,
                existingProduct = null,
                name = "Raw Sugar 50kg",
                sku = "SUG-50",
                category = "Raw Material",
                description = "Input sack",
                unit = "kg",
                currency = "SDG",
                location = "Khartoum North",
                source = "Market",
                newPriceStr = "45000",
                observationDate = "2026-11-15",
                notes = "Initial price"
            ).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        var crossOrgErr: FirebaseFirestoreException? = null
        try {
            withTimeout(OP_TIMEOUT_MS) {
                firestore.collection("organizations").document(orgId).get().await()
            }
        } catch (e: Exception) {
            crossOrgErr = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED on cross-org document get", crossOrgErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossOrgErr!!.code)

        var crossListErr: FirebaseFirestoreException? = null
        try {
            withTimeout(OP_TIMEOUT_MS) {
                firestore.collection("organizations").document(orgId).collection("products").get().await()
            }
        } catch (e: Exception) {
            crossListErr = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED on unfiltered cross-org products list", crossListErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossListErr!!.code)

        val bobProducts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeProducts(orgId, bobUid).first()
        }
        assertTrue("Expected zero leaked products for outsider filtered query", bobProducts.isEmpty())
    }

    @Test
    fun `cost snapshots BOM formulas payment requests and support tickets enforce workflow and RBAC`() = runBlocking {
        val ownerEmail = randomEmail("owner2")
        val ownerUid = signInTestUser(ownerEmail)
        val repo = FvgRepository(firestore)

        val orgId = withTimeout(OP_TIMEOUT_MS) {
            repo.createOrganization(ownerUid, ownerEmail, "Manufacturing Lab", "SDG", "manufacturing").getOrThrow()
        }
        val org = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeOrganizations(ownerUid).first { it.any { o -> o.id == orgId } }.first { it.id == orgId }
        }

        val snapshotId = withTimeout(OP_TIMEOUT_MS) {
            repo.saveCostSnapshot(
                org = org,
                actorUid = ownerUid,
                actorEmail = ownerEmail,
                productId = "p_1",
                productName = "Standard Loaf",
                period = "2026-11",
                currency = "SDG",
                costingMethod = "absorption",
                input = FvgFinancialEngine.CostingInput(
                    directMaterial = BigDecimal("500"),
                    directLabor = BigDecimal("200"),
                    overhead = BigDecimal("100"),
                    fixedCosts = BigDecimal("200"),
                    goodUnits = BigDecimal("100"),
                    sellingPrice = BigDecimal("15")
                )
            ).getOrThrow()
        }
        assertTrue(snapshotId.isNotBlank())

        val bomId = withTimeout(OP_TIMEOUT_MS) {
            repo.saveBomFormula(
                org = org,
                actorUid = ownerUid,
                actorEmail = ownerEmail,
                existingId = null,
                productName = "Standard Loaf",
                outputUnit = "unit",
                batchSize = "100",
                ingredients = listOf(mapOf("materialName" to "Flour", "qtyPerUnit" to "0.25", "unitCost" to "800")),
                unitMaterialCost = "200.00",
                scrapAllowancePct = "2.00",
                notes = "Baseline recipe"
            ).getOrThrow()
        }
        assertTrue(bomId.isNotBlank())

        val payId = withTimeout(OP_TIMEOUT_MS) {
            repo.submitPaymentRequest(
                org = org,
                actorUid = ownerUid,
                actorEmail = ownerEmail,
                planTier = "professional",
                paymentMethod = "bankak",
                amountStr = "29.00",
                currency = "USD",
                senderName = "Test Owner",
                referenceNo = "BNK-558811",
                transferDate = "2026-11-15",
                notes = "Last 4 digits: 8811",
                last4Digits = "8811"
            ).getOrThrow()
        }
        val payment = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePaymentRequests(orgId, ownerUid).first { it.any { r -> r.id == payId } }.first { r -> r.id == payId }
        }
        assertEquals("pending_verification", payment.status)
        assertEquals("8811", payment.last4Digits)

        // Regular org owner MUST NOT be allowed to self-approve payment request (only Platform Owner can)
        var selfApproveErr: FirebaseFirestoreException? = null
        try {
            withTimeout(OP_TIMEOUT_MS) {
                repo.reviewPaymentRequestAsOwner(payment, "approved", "Self approve attempt", ownerUid, ownerEmail).getOrThrow()
            }
        } catch (e: Exception) {
            selfApproveErr = e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED when org owner attempts payment self-approval", selfApproveErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, selfApproveErr!!.code)

        val ticketId = withTimeout(OP_TIMEOUT_MS) {
            repo.submitSupportTicket(
                actorUid = ownerUid,
                senderName = "Test Owner",
                senderEmail = ownerEmail,
                orgId = org.id,
                orgName = org.name,
                message = "Need Bankak transfer verification ending in 8811"
            ).getOrThrow()
        }
        assertTrue(ticketId.isNotBlank())
    }

    private companion object {
        const val OP_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 6000L
    }
}
