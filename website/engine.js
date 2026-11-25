export const MODEL_VERSION = "3.1.0";
export const TRIAL_DURATION_HOURS = 168;
export const LEGACY_TRIAL_DURATION_HOURS = 72;
export const TRIAL_DURATION_MILLIS = TRIAL_DURATION_HOURS * 3600 * 1000;
export const OWNER_EMAIL = "Financialvisionguide@gmail.com";

export const FOUNDER_INFO = {
  nameAr: "زهراء يوسف",
  nameEn: "Zahra Yousif",
  roleAr: "مطوّرة ومؤسسة دليل رؤيتك المالي • خريجة محاسبة — جامعة النيل",
  roleEn: "Founder & Architect • Accounting Graduate, Nile University",
  phone: "00249911569966",
  email: OWNER_EMAIL,
  portraitPath: "./assets/zahra_yousif_portrait.png"
};

export const ALL_COSTING_METHODS = [
  "actual",
  "standard",
  "target",
  "abc",
  "job_order",
  "process",
  "absorption",
  "variable"
];

export const DEFAULT_PLANS = [
  {
    id: "starter",
    key: "starter",
    code: "starter",
    nameAr: "الباقة الأساسية",
    nameEn: "Starter",
    priceSdg: "28000",
    priceUsd: "12.00",
    durationDays: 30,
    billingCycle: "monthly",
    tierLevel: 1,
    maxOrgs: 1,
    maxUsers: 2,
    maxProducts: 25,
    allowAllCostingMethods: false,
    allowBomAndProduction: false,
    allowAiAssistant: false,
    allowOcr: false,
    allowPdfXlsxExport: false,
    allowAuditAndBackup: false,
    allowSectorAdvancedTools: false,
    active: true,
    notesAr: "مراقبة الأسعار والتضخم والصرف اليدوي، أسلوبان للتكلفة (الفعلي والمعياري)، المحاسبة والمخزون الأساسي (منظمة واحدة، 25 صنف).",
    notesEn: "Prices, Inflation, Manual FX, 2 Costing Methods (Actual/Standard), Basic Accounting & Stock (1 org, 25 items).",
    featuresAr: "الأسعار والصرف اليدوي|التكلفة الفعلية والمعيارية|القيود والقوائم|منظمة واحدة / 25 صنف",
    featuresEn: "Prices & Manual FX|Actual & Standard Costing|Ledger & Statements|1 Org / 25 Products"
  },
  {
    id: "professional",
    key: "professional",
    code: "professional",
    nameAr: "الباقة الاحترافية",
    nameEn: "Professional",
    priceSdg: "65000",
    priceUsd: "29.00",
    durationDays: 30,
    billingCycle: "monthly",
    tierLevel: 2,
    maxOrgs: 3,
    maxUsers: 10,
    maxProducts: 250,
    allowAllCostingMethods: true,
    allowBomAndProduction: true,
    allowAiAssistant: true,
    allowOcr: true,
    allowPdfXlsxExport: true,
    allowAuditAndBackup: false,
    allowSectorAdvancedTools: true,
    active: true,
    notesAr: "كافة أساليب التكلفة الـ 8، التركيبات والانحرافات، 18 نسبة مالية، جرد الوارد أولاً والمتوسط المرجح، أدوات القطاعات، قراءة الفواتير بالكاميرا، المساعد الذكي وتصدير الملفات.",
    notesEn: "All 8 Costing Methods, BOM & Variances, 18 Ratios, FIFO/MWA Stock, Sector Tools, Camera OCR, AI Financial Assistant & PDF/XLSX Export.",
    featuresAr: "8 أساليب تكلفة|التركيبات والانحرافات|مسح الكاميرا والمساعد الذكي|أدوات القطاعات المتقدمة|تصدير PDF وملفات التقارير|3 منظمات / 250 صنف",
    featuresEn: "All 8 Costing Methods|BOM & Variance|Camera OCR + AI Chat|Sector Advanced Tools|PDF & XLSX Export|3 Orgs / 250 Products"
  },
  {
    id: "enterprise",
    key: "enterprise",
    code: "enterprise",
    nameAr: "باقة المؤسسات",
    nameEn: "Enterprise",
    priceSdg: "175000",
    priceUsd: "79.00",
    durationDays: 30,
    billingCycle: "monthly",
    tierLevel: 3,
    maxOrgs: 50,
    maxUsers: 100,
    maxProducts: 10000,
    allowAllCostingMethods: true,
    allowBomAndProduction: true,
    allowAiAssistant: true,
    allowOcr: true,
    allowPdfXlsxExport: true,
    allowAuditAndBackup: true,
    allowSectorAdvancedTools: true,
    active: true,
    notesAr: "كل مزايا الاحترافية + سعة مفتوحة، صلاحيات الفريق، سجل التدقيق الكامل، النسخ الاحتياطي الشامل ودعم أولوية من المالكة.",
    notesEn: "Everything in Professional + High Scale, Multi-User RBAC, Full Immutable Audit Logs, JSON Workspace Backup & Priority Support.",
    featuresAr: "سعة مفتوحة للمنظمات والأصناف|إدارة صلاحيات الفريق|سجل التدقيق والنسخ الاحتياطي|أولوية الدعم الفني",
    featuresEn: "High Scale Workspaces & SKUs|Full RBAC Team Control|Immutable Audit & JSON Backup|Priority Support"
  }
];

export function resolvePlanEntitlements(planTier, subscriptionStatus, plans = DEFAULT_PLANS) {
  const tier = String(planTier || "trial").toLowerCase();
  const status = String(subscriptionStatus || "trial").toLowerCase();
  if (status === "expired" || status === "cancelled") {
    return {
      planTier: tier,
      maxOrgs: 1,
      maxProducts: 0,
      allowAllCostingMethods: false,
      allowBomAndProduction: false,
      allowAiAssistant: false,
      allowOcr: false,
      allowPdfXlsxExport: false,
      allowAuditAndBackup: false,
      allowSectorAdvancedTools: false
    };
  }
  if (tier === "trial") {
    return {
      planTier: "trial",
      maxOrgs: 2,
      maxProducts: 100,
      allowAllCostingMethods: true,
      allowBomAndProduction: true,
      allowAiAssistant: true,
      allowOcr: true,
      allowPdfXlsxExport: true,
      allowAuditAndBackup: true,
      allowSectorAdvancedTools: true
    };
  }
  const sourceList = Array.isArray(plans) && plans.length ? plans : DEFAULT_PLANS;
  const matched =
    sourceList.find((p) => String(p.key || p.code || p.id || "").toLowerCase() === tier) ||
    DEFAULT_PLANS.find((p) => p.key === tier);
  if (matched) {
    return {
      planTier: matched.key || matched.code || tier,
      maxOrgs: Math.max(1, Number(matched.maxOrgs || 1)),
      maxProducts: Math.max(1, Number(matched.maxProducts || 25)),
      allowAllCostingMethods: Boolean(matched.allowAllCostingMethods),
      allowBomAndProduction: Boolean(matched.allowBomAndProduction),
      allowAiAssistant: Boolean(matched.allowAiAssistant),
      allowOcr: Boolean(matched.allowOcr),
      allowPdfXlsxExport: Boolean(matched.allowPdfXlsxExport),
      allowAuditAndBackup: Boolean(matched.allowAuditAndBackup),
      allowSectorAdvancedTools: matched.allowSectorAdvancedTools !== undefined
        ? Boolean(matched.allowSectorAdvancedTools)
        : Boolean(matched.allowBomAndProduction)
    };
  }
  return {
    planTier: tier,
    maxOrgs: 1,
    maxProducts: 25,
    allowAllCostingMethods: false,
    allowBomAndProduction: false,
    allowAiAssistant: false,
    allowOcr: false,
    allowPdfXlsxExport: false,
    allowAuditAndBackup: false,
    allowSectorAdvancedTools: false
  };
}

export const FVG_SECTORS = [
  { key: "manufacturing", nameEn: "Manufacturing & Industrial Production", nameAr: "التصنيع والإنتاج الصناعي", active: true },
  { key: "food_beverage", nameEn: "Food, Beverage & Packaging", nameAr: "الأغذية والمشروبات والتعبئة", active: true },
  { key: "retail_wholesale", nameEn: "Retail & Wholesale Trade", nameAr: "تجارة التجزئة والجملة", active: false },
  { key: "pharmacy_healthcare", nameEn: "Pharmacy & Healthcare", nameAr: "الصيدلة والرعاية الصحية", active: false },
  { key: "agriculture", nameEn: "Agriculture & Livestock", nameAr: "الزراعة والثروة الحيوانية", active: false },
  { key: "construction", nameEn: "Construction & Contracting", nameAr: "البناء والمقاولات", active: false },
  { key: "logistics", nameEn: "Logistics & Transport", nameAr: "اللوجستيات والنقل", active: false },
  { key: "hospitality", nameEn: "Hospitality & Catering", nameAr: "الضيافة والتموين", active: false },
  { key: "services", nameEn: "Professional Services & Consulting", nameAr: "الخدمات المهنية والاستشارات", active: false },
  { key: "ecommerce", nameEn: "E-Commerce & Digital Supply", nameAr: "التجارة الإلكترونية والإمداد الرقمي", active: false }
];

