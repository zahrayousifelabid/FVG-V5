package com.example.data.model



@FvgIgnoreExtraProperties
data class UserProfile(
    @field:FvgDocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val activeOrgId: String = "",
    val preferredLanguage: String = "ar",
    val darkMode: Boolean = false,
    val themeMode: String = "light", // "light", "dark", "system"
    val fontScale: Float = 1.0f, // 0.85f .. 1.30f
    val timezone: String = "Africa/Khartoum",
    val dateFormat: String = "yyyy-MM-dd",
    val numberFormat: String = "latin",
    val defaultCurrency: String = "SDG",
    val avatarLocalRef: String = "",
    val contactPhoneOptional: String = "",
    val tourCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val trialDurationHours: Long = 168L,
    @field:FvgServerTimestamp val trialStartedAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class Organization(
    @field:FvgDocumentId val id: String = "",
    val name: String = "",
    val baseCurrency: String = "SDG",
    val sectorType: String = "manufacturing",
    val brandTagline: String = "",
    val countryState: String = "",
    val fiscalYearStart: String = "01-01",
    val taxEnabled: Boolean = false,
    val defaultTaxRatePct: String = "0.00",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val roles: Map<String, String> = emptyMap(),
    val planTier: String = "trial",
    val subscriptionStatus: String = "trial",
    val trialDurationHours: Long = 168L,
    @field:FvgServerTimestamp val trialStartedAt: FvgTimestamp? = null,
    val trialEndsAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class Product(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val name: String = "",
    val sku: String = "",
    val category: String = "",
    val description: String = "",
    val unit: String = "kg",
    val currency: String = "SDG",
    val location: String = "HQ",
    val source: String = "Manual",
    val currentPrice: String = "0.00",
    val previousPrice: String = "0.00",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class PriceObservation(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val category: String = "",
    val unit: String = "kg",
    val currency: String = "SDG",
    val price: String = "0.00",
    val previousPrice: String = "0.00",
    val absChange: String = "0.00",
    val pctChange: String = "N/A",
    val direction: String = "unchanged",
    val source: String = "Manual",
    val location: String = "HQ",
    val observationDate: String = "",
    val notes: String = "",
    val attachmentRef: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class InflationRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val period: String = "",
    val category: String = "All Items",
    val monthlyRatePct: String = "0.00",
    val annualRatePct: String = "",
    val indexValue: String = "100.00",
    val basePeriod: String = "",
    val sourceType: String = "calculated",
    val sourceName: String = "",
    val formulaNote: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class BasketItemMap(
    val name: String = "",
    val unit: String = "kg",
    val quantity: String = "1",
    val weight: String = "1",
    val basePrice: String = "0.00",
    val currentPrice: String = "0.00"
)

@FvgIgnoreExtraProperties
data class BasketPeriodSnapshotMap(
    val period: String = "",
    val version: Long = 1L,
    val cost: String = "0.00",
    val indexValue: String = "100.00"
)

@FvgIgnoreExtraProperties
data class PriceBasket(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val name: String = "",
    val basePeriod: String = "",
    val currentPeriod: String = "",
    val currency: String = "SDG",
    val version: Long = 1L,
    val items: List<Map<String, Any>> = emptyList(),
    val baseCost: String = "0.00",
    val currentCost: String = "0.00",
    val indexValue: String = "100.00",
    val periodSnapshots: List<Map<String, Any>> = emptyList(),
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class FxRate(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val baseCurrency: String = "USD",
    val quoteCurrency: String = "SDG",
    val rate: String = "1.0000",
    val effectiveDate: String = "",
    val effectiveTime: String = "",
    val rateType: String = "manual",
    val sourceKind: String = "manual",
    val sourceName: String = "",
    val status: String = "approved", // "draft", "approved", "cancelled"
    val notes: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class CostSnapshot(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val period: String = "",
    val currency: String = "SDG",
    val costingMethod: String = "actual",
    val directMaterial: String = "0.00",
    val indirectMaterial: String = "0.00",
    val directLabor: String = "0.00",
    val indirectLabor: String = "0.00",
    val overhead: String = "0.00",
    val energyFuel: String = "0.00",
    val packaging: String = "0.00",
    val transport: String = "0.00",
    val wasteScrap: String = "0.00",
    val fixedCosts: String = "0.00",
    val goodUnits: String = "1",
    val totalCost: String = "0.00",
    val unitCost: String = "0.00",
    val sellingPrice: String = "0.00",
    val revenue: String = "0.00",
    val grossProfit: String = "0.00",
    val netProfit: String = "0.00",
    val profitMarginPct: String = "0.00",
    val markupPct: String = "0.00",
    val breakEvenUnits: String = "0",
    val riskLevel: String = "low",
    val formulaVersion: String = "2.2.0",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class ScenarioRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val snapshotId: String = "",
    val name: String = "",
    val scenarioType: String = "Custom",
    val materialChangePct: String = "0",
    val laborChangePct: String = "0",
    val overheadChangePct: String = "0",
    val priceChangePct: String = "0",
    val fxChangePct: String = "0",
    val volumeChangePct: String = "0",
    val baseTotalCost: String = "0.00",
    val scenarioTotalCost: String = "0.00",
    val baseProfit: String = "0.00",
    val scenarioProfit: String = "0.00",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class AccountRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val code: String = "",
    val name: String = "",
    val nameAr: String = "",
    val accountType: String = "asset",
    val subType: String = "current_asset",
    val normalBalance: String = "debit",
    val openingBalance: String = "0.00",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class JournalEntryRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val referenceNo: String = "",
    val entryDate: String = "",
    val period: String = "",
    val description: String = "",
    val currency: String = "SDG",
    val exchangeRate: String = "1.0000",
    val originalCurrency: String = "SDG",
    val originalAmount: String = "0.00",
    val status: String = "posted",
    val lines: List<Map<String, Any>> = emptyList(),
    val totalDebit: String = "0.00",
    val totalCredit: String = "0.00",
    val sourceModule: String = "manual",
    val reversedEntryId: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class CounterpartyRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val type: String = "receivable",
    val partyName: String = "",
    val invoiceNo: String = "",
    val currency: String = "SDG",
    val amount: String = "0.00",
    val paidAmount: String = "0.00",
    val dueDate: String = "",
    val status: String = "open",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class BudgetRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val name: String = "",
    val budgetType: String = "operating",
    val period: String = "",
    val version: Long = 1L,
    val currency: String = "SDG",
    val plannedAmount: String = "0.00",
    val actualAmount: String = "0.00",
    val variance: String = "0.00",
    val variancePct: String = "0.00",
    val explanation: String = "",
    val status: String = "draft",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class InventoryItemRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val sku: String = "",
    val name: String = "",
    val itemType: String = "raw_material",
    val unit: String = "kg",
    val warehouse: String = "Main Warehouse",
    val currency: String = "SDG",
    val valuationMethod: String = "fifo",
    val quantityOnHand: String = "0.0000",
    val totalBookValue: String = "0.00",
    val movingAvgCost: String = "0.0000",
    val currentMarketPrice: String = "0.00",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val fifoLots: List<Map<String, Any>> = emptyList(),
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class InventoryMovementRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val movementType: String = "receipt",
    val quantity: String = "0.0000",
    val unitCost: String = "0.0000",
    val totalCost: String = "0.00",
    val warehouse: String = "Main Warehouse",
    val reason: String = "",
    val referenceNo: String = "",
    val movementDate: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class StocktakeSessionRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val sessionName: String = "",
    val warehouse: String = "Main Warehouse",
    val sessionDate: String = "",
    val status: String = "draft",
    val lines: List<Map<String, Any>> = emptyList(),
    val totalDiscrepancyValue: String = "0.00",
    val reviewedBy: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class OcrDraftRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val fileName: String = "",
    val invoiceNumber: String = "",
    val extractedText: String = "",
    val vendorName: String = "",
    val documentDate: String = "",
    val currency: String = "",
    val subtotalAmount: String = "",
    val discountAmount: String = "",
    val taxAmount: String = "",
    val totalAmount: String = "",
    val confidenceNote: String = "",
    val lineSummary: String = "",
    val duplicateWarning: String = "",
    val reviewStatus: String = "pending_review",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class AuditLogRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val summary: String = "",
    val actorEmail: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class StandardCostSheet(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val version: Long = 1L,
    val currency: String = "SDG",
    val standardMaterialCost: String = "0.00",
    val standardLaborHours: String = "0.00",
    val standardLaborRate: String = "0.00",
    val standardOverheadPerUnit: String = "0.00",
    val standardUnitCost: String = "0.00",
    val notes: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class BomFormulaRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productName: String = "",
    val outputUnit: String = "kg",
    val batchSize: String = "1",
    val ingredients: List<Map<String, Any>> = emptyList(),
    val unitMaterialCost: String = "0.00",
    val scrapAllowancePct: String = "0.00",
    val notes: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class ProductionOrderRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val period: String = "",
    val currency: String = "SDG",
    val plannedUnits: String = "0",
    val goodUnits: String = "0",
    val scrapUnits: String = "0",
    val actualMaterialCost: String = "0.00",
    val actualLaborCost: String = "0.00",
    val actualOverheadCost: String = "0.00",
    val actualTotalCost: String = "0.00",
    val status: String = "planned",
    val stages: List<Map<String, Any>> = emptyList(),
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class ScrapLossRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val lossType: String = "scrap",
    val quantity: String = "0",
    val unitCost: String = "0.00",
    val totalLossCost: String = "0.00",
    val reason: String = "",
    val lossDate: String = "",
    val status: String = "pending_approval",
    val reviewedBy: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class PaymentRequestRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val planTier: String = "professional",
    val paymentMethod: String = "bankak",
    val amount: String = "0.00",
    val currency: String = "SDG",
    val senderName: String = "",
    val referenceNo: String = "",
    val last4Digits: String = "",
    val transferDate: String = "",
    val attachmentNote: String = "",
    val notes: String = "",
    val receiptMatchStatus: String = "",
    val receiptMatchNote: String = "",
    val expectedPlanAmount: String = "",
    val status: String = "pending_verification",
    val reviewedBy: String = "",
    val decisionActorEmail: String = "",
    val reviewNotes: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class PlatformPlanRecord(
    @field:FvgDocumentId val id: String = "",
    val key: String = "starter",
    val tierLevel: Long = 1L,
    val nameEn: String = "",
    val nameAr: String = "",
    val priceUsd: String = "0.00",
    val priceSdg: String = "0.00",
    val billingCycle: String = "monthly",
    val maxOrgs: Long = 1L,
    val maxUsers: Long = 3L,
    val maxProducts: Long = 250L,
    val allowAllCostingMethods: Boolean = true,
    val allowBomAndProduction: Boolean = true,
    val allowAiAssistant: Boolean = true,
    val allowOcr: Boolean = true,
    val allowPdfXlsxExport: Boolean = true,
    val allowAuditAndBackup: Boolean = true,
    val allowSectorAdvancedTools: Boolean = true,
    val active: Boolean = true,
    val notesEn: String = "",
    val notesAr: String = "",
    val featuresEn: String = "",
    val featuresAr: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

data class PlanEntitlements(
    val planTier: String = "trial",
    val maxOrgs: Int = 5,
    val maxProducts: Int = 500,
    val allowAllCostingMethods: Boolean = true,
    val allowBomAndProduction: Boolean = true,
    val allowAiAssistant: Boolean = true,
    val allowOcr: Boolean = true,
    val allowPdfXlsxExport: Boolean = true,
    val allowAuditAndBackup: Boolean = true,
    val allowSectorAdvancedTools: Boolean = true
) {
    companion object {
        fun defaultPlans(): List<PlatformPlanRecord> = listOf(
            PlatformPlanRecord(
                id = "starter",
                key = "starter",
                tierLevel = 1L,
                nameEn = "Starter",
                nameAr = "الباقة الأساسية",
                priceUsd = "12.00",
                priceSdg = "28000",
                billingCycle = "monthly",
                maxOrgs = 1L,
                maxUsers = 2L,
                maxProducts = 25L,
                allowAllCostingMethods = false,
                allowBomAndProduction = false,
                allowAiAssistant = false,
                allowOcr = false,
                allowPdfXlsxExport = false,
                allowAuditAndBackup = false,
                allowSectorAdvancedTools = false,
                active = true,
                notesEn = "Prices, Inflation, Manual FX, 2 Costing Methods (Actual/Standard), Basic Accounting & Stock (1 org, 25 items).",
                notesAr = "مراقبة الأسعار والتضخم والصرف اليدوي، أسلوبان للتكلفة (الفعلي والمعياري)، المحاسبة والمخزون الأساسي (منظمة واحدة، 25 صنف).",
                featuresEn = "Prices & FX|Actual & Standard Costing|Ledger & Statements|1 Org / 25 Products",
                featuresAr = "الأسعار والصرف|التكلفة الفعلية والمعيارية|القيود والقوائم|منظمة واحدة / 25 صنف"
            ),
            PlatformPlanRecord(
                id = "professional",
                key = "professional",
                tierLevel = 2L,
                nameEn = "Professional",
                nameAr = "الباقة الاحترافية",
                priceUsd = "29.00",
                priceSdg = "65000",
                billingCycle = "monthly",
                maxOrgs = 3L,
                maxUsers = 10L,
                maxProducts = 250L,
                allowAllCostingMethods = true,
                allowBomAndProduction = true,
                allowAiAssistant = true,
                allowOcr = true,
                allowPdfXlsxExport = true,
                allowAuditAndBackup = false,
                allowSectorAdvancedTools = true,
                active = true,
                notesEn = "All 8 Costing Methods, BOM & Variances, Sector Calculators, 18 Ratios, FIFO/MWA Stock, Camera OCR, AI Assistant & PDF/XLSX Export.",
                notesAr = "كافة أساليب التكلفة الـ 8، التركيبات والانحرافات، حاسبات القطاعات التخصصية، 18 نسبة مالية، جرد FIFO/MWA، مسح الفواتير بالكاميرا، المساعد الذكي وتصدير PDF/XLSX.",
                featuresEn = "All 8 Costing Methods|BOM & Variance|Sector Tools & FEFO|Camera OCR + AI Chat|PDF & XLSX Export|3 Orgs / 250 Products",
                featuresAr = "8 أساليب تكلفة|التركيبات والانحرافات|أدوات القطاعات والصلاحية|مسح الكاميرا والمساعد الذكي|تصدير PDF و XLSX|3 منظمات / 250 صنف"
            ),
            PlatformPlanRecord(
                id = "enterprise",
                key = "enterprise",
                tierLevel = 3L,
                nameEn = "Enterprise",
                nameAr = "باقة المؤسسات",
                priceUsd = "79.00",
                priceSdg = "175000",
                billingCycle = "monthly",
                maxOrgs = 50L,
                maxUsers = 100L,
                maxProducts = 10000L,
                allowAllCostingMethods = true,
                allowBomAndProduction = true,
                allowAiAssistant = true,
                allowOcr = true,
                allowPdfXlsxExport = true,
                allowAuditAndBackup = true,
                allowSectorAdvancedTools = true,
                active = true,
                notesEn = "Everything in Professional + Unlimited Scale, Multi-User RBAC, Full Immutable Audit Logs, JSON Workspace Backup & Priority Support.",
                notesAr = "كل مزايا الاحترافية + سعة مفتوحة، صلاحيات الفريق RBAC، سجل التدقيق الكامل، النسخ الاحتياطي JSON ودعم أولوية من المالكة.",
                featuresEn = "Unlimited Workspaces & SKUs|All Sector Calculators|Full RBAC Team Control|Immutable Audit & JSON Backup|Priority Support",
                featuresAr = "سعة مفتوحة للمنظمات والأصناف|كافة حاسبات القطاعات|إدارة فريق بصلاحيات RBAC|سجل التدقيق والنسخ الاحتياطي JSON|أولوية الدعم الفني"
            )
        )

        fun resolve(
            planTier: String,
            subscriptionStatus: String,
            plans: List<PlatformPlanRecord>
        ): PlanEntitlements {
            val tier = planTier.lowercase().ifBlank { "trial" }
            if (subscriptionStatus.lowercase() in setOf("expired", "cancelled")) {
                return PlanEntitlements(
                    planTier = tier,
                    maxOrgs = 1,
                    maxProducts = 0,
                    allowAllCostingMethods = false,
                    allowBomAndProduction = false,
                    allowAiAssistant = false,
                    allowOcr = false,
                    allowPdfXlsxExport = false,
                    allowAuditAndBackup = false,
                    allowSectorAdvancedTools = false
                )
            }
            if (tier == "trial") {
                return PlanEntitlements(
                    planTier = "trial",
                    maxOrgs = 2,
                    maxProducts = 100,
                    allowAllCostingMethods = true,
                    allowBomAndProduction = true,
                    allowAiAssistant = true,
                    allowOcr = true,
                    allowPdfXlsxExport = true,
                    allowAuditAndBackup = true,
                    allowSectorAdvancedTools = true
                )
            }
            val matched = plans.firstOrNull { it.key.equals(tier, ignoreCase = true) }
                ?: defaultPlans().firstOrNull { it.key.equals(tier, ignoreCase = true) }
            if (matched != null) {
                return PlanEntitlements(
                    planTier = matched.key,
                    maxOrgs = matched.maxOrgs.toInt().coerceAtLeast(1),
                    maxProducts = matched.maxProducts.toInt().coerceAtLeast(1),
                    allowAllCostingMethods = matched.allowAllCostingMethods,
                    allowBomAndProduction = matched.allowBomAndProduction,
                    allowAiAssistant = matched.allowAiAssistant,
                    allowOcr = matched.allowOcr,
                    allowPdfXlsxExport = matched.allowPdfXlsxExport,
                    allowAuditAndBackup = matched.allowAuditAndBackup,
                    allowSectorAdvancedTools = matched.allowSectorAdvancedTools
                )
            }
            return PlanEntitlements(planTier = tier)
        }
    }
}

@FvgIgnoreExtraProperties
data class AppAnnouncementRecord(
    @field:FvgDocumentId val id: String = "",
    val titleEn: String = "",
    val titleAr: String = "",
    val bodyEn: String = "",
    val bodyAr: String = "",
    val targetAudience: String = "all", // "all", "trial", "paid", "specific_email"
    val targetEmail: String = "",
    val active: Boolean = true,
    val createdByEmail: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class MailOutboxRecord(
    @field:FvgDocumentId val id: String = "",
    val from: String = "Financialvisionguide@gmail.com",
    val to: List<String> = emptyList(),
    val createdBy: String = "",
    val recipientName: String = "",
    val mailType: String = "welcome", // "welcome", "notification", "payment_update"
    val message: Map<String, Any> = emptyMap(),
    val delivery: Map<String, Any> = emptyMap(),
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class PlatformVisitRecord(
    @field:FvgDocumentId val id: String = "",
    val platform: String = "android", // "android", "web_pwa"
    val screen: String = "home",
    val visitorKey: String = "",
    val userId: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class SupportTicketRecord(
    @field:FvgDocumentId val id: String = "",
    val createdBy: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val message: String = "",
    val status: String = "open", // "open", "reviewed", "resolved"
    val ownerReply: String = "",
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class AiConversationRecord(
    @field:FvgDocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val title: String = "",
    val mode: String = "financial", // "general", "financial", "ocr"
    val messages: List<Map<String, Any>> = emptyList(),
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class UserSessionRecord(
    @field:FvgDocumentId val id: String = "",
    val userId: String = "",
    val deviceLabel: String = "",
    val platform: String = "Android",
    val active: Boolean = true,
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val lastActiveAt: FvgTimestamp? = null
)

@FvgIgnoreExtraProperties
data class AdminNotificationRecord(
    @field:FvgDocumentId val id: String = "",
    val category: String = "security_alert", // "new_user", "security_alert", "payment_request", "support_ticket"
    val severity: String = "info", // "info", "warning", "critical"
    val signalType: String = "",
    val titleEn: String = "",
    val titleAr: String = "",
    val bodyEn: String = "",
    val bodyAr: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val orgId: String = "",
    val sourcePlatform: String = "cloud_trigger",
    val acknowledged: Boolean = false,
    @field:FvgServerTimestamp val createdAt: FvgTimestamp? = null,
    @field:FvgServerTimestamp val updatedAt: FvgTimestamp? = null
)

fun tenantEnvelope(org: Organization, createdBy: String): MutableMap<String, Any> = mutableMapOf(
    "orgId" to org.id,
    "ownerId" to org.ownerId,
    "memberUids" to org.memberUids,
    "createdBy" to createdBy,
    "createdAt" to fvgServerTimestampSentinel(),
    "updatedAt" to fvgServerTimestampSentinel()
)

data class OfflineQueuedMutation(
    val id: String,
    val orgId: String,
    val operationType: String, // "PRICE", "JOURNAL", "STOCK_MOVEMENT"
    val targetCollection: String,
    val targetDocId: String, // non-blank if updating existing product/item for conflict comparison
    val baseServerUpdatedAtMillis: Long,
    val title: String,
    val payloadJson: String,
    val createdAtMillis: Long
)

data class OfflineSyncConflict(
    val id: String,
    val mutation: OfflineQueuedMutation,
    val serverUpdatedAtMillis: Long,
    val serverSummary: String,
    val localSummary: String
)