export function getSectorStarterAccounts(sectorKey) {
  if (sectorKey === "food_beverage") {
    return [
      { code: "1215", name: "Perishable Raw Ingredients", nameAr: "المواد الغذائية الأولية", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
      { code: "1225", name: "Finished Food & Beverage Stock", nameAr: "مخزون المنتجات الغذائية التامة", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
      { code: "5025", name: "Packaging & Cold-Chain COGS", nameAr: "تكلفة التعبئة والتبريد المباشرة", accountType: "cogs", subType: "cogs", normalBalance: "debit", openingBalance: "0.00" },
      { code: "6215", name: "Spoilage & Expiry Write-Off", nameAr: "خسائر الصلاحية والتلف الغذائي", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" }
    ];
  }
  if (sectorKey === "manufacturing") {
    return [
      { code: "1212", name: "Raw Materials & WIP", nameAr: "المواد الخام وتحت التشغيل", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
      { code: "1222", name: "Finished Manufactured Goods", nameAr: "المنتجات الصناعية التامة", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
      { code: "5020", name: "Direct Manufacturing Overhead", nameAr: "التكاليف الصناعية غير المباشرة", accountType: "cogs", subType: "cogs", normalBalance: "debit", openingBalance: "0.00" },
      { code: "6212", name: "Production Scrap & Rework", nameAr: "خسائر الهدر وإعادة التشغيل", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" }
    ];
  }
  return [];
}

export function isAnnouncementVisible(ann, org, userEmail) {
  if (!ann || !ann.active) return false;
  const audience = String(ann.targetAudience || "all").toLowerCase();
  if (audience === "all") return true;
  if (audience === "trial") return !org || org.planTier === "trial" || org.subscriptionStatus === "trial";
  if (audience === "paid") return Boolean(org && org.subscriptionStatus === "active" && org.planTier !== "trial");
  if (audience === "specific_email") {
    return String(ann.targetEmail || "").trim().toLowerCase() === String(userEmail || "").trim().toLowerCase();
  }
  return true;
}

export const SPEC_SCENARIO_PRESETS = [
  { key: "fx_shock_30", nameEn: "Sudden FX Shock +30%", nameAr: "صدمة صرف مفاجئة +30%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 30, volumePct: 0 },
  { key: "sales_drop_25", nameEn: "Sales Slump -25%", nameAr: "ركود مبيعات انكماشي -25%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: -25 },
  { key: "mat_surge_35", nameEn: "Raw Material Surge +35%", nameAr: "قفزة مواد خام وتضخم استيراد +35%", matPct: 35, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: 0 },
  { key: "material_plus_20", nameEn: "Raw Materials +20%", nameAr: "المواد الخام +20%", matPct: 20, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: 0 },
  { key: "fx_plus_15", nameEn: "Exchange Rate +15%", nameAr: "سعر الصرف +15%", matPct: 15, labPct: 0, ovhPct: 5, pricePct: 0, fxPct: 15, volumePct: 0 },
  { key: "price_minus_10", nameEn: "Selling Price -10%", nameAr: "سعر البيع -10%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: -10, fxPct: 0, volumePct: 0 },
  { key: "volume_minus_25", nameEn: "Volume -25%", nameAr: "حجم المبيعات -25%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: -25 }
];

export function n(v) {
  const num = Number(v);
  return Number.isFinite(num) ? num : 0;
}

export function money(v) {
  return Math.round((n(v) + Number.EPSILON) * 100) / 100;
}

export function pct4(v) {
  return Math.round((n(v) + Number.EPSILON) * 10000) / 10000;
}

export function computeServerSyncedTrialEndMillis(userTrialStartMillis, orgTrialEndsAtMillis, durationHours = TRIAL_DURATION_HOURS) {
  const expected = userTrialStartMillis ? userTrialStartMillis + Number(durationHours) * 3600 * 1000 : null;
  if (expected && orgTrialEndsAtMillis) return Math.min(expected, orgTrialEndsAtMillis);
  return orgTrialEndsAtMillis || expected || null;
}

export function assessFxRateFreshness(effectiveDate, status, todayDays = Math.floor(Date.now() / 86400000)) {
  const parsed = Date.parse(effectiveDate);
  const ageDays = Number.isFinite(parsed) ? Math.max(0, todayDays - Math.floor(parsed / 86400000)) : 999;
  const isStale = ageDays > 7;
  const isUnapproved = String(status || "").toLowerCase() !== "approved";
  return { ageDays, isStale, isUnapproved };
}

export function validateOcrArithmetic(subtotal, discount, tax, total) {
  const expectedTotal = money(n(subtotal) - n(discount) + n(tax));
  const extractedTotal = money(total);
  const diff = money(Math.abs(expectedTotal - extractedTotal));
  return {
    isConsistent: diff <= 0.05,
    expectedTotal,
    extractedTotal,
    discrepancy: diff
  };
}

export function evaluateCalculatorExpression(expr) {
  const clean = String(expr || "").replace(/\s+/g, "").replace(/×/g, "*").replace(/÷/g, "/");
  if (!clean) return { value: 0, formatted: "0", error: null };
  let pos = 0;

  function peek() { return clean[pos]; }
  function parseNumber() {
    let start = pos;
    let seenDot = false;
    while (pos < clean.length && (/[0-9]/.test(clean[pos]) || (clean[pos] === "." && !seenDot))) {
      if (clean[pos] === ".") seenDot = true;
      pos++;
    }
    if (pos === start) throw new Error("SYNTAX");
    let num = Number(clean.slice(start, pos));
    if (!Number.isFinite(num)) throw new Error("SYNTAX");
    if (peek() === "%") {
      pos++;
      num = num / 100;
    }
    return num;
  }

  function parseFactor() {
    if (peek() === "+") { pos++; return parseFactor(); }
    if (peek() === "-") { pos++; return -parseFactor(); }
    if (peek() === "(") {
      pos++;
      const val = parseExpr();
      if (peek() !== ")") throw new Error("PAREN");
      pos++;
      if (peek() === "%") { pos++; return val / 100; }
      return val;
    }
    return parseNumber();
  }

  function parseTerm() {
    let val = parseFactor();
    while (peek() === "*" || peek() === "/") {
      const op = clean[pos++];
      const rhs = parseFactor();
      if (op === "*") val = val * rhs;
      else {
        if (rhs === 0) throw new Error("DIV0");
        val = val / rhs;
      }
    }
    return val;
  }

  function parseExpr() {
    let val = parseTerm();
    while (peek() === "+" || peek() === "-") {
      const op = clean[pos++];
      const rhs = parseTerm();
      val = op === "+" ? val + rhs : val - rhs;
    }
    return val;
  }

  try {
    const res = parseExpr();
    if (pos < clean.length || !Number.isFinite(res)) return { value: null, formatted: "Error", error: "SYNTAX" };
    const rounded = Math.round((res + Number.EPSILON) * 1000000) / 1000000;
    return { value: rounded, formatted: String(rounded), error: null };
  } catch (e) {
    return { value: null, formatted: e.message === "DIV0" ? "÷0!" : "Error", error: e.message };
  }
}

export function calculatePercentageDelta(previousPrice, newPrice) {
  const absChange = money(n(newPrice) - n(previousPrice));
  const direction = absChange > 0 ? "up" : absChange < 0 ? "down" : "unchanged";
  if (n(previousPrice) <= 0) {
    return { absChange, pctChange: null, direction, isUnusual: false };
  }
  const pctChange = pct4((absChange / n(previousPrice)) * 100);
  return { absChange, pctChange, direction, isUnusual: Math.abs(pctChange) >= 15 };
}

export function calculateCompoundedInflation(monthlyRates) {
  if (!monthlyRates.length) return 0;
  let factor = 1;
  for (const r of monthlyRates) {
    factor *= 1 + n(r) / 100;
  }
  return pct4((factor - 1) * 100);
}

export function calculateWeightedBasket(items) {
  let baseTotal = 0;
  let currTotal = 0;
  for (const it of items) {
    const qtyWeight = n(it.quantity) * n(it.weight || 1);
    baseTotal += qtyWeight * n(it.basePrice);
    currTotal += qtyWeight * n(it.currentPrice);
  }
  const baseCost = money(baseTotal);
  const currentCost = money(currTotal);
  const indexValue = baseCost > 0 ? pct4((currentCost / baseCost) * 100) : null;
  const basketInflationPct = baseCost > 0 ? pct4(((currentCost - baseCost) / baseCost) * 100) : null;
  return { baseCost, currentCost, indexValue, basketInflationPct };
}

export function calculateCosting(input) {
  const units = n(input.goodUnits);
  if (units <= 0) throw new Error("Good units must be greater than zero");

  const variableTotal =
    n(input.directMaterial) +
    n(input.indirectMaterial) +
    n(input.directLabor) +
    n(input.indirectLabor) +
    n(input.overhead) +
    n(input.energyFuel) +
    n(input.packaging) +
    n(input.transport) +
    n(input.wasteScrap);

  const fixed = n(input.fixedCosts);
  const totalCost = money(variableTotal + fixed);
  const unitCost = money(totalCost / units);
  const unitVarCost = variableTotal / units;
  const price = money(input.sellingPrice);
  const revenue = money(price * units);
  const grossProfit = money(revenue - variableTotal);
  const netProfit = money(revenue - totalCost);

  const profitMarginPct = price > 0 ? pct4(((price - unitCost) / price) * 100) : null;
  const markupPct = unitCost > 0 ? pct4(((price - unitCost) / unitCost) * 100) : null;

  const unitContribution = price - unitVarCost;
  const breakEvenUnits = fixed > 0 && unitContribution > 0 ? Math.ceil(fixed / unitContribution) : null;

  return {
    totalCost,
    unitCost,
    unitVarCost: money(unitVarCost),
    revenue,
    grossProfit,
    netProfit,
    profitMarginPct,
    markupPct,
    breakEvenUnits
  };
}

export function calculateCostingByMethod(method, input, targetMarginPct = 20, completedEquivUnits = null, isArabic = true) {
  const norm = ALL_COSTING_METHODS.includes(method) ? method : "actual";
  let effectiveInput = { ...input };
  let effectiveUnits = n(input.goodUnits);
  let targetAllowableUnitCost = null;
  let targetCostGapPerUnit = null;
  let note = "";

  if (norm === "variable") {
    effectiveInput.fixedCosts = 0;
    note = isArabic
      ? "التكلفة المتغيرة: تستبعد التكاليف الثابتة من تكلفة الوحدة وتعاملها كمصروف فترة."
      : "Variable Costing excludes Fixed Overhead from unit product cost (period expense).";
  } else if (norm === "process") {
    const eq = completedEquivUnits && n(completedEquivUnits) > 0 ? n(completedEquivUnits) : effectiveUnits;
    effectiveInput.goodUnits = eq;
    effectiveUnits = eq;
    note = isArabic
      ? `تكلفة المراحل: توزّع تكلفة التشغيل على ${eq} وحدة مكافئة.`
      : `Process Costing allocates total batch cost across ${eq} equivalent units.`;
  } else if (norm === "target") {
    const res = calculateCosting(effectiveInput);
    const allowable = money(n(input.sellingPrice) * (1 - n(targetMarginPct) / 100));
    targetAllowableUnitCost = allowable;
    targetCostGapPerUnit = money(res.unitCost - allowable);
    note = isArabic
      ? `التكلفة المستهدفة: التكلفة المسموحة = ${allowable} عند هامش ${targetMarginPct}%`
      : `Target Costing: Allowable Unit Cost = ${allowable} at ${targetMarginPct}% margin.`;
    return { method: norm, coreResult: res, effectiveUnitsUsed: effectiveUnits, targetAllowableUnitCost, targetCostGapPerUnit, methodNote: note };
  } else if (norm === "absorption") {
    note = isArabic ? "التكلفة الكلية الامتصاصية: تشمل المواد المباشرة والتكاليف المتغيرة والثابتة." : "Absorption Costing includes Direct, Variable Overhead & Fixed Manufacturing Overhead.";
  } else if (norm === "standard") {
    note = isArabic ? "التكلفة المعيارية: تقارن الوصفة المعيارية بالإنفاق الفعلي." : "Standard Costing benchmarks budgeted unit recipes against actual factory spend.";
  } else if (norm === "abc") {
    note = isArabic ? "التكلفة على أساس الأنشطة: توزّع غير المباشر حسب مسببات التكلفة." : "Activity-Based Costing assigns overhead pools via measured cost drivers.";
  } else if (norm === "job_order") {
    note = isArabic ? "تكلفة الأوامر الإنتاجية: تحصر مواد وعمالة وأعباء كل أمر تشغيل." : "Job Order Costing accumulates materials, labor, and overhead per custom batch.";
  } else {
    note = isArabic ? "التكلفة الفعلية: تسجل إجمالي التكاليف الفعلية لكل وحدة صالحة." : "Actual Costing records full historical incurred factory costs per good unit.";
  }

  const coreResult = calculateCosting(effectiveInput);
  return { method: norm, coreResult, effectiveUnitsUsed: effectiveUnits, targetAllowableUnitCost, targetCostGapPerUnit, methodNote: note };
}

export function evaluateFinancialRisk(profitMarginPct, inflationRatePct = null, totalDebt = 0, totalEquity = 0, isArabic = true) {
  const margin = profitMarginPct == null ? null : n(profitMarginPct);
  const inf = inflationRatePct == null ? null : n(inflationRatePct);
  const deRatio = n(totalEquity) > 0 ? money(n(totalDebt) / n(totalEquity)) : null;

  if (margin != null && margin < 10) {
    return {
      level: "high",
      signal: "red",
      ruleName: isArabic ? "انخفاض حاد في الهامش" : "LOW_MARGIN",
      actualValue: `${margin}%`,
      thresholdValue: "10.00%",
      explanation: isArabic ? "هامش الربح أقل من حد الأمان 10% — خطر مرتفع لتآكل رأس المال." : "Profit margin is below the 10% safety floor — high risk of cost-erosion loss."
    };
  }
  if (inf != null && inf > 20 && margin != null && margin < inf) {
    return {
      level: "high",
      signal: "red",
      ruleName: isArabic ? "ضغط التضخم على الهامش" : "INFLATION_SQUEEZE",
      actualValue: `${margin}% / ${inf}%`,
      thresholdValue: isArabic ? "أعلى من التضخم" : ">= Inflation",
      explanation: isArabic ? "التضخم الشهري يفوق هامش الوحدة؛ يلزم تعديل التسعير لحماية الاستبدال." : "Inflation exceeds unit margin; capital replacement erodes without repricing."
    };
  }
  if (deRatio != null && deRatio > 2) {
    return {
      level: "high",
      signal: "red",
      ruleName: isArabic ? "ارتفاع المديونية" : "HIGH_LEVERAGE",
      actualValue: isArabic ? `${deRatio} ضعف` : `${deRatio}x`,
      thresholdValue: isArabic ? "2.00 ضعف" : "2.00x",
      explanation: isArabic ? "نسبة الديون إلى حقوق الملكية تتجاوز 2.0 ضعف — ضغط سيولة." : "Debt-to-Equity exceeds 2.0x — elevated financial solvency stress."
    };
  }
  if (margin != null && margin < 18) {
    return {
      level: "medium",
      signal: "yellow",
      ruleName: isArabic ? "هامش ربح ضيق" : "MODERATE_MARGIN",
      actualValue: `${margin}%`,
      thresholdValue: "18.00%",
      explanation: isArabic ? "الهامش موجب لكنه ضيق (بين 10% و 18%) في بيئة تضخمية." : "Margin is positive but tight under inflationary supply volatility (10%–18%)."
    };
  }
  if (inf != null && inf > 10) {
    return {
      level: "medium",
      signal: "yellow",
      ruleName: isArabic ? "تضخم مرتفع" : "ELEVATED_INFLATION",
      actualValue: `${inf}%`,
      thresholdValue: "10.00%",
      explanation: isArabic ? "التضخم يتجاوز 10%؛ راقب تكلفة الاستبدال باستمرار." : "Inflation exceeds 10%; maintain dynamic replacement-cost monitoring."
    };
  }
  return {
    level: "low",
    signal: "green",
    ruleName: isArabic ? "نطاق أمان صحي" : "HEALTHY_BUFFER",
    actualValue: margin != null ? `${margin}%` : (isArabic ? "بانتظار البيانات" : "Pending"),
    thresholdValue: ">= 18%",
    explanation: isArabic ? "الربحية والرافعة المالية وهامش الأمان التضخمي ضمن الحدود الصحية." : "Unit profitability, leverage, and inflation buffers stay within healthy tolerance."
  };
}

export function evaluateAccountingEquationSignal(stmt, hasEntries, isArabic = true) {
  if (!stmt.isAccountingEquationBalanced) {
    return {
      level: "red",
      title: isArabic ? "إشارة حمراء — عدم توازن المعادلة المحاسبية" : "RED ALERT — Accounting Equation Unbalanced",
      subtitle: isArabic ? "إجمالي الأصول لا يساوي الالتزامات + حقوق الملكية. يرجى مراجعة القيود." : "Total Assets do not equal Liabilities + Equity. Review entries immediately."
    };
  }
  if (!hasEntries) {
    return {
      level: "yellow",
      title: isArabic ? "إشارة صفراء — بانتظار ترحيل قيود" : "YELLOW NOTICE — Awaiting Posted Journals",
      subtitle: isArabic ? "الميزانية متوازنة عند الرصيد الافتتاحي. رحّل قيداً مزدوجاً لتفعيل القوائم." : "Balance sheet is balanced at zero/opening. Post a journal to activate statements."
    };
  }
  if (stmt.netProfit < 0) {
    return {
      level: "red",
      title: isArabic ? "إشارة حمراء — خسارة صافية في القوائم" : "RED ALERT — Net Loss Recorded",
      subtitle: isArabic ? `صافي الخسارة ${stmt.netProfit}؛ راجع التسعير والهدر فوراً.` : `Net loss of ${stmt.netProfit}; review pricing and overhead waste.`
    };
  }
  return {
    level: "green",
    title: isArabic ? "إشارة خضراء — الميزانية متوازنة والأداء موجب" : "GREEN SIGNAL — Equation Balanced & Profitable",
    subtitle: isArabic ? "الأصول تساوي الالتزامات + الملكية بالكامل مع فائض ربحي إيجابي." : "Assets equal Liabilities + Equity cleanly with positive net profit."
  };
}

export function evaluateInventorySignal(items, bookTotal, replTotal, isArabic = true) {
  const thirtyDaysIso = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  const expiring = items.filter((it) => it.expiryDate && it.expiryDate <= thirtyDaysIso).length;
  const gap = money(replTotal - bookTotal);
  const gapPct = bookTotal > 0 ? (gap / bookTotal) * 100 : 0;

  if (!items.length) {
    return {
      level: "yellow",
      title: isArabic ? "إشارة صفراء — المخزون فارغ" : "YELLOW NOTICE — Inventory Register Empty",
      subtitle: isArabic ? "أضف أصناف المخزون لتفعيل مراقبة الاستبدال وتنبيهات انتهاء الصلاحية." : "Add stock items to track replacement capital gap and expiry warnings."
    };
  }
  if (expiring > 0 || gapPct >= 25) {
    return {
      level: "red",
      title: isArabic ? `إشارة حمراء — ${expiring} تشغيلة قريبة الانتهاء أو فجوة استبدال حرجة` : `RED ALERT — ${expiring} Batch(es) Expiring or Severe Replacement Gap`,
      subtitle: isArabic ? `فجوة رأس المال للاستبدال: ${gap}. قم بتحديث الأسعار أو تصريف المخزون فوراً.` : `Replacement gap is ${gap}. Reprice or move expiring batches urgently.`
    };
  }
  if (gapPct > 10) {
    return {
      level: "yellow",
      title: isArabic ? "إشارة صفراء — فجوة استبدال متزايدة" : "YELLOW NOTICE — Growing Replacement Gap",
      subtitle: isArabic ? `تكلفة الاستبدال أعلى من الدفترية بنسبة ${pct4(gapPct)}%.` : `Replacement cost exceeds book value by ${pct4(gapPct)}%.`
    };
  }
  return {
    level: "green",
    title: isArabic ? "إشارة خضراء — توازن مخزني سليم" : "GREEN SIGNAL — Healthy Inventory Coverage",
    subtitle: isArabic ? "لا توجد تشغيلات قريبة الانتهاء وفجوة الاستبدال ضمن النطاق الآمن." : "No batches expiring within 30 days and replacement gap is under control."
  };
}

export function computeDetailedVariance(params) {
  const stdMatAllowed = n(params.stdMaterialQtyPerUnit) * n(params.goodUnits) * n(params.stdMaterialPrice);
  const actMatCost = n(params.actualMaterialQty) * n(params.actualMaterialPrice);
  const matPriceVar = money((n(params.actualMaterialPrice) - n(params.stdMaterialPrice)) * n(params.actualMaterialQty));
  const matUsageVar = money((n(params.actualMaterialQty) - n(params.stdMaterialQtyPerUnit) * n(params.goodUnits)) * n(params.stdMaterialPrice));

  const stdLabHrsAllowed = n(params.stdLaborHoursPerUnit) * n(params.goodUnits);
  const stdLabAllowed = stdLabHrsAllowed * n(params.stdLaborRate);
  const actLabCost = n(params.actualLaborHours) * n(params.actualLaborRate);
  const labRateVar = money((n(params.actualLaborRate) - n(params.stdLaborRate)) * n(params.actualLaborHours));
  const labEffVar = money((n(params.actualLaborHours) - stdLabHrsAllowed) * n(params.stdLaborRate));

  const stdOvhAllowed = n(params.stdOverheadPerUnit) * n(params.goodUnits);
  const ovhVar = money(n(params.actualOverheadCost) - stdOvhAllowed);

  const stdTotal = money(stdMatAllowed + stdLabAllowed + stdOvhAllowed);
  const actTotal = money(actMatCost + actLabCost + n(params.actualOverheadCost));
  const totalVar = money(actTotal - stdTotal);

  return {
    materialPriceVariance: matPriceVar,
    materialUsageVariance: matUsageVar,
    laborRateVariance: labRateVar,
    laborEfficiencyVariance: labEffVar,
    overheadVariance: ovhVar,
    standardTotalAllowed: stdTotal,
    actualTotal: actTotal,
    totalVariance: totalVar
  };
}

export function runExtendedScenario({ baseMaterial, baseLabor, baseOverheadAndOther, baseRevenue, materialChangePct = 0, laborChangePct = 0, overheadChangePct = 0, priceChangePct = 0, fxChangePct = 0, volumeChangePct = 0 }) {
  const baseTotalCost = money(n(baseMaterial) + n(baseLabor) + n(baseOverheadAndOther));
  const baseProfit = money(n(baseRevenue) - baseTotalCost);
  const volFactor = 1 + n(volumeChangePct) / 100;
  const fxFactor = 1 + n(fxChangePct) / 100;

  const newMat = n(baseMaterial) * (1 + n(materialChangePct) / 100) * fxFactor * volFactor;
  const newLab = n(baseLabor) * (1 + n(laborChangePct) / 100) * volFactor;
  const newOvh = n(baseOverheadAndOther) * (1 + n(overheadChangePct) / 100);
  const newRev = n(baseRevenue) * (1 + n(priceChangePct) / 100) * volFactor;

  const scenarioTotalCost = money(newMat + newLab + newOvh);
  const scenarioProfit = money(newRev - scenarioTotalCost);
  return {
    baseTotalCost,
    scenarioTotalCost,
    baseProfit,
    scenarioProfit,
    profitDelta: money(scenarioProfit - baseProfit)
  };
}

export function generateFinancialStatements(accounts, postedEntries) {
  const drMap = new Map();
  const crMap = new Map();
  for (const je of postedEntries) {
    if (je.status !== "posted" && je.status !== "reversed") continue;
    for (const l of je.lines) {
      drMap.set(l.accountCode, (drMap.get(l.accountCode) || 0) + n(l.debit));
      crMap.set(l.accountCode, (crMap.get(l.accountCode) || 0) + n(l.credit));
    }
  }

  let totalTrialDebits = 0;
  let totalTrialCredits = 0;
  let revenue = 0, cogs = 0, operatingExpenses = 0, interestAndOther = 0;
  let cashAndBank = 0, receivables = 0, inventoryBookValue = 0, nonCurrentAssets = 0;
  let currentLiabilities = 0, nonCurrentLiabilities = 0, equityBase = 0;

  const trialBalance = accounts.map((acc) => {
    const dr = drMap.get(acc.code) || 0;
    const cr = crMap.get(acc.code) || 0;
    const open = n(acc.openingBalance);
    const net = acc.normalBalance === "debit" ? open + dr - cr : open + cr - dr;
    const debitBal = acc.normalBalance === "debit" ? (net >= 0 ? money(net) : 0) : (net < 0 ? money(Math.abs(net)) : 0);
    const creditBal = acc.normalBalance === "credit" ? (net >= 0 ? money(net) : 0) : (net < 0 ? money(Math.abs(net)) : 0);
    totalTrialDebits += debitBal;
    totalTrialCredits += creditBal;

    if (acc.accountType === "revenue") revenue += net;
    else if (acc.accountType === "cogs") cogs += net;
    else if (acc.accountType === "expense") {
      if (acc.subType === "interest" || acc.subType === "tax") interestAndOther += net;
      else operatingExpenses += net;
    } else if (acc.accountType === "asset") {
      if (acc.subType === "cash") cashAndBank += net;
      else if (acc.subType === "receivable") receivables += net;
      else if (acc.subType === "inventory") inventoryBookValue += net;
      else if (acc.subType === "fixed_asset") nonCurrentAssets += net;
    } else if (acc.accountType === "liability") {
      if (acc.subType === "long_term_liability") nonCurrentLiabilities += net;
      else currentLiabilities += net;
    } else if (acc.accountType === "equity") {
      equityBase += net;
    }
    return { code: acc.code, name: acc.name, nameAr: acc.nameAr || acc.name, accountType: acc.accountType, debitBalance: debitBal, creditBalance: creditBal };
  });

  const grossProfit = money(revenue - cogs);
  const operatingProfit = money(grossProfit - operatingExpenses);
  const netProfit = money(operatingProfit - interestAndOther);
  const currentAssets = money(cashAndBank + receivables + inventoryBookValue);
  const totalAssets = money(currentAssets + nonCurrentAssets);
  const totalLiabilities = money(currentLiabilities + nonCurrentLiabilities);
  const totalEquityWithNetIncome = money(equityBase + netProfit);
  const totalLiabilitiesAndEquity = money(totalLiabilities + totalEquityWithNetIncome);
  const isBalanced = Math.abs(totalAssets - totalLiabilitiesAndEquity) <= 0.05;

  return {
    trialBalance,
    totalTrialDebits: money(totalTrialDebits),
    totalTrialCredits: money(totalTrialCredits),
    revenue: money(revenue),
    cogs: money(cogs),
    grossProfit,
    operatingExpenses: money(operatingExpenses),
    operatingProfit,
    interestAndOther: money(interestAndOther),
    netProfit,
    cashAndBank: money(cashAndBank),
    receivables: money(receivables),
    inventoryBookValue: money(inventoryBookValue),
    currentAssets,
    nonCurrentAssets: money(nonCurrentAssets),
    totalAssets,
    currentLiabilities: money(currentLiabilities),
    nonCurrentLiabilities: money(nonCurrentLiabilities),
    totalLiabilities,
    totalEquityWithNetIncome,
    totalLiabilitiesAndEquity,
    isAccountingEquationBalanced: isBalanced,
    operatingCashFlow: netProfit,
    investingCashFlow: money(-nonCurrentAssets),
    financingCashFlow: money(nonCurrentLiabilities + equityBase),
    closingCash: money(cashAndBank)
  };
}

export function computeFinancialRatios(stmt, isArabic = true) {
  function ratio(key, nameAr, nameEn, catAr, catEn, num, den, mult = 1, unit = "x", unitAr = "مرة", formulaAr = "", formulaEn = "", missingAr = "المقام صفر أو غير مكتمل", missingEn = "Denominator is zero") {
    const valid = den > 0;
    const val = valid ? pct4((num / den) * mult) : null;
    return {
      key,
      label: isArabic ? nameAr : nameEn,
      category: isArabic ? catAr : catEn,
      value: val,
      unitSuffix: isArabic ? unitAr : unit,
      formula: isArabic ? formulaAr : formulaEn,
      missingDataWarning: valid ? null : (isArabic ? missingAr : missingEn)
    };
  }
  const quickAssets = stmt.cashAndBank + stmt.receivables;
  const ebit = stmt.operatingProfit;
  return [
    ratio("current_ratio", "نسبة التداول الجارية", "Current Ratio", "السيولة", "Liquidity", stmt.currentAssets, stmt.currentLiabilities, 1, "x", "مرة", "الأصول المتداولة ÷ الالتزامات المتداولة", "Current Assets / Current Liabilities"),
    ratio("quick_ratio", "نسبة السيولة السريعة", "Quick Ratio", "السيولة", "Liquidity", quickAssets, stmt.currentLiabilities, 1, "x", "مرة", "(النقدية + الذمم المدينة) ÷ الالتزامات المتداولة", "(Cash + AR) / Current Liabilities"),
    ratio("cash_ratio", "نسبة النقدية", "Cash Ratio", "السيولة", "Liquidity", stmt.cashAndBank, stmt.currentLiabilities, 1, "x", "مرة", "النقدية ÷ الالتزامات المتداولة", "Cash / Current Liabilities"),
    ratio("working_capital_ratio", "رأس المال العامل للأصول", "Working Capital Ratio", "السيولة", "Liquidity", stmt.currentAssets - stmt.currentLiabilities, stmt.totalAssets, 100, "%", "%", "رأس المال العامل ÷ إجمالي الأصول", "Working Capital / Total Assets"),
    ratio("gross_profit_margin", "هامش مجمل الربح", "Gross Profit Margin", "الربحية", "Profitability", stmt.grossProfit, stmt.revenue, 100, "%", "%", "مجمل الربح ÷ الإيرادات", "Gross Profit / Revenue"),
    ratio("operating_profit_margin", "هامش الربح التشغيلي", "Operating Margin", "الربحية", "Profitability", stmt.operatingProfit, stmt.revenue, 100, "%", "%", "الربح التشغيلي ÷ الإيرادات", "Operating Profit / Revenue"),
    ratio("net_profit_margin", "هامش صافي الربح", "Net Profit Margin", "الربحية", "Profitability", stmt.netProfit, stmt.revenue, 100, "%", "%", "صافي الربح ÷ الإيرادات", "Net Profit / Revenue"),
    ratio("return_on_assets_roa", "العائد على الأصول", "Return on Assets (ROA)", "الربحية", "Profitability", stmt.netProfit, stmt.totalAssets, 100, "%", "%", "صافي الربح ÷ إجمالي الأصول", "Net Profit / Total Assets"),
    ratio("return_on_equity_roe", "العائد على الملكية", "Return on Equity (ROE)", "الربحية", "Profitability", stmt.netProfit, stmt.totalEquityWithNetIncome, 100, "%", "%", "صافي الربح ÷ حقوق الملكية", "Net Profit / Total Equity"),
    ratio("return_on_capital_employed", "العائد على رأس المال المستثمر", "ROCE", "الربحية", "Profitability", ebit, stmt.totalAssets - stmt.currentLiabilities, 100, "%", "%", "الربح قبل الفوائد ÷ رأس المال الموظف", "EBIT / Capital Employed"),
    ratio("inventory_turnover", "معدل دوران المخزون", "Inventory Turnover", "الكفاءة", "Efficiency", stmt.cogs, stmt.inventoryBookValue, 1, "x", "مرة", "تكلفة المبيعات ÷ قيمة المخزون", "COGS / Inventory Book Value"),
    ratio("dio_days_inventory", "أيام بقاء المخزون", "Days Inventory (DIO)", "الكفاءة", "Efficiency", stmt.inventoryBookValue * 365, stmt.cogs, 1, "days", "يوم", "(المخزون ÷ تكلفة المبيعات) × 365", "(Inventory / COGS) * 365"),
    ratio("receivables_turnover", "معدل دوران الذمم", "Receivables Turnover", "الكفاءة", "Efficiency", stmt.revenue, stmt.receivables, 1, "x", "مرة", "الإيرادات ÷ الذمم المدينة", "Revenue / Receivables"),
    ratio("dso_days_sales_outstanding", "أيام تحصيل الذمم", "Days Sales (DSO)", "الكفاءة", "Efficiency", stmt.receivables * 365, stmt.revenue, 1, "days", "يوم", "(الذمم المدينة ÷ الإيرادات) × 365", "(Receivables / Revenue) * 365"),
    ratio("asset_turnover", "معدل دوران الأصول", "Asset Turnover", "الكفاءة", "Efficiency", stmt.revenue, stmt.totalAssets, 1, "x", "مرة", "الإيرادات ÷ إجمالي الأصول", "Revenue / Total Assets"),
    ratio("debt_to_equity", "الدين إلى حقوق الملكية", "Debt to Equity", "الرفع المالي", "Leverage", stmt.totalLiabilities, stmt.totalEquityWithNetIncome, 1, "x", "مرة", "إجمالي الالتزامات ÷ حقوق الملكية", "Total Liabilities / Equity"),
    ratio("debt_ratio", "نسبة الديون إلى الأصول", "Debt Ratio", "الرفع المالي", "Leverage", stmt.totalLiabilities, stmt.totalAssets, 100, "%", "%", "إجمالي الالتزامات ÷ إجمالي الأصول", "Total Liabilities / Total Assets"),
    ratio("interest_coverage", "تغطية الفوائد", "Interest Coverage", "الرفع المالي", "Leverage", ebit, stmt.interestAndOther, 1, "x", "مرة", "الربح قبل الفوائد ÷ تكلفة التمويل", "EBIT / Interest & Finance Cost")
  ];
}

export function computeTrialCountdown(trialEndsAtMillis, nowMillis) {
  const diff = Math.max(0, trialEndsAtMillis - nowMillis);
  const totalSecs = Math.floor(diff / 1000);
  const days = Math.floor(totalSecs / 86400);
  const hours = Math.floor((totalSecs % 86400) / 3600);
  const minutes = Math.floor((totalSecs % 3600) / 60);
  const seconds = totalSecs % 60;
  return { days, hours, minutes, seconds, isExpired: diff <= 0 };
}

export function isAccountingOrFvgTopic(question) {
  const q = String(question || "").trim().toLowerCase();
  if (!q) return false;
  const keywords = [
    "fvg", "cost", "margin", "markup", "profit", "loss", "revenue", "ledger",
    "journal", "ratio", "balance", "asset", "liability", "equity", "inflation",
    "fx", "exchange", "inventory", "fifo", "bom", "break-even", "breakeven",
    "variance", "scenario", "what-if", "invoice", "ocr", "trial", "plan",
    "price", "sector", "budget", "cash", "credit", "debit", "financial", "accounting",
    "دليل رؤيتك", "تكلف", "هامش", "ربح", "خسار", "إيراد", "قيد", "قيود", "نسب",
    "ميزان", "أصول", "التزام", "حقوق", "تضخم", "صرف", "مخزون", "تركيب", "تعادل",
    "انحراف", "فاتور", "اشتراك", "باقة", "تجرب", "سعر", "أسعار", "قطاع", "موازن",
    "نقد", "مدين", "دائن", "محاسب", "مالي", "الذمم"
  ];
  return keywords.some((kw) => q.includes(kw));
}

export function calculateSectorModel(sectorKey, vals = {}) {
  if (sectorKey === "food_beverage") {
    const raw = Math.max(0, n(vals.batchMat));
    const lossPct = Math.min(95, Math.max(0, n(vals.foodLossPct)));
    const pack = Math.max(0, n(vals.foodPack));
    const units = Math.max(1, n(vals.batchUnits));
    const netYieldUnits = Math.max(1, units * (1 - lossPct / 100));
    const totalBatch = money(raw + pack);
    const unitCost = money(totalBatch / netYieldUnits);
    return { totalBatch, effectiveUnits: money(netYieldUnits), unitCost };
  }
  const raw = Math.max(0, n(vals.batchMat));
  const ovh = Math.max(0, n(vals.batchOvh));
  const scrapPct = Math.max(0, n(vals.batchScrapPct));
  const units = Math.max(1, n(vals.batchUnits));
  const adjustedMat = raw * (1 + scrapPct / 100);
  const totalBatch = money(adjustedMat + ovh);
  const unitCost = money(totalBatch / units);
  return { totalBatch, effectiveUnits: units, unitCost };
}

export function computeFinancialHealthScore(stmt, costSnapshots = [], latestInfPct = null, isArabic = true) {
  let score = 50;
  if (stmt?.isAccountingEquationBalanced) score += 15;
  else score -= 25;

  if ((stmt?.netProfit || 0) > 0) score += 15;
  else if ((stmt?.netProfit || 0) < 0) score -= 20;

  const lastSnap = costSnapshots.length ? costSnapshots[costSnapshots.length - 1] : null;
  const margin = lastSnap?.coreResult?.profitMarginPct;
  if (margin != null) {
    if (margin >= 20) score += 15;
    else if (margin >= 10) score += 8;
    else score -= 12;
  }
  if (latestInfPct != null && latestInfPct > 15 && (margin == null || margin < latestInfPct)) {
    score -= 10;
  } else {
    score += 5;
  }
  score = Math.max(0, Math.min(100, Math.round(score)));
  const grade = score >= 85 ? "A" : (score >= 70 ? "B" : (score >= 50 ? "C" : "D"));
  const statusLabel = isArabic
    ? (score >= 75 ? "صحة مالية ممتازة" : (score >= 55 ? "استقرار مع نقاط مراقبة" : "يتطلب مراجعة عاجلة"))
    : (score >= 75 ? "Strong Financial Health" : (score >= 55 ? "Stable — Monitor Risks" : "Needs Immediate Review"));
  return { score, grade, statusLabel };
}

export function runFormulaValidationSuite(isArabic = true) {
  const checks = [];
  const push = (id, titleEn, titleAr, eq, expected, actual, passed) => {
    checks.push({
      id,
      title: isArabic ? titleAr : titleEn,
      equation: eq,
      expected,
      actual,
      passed: Boolean(passed)
    });
  };

  // 1. Absorption vs Variable Unit Cost
  const baseIn = { directMaterial: 500, indirectMaterial: 0, directLabor: 200, indirectLabor: 0, overhead: 100, energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0, fixedCosts: 200, goodUnits: 100, sellingPrice: 15 };
  const absOut = calculateCostingByMethod("absorption", baseIn, 20, null, isArabic);
  const varOut = calculateCostingByMethod("variable", baseIn, 20, null, isArabic);
  push("abs_vs_var", "Absorption vs Variable Costing", "التكلفة الامتصاصية مقابل المتغيرة", "Absorption=10.00 | Variable=8.00", "10 / 8", `${absOut.coreResult.unitCost} / ${varOut.coreResult.unitCost}`, absOut.coreResult.unitCost === 10 && varOut.coreResult.unitCost === 8);

  // 2. Profit Margin & Markup
  const cRes = calculateCosting({ directMaterial: 60, indirectMaterial: 0, directLabor: 0, indirectLabor: 0, overhead: 0, energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0, fixedCosts: 0, goodUnits: 1, sellingPrice: 100 });
  push("margin_markup", "Profit Margin % vs Markup %", "هامش الربح % مقابل نسبة الإضافة %", "Price=100, Cost=60 => Margin=40%, Markup=66.6667%", "40% / 66.6667%", `${cRes.profitMarginPct}% / ${cRes.markupPct}%`, cRes.profitMarginPct === 40 && Math.abs(cRes.markupPct - 66.6667) < 0.01);

  // 3. Target Costing Allowable Cost
  const tgt = calculateCostingByMethod("target", baseIn, 40, null, isArabic);
  push("target_costing", "Target Costing Allowable Limit", "التكلفة المستهدفة وحد التكلفة المسموح", "15 × (1 − 40%) = 9.00", "9", String(tgt.targetAllowableUnitCost), tgt.targetAllowableUnitCost === 9);

  // 4. Break-even CEILING
  const beRes = calculateCosting({ directMaterial: 6, indirectMaterial: 0, directLabor: 0, indirectLabor: 0, overhead: 0, energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0, fixedCosts: 25, goodUnits: 10, sellingPrice: 10 });
  push("break_even", "CVP Break-Even Rounding (CEILING)", "نقطة التعادل والتقريب للأعلى", "CEILING(25 / (10 - 0.6)) = 3", "3", String(beRes.breakEvenUnits), beRes.breakEvenUnits === 3);

  // 5. Compounded Inflation
  const inf = calculateCompoundedInflation([10, 10]);
  push("compound_inf", "Compounded Monthly Inflation", "معادلة التضخم المركب", "(1.10 × 1.10 − 1) × 100 = 21%", "21%", `${inf}%`, inf === 21);

  // 6. Detailed Variance
  const vr = computeDetailedVariance({ goodUnits: 10, stdMaterialQtyPerUnit: 2, stdMaterialPrice: 10, actualMaterialQty: 22, actualMaterialPrice: 11, stdLaborHoursPerUnit: 1, stdLaborRate: 20, actualLaborHours: 9, actualLaborRate: 22, stdOverheadPerUnit: 5, actualOverheadCost: 50 });
  push("variance_decomp", "Material & Labor Variance Decomposition", "تفكيك انحرافات المواد والعمالة", "MPV=22, MUV=20, LRV=18, LEV=-20, Total=40", "40", String(vr.totalVariance), vr.materialPriceVariance === 22 && vr.totalVariance === 40);

  // 7. Zero-Denominator Ratio Guard
  const emptyStmt = generateFinancialStatements([], []);
  const zRatios = computeFinancialRatios(emptyStmt, isArabic);
  const currR = zRatios.find((r) => r.key === "current_ratio");
  push("ratio_zero_guard", "Zero-Denominator Ratio Guard", "حماية النسب من القسمة على صفر", "Div by 0 => null (Not Available)", "null", String(currR?.value), currR?.value === null);

  // 8. Accounting Equation
  const testAccs = [
    { code: "1010", name: "Cash", accountType: "asset", subType: "cash", normalBalance: "debit", openingBalance: "0" },
    { code: "3010", name: "Capital", accountType: "equity", subType: "equity", normalBalance: "credit", openingBalance: "0" }
  ];
  const testStmt = generateFinancialStatements(testAccs, [{ status: "posted", lines: [{ accountCode: "1010", debit: 5000, credit: 0 }, { accountCode: "3010", debit: 0, credit: 5000 }] }]);
  push("acct_equation", "Accounting Equation Balance", "توازن المعادلة المحاسبية", "Assets(5000) == Liabilities + Equity(5000)", "Balanced", testStmt.isAccountingEquationBalanced ? "Balanced" : "Unbalanced", testStmt.isAccountingEquationBalanced);

  // 9. OCR Arithmetic Validation
  const ocrOk = validateOcrArithmetic(1000, 50, 150, 1100);
  const ocrBad = validateOcrArithmetic(1000, 50, 150, 1250);
  push("ocr_math", "OCR Invoice Arithmetic Guard", "التحقق الحسابي لفواتير الكاميرا", "1000 − 50 + 150 = 1100 vs 1250", "true / false", `${ocrOk.isConsistent} / ${ocrBad.isConsistent}`, ocrOk.isConsistent && !ocrBad.isConsistent);

  // 10. FX Freshness Guard
  const fxOld = assessFxRateFreshness("2020-01-01", "draft");
  push("fx_stale", "FX >7-Day Stale & Draft Guard", "تنبيه تقادم سعر الصرف (> 7 أيام)", "Old draft triggers isStale && isUnapproved", "true / true", `${fxOld.isStale} / ${fxOld.isUnapproved}`, fxOld.isStale && fxOld.isUnapproved);

  // 11. Bankak / Fawry Transfer Parser & Exact Plan Match
  const parsedBankak = parseSudanTransferSmsOrText("تم تحويل مبلغ ٦٥٬٠٠٠.٠٠ جنيه عبر بنكك رقم العملية 884512 بتاريخ 2026-11-22 من السيد أحمد علي");
  const bankakMatch = evaluatePaymentReceiptMatch(parsedBankak.amount, parsedBankak.currency, parsedBankak.referenceNo, 29, 65000, []);
  push("bankak_match", "Bankak/Fawry Arabic Receipt Parser & Match", "تحليل ومطابقة إشعارات بنكك وفوري", "65000 SDG ref=884512 => exact_match", "exact_match", bankakMatch.status, parsedBankak.paymentMethod === "bankak" && bankakMatch.status === "exact_match");

  // 12. 13-Week Cash Runway Stress Test (-20% to -40%)
  const stressRunway = compute13WeekCashRunway({ startingCash: 100000, weeklyBaselineInflow: 20000, weeklyBaselineOutflow: 30000, salesDropPct: 40, fxShockPct: 20, arDelayWeeks: 1 });
  push("cash_runway", "13-Week Cash Runway Stress Test (-40%)", "توقع السيولة لـ 13 أسبوعاً تحت الضغط", "Inflow -40% & FX +20% depletes cash inside 13w", "survives=false (<14w)", `survives=${stressRunway.survives13Weeks} (${stressRunway.runwayWeeks}w)`, !stressRunway.survives13Weeks && stressRunway.runwayWeeks <= 10);

  // 13. Multi-Branch Consolidation & Inter-Company Elimination
  const consTest = consolidateMultiBranchPerformance([
    { orgId: "b1", branchName: "Factory North", currency: "SDG", revenue: 5000, cogs: 3000, opEx: 500, scrapLoss: 100 },
    { orgId: "b2", branchName: "Retail South", currency: "SDG", revenue: 3000, cogs: 2000, opEx: 400, scrapLoss: 50 }
  ], 1000, 300);
  push("multi_branch", "Multi-Branch Intercompany Elimination", "التوحيد متعدد الفروع وإلغاء المعاملات البينية", "8000 - 1000 InterCo = 7000 Rev | Net Profit=2100", "7000 / 2100", `${consTest.consolidatedRevenue} / ${consTest.consolidatedNetProfit}`, consTest.consolidatedRevenue === 7000 && consTest.consolidatedNetProfit === 2100);

  return checks;
}

export function normalizeArabicDigits(raw) {
  return String(raw || "")
    .replace(/[٠۰]/g, "0").replace(/[١۱]/g, "1").replace(/[٢۲]/g, "2")
    .replace(/[٣۳]/g, "3").replace(/[٤۴]/g, "4").replace(/[٥۵]/g, "5")
    .replace(/[٦۶]/g, "6").replace(/[٧۷]/g, "7").replace(/[٨۸]/g, "8")
    .replace(/[٩۹]/g, "9").replace(/٫/g, ".").replace(/٬/g, ",");
}

export function parseSudanTransferSmsOrText(rawText) {
  const text = normalizeArabicDigits(rawText).trim();
  if (!text) return { paymentMethod: "bankak", referenceNo: "", amount: "", currency: "SDG", transferDate: "", senderName: "", last4Digits: "", confidenceNote: "Empty" };
  const lower = text.toLowerCase();
  let paymentMethod = "bank_transfer";
  if (lower.includes("فوري") || lower.includes("fawry") || lower.includes("faisal")) paymentMethod = "fawry";
  else if (lower.includes("أوكاش") || lower.includes("اوكاش") || lower.includes("ocash") || lower.includes("o-cash")) paymentMethod = "ocash";
  else if (lower.includes("بنكك") || lower.includes("bankak") || lower.includes("الخرطوم") || lower.includes("bok")) paymentMethod = "bankak";

  let currency = "SDG";
  if (lower.includes("usd") || lower.includes("$") || lower.includes("دولار")) currency = "USD";
  else if (lower.includes("sar") || lower.includes("ريال")) currency = "SAR";
  else if (lower.includes("aed") || lower.includes("درهم")) currency = "AED";
  else if (lower.includes("egp")) currency = "EGP";

  const amtRe1 = /(?:amount|amt|total|مبلغ|بمبلغ|قدرها|تم\s*تحويل|استلام|دفع|سداد|sdg|usd|ج\.س)[:\s]*([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i;
  const amtRe2 = /([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:sdg|usd|sar|aed|egp|جنيه|ج\.س)/i;
  const rawAmt = ((text.match(amtRe1)?.[1] || text.match(amtRe2)?.[1]) || "").replace(/,/g, "");

  const refRe = /(?:ref(?:erence)?(?:\s*no)?|trx(?:\s*id)?|txn|id|رقم\s*العملية|المرجع|رقم\s*المرجع|رقم\s*التحويل|إشعار)[:#\s]*([A-Za-z0-9\-]{4,24})/i;
  const referenceNo = text.match(refRe)?.[1] || "";

  let transferDate = "";
  const isoM = text.match(/\b(20\d{2}[-/]\d{1,2}[-/]\d{1,2})\b/);
  const dmyM = text.match(/\b(\d{1,2}[-/]\d{1,2}[-/]20\d{2})\b/);
  if (isoM) transferDate = isoM[1].replace(/\//g, "-");
  else if (dmyM) {
    const p = dmyM[1].split(/[-/]/);
    if (p.length === 3) transferDate = `${p[2]}-${p[1].padStart(2, "0")}-${p[0].padStart(2, "0")}`;
  }

  const senderRe = /(?:from|sender|by|من\s*حساب|من\s*السيد|المرسل|بواسطة)[:\s]+([^\n\r,|]{3,60})/i;
  const senderName = (text.match(senderRe)?.[1] || "").trim();
  const last4Re = /(?:\*{2,}|x{2,}|ending\s*in|آخر\s*4\s*أرقام)[:\s-]*(\d{4})\b/i;
  const refDigits = referenceNo.match(/\d{4}/g);
  const last4Digits = (text.match(last4Re)?.[1] || (refDigits ? refDigits[refDigits.length - 1] : "")).slice(-4);

  return {
    paymentMethod, referenceNo, amount: rawAmt, currency, transferDate, senderName, last4Digits,
    confidenceNote: rawAmt && referenceNo ? "SMS/Text Parsed" : "Partial SMS Match"
  };
}

export function evaluatePaymentReceiptMatch(parsedAmountStr, parsedCurrency, parsedRef, expectedUsd, expectedSdg, existingRefs = []) {
  const cleanRef = String(parsedRef || "").trim();
  const curr = String(parsedCurrency || "SDG").trim().toUpperCase() || "SDG";
  const target = curr === "USD" ? money(expectedUsd) : money(expectedSdg);
  const expectedAmountLabel = `${target} ${curr === "USD" ? "USD" : "SDG"}`;

  if (cleanRef && existingRefs.some((r) => String(r).trim().toLowerCase() === cleanRef.toLowerCase())) {
    return {
      status: "duplicate_ref",
      expectedAmountLabel,
      noteAr: `تحذير: رقم العملية (${cleanRef}) مستخدم مسبقاً!`,
      noteEn: `Alert: Reference (${cleanRef}) already exists in history!`
    };
  }
  const paid = n(parsedAmountStr);
  if (!(paid > 0) || cleanRef.length < 2) {
    return {
      status: "incomplete",
      expectedAmountLabel,
      noteAr: `بيانات غير مكتملة للمطابقة (المطلوب: ${expectedAmountLabel}).`,
      noteEn: `Incomplete receipt details (Expected: ${expectedAmountLabel}).`
    };
  }
  if ((curr !== "SDG" && curr !== "USD") || !(target > 0)) {
    return {
      status: "close_match",
      expectedAmountLabel,
      noteAr: `تم استخراج ${paid} ${curr} بمرجع ${cleanRef}؛ يُراجع مقابل ${expectedAmountLabel}.`,
      noteEn: `Extracted ${paid} ${curr} (Ref: ${cleanRef}); check daily FX vs ${expectedAmountLabel}.`
    };
  }
  const diff = Math.abs(paid - target);
  const pctDiff = (diff / target) * 100;
  if (diff <= 1) {
    return {
      status: "exact_match",
      expectedAmountLabel,
      noteAr: `تطابق تام ✓: المبلغ ${money(paid)} ${curr} يطابق الباقة (${expectedAmountLabel}).`,
      noteEn: `Exact Match ✓: ${money(paid)} ${curr} matches plan fee (${expectedAmountLabel}).`
    };
  }
  if (pctDiff <= 3) {
    return {
      status: "close_match",
      expectedAmountLabel,
      noteAr: `تطابق قريب (~${pct4(pctDiff)}% فرق تحويل): ${money(paid)} ${curr} مقابل ${expectedAmountLabel}.`,
      noteEn: `Close Match (~${pct4(pctDiff)}% variance): ${money(paid)} ${curr} vs ${expectedAmountLabel}.`
    };
  }
  return {
    status: "mismatch",
    expectedAmountLabel,
    noteAr: `اختلاف مبلغ (${money(paid)} ${curr} مقابل المتوقع ${expectedAmountLabel}).`,
    noteEn: `Amount mismatch (${money(paid)} ${curr} vs expected ${expectedAmountLabel}).`
  };
}

export function compute13WeekCashRunway({
  startingCash = 0,
  weeklyBaselineInflow = 0,
  weeklyBaselineOutflow = 0,
  openReceivables = 0,
  openPayables = 0,
  fxShockPct = 0,
  salesDropPct = 0,
  arDelayWeeks = 0
} = {}) {
  const safeDrop = Math.min(90, Math.max(0, n(salesDropPct)));
  const safeFx = Math.min(300, Math.max(-50, n(fxShockPct)));
  const delayW = Math.min(6, Math.max(0, Math.round(n(arDelayWeeks))));
  const salesMult = 1 - safeDrop / 100;
  const fxOutMult = Math.max(0.5, 1 + (safeFx / 100) * 0.45);
  const baseIn = Math.max(0, n(weeklyBaselineInflow)) * salesMult;
  const baseOut = Math.max(0, n(weeklyBaselineOutflow)) * fxOutMult;
  const arChunk = openReceivables > 0 ? n(openReceivables) / 4 : 0;
  const apChunk = openPayables > 0 ? n(openPayables) / 4 : 0;

  let cash = money(startingCash);
  let minCash = cash;
  let depletionWeek = null;
  let totalInflows = 0;
  let totalOutflows = 0;
  const weeks = [];

  for (let w = 1; w <= 13; w++) {
    const arOffset = w - delayW;
    const arCol = arOffset >= 1 && arOffset <= 4 ? arChunk : 0;
    let regIn = baseIn;
    if (delayW > 0 && w <= delayW) regIn = baseIn * 0.65;
    else if (delayW > 0 && w <= delayW * 2) regIn = baseIn * 1.2;

    const inflow = money(regIn + arCol);
    const apPay = w <= 4 ? apChunk : 0;
    const outflow = money(baseOut + apPay);
    const netCashFlow = money(inflow - outflow);
    cash = money(cash + netCashFlow);
    if (cash < minCash) minCash = cash;
    if (depletionWeek === null && cash <= 0) depletionWeek = w;
    totalInflows = money(totalInflows + inflow);
    totalOutflows = money(totalOutflows + outflow);
    weeks.push({ weekIndex: w, labelEn: `W${w}`, labelAr: `أ${w}`, inflow, outflow, netCashFlow, endingCash: cash });
  }

  const runwayWeeks = depletionWeek ?? 14;
  const survives13Weeks = depletionWeek === null;
  const riskLevel = depletionWeek !== null && depletionWeek <= 6 ? "red" : (depletionWeek !== null || minCash <= 0 ? "yellow" : "green");
  return {
    startingCash: money(startingCash),
    endingCashWeek13: cash,
    minimumCash: minCash,
    runwayWeeks,
    survives13Weeks,
    riskLevel,
    totalInflows,
    totalOutflows,
    weeks,
    summaryAr: survives13Weeks
      ? `تغطي السيولة كافة الـ 13 أسبوعاً (+13 أسبوعاً). أدنى رصيد متوقع: ${minCash}.`
      : `تحذير: تنفد السيولة في الأسبوع ${depletionWeek} (-${pct4(safeDrop)}% مبيعات / ${pct4(safeFx)}% صرف).`,
    summaryEn: survives13Weeks
      ? `Liquidity covers all 13 weeks (>13w runway). Minimum cash: ${minCash}.`
      : `Warning: Cash depletes in Week ${depletionWeek} (-${pct4(safeDrop)}% sales / ${pct4(safeFx)}% FX).`
  };
}

export function consolidateMultiBranchPerformance(branches = [], intercompanyRevenueCost = 0, intercompanyArAp = 0) {
  const elimRevCost = Math.max(0, n(intercompanyRevenueCost));
  const elimArAp = Math.max(0, n(intercompanyArAp));
  let grossGroupRevenue = 0;
  let totalCogs = 0;
  let consolidatedOpEx = 0;
  let totalScrapLoss = 0;
  let totAssets = 0;
  let totLiab = 0;

  const rows = branches.map((b) => {
    const rev = money(b.revenue);
    const cogs = money(b.cogs);
    const opEx = money(b.opEx);
    const scrap = money(b.scrapLoss);
    const netProfit = money(rev - cogs - opEx);
    const profitMarginPct = rev > 0 ? pct4((netProfit / rev) * 100) : 0;
    const scrapPctOfRevenue = rev > 0 ? pct4((scrap / rev) * 100) : 0;
    grossGroupRevenue += rev;
    totalCogs += cogs;
    consolidatedOpEx += opEx;
    totalScrapLoss += scrap;
    totAssets += n(b.assets);
    totLiab += n(b.liabilities);
    return {
      orgId: b.orgId,
      branchName: b.branchName,
      currency: b.currency || "SDG",
      revenue: rev,
      totalCost: money(cogs + opEx),
      netProfit,
      profitMarginPct,
      scrapLoss: scrap,
      scrapPctOfRevenue
    };
  });

  const intercompanyEliminated = money(Math.min(elimRevCost, grossGroupRevenue, totalCogs));
  const consolidatedRevenue = money(grossGroupRevenue - intercompanyEliminated);
  const consolidatedCogs = money(totalCogs - intercompanyEliminated);
  const consolidatedNetProfit = money(consolidatedRevenue - consolidatedCogs - consolidatedOpEx);
  const consolidatedMarginPct = consolidatedRevenue > 0 ? pct4((consolidatedNetProfit / consolidatedRevenue) * 100) : 0;
  const bsElim = Math.min(elimArAp, totAssets, totLiab);
  const sortedMargin = [...rows].sort((a, b) => b.profitMarginPct - a.profitMarginPct);
  const sortedWaste = [...rows].filter((r) => r.scrapLoss > 0).sort((a, b) => b.scrapLoss - a.scrapLoss);

  return {
    branchCount: branches.length,
    grossGroupRevenue: money(grossGroupRevenue),
    intercompanyEliminated,
    consolidatedRevenue,
    consolidatedCogs,
    consolidatedOpEx: money(consolidatedOpEx),
    consolidatedNetProfit,
    consolidatedMarginPct,
    totalScrapLoss: money(totalScrapLoss),
    consolidatedAssets: money(totAssets - bsElim),
    consolidatedLiabilities: money(totLiab - bsElim),
    rows,
    bestMarginBranch: sortedMargin[0]?.branchName || "",
    highestWasteBranch: sortedWaste[0]?.branchName || ""
  };
}

