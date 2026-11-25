import * as Engine from "./engine.js";
import { FVG_FIREBASE_CONFIG, isFirebaseWebConfigured } from "./firebase-config.js";

const STORAGE_KEY = "fvg_web_workspace_v4";
const SESSION_KEY = "fvg_web_session_id";
const VISITOR_KEY = "fvg_web_visitor_id";
const ADMIN_SEC_KEY = "fvg_web_admin_sec_v2";
const ADMIN_SEC_SALT = "FVG_OWNER_SEC_2026_V7::";

function sha256Pure(ascii) {
  const rightRotate = (v, a) => (v >>> a) | (v << (32 - a));
  const mathPow = Math.pow;
  const maxWord = mathPow(2, 32);
  let result = "";
  const words = [];
  const asciiBitLength = ascii.length * 8;
  let hash = [];
  const k = [];
  let primeCounter = 0;
  const isComposite = {};
  for (let candidate = 2; primeCounter < 64; candidate++) {
    if (!isComposite[candidate]) {
      for (let i = 0; i < 313; i += candidate) isComposite[i] = candidate;
      hash[primeCounter] = (mathPow(candidate, 0.5) * maxWord) | 0;
      k[primeCounter++] = (mathPow(candidate, 1 / 3) * maxWord) | 0;
    }
  }
  ascii += "\x80";
  while ((ascii.length % 64) - 56) ascii += "\x00";
  for (let i = 0; i < ascii.length; i++) {
    const j = ascii.charCodeAt(i);
    words[i >> 2] |= j << (((3 - i) % 4) * 8);
  }
  words[words.length] = (asciiBitLength / maxWord) | 0;
  words[words.length] = asciiBitLength;
  for (let j = 0; j < words.length;) {
    const w = words.slice(j, (j += 16));
    const oldHash = hash.slice(0);
    for (let i = 0; i < 64; i++) {
      const w15 = w[i - 15], w2 = w[i - 2];
      const a = hash[0], e = hash[4];
      const temp1 = hash[7]
        + (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25))
        + ((e & hash[5]) ^ ((~e) & hash[6]))
        + k[i]
        + (w[i] = i < 16 ? w[i] : (w[i - 16] + (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) + w[i - 7] + (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))) | 0);
      const temp2 = (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)) + ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]));
      hash = [(temp1 + temp2) | 0].concat(hash);
      hash[4] = (hash[4] + temp1) | 0;
      hash.pop();
    }
    for (let i = 0; i < 8; i++) hash[i] = (hash[i] + oldHash[i]) | 0;
  }
  for (let i = 0; i < 8; i++) {
    for (let j = 3; j + 1; j--) {
      const b = (hash[i] >> (j * 8)) & 255;
      result += (b < 16 ? "0" : "") + b.toString(16);
    }
  }
  return result;
}

function hashAdminSecret(pw) {
  const bytes = unescape(encodeURIComponent(ADMIN_SEC_SALT + String(pw || "")));
  return sha256Pure(bytes);
}

function getWebAdminSecRecord() {
  try {
    const raw = localStorage.getItem(ADMIN_SEC_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed && parsed.setupCompleted === true && parsed.hash ? parsed : null;
  } catch {
    return null;
  }
}

function isAuthorizedAdminEmailWeb(email) {
  return String(email || "").trim().toLowerCase() === Engine.OWNER_EMAIL.toLowerCase();
}

function isWebAdminSetupCompleted() {
  return Boolean(getWebAdminSecRecord());
}

function isWebAdminEmailPendingSetup(email) {
  return isAuthorizedAdminEmailWeb(email) && !isWebAdminSetupCompleted();
}

function validateWebAdminPasswordStrength(pw) {
  const raw = String(pw || "");
  if (raw.length < 8) {
    return t("Password must be at least 8 characters.", "يجب أن تكون كلمة المرور 8 أحرف على الأقل.");
  }
  const hasLetter = /[A-Za-z\u0600-\u06FF]/.test(raw);
  const hasNumOrSym = /[0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?~`]/.test(raw);
  if (!hasLetter || !hasNumOrSym) {
    return t(
      "Password must include at least one letter and one number or symbol.",
      "يجب أن تحتوي كلمة المرور على حرف واحد ورقم أو رمز واحد على الأقل."
    );
  }
  return null;
}

function completeOneTimeWebAdminSetup(email, newPw, confPw) {
  if (!isAuthorizedAdminEmailWeb(email)) {
    return { ok: false, error: t("Unauthorized administrator email.", "البريد الإلكتروني غير مصرح له بإعداد بوابة الإدارة.") };
  }
  if (isWebAdminSetupCompleted()) {
    return { ok: false, error: t("One-time initial admin setup is already completed.", "تم إكمال الإعداد الأولي مسبقاً.") };
  }
  if (String(newPw || "") !== String(confPw || "")) {
    return { ok: false, error: t("Passwords do not match.", "كلمتا المرور غير متطابقتين.") };
  }
  const strengthErr = validateWebAdminPasswordStrength(newPw);
  if (strengthErr) return { ok: false, error: strengthErr };
  const next = { hash: hashAdminSecret(newPw), setupCompleted: true, updatedAt: new Date().toISOString() };
  try {
    localStorage.removeItem("fvg_web_admin_sec_v1");
    localStorage.setItem(ADMIN_SEC_KEY, JSON.stringify(next));
  } catch {}
  return { ok: true };
}

function verifyWebAdminPassword(pw) {
  const rec = getWebAdminSecRecord();
  if (!rec || !pw) return false;
  return hashAdminSecret(pw) === rec.hash;
}

const ADMIN_SEC_QUEUE_KEY = "fvg_admin_sec_queue_v1";

function getWebAdminLockoutSeconds() {
  if (!state.adminLockoutUntil || Date.now() >= state.adminLockoutUntil) return 0;
  return Math.max(1, Math.ceil((state.adminLockoutUntil - Date.now()) / 1000));
}

function resetWebAdminFailState() {
  state.adminFailCount = 0;
  state.adminLockoutUntil = 0;
}

function recordFailedWebAdminAttempt(email, reason) {
  const nextCount = (state.adminFailCount || 0) + 1;
  state.adminFailCount = nextCount;
  if (nextCount >= 3) {
    state.adminLockoutUntil = Date.now() + 60000;
  }
  try {
    const raw = localStorage.getItem(ADMIN_SEC_QUEUE_KEY);
    const list = raw ? JSON.parse(raw) : [];
    const safeList = Array.isArray(list) ? list : [];
    safeList.push({
      email: String(email || "").trim().slice(0, 120) || "unknown",
      reason: String(reason || "failed_admin_login"),
      count: nextCount,
      at: new Date().toISOString()
    });
    localStorage.setItem(ADMIN_SEC_QUEUE_KEY, JSON.stringify(safeList.slice(-25)));
  } catch {}
  return nextCount;
}

function consumeQueuedWebAdminSecurityEvents() {
  try {
    const raw = localStorage.getItem(ADMIN_SEC_QUEUE_KEY);
    localStorage.removeItem(ADMIN_SEC_QUEUE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

async function pushWebAdminSecurityAlert(payload) {
  if (!isFirebaseWebConfigured()) return;
  try {
    const { db, fbStore, auth } = await getFirebaseHandles();
    const uid = auth.currentUser?.uid || state.user?.uid || "";
    if (!uid) return;
    await fbStore.addDoc(fbStore.collection(db, "adminNotifications"), {
      category: "security_alert",
      severity: String(payload.severity || "warning").slice(0, 16),
      signalType: String(payload.signalType || "suspicious_activity").slice(0, 80),
      titleEn: String(payload.titleEn || "Security Alert").slice(0, 160),
      titleAr: String(payload.titleAr || "تنبيه أمني").slice(0, 160),
      bodyEn: String(payload.bodyEn || "").slice(0, 600),
      bodyAr: String(payload.bodyAr || "").slice(0, 600),
      actorUid: String(payload.actorUid || uid).slice(0, 128),
      actorEmail: String(payload.actorEmail || auth.currentUser?.email || state.user?.email || "").slice(0, 160),
      orgId: String(payload.orgId || "").slice(0, 128),
      sourcePlatform: "web_pwa",
      acknowledged: false,
      createdAt: fbStore.serverTimestamp(),
      updatedAt: fbStore.serverTimestamp()
    });
  } catch {}
}

function isIosDeviceWeb() {
  try {
    const ua = navigator.userAgent || "";
    return /iphone|ipad|ipod/i.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  } catch {
    return false;
  }
}

function changeWebAdminPassword(currentPw, newPw, confPw) {
  if (!isWebAdminSetupCompleted()) {
    return { ok: false, error: t("Complete one-time initial admin setup first.", "يجب إكمال الإعداد الأولي للإدارة أولاً.") };
  }
  if (!verifyWebAdminPassword(currentPw)) {
    return { ok: false, error: t("Current Admin password is incorrect.", "كلمة مرور الإدارة الحالية غير صحيحة.") };
  }
  if (String(newPw || "") !== String(confPw || "")) {
    return { ok: false, error: t("New passwords do not match.", "كلمتا المرور الجديدتان غير متطابقتين.") };
  }
  const strengthErr = validateWebAdminPasswordStrength(newPw);
  if (strengthErr) return { ok: false, error: strengthErr };
  const next = { hash: hashAdminSecret(newPw), setupCompleted: true, updatedAt: new Date().toISOString() };
  try { localStorage.setItem(ADMIN_SEC_KEY, JSON.stringify(next)); } catch {}
  return { ok: true };
}

const APPROVED_ARABIC_ABOUT_US =
  "في ظل متغيّرات اقتصادية متسارعة وضغوط تضخمية حادة تهدد استمرارية المشاريع، يأتي 'دليل رؤيتك المالي' ليكون بوصلتك نحو الاستقرار المالي. نحن لا نكتفي بحساب الأرقام، بل نسهل لك اتخاذ قرارات التسعير الصحيحة بشكل تلقائي ومدروس، لنحول معادلات التكاليف المعقدة إلى أدوات عملية دقيقة تضمن لك الحفاظ على رأس المال، تعظيم الأرباح، والعبور بمشروعك نحو بر الأمان بكل ثقة واحترافية.";

const APPROVED_ENGLISH_ABOUT_US =
  "Amid rapid economic shifts and intense inflationary pressures that challenge business continuity, Financial Vision Guide serves as your compass toward financial stability. We go beyond simple calculations to automate disciplined pricing decisions, turning complex cost equations into practical tools that preserve capital, grow profits, and guide your enterprise safely forward with confidence and professionalism.";

const DEFAULT_COA = [
  { code: "1010", name: "Cash & Bank", nameAr: "النقدية والبنوك", accountType: "asset", subType: "cash", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1110", name: "Accounts Receivable", nameAr: "الذمم المدينة", accountType: "asset", subType: "receivable", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1210", name: "Inventory", nameAr: "المخزون", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1510", name: "Property & Equipment", nameAr: "الأصول الثابتة", accountType: "asset", subType: "fixed_asset", normalBalance: "debit", openingBalance: "0.00" },
  { code: "2010", name: "Accounts Payable", nameAr: "الذمم الدائنة", accountType: "liability", subType: "current_liability", normalBalance: "credit", openingBalance: "0.00" },
  { code: "2510", name: "Long-Term Loans", nameAr: "قروض طويلة الأجل", accountType: "liability", subType: "long_term_liability", normalBalance: "credit", openingBalance: "0.00" },
  { code: "3010", name: "Owner Capital", nameAr: "رأس المال", accountType: "equity", subType: "equity", normalBalance: "credit", openingBalance: "0.00" },
  { code: "4010", name: "Sales Revenue", nameAr: "إيرادات المبيعات", accountType: "revenue", subType: "revenue", normalBalance: "credit", openingBalance: "0.00" },
  { code: "5010", name: "Cost of Goods Sold", nameAr: "تكلفة البضاعة المباعة", accountType: "cogs", subType: "cogs", normalBalance: "debit", openingBalance: "0.00" },
  { code: "6010", name: "Operating & Admin Expenses", nameAr: "مصروفات تشغيلية وإدارية", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" },
  { code: "6210", name: "Inventory Loss & Scrap", nameAr: "خسائر الهدر والتلف", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" },
  { code: "7010", name: "Finance & Interest Expense", nameAr: "تكاليف التمويل والفوائد", accountType: "expense", subType: "interest", normalBalance: "debit", openingBalance: "0.00" }
];

const HELP_TOPICS = {
  health_signal: {
    titleAr: "إشارات السلامة المالية (أخضر / أصفر / أحمر)",
    titleEn: "Red / Yellow / Green Financial Signals",
    bodyAr: "الأخضر يعني توازن الميزانية وربحية آمنة؛ الأصفر ينبه لهوامش ضيقة أو تضخم صاعد أو بيانات غير مكتملة؛ الأحمر ينذر بخسارة أو انتهاء تجربة أو خطر تآكل رأس المال.",
    bodyEn: "Green indicates balanced books and safe margins; Yellow warns of tight buffers, rising inflation, or incomplete data; Red flags losses, expired status, or high erosion risk."
  },
  sectors: {
    titleAr: "القطاعات المفعلة ودليل الحسابات",
    titleEn: "Active Sectors & Chart of Accounts",
    bodyAr: "الضغط على قطاع التصنيع أو الأغذية والمشروبات يعرض حسابات التكلفة المخصصة له ويضيفها إلى دليلك بأرصدة افتتاحية صفرية دون أي بيانات وهمية.",
    bodyEn: "Tapping Manufacturing or Food & Beverage previews sector-specific COA accounts and appends missing entries with zero synthetic balances."
  },
  prices: {
    titleAr: "مراقبة الأسعار التاريخية",
    titleEn: "Historical Price Monitoring",
    bodyAr: "يحفظ كل سجل سعري مع مقارنة بالسعر السابق وحساب نسبة التغير، وينبّه تلقائياً عند وجود قفزة سعرية تبلغ 15% فأكثر.",
    bodyEn: "Stores append-only price records, computes delta vs prior entry, and flags unusual swings >= 15%."
  },
  inflation: {
    titleAr: "التضخم المركب وسلال التكلفة",
    titleEn: "Compounded Inflation & Cost Baskets",
    bodyAr: "التضخم التراكمي يحسب حاصل ضرب معدلات الشهور المركب، بينما تقيس السلة الموزونة تغيّر تكلفة مدخلات مشروعك الفعلية مقارنة بفترة الأساس.",
    bodyEn: "Compounded inflation multiplies monthly factors cumulatively; weighted baskets track real input cost change from base period."
  },
  fx_manual: {
    titleAr: "سعر الصرف اليدوي",
    titleEn: "Manual FX Rate Registry",
    bodyAr: "يُدخل سعر الصرف يدوياً (مسودة أو معتمد أو ملغى) لتفادي الأخطاء الإلكترونية من المصادر الخارجية، مع تنبيه عند مرور أكثر من 7 أيام على آخر تحديث.",
    bodyEn: "Rates are documented manually (Draft / Approved / Cancelled) to avoid electronic feed errors, with stale alerts after 7 days."
  },
  bom: {
    titleAr: "قائمة المواد والتركيبات والهدر",
    titleEn: "BOM Recipes & Scrap Allowance",
    bodyAr: "تحسب تكلفة المادة لكل وحدة منتج مع إضافة نسبة الفاقد المسموح وتقسيمها على حجم التشغيلة الصالح.",
    bodyEn: "Computes unit material cost from batch ingredient usage adjusted by scrap allowance percentage."
  },
  costing_8: {
    titleAr: "أساليب التكلفة الـ 8 ونقطة التعادل",
    titleEn: "8 Management Costing Methods & CVP",
    bodyAr: "قارن التكلفة الفعلية والمعيارية والمستهدفة والأنشطة والأوامر والمراحل والامتصاصية والمتغيرة، مع حساب هامش الربح ونقطة التعادل ومحرك المخاطر.",
    bodyEn: "Compare Actual, Standard, Target, ABC, Job Order, Process, Absorption, and Variable costing alongside margin, break-even units, and risk evaluation."
  },
  ledger_je: {
    titleAr: "القيود المزدوجة والقوائم المالية",
    titleEn: "Double-Entry Ledger & Financial Statements",
    bodyAr: "كل قيد متوازن (المدين = الدائن) يحدّث قائمة الدخل والميزانية العمومية وميزان المراجعة و18 نسبة مالية تلقائياً، وتُصحح الأخطاء عبر قيود العكس.",
    bodyEn: "Balanced journals (Debit = Credit) dynamically update Income Statement, Balance Sheet, Trial Balance, and 18 ratios. Posted entries are corrected only via reversals."
  },
  inventory: {
    titleAr: "الجرد وفجوة رأس المال للاستبدال",
    titleEn: "Inventory Valuation & Replacement Gap",
    bodyAr: "يقارن المخزون بين القيمة الدفترية التاريخية (الوارد أولاً أو المتوسط المرجح) وتكلفة الاستبدال بالسوق اليوم، ويراقب تشغيلات الصلاحية خلال 30 يوماً.",
    bodyEn: "Compares historical book value (FIFO or Moving Average) against current market replacement cost and flags batches expiring within 30 days."
  },
  ai_ocr: {
    titleAr: "المساعد المالي وقراءة الفواتير بالكاميرا",
    titleEn: "Financial Assistant & Camera Invoice OCR",
    bodyAr: "يحلل المساعد المالي أرقامك الفعلية عبر محادثة تفاعلية، بينما تلتقط الكاميرا الفاتورة وتتحقق من تطابق الإجمالي والخصم والضريبة قبل الاعتماد.",
    bodyEn: "Multi-turn assistant reasons over your verified workspace figures; Camera OCR captures invoices and validates subtotal - discount + tax = total before approval."
  },
  billing: {
    titleAr: "الاشتراكات والتحقق من الدفع",
    titleEn: "Tiered Plans & Payment Verification",
    bodyAr: "اختر الباقة المناسبة لقدرات عملك وأرفق مرجع التحويل وآخر 4 أرقام للمعاملة مع اسمك وملاحظاتك ليصل مباشرة إلى لوحة المالكة زهراء يوسف للمراجعة.",
    bodyEn: "Choose the plan matching your feature needs and submit transfer reference plus optional last 4 digits and user note directly to Owner Zahra Yousif for verification."
  },
  what_if: {
    titleAr: "محاكاة ماذا لو للحساسية والقرارات",
    titleEn: "What-If Decision Simulator",
    bodyAr: "أداة محاكاة افتراضية غير مدمّرة لا تؤثر على بياناتك الأصلية. تتيح لك الإجابة المسبقة عن أثر صدمات سعر الصرف (+30%)، وركود المبيعات (-25%)، وقفزات أسعار المواد الخام (+35%) على أرباحك قبل اتخاذ القرارات على أرض الواقع.",
    bodyEn: "A safe non-destructive sandbox. Stress-test currency devaluations (+30%), sales recessions (-25%), or material spikes (+35%) to see the bottom-line effect before acting in real life."
  }
};

function getOrCreateSessionId() {
  try {
    let sid = localStorage.getItem(SESSION_KEY);
    if (!sid) {
      sid = "sess_" + Math.random().toString(36).slice(2, 10) + "_" + Date.now();
      localStorage.setItem(SESSION_KEY, sid);
    }
    return sid;
  } catch {
    return "sess_browser";
  }
}

function getOrCreateVisitorId() {
  try {
    let vid = localStorage.getItem(VISITOR_KEY);
    if (!vid) {
      vid = "vis_" + Math.random().toString(36).slice(2, 10) + "_" + Date.now();
      localStorage.setItem(VISITOR_KEY, vid);
    }
    return vid;
  } catch {
    return "vis_browser";
  }
}

function loadSavedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY) || localStorage.getItem("fvg_web_workspace_v3");
    if (raw) return JSON.parse(raw);
  } catch {}
  return null;
}

const saved = loadSavedState();

const state = {
  stage: saved?.user && saved?.activeOrg ? "app" : "splash",
  secondaryView: null, // null | "profile" | "help" | "owner_admin"
  adminPreviewUserApp: false,
  ownerTab: 0,
  ownerSearch: "",
  ownerOrgSearch: "",
  ownerOrgStatusFilter: "all",
  ownerTicketFilter: "all",
  ownerAuditDialogOrgName: "",
  ownerAuditLogs: [],
  ownerAuditLoading: false,
  ownerChartStyle: "bar", // "bar" | "line"
  ownerTrafficChartStyle: "donut", // "donut" | "bar"
  showOwnerAlertsModal: false,
  ownerNotifFilter: "all",
  adminNotifications: [],
  adminFailCount: 0,
  adminLockoutUntil: 0,
  deferredInstallPrompt: null,
  showIosInstallGuide: false,
  showCalculator: false,
  showAiDrawer: false,
  showWelcomePopup: false,
  showAdminModal: false,
  adminModalEmailDraft: "",
  adminModalPassVisible: false,
  adminModalConfVisible: false,
  secCurrPassVisible: false,
  secNewPassVisible: false,
  secConfPassVisible: false,
  authChannel: "email", // "email" | "phone"
  authRegisterMode: true,
  authPassVisible: false,
  phoneOtpStep: "input", // "input" | "code"
  phonePendingConfirmation: null,
  phonePendingE164: "",
  phonePendingOptionalEmail: "",
  phonePendingName: "",
  phonePendingOrg: "",
  aiDrawerMode: "general",
  preLoginAiQuestion: "",
  preLoginAiAnswer: "",
  isAiLoading: false,
  aiAbortRequested: false,
  showTour: false,
  tourStep: 0,
  activeHelpKey: null,
  activeSectorPreviewKey: null,
  sectorCalcModel: { batchMat: 1000, batchOvh: 250, batchScrapPct: 5, batchUnits: 100, foodLossPct: 8, foodPack: 120 },
  ownerCreatePlanMode: false,
  ownerFormulaChecks: [],
  ownerSandboxMat: 500,
  ownerSandboxFix: 250,
  ownerSandboxUnits: 100,
  ownerSandboxPrice: 16,
  dismissedWelcomeFor: saved?.dismissedWelcomeFor ?? [],
  isArabic: saved?.isArabic ?? true,
  themeMode: saved?.themeMode === "dark" ? "dark" : "light", // Strictly 2-state Light/Dark parity
  fontScale: saved?.fontScale ?? 1.0,
  publicPage: 0,
  appTab: 0,
  subTab: { prices: 0, costing: 0, ledger: 0, stock: 0, reports: 1 },
  user: saved?.user ?? null,
  registeredUsers: saved?.registeredUsers ?? [],
  organizations: saved?.organizations ?? (saved?.activeOrg ? [saved.activeOrg] : []),
  activeOrg: saved?.activeOrg ?? null,
  sessions: saved?.sessions ?? [{ id: getOrCreateSessionId(), label: navigator.userAgent.slice(0, 48), platform: "PWA / Web", lastActive: new Date().toISOString().slice(0, 16), revoked: false }],
  products: saved?.products ?? [],
  priceObservations: saved?.priceObservations ?? [],
  inflationRecords: saved?.inflationRecords ?? [],
  baskets: saved?.baskets ?? [],
  fxRates: saved?.fxRates ?? [],
  costSnapshots: saved?.costSnapshots ?? [],
  bomFormulas: saved?.bomFormulas ?? [],
  scenarios: saved?.scenarios ?? [],
  accounts: saved?.accounts ?? DEFAULT_COA,
  journalEntries: saved?.journalEntries ?? [],
  counterpartyItems: saved?.counterpartyItems ?? [],
  inventoryItems: saved?.inventoryItems ?? [],
  stocktakes: saved?.stocktakes ?? [],
  scrapLosses: saved?.scrapLosses ?? [],
  budgets: saved?.budgets ?? [],
  paymentRequests: saved?.paymentRequests ?? [],
  ocrDrafts: saved?.ocrDrafts ?? [],
  pendingOcrDataUrl: null,
  auditLogs: saved?.auditLogs ?? [],
  aiConversations: saved?.aiConversations ?? [],
  activeConversationId: saved?.activeConversationId ?? null,
  aiMode: "general",
  aiResponse: "",
  aiSearchQueries: [],
  aiGroundingSources: [],
  preLoginAiQueries: [],
  preLoginAiSources: [],
  isOnline: typeof navigator !== "undefined" ? navigator.onLine !== false : true,
  offlineQueue: saved?.offlineQueue ?? [],
  offlineConflicts: saved?.offlineConflicts ?? [],
  cashRunwayResult: null,
  runwayParams: { startingCash: 100000, weeklyInflow: 25000, weeklyOutflow: 30000, openAr: 0, fxShockPct: 0, salesDropPct: 20, arDelayWeeks: 1 },
  branchConsolidation: null,
  paySmsText: "",
  receiptMatchPreview: null,
  chatTranscript: saved?.chatTranscript ?? [],
  platformPlans: saved?.platformPlans?.length ? saved.platformPlans : Engine.DEFAULT_PLANS,
  announcements: saved?.announcements ?? [],
  supportTickets: saved?.supportTickets ?? [],
  mailOutbox: saved?.mailOutbox ?? [],
  platformVisits: saved?.platformVisits ?? [],
  sessionVisitLogged: false,
  costDraftMethod: "actual",
  selectedReport: "statements",
  toast: null,
  calc: { expr: "", display: "0", memory: 0, currencyFmt: true, history: [] }
};

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      isArabic: state.isArabic,
      themeMode: state.themeMode,
      fontScale: state.fontScale,
      dismissedWelcomeFor: state.dismissedWelcomeFor,
      user: state.user,
      registeredUsers: state.registeredUsers,
      organizations: state.organizations,
      activeOrg: state.activeOrg,
      sessions: state.sessions,
      products: state.products,
      priceObservations: state.priceObservations,
      inflationRecords: state.inflationRecords,
      baskets: state.baskets,
      fxRates: state.fxRates,
      costSnapshots: state.costSnapshots,
      bomFormulas: state.bomFormulas,
      scenarios: state.scenarios,
      accounts: state.accounts,
      journalEntries: state.journalEntries,
      counterpartyItems: state.counterpartyItems,
      inventoryItems: state.inventoryItems,
      stocktakes: state.stocktakes,
      scrapLosses: state.scrapLosses,
      budgets: state.budgets,
      paymentRequests: state.paymentRequests,
      ocrDrafts: state.ocrDrafts,
      auditLogs: state.auditLogs,
      offlineQueue: state.offlineQueue,
      offlineConflicts: state.offlineConflicts,
      aiConversations: state.aiConversations,
      activeConversationId: state.activeConversationId,
      chatTranscript: state.chatTranscript,
      platformPlans: state.platformPlans,
      announcements: state.announcements,
      supportTickets: state.supportTickets,
      mailOutbox: state.mailOutbox,
      platformVisits: state.platformVisits
    }));
  } catch {}
}

function isPlatformOwner() {
  return String(state.user?.email || "").trim().toLowerCase() === Engine.OWNER_EMAIL.toLowerCase();
}

function getEntitlements() {
  return Engine.resolvePlanEntitlements(
    state.activeOrg?.planTier || "trial",
    state.activeOrg?.subscriptionStatus || "trial",
    state.platformPlans
  );
}

function esc(str) {
  return String(str ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function t(en, ar) {
  return state.isArabic ? ar : en;
}

function localizeCurrency(code) {
  const c = String(code || "SDG").toUpperCase();
  if (!state.isArabic) return c;
  if (c === "SDG") return "ج.س";
  if (c === "USD") return "دولار";
  if (c === "SAR") return "ر.س";
  if (c === "AED") return "د.إ";
  if (c === "EGP") return "ج.م";
  if (c === "EUR") return "يورو";
  return c;
}

function localizeStatus(st) {
  if (!state.isArabic) return st;
  const m = {
    draft: "مسودة",
    approved: "معتمد",
    cancelled: "ملغى",
    posted: "مرحّل",
    reversed: "معكوس",
    pending_verification: "قيد التحقق",
    rejected: "مرفوض",
    active: "نشط",
    trial: "تجربة",
    expired: "منتهي",
    pending_review: "قيد المراجعة",
    verified: "معتمد",
    open: "مفتوح",
    in_review: "قيد المراجعة",
    reviewed: "تمت المراجعة",
    replied: "تم الرد",
    resolved: "محلول",
    official: "رسمي",
    calculated: "محسوب",
    manual: "يدوي",
    provisional: "تقديري",
    queued: "في طابور الإرسال"
  };
  return m[String(st).toLowerCase()] || st;
}

function localizeCostMethod(m) {
  if (!state.isArabic) return m.replace("_", " ").toUpperCase();
  const map = {
    actual: "التكلفة الفعلية",
    standard: "التكلفة المعيارية",
    target: "التكلفة المستهدفة",
    abc: "التكلفة على أساس الأنشطة",
    job_order: "تكلفة الأوامر الإنتاجية",
    process: "تكلفة المراحل",
    absorption: "التكلفة الكلية الامتصاصية",
    variable: "التكلفة المتغيرة"
  };
  return map[m] || m;
}

function helpBtn(key) {
  return `<button type="button" class="fvg-help-btn" data-help="${esc(key)}" title="${t("Explain this section", "توضيح هذا القسم")}">!</button>`;
}

function renderStatusSignalBanner(level, title, subtitle) {
  const icon = level === "red" ? "⛔" : level === "yellow" ? "⚠" : "✅";
  return `
    <div class="fvg-signal-banner ${level}">
      <div class="fvg-signal-icon">${icon}</div>
      <div style="flex:1;min-width:0;">
        <div class="fvg-signal-title">${esc(title)}</div>
        <div class="fvg-signal-sub">${esc(subtitle)}</div>
      </div>
    </div>
  `;
}

function renderFounderCard(compact = false) {
  const f = Engine.FOUNDER_INFO;
  return `
    <div class="fvg-founder-card">
      <div class="fvg-founder-row">
        <img class="fvg-founder-img" src="${f.portraitPath}" alt="${esc(state.isArabic ? f.nameAr : f.nameEn)}" />
        <div style="flex:1;min-width:190px;">
          <div style="font-size:1.08rem;font-weight:800;color:#DDB065;">${esc(state.isArabic ? f.nameAr : f.nameEn)}</div>
          <div style="font-size:0.84rem;color:#FAF2E8;opacity:0.95;margin-top:2px;">${esc(state.isArabic ? f.roleAr : f.roleEn)}</div>
          <div style="font-size:0.78rem;color:#DDB065;margin-top:4px;">📞 ${esc(f.phone)} • ✉ ${esc(f.email)}</div>
        </div>
      </div>
      ${!compact ? `
        <div class="fvg-founder-quote">${esc(state.isArabic ? APPROVED_ARABIC_ABOUT_US : APPROVED_ENGLISH_ABOUT_US)}</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          <a class="btn btn-gold" style="min-height:40px;padding:6px 14px;" href="tel:${esc(f.phone)}">📞 ${t("Call", "اتصال هاتفي")}</a>
          <a class="btn btn-outline" style="min-height:40px;padding:6px 14px;color:#FAF2E8;border-color:#DDB065;" href="mailto:${esc(f.email)}">✉ ${t("Email", "مراسلة بالبريد")}</a>
        </div>
      ` : ""}
    </div>
  `;
}

function logAudit(action, entityType, summary) {
  state.auditLogs.unshift({
    id: "aud_" + Date.now(),
    action,
    entityType,
    summary,
    actorEmail: state.user?.email || "user@local",
    at: new Date().toISOString().slice(0, 19)
  });
}

function showToast(msg) {
  state.toast = msg;
  render();
  setTimeout(() => {
    if (state.toast === msg) {
      state.toast = null;
      render();
    }
  }, 3600);
}

function effectiveTrialDurationHours() {
  return state.activeOrg?.trialDurationHours || state.user?.trialDurationHours || Engine.TRIAL_DURATION_HOURS;
}

function effectiveTrialEndsMillis() {
  return Engine.computeServerSyncedTrialEndMillis(
    state.user?.trialStartedAtMillis,
    state.activeOrg?.trialEndsAtMillis,
    effectiveTrialDurationHours()
  );
}

function isTrialLocked() {
  const org = state.activeOrg;
  if (!org) return false;
  if (org.subscriptionStatus === "active") return false;
  if (org.subscriptionStatus === "expired" || org.subscriptionStatus === "cancelled") return true;
  const ends = effectiveTrialEndsMillis();
  return ends ? Date.now() >= ends : false;
}

function upsertRegisteredUser(email, displayName, orgName) {
  const cleanEmail = String(email || "").trim().toLowerCase();
  if (!cleanEmail) return;
  const idx = state.registeredUsers.findIndex((u) => String(u.email || "").toLowerCase() === cleanEmail);
  const rec = {
    uid: state.user?.uid || "usr_" + cleanEmail.replace(/[^a-z0-9]/g, "_"),
    email: email,
    displayName: displayName || email.split("@")[0],
    orgName: orgName || state.activeOrg?.name || "",
    preferredLanguage: state.isArabic ? "ar" : "en",
    lastSeenAt: new Date().toISOString().slice(0, 16)
  };
  if (idx >= 0) state.registeredUsers[idx] = { ...state.registeredUsers[idx], ...rec };
  else state.registeredUsers.unshift(rec);
}

function ensureWelcomeMailQueued(email, displayName) {
  const cleanEmail = String(email || "").trim();
  if (!cleanEmail || !cleanEmail.includes("@")) return;
  const uidKey = state.user?.uid || cleanEmail.toLowerCase().replace(/[^a-z0-9]/g, "_");
  const mailId = "welcome_" + uidKey;
  if (state.mailOutbox.some((m) => m.id === mailId)) return;

  const subject = state.isArabic
    ? "مرحباً بك في دليل رؤيتك المالي"
    : "Welcome to Financial Vision Guide (FVG)";
  const body = state.isArabic
    ? `مرحباً ${displayName || "بك"}،\n\nيسعدنا انضمامك إلى منصة دليل رؤيتك المالي. تم تفعيل فترتك التجريبية لمدة 7 أيام (168 ساعة) لتجربة كافة أدوات التكاليف والتسعير والمحاسبة والمخزون والمساعد المالي الذكي.\n\nلأي دعم فني أو استفسار يمكنك مراسلتنا عبر البريد الرسمي أو الهاتف: 00249911569966.\n\nمع أطيب التمنيات بالنجاح،\nالمالكة زهراء يوسف`
    : `Hello ${displayName || "there"},\n\nWelcome to Financial Vision Guide (FVG). Your full 7-day (168-hour) trial is now active across Costing, Pricing, Accounting, Inventory, and the Financial Assistant.\n\nFor support contact us anytime on 00249911569966.\n\nWarm regards,\nOwner Zahra Yousif`;

  const entry = {
    id: mailId,
    from: Engine.OWNER_EMAIL,
    to: [cleanEmail],
    createdBy: state.user?.uid || uidKey,
    mailType: "welcome",
    status: "queued",
    message: { subject, text: body },
    createdAt: new Date().toISOString().slice(0, 19)
  };
  state.mailOutbox.unshift(entry);
  if (isFirebaseWebConfigured() && state.user?.uid) {
    queueMailInFirestore(entry).catch(() => {});
  }
}

function recordPlatformVisitOnce(screen = "dashboard") {
  if (state.sessionVisitLogged) return;
  state.sessionVisitLogged = true;
  const visit = {
    id: "vis_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    platform: "web_pwa",
    screen,
    visitorKey: getOrCreateVisitorId(),
    userId: state.user?.uid || "",
    createdAt: new Date().toISOString().slice(0, 19)
  };
  state.platformVisits.unshift(visit);
  if (isFirebaseWebConfigured()) {
    logVisitInFirestore(visit).catch(() => {});
  }
}

function renderTrialBanner(preview = false) {
  const hours = preview ? Engine.TRIAL_DURATION_HOURS : effectiveTrialDurationHours();
  const daysNum = Math.max(1, Math.round(hours / 24));
  const target = preview ? Date.now() + Engine.TRIAL_DURATION_MILLIS : effectiveTrialEndsMillis();
  if (!target) return "";
  const cd = Engine.computeTrialCountdown(target, Date.now());
  const pad = (v) => String(v).padStart(2, "0");
  return `
    <div class="card cream" data-trial-banner="${preview ? "preview" : target}">
      <div class="row-between">
        <strong>${cd.isExpired ? t(`${daysNum}-Day (${hours}h) Trial Ended`, `انتهت الفترة التجريبية (${daysNum} أيام / ${hours} ساعة)`) : (preview ? t(`Full ${daysNum}-Day (${hours}-Hour) Free Trial`, `فترة تجربة مجانية لمدة ${daysNum} أيام (${hours} ساعة)`) : t(`${daysNum}-Day (${hours}h) Active Trial`, `الفترة التجريبية النشطة (${daysNum} أيام)`))}</strong>
        <div style="display:flex;align-items:center;gap:6px;">
          <span class="badge ${cd.isExpired ? "high" : "low"}">${cd.isExpired ? t("EXPIRED", "منتهية") : `${hours} ${t("HRS", "ساعة")}`}</span>
          ${helpBtn("billing")}
        </div>
      </div>
      <div class="grid-4" style="margin-top:8px;">
        <div class="timer-box"><strong data-cd="d">${pad(cd.days)}</strong><span>${t("Days", "أيام")}</span></div>
        <div class="timer-box"><strong data-cd="h">${pad(cd.hours)}</strong><span>${t("Hours", "ساعات")}</span></div>
        <div class="timer-box"><strong data-cd="m">${pad(cd.minutes)}</strong><span>${t("Mins", "دقائق")}</span></div>
        <div class="timer-box"><strong data-cd="s">${pad(cd.seconds)}</strong><span>${t("Secs", "ثواني")}</span></div>
      </div>
    </div>
  `;
}

function renderSplash() {
  return `
    <div class="fvg-splash" id="splashRoot">
      <svg class="fvg-splash-rays" viewBox="0 0 400 800" preserveAspectRatio="none">
        <polygon points="224,0 320,0 136,352 64,352" fill="#517D77" fill-opacity="0.08"/>
        <polygon points="336,0 400,0 400,112 208,400 152,400" fill="#517D77" fill-opacity="0.06"/>
        <line x1="300" y1="92" x2="300" y2="160" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="295" y="106" width="10" height="42" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="324" y1="74" x2="324" y2="148" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="319" y="88" width="10" height="48" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="348" y1="54" x2="348" y2="132" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="343" y="68" width="10" height="54" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="372" y1="34" x2="372" y2="118" stroke="#DDB065" stroke-opacity="0.24" stroke-width="3"/>
        <rect x="367" y="48" width="10" height="58" rx="3" fill="#DDB065" fill-opacity="0.24"/>
      </svg>
      <div class="fvg-splash-center">
        <img class="fvg-splash-logo" src="./assets/fvg-icon.svg" alt="شعار المنصة"/>
        <h1 class="fvg-splash-title">${t("FVG", "دليل رؤيتك المالي")}</h1>
        <div class="fvg-splash-sub-en">${t("Financial Vision Guide", "منصة المحاسبة الإدارية والتسعير")}</div>
        <div class="fvg-splash-sub-ar">
          <span class="fvg-gold-line"></span>
          <span>${t("دليل رؤيتك المالي", "الإدارة المالية والتكاليف")}</span>
          <span class="fvg-gold-line"></span>
        </div>
        <p class="fvg-splash-tagline">${t("Smarter Decisions.<br>Stronger Financial Future.", "قرارات أذكى.<br>مستقبل مالي أقوى.")}</p>
      </div>
      <svg class="fvg-splash-waves" viewBox="0 0 400 200" preserveAspectRatio="none">
        <path d="M0,45 C110,0 270,95 400,25 L400,200 L0,200 Z" fill="#15423A"/>
        <path d="M0,78 C120,32 280,125 400,60 L400,200 L0,200 Z" fill="#DDB065"/>
        <path d="M0,96 C120,50 280,142 400,78 L400,200 L0,200 Z" fill="#022A27"/>
      </svg>
      <div class="fvg-splash-footer">${t("Tap anywhere to continue", "انقر في أي مكان للمتابعة")}</div>
    </div>
  `;
}

function formatAiMarkdownHtml(raw) {
  const lines = String(raw || "").split(/\r?\n/);
  let html = "";
  let inList = false;
  let tableRows = [];

  const flushList = () => {
    if (inList) { html += "</ul>"; inList = false; }
  };
  const inlineFmt = (txt) => esc(txt)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");

  const flushTable = () => {
    if (!tableRows.length) return;
    const rows = tableRows.filter((r) => !/^\|?[\s:-]+(\|[\s:-]+)+\|?$/.test(r.trim()));
    if (rows.length) {
      html += `<div class="table-wrap"><table>`;
      rows.forEach((r, idx) => {
        const cells = r.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim());
        const tag = idx === 0 ? "th" : "td";
        if (idx === 0) html += "<thead>";
        if (idx === 1) html += "<tbody>";
        html += `<tr>${cells.map((c) => `<${tag}>${inlineFmt(c)}</${tag}>`).join("")}</tr>`;
        if (idx === 0) html += "</thead>";
      });
      if (rows.length > 1) html += "</tbody>";
      html += `</table></div>`;
    }
    tableRows = [];
  };

  lines.forEach((line) => {
    const trimmed = line.trim();
    if (trimmed.startsWith("|") && trimmed.includes("|", 1)) {
      flushList();
      tableRows.push(trimmed);
      return;
    }
    flushTable();
    if (!trimmed) {
      flushList();
      html += `<div style="height:6px;"></div>`;
      return;
    }
    if (/^#{1,4}\s+/.test(trimmed)) {
      flushList();
      html += `<h4>${inlineFmt(trimmed.replace(/^#{1,4}\s+/, ""))}</h4>`;
      return;
    }
    if (/^([•\-\*]|\d+\.)\s+/.test(trimmed)) {
      if (!inList) { html += "<ul>"; inList = true; }
      html += `<li>${inlineFmt(trimmed.replace(/^([•\-\*]|\d+\.)\s+/, ""))}</li>`;
      return;
    }
    flushList();
    html += `<div>${inlineFmt(trimmed)}</div>`;
  });
  flushList();
  flushTable();
  return `<div class="fvg-ai-msg-rich">${html}</div>`;
}

function renderSvgBarOrLineChart(points, style = "bar", primary = "#15423A", accent = "#DDB065") {
  const safePoints = points && points.length ? points : [{ label: "-", value: 0 }];
  const w = 540, h = 200, padL = 34, padR = 16, padT = 20, padB = 34;
  const chartW = w - padL - padR;
  const chartH = h - padT - padB;
  const maxVal = Math.max(1, ...safePoints.map((p) => Number(p.value || 0)));
  const stepX = chartW / safePoints.length;

  const gridLines = [0, 0.5, 1].map((r) => {
    const y = padT + chartH * (1 - r);
    const lbl = Math.round(maxVal * r);
    return `<line x1="${padL}" y1="${y}" x2="${w - padR}" y2="${y}" stroke="currentColor" stroke-opacity="0.12" stroke-dasharray="3 3"/><text x="${padL - 6}" y="${y + 4}" text-anchor="end" font-size="10" fill="currentColor" fill-opacity="0.65">${lbl}</text>`;
  }).join("");

  let series = "";
  if (style === "line") {
    const coords = safePoints.map((p, i) => {
      const x = padL + stepX * i + stepX / 2;
      const y = padT + chartH - (Number(p.value || 0) / maxVal) * chartH;
      return { x, y, val: p.value, label: p.label };
    });
    const pathD = coords.map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
    const areaD = `${pathD} L${coords[coords.length - 1].x.toFixed(1)},${padT + chartH} L${coords[0].x.toFixed(1)},${padT + chartH} Z`;
    series = `
      <path d="${areaD}" fill="${accent}" fill-opacity="0.24"/>
      <path d="${pathD}" fill="none" stroke="${primary}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      ${coords.map((c) => `
        <circle cx="${c.x}" cy="${c.y}" r="5" fill="${accent}" stroke="${primary}" stroke-width="2"/>
        <text x="${c.x}" y="${c.y - 9}" text-anchor="middle" font-size="10.5" font-weight="700" fill="currentColor">${c.val}</text>
      `).join("")}
    `;
  } else {
    const barW = Math.min(38, stepX * 0.56);
    series = safePoints.map((p, i) => {
      const val = Number(p.value || 0);
      const bh = Math.max(4, (val / maxVal) * chartH);
      const x = padL + stepX * i + (stepX - barW) / 2;
      const y = padT + chartH - bh;
      const fill = i % 2 === 0 ? primary : accent;
      return `
        <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barW.toFixed(1)}" height="${bh.toFixed(1)}" rx="6" fill="${fill}"/>
        <text x="${(x + barW / 2).toFixed(1)}" y="${(y - 5).toFixed(1)}" text-anchor="middle" font-size="10.5" font-weight="700" fill="currentColor">${val}</text>
      `;
    }).join("");
  }

  const labels = safePoints.map((p, i) => {
    const x = padL + stepX * i + stepX / 2;
    return `<text x="${x.toFixed(1)}" y="${h - 10}" text-anchor="middle" font-size="10" fill="currentColor" fill-opacity="0.82">${esc(String(p.label).slice(0, 12))}</text>`;
  }).join("");

  return `<svg class="fvg-svg-chart" viewBox="0 0 ${w} ${h}" role="img" aria-label="Chart">${gridLines}${series}${labels}</svg>`;
}

function renderSvgDonutChart(slices, centerLabel = "") {
  const valid = slices.filter((s) => Number(s.value || 0) > 0);
  const total = valid.reduce((a, s) => a + Number(s.value || 0), 0);
  const r = 56, c = 2 * Math.PI * r;
  let offset = 0;
  const rings = total > 0
    ? valid.map((s) => {
        const len = (Number(s.value) / total) * c;
        const el = `<circle cx="76" cy="76" r="${r}" fill="none" stroke="${s.color}" stroke-width="20" stroke-dasharray="${len.toFixed(2)} ${c.toFixed(2)}" stroke-dashoffset="${(-offset).toFixed(2)}" transform="rotate(-90 76 76)"/>`;
        offset += len;
        return el;
      }).join("")
    : `<circle cx="76" cy="76" r="${r}" fill="none" stroke="#DCD7C8" stroke-width="20"/>`;

  return `
    <div class="fvg-donut-row">
      <svg width="152" height="152" viewBox="0 0 152 152" style="flex-shrink:0;">
        ${rings}
        <text x="76" y="73" text-anchor="middle" font-size="20" font-weight="800" fill="currentColor">${total}</text>
        <text x="76" y="92" text-anchor="middle" font-size="10" fill="currentColor" fill-opacity="0.72">${esc(centerLabel)}</text>
      </svg>
      <div class="fvg-donut-legend">
        ${slices.map((s) => `
          <div class="fvg-legend-item">
            <span style="display:inline-flex;align-items:center;gap:7px;"><span class="fvg-legend-dot" style="background:${s.color};"></span>${esc(s.label)}</span>
            <strong>${Number(s.value || 0)}</strong>
          </div>
        `).join("")}
      </div>
    </div>
  `;
}

function renderBanner(title, subtitle, authenticated = false) {
  const nextThemeLabel = state.themeMode === "dark" ? "☀" : "☾";
  const showBackHome = authenticated && (state.secondaryView !== null || state.appTab !== 0);
  const avatarHtml = authenticated
    ? (state.user?.avatarDataUrl
        ? `<img src="${state.user.avatarDataUrl}" alt="الصورة الشخصية" style="width:36px;height:36px;border-radius:50%;object-fit:cover;border:2px solid #DDB065;cursor:pointer;" data-action="openProfile"/>`
        : `<button class="icon-pill" data-action="openProfile" title="${t("Profile", "ملفي الشخصي")}">👤</button>`)
    : "";
  return `
    <div class="fvg-banner">
      <div style="display:flex;align-items:center;gap:10px;min-width:0;flex:1;">
        ${avatarHtml}
        <div style="min-width:0;">
          <div style="font-size:1.05rem;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(title)}</div>
          <div style="font-size:0.75rem;color:#DDB065;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(subtitle)}</div>
        </div>
      </div>
      <div class="fvg-banner-actions">
        ${showBackHome ? `<button class="icon-pill" data-action="goHome" title="${t("Back to Home", "العودة للرئيسية")}">🏠 ${t("Home", "الرئيسية")}</button>` : ""}
        ${authenticated ? `<button class="icon-pill" data-action="openCalc" title="${t("Calculator", "الآلة الحاسبة")}">🧮</button>` : ""}
        ${authenticated ? `<button class="icon-pill" data-action="openHelp" title="${t("Help", "المساعدة")}">؟</button>` : ""}
        <button class="icon-pill" data-action="toggleLang">${state.isArabic ? "إنجليزي" : "عربي"}</button>
        <button class="icon-pill" data-action="cycleTheme" title="${t("Theme: " + state.themeMode, "المظهر: " + localizeStatus(state.themeMode))}">${nextThemeLabel}</button>
      </div>
    </div>
  `;
}

function renderSectorsCards() {
  const activeS = Engine.FVG_SECTORS.filter((s) => s.key === "manufacturing" || s.key === "food_beverage");
  const upcomingS = Engine.FVG_SECTORS.filter((s) => s.key !== "manufacturing" && s.key !== "food_beverage");

  const activeHtml = activeS.map((s) => `
    <div class="card clickable cream" style="border:1.5px solid var(--fvg-warm-gold);margin-bottom:8px;" data-sector-preview="${esc(s.key)}">
      <div class="row-between">
        <strong>${s.key === "manufacturing" ? "🏭 " : "🍽️ "}${esc(state.isArabic ? s.nameAr : s.nameEn)}</strong>
        <span class="badge low">${t("Active ✓", "مفعّل ✓")}</span>
      </div>
      <div style="font-size:0.8rem;color:var(--fvg-text-muted);margin:6px 0;">
        ${s.key === "manufacturing"
          ? t("Production run costing, direct materials, labor, factory overhead (FOH) absorption, and 25% margin pricing.", "حاسبة تكاليف خطوط الإنتاج، المواد المباشرة، الأجور الصناعية، تكاليف المصنع الإضافية (FOH)، وحساب سعر البيع بهامش 25%.")
          : t("Recipe costing, fresh ingredient yield, packaging, spoilage absorption, and FEFO expiry risk.", "حاسبة تكلفة الوصفات والمكونات الطازجة، التغليف، امتصاص الهدر والتلف، وسياسة الصلاحية (FEFO).")}
      </div>
      <button class="btn btn-outline" style="width:100%;margin-top:4px;" data-sector-preview="${esc(s.key)}">🧮 ${t("Open Dedicated Calculator & Starter COA", "فتح الحاسبة المخصصة ودليل الحسابات")}</button>
    </div>
  `).join("");

  const upcomingHtml = `
    <div style="grid-column: 1 / -1; margin-top: 10px;">
      <div style="font-weight:700;font-size:0.85rem;color:var(--fvg-text-muted);margin-bottom:6px;">
        ${t("Upcoming Sectors Roadmap (In Development Pipeline):", "خارطة القطاعات القادمة قريباً (خطوة مستقبلية):")}
      </div>
      <div class="grid-2">
        ${upcomingS.map((s) => `
          <div class="card" style="opacity:0.85;padding:10px 14px;">
            <div class="row-between">
              <span style="font-size:0.85rem;">${esc(state.isArabic ? s.nameAr : s.nameEn)}</span>
              <span class="badge">${t("Coming Soon", "قريباً")}</span>
            </div>
          </div>
        `).join("")}
      </div>
    </div>
  `;

  return activeHtml + upcomingHtml;
}

function renderPlansCards() {
  return state.platformPlans.map((p) => {
    const feats = state.isArabic
      ? String(p.featuresAr || p.notesAr || "").split("|").filter(Boolean)
      : String(p.featuresEn || p.notesEn || "").split("|").filter(Boolean);
    return `
      <div class="card cream">
        <div class="row-between">
          <strong>${esc(state.isArabic ? p.nameAr : p.nameEn)}</strong>
          <span class="badge">${esc(p.priceSdg)} ${localizeCurrency("SDG")} / $${esc(p.priceUsd)}</span>
        </div>
        <div style="font-size:0.82rem;color:var(--fvg-text-muted);margin-top:4px;">${esc(state.isArabic ? p.notesAr : p.notesEn)}</div>
        <div style="margin-top:6px;">
          ${feats.map((f) => `<div style="font-size:0.8rem;">• ${esc(f)}</div>`).join("")}
        </div>
      </div>
    `;
  }).join("");
}

function renderPublicSite() {
  const pages = [
    t("Home", "الرئيسية"),
    t("Features & Sectors", "المزايا والقطاعات"),
    t("Plans & Pay", "الباقات والدفع"),
    t("About Platform", "عن المنصة"),
    t("9-Step Guide", "دليل 9 خطوات"),
    t("Contact Us", "تواصل معنا"),
    t("Privacy", "الخصوصية"),
    t("Terms", "الشروط")
  ];
  let body = "";
  if (state.publicPage === 0) {
    body = `
      ${renderFounderCard(false)}
      <div class="card hero">
        <div style="display:flex;align-items:center;gap:12px;">
          <img src="./assets/fvg-icon.svg" style="width:52px;height:52px;border-radius:12px;background:#FAF2E8;padding:4px;" alt="شعار المنصة"/>
          <div>
            <h2 style="margin:0;">${t("FVG — Management Accounting & Pricing", "منصة دليل رؤيتك المالي للمحاسبة الإدارية والتسعير")}</h2>
            <div style="color:#DDB065;font-size:0.82rem;">${t("Installable on iPhone (Safari -> Add to Home Screen), Tablet, Android & Desktop", "تثبيت مباشر على أيفون (مشاركة ← إضافة للشاشة الرئيسية) واللوحي والكمبيوتر وأندرويد")}</div>
          </div>
        </div>
        <p style="font-size:0.9rem;line-height:1.65;">${t("8 Costing Methods, Financial Risk Engine, Price & Inflation Monitoring, Manual FX Registry, Double-Entry Ledger, 18 Financial Ratios, FIFO/MWA Stock, Camera OCR & Financial Assistant.", "8 أساليب للتكلفة والتركيبات، محرك المخاطر المالية بالإشارات الواضحة، مراقبة الأسعار والتضخم، الصرف اليدوي، القيود والقوائم والـ 18 نسبة، المخزون والتلف، مسح الفواتير بالكاميرا، والمساعد المالي الذكي.")}</p>

        <div style="font-size:0.82rem;font-weight:700;color:#DDB065;margin-bottom:6px;">
          ${t("Choose a Sign-In Method:", "اختر طريقة تسجيل الدخول أو إنشاء الحساب:")}
        </div>
        <div class="fvg-auth-providers">
          <button type="button" class="fvg-provider-btn google" data-action="launchGoogle">
            <span>G</span><span>${t("Google", "قوقل")}</span>
          </button>
          <button type="button" class="fvg-provider-btn apple" data-action="launchApple">
            <span></span><span>${t("Apple", "أبل")}</span>
          </button>
          <button type="button" class="fvg-provider-btn facebook" data-action="launchFacebook">
            <span>f</span><span>${t("Facebook", "فيسبوك")}</span>
          </button>
          <button type="button" class="fvg-provider-btn ${state.authChannel === "email" ? "active" : ""}" data-auth-channel="email">
            <span>✉</span><span>${t("Email", "البريد")}</span>
          </button>
          <button type="button" class="fvg-provider-btn ${state.authChannel === "phone" ? "active" : ""}" data-auth-channel="phone">
            <span>📱</span><span>${t("Phone", "رقم الهاتف")}</span>
          </button>
          <button type="button" class="fvg-provider-btn" data-action="openAdminModal">
            <span>🛡</span><span>${t("Admin", "الإدارة")}</span>
          </button>
        </div>

        <div style="display:flex;gap:8px;margin-bottom:10px;flex-wrap:wrap;align-items:center;justify-content:space-between;">
          <span style="font-size:0.82rem;color:#FAF2E8;opacity:0.9;">
            ${state.authRegisterMode ? t("Creating new workspace (7-day / 168h free trial)", "إنشاء حساب جديد (تجربة مجانية 7 أيام / 168 ساعة)") : t("Signing in to existing workspace", "تسجيل الدخول لحسابك الحالي")}
          </span>
          <button type="button" class="btn btn-outline" style="min-height:38px;padding:4px 12px;color:#DDB065;border-color:rgba(221,176,101,0.5);" data-action="toggleAuthMode">
            ${state.authRegisterMode ? t("Already have an account? Sign In", "لديك حساب؟ تسجيل الدخول") : t("New user? Create Account", "مستخدم جديد؟ إنشاء حساب")}
          </button>
        </div>

        <div class="grid-2">
          ${state.authChannel === "phone" ? `
            <div class="field-group">
              <label style="color:#DDB065;">${t("Phone Number (e.g. +249912345678)", "رقم الهاتف (مثال: +249912345678)")}</label>
              <input id="authPhoneInput" type="tel" placeholder="+249912345678" value="${esc(state.phonePendingE164)}" ${state.phoneOtpStep === "code" ? "disabled" : ""}/>
            </div>
            ${state.phoneOtpStep === "code" && isFirebaseWebConfigured() ? `
              <div class="field-group">
                <label style="color:#DDB065;">${t("6-Digit SMS Verification Code", "رمز التحقق SMS (6 أرقام)")}</label>
                <input id="authOtpCodeInput" type="number" placeholder="123456" />
              </div>
            ` : (!isFirebaseWebConfigured() ? `
              <div class="field-group">
                <label style="color:#DDB065;">${t("Password (6+ characters)", "كلمة المرور (6 أحرف على الأقل)")}</label>
                <div class="fvg-pass-wrap">
                  <input id="authPassInput" type="${state.authPassVisible ? "text" : "password"}" placeholder="••••••" />
                  <button type="button" class="fvg-pass-eye" data-action="toggleAuthPass" title="${state.authPassVisible ? t("Hide Password", "إخفاء كلمة المرور") : t("Show Password", "إظهار كلمة المرور")}">${state.authPassVisible ? "🙈" : "👁"}</button>
                </div>
              </div>
            ` : (state.authRegisterMode ? `
              <div class="field-group">
                <label style="color:#DDB065;">${t("Email (optional, for Welcome Letter)", "البريد الإلكتروني (اختياري لرسالة الترحيب)")}</label>
                <input id="authPhoneOptionalEmail" type="email" placeholder="name@company.com" value="${esc(state.phonePendingOptionalEmail)}" />
              </div>
            ` : `<div></div>`))}
          ` : `
            <div class="field-group">
              <label style="color:#DDB065;">${t("Workspace Email", "البريد الإلكتروني")}</label>
              <input id="authEmailInput" type="email" placeholder="name@company.com" value="${esc(state.user?.email || "")}" />
            </div>
            <div class="field-group">
              <label style="color:#DDB065;">${t("Password (6+ characters)", "كلمة المرور (6 أحرف على الأقل)")}</label>
              <div class="fvg-pass-wrap">
                <input id="authPassInput" type="${state.authPassVisible ? "text" : "password"}" placeholder="••••••" />
                <button type="button" class="fvg-pass-eye" data-action="toggleAuthPass" title="${state.authPassVisible ? t("Hide Password", "إخفاء كلمة المرور") : t("Show Password", "إظهار كلمة المرور")}">${state.authPassVisible ? "🙈" : "👁"}</button>
              </div>
            </div>
          `}
        </div>
        ${state.authRegisterMode && state.phoneOtpStep !== "code" ? `
          <div class="grid-2">
            <div class="field-group">
              <label style="color:#DDB065;">${t("Full Name", "الاسم الكامل")}</label>
              <input id="authNameInput" type="text" placeholder="${t("Your Name", "الاسم الكامل")}" value="${esc(state.user?.displayName || state.phonePendingName)}" />
            </div>
            <div class="field-group">
              <label style="color:#DDB065;">${t("Organization Name (SDG Default)", "اسم المنظمة / الشركة (الافتراضي جنيه سوداني)")}</label>
              <input id="authOrgInput" type="text" placeholder="${t("My Company", "شركتي")}" value="${esc(state.activeOrg?.name || state.phonePendingOrg)}" />
            </div>
          </div>
          ${state.authChannel === "phone" && !isFirebaseWebConfigured() ? `
            <div class="field-group">
              <label style="color:#DDB065;">${t("Email (optional, for Welcome Letter)", "البريد الإلكتروني (اختياري لرسالة الترحيب)")}</label>
              <input id="authPhoneOptionalEmail" type="email" placeholder="name@company.com" value="${esc(state.phonePendingOptionalEmail)}" />
            </div>
          ` : ""}
        ` : `<input id="authOrgInput" type="hidden" value="${esc(state.activeOrg?.name || state.phonePendingOrg)}" />`}
        ${state.authChannel === "phone" ? `<div id="fvgRecaptchaBox" style="margin-bottom:8px;"></div>` : ""}
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          ${state.authChannel === "phone" && isFirebaseWebConfigured() && state.phoneOtpStep === "code" ? `
            <button type="button" class="btn btn-gold" style="flex:1;" data-action="verifyPhoneOtp">${t("Verify SMS Code & Sign In", "تأكيد الرمز والدخول")}</button>
            <button type="button" class="btn btn-outline" style="color:#DDB065;border-color:rgba(221,176,101,0.45);" data-action="resetPhoneOtp">${t("Change Number", "تغيير الرقم")}</button>
          ` : `
            <button type="button" class="btn btn-gold" style="flex:1;" data-action="launchEmailSignIn">
              ${state.authChannel === "phone"
                ? (isFirebaseWebConfigured()
                    ? t("Send SMS Verification Code", "إرسال رمز التحقق SMS")
                    : (state.authRegisterMode ? t("Register with Phone (Local Mode)", "تسجيل بالهاتف (الوضع المحلي)") : t("Sign In with Phone (Local Mode)", "دخول بالهاتف (الوضع المحلي)")))
                : (state.authRegisterMode ? t("Create Account (7-Day Trial)", "إنشاء حساب (تجربة 7 أيام)") : t("Sign In with Email", "دخول بالبريد الإلكتروني"))}
            </button>
          `}
          <button type="button" class="btn btn-outline" style="color:#DDB065;border-color:rgba(221,176,101,0.45);" data-action="resetPassword">${t("Forgot Password?", "نسيت كلمة المرور؟")}</button>
        </div>
      </div>
      ${renderTrialBanner(true)}
      <div class="card cream" style="border-color:var(--fvg-warm-gold);">
        <div class="row-between">
          <h3 style="margin:0;">✨ ${t("Smart AI Assistant & Supervisor", "المساعد الذكي والمشرف الشخصي")}</h3>
          <span class="badge">${t("Open Q&A", "إجابة شاملة")}</span>
        </div>
        <p style="font-size:0.82rem;color:var(--fvg-text-muted);margin:6px 0;">
          ${t("Ask anything about FVG accounting, pricing strategies, business planning, or any general outside topic.", "اسأل أي سؤال عن المحاسبة والتكاليف أو أي موضوع عام خارج التطبيق مباشرة قبل التسجيل.")}
        </p>
        <div class="subtabs" style="margin-bottom:6px;">
          ${[
            t("How does FVG protect capital from inflation?", "كيف يحمي دليل رؤيتك رأس المال من التضخم؟"),
            t("What are the types of flowers?", "ما هي أنواع الزهور وأشهر أصنافها؟"),
            t("Explain Absorption vs Variable costing", "اشرح الفرق بين التكلفة الكلية والمتغيرة")
          ].map((ch) => `<button type="button" class="subtab" data-prelogin-chip="${esc(ch)}">💡 ${esc(ch)}</button>`).join("")}
        </div>
        <div class="field-group">
          <textarea id="preLoginAiPrompt" rows="2" placeholder="${t("Type any question here...", "اكتب أي سؤال هنا...")}">${esc(state.preLoginAiQuestion)}</textarea>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          <button type="button" class="btn btn-primary" style="flex:1;" ${state.isAiLoading ? "disabled" : ""} data-action="askPreLoginAi">
            ${state.isAiLoading ? t("Thinking...", "جاري التفكير والرد...") : t("Ask AI Assistant", "إرسال للمساعد الذكي")}
          </button>
          ${state.isAiLoading ? `<button type="button" class="btn btn-danger" data-action="stopAi">${t("Stop", "إيقاف")}</button>` : ""}
        </div>
        ${state.preLoginAiAnswer ? `<div class="fvg-chat-bubble assistant" style="margin-top:10px;max-width:100%;">${formatAiMarkdownHtml(state.preLoginAiAnswer)}</div>` : ""}
        ${state.preLoginAiQueries.length ? `<div style="font-size:0.75rem;color:var(--fvg-text-muted);margin-top:6px;">🔎 ${esc(state.preLoginAiQueries.join(" • "))}</div>` : ""}
        ${state.preLoginAiSources.length ? `<div class="fvg-source-row">${state.preLoginAiSources.map((s) => `<a class="fvg-source-chip" href="${esc(s.uri)}" target="_blank" rel="noopener">🌐 ${esc(s.title)}</a>`).join("")}</div>` : ""}
      </div>
      <div class="card" style="border-color:rgba(221,176,101,0.5);">
        <div class="row-between">
          <div>
            <strong>📲 ${t("Install on iPhone, Tablet, Android & Laptop", "تثبيت المنصة على أيفون والتابلت وأندرويد واللابتوب")}</strong>
            <div style="font-size:0.8rem;color:var(--fvg-text-muted);margin-top:2px;">
              ${t("Install FVG as a standalone responsive app on iPhone (Safari), iPad, Android, Windows & macOS.", "ثبّت دليل رؤيتك المالي كتطبيق مستقل سريع الاستجابة على أيفون وأيباد وأندرويد واللابتوب.")}
            </div>
          </div>
          <div style="display:flex;gap:6px;flex-wrap:wrap;">
            <a class="btn btn-outline" href="./assets/fvg-app-debug.apk" download="fvg-app.apk" style="font-size:0.8rem;padding:4px 10px;color:#DDB065;border-color:rgba(221,176,101,0.5);">🤖 ${t("Download APK", "تحميل APK")}</a>
            <button type="button" class="btn btn-gold" data-action="triggerPwaInstall">⬇ ${t("Install / Guide", "دليل التثبيت (آيفون/كمبيوتر)")}</button>
          </div>
        </div>
      </div>
    `;
  } else if (state.publicPage === 1) {
    body = `
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Supported Business Sectors", "القطاعات المدعومة وخارطة التوسع")}</h3>${helpBtn("sectors")}</div>
        <p style="font-size:0.84rem;color:var(--fvg-text-muted);margin-top:6px;">${t("Tap any active sector to preview its Chart of Accounts structure.", "اضغط على أي قطاع مفعّل لمعاينة دليل الحسابات القياسي الخاص به.")}</p>
        ${renderSectorsCards()}
      </div>
    `;
  } else if (state.publicPage === 2) {
    body = `
      ${renderTrialBanner(true)}
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Tiered Subscription Plans & Verification", "الباقات المتدرجة والتحقق من الدفع")}</h3>${helpBtn("billing")}</div>
        <p style="font-size:0.86rem;">${t("Every workspace receives a full 7-day (168-hour) trial. Choose Starter, Professional, or Enterprise based on required modules and quotas. Upgrades use Bankak, O-Cash, or Bank Transfer proof reviewed by Owner Zahra Yousif.", "تبدأ كل منظمة بتجربة مجانية 7 أيام (168 ساعة). تتميز كل باقة بمستوى صلاحيات وحدود خاص، وتتم المراجعة والموافقة حصرياً من المالكة زهراء يوسف.")}</p>
        ${renderPlansCards()}
      </div>
    `;
  } else if (state.publicPage === 3) {
    body = `
      ${renderFounderCard(false)}
      <div class="card">
        <h3 style="margin-top:0;">${t("About Financial Vision Guide", "رؤية منصة دليل رؤيتك المالي")}</h3>
        <p style="line-height:1.8;">${esc(state.isArabic ? APPROVED_ARABIC_ABOUT_US : APPROVED_ENGLISH_ABOUT_US)}</p>
      </div>
    `;
  } else if (state.publicPage === 4) {
    const stepsEn = [
      "1. Sign in with Email or Google (SDG default) to activate your 7-day (168-hour) server-synced trial.",
      "2. Record product prices with append-only history, CSV import, and >= 15% unusual spike alerts.",
      "3. Log monthly/annual inflation rates & weighted input baskets.",
      "4. Document manual FX rates (Draft / Approved / Cancelled) with stale (>7 days) reminders.",
      "5. Build product BOM recipes and compute costs across all 8 Costing Methods + Risk Engine.",
      "6. Run What-If sensitivity simulations across materials, FX, price, and volume.",
      "7. Post balanced Double-Entry Journals to render Income Statement, Balance Sheet & 18 Ratios.",
      "8. Track FIFO / Moving Average inventory, batch expiry alerts & replacement capital gap.",
      "9. Scan invoices via camera OCR, query the Financial Assistant, and export CSV/JSON/PDF."
    ];
    const stepsAr = [
      "١. سجّل بالبريد الإلكتروني أو حساب قوقل لتفعيل الفترة التجريبية الكاملة لمدة 7 أيام (168 ساعة).",
      "٢. وثّق أسعار المنتجات والمواد الخام دون مسح التاريخ واستورد جداول الأسعار مع تنبيه القفزات 15% فأكثر.",
      "٣. احسب التضخم المركب وسلات التكاليف الموزونة.",
      "٤. سجّل أسعار الصرف يدوياً (مسودة / معتمد / ملغى) مع تنبيه الأسعار الأقدم من 7 أيام.",
      "٥. أنشئ تركيبات المنتجات واحسب التكلفة بالأساليب الـ 8 مع محرك المخاطر ونقطة التعادل.",
      "٦. اختبر سيناريوهات ماذا لو لصدمات المواد وسعر الصرف والحجم.",
      "٧. رحّل قيوداً مزدوجة متوازنة لاستخراج قائمة الدخل والميزانية و18 نسبة مالية.",
      "٨. راقب المخزون وتواريخ الصلاحية وفجوة تكلفة الاستبدال الفعلية.",
      "٩. صوّر الفواتير بالكاميرا واستشر المساعد المالي وصدّر التقارير والنسخ الاحتياطي."
    ];
    body = `<div class="card cream"><h3 style="margin-top:0;">${t("9-Step Operational Guide", "دليل التشغيل السريع في 9 خطوات")}</h3>${(state.isArabic ? stepsAr : stepsEn).map((st) => `<p style="margin:6px 0;">${esc(st)}</p>`).join("")}</div>`;
  } else if (state.publicPage === 5) {
    body = `
      ${renderFounderCard(false)}
      <div class="card">
        <h3 style="margin-top:0;">${t("Official Support Contact", "التواصل والدعم الرسمي")}</h3>
        <p><strong>${t("Founder & Owner:", "المؤسسة والمالكة:")}</strong> ${esc(state.isArabic ? Engine.FOUNDER_INFO.nameAr : Engine.FOUNDER_INFO.nameEn)}</p>
        <p><strong>${t("Direct Phone / WhatsApp:", "الهاتف / واتساب المباشر:")}</strong> ${esc(Engine.FOUNDER_INFO.phone)}</p>
        <p><strong>${t("Official Email:", "البريد الرسمي:")}</strong> ${esc(Engine.OWNER_EMAIL)}</p>
        <p style="font-size:0.86rem;color:var(--fvg-text-muted);">${t("Sign in to submit tracked support tickets directly to Owner Zahra Yousif and receive updates in-app.", "بعد تسجيل الدخول يمكنك إرسال تذكرة دعم ومتابعة رد المالكة مباشرة داخل المنصة.")}</p>
        <a class="btn btn-primary" href="mailto:${esc(Engine.OWNER_EMAIL)}?subject=Support">${t("Open Email Client", "إرسال بريد إلكتروني")}</a>
      </div>
    `;
  } else if (state.publicPage === 6) {
    body = `<div class="card"><h3 style="margin-top:0;">${t("Privacy & Tenant Isolation", "الخصوصية وعزل البيانات")}</h3><p>${t("Each organization's financial records, products, inventory, and documents remain strictly isolated by membership rules.", "تُحفظ بيانات كل منظمة بعزل كامل وفق قواعد الصلاحيات ولا تتم مشاركتها مع أي منظمة أخرى.")}</p></div>`;
  } else {
    body = `<div class="card"><h3 style="margin-top:0;">${t("Terms & Methodology", "الشروط والمنهجية المحاسبية")}</h3><p>${t("What-If simulations show hypothetical scenario outcomes rather than predictions. Posted journals are immutable and corrected via reversing entries.", "نتائج المحاكاة توضح أثر السيناريوهات الافتراضية وليست تنبؤاً، وتُصحح القيود المرحّلة عبر قيود العكس فقط.")}</p></div>`;
  }

  return `
    <div class="main-content">
      ${renderBanner(t("FVG • Financial Vision Guide", "دليل رؤيتك المالي"), t("Smarter Decisions. Stronger Future.", "قرارات أذكى. مستقبل مالي أقوى."), false)}
      ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
      <div class="subtabs-sticky">
        <div class="subtabs">
          ${pages.map((p, i) => `<button class="subtab ${state.publicPage === i ? "active" : ""}" data-public-page="${i}">${esc(p)}</button>`).join("")}
        </div>
      </div>
      ${body}
    </div>
    <button class="fvg-fab-ai" data-action="openAiDrawer" title="${t("Always-On Smart AI Assistant", "المساعد الذكي الفوري")}">✨ ${t("AI Help", "مساعد ذكي")}</button>
    ${renderFloatingAiModal()}
    ${renderAdminLoginModal()}
    ${renderCrossDeviceInstallModal()}
  `;
}

function renderStatementsAndRatios() {
  return Engine.generateFinancialStatements(state.accounts, state.journalEntries);
}

function renderTrialGateCard() {
  const hours = effectiveTrialDurationHours();
  const days = Math.max(1, Math.round(hours / 24));
  return `
    <div class="card cream" style="border-color:var(--fvg-negative);">
      <h3 style="color:var(--fvg-negative);margin-top:0;">${t(`${days}-Day (${hours}-Hour) Trial Ended`, `انتهت الفترة التجريبية (${days} أيام / ${hours} ساعة)`)}</h3>
      <p>${t("Submit a Bankak, O-Cash, or Bank Transfer proof in Assistant -> Org & Billing for Platform Owner verification.", "يرجى إرسال إثبات الدفع (بنكك / أوكاش / تحويل بنكي) في المساعد المالي ← المنظمة والدفع للمراجعة عبر المالكة زهراء يوسف.")}</p>
      <button class="btn btn-gold" data-action="goBilling">${t("Upgrade Plan & Submit Payment Proof", "ترقية الباقة وإرسال إثبات الدفع")}</button>
    </div>
  `;
}

function renderPlanGateBanner(featureNameAr, featureNameEn) {
  return `
    <div class="card cream" style="border-color:var(--fvg-warning);">
      <div class="row-between">
        <strong>🔒 ${t("Feature Locked on Starter Tier", "ميزة مقفولة في باقتك الحالية")}</strong>
        <button class="btn btn-gold" style="min-height:38px;padding:6px 14px;" data-action="goBilling">${t("Upgrade", "ترقية الباقة")}</button>
      </div>
      <div style="font-size:0.84rem;color:var(--fvg-text-muted);margin-top:4px;">
        ${t(`${featureNameEn} requires Professional or Enterprise plan.`, `تتطلب ميزة (${featureNameAr}) الباقة الاحترافية أو باقة المؤسسات.`)}
      </div>
    </div>
  `;
}

function renderHomeTab() {
  const org = state.activeOrg;
  const curr = localizeCurrency(org?.baseCurrency || "SDG");
  const stmt = renderStatementsAndRatios();
  const invBook = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const invRepl = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice || it.avgCost), 0));
  const portfolio = Engine.money(stmt.cashAndBank + invBook);
  const latestInf = state.inflationRecords[0];
  const latestSnap = state.costSnapshots[state.costSnapshots.length - 1];
  const risk = latestSnap
    ? Engine.evaluateFinancialRisk(latestSnap.coreResult.profitMarginPct, latestInf ? latestInf.monthlyRate : null, stmt.totalLiabilities, stmt.totalEquityWithNetIncome, state.isArabic)
    : null;
  const activeAnn = state.announcements.filter((a) => Engine.isAnnouncementVisible(a, org, state.user?.email));

  const userWelcomeKey = (state.user?.email || "local").toLowerCase();
  const showWelcomeCard = !state.dismissedWelcomeFor.includes(userWelcomeKey);
  const resolvedName = state.user?.displayName || state.user?.email || t("Friend", "ضيفنا الكريم");

  let homeSignalLevel = "yellow";
  let homeSignalTitle = t("YELLOW NOTICE — Awaiting Costing & Ledger Data", "إشارة صفراء — بانتظار إدخال التكاليف والقيود");
  let homeSignalSub = t("Save your first cost snapshot and post a balanced journal to calculate complete live risk health.", "احفظ أول عملية تكلفة ورحّل قيداً مزدوجاً لتفعيل التقييم المالي الكامل.");

  if (isTrialLocked()) {
    homeSignalLevel = "red";
    homeSignalTitle = t("RED ALERT — Subscription Renewal Needed", "إشارة حمراء — انتهت صلاحية الفترة التجريبية");
    homeSignalSub = t("Upgrade via Bankak, O-Cash, or Bank Transfer to unlock full operations.", "قم بالترقية وإرسال إثبات الدفع لاستمرار كافة العمليات.");
  } else if (risk?.signal === "red" || stmt.netProfit < 0) {
    homeSignalLevel = "red";
    homeSignalTitle = t("RED ALERT — Margin Compression or Net Loss", "إشارة حمراء — تآكل هامش أو صافي خسارة");
    homeSignalSub = risk ? risk.explanation : t("Net income is negative; inspect costs and pricing.", "صافي الربح سالب حالياً؛ راجع التكاليف والأسعار فوراً.");
  } else if (risk?.signal === "yellow") {
    homeSignalLevel = "yellow";
    homeSignalTitle = t("YELLOW NOTICE — Tight Margin or Inflation Pressure", "إشارة صفراء — هامش ضيق أو تضخم صاعد");
    homeSignalSub = risk.explanation;
  } else if (risk?.signal === "green" && stmt.isAccountingEquationBalanced) {
    homeSignalLevel = "green";
    homeSignalTitle = t("GREEN SIGNAL — Healthy Financial Vision", "إشارة خضراء — صحة مالية مستقرة وآمنة");
    homeSignalSub = t("Cost margins, capital coverage, and ledger balance are within safe limits.", "هوامش التكلفة وتوازن الميزانية ضمن النطاق الصحي الآمن.");
  }

  return `
    ${showWelcomeCard ? `
      <div class="card cream" style="border-color:var(--fvg-warm-gold);">
        <div class="row-between">
          <strong>👋 ${t(`Welcome, ${resolvedName}!`, `أهلاً بك يا ${resolvedName}!`)}</strong>
          <button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="dismissWelcome">${t("Dismiss", "إخفاء")}</button>
        </div>
        <p style="font-size:0.86rem;line-height:1.65;margin:6px 0;">
          ${t("Your 7-day (168h) workspace is ready! Start by adding product prices, running an 8-method cost snapshot, or asking the Smart AI Supervisor.", "مساحة عملك للتجربة الكاملة لمدة 7 أيام جاهزة الآن! ابدأ بتوثيق الأسعار أو حساب التكلفة أو استشارة المشرف الذكي.")}
        </p>
      </div>
    ` : ""}
    <div class="card ${state.offlineConflicts.length ? "cream" : ""}" style="border-color:${state.offlineConflicts.length ? "var(--fvg-negative)" : (!state.isOnline || state.offlineQueue.length ? "var(--fvg-warning)" : "var(--fvg-positive)")};">
      <div class="row-between">
        <div>
          <strong>${state.isOnline ? "☁" : "📴"} ${state.offlineConflicts.length
            ? t(`Sync Conflict (${state.offlineConflicts.length}) — Action Needed`, `تعارض مزامنة (${state.offlineConflicts.length}) — يتطلب قرارك`)
            : (!state.isOnline
              ? t(`Offline Mode • Queued: ${state.offlineQueue.length}`, `وضع عدم الاتصال • طابور الانتظار: ${state.offlineQueue.length}`)
              : (state.offlineQueue.length
                ? t(`Online • Pending Sync: ${state.offlineQueue.length}`, `متصل بالشبكة • بانتظار المزامنة: ${state.offlineQueue.length}`)
                : t("Online • Hybrid Sync Ready ✓", "متصل بالشبكة • المزامنة الهجينة محدثة ✓")))}</strong>
          ${state.offlineQueue.length ? `<div style="font-size:0.78rem;color:var(--fvg-text-muted);margin-top:2px;">${state.offlineQueue.slice(0, 3).map((q) => esc(q.title)).join(" • ")}</div>` : ""}
        </div>
        ${state.offlineQueue.length ? `<button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" ${!state.isOnline ? "disabled" : ""} data-action="flushOfflineQueue">🔄 ${t("Sync Now", "مزامنة الآن")}</button>` : ""}
      </div>
      ${state.offlineConflicts.map((c) => `
        <div class="card cream" style="margin-top:8px;">
          <strong style="color:var(--fvg-negative);">${esc(c.opType)}: ${esc(c.localSummary)}</strong>
          <div style="font-size:0.78rem;color:var(--fvg-text-muted);margin:4px 0;">${t("Server Version:", "نسخة السحابة:")} ${esc(c.serverSummary)}</div>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-primary" style="flex:1;min-height:38px;" data-resolve-conflict="local:${esc(c.id)}">${t("Keep Local", "اعتماد المحلي")}</button>
            <button class="btn btn-outline" style="flex:1;min-height:38px;" data-resolve-conflict="server:${esc(c.id)}">${t("Keep Server", "اعتماد السحابة")}</button>
          </div>
        </div>
      `).join("")}
    </div>
    ${isTrialLocked() ? renderTrialGateCard() : renderTrialBanner(false)}
    ${renderStatusSignalBanner(homeSignalLevel, homeSignalTitle, homeSignalSub)}
    ${activeAnn.map((a) => `
      <div class="card cream" style="border-color:var(--fvg-warm-gold);">
        <div class="row-between"><strong>📢 ${esc(state.isArabic ? (a.titleAr || a.titleEn) : (a.titleEn || a.titleAr))}</strong><span class="badge">${t("Announcement", "تنبيه من الإدارة")}</span></div>
        <div style="font-size:0.88rem;margin-top:4px;">${esc(state.isArabic ? (a.bodyAr || a.bodyEn) : (a.bodyEn || a.bodyAr))}</div>
      </div>
    `).join("")}
    <div class="card hero">
      <div class="row-between">
        <span style="color:#DDB065;font-weight:700;">${t("Total Cash & Inventory Book Value", "إجمالي النقدية والقيمة الدفترية للمخزون")}</span>
        <div style="display:flex;align-items:center;gap:6px;">
          <span class="badge" style="color:#DDB065;border-color:#DDB065;">${esc(curr)}</span>
          ${helpBtn("health_signal")}
        </div>
      </div>
      <div style="font-size:1.9rem;font-weight:800;margin:6px 0;">${portfolio} ${esc(curr)}</div>
      <div style="font-size:0.85rem;">${t("Cash", "النقدية")}: ${stmt.cashAndBank} • ${t("Inventory Book", "المخزون الدفتري")}: ${invBook}</div>
      <div style="font-size:0.78rem;color:#DDB065;margin-top:4px;">${t("Replacement Estimate", "تكلفة الاستبدال الحالية")}: ${invRepl} ${esc(curr)}</div>
    </div>
    ${(() => {
      const hs = Engine.computeFinancialHealthScore(stmt, state.costSnapshots, latestInf ? latestInf.monthlyRate : null, state.isArabic);
      return `
        <div class="card cream" style="display:flex;align-items:center;gap:14px;">
          <div class="fvg-health-ring">
            <span style="font-size:1.15rem;color:#DDB065;">${hs.score}</span>
            <span style="font-size:0.65rem;">/100 (${hs.grade})</span>
          </div>
          <div style="flex:1;min-width:0;">
            <div style="font-weight:800;">${t("Executive Financial Health Score", "المؤشر التنفيذي للصحة المالية")}</div>
            <div style="font-size:0.84rem;color:var(--fvg-text-muted);">${esc(hs.statusLabel)}</div>
          </div>
          <span class="badge ${hs.score >= 70 ? "low" : (hs.score >= 50 ? "medium" : "high")}">${esc(hs.grade)}</span>
        </div>
      `;
    })()}
    <div class="fvg-chart-card">
      <div class="row-between" style="margin-bottom:6px;">
        <strong>📊 ${t("Live Financial Pulse Overview", "ملخص الأداء المالي المباشر")}</strong>
        <span class="badge">${esc(curr)}</span>
      </div>
      ${renderSvgBarOrLineChart([
        { label: t("Revenue", "الإيرادات"), value: Math.max(0, Math.round(stmt.revenue)) },
        { label: t("COGS", "التكلفة"), value: Math.max(0, Math.round(stmt.cogs)) },
        { label: t("Expenses", "المصروفات"), value: Math.max(0, Math.round(stmt.operatingExpenses)) },
        { label: t("Net Profit", "صافي الربح"), value: Math.max(0, Math.round(stmt.netProfit)) },
        { label: t("Cash", "النقدية"), value: Math.max(0, Math.round(stmt.cashAndBank)) },
        { label: t("Inventory", "المخزون"), value: Math.max(0, Math.round(invBook)) }
      ], "bar", "#15423A", "#DDB065")}
    </div>
    <div class="grid-3">
      <div class="card"><div style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Revenue", "الإيرادات")}</div><strong style="font-size:1.25rem;color:var(--fvg-positive);">${stmt.revenue}</strong></div>
      <div class="card"><div style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("COGS & Expenses", "التكاليف والمصروفات")}</div><strong style="font-size:1.25rem;color:var(--fvg-warning);">${Engine.money(stmt.cogs + stmt.operatingExpenses)}</strong></div>
      <div class="card"><div style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Net Profit", "صافي الربح")}</div><strong style="font-size:1.25rem;color:${stmt.netProfit < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${stmt.netProfit}</strong></div>
    </div>
    <div class="card">
      <div class="row-between"><h3 style="margin:0;">${t("Active Sectors (Tap to Preview & Apply Accounts)", "القطاعات المفعّلة (اضغط للمعاينة والتطبيق)")}</h3>${helpBtn("sectors")}</div>
      <div class="grid-2" style="margin-top:8px;">
        ${renderSectorsCards()}
      </div>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Quick Shortcuts & Tools", "اختصارات سريعة للأدوات")}</h3>
      <div class="grid-3">
        <button class="btn btn-outline" data-tab="1">${t("Prices", "الأسعار")}</button>
        <button class="btn btn-outline" data-tab="2">${t("Costing", "التكاليف")}</button>
        <button class="btn btn-outline" data-tab="3">${t("Ledger", "المحاسبة")}</button>
        <button class="btn btn-outline" data-tab="4">${t("Stock", "المخزون")}</button>
        <button class="btn btn-outline" data-tab="5">${t("Financial Assistant", "المساعد المالي")}</button>
        <button class="btn btn-gold" data-action="openCalc">🧮 ${t("Calculator", "الآلة الحاسبة")}</button>
        <button class="btn btn-outline" data-action="openProfile">👤 ${t("My Profile", "ملفي الشخصي")}</button>
        <button class="btn btn-outline" data-action="openHelp">❓ ${t("Help & Support", "المساعدة والدعم")}</button>
        <button class="btn btn-outline" data-action="replayTour">🧭 ${t("Start Tour", "جولة تعريفية")}</button>
        ${isPlatformOwner() ? `<button class="btn btn-primary" data-action="openOwnerAdmin">🛡 ${t("Owner Console (/admin)", "لوحة الإدارة (/admin)")}</button>` : ""}
      </div>
    </div>
    ${renderFounderCard(true)}
  `;
}

function renderPricesTab() {
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  const sub = state.subTab.prices;
  const subLabels = [t("Prices & Import", "الأسعار والاستيراد"), t("Inflation & Baskets", "التضخم والسلات"), t("Manual FX", "الصرف اليدوي")];
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Record Product Price Observation", "تسجيل ملاحظة سعر جديدة")}</h3>${helpBtn("prices")}</div>
        <div class="grid-2" style="margin-top:8px;">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="prName" placeholder="${t("Sugar 50kg", "سكر 50 كجم")}"/></div>
          <div class="field-group"><label>${t("SKU / Code", "الرمز التعريفي")}</label><input id="prSku" placeholder="${t("SUG-50", "رم-50")}"/></div>
          <div class="field-group"><label>${t("Category", "الفئة")}</label><input id="prCat" value="${t("Raw Material", "مادة خام")}"/></div>
          <div class="field-group"><label>${t("New Price", "السعر الجديد")} (${esc(curr)})</label><input id="prVal" type="number" step="0.01"/></div>
        </div>
        <button class="btn btn-primary" data-action="savePrice">${t("Save Price Observation", "حفظ السعر وحساب التغير %")}</button>
      </div>
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Import Product Prices from Table File", "استيراد قائمة الأسعار من ملف جدول")}</h3>
        <p style="font-size:0.8rem;margin-top:0;color:var(--fvg-text-muted);">${t("Format: name,sku,category,price (one item per line)", "التنسيق: اسم المنتج,الرمز,الفئة,السعر (صنف بكل سطر)")}</p>
        <div class="field-group"><input id="csvFile" type="file" accept=".csv,text/csv"/></div>
        <div class="field-group"><textarea id="csvPaste" rows="2" placeholder="${t("Sugar 50kg,SUG-50,Raw Material,42000", "سكر 50 كجم,رم-50,مادة خام,42000")}"></textarea></div>
        <div class="grid-2">
          <button class="btn btn-gold" data-action="importCsv">${t("Import Rows", "استيراد الصفوف")}</button>
          <button class="btn btn-outline" data-action="downloadCsvTemplate">⬇ ${t("Download CSV Template", "تحميل قالب CSV جاهز")}</button>
        </div>
      </div>
      ${state.priceObservations.length === 0 ? `<div class="card cream">${t("No price records yet.", "لا توجد أسعار مسجلة بعد.")}</div>` : state.priceObservations.slice().reverse().map((o) => `
        <div class="card">
          <div class="row-between"><strong>${esc(o.productName)} (${esc(o.sku)})</strong><span class="badge ${o.isUnusual ? "high" : "low"}">${o.pctChange == null ? t("NEW", "جديد") : `${o.pctChange}% ${o.direction === "up" ? t("UP", "ارتفاع") : o.direction === "down" ? t("DOWN", "انخفاض") : ""}`}</span></div>
          <div style="font-size:0.85rem;">${o.previousPrice} → ${o.price} ${esc(curr)} • ${esc(o.date)}</div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    const compound = Engine.calculateCompoundedInflation(state.inflationRecords.map((r) => r.monthlyRate));
    panel = `
      <div class="card cream">
        <div class="row-between"><strong>${t("Cumulative Compounded Inflation", "التضخم المركب التراكمي")}</strong><div style="display:flex;align-items:center;gap:6px;"><strong style="color:var(--fvg-negative);">${compound}%</strong>${helpBtn("inflation")}</div></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Add Inflation Record", "تسجيل معدل التضخم")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Period (YYYY-MM)", "الفترة (سنة-شهر)")}</label><input id="infPeriod" value="${new Date().toISOString().slice(0, 7)}"/></div>
          <div class="field-group"><label>${t("Monthly %", "المعدل الشهري %")}</label><input id="infMonthly" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Source Type", "نوع المصدر")}</label><select id="infSource"><option value="official">${t("Official", "رسمي")}</option><option value="calculated">${t("Calculated", "محسوب")}</option><option value="manual">${t("Manual", "يدوي")}</option><option value="provisional">${t("Provisional", "تقديري")}</option></select></div>
        </div>
        <button class="btn btn-primary" data-action="saveInflation">${t("Save Inflation Record", "حفظ معدل التضخم")}</button>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Weighted Inflation Price Basket", "سلة الأسعار الموزونة لقياس التضخم")}</h3>
        <div class="grid-4">
          <div class="field-group"><label>${t("Basket Name", "اسم السلة")}</label><input id="bskName" value="${t("Factory Inputs", "مدخلات الإنتاج")}"/></div>
          <div class="field-group"><label>${t("Item", "الصنف")}</label><input id="bskItem" placeholder="${t("Diesel / Sugar", "وقود / سكر")}"/></div>
          <div class="field-group"><label>${t("Base Price", "سعر الأساس")}</label><input id="bskBase" type="number" value="1000"/></div>
          <div class="field-group"><label>${t("Current Price", "السعر الحالي")}</label><input id="bskCurr" type="number" value="1250"/></div>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="bskQty" type="number" value="10"/></div>
          <div class="field-group"><label>${t("Weight", "الوزن")}</label><input id="bskWeight" type="number" step="0.1" value="1"/></div>
        </div>
        <button class="btn btn-gold" data-action="saveBasket">${t("Calculate & Save Weighted Basket", "حساب وحفظ السلة الموزونة")}</button>
      </div>
      ${state.baskets.map((b) => `<div class="card cream"><div class="row-between"><strong>${esc(b.name)} (${esc(b.item)})</strong><span class="badge high">${t("Index", "المؤشر")} ${b.indexValue ?? t("Not Available", "غير متاح")} (${b.basketInflationPct ?? 0}%)</span></div><div style="font-size:0.85rem;">${t("Base", "الأساس")}: ${b.baseCost} → ${t("Current", "الحالي")}: ${b.currentCost} ${esc(curr)}</div></div>`).join("")}
      ${state.inflationRecords.map((r) => `<div class="card"><div class="row-between"><strong>${esc(r.period)}</strong><span class="badge">${esc(localizeStatus(r.sourceType))}</span></div><div>${r.monthlyRate}% / ${t("mo", "شهرياً")}</div></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card cream">
        <div class="row-between"><h3 style="margin:0;">${t("Manual FX Registry (No Auto-Fetch Errors)", "توثيق أسعار الصرف يدوياً (لتجنب الأخطاء الإلكترونية)")}</h3>${helpBtn("fx_manual")}</div>
        <p style="font-size:0.82rem;color:var(--fvg-text-muted);margin-top:6px;">${t("Enter exchange rates manually as Draft or Approved. Rates older than 7 days trigger a stale-rate warning.", "يُدخل سعر الصرف يدوياً بحالة مسودة أو معتمد دون سحب إلكتروني تلقائي. الأسعار الأقدم من 7 أيام تُظهر تنبيهاً.")}</p>
        <div class="grid-3">
          <div class="field-group"><label>${t("Base Currency", "العملة الأساس")}</label><input id="fxBase" value="${t("USD", "دولار أمريكي")}"/></div>
          <div class="field-group"><label>${t("Quote Currency", "العملة المقابلة")}</label><input id="fxQuote" value="${esc(curr)}"/></div>
          <div class="field-group"><label>${t("Manual Rate", "السعر اليدوي")}</label><input id="fxRate" type="number" step="0.0001"/></div>
          <div class="field-group"><label>${t("Effective Date", "تاريخ السريان")}</label><input id="fxDate" type="date" value="${new Date().toISOString().slice(0, 10)}"/></div>
          <div class="field-group"><label>${t("Source", "المصدر")}</label><input id="fxSource" placeholder="${t("Central Bank / Market", "البنك المركزي / السوق")}"/></div>
          <div class="field-group"><label>${t("Status", "الحالة")}</label><select id="fxStatus"><option value="approved">${t("Approved", "معتمد")}</option><option value="draft">${t("Draft", "مسودة")}</option></select></div>
        </div>
        <button class="btn btn-primary" data-action="saveFx">${t("Save Manual FX Rate", "حفظ سعر الصرف اليدوي")}</button>
      </div>
      ${state.fxRates.map((f) => {
        const fresh = Engine.assessFxRateFreshness(f.date, f.status);
        return `
          <div class="card">
            <div class="row-between"><strong>${esc(f.base)} / ${esc(f.quote)} = ${f.rate}</strong><span class="badge ${f.status === "approved" ? "low" : (f.status === "cancelled" ? "high" : "medium")}">${esc(localizeStatus(f.status || "approved"))}</span></div>
            <div style="font-size:0.8rem;color:var(--fvg-text-muted);">${esc(f.date)} • ${esc(f.source || t("Manual Entry", "إدخال يدوي"))}</div>
            ${fresh.isStale ? `<div style="font-size:0.78rem;color:var(--fvg-warning);margin-top:4px;">⚠ ${t("Stale rate (>7 days old) — please verify.", "سعر صرف قديم (أكثر من 7 أيام) — يُنصح بالتحديث.")}</div>` : ""}
            <div style="display:flex;gap:8px;margin-top:8px;">
              ${f.status !== "approved" ? `<button class="btn btn-outline" data-fx-status="approved:${esc(f.id)}">${t("Approve", "اعتماد")}</button>` : ""}
              ${f.status !== "cancelled" ? `<button class="btn btn-outline" data-fx-status="cancelled:${esc(f.id)}">${t("Cancel", "إلغاء")}</button>` : ""}
            </div>
          </div>
        `;
      }).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="prices:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderCostingTab() {
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  const ent = getEntitlements();
  const sub = state.subTab.costing;
  const subLabels = [t("8 Costing & BOM", "8 طرق تكلفة والتركيبات"), t("CVP & Risk", "التعادل والمخاطر"), t("Variance", "الانحرافات"), t("What-If", "ماذا لو")];
  let panel = "";
  if (sub === 0) {
    const allowedMethods = ent.allowAllCostingMethods ? Engine.ALL_COSTING_METHODS : ["actual", "standard"];
    if (!allowedMethods.includes(state.costDraftMethod)) state.costDraftMethod = "actual";
    panel = `
      ${!ent.allowBomAndProduction ? renderPlanGateBanner("قائمة المواد والتركيبات + الأساليب المتقدمة", "BOM Recipes & All 8 Costing Methods") : `
        <div class="card cream">
          <div class="row-between"><h3 style="margin:0;">${t("BOM / Product Recipe Builder", "بناء قائمة المواد والتركيبات")}</h3>${helpBtn("bom")}</div>
          <div class="grid-3" style="margin-top:8px;">
            <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="bomProd" placeholder="${t("Batch Product", "منتج تشغيلة")}"/></div>
            <div class="field-group"><label>${t("Ingredient Name", "المادة الخام")}</label><input id="bomIng" placeholder="${t("Raw Flour", "دقيق خام")}"/></div>
            <div class="field-group"><label>${t("Qty Per Batch", "الكمية للتشغيلة")}</label><input id="bomQty" type="number" value="50"/></div>
            <div class="field-group"><label>${t("Ingredient Unit Cost", "تكلفة وحدة المادة")}</label><input id="bomCost" type="number" value="10"/></div>
            <div class="field-group"><label>${t("Batch Output Units", "وحدات مخرج التشغيلة")}</label><input id="bomBatch" type="number" value="100"/></div>
            <div class="field-group"><label>${t("Scrap Allowance %", "نسبة الفاقد المسموح %")}</label><input id="bomScrap" type="number" value="5"/></div>
          </div>
          <button class="btn btn-gold" data-action="saveBom">${t("Save BOM Recipe & Calculate Unit Material", "حفظ التركيبة وحساب تكلفة الوحدة")}</button>
          ${state.bomFormulas.map((b) => `<div class="row-between" style="margin-top:8px;border-top:1px solid var(--fvg-border);padding-top:6px;"><span><strong>${esc(b.productName)}</strong> (${esc(b.ingredientName)})</span><span><strong>${b.unitMaterialCost} ${esc(curr)}</strong> <button class="btn btn-outline" style="min-height:34px;padding:4px 10px;" data-del-bom="${esc(b.id)}">✕</button></span></div>`).join("")}
        </div>
      `}
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("8 Management Accounting Costing Methods", "طرق التكلفة الإدارية ومحرك المخاطر")}</h3>${helpBtn("costing_8")}</div>
        ${!ent.allowAllCostingMethods ? `<div style="font-size:0.8rem;color:var(--fvg-warning);margin:6px 0;">🔒 ${t("Starter unlocks Actual & Standard. Upgrade for all 8 methods.", "الباقة الأساسية تتيح الفعلي والمعياري فقط؛ قم بالترقية لفتح الأساليب الـ 8 كاملة.")}</div>` : ""}
        <div class="field-group" style="margin-top:8px;">
          <label>${t("Select Method", "اختر طريقة التكلفة")}</label>
          <select id="costMethod">
            ${Engine.ALL_COSTING_METHODS.map((m) => {
              const unlocked = allowedMethods.includes(m);
              return `<option value="${m}" ${!unlocked ? "disabled" : ""} ${state.costDraftMethod === m ? "selected" : ""}>${!unlocked ? "🔒 " : ""}${esc(localizeCostMethod(m))}</option>`;
            }).join("")}
          </select>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="cProd" value="${t("Industrial Batch A", "تشغيلة صناعية أ")}"/></div>
          <div class="field-group"><label>${t("Good Units", "الوحدات الصالحة")}</label><input id="cUnits" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Direct Material", "مواد مباشرة")}</label><input id="cMat" type="number" value="500"/></div>
          <div class="field-group"><label>${t("Direct Labor", "عمالة مباشرة")}</label><input id="cLab" type="number" value="200"/></div>
          <div class="field-group"><label>${t("Variable Overhead + Pack", "تكاليف إضافية متغيرة")}</label><input id="cOvh" type="number" value="150"/></div>
          <div class="field-group"><label>${t("Fixed Costs", "التكاليف الثابتة")}</label><input id="cFix" type="number" value="250"/></div>
          <div class="field-group"><label>${t("Selling Price / Unit", "سعر البيع للوحدة")}</label><input id="cPrice" type="number" value="16"/></div>
          <div class="field-group"><label>${t("Target Margin % / Equiv Units", "الهامش المستهدف / الوحدات المكافئة")}</label><input id="cExtra" type="number" value="20"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveCosting">${t("Compute & Save Snapshot", "حساب التكلفة والمخاطر وحفظها")}</button>
      </div>
      ${state.costSnapshots.slice().reverse().map((s) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(s.productName)} (${esc(localizeCostMethod(s.method))})</strong><span class="badge ${s.risk.level}">${t(s.risk.level.toUpperCase(), s.risk.level === "high" ? "خطر مرتفع" : s.risk.level === "medium" ? "خطر متوسط" : "آمن")}</span></div>
          <div>${t("Unit Cost", "تكلفة الوحدة")}: <strong>${s.coreResult.unitCost} ${esc(curr)}</strong> | ${t("Price", "السعر")}: ${s.sellingPrice} | ${t("Margin", "الهامش")}: ${s.coreResult.profitMarginPct ?? t("Not Available", "غير متاح")}% | ${t("Markup", "العلاوة")}: ${s.coreResult.markupPct ?? t("Not Available", "غير متاح")}%</div>
          <div style="font-size:0.8rem;color:var(--fvg-text-muted);">${t("Break-Even", "نقطة التعادل")}: ${s.coreResult.breakEvenUnits ?? t("Not Available", "غير متاح")} ${t("units", "وحدة")} • ${esc(s.methodNote)}</div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    const snap = state.costSnapshots[state.costSnapshots.length - 1];
    panel = !snap ? `<div class="card cream">${t("Save a cost snapshot in 8 Costing first.", "احفظ حساب تكلفة أولاً لعرض تحليل التعادل والمخاطر.")}</div>` : `
      ${renderStatusSignalBanner(snap.risk.signal || "green", `${snap.risk.ruleName} — ${snap.productName}`, snap.risk.explanation)}
      <div class="card cream">
        <div class="row-between"><strong>${esc(snap.productName)} — ${t("Break-Even & Risk", "نقطة التعادل والمخاطر")}</strong>${helpBtn("costing_8")}</div>
        <div style="font-size:1.6rem;font-weight:800;margin:8px 0;">${snap.coreResult.breakEvenUnits ?? t("Not Available", "غير متاح")} ${t("units", "وحدة")}</div>
        <div>${t("Revenue", "الإيرادات")}: ${snap.coreResult.revenue} ${esc(curr)} | ${t("Total Cost", "التكلفة الكلية")}: ${snap.coreResult.totalCost} | ${t("Net Profit", "صافي الربح")}: ${snap.coreResult.netProfit}</div>
      </div>
    `;
  } else if (sub === 2) {
    panel = !ent.allowBomAndProduction ? renderPlanGateBanner("تحليل الانحرافات التفصيلي", "Variance Decomposition") : `
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Detailed Material & Labor Variance Decomposition", "تحليل انحرافات المواد والعمالة التفصيلي")}</h3>${helpBtn("costing_8")}</div>
        <div class="grid-3" style="margin-top:8px;">
          <div class="field-group"><label>${t("Good Units", "الوحدات الصالحة")}</label><input id="vUnits" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Std Mat Qty/Unit", "كمية المادة المعيارية")}</label><input id="vSmq" type="number" value="2"/></div>
          <div class="field-group"><label>${t("Std Mat Price", "سعر المادة المعياري")}</label><input id="vSmp" type="number" value="5"/></div>
          <div class="field-group"><label>${t("Act Mat Qty", "كمية المادة الفعلية")}</label><input id="vAmq" type="number" value="215"/></div>
          <div class="field-group"><label>${t("Act Mat Price", "سعر المادة الفعلي")}</label><input id="vAmp" type="number" value="5.4"/></div>
          <div class="field-group"><label>${t("Std Labor Hrs/Unit", "ساعات العمل المعيارية")}</label><input id="vSlh" type="number" value="1.5"/></div>
          <div class="field-group"><label>${t("Std Labor Rate", "أجر الساعة المعياري")}</label><input id="vSlr" type="number" value="6"/></div>
          <div class="field-group"><label>${t("Act Labor Hrs", "ساعات العمل الفعلية")}</label><input id="vAlh" type="number" value="145"/></div>
          <div class="field-group"><label>${t("Act Labor Rate", "أجر الساعة الفعلي")}</label><input id="vAlr" type="number" value="6.2"/></div>
        </div>
        <button class="btn btn-gold" data-action="calcVariance">${t("Run Variance Decomposition", "حساب الانحرافات التفصيلية")}</button>
        <div id="varianceResultBox" style="margin-top:10px;"></div>
      </div>
    `;
  } else {
    const snap = state.costSnapshots[state.costSnapshots.length - 1];
    panel = !ent.allowBomAndProduction ? renderPlanGateBanner("محاكي سيناريوهات ماذا لو", "What-If Scenario Simulator") : (!snap ? `<div class="card cream">${t("Save a cost snapshot first to run What-If Scenarios.", "احفظ لقطة تكلفة أولاً لتشغيل محاكي ماذا لو.")}</div>` : `
      <div class="card">
        <div class="row-between"><strong>${t("What-If Sensitivity Simulator", "محاكي ماذا لو للحساسية")}</strong><span class="badge">${t("Scenario outcome — not a prediction", "نتيجة سيناريو — ليست توقعاً")}</span></div>
        <div class="subtabs" style="margin-top:8px;">
          ${Engine.SPEC_SCENARIO_PRESETS.map((p) => `<button class="subtab" data-preset="${p.key}">${esc(state.isArabic ? p.nameAr : p.nameEn)}</button>`).join("")}
        </div>
        <div class="grid-3">
          <div class="field-group"><label>${t("Material %", "تغير المواد %")}</label><input id="scMat" type="number" value="20"/></div>
          <div class="field-group"><label>${t("FX Shock %", "صدمة الصرف %")}</label><input id="scFx" type="number" value="15"/></div>
          <div class="field-group"><label>${t("Volume %", "تغير الحجم %")}</label><input id="scVol" type="number" value="0"/></div>
          <div class="field-group"><label>${t("Price %", "تغير السعر %")}</label><input id="scPrice" type="number" value="0"/></div>
        </div>
        <button class="btn btn-primary" data-action="runScenario">${t("Simulate & Save Scenario", "محاكاة وحفظ السيناريو")}</button>
      </div>
      ${state.scenarios.slice().reverse().map((sc) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(sc.name)}</strong><span class="badge">${t("Scenario outcome — not a prediction", "نتيجة سيناريو — ليست توقعاً")}</span></div>
          <div>${t("Base Profit", "الربح الأساسي")}: ${sc.baseProfit} → ${t("Scenario Profit", "ربح السيناريو")}: <strong style="color:${sc.scenarioProfit < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${sc.scenarioProfit} ${esc(curr)}</strong></div>
        </div>
      `).join("")}
    `);
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="costing:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderLedgerTab() {
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  const sub = state.subTab.ledger;
  const subLabels = [t("Journal & COA", "القيود والدليل"), t("Statements", "القوائم المالية"), t("18 Ratios", "18 نسبة مالية"), t("Receivables & Payables", "الذمم والعملاء")];
  const stmt = renderStatementsAndRatios();
  const ratios = Engine.computeFinancialRatios(stmt, state.isArabic);
  const eqSig = Engine.evaluateAccountingEquationSignal(stmt, state.journalEntries.length > 0, state.isArabic);

  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Post Balanced Double-Entry Journal", "ترحيل قيد يومية مزدوج متوازن")}</h3>${helpBtn("ledger_je")}</div>
        <div class="grid-2" style="margin-top:8px;">
          <div class="field-group"><label>${t("Description", "البيان")}</label><input id="jeDesc" placeholder="${t("Sales cash settlement", "تحصيل مبيعات نقدية")}"/></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")} (${esc(curr)})</label><input id="jeAmt" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Debit Account", "حساب المدين")}</label><select id="jeDr">${state.accounts.map((a) => `<option value="${a.code}">${a.code} ${esc(state.isArabic ? a.nameAr : a.name)}</option>`).join("")}</select></div>
          <div class="field-group"><label>${t("Credit Account", "حساب الدائن")}</label><select id="jeCr">${state.accounts.map((a, idx) => `<option value="${a.code}" ${idx === 7 ? "selected" : ""}>${a.code} ${esc(state.isArabic ? a.nameAr : a.name)}</option>`).join("")}</select></div>
        </div>
        <button class="btn btn-primary" data-action="postJe">${t("Post Double-Entry Journal", "ترحيل القيد")}</button>
      </div>
      ${state.journalEntries.slice().reverse().map((je) => `
        <div class="card">
          <div class="row-between"><strong>${esc(je.referenceNo)} • ${esc(je.date)}</strong><span class="badge">${esc(localizeStatus(je.status))}</span></div>
          <div>${esc(je.description)} — ${je.total} ${esc(curr)}</div>
          ${je.status === "posted" ? `<button class="btn btn-outline" style="margin-top:8px;" data-reverse-je="${esc(je.id)}">${t("Create Reversal Entry", "عكس القيد")}</button>` : ""}
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    panel = `
      ${renderStatusSignalBanner(eqSig.level, eqSig.title, eqSig.subtitle)}
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Income Statement", "قائمة الدخل")}</h3>
        <div class="row-between"><span>${t("Revenue", "الإيرادات التشغيلية")}</span><strong>${stmt.revenue} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("COGS", "تكلفة المبيعات")}</span><strong>(${stmt.cogs}) ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Operating Expenses", "المصروفات التشغيلية")}</span><strong>(${stmt.operatingExpenses}) ${esc(curr)}</strong></div>
        <div class="row-between"><strong>${t("Net Profit", "صافي الربح")}</strong><strong>${stmt.netProfit} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Balance Sheet", "الميزانية العمومية")}</h3><span class="badge ${stmt.isAccountingEquationBalanced ? "low" : "high"}">${stmt.isAccountingEquationBalanced ? t("Balanced ✓", "متوازنة ✓") : t("Check", "مراجعة")}</span></div>
        <div class="row-between"><span>${t("Total Assets", "إجمالي الأصول")}</span><strong>${stmt.totalAssets} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Total Liabilities", "إجمالي الالتزامات")}</span><strong>${stmt.totalLiabilities} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Total Equity (incl. Net Income)", "إجمالي حقوق الملكية")}</span><strong>${stmt.totalEquityWithNetIncome} ${esc(curr)}</strong></div>
      </div>
      <div class="card table-wrap">
        <h3 style="margin-top:0;">${t("Trial Balance", "ميزان المراجعة")} (${t("Dr", "مدين")} ${stmt.totalTrialDebits} = ${t("Cr", "دائن")} ${stmt.totalTrialCredits})</h3>
        <table><thead><tr><th>${t("Code", "الرمز")}</th><th>${t("Account", "الحساب")}</th><th>${t("Debit", "مدين")}</th><th>${t("Credit", "دائن")}</th></tr></thead>
        <tbody>${stmt.trialBalance.map((r) => `<tr><td>${r.code}</td><td>${esc(state.isArabic ? r.nameAr : r.name)}</td><td>${r.debitBalance}</td><td>${r.creditBalance}</td></tr>`).join("")}</tbody></table>
      </div>
      <div class="card cream">
        <div class="row-between"><h3 style="margin:0;">⏳ ${t("13-Week Cash Runway & Liquidity Stress Test", "توقع السيولة والتدفق النقدي لـ 13 أسبوعاً (اختبار الضغط)")}</h3><span class="badge">${t("Stress Simulator", "محاكاة ضغط")}</span></div>
        <div class="grid-3" style="margin-top:8px;">
          <div class="field-group"><label>${t("Starting Cash", "النقدية الابتدائية")}</label><input id="rwCash" type="number" value="${esc(state.runwayParams.startingCash)}"/></div>
          <div class="field-group"><label>${t("Weekly Inflow", "التدفق الداخل/أسبوع")}</label><input id="rwIn" type="number" value="${esc(state.runwayParams.weeklyInflow)}"/></div>
          <div class="field-group"><label>${t("Weekly Outflow", "التدفق الخارج/أسبوع")}</label><input id="rwOut" type="number" value="${esc(state.runwayParams.weeklyOutflow)}"/></div>
          <div class="field-group"><label>${t("Open A/R", "الذمم المدينة")}</label><input id="rwAr" type="number" value="${esc(state.runwayParams.openAr)}"/></div>
          <div class="field-group"><label>${t("FX Shock %", "صدمة الصرف %")}</label><input id="rwFx" type="number" value="${esc(state.runwayParams.fxShockPct)}"/></div>
          <div class="field-group"><label>${t("Sales Drop %", "تراجع المبيعات %")}</label><input id="rwDrop" type="number" value="${esc(state.runwayParams.salesDropPct)}"/></div>
        </div>
        <div class="subtabs" style="margin-top:6px;">
          ${[0, 20, 30, 40].map((d) => `<button type="button" class="subtab ${Number(state.runwayParams.salesDropPct) === d ? "active" : ""}" data-runway-drop="${d}">${d === 0 ? t("Base 0%", "أساسي 0%") : `-${d}% ${t("Sales", "مبيعات")}`}</button>`).join("")}
        </div>
        <div class="field-group" style="margin-top:6px;">
          <label>${t("A/R Collection Delay (Weeks)", "تأخر تحصيل الذمم (بالأسابيع)")}</label>
          <select id="rwDelay">${[0, 1, 2, 3, 4].map((w) => `<option value="${w}" ${Number(state.runwayParams.arDelayWeeks) === w ? "selected" : ""}>${w} ${t("Weeks", "أسبوع")}</option>`).join("")}</select>
        </div>
        <button class="btn btn-primary" data-action="runCashRunway">${t("Simulate 13-Week Cash Runway", "احسب كفاية السيولة (13 أسبوعاً)")}</button>
        ${state.cashRunwayResult ? `
          <div style="margin-top:10px;">
            ${renderStatusSignalBanner(
              state.cashRunwayResult.riskLevel,
              t(`Projected Runway: ${state.cashRunwayResult.runwayWeeks} Weeks (Min Cash: ${state.cashRunwayResult.minimumCash} ${curr})`, `كفاية السيولة: ${state.cashRunwayResult.runwayWeeks} أسبوعاً (أدنى رصيد: ${state.cashRunwayResult.minimumCash} ${curr})`),
              state.isArabic ? state.cashRunwayResult.summaryAr : state.cashRunwayResult.summaryEn
            )}
            <div class="table-wrap" style="margin-top:8px;">
              <table><thead><tr><th>${t("Week", "الأسبوع")}</th><th>${t("Inflow", "الداخل")}</th><th>${t("Outflow", "الخارج")}</th><th>${t("Ending Cash", "الرصيد الختامي")}</th></tr></thead>
              <tbody>${state.cashRunwayResult.weeks.map((w) => `<tr><td>${esc(state.isArabic ? w.labelAr : w.labelEn)}</td><td>${w.inflow}</td><td>${w.outflow}</td><td style="font-weight:700;color:${w.endingCash < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${w.endingCash}</td></tr>`).join("")}</tbody></table>
            </div>
          </div>
        ` : ""}
      </div>
    `;
  } else if (sub === 2) {
    panel = ratios.map((r) => `
      <div class="card">
        <div class="row-between"><strong>${esc(r.label)}</strong><strong style="color:${r.value == null ? "var(--fvg-warning)" : "var(--fvg-positive)"};">${r.value == null ? t("Not Available", "غير متاح") : `${r.value} ${r.unitSuffix}`}</strong></div>
        <div style="font-size:0.78rem;color:var(--fvg-text-muted);">${esc(r.category)} • ${esc(r.formula)}</div>
        ${r.missingDataWarning ? `<div style="font-size:0.78rem;color:var(--fvg-negative);">⚠ ${esc(r.missingDataWarning)}</div>` : ""}
      </div>
    `).join("");
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Receivables & Payables Tracker", "متابعة الذمم المدينة والدائنة")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Type", "النوع")}</label><select id="cpType"><option value="receivable">${t("Receivable", "ذمم مدينة")}</option><option value="payable">${t("Payable", "ذمم دائنة")}</option></select></div>
          <div class="field-group"><label>${t("Party Name", "اسم العميل / المورد")}</label><input id="cpParty" placeholder="${t("Distributor Name", "اسم الجهة")}"/></div>
          <div class="field-group"><label>${t("Invoice #", "رقم الفاتورة")}</label><input id="cpInv" placeholder="${t("INV-101", "ف-101")}"/></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")} (${esc(curr)})</label><input id="cpAmt" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Due Date", "تاريخ الاستحقاق")}</label><input id="cpDue" type="date" value="${new Date().toISOString().slice(0, 10)}"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveCounterparty">${t("Save Record", "حفظ فاتورة الذمة")}</button>
      </div>
      ${state.counterpartyItems.map((c) => `<div class="card"><div class="row-between"><strong>${esc(c.partyName)} (${esc(c.invoiceNo)})</strong><span class="badge">${c.type === "receivable" ? t("Receivable", "مدين") : t("Payable", "دائن")} • ${esc(localizeStatus(c.status))}</span></div><div>${t("Amount", "المبلغ")}: ${c.amount} | ${t("Paid", "المدفوع")}: ${c.paidAmount} ${esc(curr)} • ${t("Due", "الاستحقاق")} ${esc(c.dueDate)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="ledger:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderStockTab() {
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  const sub = state.subTab.stock;
  const subLabels = [t("Inventory & Batches", "المخزون والتشغيلات"), t("Scrap & Losses", "الهدر والتلف"), t("Budgets", "الموازنات")];
  const bookTotal = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const replTotal = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice), 0));
  const invSig = Engine.evaluateInventorySignal(state.inventoryItems, bookTotal, replTotal, state.isArabic);
  const thirtyDaysIso = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);

  let panel = "";
  if (sub === 0) {
    panel = `
      ${renderStatusSignalBanner(invSig.level, invSig.title, invSig.subtitle)}
      <div class="card cream">
        <div class="row-between"><h3 style="margin:0;">${t("Book Value vs Current Replacement Cost", "القيمة الدفترية مقابل تكلفة الاستبدال")}</h3>${helpBtn("inventory")}</div>
        <div class="row-between"><span>${t("Accounting Book Value", "القيمة الدفترية")}</span><strong>${bookTotal} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Current Replacement Cost", "تكلفة الاستبدال الحالية")}</span><strong>${replTotal} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Replacement Capital Gap", "فجوة رأس المال للاستبدال")}</span><strong style="color:var(--fvg-negative);">${Engine.money(replTotal - bookTotal)} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Register Inventory Item & Batch", "إضافة صنف مخزني وتشغيلة")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("SKU", "الرمز")}</label><input id="invSku" placeholder="${t("ITM-01", "صنف-01")}"/></div>
          <div class="field-group"><label>${t("Item Name", "اسم الصنف")}</label><input id="invName" placeholder="${t("Raw Material", "مادة خام")}"/></div>
          <div class="field-group"><label>${t("Valuation Method", "طريقة التقييم")}</label><select id="invMethod"><option value="fifo">${t("First-In First-Out (FIFO)", "الوارد أولاً يُصرف أولاً")}</option><option value="moving_avg">${t("Moving Average", "المتوسط المرجح")}</option></select></div>
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="invQty" type="number" value="50"/></div>
          <div class="field-group"><label>${t("Unit Cost", "تكلفة الوحدة")}</label><input id="invCost" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Market Price", "سعر السوق")}</label><input id="invMkt" type="number" value="130"/></div>
          <div class="field-group"><label>${t("Batch Number (opt.)", "رقم التشغيلة")}</label><input id="invBatch" placeholder="${t("B-2026-01", "تشغيلة-01")}"/></div>
          <div class="field-group"><label>${t("Expiry Date (opt.)", "انتهاء الصلاحية")}</label><input id="invExpiry" type="date"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveInventory">${t("Create Inventory Item", "حفظ الصنف المخزني")}</button>
      </div>
      ${state.inventoryItems.map((it) => {
        const expSoon = it.expiryDate && it.expiryDate <= thirtyDaysIso;
        return `
          <div class="card">
            <div class="row-between"><strong>${esc(it.sku)} • ${esc(it.name)}</strong><span class="badge">${it.method === "fifo" ? t("FIFO", "الوارد أولاً") : t("Moving Avg", "المتوسط المرجح")}</span></div>
            <div>${t("Qty", "الكمية")}: ${it.qty} | ${t("Avg Cost", "متوسط التكلفة")}: ${it.avgCost} | ${t("Market", "السوق")}: ${it.marketPrice} ${esc(curr)}</div>
            ${it.batchNumber || it.expiryDate ? `<div style="font-size:0.8rem;margin-top:4px;">${it.batchNumber ? `${t("Batch", "تشغيلة")}: ${esc(it.batchNumber)} ` : ""}${it.expiryDate ? `<span class="badge ${expSoon ? "high" : ""}">${expSoon ? "⚠ " : ""}${t("Expiry", "الصلاحية")}: ${esc(it.expiryDate)}</span>` : ""}</div>` : ""}
            <div style="display:flex;gap:8px;margin-top:8px;">
              <button class="btn btn-outline" data-stock-move="in:${esc(it.id)}">+10 ${t("Receipt", "استلام")}</button>
              <button class="btn btn-outline" data-stock-move="out:${esc(it.id)}">-10 ${t("Issue", "صرف")}</button>
            </div>
          </div>
        `;
      }).join("")}
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Log Scrap / Damaged / Expired Loss", "تسجيل فاقد وتلف المخزون")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Item", "الصنف")}</label><input id="scrItem" placeholder="${t("Packaging Film", "عبوات تغليف")}"/></div>
          <div class="field-group"><label>${t("Loss Type", "النوع")}</label><select id="scrType"><option value="scrap">${t("Scrap", "هدر")}</option><option value="damaged">${t("Damaged", "تالف")}</option><option value="expired">${t("Expired", "منتهي الصلاحية")}</option></select></div>
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="scrQty" type="number" value="5"/></div>
          <div class="field-group"><label>${t("Unit Cost", "تكلفة الوحدة")}</label><input id="scrCost" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Reason", "السبب")}</label><input id="scrReason" placeholder="${t("Storage damage", "تلف تخزيني")}"/></div>
        </div>
        <button class="btn btn-danger" data-action="saveScrap">${t("Log Scrap Loss", "تسجيل الفاقد")}</button>
      </div>
      ${state.scrapLosses.map((s) => `<div class="card cream"><div class="row-between"><strong>${esc(s.itemName)}</strong><strong style="color:var(--fvg-negative);">-${s.totalLossCost} ${esc(curr)}</strong></div><div>${t("Qty", "الكمية")}: ${s.quantity} • ${esc(s.reason)}</div></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Budget & Variance", "إنشاء موازنة ومقارنتها بالفعلي")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Budget Name", "اسم الموازنة")}</label><input id="bdgName" placeholder="${t("Q4 Production", "تشغيل الربع الرابع")}"/></div>
          <div class="field-group"><label>${t("Planned", "المخطط")}</label><input id="bdgPlan" type="number" value="5000"/></div>
          <div class="field-group"><label>${t("Actual", "الفعلي")}</label><input id="bdgAct" type="number" value="5400"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveBudget">${t("Save Budget", "حفظ الموازنة")}</button>
      </div>
      ${state.budgets.map((b) => `<div class="card"><div class="row-between"><strong>${esc(b.name)}</strong><span class="badge">${b.variancePct}%</span></div><div>${t("Planned", "المخطط")}: ${b.planned} | ${t("Actual", "الفعلي")}: ${b.actual} | ${t("Variance", "الانحراف")}: ${b.variance} ${esc(curr)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="stock:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function buildDeterministicAiReply(promptText) {
  const stmt = renderStatementsAndRatios();
  const invBook = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const invRepl = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice || it.avgCost), 0));
  const latestSnap = state.costSnapshots[state.costSnapshots.length - 1];
  const latestInf = state.inflationRecords[0];
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");

  if (state.isArabic) {
    return [
      `تحليل مالي مباشر استناداً إلى سجلات منشأتك (${state.activeOrg?.name || "غير محددة"}):`,
      `• الإيرادات: ${stmt.revenue} ${curr} | مجمل الربح: ${stmt.grossProfit} ${curr} | صافي الربح: ${stmt.netProfit} ${curr}`,
      `• النقدية المتاحة: ${stmt.cashAndBank} ${curr} | المخزون الدفتري: ${invBook} ${curr} (قيمة الاستبدال الحالية: ${invRepl} ${curr})`,
      latestSnap ? `• أحدث تكلفة محسوبة (${latestSnap.productName}): تكلفة الوحدة ${latestSnap.coreResult.unitCost} ${curr}، الهامش ${latestSnap.coreResult.profitMarginPct ?? "غير متاح"}%، التقييم: ${latestSnap.risk.ruleName}` : `• لا توجد حسابات تكلفة محفوظة بعد — يمكنك حفظ لقطة من قسم التكاليف للتحليل الأدق.`,
      latestInf ? `• أحدث معدل تضخم مسجل: ${latestInf.monthlyRate}% للفترة ${latestInf.period}` : `• لا يوجد معدل تضخم مسجل بعد.`,
      ``,
      `بخصوص استفسارك: "${promptText}"`,
      invRepl > invBook
        ? `التوصية المحاسبية: فجوة الاستبدال تبلغ ${Engine.money(invRepl - invBook)} ${curr}؛ احرص على احتساب التسعير بناءً على تكلفة الاستبدال الحالية وليس التكلفة التاريخية حتى لا يتآكل رأس المال العامل.`
        : `التوصية المحاسبية: راقب تغيرات أسعار المدخلات دورياً واستخدم سيناريوهات ماذا لو لاختبار هامش الأمان قبل تثبيت أي عروض سعرية.`
    ].join("\n");
  }
  const arMetrics = [
    `تحليل مبني على بيانات مساحة العمل (${state.activeOrg?.name || "مساحة العمل"}):`,
    `• الإيرادات: ${stmt.revenue} ${curr} | مجمل الربح: ${stmt.grossProfit} ${curr} | صافي الربح: ${stmt.netProfit} ${curr}`,
    `• النقدية المتاحة: ${stmt.cashAndBank} ${curr} | المخزون الدفتري: ${invBook} ${curr} | تكلفة الاستبدال: ${invRepl} ${curr}`,
    latestSnap ? `• آخر لقطة تكلفة (${latestSnap.productName}): تكلفة الوحدة ${latestSnap.coreResult.unitCost} ${curr}، الهامش ${latestSnap.coreResult.profitMarginPct ?? "غير متاح"}%، التقييم: ${latestSnap.risk.ruleName}` : `• لا توجد لقطة تكلفة محفوظة بعد — أضف عملية في قسم التكاليف لتحليل نقطة التعادل بدقة.`,
    ``,
    `بخصوص استفسارك: "${promptText}"`,
    invRepl > invBook
      ? `التوصية: فجوة تكلفة الاستبدال الحالية تبلغ ${Engine.money(invRepl - invBook)} ${curr}. يُنصح بالتسعير وفق تكلفة الاستبدال وليس متوسط التكلفة التاريخية لحماية رأس المال العامل.`
      : `التوصية: استمر في مراقبة قفزات الأسعار (15% فأكثر) وإجراء محاكاة ماذا لو لصدمات المواد وسعر الصرف قبل تثبيت أسعار البيع.`
  ].join("\n");

  const enMetrics = [
    `Live Financial Analysis from your verified workspace (${state.activeOrg?.name || "Workspace"}):`,
    `• Revenue: ${stmt.revenue} ${curr} | Gross Profit: ${stmt.grossProfit} ${curr} | Net Profit: ${stmt.netProfit} ${curr}`,
    `• Cash: ${stmt.cashAndBank} ${curr} | Inventory Book: ${invBook} ${curr} (Replacement Value: ${invRepl} ${curr})`,
    latestSnap ? `• Latest Snapshot (${latestSnap.productName}): Unit Cost ${latestSnap.coreResult.unitCost} ${curr}, Margin ${latestSnap.coreResult.profitMarginPct ?? "Not Available"}%, Risk: ${latestSnap.risk.ruleName}` : `• No cost snapshot saved yet — save one under Costing for deeper break-even analysis.`,
    ``,
    `Regarding your question: "${promptText}"`,
    invRepl > invBook
      ? `Recommendation: Replacement capital gap is ${Engine.money(invRepl - invBook)} ${curr}. Price future orders using replacement cost rather than historical averages to preserve working capital.`
      : `Recommendation: Continue monitoring input spikes >= 15% and run What-If stress tests before locking sales quotes.`
  ].join("\n");

  if (!Engine.isAccountingOrFvgTopic(promptText)) {
    const qLower = String(promptText || "").toLowerCase();
    const isFlower = [
      "flower", "flowers", "rose", "roses", "orchid", "orchids", "tulip", "tulips",
      "jasmine", "sunflower", "lily", "lilies", "botany", "plant", "زهور", "ورد",
      "أزهار", "سوسن", "ياسمين", "توليب", "سحلب", "نرجس", "أوركيد", "نبات"
    ].some((k) => qLower.includes(k));

    if (isFlower) {
      if (state.isArabic) {
        return [
          "🌸 دليل أنواع الزهور وأشهر تصنيفاتها (إجابة المساعد الذكي FVG AI):",
          "",
          "1. التصنيف النباتي والبيولوجي لدورة حياة الزهور:",
          "• الزهور الحولية (Annuals): تكمل دورة حياتها وتزهر بغزارة في موسم نمو واحد، مثل البيتونيا (Petunia)، القطيفة (Marigold)، ودوار الشمس (Sunflower).",
          "• الزهور المعمرة (Perennials): تعيش لأكثر من عامين وتزهر موسمياً متكرراً مثل الورد الجوري (Garden Roses)، اللافندر (Lavender)، والأقحوان (Chrysanthemums).",
          "• الزهور ثنائية الحول (Biennials): تحتاج لموسمين لإكمال دورة حياتها (أوراق في الموسم الأول وأزهار وبذور في الثاني) مثل البنفسج وثعلب الغاب (Foxglove).",
          "• الزهور البصيلية والدرنية (Bulbs & Tubers): تنمو من أبصال تخزن الغذاء مثل التوليب (Tulip)، الزنبق (Lily)، والنرجس (Daffodil).",
          "",
          "2. أشهر العائلات وأصناف الزهور عالمياً:",
          "• الورد الجوري والدمشقي (Rosa): رمز العطور وصناعة ماء الورد والزيوت العطرية والهدايا.",
          "• السحلبيات أو الأوركيد (Orchids): فصيلة نادرة شديدة الرقة والتنوع الشكلي والمناخي.",
          "• الياسمين والفل (Jasmine): شجيرات عطرية ليلية الانتشار في الشرق الأوسط والبحر الأبيض المتوسط.",
          "",
          "3. الربط المحاسبي وإدارة تجارة الورود (FVG Costing & FEFO):",
          "• في تجارة الزهور ومحلات الورود، البضاعة سريعة التلف (Perishable) وتخضع محاسبياً لنظام الوارد أولاً يُصرف أولاً بالصلاحية (FEFO).",
          "• يجب احتساب نسبة هدر وتلف إجبارية (عادة 12% إلى 20%) وتكاليف التبريد وسلسلة الإمداد البارد (Cold-Chain) ضمن التكاليف غير المباشرة (FOH) لحماية هامش الربح."
        ].join("\n");
      }
      return [
        "🌸 Floral Classification & Botanical Guide (FVG AI Assistant):",
        "",
        "1. Botanical Lifecycle Classification:",
        "• Annual Flowers: Complete lifecycle in one growing season with intense blooming (e.g., Sunflowers, Petunias, Marigolds, Zinnias).",
        "• Perennial Flowers: Regrow every spring across several years (e.g., Garden Roses, Lavender, Peonies, Coneflowers).",
        "• Biennial Flowers: Take two biological years to bloom (e.g., Foxgloves, Sweet Williams).",
        "• Bulbous & Rhizomatous: Emerge from nutrient-storing underground bulbs (e.g., Tulips, Lilies, Daffodils, Dahlias).",
        "",
        "2. World's Most Renowned Floral Families:",
        "• Roses (Rosa): Cultivated for centuries for fragrances, essential oils, and premium floral gifting.",
        "• Orchids (Orchidaceae): Diverse, exotic varieties demanding controlled humidity and indirect light.",
        "• Lilies & Tulips: Highly prized ornamental spring bulbs grown globally in greenhouse agriculture.",
        "",
        "3. Managerial Costing & Perishable Inventory Economics (FVG Engine):",
        "• Cut-flower retail inventory is strictly governed by FEFO (First-Expired, First-Out) due to rapid biological wilting.",
        "• Floriculture businesses must absorb a 12% to 20% shrinkage/spoilage allowance alongside refrigerated storage costs into batch overhead before establishing consumer selling prices."
      ].join("\n");
    }

    if (state.isArabic) {
      return [
        `المساعد الذكي FVG AI:`,
        `بخصوص استفسارك عن "${promptText}":`,
        `يقدم دليل رؤيتك المالي إجابات ذكية شاملة وموثوقة لكافة الأسئلة العامة والمالية. إذا كنت تبحث عن استشارة إدارية أو تحليل تكلفة، يمكنك أيضاً استخدام حاسبات القطاعات المخصصة وسيناريوهات ماذا لو لمطابقة حساباتك بدقة.`
      ].join("\n");
    }
    return [
      `FVG AI Assistant:`,
      `Regarding your inquiry on "${promptText}":`,
      `Financial Vision Guide provides comprehensive responses across general knowledge and financial disciplines. For operational modeling, you can also leverage our dedicated Sector Calculators and What-If Decision Simulators.`
    ].join("\n");
  }

  return state.isArabic ? arMetrics : enMetrics;
}

function buildWebAiSystemPrompt(mode) {
  const isLoggedIn = state.stage === "app" && state.activeOrg;
  if (!isLoggedIn) {
    return [
      "You are the Financial Vision Guide (FVG — دليل رؤيتك المالي) AI Assistant founded by Zahra Yousif.",
      "You can answer ANY question — both open-world general knowledge topics (botany, science, history, business, daily life) AND FVG accounting, costing, pricing, and platform guidance.",
      "Format structured answers clearly using short headings, bullet points, and Markdown tables when comparing figures or options.",
      "Reply naturally, accurately, and politely in the user's language (Arabic or English). Never output raw internal errors or platform references."
    ].join(" ");
  }
  const stmt = renderStatementsAndRatios();
  const invBook = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const invRepl = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice || it.avgCost), 0));
  const missing = [];
  if (!state.priceObservations.length) missing.push("No price observations recorded yet");
  if (!state.costSnapshots.length) missing.push("No cost snapshots computed yet");
  if (!state.journalEntries.length) missing.push("No journal entries posted yet");
  if (!state.inventoryItems.length) missing.push("No inventory items registered yet");
  if (!state.fxRates.length) missing.push("No manual FX rates logged yet");

  return [
    `You are the FVG Smart AI Assistant & Personal Financial Supervisor (${mode} mode) founded by Zahra Yousif.`,
    "You answer BOTH open-world general questions (any topic) AND specialized accounting/costing advice grounded in the user's live workspace.",
    `Active Workspace "${state.activeOrg?.name || ""}" (${state.activeOrg?.baseCurrency || "SDG"}):`,
    `Revenue=${stmt.revenue}, GrossProfit=${stmt.grossProfit}, NetProfit=${stmt.netProfit}, Cash=${stmt.cashAndBank}, InventoryBook=${invBook}, ReplacementValue=${invRepl}, Products=${state.products.length}, CostSnapshots=${state.costSnapshots.length}, Journals=${state.journalEntries.length}.`,
    `Supervisor Readiness Gaps: ${missing.length ? missing.join("; ") : "All core modules populated!"}`,
    "When relevant, proactively suggest the user's best next operational step. Format structured numbers or comparisons cleanly using Markdown headings, bullet lists, and tables. Answer politely in the user's language."
  ].join(" ");
}

async function askSmartGeminiOrHonestLocal(promptText, mode = "general", onPartial = null, isPreLogin = false) {
  const cleanPrompt = String(promptText || "").trim();
  if (!cleanPrompt) return "";
  state.aiAbortRequested = false;
  if (isPreLogin) { state.preLoginAiQueries = []; state.preLoginAiSources = []; }
  else { state.aiSearchQueries = []; state.aiGroundingSources = []; }

  if (isFirebaseWebConfigured()) {
    try {
      const { app, fbAi } = await getFirebaseHandles();
      if (fbAi?.getAI && fbAi?.getGenerativeModel) {
        const ai = fbAi.getAI(app, { backend: new fbAi.GoogleAIBackend() });
        const modelsToTry = ["gemini-3.8-flash", "gemini-3.5-flash-lite"];
        let success = false;
        let lastErr = null;

        for (const modelName of modelsToTry) {
          if (state.aiAbortRequested) break;
          try {
            const model = fbAi.getGenerativeModel(ai, {
              model: modelName,
              tools: [{ googleSearch: {} }],
              systemInstruction: buildWebAiSystemPrompt(mode)
            });
            const streamRes = await model.generateContentStream(cleanPrompt);
            let fullText = "";
            for await (const chunk of streamRes.stream) {
              if (state.aiAbortRequested) break;
              const part = typeof chunk.text === "function" ? chunk.text() : "";
              if (part) {
                fullText += part;
                if (onPartial) onPartial(fullText);
              }
            }
            try {
              const resp = await streamRes.response;
              const gm = resp?.candidates?.[0]?.groundingMetadata;
              const queries = Array.isArray(gm?.webSearchQueries) ? gm.webSearchQueries.filter(Boolean) : [];
              const rawChunks = Array.isArray(gm?.groundingChunks) ? gm.groundingChunks : [];
              const seen = new Set();
              const sources = [];
              rawChunks.forEach((c) => {
                const uri = c?.web?.uri;
                if (uri && !seen.has(uri)) {
                  seen.add(uri);
                  sources.push({ title: c?.web?.title || uri, uri });
                }
              });
              if (isPreLogin) { state.preLoginAiQueries = queries; state.preLoginAiSources = sources; }
              else { state.aiSearchQueries = queries; state.aiGroundingSources = sources; }
            } catch {}
            if (fullText.trim()) {
              return fullText.trim();
            }
            success = true;
            break;
          } catch (modelErr) {
            lastErr = modelErr;
          }
        }
      }
    } catch (err) {}
  }
  return buildDeterministicAiReply(cleanPrompt);
}

function renderReportsAiTab() {
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  const ent = getEntitlements();
  const sub = state.subTab.reports;
  const subLabels = [t("Reports & Export", "التقارير والتصدير"), t("Financial Assistant & Camera", "المساعد المالي والكاميرا"), t("Org & Billing", "المنظمة والدفع")];
  const stmt = renderStatementsAndRatios();
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Verified Financial Reports & Exports", "التقارير المالية والتصدير")}</h3>${helpBtn("ledger_je")}</div>
        <div class="field-group" style="margin-top:8px;">
          <label>${t("Report Dataset", "التقرير المطلوب")}</label>
          <select id="repSelect">
            <option value="statements">${t("Financial Statements", "القوائم المالية")}</option>
            <option value="prices">${t("Prices History", "سجل الأسعار")}</option>
            <option value="costing">${t("8-Method Costing Snapshots", "ملخص طرق التكلفة")}</option>
            <option value="ratios">${t("18 Financial Ratios", "النسب المالية الـ 18")}</option>
          </select>
        </div>
        <div class="grid-3">
          <button class="btn btn-primary" data-action="exportCsv">${t("Export CSV Table", "تصدير جدول نصي")}</button>
          <button class="btn btn-gold" ${!ent.allowAuditAndBackup ? "disabled" : ""} data-action="exportJson">${!ent.allowAuditAndBackup ? "🔒 " : ""}${t("Export Workspace Backup", "تصدير نسخة احتياطية")}</button>
          <button class="btn btn-outline" ${!ent.allowPdfXlsxExport ? "disabled" : ""} data-action="printPdf">${!ent.allowPdfXlsxExport ? "🔒 " : ""}${t("Print / Save Document", "طباعة أو حفظ مستند")}</button>
        </div>
        ${!ent.allowPdfXlsxExport || !ent.allowAuditAndBackup ? `<div style="font-size:0.8rem;color:var(--fvg-warning);margin-top:8px;">🔒 ${t("Upgrade to Professional for PDF/Print or Enterprise for full JSON Backup.", "تصدير المستندات يتطلب الاحترافية، والنسخ الاحتياطي يتطلب باقة المؤسسات.")}</div>` : ""}
      </div>
      <div class="card cream">
        <div class="row-between"><strong>${t("Net Profit", "صافي الربح")}: ${stmt.netProfit} ${esc(curr)}</strong><strong>${t("Total Assets", "إجمالي الأصول")}: ${stmt.totalAssets} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">🏢 ${t(`Multi-Branch Consolidation (${state.organizations.length} Branches)`, `التقارير الموحّدة متعددة الفروع (${state.organizations.length} فروع)`)}</h3><span class="badge">${t("Enterprise", "المؤسسات")}</span></div>
        ${!ent.allowAuditAndBackup ? `<div style="font-size:0.8rem;color:var(--fvg-warning);margin-top:6px;">🔒 ${t("Multi-Branch Consolidation & Inter-company Eliminations require Trial or Enterprise tier.", "مقارنة الفروع وإلغاء المعاملات البينية متاحان لمشتركي باقة المؤسسات والتجربة.")}</div>` : `
          <div class="grid-2" style="margin-top:8px;">
            <div class="field-group"><label>${t("Eliminate Inter-Co Rev/COGS", "استبعاد مبيعات/تكلفة بينية")}</label><input id="elimRevCost" type="number" value="0"/></div>
            <div class="field-group"><label>${t("Eliminate Inter-Co AR/AP", "استبعاد ذمم بينية")}</label><input id="elimArAp" type="number" value="0"/></div>
          </div>
          <button class="btn btn-primary" data-action="runMultiBranch">${t("Consolidate All Branches", "توحيد ومقارنة جميع الفروع")}</button>
          ${state.branchConsolidation ? `
            <div class="card cream" style="margin-top:10px;">
              <strong>${t("Consolidated Revenue:", "الإيراد الموحّد:")} ${state.branchConsolidation.consolidatedRevenue} ${esc(curr)} | ${t("Net Profit:", "صافي الربح:")} ${state.branchConsolidation.consolidatedNetProfit} ${esc(curr)} (${state.branchConsolidation.consolidatedMarginPct}%)</strong>
              <div style="font-size:0.8rem;color:var(--fvg-text-muted);margin-top:4px;">${t("Scrap Loss:", "إجمالي الهدر:")} ${state.branchConsolidation.totalScrapLoss} | ${t("Eliminated Inter-Co:", "مستبعدات بينية:")} ${state.branchConsolidation.intercompanyEliminated}</div>
              <div style="font-size:0.82rem;font-weight:700;color:var(--fvg-antique-gold);margin-top:4px;">${t("Best Margin:", "أعلى هامش:")} ${esc(state.branchConsolidation.bestMarginBranch || "-")} • ${t("Highest Waste:", "أعلى هدر:")} ${esc(state.branchConsolidation.highestWasteBranch || "-")}</div>
              <div class="table-wrap" style="margin-top:8px;">
                <table><thead><tr><th>${t("Branch", "الفرع")}</th><th>${t("Revenue", "الإيراد")}</th><th>${t("Net Profit", "الربح")}</th><th>${t("Margin %", "الهامش %")}</th><th>${t("Waste %", "الهدر %")}</th></tr></thead>
                <tbody>${state.branchConsolidation.rows.map((r) => `<tr><td>${esc(r.branchName)}</td><td>${r.revenue}</td><td>${r.netProfit}</td><td>${r.profitMarginPct}%</td><td>${r.scrapPctOfRevenue}%</td></tr>`).join("")}</tbody></table>
              </div>
            </div>
          ` : ""}
        `}
      </div>
    `;
  } else if (sub === 1) {
    const promptChips = state.isArabic
      ? [
          "حلل هوامش الربح الحالية ونقطة التعادل",
          "ما تأثير التضخم على تكلفة الاستبدال؟",
          "لخّص الميزانية العمومية والنسب المالية",
          "كيف أحسّن التدفق النقدي من المخزون؟"
        ]
      : [
          "Analyze profit margins and break-even units",
          "How is inflation affecting replacement cost?",
          "Summarize balance sheet and key ratios",
          "How do I protect inventory working capital?"
        ];
    panel = `
      ${!ent.allowAiAssistant && state.aiMode !== "general" ? renderPlanGateBanner("المساعد المالي التخصصي وتحليل الفواتير", "Financial & OCR AI Analysis (Open Q&A remains unlocked)") : `
        <div class="card">
          <div class="row-between">
            <div style="display:flex;align-items:center;gap:6px;"><h3 style="margin:0;">${t("Smart AI Assistant (General & Financial)", "المساعد الذكي التفاعلي (عام ومحاسبي)")}</h3>${helpBtn("ai_ocr")}</div>
            <div style="display:flex;gap:6px;">
              <button class="btn btn-outline" style="min-height:38px;padding:6px 12px;" data-action="newConversation">+ ${t("New Chat", "محادثة جديدة")}</button>
            </div>
          </div>
          ${state.aiConversations.length ? `
            <div class="subtabs" style="margin-top:8px;">
              ${state.aiConversations.map((c) => `
                <span style="display:inline-flex;align-items:center;gap:4px;">
                  <button class="subtab ${state.activeConversationId === c.id ? "active" : ""}" data-select-conv="${esc(c.id)}">${esc(c.title)}</button>
                  <button class="subtab" style="padding:4px 8px;" data-del-conv="${esc(c.id)}" title="${t("Delete Chat", "حذف المحادثة")}">✕</button>
                </span>
              `).join("")}
            </div>
          ` : ""}
          <div class="subtabs" style="margin-top:8px;">
            <button class="subtab ${state.aiMode === "general" ? "active" : ""}" data-ai-mode="general">${t("General Q&A (Any Topic)", "أسئلة عامة ومفتوحة")}</button>
            <button class="subtab ${state.aiMode === "financial" ? "active" : ""}" data-ai-mode="financial">${!ent.allowAiAssistant ? "🔒 " : ""}${t("Financial Analyst", "تحليل مالي")}</button>
            <button class="subtab ${state.aiMode === "ocr" ? "active" : ""}" data-ai-mode="ocr">${!ent.allowAiAssistant ? "🔒 " : ""}${t("Invoice Review", "مراجعة الفواتير")}</button>
          </div>
          <div class="subtabs" style="margin-top:4px;">
            ${promptChips.map((ch) => `<button class="subtab" data-ai-chip="${esc(ch)}">💡 ${esc(ch)}</button>`).join("")}
          </div>
          ${state.chatTranscript.length ? `
            <div class="fvg-chat-thread">
              ${state.chatTranscript.map((m, idx) => {
                const isUser = String(m.role).toLowerCase().startsWith("user") || String(m.role).startsWith("المستخدم");
                return `
                  <div class="fvg-chat-bubble ${isUser ? "user" : "assistant"}">
                    ${isUser ? `<div>${esc(m.text)}</div>` : formatAiMarkdownHtml(m.text)}
                    ${!isUser ? `
                      <div style="display:flex;gap:6px;margin-top:6px;">
                        <button class="btn btn-outline" style="min-height:30px;padding:2px 10px;font-size:0.75rem;" data-copy-msg="${idx}">📋 ${t("Copy", "نسخ")}</button>
                        ${idx === state.chatTranscript.length - 1 ? `<button class="btn btn-outline" style="min-height:30px;padding:2px 10px;font-size:0.75rem;" data-action="regenAi">↻ ${t("Regenerate", "إعادة الصياغة")}</button>` : ""}
                      </div>
                    ` : ""}
                  </div>
                `;
              }).join("")}
            </div>
          ` : `<p style="font-size:0.84rem;color:var(--fvg-text-muted);">${t("Ask ANY outside general question or analyze your workspace finances below. Conversations are saved automatically.", "اسأل أي سؤال عام داخل أو خارج المنصة أو اطلب من المشرف الذكي متابعة خطوات عملك أدناه. تحفظ المحادثات تلقائياً.")}</p>`}
          ${state.aiSearchQueries.length ? `<div style="font-size:0.75rem;color:var(--fvg-text-muted);margin-top:6px;">🔎 ${esc(state.aiSearchQueries.join(" • "))}</div>` : ""}
          ${state.aiGroundingSources.length ? `<div class="fvg-source-row">${state.aiGroundingSources.map((s) => `<a class="fvg-source-chip" href="${esc(s.uri)}" target="_blank" rel="noopener">🌐 ${esc(s.title)}</a>`).join("")}</div>` : ""}
          <div class="field-group" style="margin-top:8px;"><label>${t("Ask AI Assistant & Supervisor", "اكتب سؤالك للمساعد والمشرف الذكي")}</label><textarea id="aiPrompt" rows="2" placeholder="${t("Ask anything (e.g., types of flowers, break-even analysis, next step in workspace)...", "اكتب أي سؤال (مثال: تحليل الهامش، أنواع الزهور، ماذا أفعل الآن في مشروعي؟)...")}"></textarea></div>
          <div class="grid-3">
            <button class="btn btn-primary" ${state.isAiLoading ? "disabled" : ""} data-action="askAi">${state.isAiLoading ? t("Generating...", "جاري الرد...") : t("Send & Analyze", "إرسال وتحليل")}</button>
            ${state.isAiLoading ? `<button class="btn btn-danger" data-action="stopAi">${t("Stop", "إيقاف")}</button>` : `<button class="btn btn-outline" data-action="exportTranscript">${t("Export Transcript", "تصدير المحادثة")}</button>`}
          </div>
        </div>
      `}
      ${!ent.allowOcr ? renderPlanGateBanner("تصوير الفواتير بالكاميرا والتحقق الحسابي", "Camera Invoice Capture & OCR") : `
        <div class="card cream">
          <div class="row-between"><h3 style="margin:0;">${t("Camera Invoice Capture & Review", "تصوير الفواتير بالكاميرا والمراجعة")}</h3>${helpBtn("ai_ocr")}</div>
          <p style="font-size:0.82rem;color:var(--fvg-text-muted);margin-top:6px;">${t("Capture invoices directly via phone camera, rotate 90° if needed, validate math consistency and approve drafts.", "صوّر الفاتورة بالكاميرا مباشرة، قم بتدويرها 90 درجة عند الحاجة، ثم راجع المجاميع وتحقق من تطابقها.")}</p>
          <div class="grid-2">
            <label class="btn btn-gold" style="cursor:pointer;">
              📷 ${t("Capture Invoice via Camera", "تصوير الفاتورة بالكاميرا")}
              <input id="ocrCameraInput" type="file" accept="image/*" capture="environment" style="display:none;" />
            </label>
          </div>
          ${state.pendingOcrDataUrl ? `
            <div style="margin-top:10px;text-align:center;">
              <img src="${state.pendingOcrDataUrl}" style="max-width:100%;max-height:220px;border-radius:12px;border:1px solid var(--fvg-border);" alt="معاينة الفاتورة"/>
              <div class="grid-2" style="margin-top:8px;">
                <button class="btn btn-outline" data-action="rotateOcr">↻ ${t("Rotate 90°", "تدوير 90 درجة")}</button>
                <button class="btn btn-primary" data-action="createOcrDraft">${t("Create Invoice Review Draft", "إنشاء مسودة مراجعة")}</button>
              </div>
            </div>
          ` : ""}
        </div>
        ${state.ocrDrafts.slice().reverse().map((d) => {
          const math = Engine.validateOcrArithmetic(d.subtotal, d.discount, d.tax, d.total);
          return `
            <div class="card">
              <div class="row-between"><strong>${esc(d.vendorName || t("Invoice Draft", "مسودة فاتورة"))} (#${esc(d.invoiceNumber || "---")})</strong><span class="badge">${esc(localizeStatus(d.reviewStatus))}</span></div>
              ${!math.isConsistent ? `<div style="color:var(--fvg-negative);font-size:0.8rem;">⚠ ${t("Math mismatch: expected", "عدم تطابق حسابي: المتوقع")} ${math.expectedTotal} ≠ ${math.extractedTotal}</div>` : ""}
              <div class="grid-3" style="margin-top:6px;">
                <div class="field-group"><label>${t("Vendor", "المورد")}</label><input data-ocr-field="vendor:${esc(d.id)}" value="${esc(d.vendorName)}"/></div>
                <div class="field-group"><label>${t("Invoice #", "رقم الفاتورة")}</label><input data-ocr-field="inv:${esc(d.id)}" value="${esc(d.invoiceNumber)}"/></div>
                <div class="field-group"><label>${t("Subtotal", "الفرعي")}</label><input type="number" step="0.01" data-ocr-field="sub:${esc(d.id)}" value="${esc(d.subtotal)}"/></div>
                <div class="field-group"><label>${t("Discount", "الخصم")}</label><input type="number" step="0.01" data-ocr-field="disc:${esc(d.id)}" value="${esc(d.discount)}"/></div>
                <div class="field-group"><label>${t("Tax", "الضريبة")}</label><input type="number" step="0.01" data-ocr-field="tax:${esc(d.id)}" value="${esc(d.tax)}"/></div>
                <div class="field-group"><label>${t("Final Total", "الإجمالي النهائي")}</label><input type="number" step="0.01" data-ocr-field="tot:${esc(d.id)}" value="${esc(d.total)}"/></div>
              </div>
              ${d.reviewStatus === "pending_review" ? `<button class="btn btn-primary" data-verify-ocr="${esc(d.id)}">${t("Verify & Save Draft", "اعتماد المسودة")}</button>` : ""}
            </div>
          `;
        }).join("")}
      `}
    `;
  } else {
    panel = `
      ${renderTrialBanner(false)}
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Available Plans & Features", "الباقات المتاحة ومميزات كل باقة")}</h3>${helpBtn("billing")}</div>
        <div style="margin-top:8px;">${renderPlansCards()}</div>
      </div>
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Submit Payment Proof (Bankak / Fawry / O-Cash)", "تقديم إثبات الدفع (بنكك / فوري / أوكاش)")}</h3>
        <p style="font-size:0.82rem;color:var(--fvg-text-muted);">${t("Scan a Bankak/Fawry transfer receipt photo or paste the transfer SMS notification to auto-extract and verify against plan fees before sending to Owner Zahra.", "صوّر إشعار بنكك/فوري أو ألصق نص الإشعار ليتم استخراج المبلغ والمرجع ومطابقتهما تلقائياً قبل الإرسال للمالكة زهراء يوسف.")}</p>
        <div class="grid-2" style="margin-bottom:8px;">
          <label class="btn btn-outline" style="cursor:pointer;">
            📷 ${t("Scan Receipt Image", "مسح صورة الإيصال")}
            <input id="receiptImgInput" type="file" accept="image/*" capture="environment" style="display:none;" />
          </label>
          <button type="button" class="btn btn-primary" data-action="parsePaySms">💬 ${t("Extract SMS Below", "تحليل ومطابقة الإشعار النصي")}</button>
        </div>
        <div class="field-group"><label>${t("Paste Bankak / Fawry SMS Notice (Optional)", "لصق نص إشعار بنكك أو فوري (اختياري)")}</label><textarea id="paySmsInput" rows="2" placeholder="${t("Paste transfer SMS here...", "ألصق نص إشعار التحويل هنا...")}">${esc(state.paySmsText)}</textarea></div>
        <div class="grid-3">
          <div class="field-group"><label>${t("Plan", "الباقة")}</label><select id="payPlan">${state.platformPlans.map((p) => `<option value="${esc(p.key || p.code)}">${esc(state.isArabic ? p.nameAr : p.nameEn)} ($${esc(p.priceUsd)} / ${esc(p.priceSdg)} ${localizeCurrency("SDG")})</option>`).join("")}</select></div>
          <div class="field-group"><label>${t("Method", "الوسيلة")}</label><select id="payMethod"><option value="bankak">${t("Bankak", "بنكك")}</option><option value="fawry">${t("Fawry", "فوري")}</option><option value="ocash">${t("O-Cash", "أوكاش")}</option><option value="bank_transfer">${t("Bank Transfer", "تحويل بنكي")}</option></select></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")}</label><input id="payAmt" type="number" value="29"/></div>
          <div class="field-group"><label>${t("Sender Name", "اسم المحوّل")}</label><input id="paySender" placeholder="${t("Full Name", "الاسم الكامل")}"/></div>
          <div class="field-group"><label>${t("Reference No", "رقم المرجع")}</label><input id="payRef" placeholder="${t("BNK-2026-01", "مرجع-2026-01")}"/></div>
          <div class="field-group"><label>${t("Last 4 Digits", "آخر 4 أرقام للمعاملة")}</label><input id="payLast4" maxlength="4" placeholder="1234"/></div>
        </div>
        <div class="field-group"><label>${t("User Name & Comment", "تعليق باسم المستخدم + تم")}</label><input id="payNotes" placeholder="${t("Name + Transfer Completed", "الاسم الكامل - تم التحويل")}"/></div>
        ${state.receiptMatchPreview ? `
          <div class="fvg-match-badge ${state.receiptMatchPreview.status === "exact_match" ? "exact" : (state.receiptMatchPreview.status === "close_match" ? "close" : "warn")}">
            ${esc(state.isArabic ? state.receiptMatchPreview.noteAr : state.receiptMatchPreview.noteEn)}
          </div>
        ` : ""}
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          <button class="btn btn-gold" data-action="submitPayment">${t("Submit Payment Proof", "إرسال إثبات الدفع للموافقة")}</button>
          <a class="btn btn-outline" href="https://wa.me/249911569966" target="_blank" rel="noopener">💬 ${t("Notify Zahra on WhatsApp", "إشعار زهراء عبر واتساب")}</a>
          <a class="btn btn-outline" href="mailto:${esc(Engine.OWNER_EMAIL)}?subject=Payment%20Proof">✉ ${t("Notify via Email", "إشعار عبر البريد")}</a>
        </div>
      </div>
      ${state.paymentRequests.map((p) => `
        <div class="card">
          <div class="row-between"><strong>${esc(p.paymentMethod)} • ${esc(p.referenceNo)}</strong><span class="badge">${esc(localizeStatus(p.status))}</span></div>
          <div>${esc(p.planTier)} • ${p.amount} • ${esc(p.senderName)} ${p.last4Digits ? `• ****${esc(p.last4Digits)}` : ""}</div>
          ${p.receiptMatchNote ? `<div class="fvg-match-badge ${p.receiptMatchStatus === "exact_match" ? "exact" : (p.receiptMatchStatus === "close_match" ? "close" : "warn")}">${esc(p.receiptMatchNote)}</div>` : ""}
          ${p.reviewNotes ? `<div style="font-size:0.8rem;color:var(--fvg-warning);">${esc(p.reviewNotes)}</div>` : ""}
        </div>
      `).join("")}
      <div class="card">
        <h3 style="margin-top:0;">${t("Install App on iPhone, iPad, Android & Laptop", "تثبيت التطبيق على أيفون والآيباد وأندرويد واللابتوب")}</h3>
        <p style="font-size:0.85rem;margin:0 0 10px;">${t("On iPhone/iPad Safari: Tap Share -> 'Add to Home Screen'. On Android/Laptop Chrome or Edge: Tap Install App below.", "في أيفون/آيباد (سفاري): اضغط زر المشاركة ← 'إضافة إلى الشاشة الرئيسية'. في أندرويد واللابتوب: اضغط تثبيت التطبيق أدناه.")}</p>
        <div class="grid-3">
          <button class="btn btn-gold" data-action="triggerPwaInstall">📲 ${t("Install App", "تثبيت التطبيق")}</button>
          <button class="btn btn-outline" data-action="openProfile">👤 ${t("Profile", "الملف الشخصي")}</button>
          <button class="btn btn-danger" data-action="signOut">${t("Sign Out", "تسجيل الخروج")}</button>
        </div>
      </div>
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="reports:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderProfileView() {
  const u = state.user || {};
  return `
    <div class="card">
      <div class="row-between">
        <h2 style="margin:0;">${t("My Profile & Preferences", "ملفي الشخصي والتفضيلات")}</h2>
        <button class="btn btn-outline" data-action="closeSecondary">← ${t("Back to Home", "العودة للرئيسية")}</button>
      </div>
    </div>
    <div class="card cream" style="display:flex;align-items:center;gap:14px;flex-wrap:wrap;">
      ${u.avatarDataUrl ? `<img class="avatar-circle" src="${u.avatarDataUrl}" alt="الصورة الشخصية"/>` : `<div class="avatar-circle">${esc((u.displayName || u.email || "د").slice(0, 2).toUpperCase())}</div>`}
      <div style="flex:1;min-width:200px;">
        <strong>${esc(u.displayName || t("Platform User", "مستخدم المنصة"))}</strong>
        <div style="font-size:0.82rem;color:var(--fvg-text-muted);">${esc(u.email || "")}</div>
        <div style="display:flex;gap:8px;margin-top:8px;flex-wrap:wrap;">
          <label class="btn btn-gold" style="min-height:40px;cursor:pointer;">
            📷 ${t("Upload / Take Photo", "رفع أو التقاط صورة")}
            <input id="avatarFileInput" type="file" accept="image/*" style="display:none;" />
          </label>
          ${u.avatarDataUrl ? `<button class="btn btn-outline" style="min-height:40px;" data-action="removeAvatar">${t("Remove Photo", "حذف الصورة")}</button>` : ""}
        </div>
      </div>
    </div>
    <div class="card">
      <div class="grid-2">
        <div class="field-group"><label>${t("Display Name", "الاسم الظاهر")}</label><input id="profName" value="${esc(u.displayName || "")}"/></div>
        <div class="field-group"><label>${t("Workspace Name", "اسم المنظمة / النشاط")}</label><input id="profOrgName" value="${esc(state.activeOrg?.name || "")}"/></div>
        <div class="field-group"><label>${t("Default Currency", "العملة الافتراضية")}</label><select id="profCurr"><option value="SDG" ${(state.activeOrg?.baseCurrency || "SDG") === "SDG" ? "selected" : ""}>${t("SDG (Sudanese Pound)", "جنيه سوداني")}</option><option value="USD" ${state.activeOrg?.baseCurrency === "USD" ? "selected" : ""}>${t("USD (US Dollar)", "دولار أمريكي")}</option><option value="SAR" ${state.activeOrg?.baseCurrency === "SAR" ? "selected" : ""}>${t("SAR (Saudi Riyal)", "ريال سعودي")}</option><option value="AED" ${state.activeOrg?.baseCurrency === "AED" ? "selected" : ""}>${t("AED (UAE Dirham)", "درهم إماراتي")}</option><option value="EGP" ${state.activeOrg?.baseCurrency === "EGP" ? "selected" : ""}>${t("EGP (Egyptian Pound)", "جنيه مصري")}</option></select></div>
        <div class="field-group"><label>${t("Language", "اللغة")}</label><select id="profLang"><option value="ar" ${state.isArabic ? "selected" : ""}>العربية (كامل الواجهة بالعربي)</option><option value="en" ${!state.isArabic ? "selected" : ""}>English</option></select></div>
        <div class="field-group"><label>${t("Theme Mode", "مظهر التطبيق")}</label><select id="profTheme"><option value="light" ${state.themeMode === "light" ? "selected" : ""}>${t("Light Mode", "الوضع الفاتح")}</option><option value="dark" ${state.themeMode === "dark" ? "selected" : ""}>${t("Dark Mode", "الوضع الليلي")}</option></select></div>
        <div class="field-group"><label>${t("Font Size Scale", "حجم الخط")}: ${Math.round(state.fontScale * 100)}%</label><input id="profFontScale" type="range" min="0.85" max="1.30" step="0.05" value="${state.fontScale}"/></div>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:8px;">
        <button class="btn btn-primary" data-action="saveProfile">${t("Save Profile Preferences", "حفظ تفضيلات الملف الشخصي")}</button>
        <button class="btn btn-danger" data-action="signOut">${t("Sign Out", "تسجيل الخروج")}</button>
      </div>
    </div>
  `;
}

function renderHelpView() {
  return `
    <div class="card">
      <div class="row-between">
        <h2 style="margin:0;">${t("Help, Methodology & Support", "المساعدة والدليل والتواصل")}</h2>
        <button class="btn btn-outline" data-action="closeSecondary">← ${t("Back to Home", "العودة للرئيسية")}</button>
      </div>
    </div>
    ${renderFounderCard(false)}
    <div class="card cream">
      <h3 style="margin-top:0;">${t("Interactive Walkthrough Tour", "الجولة التعريفية التفاعلية")}</h3>
      <p style="font-size:0.86rem;">${t("Replay the guided spotlight walkthrough starting with Arabic/English language selection and explaining every tool step by step.", "أعد تشغيل الجولة الإرشادية بدءاً باختيار اللغة العربية أو الإنجليزية وتوضيح كل قسم خطوة بخطوة.")}</p>
      <button class="btn btn-gold" data-action="replayTour">🧭 ${t("Start Interactive Tour", "تشغيل الجولة التعريفية")}</button>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Core Accounting & Costing Formulas", "ملخص المعادلات المحاسبية المعتمدة")}</h3>
      <p style="font-size:0.85rem;">• ${t("Profit Margin % = ((Selling Price − Unit Cost) / Selling Price) × 100", "هامش الربح % = ((سعر البيع − تكلفة الوحدة) / سعر البيع) × 100")}</p>
      <p style="font-size:0.85rem;">• ${t("Markup % = ((Selling Price − Unit Cost) / Unit Cost) × 100", "نسبة الإضافة % = ((سعر البيع − تكلفة الوحدة) / تكلفة الوحدة) × 100")}</p>
      <p style="font-size:0.85rem;">• ${t("Break-Even Units = CEILING(Fixed Costs / (Price − Variable Cost/Unit))", "نقطة التعادل = تقريب للأعلى(التكاليف الثابتة / (السعر − التكلفة المتغيرة للوحدة))")}</p>
      <p style="font-size:0.85rem;">• ${t("Compounded Inflation = Π(1 + rᵢ/100) − 1", "التضخم المركب = حاصل ضرب (1 + معدل الشهر) − 1")}</p>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Send Support Request", "إرسال تذكرة دعم أو استفسار")}</h3>
      <div class="field-group"><label>${t("Message to Owner Zahra Yousif", "نص الرسالة للمالكة زهراء يوسف")}</label><textarea id="helpMsg" rows="3" placeholder="${t("Describe your question or issue...", "اكتب استفسارك هنا...")}"></textarea></div>
      <button class="btn btn-primary" data-action="submitTicket">${t("Send Support Request", "إرسال الطلب")}</button>
      ${state.supportTickets.map((tk) => `<div class="card cream" style="margin-top:8px;"><div class="row-between"><strong>${esc(tk.createdAt)}</strong><span class="badge">${esc(localizeStatus(tk.status))}</span></div><div>${esc(tk.message)}</div>${tk.ownerReply ? `<div style="color:var(--fvg-positive);margin-top:4px;">↳ ${esc(tk.ownerReply)}</div>` : ""}</div>`).join("")}
    </div>
  `;
}

function renderWelcomePopupModal() {
  if (!state.showWelcomePopup || isPlatformOwner()) return "";
  const resolvedName = state.user?.displayName || state.user?.email || t("Friend", "ضيفنا الكريم");
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card">
        <div class="row-between">
          <span class="badge low">✨ ${t("Welcome", "أهلاً بك")}</span>
          <button type="button" class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeWelcomePopup">✕</button>
        </div>
        <h2 style="margin:12px 0 6px;">${t(`Welcome, ${resolvedName}!`, `مرحباً بك يا ${resolvedName}!`)}</h2>
        <p style="line-height:1.7;color:var(--fvg-text-muted);margin:0 0 14px;">
          ${t("Your Financial Vision Guide workspace is active with a full 7-day trial. Take a quick guided tour to explore every module.", "تم تفعيل مساحة عملك في دليل رؤيتك المالي بنجاح. يمكنك بدء الجولة التعريفية السريعة لاستكشاف كافة الأدوات.")}
        </p>
        <div style="display:flex;gap:8px;">
          <button type="button" class="btn btn-gold" style="flex:1;" data-action="startTourFromWelcome">🧭 ${t("Start Guided Tour", "بدء الجولة التعريفية")}</button>
          <button type="button" class="btn btn-outline" data-action="closeWelcomePopup">${t("Later", "لاحقاً")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderAdminLoginModal() {
  if (!state.showAdminModal) return "";
  const pendingSetup = isWebAdminEmailPendingSetup(state.adminModalEmailDraft);
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card">
        <div class="row-between">
          <strong>🛡 ${pendingSetup ? t("One-Time Admin Initial Setup (/admin)", "الإعداد الأمني الأولي للإدارة (/admin)") : t("Executive Admin Login (/admin)", "دخول لوحة الإدارة التنفيذية (/admin)")}</strong>
          <button type="button" class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeAdminModal">✕</button>
        </div>
        ${pendingSetup ? renderStatusSignalBanner("yellow", t("First-Time Setup Required", "مطلوب إعداد كلمة مرور الإدارة لمرة واحدة"), t("No default password exists. Choose your permanent strong admin password below (8+ characters, letter + number/symbol).", "لا توجد كلمة مرور افتراضية مسبقة. حددي كلمة مرور إدارية دائمة وقوية الآن لإكمال الإعداد لمرة واحدة.")) : `
          <p style="font-size:0.84rem;color:var(--fvg-text-muted);margin:8px 0 12px;">
            ${t("Enter your authorized administrator email and password manually.", "أدخلي البريد الإلكتروني الإداري وكلمة المرور يدوياً للوصول إلى لوحة التحكم.")}
          </p>
        `}
        <div class="field-group">
          <label>${t("Administrator Email", "بريد الإدارة")}</label>
          <input id="adminModalEmail" type="email" autocomplete="off" value="${esc(state.adminModalEmailDraft)}" placeholder="admin@domain.com" />
        </div>
        <div class="field-group">
          <label>${pendingSetup ? t("New Admin Password (8+ chars, letter + digit/symbol)", "كلمة المرور الإدارية الجديدة (8 أحرف + رقم/رمز)") : t("Admin Password", "كلمة المرور الإدارية")}</label>
          <div class="fvg-pass-wrap">
            <input id="adminModalPass" type="${state.adminModalPassVisible ? "text" : "password"}" autocomplete="${pendingSetup ? "new-password" : "current-password"}" value="" placeholder="••••••••" />
            <button type="button" class="fvg-pass-eye" data-action="toggleAdminModalPass" title="${state.adminModalPassVisible ? t("Hide Password", "إخفاء") : t("Show Password", "إظهار")}">${state.adminModalPassVisible ? "🙈" : "👁"}</button>
          </div>
        </div>
        ${!pendingSetup ? `
          <div style="display:flex;justify-content:flex-end;margin-bottom:8px;">
            <button type="button" class="btn btn-outline" style="font-size:0.78rem;padding:3px 10px;min-height:30px;color:#DDB065;border-color:rgba(221,176,101,0.5);" data-action="adminForgotPassword">
              🔑 ${t("Forgot Admin Password? Reset via Email", "نسيت كلمة المرور؟ استعادة عبر البريد الإداري")}
            </button>
          </div>
        ` : ""}
        ${pendingSetup ? `
          <div class="field-group">
            <label>${t("Confirm Admin Password", "تأكيد كلمة المرور الإدارية")}</label>
            <div class="fvg-pass-wrap">
              <input id="adminModalConfPass" type="${state.adminModalConfVisible ? "text" : "password"}" autocomplete="new-password" value="" placeholder="••••••••" />
              <button type="button" class="fvg-pass-eye" data-action="toggleAdminModalConf" title="${state.adminModalConfVisible ? t("Hide Password", "إخفاء") : t("Show Password", "إظهار")}">${state.adminModalConfVisible ? "🙈" : "👁"}</button>
            </div>
          </div>
        ` : ""}
        <div style="display:flex;gap:8px;margin-top:8px;">
          <button type="button" class="btn btn-primary" style="flex:1;" data-action="${pendingSetup ? "submitAdminInitialSetup" : "submitAdminLogin"}">
            ${pendingSetup ? t("Set Password & Open Admin", "حفظ كلمة المرور وفتح الإدارة") : t("Unlock Executive Dashboard", "فتح لوحة التحكم الإدارية")}
          </button>
          <button type="button" class="btn btn-outline" data-action="closeAdminModal">${t("Cancel", "إلغاء")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderWebOwnerOneTimeSetupGate() {
  return `
    <div class="fvg-admin-shell">
      <header class="fvg-admin-header">
        <div style="display:flex;align-items:center;gap:12px;">
          <img src="./assets/fvg-icon.svg" style="width:40px;height:40px;border-radius:10px;background:#FAF2E8;padding:3px;" alt="شعار الإدارة"/>
          <div>
            <div style="font-size:1.05rem;font-weight:800;">🛡 ${t("One-Time Admin Security Setup", "الإعداد الأمني الأولي للإدارة")}</div>
            <div style="font-size:0.75rem;color:#DDB065;">${t("Permanent Credentials Required Before Unlocking /admin", "يجب تعيين كلمة مرور إدارية دائمة قبل فتح لوحة التحكم")}</div>
          </div>
        </div>
        <div class="fvg-banner-actions">
          <button type="button" class="icon-pill" data-action="toggleLang">${state.isArabic ? "إنجليزي" : "عربي"}</button>
          <button type="button" class="icon-pill" data-action="signOut">${t("Sign Out", "خروج")}</button>
        </div>
      </header>
      <main class="fvg-admin-main" style="max-width:560px;margin:28px auto;">
        ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
        <div class="card">
          <h3 style="margin-top:0;">🔐 ${t("Establish Admin Credentials", "إعداد كلمة مرور لوحة الإدارة لمرة واحدة")}</h3>
          <p style="font-size:0.86rem;color:var(--fvg-text-muted);line-height:1.6;">
            ${t("No default admin password is shipped. Enter your authorized administrator email manually and choose a strong permanent password (8+ chars, letters + number/symbol).", "لا توجد كلمة مرور افتراضية مسبقة لحماية حسابك. أدخلي بريد الإدارة المعتمد يدوياً وحددي كلمة مرور دائمة وقوية (8 أحرف فأكثر تتضمن أحرفاً وأرقاماً/رموزاً).")}
          </p>
          <div class="field-group">
            <label>${t("Authorized Administrator Email", "بريد الإدارة المعتمد")}</label>
            <input id="gateAdminEmail" type="email" autocomplete="off" value="" placeholder="admin@domain.com" />
          </div>
          <div class="field-group">
            <label>${t("New Admin Password", "كلمة المرور الإدارية الجديدة")}</label>
            <div class="fvg-pass-wrap">
              <input id="gateAdminPass" type="${state.adminModalPassVisible ? "text" : "password"}" autocomplete="new-password" placeholder="••••••••" />
              <button type="button" class="fvg-pass-eye" data-action="toggleAdminModalPass">${state.adminModalPassVisible ? "🙈" : "👁"}</button>
            </div>
          </div>
          <div class="field-group">
            <label>${t("Confirm Admin Password", "تأكيد كلمة المرور الإدارية")}</label>
            <div class="fvg-pass-wrap">
              <input id="gateAdminConf" type="${state.adminModalConfVisible ? "text" : "password"}" autocomplete="new-password" placeholder="••••••••" />
              <button type="button" class="fvg-pass-eye" data-action="toggleAdminModalConf">${state.adminModalConfVisible ? "🙈" : "👁"}</button>
            </div>
          </div>
          <button type="button" class="btn btn-gold" style="width:100%;" data-action="completeGateAdminSetup">
            🔐 ${t("Save Credentials & Unlock /admin", "حفظ كلمة المرور وفتح لوحة الإدارة")}
          </button>
        </div>
      </main>
    </div>
  `;
}

function renderWebOwnerAuditModal() {
  if (!state.ownerAuditDialogOrgName) return "";
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card" style="max-width:640px;max-height:85vh;overflow:auto;">
        <div class="row-between" style="margin-bottom:10px;">
          <strong>📜 ${t("Org Audit Trail:", "سجل تدقيق المنظمة:")} ${esc(state.ownerAuditDialogOrgName)}</strong>
          <button type="button" class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeOwnerAuditModal">✕</button>
        </div>
        ${state.ownerAuditLoading ? `<p>${t("Loading audit logs...", "جاري تحميل سجل التدقيق...")}</p>` : (
          state.ownerAuditLogs.length === 0 ? `<p style="color:var(--fvg-text-muted);">${t("No audit log entries recorded for this organization yet.", "لا توجد سجلات تدقيق مسجلة لهذه المنظمة بعد.")}</p>` : state.ownerAuditLogs.map((log) => `
            <div class="card cream" style="margin-bottom:8px;">
              <div class="row-between">
                <span class="badge">${esc(log.action || "ACTION")}</span>
                <span style="font-size:0.75rem;color:var(--fvg-text-muted);">${esc(log.createdAtStr || log.at || "")}</span>
              </div>
              <div style="font-size:0.85rem;margin-top:4px;">${esc(log.summary || `${log.entityType || ""} ${log.entityId || ""}`)}</div>
              ${log.actorEmail ? `<div style="font-size:0.75rem;color:var(--fvg-heading);margin-top:2px;">${t("Actor:", "المنفّذ:")} ${esc(log.actorEmail)}</div>` : ""}
            </div>
          `).join("")
        )}
      </div>
    </div>
  `;
}

function getCombinedWebAdminNotifications() {
  const real = Array.isArray(state.adminNotifications) ? state.adminNotifications : [];
  const existingIds = new Set(real.map((x) => x.id));
  const synthPays = (state.paymentRequests || [])
    .filter((p) => p.status === "pending_verification" && !existingIds.has("pay_" + p.id))
    .map((p) => ({
      id: "synth_pay_" + p.id,
      synthetic: true,
      category: "payment_request",
      severity: "warning",
      signalType: "payment_proof_submitted",
      titleEn: `Pending Payment Proof (${p.planTier})`,
      titleAr: `إثبات دفع معلق (${p.planTier})`,
      bodyEn: `${p.amount} ${p.currency || "SDG"} • Ref: ${p.referenceNo || "-"}`,
      bodyAr: `${p.amount} ${p.currency || "SDG"} • مرجع: ${p.referenceNo || "-"}`,
      orgId: p.orgId || "",
      sourcePlatform: "live_listener",
      acknowledged: false,
      createdAtMs: 0,
      createdAtStr: p.transferDate || ""
    }));
  return [...real, ...synthPays];
}

function renderWebOwnerAlertsModal() {
  if (!state.showOwnerAlertsModal) return "";
  const allAlerts = getCombinedWebAdminNotifications();
  const filtered = allAlerts.filter((n) => state.ownerNotifFilter === "all" || n.category === state.ownerNotifFilter);
  const unreadRealCount = (state.adminNotifications || []).filter((n) => !n.acknowledged).length;
  const pushPermission = typeof Notification !== "undefined" ? Notification.permission : "unsupported";
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card" style="max-width:680px;">
        <div class="row-between" style="margin-bottom:8px;">
          <div>
            <strong>🔔 ${t("Real-Time Admin Alerts Center", "مركز إشعارات الإدارة الفوري")}</strong>
            <div style="font-size:0.76rem;color:var(--fvg-text-muted);">${t("Firestore Triggers • New Signups • Suspicious Activity", "مشغلات Firestore الفورية • التسجيلات الجديدة • الأنشطة المشبوهة")}</div>
          </div>
          <button type="button" class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeOwnerAlerts">✕</button>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px;">
          <button type="button" class="btn btn-primary" style="min-height:38px;padding:4px 12px;" data-action="runAdminSecurityScan">🛡 ${t("Security Scan", "فحص أمني فوري")}</button>
          <button type="button" class="btn btn-outline" style="min-height:38px;padding:4px 12px;" ${unreadRealCount === 0 ? "disabled" : ""} data-action="markAllAdminNotifsRead">✓ ${t("Mark All Read", "تحديد الكل كمقروء")}</button>
          ${pushPermission === "default" ? `<button type="button" class="btn btn-gold" style="min-height:38px;padding:4px 12px;" data-action="requestBrowserNotifs">🔔 ${t("Enable Desktop Alerts", "تفعيل إشعارات المتصفح")}</button>` : ""}
        </div>
        <div class="subtabs" style="margin-bottom:10px;">
          ${[
            ["all", t(`All (${allAlerts.length})`, `الكل (${allAlerts.length})`)],
            ["new_user", t("New Users", "مستخدمون")],
            ["security_alert", t("Security", "أمان")],
            ["payment_request", t("Payments", "مدفوعات")]
          ].map(([k, lbl]) => `<button type="button" class="subtab ${state.ownerNotifFilter === k ? "active" : ""}" data-owner-notif-filter="${k}">${esc(lbl)}</button>`).join("")}
        </div>
        ${filtered.length === 0 ? `<p style="color:var(--fvg-text-muted);">${t("No alerts in this category yet.", "لا توجد تنبيهات في هذا التصنيف حالياً.")}</p>` : filtered.map((n) => {
          const sev = String(n.severity || "info").toLowerCase();
          const badgeCls = sev === "critical" ? "high" : (sev === "warning" ? "medium" : "low");
          return `
            <div class="fvg-alert-card ${n.acknowledged ? "" : "unread"} ${esc(sev)}">
              <div class="row-between">
                <strong>${esc(state.isArabic ? (n.titleAr || n.titleEn) : (n.titleEn || n.titleAr))}</strong>
                <span class="badge ${badgeCls}">${esc(sev.toUpperCase())}</span>
              </div>
              <div style="font-size:0.84rem;margin:4px 0;">${esc(state.isArabic ? (n.bodyAr || n.bodyEn) : (n.bodyEn || n.bodyAr))}</div>
              <div class="row-between" style="font-size:0.75rem;color:var(--fvg-text-muted);margin-top:4px;">
                <span>${esc(n.sourcePlatform || "cloud_trigger")} ${n.createdAtStr ? "• " + esc(n.createdAtStr) : ""}</span>
                <div style="display:flex;gap:6px;flex-wrap:wrap;">
                  ${n.category === "payment_request" ? `<button type="button" class="btn btn-outline" style="min-height:30px;padding:2px 8px;font-size:0.74rem;" data-jump-owner-tab="1">${t("Review Pay", "مراجعة الدفع")}</button>` : ""}
                  ${n.orgId ? `<button type="button" class="btn btn-outline" style="min-height:30px;padding:2px 8px;font-size:0.74rem;" data-owner-org-audit="${esc(n.orgId)}">${t("Audit", "التدقيق")}</button>` : ""}
                  ${!n.synthetic ? `
                    <button type="button" class="btn btn-outline" style="min-height:30px;padding:2px 8px;font-size:0.74rem;" data-notif-toggle="${esc(n.id)}:${n.acknowledged ? "0" : "1"}">${n.acknowledged ? t("Unread", "غير مقروء") : t("Mark Read", "مقروء")}</button>
                    <button type="button" class="btn btn-danger" style="min-height:30px;padding:2px 8px;font-size:0.74rem;" data-notif-del="${esc(n.id)}">✕</button>
                  ` : ""}
                </div>
              </div>
            </div>
          `;
        }).join("")}
      </div>
    </div>
  `;
}

function renderCrossDeviceInstallModal() {
  if (!state.showIosInstallGuide) return "";
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card" style="max-width:520px;">
        <div class="row-between" style="margin-bottom:8px;">
          <strong>📲 ${t("Install FVG on Every Device", "تثبيت دليل رؤيتك المالي على جميع أجهزتك")}</strong>
          <button type="button" class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeInstallGuide">✕</button>
        </div>
        <div class="card cream" style="margin-bottom:8px;">
          <strong>🍎 ${t("iPhone & iPad (iOS Safari)", "أيفون وآيباد (متصفح Safari)")}</strong>
          <p style="font-size:0.84rem;margin:4px 0 0;line-height:1.6;">
            ${t("1. Open this site in Safari. 2. Tap the Share button (square with up arrow). 3. Scroll and tap 'Add to Home Screen'.", "١. افتح الموقع في متصفح Safari. ٢. اضغط على أيقونة المشاركة بالأسفل. ٣. اختر 'إضافة إلى الشاشة الرئيسية' (Add to Home Screen).")}
          </p>
        </div>
        <div class="card cream" style="margin-bottom:8px;">
          <div class="row-between">
            <strong>🤖 ${t("Android Phone & Tablet", "هواتف وأجهزة أندرويد اللوحية")}</strong>
            <a class="btn btn-gold" href="./assets/fvg-app-debug.apk" download="fvg-app.apk" style="font-size:0.75rem;padding:3px 10px;min-height:30px;">
              ⬇ ${t("Download APK", "تحميل ملف APK")}
            </a>
          </div>
          <p style="font-size:0.84rem;margin:4px 0 0;line-height:1.6;">
            ${t("Download and install the native Android APK directly, or tap 'Install App' in Chrome menu for the PWA.", "حمّل وثبّت ملف APK المباشر لنظام أندرويد، أو اضغط 'تثبيت التطبيق' من متصفح Chrome.")}
          </p>
        </div>
        <div class="card cream" style="margin-bottom:10px;">
          <strong>💻 ${t("Laptop & Desktop (Windows / macOS / Linux)", "اللابتوب والكمبيوتر (ويندوز / ماك / لينكس)")}</strong>
          <p style="font-size:0.84rem;margin:4px 0 0;line-height:1.6;">
            ${t("Click the Install icon inside the address bar in Chrome or Edge to pin FVG as a standalone desktop window.", "انقر على أيقونة التثبيت في شريط العنوان بمتصفح Chrome أو Edge لتشغيل المنصة كبرنامج مستقل.")}
          </p>
        </div>
        <button type="button" class="btn btn-gold" style="width:100%;" data-action="closeInstallGuide">${t("Got It", "فهمت")}</button>
      </div>
    </div>
  `;
}

function renderOwnerAdminShell() {
  if (!isPlatformOwner()) return renderAppShell();
  if (!isWebAdminSetupCompleted()) return renderWebOwnerOneTimeSetupGate();
  const pendingCount = state.paymentRequests.filter((p) => p.status === "pending_verification").length;
  const androidVisits = state.platformVisits.filter((v) => v.platform === "android" && v.screen !== "android_install").length;
  const webVisits = state.platformVisits.filter((v) => v.platform === "web_pwa" && v.screen !== "pwa_install").length;
  const installsCount = state.platformVisits.filter((v) => v.screen === "android_install" || v.screen === "pwa_install").length;
  const openTickets = state.supportTickets.filter((tk) => tk.status === "open").length;
  const activeOrgs = state.organizations.filter((o) => o.subscriptionStatus === "active").length;
  const trialOrgs = state.organizations.filter((o) => o.subscriptionStatus === "trial").length;
  const suspendedOrgs = state.organizations.filter((o) => o.subscriptionStatus === "expired" || o.subscriptionStatus === "cancelled").length;
  const approvedPay = state.paymentRequests.filter((p) => p.status === "approved").length;
  const rejectedPay = state.paymentRequests.filter((p) => p.status === "rejected").length;
  const q = state.ownerSearch.trim().toLowerCase();
  const filteredUsers = state.registeredUsers.filter((u) => !q || String(u.email).toLowerCase().includes(q) || String(u.displayName).toLowerCase().includes(q));
  const oq = state.ownerOrgSearch.trim().toLowerCase();
  const filteredOrgs = state.organizations.filter((o) => {
    const matchTxt = !oq || String(o.name || "").toLowerCase().includes(oq) || String(o.id || "").toLowerCase().includes(oq);
    const matchStatus = state.ownerOrgStatusFilter === "all" || o.subscriptionStatus === state.ownerOrgStatusFilter;
    return matchTxt && matchStatus;
  });
  const nextThemeLabel = state.themeMode === "dark" ? "☀" : "☾";

  const ownerNav = [
    { idx: 0, icon: "📊", label: t("Analytics", "الإحصائيات") },
    { idx: 1, icon: "🏢", label: t("Orgs & Pay", "المنظمات والدفع") },
    { idx: 2, icon: "💳", label: t("Plans", "الباقات") },
    { idx: 3, icon: "📣", label: t("Mail & Tickets", "البريد والتذاكر") },
    { idx: 4, icon: "🧮", label: t("Formulas", "المعادلات") },
    { idx: 5, icon: "🔐", label: t("Security", "الأمان") }
  ];

  let body = "";
  if (state.ownerTab === 0) {
    const regPoints = [
      { label: t("Users", "المستخدمون"), value: state.registeredUsers.length },
      { label: t("Orgs", "المنظمات"), value: state.organizations.length },
      { label: t("Web Visits", "زيارات الويب"), value: webVisits },
      { label: t("Android", "أندرويد"), value: androidVisits },
      { label: t("Installs", "التثبيتات"), value: installsCount },
      { label: t("Payments", "المدفوعات"), value: state.paymentRequests.length }
    ];
    const trafficSlices = [
      { label: t("Web Visits", "زيارات الويب"), value: webVisits, color: "#15423A" },
      { label: t("Android Visits", "زيارات أندرويد"), value: androidVisits, color: "#517D77" },
      { label: t("App Installs", "التثبيتات / التحميلات"), value: installsCount, color: "#DDB065" }
    ];
    body = `
      <div class="card" style="border:1.5px solid var(--fvg-warm-gold);background:var(--fvg-forest-green);color:#FAF2E8;margin-bottom:12px;">
        <div class="row-between" style="flex-wrap:wrap;gap:10px;">
          <div style="display:flex;align-items:center;gap:12px;">
            <span style="font-size:1.6rem;background:rgba(221,176,101,0.2);padding:6px 10px;border-radius:12px;border:1px solid #DDB065;">📥</span>
            <div>
              <strong style="font-size:1.02rem;color:#DDB065;">${t("User Metrics & Site Traffic CSV Report", "تقرير إحصائيات المستخدمين وحركة المرور (CSV)")}</strong>
              <div style="font-size:0.8rem;opacity:0.92;margin-top:2px;">
                ${t("Export offline financial reporting file with complete traffic, visits, app downloads, users, and subscriptions.", "تصدير فوري لتقرير مالي دون اتصال يشمل إحصائيات حركة المرور، الزوار، التثبيتات، والمستخدمين والاشتراكات.")}
              </div>
            </div>
          </div>
          <button type="button" class="btn btn-gold" style="min-height:42px;padding:6px 16px;font-weight:700;" data-action="exportAdminCsv">
            📥 ${t("Download CSV Report", "تحميل تقرير CSV")}
          </button>
        </div>
      </div>

      <div class="fvg-kpi-grid">
        <div class="fvg-kpi-tile gold"><span style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Registered Users", "المستخدمون المسجلون")}</span><strong style="font-size:1.5rem;">${state.registeredUsers.length}</strong></div>
        <div class="fvg-kpi-tile"><span style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Organizations", "المنظمات")}</span><strong style="font-size:1.5rem;">${state.organizations.length}</strong></div>
        <div class="fvg-kpi-tile"><span style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Web + Android Traffic", "زيارات الويب وأندرويد")}</span><strong style="font-size:1.5rem;color:var(--fvg-positive);">${webVisits + androidVisits}</strong></div>
        <div class="fvg-kpi-tile gold"><span style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("App Installs", "التثبيتات المسجلة")}</span><strong style="font-size:1.5rem;">${installsCount}</strong></div>
        <div class="fvg-kpi-tile"><span style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Pending Payments", "مدفوعات معلقة")}</span><strong style="font-size:1.5rem;color:var(--fvg-warning);">${pendingCount}</strong></div>
        <div class="fvg-kpi-tile"><span style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Open Support Tickets", "تذاكر مفتوحة")}</span><strong style="font-size:1.5rem;">${openTickets}</strong></div>
      </div>

      <div class="fvg-admin-charts-row">
        <div class="fvg-chart-card">
          <div class="row-between" style="margin-bottom:8px;">
            <strong>📈 ${t("Registrations & Platform Growth", "نمو التسجيلات والزيارات")}</strong>
            <div style="display:flex;gap:6px;">
              <button type="button" class="subtab ${state.ownerChartStyle === "bar" ? "active" : ""}" data-owner-chart="bar">${t("Bar", "أعمدة")}</button>
              <button type="button" class="subtab ${state.ownerChartStyle === "line" ? "active" : ""}" data-owner-chart="line">${t("Line", "خطي")}</button>
            </div>
          </div>
          ${renderSvgBarOrLineChart(regPoints, state.ownerChartStyle, "#15423A", "#DDB065")}
        </div>

        <div class="fvg-chart-card">
          <div class="row-between" style="margin-bottom:8px;">
            <strong>🌐 ${t("Traffic & App Downloads Breakdown", "توزيع الزوار والتثبيتات")}</strong>
            <div style="display:flex;gap:6px;">
              <button type="button" class="subtab ${state.ownerTrafficChartStyle === "donut" ? "active" : ""}" data-owner-traffic-chart="donut">${t("Donut", "دائري")}</button>
              <button type="button" class="subtab ${state.ownerTrafficChartStyle === "bar" ? "active" : ""}" data-owner-traffic-chart="bar">${t("Bar", "أعمدة")}</button>
            </div>
          </div>
          ${state.ownerTrafficChartStyle === "donut"
            ? renderSvgDonutChart(trafficSlices, t("Events", "حدث"))
            : renderSvgBarOrLineChart(trafficSlices.map((s) => ({ label: s.label, value: s.value })), "bar", "#517D77", "#DDB065")}
        </div>
      </div>

      <div class="card">
        <div class="row-between" style="flex-wrap:wrap;gap:8px;">
          <div style="display:flex;align-items:center;gap:8px;">
            <h3 style="margin:0;">${t("Registered Users Directory", "سجل المستخدمين المسجلين")}</h3>
            <span class="badge">${filteredUsers.length}</span>
          </div>
          <button type="button" class="btn btn-outline" style="min-height:38px;padding:4px 12px;" data-action="exportAdminCsv">📥 ${t("Export Admin CSV", "تصدير تقرير الإدارة CSV")}</button>
        </div>
        <div class="field-group" style="margin-top:8px;"><input id="ownerSearchInput" value="${esc(state.ownerSearch)}" placeholder="${t("Search by name or email...", "ابحث بالاسم أو البريد...")}"/></div>
        ${filteredUsers.length === 0 ? `<p style="font-size:0.85rem;">${t("No matching users found.", "لا توجد نتائج مطابقة.")}</p>` : filteredUsers.map((u) => {
          const welcomeMail = state.mailOutbox.find((m) => m.mailType === "welcome" && Array.isArray(m.to) && m.to.some((e) => String(e).toLowerCase() === String(u.email).toLowerCase()));
          const mailState = welcomeMail?.delivery?.state || welcomeMail?.status || "NOT_QUEUED";
          const mailCls = mailState === "SUCCESS" ? "low" : (mailState === "ERROR" ? "high" : "medium");
          return `
            <div class="card cream" style="margin-bottom:8px;">
              <div class="row-between" style="flex-wrap:wrap;gap:8px;">
                <div>
                  <strong>${esc(u.displayName)}</strong>
                  <div style="font-size:0.82rem;color:var(--fvg-text-muted);">${esc(u.email)} • ${esc(u.orgName)}</div>
                  <div style="margin-top:4px;"><span class="badge ${mailCls}">${t("Welcome Mail:", "بريد الترحيب:")} ${esc(mailState)}</span></div>
                </div>
                <button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-resend-welcome="${esc(u.email)}">✉ ${t("Resend Welcome", "إعادة إرسال الترحيب")}</button>
              </div>
            </div>
          `;
        }).join("")}
      </div>
    `;
  } else if (state.ownerTab === 1) {
    const orgSlices = [
      { label: t("Active Paid", "اشتراكات نشطة"), value: activeOrgs, color: "#1B6B4A" },
      { label: t("7-Day Trial", "فترة تجريبية"), value: trialOrgs, color: "#DDB065" },
      { label: t("Suspended / Expired", "معلقة / منتهية"), value: suspendedOrgs, color: "#B3261E" }
    ];
    const paySlices = [
      { label: t("Approved", "مقبولة"), value: approvedPay, color: "#15423A" },
      { label: t("Pending Review", "قيد المراجعة"), value: pendingCount, color: "#AF8240" },
      { label: t("Rejected", "مرفوضة"), value: rejectedPay, color: "#B3261E" }
    ];
    body = `
      <div class="grid-2">
        <div class="fvg-chart-card">
          <strong>🏢 ${t("Organizations Status Mix", "توزيع حالات المنظمات")}</strong>
          ${renderSvgDonutChart(orgSlices, t("Orgs", "منظمة"))}
        </div>
        <div class="fvg-chart-card">
          <strong>💳 ${t("Payment Verification Status", "توزيع حالة طلبات الدفع")}</strong>
          ${renderSvgDonutChart(paySlices, t("Payments", "طلب"))}
        </div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Payment Verification Queue", "طابور مراجعة إثباتات الدفع")}</h3>
        ${state.paymentRequests.length === 0 ? `<p>${t("No payment requests submitted yet.", "لا توجد طلبات دفع.")}</p>` : state.paymentRequests.map((p) => `
          <div class="card cream">
            <div class="row-between"><strong>${esc(p.senderName)} • ${esc(p.referenceNo)}</strong><span class="badge">${esc(localizeStatus(p.status))}</span></div>
            <div>${esc(p.planTier)} • ${esc(p.amount)} ${esc(p.currency || "SDG")} • ${esc(p.paymentMethod)} ${p.last4Digits ? `• ****${esc(p.last4Digits)}` : ""}</div>
            ${p.receiptMatchStatus && p.receiptMatchStatus !== "unverified" ? `<div class="fvg-match-badge ${p.receiptMatchStatus === "exact_match" ? "exact" : (p.receiptMatchStatus === "close_match" ? "close" : "warn")}">${esc(p.receiptMatchStatus.toUpperCase())}${p.expectedPlanAmount ? ` (${t("Expected:", "المتوقع:")} ${esc(p.expectedPlanAmount)})` : ""}${p.receiptMatchNote ? ` — ${esc(p.receiptMatchNote)}` : ""}</div>` : ""}
            ${p.notes ? `<div style="font-size:0.8rem;color:var(--fvg-text-muted);">${esc(p.notes)}</div>` : ""}
            ${p.status === "pending_verification" ? `
              <div class="field-group" style="margin-top:8px;">
                <input id="payReviewNote_${esc(p.id)}" placeholder="${t("Verification / rejection note...", "ملاحظة المراجعة أو سبب الرفض...")}" value="${esc(p.reviewNotes || "")}"/>
              </div>
              <div style="display:flex;gap:8px;">
                <button class="btn btn-primary" data-owner-pay="approved:${esc(p.id)}">${t("Approve & Activate", "موافقة وتفعيل")}</button>
                <button class="btn btn-danger" data-owner-pay="rejected:${esc(p.id)}">${t("Reject", "رفض")}</button>
              </div>
            ` : (p.reviewNotes ? `<div style="font-size:0.8rem;margin-top:4px;color:var(--fvg-heading);">${t("Note:", "ملاحظة:")} ${esc(p.reviewNotes)}</div>` : "")}
          </div>
        `).join("")}
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Organizations, Subscriptions & RBAC Permissions", "إدارة المنظمات والاشتراكات وصلاحيات الأعضاء")} (${filteredOrgs.length})</h3>
        <div class="field-group"><input id="ownerOrgSearchInput" value="${esc(state.ownerOrgSearch)}" placeholder="${t("Search org by name or ID...", "بحث باسم المنظمة أو المعرف...")}"/></div>
        <div class="subtabs" style="margin-bottom:10px;">
          ${[
            ["all", t("All", "الكل")],
            ["trial", t("Trial", "تجربة")],
            ["active", t("Active", "نشط")],
            ["expired", t("Expired", "منتهي")],
            ["cancelled", t("Suspended", "موقوف")]
          ].map(([k, lbl]) => `<button type="button" class="subtab ${state.ownerOrgStatusFilter === k ? "active" : ""}" data-owner-org-filter="${k}">${esc(lbl)}</button>`).join("")}
        </div>
        ${filteredOrgs.map((org) => `
          <div class="card cream">
            <div class="row-between" style="flex-wrap:wrap;gap:6px;">
              <strong>${esc(org.name)}</strong>
              <div style="display:flex;gap:6px;align-items:center;">
                <span class="badge ${org.subscriptionStatus === "cancelled" || org.subscriptionStatus === "expired" ? "high" : (org.subscriptionStatus === "active" ? "low" : "medium")}">${esc(localizeStatus(org.subscriptionStatus))} • ${esc(org.planTier)}</span>
                <button type="button" class="btn btn-outline" style="min-height:34px;padding:3px 10px;font-size:0.78rem;" data-owner-org-audit="${esc(org.id)}">📜 ${t("Audit Logs", "سجل التدقيق")}</button>
              </div>
            </div>
            <div style="font-size:0.75rem;color:var(--fvg-text-muted);margin-top:2px;">ID: ${esc(org.id)} • ${esc(org.sectorType || "manufacturing")} • ${esc(org.baseCurrency || "SDG")}</div>
            <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:8px;align-items:center;">
              <select id="orgPlanSel_${esc(org.id)}" style="flex:1;min-width:150px;">
                ${state.platformPlans.map((pl) => {
                  const pk = pl.key || pl.code || pl.id;
                  return `<option value="${esc(pk)}" ${org.planTier === pk ? "selected" : ""}>${esc(state.isArabic ? pl.nameAr : pl.nameEn)} (${esc(pk)})</option>`;
                }).join("")}
              </select>
              <button class="btn btn-primary" style="min-height:40px;" data-owner-assign-plan="${esc(org.id)}">${t("Apply Plan", "تفعيل الباقة")}</button>
              <button class="btn btn-outline" style="min-height:40px;" data-owner-org="extend7d:${esc(org.id)}">+7d</button>
              ${org.subscriptionStatus === "cancelled" || org.subscriptionStatus === "expired"
                ? `<button class="btn btn-gold" style="min-height:40px;" data-owner-org="activate:${esc(org.id)}">${t("Reactivate", "إعادة تفعيل")}</button>`
                : `<button class="btn btn-danger" style="min-height:40px;" data-owner-org="suspend:${esc(org.id)}">${t("Suspend", "تعليق الحساب")}</button>`}
            </div>
            <div style="margin-top:10px;padding-top:8px;border-top:1px solid var(--fvg-border);">
              <div style="font-size:0.8rem;font-weight:700;margin-bottom:6px;">${t("Assign / Edit Member Role:", "إدارة صلاحيات أعضاء المنظمة:")}</div>
              <div class="grid-3">
                <input id="rbacUser_${esc(org.id)}" placeholder="${t("Member Email or UID", "بريد العضو أو معرفه")}"/>
                <select id="rbacRole_${esc(org.id)}">
                  <option value="admin">${t("Admin (مشرف)", "مشرف (admin)")}</option>
                  <option value="accountant">${t("Accountant (محاسب)", "محاسب (accountant)")}</option>
                  <option value="viewer">${t("Viewer (عارض)", "عارض (viewer)")}</option>
                </select>
                <button class="btn btn-gold" style="min-height:42px;" data-owner-assign-role="${esc(org.id)}">${t("Grant Role", "منح الصلاحية")}</button>
              </div>
              ${org.roles ? `
                <div style="display:flex;flex-direction:column;gap:6px;margin-top:8px;">
                  ${Object.entries(org.roles).map(([uid, role]) => {
                    const mUsr = state.registeredUsers.find((u) => u.uid === uid);
                    const lbl = mUsr ? `${mUsr.displayName || mUsr.email} (${mUsr.email})` : uid;
                    const isOwnerMember = uid === org.ownerId || role === "owner";
                    return `
                      <div class="row-between" style="font-size:0.78rem;padding:4px 8px;border-radius:8px;background:var(--fvg-surface);">
                        <span>${esc(lbl)}</span>
                        ${isOwnerMember ? `<span class="badge">owner</span>` : `
                          <div style="display:flex;gap:4px;align-items:center;">
                            ${["admin", "accountant", "viewer"].map((r) => `
                              <button type="button" class="subtab ${role === r ? "active" : ""}" style="padding:2px 8px;font-size:0.72rem;" data-owner-set-role="${esc(org.id)}:${esc(uid)}:${r}">${r}</button>
                            `).join("")}
                            <button type="button" class="btn btn-danger" style="min-height:28px;padding:2px 8px;font-size:0.72rem;" data-owner-rem-role="${esc(org.id)}:${esc(uid)}">✕</button>
                          </div>
                        `}
                      </div>
                    `;
                  }).join("")}
                </div>
              ` : ""}
            </div>
          </div>
        `).join("")}
      </div>
    `;
  } else if (state.ownerTab === 2) {
    body = `
      <div class="card cream">
        <div class="row-between">
          <h3 style="margin:0;">${t("Subscription Tiers CRUD", "إنشاء وتعديل وحذف باقات الاشتراك")}</h3>
          <button class="btn btn-gold" style="min-height:40px;" data-action="toggleCreatePlan">${state.ownerCreatePlanMode ? t("Cancel New Tier", "إلغاء الإنشاء") : `+ ${t("New Tier", "إنشاء باقة جديدة")}`}</button>
        </div>
        ${state.ownerCreatePlanMode ? `
          <div style="margin-top:12px;border-top:1px solid var(--fvg-border);padding-top:10px;">
            <div class="grid-3">
              <div class="field-group"><label>${t("Slug Key (e.g. vip_gold)", "المعرّف الإنجليزي (مثال: vip_gold)")}</label><input id="newPlKey" placeholder="vip_gold"/></div>
              <div class="field-group"><label>${t("Name (AR)", "الاسم بالعربية")}</label><input id="newPlNameAr" placeholder="باقة التميز"/></div>
              <div class="field-group"><label>${t("Name (EN)", "الاسم بالإنجليزية")}</label><input id="newPlNameEn" placeholder="VIP Gold"/></div>
              <div class="field-group"><label>${t("USD Price", "السعر (دولار)")}</label><input id="newPlUsd" value="49.00"/></div>
              <div class="field-group"><label>${t("SDG Price", "السعر (جنيه)")}</label><input id="newPlSdg" value="110000"/></div>
              <div class="field-group"><label>${t("Tier Level", "مستوى التدرج")}</label><input id="newPlLvl" type="number" value="4"/></div>
              <div class="field-group"><label>${t("Max Orgs", "أقصى منظمات")}</label><input id="newPlMaxOrgs" type="number" value="5"/></div>
              <div class="field-group"><label>${t("Max Users", "أقصى أعضاء")}</label><input id="newPlMaxUsers" type="number" value="25"/></div>
              <div class="field-group"><label>${t("Max SKUs", "أقصى أصناف")}</label><input id="newPlMaxProducts" type="number" value="1000"/></div>
            </div>
            <div class="grid-2" style="margin:6px 0;">
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlCost" checked/> ${t("All 8 Costing Methods", "كافة طرق التكلفة الـ 8")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlBom" checked/> ${t("BOM, Variance & What-If", "التركيبات والانحرافات وماذا لو")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlAi" checked/> ${t("Financial & OCR AI Mode", "تحليل المساعد المالي التخصصي")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlOcr" checked/> ${t("Camera Invoice OCR", "تصوير الفواتير بالكاميرا")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlPdf" checked/> ${t("Print & Document Exports", "تصدير وطباعة المستندات")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlBak" checked/> ${t("Audit Logs & Backup", "سجل التدقيق والنسخ الاحتياطي")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="newPlSec" checked/> ${t("Sector Advanced Tools", "أدوات القطاعات المتقدمة")}</label>
            </div>
            <button class="btn btn-primary" data-action="createCustomPlan">${t("Create Subscription Tier", "حفظ وإنشاء الباقة")}</button>
          </div>
        ` : ""}
      </div>
      ${state.platformPlans.map((p) => {
        const k = esc(p.key || p.code || p.id);
        const isCore = ["starter", "professional", "enterprise"].includes(k);
        return `
          <div class="card">
            <div class="row-between"><h3 style="margin:0;">${esc(state.isArabic ? p.nameAr : p.nameEn)}</h3><span class="badge">${k} • L${p.tierLevel || 1}</span></div>
            <div class="grid-2" style="margin-top:8px;">
              <div class="field-group"><label>${t("Name (AR)", "الاسم بالعربية")}</label><input id="plNameAr_${k}" value="${esc(p.nameAr || "")}"/></div>
              <div class="field-group"><label>${t("Name (EN)", "الاسم بالإنجليزية")}</label><input id="plNameEn_${k}" value="${esc(p.nameEn || "")}"/></div>
            </div>
            <div class="grid-3">
              <div class="field-group"><label>${t("Price (USD)", "السعر (دولار)")}</label><input id="plUsd_${k}" value="${esc(p.priceUsd)}"/></div>
              <div class="field-group"><label>${t("Price (SDG)", "السعر (جنيه)")}</label><input id="plSdg_${k}" value="${esc(p.priceSdg)}"/></div>
              <div class="field-group"><label>${t("Max Orgs", "أقصى منظمات")}</label><input id="plOrgs_${k}" type="number" value="${esc(p.maxOrgs || 1)}"/></div>
              <div class="field-group"><label>${t("Max Users", "أقصى أعضاء")}</label><input id="plUsers_${k}" type="number" value="${esc(p.maxUsers || 5)}"/></div>
              <div class="field-group"><label>${t("Max SKUs", "أقصى أصناف")}</label><input id="plProds_${k}" type="number" value="${esc(p.maxProducts || 25)}"/></div>
            </div>
            <div class="grid-2" style="margin:6px 0;">
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plCost_${k}" ${p.allowAllCostingMethods ? "checked" : ""}/> ${t("All 8 Costing Methods", "كافة طرق التكلفة الـ 8")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plBom_${k}" ${p.allowBomAndProduction ? "checked" : ""}/> ${t("BOM, Variance & What-If", "التركيبات والانحرافات وماذا لو")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plAi_${k}" ${p.allowAiAssistant ? "checked" : ""}/> ${t("Financial & OCR AI Mode", "تحليل المساعد المالي التخصصي")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plOcr_${k}" ${p.allowOcr ? "checked" : ""}/> ${t("Camera Invoice OCR", "تصوير الفواتير بالكاميرا")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plPdf_${k}" ${p.allowPdfXlsxExport ? "checked" : ""}/> ${t("Print & Document Exports", "تصدير وطباعة المستندات")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plBak_${k}" ${p.allowAuditAndBackup ? "checked" : ""}/> ${t("Audit Logs & Backup", "سجل التدقيق والنسخ الاحتياطي")}</label>
              <label style="display:flex;align-items:center;gap:8px;"><input type="checkbox" id="plSec_${k}" ${p.allowSectorAdvancedTools ? "checked" : ""}/> ${t("Sector Advanced Tools", "أدوات القطاعات المتقدمة")}</label>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap;">
              <button class="btn btn-primary" style="flex:1;" data-save-plan="${k}">${t("Save Plan Controls", "حفظ إعدادات الباقة")}</button>
              ${!isCore ? `<button class="btn btn-danger" data-delete-plan="${k}">${t("Delete Tier", "حذف الباقة")}</button>` : ""}
            </div>
          </div>
        `;
      }).join("")}
    `;
  } else if (state.ownerTab === 3) {
    const mailIssues = state.mailOutbox.filter((m) => {
      const ds = m.delivery?.state || m.status || "";
      return ds === "PENDING_SMTP_CONFIG" || ds === "ERROR";
    }).length;
    const hasSuccessMail = state.mailOutbox.some((m) => (m.delivery?.state || m.status) === "SUCCESS");
    const filteredTickets = state.supportTickets.filter((tk) => state.ownerTicketFilter === "all" || tk.status === state.ownerTicketFilter);
    body = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Manual Broadcast / Targeted Notification", "نشر إشعار وتنبيه يدوي للمستخدمين")}</h3>
        <div class="grid-2">
          <div class="field-group"><label>${t("Title (AR)", "العنوان بالعربية")}</label><input id="annTitleAr"/></div>
          <div class="field-group"><label>${t("Title (EN)", "العنوان بالإنجليزية")}</label><input id="annTitleEn"/></div>
          <div class="field-group"><label>${t("Target Audience", "الجمهور المستهدف")}</label><select id="annAudience"><option value="all">${t("All Users", "جميع المستخدمين")}</option><option value="trial">${t("Trial Only", "المجربون فقط")}</option><option value="paid">${t("Paid Plans Only", "المشتركون المدفوعون")}</option><option value="specific_email">${t("Specific User Email", "بريد مستخدم محدد")}</option></select></div>
          <div class="field-group"><label>${t("Target Email (if specific)", "البريد المستهدف (عند التحديد)")}</label><input id="annTargetEmail" placeholder="user@domain.com"/></div>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Message Body (AR)", "نص الإشعار (عربي)")}</label><textarea id="annBodyAr" rows="2"></textarea></div>
          <div class="field-group"><label>${t("Message Body (EN)", "نص الإشعار (إنجليزي)")}</label><textarea id="annBodyEn" rows="2"></textarea></div>
        </div>
        <label style="display:flex;align-items:center;gap:8px;margin-bottom:10px;"><input type="checkbox" id="annAlsoMail"/> ${t("Also queue email from official platform mail", "إرسال نسخة أيضاً لطابور البريد الرسمي")}</label>
        <button class="btn btn-gold" data-action="publishAnnouncement">${t("Publish Notification", "نشر الإشعار الآن")}</button>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Mail Outbox Monitor (/mail)", "سجل رسائل البريد الصادرة (/mail)")}</h3>
        ${state.mailOutbox.length > 0 && (mailIssues > 0 || !hasSuccessMail) ? renderStatusSignalBanner("yellow", t("SMTP Live Delivery Check", "تنبيه تشخيص إرسال البريد التلقائي (SMTP)"), t("Emails are queued in /mail; configure FVG_SMTP_APP_PASSWORD secret & deploy Cloud Functions for live Gmail dispatch.", "الرسائل مسجلة في طابور /mail؛ لتفعيل الإرسال التلقائي الحقيقي لبريد المستخدمين يرجى ضبط سر FVG_SMTP_APP_PASSWORD ونشر دوال Cloud Functions.")) : ""}
        ${state.mailOutbox.length === 0 ? `<p>${t("No queued emails yet.", "لا توجد رسائل صادرة.")}</p>` : state.mailOutbox.slice(0, 10).map((m) => {
          const st = m.delivery?.state || m.status || "QUEUED";
          const errMsg = m.delivery?.error || "";
          return `
            <div class="card cream" style="margin-bottom:8px;">
              <div class="row-between"><strong>${esc(m.message?.subject || m.mailType)}</strong><span class="badge">${esc(localizeStatus(st))}</span></div>
              <div style="font-size:0.8rem;color:var(--fvg-text-muted);">${esc((m.to || []).join(", "))} • ${esc(m.createdAt)}</div>
              ${errMsg ? `<div style="font-size:0.78rem;color:var(--fvg-negative);margin-top:4px;">↳ ${esc(errMsg)}</div>` : ""}
            </div>
          `;
        }).join("")}
      </div>
      <div class="card">
        <div class="row-between" style="flex-wrap:wrap;gap:8px;margin-bottom:8px;">
          <h3 style="margin:0;">${t("Support Tickets Inbox", "تذاكر الدعم والرد عليها")} (${filteredTickets.length})</h3>
          <div class="subtabs">
            ${[
              ["all", t("All", "الكل")],
              ["open", t("Open", "مفتوحة")],
              ["resolved", t("Resolved", "تم الحل")]
            ].map(([k, lbl]) => `<button type="button" class="subtab ${state.ownerTicketFilter === k ? "active" : ""}" data-owner-ticket-filter="${k}">${esc(lbl)}</button>`).join("")}
          </div>
        </div>
        ${filteredTickets.length === 0 ? `<p>${t("No matching support tickets.", "لا توجد تذاكر دعم مطابقة.")}</p>` : filteredTickets.map((tk) => `
          <div class="card cream">
            <div class="row-between"><strong>${esc(tk.senderEmail)}</strong><span class="badge">${esc(localizeStatus(tk.status))}</span></div>
            <div>${esc(tk.message)}</div>
            <div style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;">
              <input id="reply_${esc(tk.id)}" style="flex:1;min-width:180px;" placeholder="${t("Owner reply...", "رد المالكة...")}" value="${esc(tk.ownerReply || "")}"/>
              <button class="btn btn-primary" data-owner-reply="${esc(tk.id)}">${t("Reply & Resolve", "رد وإغلاق")}</button>
              ${tk.status === "resolved" ? `<button class="btn btn-outline" data-owner-reopen="${esc(tk.id)}">${t("Reopen", "إعادة فتح")}</button>` : ""}
            </div>
          </div>
        `).join("")}
      </div>
    `;
  } else if (state.ownerTab === 4) {
    const checks = state.ownerFormulaChecks.length ? state.ownerFormulaChecks : Engine.runFormulaValidationSuite(state.isArabic);
    const passedCount = checks.filter((c) => c.passed).length;
    const sbOut = Engine.calculateCosting({
      directMaterial: state.ownerSandboxMat, indirectMaterial: 0, directLabor: 0, indirectLabor: 0, overhead: 0, energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0,
      fixedCosts: state.ownerSandboxFix, goodUnits: Math.max(1, state.ownerSandboxUnits), sellingPrice: state.ownerSandboxPrice
    });
    body = `
      <div class="card hero">
        <div class="row-between">
          <div>
            <div style="color:#DDB065;font-weight:700;">${t("Accounting Formula Validation Engine", "محرك تدقيق واعتماد المعادلات المحاسبية")}</div>
            <div style="font-size:1.35rem;font-weight:800;">${passedCount} / ${checks.length} ${t("Checks Verified", "معادلة معتمدة وصحيحة")}</div>
          </div>
          <button class="btn btn-gold" data-action="runFormulaSuite">✓ ${t("Run Full Audit", "إعادة الفحص الآن")}</button>
        </div>
      </div>
      ${checks.map((c) => `
        <div class="card cream" style="border-color:${c.passed ? "var(--fvg-positive)" : "var(--fvg-negative)"};">
          <div class="row-between">
            <strong>${esc(c.title)}</strong>
            <span class="badge ${c.passed ? "low" : "high"}">${c.passed ? t("PASS", "مطابق") : t("FAIL", "اختلاف")}</span>
          </div>
          <div style="font-size:0.8rem;color:var(--fvg-text-muted);">${esc(c.equation)}</div>
          <div style="font-size:0.8rem;margin-top:4px;">${t("Expected", "المتوقع")}: <strong>${esc(c.expected)}</strong> | ${t("Actual", "الفعلي")}: <strong>${esc(c.actual)}</strong></div>
        </div>
      `).join("")}
      <div class="card">
        <h3 style="margin-top:0;">${t("Live Formula Verification Sandbox", "بيئة اختبار المعادلات المباشرة")}</h3>
        <div class="grid-4">
          <div class="field-group"><label>${t("Variable Cost", "المتغيرة")}</label><input id="sbMat" type="number" data-sandbox="ownerSandboxMat" value="${state.ownerSandboxMat}"/></div>
          <div class="field-group"><label>${t("Fixed Cost", "الثابتة")}</label><input id="sbFix" type="number" data-sandbox="ownerSandboxFix" value="${state.ownerSandboxFix}"/></div>
          <div class="field-group"><label>${t("Good Units", "الوحدات")}</label><input id="sbUnits" type="number" data-sandbox="ownerSandboxUnits" value="${state.ownerSandboxUnits}"/></div>
          <div class="field-group"><label>${t("Price", "السعر")}</label><input id="sbPrice" type="number" data-sandbox="ownerSandboxPrice" value="${state.ownerSandboxPrice}"/></div>
        </div>
        <div id="ownerSandboxResult" class="card cream" style="margin:0;">
          <strong>${t("Unit Cost", "تكلفة الوحدة")}:</strong> ${sbOut.unitCost} | <strong>${t("Margin", "الهامش")}:</strong> ${sbOut.profitMarginPct ?? "N/A"}% | <strong>${t("Markup", "العلاوة")}:</strong> ${sbOut.markupPct ?? "N/A"}% | <strong>${t("Break-Even", "نقطة التعادل")}:</strong> ${sbOut.breakEvenUnits ?? "N/A"}
        </div>
      </div>
    `;
  } else {
    const unreadAlertsSec = getCombinedWebAdminNotifications().filter((n) => !n.acknowledged).length;
    body = `
      ${renderStatusSignalBanner("green", t("Custom Admin Password Protected ✓", "كلمة مرور الإدارة المخصصة مفعّلة ✓"), t("One-time setup completed. Executive Admin access is protected by your custom salted SHA-256 credential.", "تم إكمال الإعداد الأولي لمرة واحدة، ولوحة التحكم محمية بكلمة مرورك الخاصة المشفرة (SHA-256)."))}
      <div class="card cream">
        <div class="row-between">
          <div>
            <h3 style="margin:0;">🔔 ${t("Real-Time Security & Trigger Monitor", "مراقبة الأمان والتنبيهات الفورية")}</h3>
            <div style="font-size:0.82rem;color:var(--fvg-text-muted);">${t(`${unreadAlertsSec} unread real-time alerts`, `${unreadAlertsSec} تنبيه غير مقروء من المشغلات السحابية`)}</div>
          </div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;">
          <button type="button" class="btn btn-primary" data-action="runAdminSecurityScan">🛡 ${t("Run Security Scan", "فحص أمني فوري")}</button>
          <button type="button" class="btn btn-gold" data-action="openOwnerAlerts">🔔 ${t("Open Alert Center", "فتح مركز التنبيهات")}</button>
          ${typeof Notification !== "undefined" && Notification.permission === "default" ? `<button type="button" class="btn btn-outline" data-action="requestBrowserNotifs">🖥 ${t("Enable Browser Push", "تفعيل إشعارات الجهاز")}</button>` : ""}
        </div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">🔐 ${t("Change Executive Admin Password", "تغيير كلمة مرور لوحة الإدارة")}</h3>
        <div class="field-group">
          <label>${t("Current Admin Password", "كلمة المرور الحالية")}</label>
          <div class="fvg-pass-wrap">
            <input id="secCurrPass" type="${state.secCurrPassVisible ? "text" : "password"}" placeholder="••••••••" />
            <button type="button" class="fvg-pass-eye" data-action="toggleSecCurrPass">${state.secCurrPassVisible ? "🙈" : "👁"}</button>
          </div>
        </div>
        <div class="grid-2">
          <div class="field-group">
            <label>${t("New Password (8+ chars, letter + digit/symbol)", "كلمة المرور الجديدة (8 أحرف + رقم/رمز)")}</label>
            <div class="fvg-pass-wrap">
              <input id="secNewPass" type="${state.secNewPassVisible ? "text" : "password"}" placeholder="••••••••" />
              <button type="button" class="fvg-pass-eye" data-action="toggleSecNewPass">${state.secNewPassVisible ? "🙈" : "👁"}</button>
            </div>
          </div>
          <div class="field-group">
            <label>${t("Confirm New Password", "تأكيد كلمة المرور الجديدة")}</label>
            <div class="fvg-pass-wrap">
              <input id="secConfPass" type="${state.secConfPassVisible ? "text" : "password"}" placeholder="••••••••" />
              <button type="button" class="fvg-pass-eye" data-action="toggleSecConfPass">${state.secConfPassVisible ? "🙈" : "👁"}</button>
            </div>
          </div>
        </div>
        <button type="button" class="btn btn-primary" data-action="changeAdminPass">${t("Update Admin Password", "حفظ كلمة المرور الجديدة")}</button>
      </div>
    `;
  }

  const allAdminAlerts = getCombinedWebAdminNotifications();
  const unreadAlertsCount = allAdminAlerts.filter((n) => !n.acknowledged).length;
  const latestUnreadAlert = allAdminAlerts.find((n) => !n.acknowledged && (n.category === "security_alert" || n.category === "new_user")) || allAdminAlerts.find((n) => !n.acknowledged);

  return `
    <div class="fvg-admin-shell">
      <header class="fvg-admin-header">
        <div style="display:flex;align-items:center;gap:12px;">
          <img src="./assets/fvg-icon.svg" style="width:40px;height:40px;border-radius:10px;background:#FAF2E8;padding:3px;" alt="شعار الإدارة"/>
          <div>
            <div style="font-size:1.05rem;font-weight:800;">🛡 ${t("Executive Admin Command Center", "مركز القيادة الإداري — لوحة المالكة")}</div>
            <div style="font-size:0.75rem;color:#DDB065;">${t("Owner Zahra Yousif • Full Platform Authority", "المالكة زهراء يوسف • صلاحيات الإدارة والتحكم الشاملة")}</div>
          </div>
        </div>
        <div class="fvg-banner-actions">
          <button type="button" class="fvg-notif-bell" data-action="openOwnerAlerts" title="${t("Real-Time Alerts", "التنبيهات الفورية")}">
            <span>🔔</span>${unreadAlertsCount > 0 ? `<span class="fvg-notif-badge">${unreadAlertsCount > 99 ? "99+" : unreadAlertsCount}</span>` : ""}
          </button>
          <button type="button" class="icon-pill" data-action="previewUserApp">👁 ${t("Preview User App", "معاينة واجهة المستخدم")}</button>
          <button type="button" class="icon-pill" data-action="toggleLang">${state.isArabic ? "إنجليزي" : "عربي"}</button>
          <button type="button" class="icon-pill" data-action="cycleTheme" title="${t("Theme", "المظهر")}">${nextThemeLabel}</button>
          <button type="button" class="icon-pill" data-action="signOut">${t("Sign Out", "خروج")}</button>
        </div>
      </header>
      <div class="fvg-admin-body">
        <aside class="fvg-admin-rail">
          ${ownerNav.map((n) => `<button class="nav-btn ${state.ownerTab === n.idx ? "active" : ""}" data-owner-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
        </aside>
        <main class="fvg-admin-main">
          ${latestUnreadAlert ? `
            <div class="fvg-live-alert-strip ${esc(String(latestUnreadAlert.severity || "info").toLowerCase())}" data-action="openOwnerAlerts">
              <div style="flex:1;min-width:220px;">
                <strong>🔔 ${esc(state.isArabic ? (latestUnreadAlert.titleAr || latestUnreadAlert.titleEn) : (latestUnreadAlert.titleEn || latestUnreadAlert.titleAr))}</strong>
                <div style="font-size:0.82rem;color:var(--fvg-text-muted);">${esc(state.isArabic ? (latestUnreadAlert.bodyAr || latestUnreadAlert.bodyEn) : (latestUnreadAlert.bodyEn || latestUnreadAlert.bodyAr))}</div>
              </div>
              ${!latestUnreadAlert.synthetic ? `<button type="button" class="btn btn-outline" style="min-height:36px;padding:4px 10px;font-size:0.78rem;" data-notif-read="${esc(latestUnreadAlert.id)}">${t("Mark Read", "تمت القراءة")}</button>` : ""}
            </div>
          ` : ""}
          ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
          ${body}
        </main>
      </div>
      <nav class="fvg-admin-bottom-nav">
        ${ownerNav.map((n) => `<button class="nav-btn ${state.ownerTab === n.idx ? "active" : ""}" data-owner-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
      </nav>
      ${renderWebOwnerAuditModal()}
      ${renderWebOwnerAlertsModal()}
    </div>
  `;
}

function renderCalculatorModal() {
  if (!state.showCalculator) return "";
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  const keys = ["MC", "MR", "M+", "M-", "(", ")", "%", "⌫", "7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "-", "C", "0", ".", "+"];
  return `
    <div class="fvg-modal-overlay" id="calcOverlay">
      <div class="fvg-calc-shell">
        <div class="row-between" style="margin-bottom:8px;direction:${state.isArabic ? "rtl" : "ltr"};">
          <strong style="color:#DDB065;">🧮 ${t("Financial Calculator", "الآلة الحاسبة المالية")} (${esc(curr)})</strong>
          <button class="icon-pill" style="min-height:36px;min-width:36px;" data-action="closeCalc">✕</button>
        </div>
        <div class="fvg-calc-display">
          <div class="fvg-calc-expr">${esc(state.calc.expr || "0")}</div>
          <div class="fvg-calc-val">${esc(state.calc.display)}</div>
        </div>
        <div class="fvg-calc-grid">
          ${keys.map((k) => {
            const cls = ["MC", "MR", "M+", "M-"].includes(k) ? "mem" : (["÷", "×", "-", "+", "(", ")", "%", "⌫", "C"].includes(k) ? "op" : "");
            return `<button class="fvg-calc-key ${cls}" data-calc-key="${esc(k)}">${esc(k)}</button>`;
          }).join("")}
          <button class="fvg-calc-key eq" style="grid-column: span 2;" data-calc-key="=">=</button>
          <button class="fvg-calc-key mem" style="grid-column: span 2;" data-action="copyCalc">📋 ${t("Copy", "نسخ")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderHelpPopoverModal() {
  if (!state.activeHelpKey) return "";
  const topic = HELP_TOPICS[state.activeHelpKey];
  if (!topic) return "";
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card">
        <div class="row-between">
          <strong>! ${esc(state.isArabic ? topic.titleAr : topic.titleEn)}</strong>
          <button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeHelpPopover">✕</button>
        </div>
        <p style="line-height:1.75;margin:12px 0;">${esc(state.isArabic ? topic.bodyAr : topic.bodyEn)}</p>
        <button class="btn btn-gold" style="width:100%;" data-action="closeHelpPopover">${t("Got It", "فهمت")}</button>
      </div>
    </div>
  `;
}

function renderSectorPreviewModal() {
  if (!state.activeSectorPreviewKey) return "";
  const sec = Engine.FVG_SECTORS.find((s) => s.key === state.activeSectorPreviewKey);
  const accs = Engine.getSectorStarterAccounts(state.activeSectorPreviewKey);
  if (!sec || !accs.length) return "";
  const scRes = Engine.calculateSectorModel(sec.key, state.sectorCalcModel);
  const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card">
        <div class="row-between">
          <strong>${esc(state.isArabic ? sec.nameAr : sec.nameEn)}</strong>
          <button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="closeSectorModal">✕</button>
        </div>
        <p style="font-size:0.84rem;color:var(--fvg-text-muted);">${t("Standard starter accounts added with zero synthetic balances:", "الحسابات القياسية التي ستُضاف لدليل الحسابات بأرصدة افتتاحية صفرية:")}</p>
        <div style="margin:8px 0;">
          ${accs.map((a) => `<div class="row-between" style="border-bottom:1px solid var(--fvg-border);padding:5px 0;"><span>${a.code} — ${esc(state.isArabic ? a.nameAr : a.name)}</span><span class="badge">0.00</span></div>`).join("")}
        </div>
        <div class="card cream" style="margin:10px 0;">
          <strong style="display:block;margin-bottom:6px;">🧮 ${t("Quick Sector Unit Calculator", "حاسبة القطاع السريعة")}</strong>
          <div class="grid-2">
            <div class="field-group"><label>${t("Batch Materials", "مواد التشغيلة")}</label><input type="number" data-sector-calc="batchMat" value="${state.sectorCalcModel.batchMat}"/></div>
            <div class="field-group"><label>${t("Output Units", "وحدات المخرج")}</label><input type="number" data-sector-calc="batchUnits" value="${state.sectorCalcModel.batchUnits}"/></div>
            ${sec.key === "food_beverage" ? `
              <div class="field-group"><label>${t("Shrinkage / Loss %", "نسبة الفاقد %")}</label><input type="number" data-sector-calc="foodLossPct" value="${state.sectorCalcModel.foodLossPct}"/></div>
              <div class="field-group"><label>${t("Packaging Cost", "التعبئة والتغليف")}</label><input type="number" data-sector-calc="foodPack" value="${state.sectorCalcModel.foodPack}"/></div>
            ` : `
              <div class="field-group"><label>${t("Overhead Cost", "الأعباء غير المباشرة")}</label><input type="number" data-sector-calc="batchOvh" value="${state.sectorCalcModel.batchOvh}"/></div>
              <div class="field-group"><label>${t("Scrap Allowance %", "نسبة الهدر %")}</label><input type="number" data-sector-calc="batchScrapPct" value="${state.sectorCalcModel.batchScrapPct}"/></div>
            `}
          </div>
          <div id="sectorCalcResultBox" style="font-weight:700;color:var(--fvg-positive);">
            ${t("Effective Units", "الوحدات الصالحة")}: ${scRes.effectiveUnits} | ${t("Unit Cost", "تكلفة الوحدة")}: ${scRes.unitCost} ${esc(curr)}
          </div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          ${state.stage === "app" ? `<button class="btn btn-gold" style="flex:1;" data-apply-sector="${esc(sec.key)}">${t("Apply Accounts", "تطبيق الحسابات")}</button>` : ""}
          ${state.stage === "app" ? `<button class="btn btn-primary" style="flex:1;" data-apply-sector-costing="${esc(sec.key)}">${t("Apply & Open Costing", "تطبيق وفتح التكاليف")}</button>` : ""}
          <button class="btn btn-outline" style="flex:1;" data-action="closeSectorModal">${t("Close", "إغلاق")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderTourModal() {
  if (!state.showTour) return "";
  const steps = [
    {
      isLangPicker: true,
      tab: 0,
      titleAr: "اختر لغة التوضيح والواجهة أولاً",
      titleEn: "Choose Your Walkthrough Language",
      descAr: "حدّد لغتك المفضلة الآن ليتم تحويل الشرح وكافة النوافذ والأدوات بالكامل قبل بدء الجولة.",
      descEn: "Pick your language first so the interactive guide and entire workspace adapt seamlessly."
    },
    { tab: 0, titleAr: "مرحباً بك في دليل رؤيتك المالي", titleEn: "Welcome to Financial Vision Guide", descAr: "هنا لوحة القيادة لمتابعة السيولة والمخزون وصافي الربح والفترة التجريبية 7 أيام وإشارات الأمان الملونة وعلامات (!) للتوضيح السريع.", descEn: "Your hub tracking cash, inventory, net profit, 7-day trial, Red/Yellow/Green signals & contextual (!) help tips." },
    { tab: 1, titleAr: "الأسعار والتضخم والصرف اليدوي", titleEn: "Prices, Inflation & Manual FX", descAr: "وثّق تغيرات الأسعار واستورد جداول الأسعار وأدخل أسعار الصرف يدوياً لتفادي أخطاء التحويل الإلكتروني.", descEn: "Track historical product prices, weighted inflation baskets & verified manual FX rates." },
    { tab: 2, titleAr: "التكاليف الـ 8 وتركيبات المنتجات", titleEn: "8 Costing Methods & Recipes", descAr: "ابنِ تركيبات المنتجات وقارن 8 أساليب محاسبية مع محرك المخاطر ونقطة التعادل وسيناريوهات ماذا لو.", descEn: "Build recipes and compute unit cost across 8 methods, break-even, risk & What-If scenarios." },
    { tab: 3, titleAr: "المحاسبة المزدوجة والقوائم والـ 18 نسبة", titleEn: "Double-Entry Ledger & 18 Ratios", descAr: "رحّل قيوداً متوازنة لاستخراج قائمة الدخل والميزانية العمومية و18 نسبة مالية وأعمار الذمم.", descEn: "Post balanced journals for Income Statement, Balance Sheet, 18 ratios & AR/AP aging." },
    { tab: 4, titleAr: "المخزون والهدر والموازنات", titleEn: "Inventory, Expiry & Budgets", descAr: "تابع طرق تقييم المخزون وتنبيهات الصلاحية وفجوة تكلفة الاستبدال الفعلية.", descEn: "Monitor inventory book value vs replacement cost, batch expiry & budgets." },
    { tab: 5, titleAr: "المساعد المالي وكاميرا الفواتير والفوترة", titleEn: "Financial Assistant, Camera & Billing", descAr: "اسأل المساعد المالي بنظام محادثة تفاعلي، وصوّر الفواتير بالكاميرا مباشرة، وأرسل إثبات الدفع لمالكة المنصة.", descEn: "Query the interactive Financial Assistant, scan invoices via camera & submit payment proofs." }
  ];
  const st = steps[Math.min(state.tourStep, steps.length - 1)];
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card">
        <div class="row-between">
          <span class="badge">${state.tourStep + 1} / ${steps.length}</span>
          <button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="skipTour">${t("Skip", "تخطي")}</button>
        </div>
        <h3 style="margin:10px 0 6px;">${esc(state.isArabic ? st.titleAr : st.titleEn)}</h3>
        <p style="line-height:1.65;margin:0 0 14px;">${esc(state.isArabic ? st.descAr : st.descEn)}</p>
        ${st.isLangPicker ? `
          <div class="grid-2" style="margin-bottom:14px;">
            <button class="btn ${state.isArabic ? "btn-gold" : "btn-outline"}" data-tour-lang="ar">العربية (كامل الواجهة بالعربي)</button>
            <button class="btn ${!state.isArabic ? "btn-gold" : "btn-outline"}" data-tour-lang="en">English (Full English UI)</button>
          </div>
        ` : ""}
        <div style="display:flex;gap:8px;">
          ${state.tourStep > 0 ? `<button class="btn btn-outline" style="flex:1;" data-action="prevTour">${t("Previous", "السابق")}</button>` : ""}
          <button class="btn btn-gold" style="flex:1;" data-action="nextTour">${state.tourStep === steps.length - 1 ? t("Finish", "إنهاء") : t("Next", "التالي")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderAppShell() {
  const nav = [
    { idx: 0, label: t("Home", "الرئيسية"), icon: "🏠" },
    { idx: 1, label: t("Prices", "الأسعار"), icon: "📈" },
    { idx: 2, label: t("Costing", "التكاليف"), icon: "🧮" },
    { idx: 3, label: t("Ledger", "المحاسبة"), icon: "⚖️" },
    { idx: 4, label: t("Stock", "المخزون"), icon: "📦" },
    { idx: 5, label: t("Assistant", "المساعد المالي"), icon: "✨" }
  ];

  let screenHtml = "";
  if (state.secondaryView === "profile") screenHtml = renderProfileView();
  else if (state.secondaryView === "help") screenHtml = renderHelpView();
  else if (isTrialLocked() && state.appTab >= 1 && state.appTab <= 4) screenHtml = renderTrialGateCard();
  else if (state.appTab === 0) screenHtml = renderHomeTab();
  else if (state.appTab === 1) screenHtml = renderPricesTab();
  else if (state.appTab === 2) screenHtml = renderCostingTab();
  else if (state.appTab === 3) screenHtml = renderLedgerTab();
  else if (state.appTab === 4) screenHtml = renderStockTab();
  else screenHtml = renderReportsAiTab();

  const tourActiveTab = state.showTour && state.tourStep > 0 ? state.tourStep - 1 : -1;

  return `
    <div class="app-shell">
      <aside class="side-rail">
        <div class="rail-brand">
          <img src="./assets/fvg-icon.svg" alt="شعار المنصة"/>
          <div><div style="font-weight:800;">${t("FVG", "دليل رؤيتك المالي")}</div><div style="font-size:0.72rem;color:#DDB065;">${esc(state.activeOrg?.name || t("Workspace", "مساحة العمل"))}</div></div>
        </div>
        ${nav.map((n) => `<button class="nav-btn ${state.appTab === n.idx && !state.secondaryView ? "active" : ""} ${tourActiveTab === n.idx ? "fvg-tour-highlight" : ""}" data-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
        <hr style="border:none;border-top:1px solid rgba(221,176,101,0.25);width:100%;margin:8px 0;"/>
        <button class="nav-btn ${state.secondaryView === "profile" ? "active" : ""}" data-action="openProfile"><span>👤</span><span>${t("My Profile", "ملفي الشخصي")}</span></button>
        <button class="nav-btn ${state.secondaryView === "help" ? "active" : ""}" data-action="openHelp"><span>❓</span><span>${t("Help & Support", "المساعدة والدعم")}</span></button>
        ${isPlatformOwner() ? `<button class="nav-btn active" data-action="exitUserPreview"><span>🛡</span><span>${t("Return to Admin", "العودة للوحة الإدارة")}</span></button>` : ""}
      </aside>
      <main class="main-content">
        ${isPlatformOwner() && state.adminPreviewUserApp ? `
          <div class="card cream" style="border-color:var(--fvg-warm-gold);margin-bottom:10px;">
            <div class="row-between">
              <strong>👁 ${t("Previewing User Workspace", "وضع معاينة واجهة المستخدم")}</strong>
              <button type="button" class="btn btn-primary" style="min-height:38px;padding:4px 12px;" data-action="exitUserPreview">🛡 ${t("Return to Admin Console", "العودة للوحة الإدارة")}</button>
            </div>
          </div>
        ` : ""}
        ${renderBanner(state.activeOrg?.name || t("FVG", "دليل رؤيتك المالي"), `${state.user?.email || ""} • ${localizeCurrency(state.activeOrg?.baseCurrency || "SDG")}`, true)}
        ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
        ${screenHtml}
      </main>
      <nav class="bottom-nav">
        ${nav.map((n) => `<button class="nav-btn ${state.appTab === n.idx && !state.secondaryView ? "active" : ""} ${tourActiveTab === n.idx ? "fvg-tour-highlight" : ""}" data-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
      </nav>
    </div>
    <button class="fvg-fab-ai" data-action="openAiDrawer" title="${t("Always-On Smart AI Assistant", "المساعد الذكي الفوري")}">✨ ${t("AI Help", "مساعد ذكي")}</button>
    ${renderFloatingAiModal()}
    ${renderCalculatorModal()}
    ${renderHelpPopoverModal()}
    ${renderSectorPreviewModal()}
    ${renderWelcomePopupModal()}
    ${renderTourModal()}
    ${renderCrossDeviceInstallModal()}
  `;
}

function renderFloatingAiModal() {
  if (!state.showAiDrawer) return "";
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-ai-drawer">
        <div class="row-between">
          <strong>✨ ${t("Always-On AI Assistant & Supervisor", "المساعد الذكي والمشرف الشخصي")}</strong>
          <div style="display:flex;gap:6px;">
            <button class="btn btn-outline" style="min-height:36px;padding:4px 10px;" data-action="newConversation">+ ${t("New", "جديد")}</button>
            <button class="btn btn-outline" style="min-height:36px;padding:4px 10px;" data-action="closeAiDrawer">✕</button>
          </div>
        </div>
        ${state.aiConversations.length ? `
          <div class="subtabs">
            ${state.aiConversations.slice(0, 6).map((c) => `
              <span style="display:inline-flex;align-items:center;gap:4px;">
                <button class="subtab ${state.activeConversationId === c.id ? "active" : ""}" data-select-conv="${esc(c.id)}">${esc(c.title)}</button>
                <button class="subtab" style="padding:4px 8px;" data-del-conv="${esc(c.id)}" title="${t("Delete Chat", "حذف المحادثة")}">✕</button>
              </span>
            `).join("")}
          </div>
        ` : ""}
        <div class="subtabs">
          <button class="subtab ${state.aiDrawerMode === "general" ? "active" : ""}" data-drawer-mode="general">${t("General Q&A", "سؤال عام")}</button>
          <button class="subtab ${state.aiDrawerMode === "financial" ? "active" : ""}" data-drawer-mode="financial">${t("Financial", "تحليل مالي")}</button>
          <button class="subtab ${state.aiDrawerMode === "ocr" ? "active" : ""}" data-drawer-mode="ocr">${t("Invoices", "الفواتير")}</button>
        </div>
        <div class="fvg-chat-thread" style="max-height:280px;">
          ${state.chatTranscript.length ? state.chatTranscript.map((m, idx) => {
            const isUser = String(m.role).toLowerCase().startsWith("user") || String(m.role).startsWith("المستخدم");
            return `<div class="fvg-chat-bubble ${isUser ? "user" : "assistant"}">${isUser ? `<div>${esc(m.text)}</div>` : formatAiMarkdownHtml(m.text)}${!isUser ? `<button class="btn btn-outline" style="min-height:28px;padding:2px 8px;font-size:0.72rem;margin-top:4px;" data-copy-msg="${idx}">📋 ${t("Copy", "نسخ")}</button>` : ""}</div>`;
          }).join("") : `<div style="font-size:0.84rem;color:var(--fvg-text-muted);">${t("Ask anything — inside your workspace or any general world knowledge topic!", "اسأل في أي وقت عن أي موضوع داخل أو خارج التطبيق!")}</div>`}
        </div>
        ${state.aiSearchQueries.length ? `<div style="font-size:0.74rem;color:var(--fvg-text-muted);">🔎 ${esc(state.aiSearchQueries.join(" • "))}</div>` : ""}
        ${state.aiGroundingSources.length ? `<div class="fvg-source-row">${state.aiGroundingSources.map((s) => `<a class="fvg-source-chip" href="${esc(s.uri)}" target="_blank" rel="noopener">🌐 ${esc(s.title)}</a>`).join("")}</div>` : ""}
        <textarea id="drawerAiPrompt" rows="2" placeholder="${t("Type your question...", "اكتب سؤالك هنا...")}"></textarea>
        <div style="display:flex;gap:8px;">
          <button class="btn btn-primary" style="flex:1;" ${state.isAiLoading ? "disabled" : ""} data-action="askDrawerAi">${state.isAiLoading ? t("Thinking...", "جاري الرد...") : t("Send", "إرسال")}</button>
          ${state.isAiLoading ? `<button class="btn btn-danger" data-action="stopAi">${t("Stop", "إيقاف")}</button>` : ""}
        </div>
      </div>
    </div>
  `;
}

function downloadFile(filename, content, mime) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function processImageFileToDataUrl(file, maxSize, callback) {
  const reader = new FileReader();
  reader.onload = () => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      const ctx = canvas.getContext("2d");
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      callback(canvas.toDataURL("image/jpeg", 0.82));
    };
    img.src = reader.result;
  };
  reader.readAsDataURL(file);
}

function rotateDataUrl90(dataUrl, callback) {
  const img = new Image();
  img.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = img.height;
    canvas.height = img.width;
    const ctx = canvas.getContext("2d");
    ctx.translate(canvas.width / 2, canvas.height / 2);
    ctx.rotate(Math.PI / 2);
    ctx.drawImage(img, -img.width / 2, -img.height / 2);
    callback(canvas.toDataURL("image/jpeg", 0.85));
  };
  img.src = dataUrl;
}

function bindEvents() {
  const root = document.getElementById("app");
  if (!root) return;

  const splash = document.getElementById("splashRoot");
  if (splash) {
    splash.onclick = () => { state.stage = "public"; render(); };
  }

  root.querySelectorAll("[data-public-page]").forEach((btn) => {
    btn.onclick = () => {
      state.publicPage = Number(btn.dataset.publicPage);
      render();
      try { window.scrollTo({ top: 0, behavior: "smooth" }); } catch {}
    };
  });

  root.querySelectorAll("[data-tab]").forEach((btn) => {
    btn.onclick = () => {
      state.secondaryView = null;
      state.appTab = Number(btn.dataset.tab);
      render();
      try { window.scrollTo({ top: 0, behavior: "smooth" }); } catch {}
    };
  });

  root.querySelectorAll("[data-sub]").forEach((btn) => {
    btn.onclick = () => {
      const [group, idx] = btn.dataset.sub.split(":");
      state.subTab[group] = Number(idx);
      render();
    };
  });

  root.querySelectorAll("[data-owner-tab]").forEach((btn) => {
    btn.onclick = () => {
      state.ownerTab = Number(btn.dataset.ownerTab);
      render();
      try { window.scrollTo({ top: 0, behavior: "smooth" }); } catch {}
    };
  });

  root.querySelectorAll("[data-owner-chart]").forEach((btn) => {
    btn.onclick = () => { state.ownerChartStyle = btn.dataset.ownerChart; render(); };
  });

  root.querySelectorAll("[data-owner-traffic-chart]").forEach((btn) => {
    btn.onclick = () => { state.ownerTrafficChartStyle = btn.dataset.ownerTrafficChart; render(); };
  });

  root.querySelectorAll("[data-owner-org-filter]").forEach((btn) => {
    btn.onclick = () => { state.ownerOrgStatusFilter = btn.dataset.ownerOrgFilter; render(); };
  });

  root.querySelectorAll("[data-owner-ticket-filter]").forEach((btn) => {
    btn.onclick = () => { state.ownerTicketFilter = btn.dataset.ownerTicketFilter; render(); };
  });

  root.querySelectorAll("[data-auth-channel]").forEach((btn) => {
    btn.onclick = () => {
      state.authChannel = btn.dataset.authChannel;
      state.phoneOtpStep = "input";
      state.phonePendingConfirmation = null;
      render();
    };
  });

  const adminModalEmailInput = document.getElementById("adminModalEmail");
  if (adminModalEmailInput) {
    adminModalEmailInput.oninput = (e) => {
      const prevPending = isWebAdminEmailPendingSetup(state.adminModalEmailDraft);
      state.adminModalEmailDraft = e.target.value;
      const nextPending = isWebAdminEmailPendingSetup(state.adminModalEmailDraft);
      if (prevPending !== nextPending) {
        const pos = e.target.selectionStart || state.adminModalEmailDraft.length;
        render();
        const restored = document.getElementById("adminModalEmail");
        if (restored) {
          restored.focus();
          restored.setSelectionRange(pos, pos);
        }
      }
    };
  }

  const ownerSearchInput = document.getElementById("ownerSearchInput");
  if (ownerSearchInput) {
    ownerSearchInput.oninput = (e) => {
      state.ownerSearch = e.target.value;
      render();
      const restored = document.getElementById("ownerSearchInput");
      if (restored) {
        restored.focus();
        restored.setSelectionRange(restored.value.length, restored.value.length);
      }
    };
  }

  const ownerOrgSearchInput = document.getElementById("ownerOrgSearchInput");
  if (ownerOrgSearchInput) {
    ownerOrgSearchInput.oninput = (e) => {
      state.ownerOrgSearch = e.target.value;
      render();
      const restored = document.getElementById("ownerOrgSearchInput");
      if (restored) {
        restored.focus();
        restored.setSelectionRange(restored.value.length, restored.value.length);
      }
    };
  }

  root.querySelectorAll("[data-help]").forEach((btn) => {
    btn.onclick = (e) => {
      e.stopPropagation();
      state.activeHelpKey = btn.dataset.help;
      render();
    };
  });

  root.querySelectorAll("[data-sector-preview]").forEach((card) => {
    card.onclick = () => {
      state.activeSectorPreviewKey = card.dataset.sectorPreview;
      render();
    };
  });

  root.querySelectorAll("[data-apply-sector], [data-apply-sector-costing]").forEach((btn) => {
    btn.onclick = () => {
      const goCosting = Boolean(btn.dataset.applySectorCosting);
      const key = btn.dataset.applySector || btn.dataset.applySectorCosting;
      const starters = Engine.getSectorStarterAccounts(key);
      if (state.activeOrg) state.activeOrg.sectorType = key;
      let added = 0;
      starters.forEach((acc) => {
        if (!state.accounts.some((existing) => existing.code === acc.code)) {
          state.accounts.push({ ...acc });
          added++;
        }
      });
      state.activeSectorPreviewKey = null;
      if (goCosting) { state.secondaryView = null; state.appTab = 2; state.subTab.costing = 0; }
      logAudit("APPLY_SECTOR", "organization", `Applied ${key} (+${added} accounts)`);
      persist();
      showToast(t(`Sector applied (${added} starter accounts added).`, `تم تفعيل القطاع وإضافة ${added} حسابات قياسية.`));
    };
  });

  root.querySelectorAll("[data-sector-calc]").forEach((inp) => {
    inp.oninput = () => {
      state.sectorCalcModel[inp.dataset.sectorCalc] = Number(inp.value || 0);
      const scRes = Engine.calculateSectorModel(state.activeSectorPreviewKey, state.sectorCalcModel);
      const curr = localizeCurrency(state.activeOrg?.baseCurrency || "SDG");
      const box = document.getElementById("sectorCalcResultBox");
      if (box) box.textContent = `${t("Effective Units", "الوحدات الصالحة")}: ${scRes.effectiveUnits} | ${t("Unit Cost", "تكلفة الوحدة")}: ${scRes.unitCost} ${curr}`;
    };
  });

  root.querySelectorAll("[data-sandbox]").forEach((inp) => {
    inp.oninput = () => {
      state[inp.dataset.sandbox] = Number(inp.value || 0);
      const sbOut = Engine.calculateCosting({
        directMaterial: state.ownerSandboxMat, indirectMaterial: 0, directLabor: 0, indirectLabor: 0, overhead: 0, energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0,
        fixedCosts: state.ownerSandboxFix, goodUnits: Math.max(1, state.ownerSandboxUnits), sellingPrice: state.ownerSandboxPrice
      });
      const box = document.getElementById("ownerSandboxResult");
      if (box) box.innerHTML = `<strong>${t("Unit Cost", "تكلفة الوحدة")}:</strong> ${sbOut.unitCost} | <strong>${t("Margin", "الهامش")}:</strong> ${sbOut.profitMarginPct ?? "N/A"}% | <strong>${t("Markup", "العلاوة")}:</strong> ${sbOut.markupPct ?? "N/A"}% | <strong>${t("Break-Even", "نقطة التعادل")}:</strong> ${sbOut.breakEvenUnits ?? "N/A"}`;
    };
  });

  root.querySelectorAll("[data-drawer-mode]").forEach((btn) => {
    btn.onclick = () => { state.aiDrawerMode = btn.dataset.drawerMode; render(); };
  });

  root.querySelectorAll("[data-prelogin-chip]").forEach((btn) => {
    btn.onclick = () => {
      state.preLoginAiQuestion = btn.dataset.preloginChip;
      const el = document.getElementById("preLoginAiPrompt");
      if (el) el.value = state.preLoginAiQuestion;
    };
  });

  root.querySelectorAll("[data-tour-lang]").forEach((btn) => {
    btn.onclick = () => {
      state.isArabic = btn.dataset.tourLang === "ar";
      state.tourStep = 1;
      persist();
      render();
    };
  });

  root.querySelectorAll("[data-ai-mode]").forEach((btn) => {
    btn.onclick = () => { state.aiMode = btn.dataset.aiMode; render(); };
  });

  root.querySelectorAll("[data-ai-chip]").forEach((btn) => {
    btn.onclick = () => {
      const el = document.getElementById("aiPrompt");
      if (el) el.value = btn.dataset.aiChip;
    };
  });

  root.querySelectorAll("[data-copy-msg]").forEach((btn) => {
    btn.onclick = () => {
      const idx = Number(btn.dataset.copyMsg);
      const txt = state.chatTranscript[idx]?.text || "";
      navigator.clipboard?.writeText(txt).catch(() => {});
      showToast(t("Answer copied.", "تم نسخ الإجابة."));
    };
  });

  root.querySelectorAll("[data-select-conv]").forEach((btn) => {
    btn.onclick = () => {
      const conv = state.aiConversations.find((c) => c.id === btn.dataset.selectConv);
      if (conv) {
        state.activeConversationId = conv.id;
        state.chatTranscript = conv.messages;
        state.aiResponse = conv.messages[conv.messages.length - 1]?.text || "";
        render();
      }
    };
  });

  root.querySelectorAll("[data-del-conv]").forEach((btn) => {
    btn.onclick = () => {
      const delId = btn.dataset.delConv;
      state.aiConversations = state.aiConversations.filter((c) => c.id !== delId);
      if (state.activeConversationId === delId) {
        state.activeConversationId = null;
        state.chatTranscript = [];
        state.aiResponse = "";
      }
      persist();
      showToast(t("Conversation deleted.", "تم حذف المحادثة."));
    };
  });

  root.querySelectorAll("[data-preset]").forEach((btn) => {
    btn.onclick = () => {
      const p = Engine.SPEC_SCENARIO_PRESETS.find((x) => x.key === btn.dataset.preset);
      if (!p) return;
      const setV = (id, v) => { const el = document.getElementById(id); if (el) el.value = v; };
      setV("scMat", p.matPct); setV("scFx", p.fxPct); setV("scVol", p.volumePct); setV("scPrice", p.pricePct);
    };
  });

  root.querySelectorAll("[data-fx-status]").forEach((btn) => {
    btn.onclick = () => {
      const [nextStatus, fxId] = btn.dataset.fxStatus.split(":");
      const item = state.fxRates.find((f) => f.id === fxId);
      if (item) { item.status = nextStatus; persist(); showToast(t("FX rate status updated.", "تم تحديث حالة سعر الصرف.")); }
    };
  });

  root.querySelectorAll("[data-del-bom]").forEach((btn) => {
    btn.onclick = () => {
      state.bomFormulas = state.bomFormulas.filter((b) => b.id !== btn.dataset.delBom);
      persist();
      showToast(t("BOM deleted.", "تم حذف التركيبة."));
    };
  });

  root.querySelectorAll("[data-verify-ocr]").forEach((btn) => {
    btn.onclick = () => {
      const id = btn.dataset.verifyOcr;
      const d = state.ocrDrafts.find((x) => x.id === id);
      if (!d) return;
      const q = (f) => root.querySelector(`[data-ocr-field="${f}:${id}"]`)?.value;
      d.vendorName = q("vendor") || d.vendorName;
      d.invoiceNumber = q("inv") || d.invoiceNumber;
      d.subtotal = Engine.money(q("sub"));
      d.discount = Engine.money(q("disc"));
      d.tax = Engine.money(q("tax"));
      d.total = Engine.money(q("tot"));
      d.reviewStatus = "verified";
      persist();
      showToast(t("Invoice draft verified.", "تم اعتماد مسودة الفاتورة."));
    };
  });

  root.querySelectorAll("[data-owner-pay]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const [decision, id] = btn.dataset.ownerPay.split(":");
      const req = state.paymentRequests.find((r) => r.id === id);
      if (!req) return;
      const customNote = document.getElementById(`payReviewNote_${id}`)?.value?.trim();
      req.status = decision;
      req.decisionActorEmail = Engine.OWNER_EMAIL;
      req.reviewedBy = state.user?.uid || Engine.OWNER_EMAIL;
      req.reviewNotes = customNote || (decision === "approved"
        ? t("Verified by Owner Zahra Yousif.", "تم التحقق والموافقة من المالكة زهراء يوسف.")
        : t("Could not verify transfer reference.", "تعذّر مطابقة مرجع التحويل."));
      const targetOrg = state.organizations.find((o) => o.id === req.orgId) || state.activeOrg;
      if (decision === "approved" && targetOrg) {
        targetOrg.planTier = req.planTier;
        targetOrg.subscriptionStatus = "active";
        if (isFirebaseWebConfigured()) ownerUpdateOrgInFirestore(targetOrg).catch(() => {});
      }
      if (isFirebaseWebConfigured() && req.orgId) ownerUpdatePaymentInFirestore(req).catch(() => {});
      persist();
      showToast(t("Payment request updated.", "تم تحديث حالة الدفع."));
    };
  });

  root.querySelectorAll("[data-resend-welcome]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const email = btn.dataset.resendWelcome;
      const userRec = state.registeredUsers.find((u) => String(u.email).toLowerCase() === String(email).toLowerCase());
      const entry = {
        id: "welcome_resend_" + Date.now(),
        from: Engine.OWNER_EMAIL,
        to: [email],
        createdBy: state.user?.uid || "owner",
        recipientName: userRec?.displayName || email,
        mailType: "welcome",
        status: "queued",
        message: {
          subject: state.isArabic ? "مرحباً بك في دليل رؤيتك المالي" : "Welcome to Financial Vision Guide (FVG)",
          text: `مرحباً ${userRec?.displayName || ""}،\n\nيسعدنا انضمامك إلى منصة دليل رؤيتك المالي (FVG).\n\nمع أطيب التمنيات بالنجاح،\nالمالكة زهراء يوسف`
        },
        createdAt: new Date().toISOString().slice(0, 19)
      };
      state.mailOutbox.unshift(entry);
      if (isFirebaseWebConfigured() && state.user?.uid) queueMailInFirestore(entry).catch(() => {});
      persist();
      showToast(t("Welcome email queued for " + email, "تمت إعادة إيداع رسالة الترحيب في طابور البريد لـ " + email));
    };
  });

  root.querySelectorAll("[data-owner-assign-plan]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const orgId = btn.dataset.ownerAssignPlan;
      const org = state.organizations.find((o) => o.id === orgId);
      const selectedPlan = document.getElementById(`orgPlanSel_${orgId}`)?.value;
      if (!org || !selectedPlan) return;
      org.planTier = selectedPlan;
      org.subscriptionStatus = selectedPlan === "trial" ? "trial" : "active";
      if (isFirebaseWebConfigured()) ownerUpdateOrgInFirestore(org).catch(() => {});
      persist();
      showToast(t("Plan updated to " + selectedPlan, "تم تفعيل الباقة: " + selectedPlan));
    };
  });

  root.querySelectorAll("[data-owner-assign-role]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const orgId = btn.dataset.ownerAssignRole;
      const org = state.organizations.find((o) => o.id === orgId);
      const memberInput = document.getElementById(`rbacUser_${orgId}`)?.value?.trim();
      const newRole = document.getElementById(`rbacRole_${orgId}`)?.value || "accountant";
      if (!org || !memberInput) return showToast(t("Enter user email or UID.", "أدخل بريد العضو أو معرفه."));
      const matched = state.registeredUsers.find((u) => String(u.email).toLowerCase() === memberInput.toLowerCase() || u.uid === memberInput);
      const memberUid = matched?.uid || memberInput;
      if (org.ownerId && memberUid === org.ownerId) return showToast(t("Owner role cannot be overwritten.", "لا يمكن تغيير دور المالك الأساسي."));
      org.memberUids = Array.from(new Set([...(org.memberUids || []), memberUid]));
      org.roles = { ...(org.roles || {}), [memberUid]: newRole };
      if (isFirebaseWebConfigured()) ownerUpdateOrgRolesInFirestore(org).catch(() => {});
      persist();
      showToast(t("Assigned role " + newRole, "تم منح الصلاحية: " + newRole));
    };
  });

  root.querySelectorAll("[data-owner-set-role]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const [orgId, memberUid, newRole] = btn.dataset.ownerSetRole.split(":");
      const org = state.organizations.find((o) => o.id === orgId);
      if (!org || memberUid === org.ownerId) return;
      org.memberUids = Array.from(new Set([...(org.memberUids || []), memberUid]));
      org.roles = { ...(org.roles || {}), [memberUid]: newRole };
      if (isFirebaseWebConfigured()) ownerUpdateOrgRolesInFirestore(org).catch(() => {});
      persist();
      showToast(t(`Role updated to ${newRole}.`, `تم تعديل الصلاحية إلى ${newRole}.`));
    };
  });

  root.querySelectorAll("[data-owner-rem-role]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const [orgId, memberUid] = btn.dataset.ownerRemRole.split(":");
      const org = state.organizations.find((o) => o.id === orgId);
      if (!org || memberUid === org.ownerId) return;
      org.memberUids = (org.memberUids || []).filter((id) => id !== memberUid);
      if (org.roles) delete org.roles[memberUid];
      if (isFirebaseWebConfigured()) ownerUpdateOrgRolesInFirestore(org).catch(() => {});
      persist();
      showToast(t("Member removed from organization.", "تمت إزالة العضو من المنظمة."));
    };
  });

  root.querySelectorAll("[data-owner-org-audit]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      openWebOwnerAuditLogs(btn.dataset.ownerOrgAudit);
    };
  });

  root.querySelectorAll("[data-owner-org]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const [cmd, orgId] = btn.dataset.ownerOrg.split(":");
      const org = state.organizations.find((o) => o.id === orgId);
      if (!org) return;
      if (cmd === "extend7d") {
        org.subscriptionStatus = "trial";
        org.planTier = "trial";
        org.trialDurationHours = 168;
        org.trialEndsAtMillis = Date.now() + Engine.TRIAL_DURATION_MILLIS;
      } else if (cmd === "suspend" || cmd === "expired") {
        org.subscriptionStatus = "cancelled";
      } else if (cmd === "activate") {
        org.subscriptionStatus = "active";
      }
      if (isFirebaseWebConfigured()) ownerUpdateOrgInFirestore(org).catch(() => {});
      persist();
      showToast(t("Organization subscription updated.", "تم تحديث اشتراك المنظمة."));
    };
  });

  root.querySelectorAll("[data-save-plan]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const k = btn.dataset.savePlan;
      const plan = state.platformPlans.find((p) => (p.key || p.code || p.id) === k);
      if (!plan) return;
      plan.nameAr = document.getElementById(`plNameAr_${k}`)?.value?.trim() || plan.nameAr;
      plan.nameEn = document.getElementById(`plNameEn_${k}`)?.value?.trim() || plan.nameEn;
      plan.priceUsd = document.getElementById(`plUsd_${k}`)?.value?.trim() || plan.priceUsd;
      plan.priceSdg = document.getElementById(`plSdg_${k}`)?.value?.trim() || plan.priceSdg;
      plan.maxOrgs = Math.max(1, Number(document.getElementById(`plOrgs_${k}`)?.value || plan.maxOrgs || 1));
      plan.maxUsers = Math.max(1, Number(document.getElementById(`plUsers_${k}`)?.value || plan.maxUsers || 5));
      plan.maxProducts = Math.max(1, Number(document.getElementById(`plProds_${k}`)?.value || plan.maxProducts || 25));
      plan.allowAllCostingMethods = Boolean(document.getElementById(`plCost_${k}`)?.checked);
      plan.allowBomAndProduction = Boolean(document.getElementById(`plBom_${k}`)?.checked);
      plan.allowAiAssistant = Boolean(document.getElementById(`plAi_${k}`)?.checked);
      plan.allowOcr = Boolean(document.getElementById(`plOcr_${k}`)?.checked);
      plan.allowPdfXlsxExport = Boolean(document.getElementById(`plPdf_${k}`)?.checked);
      plan.allowAuditAndBackup = Boolean(document.getElementById(`plBak_${k}`)?.checked);
      plan.allowSectorAdvancedTools = Boolean(document.getElementById(`plSec_${k}`)?.checked);
      if (isFirebaseWebConfigured()) savePlanToFirestore(plan).catch(() => {});
      persist();
      showToast(t("Plan pricing & features saved.", "تم حفظ أسعار ومزايا الباقة."));
    };
  });

  root.querySelectorAll("[data-delete-plan]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const k = btn.dataset.deletePlan;
      if (["starter", "professional", "enterprise"].includes(k)) return;
      state.platformPlans = state.platformPlans.filter((p) => (p.key || p.code || p.id) !== k);
      if (isFirebaseWebConfigured()) deletePlanFromFirestore(k).catch(() => {});
      persist();
      showToast(t("Custom tier deleted.", "تم حذف الباقة المخصصة."));
    };
  });

  root.querySelectorAll("[data-owner-reply]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const id = btn.dataset.ownerReply;
      const tk = state.supportTickets.find((x) => x.id === id);
      if (!tk) return;
      tk.ownerReply = document.getElementById("reply_" + id)?.value?.trim() || "";
      tk.status = "resolved";
      if (isFirebaseWebConfigured()) ownerUpdateTicketInFirestore(tk).catch(() => {});
      persist();
      showToast(t("Reply sent and ticket resolved.", "تم إرسال الرد وإغلاق التذكرة."));
    };
  });

  root.querySelectorAll("[data-owner-reopen]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const id = btn.dataset.ownerReopen;
      const tk = state.supportTickets.find((x) => x.id === id);
      if (!tk) return;
      tk.status = "open";
      if (isFirebaseWebConfigured()) ownerUpdateTicketInFirestore(tk).catch(() => {});
      persist();
      showToast(t("Support ticket reopened.", "تمت إعادة فتح التذكرة."));
    };
  });

  root.querySelectorAll("[data-revoke-sess]").forEach((btn) => {
    btn.onclick = () => {
      const s = state.sessions.find((x) => x.id === btn.dataset.revokeSess);
      if (s) { s.revoked = true; persist(); showToast(t("Session revoked.", "تم إنهاء الجلسة.")); }
    };
  });

  root.querySelectorAll("[data-calc-key]").forEach((btn) => {
    btn.onclick = () => {
      const k = btn.dataset.calcKey;
      if (k === "C") { state.calc.expr = ""; state.calc.display = "0"; }
      else if (k === "⌫") { state.calc.expr = state.calc.expr.slice(0, -1); }
      else if (k === "MC") { state.calc.memory = 0; }
      else if (k === "MR") { state.calc.expr += String(state.calc.memory); }
      else if (k === "M+" || k === "M-") {
        const res = Engine.evaluateCalculatorExpression(state.calc.expr || state.calc.display);
        if (res.value != null) state.calc.memory += (k === "M+" ? res.value : -res.value);
      } else if (k === "=") {
        const res = Engine.evaluateCalculatorExpression(state.calc.expr);
        state.calc.display = res.error ? res.formatted : (state.calc.currencyFmt ? Engine.money(res.value).toLocaleString("en-US") : res.formatted);
        if (!res.error) state.calc.expr = String(res.value);
      } else {
        state.calc.expr += k;
        const live = Engine.evaluateCalculatorExpression(state.calc.expr);
        if (!live.error) state.calc.display = state.calc.currencyFmt ? Engine.money(live.value).toLocaleString("en-US") : live.formatted;
      }
      render();
    };
  });

  const avatarEl = document.getElementById("avatarFileInput");
  if (avatarEl) {
    avatarEl.onchange = () => {
      const file = avatarEl.files?.[0];
      if (!file) return;
      processImageFileToDataUrl(file, 256, (dataUrl) => {
        if (state.user) state.user.avatarDataUrl = dataUrl;
        persist();
        showToast(t("Profile photo updated.", "تم تحديث صورة الملف الشخصي."));
      });
    };
  }

  ["ocrCameraInput", "ocrGalleryInput"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) {
      el.onchange = () => {
        const file = el.files?.[0];
        if (!file) return;
        processImageFileToDataUrl(file, 1024, (dataUrl) => {
          state.pendingOcrDataUrl = dataUrl;
          render();
        });
      };
    }
  });

  const receiptEl = document.getElementById("receiptImgInput");
  if (receiptEl) {
    receiptEl.onchange = () => {
      const file = receiptEl.files?.[0];
      if (!file) return;
      processImageFileToDataUrl(file, 1024, async (dataUrl) => {
        let parsed = null;
        if (isFirebaseWebConfigured()) {
          try {
            const { app, fbAi } = await getFirebaseHandles();
            if (fbAi?.getAI && fbAi?.getGenerativeModel) {
              const ai = fbAi.getAI(app, { backend: new fbAi.GoogleAIBackend() });
              const m = fbAi.getGenerativeModel(ai, { model: "gemini-3.8-flash" });
              const b64 = dataUrl.split(",")[1];
              const res = await m.generateContent([
                "Extract Bankak, Fawry, O-Cash, or Bank transfer receipt details strictly as JSON: {\"paymentMethod\":\"bankak|fawry|ocash|bank_transfer\",\"amount\":\"numeric\",\"currency\":\"SDG|USD\",\"referenceNo\":\"string\",\"senderName\":\"string\",\"last4Digits\":\"4 digits\",\"transferDate\":\"YYYY-MM-DD\"}",
                { inlineData: { data: b64, mimeType: "image/jpeg" } }
              ]);
              const txt = res?.response?.text?.() || "";
              const jsonMatch = txt.match(/\{[\s\S]*\}/);
              if (jsonMatch) parsed = JSON.parse(jsonMatch[0]);
            }
          } catch {}
        }
        if (parsed) {
          if (parsed.paymentMethod && document.getElementById("payMethod")) document.getElementById("payMethod").value = parsed.paymentMethod;
          if (parsed.amount && document.getElementById("payAmt")) document.getElementById("payAmt").value = Engine.normalizeArabicDigits(parsed.amount);
          if (parsed.senderName && document.getElementById("paySender")) document.getElementById("paySender").value = parsed.senderName;
          if (parsed.referenceNo && document.getElementById("payRef")) document.getElementById("payRef").value = parsed.referenceNo;
          if (parsed.last4Digits && document.getElementById("payLast4")) document.getElementById("payLast4").value = String(parsed.last4Digits).slice(-4);
          updateWebReceiptMatchPreview();
          showToast(t("Receipt extracted automatically — review fields before submitting.", "تم استخراج بيانات الإيصال آلياً — راجعها قبل الإرسال."));
        } else {
          showToast(t("Image loaded; paste SMS notice if automated extraction is unavailable offline.", "تم تحميل الصورة؛ يمكنك لصق نص الإشعار في وضع عدم الاتصال."));
        }
      });
    };
  }

  root.querySelectorAll("[data-owner-notif-filter]").forEach((btn) => {
    btn.onclick = () => { state.ownerNotifFilter = btn.dataset.ownerNotifFilter; render(); };
  });

  root.querySelectorAll("[data-jump-owner-tab]").forEach((btn) => {
    btn.onclick = () => {
      state.ownerTab = Number(btn.dataset.jumpOwnerTab || 0);
      state.showOwnerAlertsModal = false;
      render();
    };
  });

  root.querySelectorAll("[data-notif-toggle]").forEach((btn) => {
    btn.onclick = (e) => {
      e.stopPropagation();
      const [id, flag] = String(btn.dataset.notifToggle || "").split(":");
      if (id) updateWebAdminNotificationRead(id, flag === "1");
    };
  });

  root.querySelectorAll("[data-notif-read]").forEach((btn) => {
    btn.onclick = (e) => {
      e.stopPropagation();
      const id = btn.dataset.notifRead;
      if (id) updateWebAdminNotificationRead(id, true);
    };
  });

  root.querySelectorAll("[data-notif-del]").forEach((btn) => {
    btn.onclick = (e) => {
      e.stopPropagation();
      const id = btn.dataset.notifDel;
      if (id) deleteWebAdminNotification(id);
    };
  });

  root.querySelectorAll("[data-runway-drop]").forEach((btn) => {
    btn.onclick = () => {
      state.runwayParams.salesDropPct = Number(btn.dataset.runwayDrop || 0);
      runWebCashRunway();
    };
  });

  root.querySelectorAll("[data-resolve-conflict]").forEach((btn) => {
    btn.onclick = () => {
      const [choice, cid] = String(btn.dataset.resolveConflict || "").split(":");
      resolveWebOfflineConflict(cid, choice === "local");
    };
  });

  root.querySelectorAll("[data-action]").forEach((btn) => {
    btn.onclick = () => {
      const act = btn.dataset.action;
      if (act === "toggleLang") {
        state.isArabic = !state.isArabic; persist(); render();
      } else if (act === "cycleTheme") {
        state.themeMode = state.themeMode === "dark" ? "light" : "dark";
        persist(); render();
      } else if (act === "toggleAuthPass") {
        state.authPassVisible = !state.authPassVisible; render();
      } else if (act === "toggleAuthMode") {
        state.authRegisterMode = !state.authRegisterMode; render();
      } else if (act === "openAdminModal") {
        state.adminModalEmailDraft = "";
        state.showAdminModal = true; render();
      } else if (act === "closeAdminModal") {
        state.showAdminModal = false; render();
      } else if (act === "closeOwnerAuditModal") {
        state.ownerAuditDialogOrgName = ""; state.ownerAuditLogs = []; state.ownerAuditLoading = false; render();
      } else if (act === "toggleAdminModalPass") {
        state.adminModalPassVisible = !state.adminModalPassVisible; render();
      } else if (act === "toggleAdminModalConf") {
        state.adminModalConfVisible = !state.adminModalConfVisible; render();
      } else if (act === "toggleSecCurrPass") {
        state.secCurrPassVisible = !state.secCurrPassVisible; render();
      } else if (act === "toggleSecNewPass") {
        state.secNewPassVisible = !state.secNewPassVisible; render();
      } else if (act === "toggleSecConfPass") {
        state.secConfPassVisible = !state.secConfPassVisible; render();
      } else if (act === "closeWelcomePopup") {
        state.showWelcomePopup = false; render();
      } else if (act === "startTourFromWelcome") {
        state.showWelcomePopup = false; state.tourStep = 0; state.showTour = true; render();
      } else if (act === "previewUserApp") {
        state.adminPreviewUserApp = true; state.secondaryView = null; state.appTab = 0; render();
      } else if (act === "exitUserPreview") {
        state.adminPreviewUserApp = false; state.secondaryView = "owner_admin"; render();
      } else if (act === "changeAdminPass" && isPlatformOwner()) {
        const currPw = document.getElementById("secCurrPass")?.value || "";
        const newPw = document.getElementById("secNewPass")?.value || "";
        const confPw = document.getElementById("secConfPass")?.value || "";
        const res = changeWebAdminPassword(currPw, newPw, confPw);
        if (!res.ok) return showToast(res.error);
        if (isFirebaseWebConfigured()) {
          getFirebaseHandles().then(({ fbAuth, auth }) => {
            if (auth.currentUser) fbAuth.updatePassword(auth.currentUser, newPw).catch(() => {});
          }).catch(() => {});
        }
        showToast(t("Admin password updated securely.", "تم تحديث كلمة مرور الإدارة بأمان."));
      } else if (act === "submitAdminInitialSetup") {
        const em = (document.getElementById("adminModalEmail")?.value || state.adminModalEmailDraft || "").trim();
        const pw = document.getElementById("adminModalPass")?.value || "";
        const conf = document.getElementById("adminModalConfPass")?.value || "";
        const res = completeOneTimeWebAdminSetup(em, pw, conf);
        if (!res.ok) return showToast(res.error);
        state.adminPreviewUserApp = false;
        state.showWelcomePopup = false;
        state.showAdminModal = false;
        if (isFirebaseWebConfigured()) {
          launchWithFirebaseEmailPassword(em, pw, t("Owner Zahra Yousif", "المالكة زهراء يوسف"), t("FVG Platform Executive", "إدارة المنصة التنفيذية"), { isAdminLogin: true, registerMode: true });
          return;
        }
        initLocalWorkspace(em, t("Owner Zahra Yousif", "المالكة زهراء يوسف"), t("FVG Platform Executive", "إدارة المنصة التنفيذية"));
      } else if (act === "completeGateAdminSetup") {
        const em = document.getElementById("gateAdminEmail")?.value?.trim() || "";
        const pw = document.getElementById("gateAdminPass")?.value || "";
        const conf = document.getElementById("gateAdminConf")?.value || "";
        const res = completeOneTimeWebAdminSetup(em, pw, conf);
        if (!res.ok) return showToast(res.error);
        if (isFirebaseWebConfigured()) {
          getFirebaseHandles().then(({ fbAuth, auth }) => {
            if (auth.currentUser) fbAuth.updatePassword(auth.currentUser, pw).catch(() => {});
          }).catch(() => {});
        }
        showToast(t("One-time admin setup completed.", "تم إكمال الإعداد الأمني الأولي وفتح لوحة الإدارة."));
      } else if (act === "openOwnerAlerts") {
        state.showOwnerAlertsModal = true; render();
      } else if (act === "closeOwnerAlerts") {
        state.showOwnerAlertsModal = false; render();
      } else if (act === "markAllAdminNotifsRead" && isPlatformOwner()) {
        markAllWebAdminNotificationsRead();
      } else if (act === "runAdminSecurityScan" && isPlatformOwner()) {
        runWebAdminSecurityScan();
      } else if (act === "requestBrowserNotifs") {
        if (typeof Notification !== "undefined" && Notification.requestPermission) {
          Notification.requestPermission().then((perm) => {
            if (perm === "granted") showToast(t("Browser push alerts enabled.", "تم تفعيل إشعارات التنبيه المباشرة على الجهاز."));
            render();
          }).catch(() => {});
        }
      } else if (act === "triggerPwaInstall") {
        if (state.deferredInstallPrompt && !isIosDeviceWeb()) {
          const p = state.deferredInstallPrompt;
          state.deferredInstallPrompt = null;
          p.prompt();
          p.userChoice?.catch(() => {});
        } else {
          state.showIosInstallGuide = true;
          render();
        }
      } else if (act === "closeInstallGuide") {
        state.showIosInstallGuide = false; render();
      } else if (act === "submitAdminLogin") {
        const lockSec = getWebAdminLockoutSeconds();
        if (lockSec > 0) {
          return showToast(t(`Too many failed admin attempts. Locked for ${lockSec}s.`, `تم تجاوز الحد المسموح. محظور لمدة ${lockSec} ثانية.`));
        }
        const em = (document.getElementById("adminModalEmail")?.value || state.adminModalEmailDraft || "").trim();
        const pw = document.getElementById("adminModalPass")?.value || "";
        if (!em || !pw) return showToast(t("Enter admin email and password.", "أدخل البريد الإلكتروني وكلمة المرور."));
        if (!isAuthorizedAdminEmailWeb(em)) {
          recordFailedWebAdminAttempt(em, "unauthorized_email");
          return showToast(t("Invalid administrator credentials.", "بيانات الدخول الإدارية غير صحيحة."));
        }
        if (!isWebAdminSetupCompleted()) return showToast(t("Complete one-time initial admin setup first.", "أكملي الإعداد الأمني الأولي لمرة واحدة أولاً."));
        state.adminPreviewUserApp = false;
        state.showWelcomePopup = false;
        if (isFirebaseWebConfigured()) {
          launchWithFirebaseEmailPassword(em, pw, t("Owner Zahra Yousif", "المالكة زهراء يوسف"), t("FVG Platform Executive", "إدارة المنصة التنفيذية"), { isAdminLogin: true });
          state.showAdminModal = false;
          return;
        }
        if (!verifyWebAdminPassword(pw)) {
          const cnt = recordFailedWebAdminAttempt(em, "wrong_admin_password");
          const rem = getWebAdminLockoutSeconds();
          return showToast(rem > 0
            ? t(`Too many failed admin attempts. Locked for ${rem}s.`, `محاولات متكررة فاشلة. تم الحظر مؤقتاً لمدة ${rem} ثانية.`)
            : t(`Invalid administrator credentials (${cnt}/3).`, `بيانات الدخول الإدارية غير صحيحة (${cnt}/3).`));
        }
        resetWebAdminFailState();
        state.showAdminModal = false;
        initLocalWorkspace(em, t("Owner Zahra Yousif", "المالكة زهراء يوسف"), t("FVG Platform Executive", "إدارة المنصة التنفيذية"));
      } else if (act === "adminForgotPassword") {
        const em = (document.getElementById("adminModalEmail")?.value || state.adminModalEmailDraft || Engine.OWNER_EMAIL).trim();
        if (!em || !em.includes("@")) {
          return showToast(t("Enter the authorized administrator email first.", "أدخل بريد الإدارة المعتمد أولاً."));
        }
        if (!isAuthorizedAdminEmailWeb(em)) {
          return showToast(t("Unauthorized administrator email.", "البريد الإلكتروني غير مصرح له بإدارة المنصة."));
        }
        if (isFirebaseWebConfigured()) {
          sendFirebasePasswordReset(em);
        }
        try { localStorage.removeItem(ADMIN_SEC_KEY); } catch {}
        resetWebAdminFailState();
        state.adminModalEmailDraft = em;
        showToast(t(`Password reset email sent to ${em}. You may now establish a new admin password.`, `تم إرسال رابط استعادة كلمة المرور إلى ${em}، ويمكنك الآن تعيين كلمة مرور الإدارة الجديدة.`));
        render();
      } else if (act === "exportAdminCsv" && isPlatformOwner()) {
        const escC = (v) => `"${String(v ?? "").replaceAll('"', '""')}"`;
        const activeOrgs = state.organizations.filter((o) => o.subscriptionStatus === "active").length;
        const trialOrgs = state.organizations.filter((o) => o.subscriptionStatus === "trial").length;
        const expiredOrgs = state.organizations.filter((o) => o.subscriptionStatus === "expired").length;
        const cancelledOrgs = state.organizations.filter((o) => o.subscriptionStatus === "cancelled").length;
        const androidVisits = state.platformVisits.filter((v) => v.platform === "android" && v.screen !== "android_install").length;
        const webVisits = state.platformVisits.filter((v) => v.platform === "web_pwa" && v.screen !== "pwa_install").length;
        const androidInstalls = state.platformVisits.filter((v) => v.screen === "android_install").length;
        const pwaInstalls = state.platformVisits.filter((v) => v.screen === "pwa_install").length;
        const pendingPays = state.paymentRequests.filter((p) => p.status === "pending_verification").length;
        const approvedPays = state.paymentRequests.filter((p) => p.status === "approved").length;
        const rejectedPays = state.paymentRequests.filter((p) => p.status === "rejected").length;

        const lines = [
          "# FVG Platform Executive Admin Analytics & Site Traffic Report",
          `# Generated: ${new Date().toISOString()}`,
          `# Platform: دليل رؤيتك المالي | Financial Vision Guide (FVG)`,
          "",
          "SECTION,METRIC,VALUE,NOTES",
          `SUMMARY,Registered Users,${state.registeredUsers.length},Total authenticated profiles`,
          `SUMMARY,Total Organizations,${state.organizations.length},Active=${activeOrgs} Trial=${trialOrgs} Expired=${expiredOrgs} Suspended=${cancelledOrgs}`,
          `SUMMARY,Platform Visits,${androidVisits + webVisits},Android=${androidVisits} Web=${webVisits}`,
          `SUMMARY,App Downloads & Installs,${androidInstalls + pwaInstalls},Android APK=${androidInstalls} Web PWA=${pwaInstalls}`,
          `SUMMARY,Payment Requests,${state.paymentRequests.length},Approved=${approvedPays} Pending=${pendingPays} Rejected=${rejectedPays}`,
          `SUMMARY,Support Tickets,${state.supportTickets.length},Open=${state.supportTickets.filter(t => t.status === "open").length}`,
          `SUMMARY,Security & System Alerts,${state.adminNotifications.length},Unread=${state.adminNotifications.filter(n => !n.acknowledged).length}`,
          "",
          "EVENT_TYPE,EventId,Platform,ScreenOrAction,VisitorKey,UserId,Timestamp",
          ...state.platformVisits.map((v) => [
            String(v.screen || "").includes("install") ? "APP_INSTALL" : "SITE_VISIT",
            v.id,
            v.platform,
            v.screen,
            v.visitorKey,
            v.userId,
            v.createdAt
          ].map(escC).join(",")),
          "",
          "USER_ROW,UserId,DisplayName,Email,OrgName,Language,WelcomeMailStatus",
          ...state.registeredUsers.map((u) => {
            const welcome = state.mailOutbox.find(m => m.mailType === "welcome" && (m.to || []).some(e => String(e).toLowerCase() === String(u.email).toLowerCase()));
            const mailStatus = welcome?.delivery?.state || welcome?.status || "NOT_QUEUED";
            return [
              "USER",
              u.uid,
              u.displayName,
              u.email,
              u.orgName,
              u.preferredLanguage || "ar",
              mailStatus
            ].map(escC).join(",");
          }),
          "",
          "ORG_ROW,OrgId,OrgName,Sector,Currency,PlanTier,Status,MembersCount",
          ...state.organizations.map((o) => [
            "ORG",
            o.id,
            o.name,
            o.sectorType || "manufacturing",
            o.baseCurrency || "SDG",
            o.planTier,
            o.subscriptionStatus,
            (o.memberUids || []).length
          ].map(escC).join(",")),
          "",
          "PAY_ROW,RequestId,OrgId,PlanTier,Amount,Currency,Method,SenderName,ReferenceNo,Last4,Status,ReviewNotes",
          ...state.paymentRequests.map((p) => [
            "PAY",
            p.id,
            p.orgId,
            p.planTier,
            p.amount,
            p.currency || "SDG",
            p.paymentMethod,
            p.senderName,
            p.referenceNo,
            p.last4Digits || "",
            p.status,
            p.reviewNotes || ""
          ].map(escC).join(","))
        ];
        downloadFile(`fvg_admin_analytics_${Date.now()}.csv`, lines.join("\n"), "text/csv");
        showToast(t("User metrics & site traffic CSV exported.", "تم تنزيل ملف إحصائيات المستخدمين وحركة المرور (CSV)."));
      } else if (act === "goHome") {
        state.secondaryView = null; state.appTab = 0; render();
      } else if (act === "openCalc") { state.showCalculator = true; render();
      } else if (act === "closeCalc") { state.showCalculator = false; render();
      } else if (act === "closeHelpPopover") { state.activeHelpKey = null; render();
      } else if (act === "closeSectorModal") { state.activeSectorPreviewKey = null; render();
      } else if (act === "dismissWelcome") {
        const k = (state.user?.email || "local").toLowerCase();
        if (!state.dismissedWelcomeFor.includes(k)) state.dismissedWelcomeFor.push(k);
        persist(); render();
      } else if (act === "copyCalc") {
        navigator.clipboard?.writeText(state.calc.display).catch(() => {});
        showToast(t("Result copied.", "تم نسخ النتيجة."));
      } else if (act === "openProfile") { state.secondaryView = "profile"; render();
      } else if (act === "openHelp") { state.secondaryView = "help"; render();
      } else if (act === "openOwnerAdmin") {
        if (isPlatformOwner()) {
          state.adminPreviewUserApp = false;
          state.secondaryView = "owner_admin";
          try { if (!window.location.hash.includes("/admin")) window.history.replaceState(null, "", "#/admin"); } catch {}
          render();
        }
      } else if (act === "closeSecondary") {
        state.secondaryView = null;
        try { if (window.location.hash === "#/admin") window.history.replaceState(null, "", "#/"); } catch {}
        render();
      } else if (act === "openAiDrawer") { state.showAiDrawer = true; render();
      } else if (act === "closeAiDrawer") { state.showAiDrawer = false; render();
      } else if (act === "stopAi") { state.aiAbortRequested = true; state.isAiLoading = false; render();
      } else if (act === "toggleCreatePlan") { state.ownerCreatePlanMode = !state.ownerCreatePlanMode; render();
      } else if (act === "createCustomPlan" && isPlatformOwner()) {
        const slug = (document.getElementById("newPlKey")?.value || "").trim().toLowerCase().replace(/[^a-z0-9_]/g, "");
        if (!slug || slug.length < 3 || slug === "trial") return showToast(t("Valid tier slug required (e.g. vip_gold).", "يرجى إدخال معرف باقة صحيح بالإنجليزية."));
        if (state.platformPlans.some((p) => (p.key || p.code || p.id) === slug)) return showToast(t("Tier already exists.", "هذا المعرّف موجود مسبقاً."));
        const np = {
          id: slug, key: slug, code: slug,
          nameAr: document.getElementById("newPlNameAr")?.value?.trim() || slug,
          nameEn: document.getElementById("newPlNameEn")?.value?.trim() || slug,
          priceUsd: document.getElementById("newPlUsd")?.value?.trim() || "49.00",
          priceSdg: document.getElementById("newPlSdg")?.value?.trim() || "110000",
          tierLevel: Math.max(1, Number(document.getElementById("newPlLvl")?.value || 4)),
          maxOrgs: Math.max(1, Number(document.getElementById("newPlMaxOrgs")?.value || 5)),
          maxUsers: Math.max(1, Number(document.getElementById("newPlMaxUsers")?.value || 25)),
          maxProducts: Math.max(1, Number(document.getElementById("newPlMaxProducts")?.value || 1000)),
          allowAllCostingMethods: Boolean(document.getElementById("newPlCost")?.checked ?? true),
          allowBomAndProduction: Boolean(document.getElementById("newPlBom")?.checked ?? true),
          allowAiAssistant: Boolean(document.getElementById("newPlAi")?.checked ?? true),
          allowOcr: Boolean(document.getElementById("newPlOcr")?.checked ?? true),
          allowPdfXlsxExport: Boolean(document.getElementById("newPlPdf")?.checked ?? true),
          allowAuditAndBackup: Boolean(document.getElementById("newPlBak")?.checked ?? true),
          allowSectorAdvancedTools: Boolean(document.getElementById("newPlSec")?.checked ?? true),
          active: true
        };
        state.platformPlans.push(np);
        state.ownerCreatePlanMode = false;
        if (isFirebaseWebConfigured()) savePlanToFirestore(np).catch(() => {});
        persist(); showToast(t("Custom subscription tier created.", "تم إنشاء الباقة الجديدة بنجاح."));
      } else if (act === "runFormulaSuite") {
        state.ownerFormulaChecks = Engine.runFormulaValidationSuite(state.isArabic);
        showToast(t("All 10 accounting formulas audited.", "تم تدقيق وفحص كافة المعادلات الـ 10 بنجاح."));
      } else if (act === "downloadCsvTemplate") {
        const sample = state.isArabic
          ? "اسم المنتج,الرمز,الفئة,السعر\nسكر 50 كجم,SUG-50,مادة خام,42000\nدقيق 25 كجم,FLR-25,مادة خام,28500\n"
          : "name,sku,category,price\nSugar 50kg,SUG-50,Raw Material,42000\nFlour 25kg,FLR-25,Raw Material,28500\n";
        downloadFile("fvg_prices_template.csv", sample, "text/csv");
        showToast(t("CSV template downloaded.", "تم تنزيل قالب الأسعار CSV."));
      } else if (act === "replayTour") { state.secondaryView = null; state.appTab = 0; state.tourStep = 0; state.showTour = true; render();
      } else if (act === "nextTour") {
        if (state.tourStep < 6) {
          state.tourStep++;
          state.appTab = Math.max(0, state.tourStep - 1);
          render();
        } else {
          state.showTour = false;
          if (state.user) state.user.tourCompleted = true;
          persist(); render();
        }
      } else if (act === "prevTour") {
        if (state.tourStep > 0) {
          state.tourStep--;
          state.appTab = Math.max(0, state.tourStep - 1);
          render();
        }
      } else if (act === "skipTour") {
        state.showTour = false; if (state.user) state.user.tourCompleted = true; persist(); render();
      } else if (act === "goBilling") {
        state.secondaryView = null; state.appTab = 5; state.subTab.reports = 2; render();
      } else if (act === "signOut") {
        if (unsubscribeAdminAlerts) { try { unsubscribeAdminAlerts(); } catch {} unsubscribeAdminAlerts = null; }
        state.adminNotifications = [];
        state.showOwnerAlertsModal = false;
        state.secondaryView = null; state.adminPreviewUserApp = false; state.showWelcomePopup = false;
        state.phoneOtpStep = "input"; state.phonePendingConfirmation = null;
        state.stage = "public";
        if (isFirebaseWebConfigured()) {
          getFirebaseHandles().then(({ fbAuth, auth }) => fbAuth.signOut(auth)).catch(() => {});
        }
        render();
      } else if (act === "resetPassword") {
        const email = document.getElementById("authEmailInput")?.value?.trim() || document.getElementById("authPhoneOptionalEmail")?.value?.trim();
        if (!email || !email.includes("@")) return showToast(t("Enter your email address first.", "أدخل بريدك الإلكتروني أولاً لاستعادة كلمة المرور."));
        if (isFirebaseWebConfigured()) { sendFirebasePasswordReset(email); return; }
        showToast(t(`Password reset requested for ${email}.`, `تم طلب استعادة كلمة المرور للبريد ${email}.`));
      } else if (act === "launchGoogle") {
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || t("FVG Workspace", "مساحة عمل المنصة");
        if (isFirebaseWebConfigured()) { launchWithFirebaseWeb(orgName); return; }
        initLocalWorkspace("user@local.fvg", t("Workspace User", "مستخدم المنصة"), orgName);
      } else if (act === "launchApple") {
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || t("FVG Workspace", "مساحة عمل المنصة");
        if (isFirebaseWebConfigured()) { launchWithFirebaseOAuthProvider("apple.com", orgName); return; }
        initLocalWorkspace("apple_user@local.fvg", t("Apple User", "مستخدم أبل"), orgName);
      } else if (act === "launchFacebook") {
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || t("FVG Workspace", "مساحة عمل المنصة");
        if (isFirebaseWebConfigured()) { launchWithFirebaseOAuthProvider("facebook.com", orgName); return; }
        initLocalWorkspace("fb_user@local.fvg", t("Facebook User", "مستخدم فيسبوك"), orgName);
      } else if (act === "resetPhoneOtp") {
        state.phoneOtpStep = "input";
        state.phonePendingConfirmation = null;
        render();
      } else if (act === "verifyPhoneOtp") {
        const code = document.getElementById("authOtpCodeInput")?.value?.trim() || "";
        verifyWebPhoneOtp(code);
      } else if (act === "launchEmailSignIn") {
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || t("FVG Workspace", "مساحة عمل المنصة");
        const nameInput = document.getElementById("authNameInput")?.value?.trim() || "";
        if (state.authChannel === "phone") {
          const rawPhone = document.getElementById("authPhoneInput")?.value?.trim() || "";
          const optEmail = document.getElementById("authPhoneOptionalEmail")?.value?.trim() || "";
          if (isFirebaseWebConfigured()) {
            sendWebPhoneOtp(rawPhone, nameInput, orgName, optEmail);
            return;
          }
          const digits = rawPhone.replace(/\D/g, "");
          const pass = document.getElementById("authPassInput")?.value || "";
          if (digits.length < 7) return showToast(t("Enter a valid phone number (7+ digits).", "يرجى إدخال رقم هاتف صحيح (7 أرقام فأكثر)."));
          if (pass.length < 6) return showToast(t("Password must be at least 6 characters.", "كلمة المرور يجب أن تكون 6 أحرف على الأقل."));
          const identEmail = optEmail.includes("@") ? optEmail : `phone_${digits}@fvg.phone`;
          initLocalWorkspace(identEmail, nameInput || `+${digits}`, orgName);
          return;
        }
        const pass = document.getElementById("authPassInput")?.value || "";
        const email = document.getElementById("authEmailInput")?.value?.trim() || "";
        if (!email || !email.includes("@")) return showToast(t("Please enter a valid email address.", "يرجى إدخال بريد إلكتروني صحيح."));
        if (pass.length < 6) return showToast(t("Password must be at least 6 characters.", "كلمة المرور يجب أن تكون 6 أحرف على الأقل."));
        const name = nameInput || email.split("@")[0] || "مستخدم";
        if (isFirebaseWebConfigured()) { launchWithFirebaseEmailPassword(email, pass, name, orgName, { registerMode: state.authRegisterMode }); return; }
        initLocalWorkspace(email, name, orgName);
      } else if (act === "saveProfile") {
        if (state.user) state.user.displayName = document.getElementById("profName")?.value?.trim() || state.user.displayName;
        if (state.activeOrg) {
          const newOrgName = document.getElementById("profOrgName")?.value?.trim();
          const newCurr = document.getElementById("profCurr")?.value;
          if (newOrgName) state.activeOrg.name = newOrgName;
          if (newCurr) state.activeOrg.baseCurrency = newCurr;
        }
        state.isArabic = (document.getElementById("profLang")?.value || "ar") === "ar";
        state.themeMode = document.getElementById("profTheme")?.value || "light";
        state.fontScale = Number(document.getElementById("profFontScale")?.value || 1.0);
        persist(); showToast(t("Profile saved.", "تم حفظ التفضيلات."));
      } else if (act === "removeAvatar") {
        if (state.user) state.user.avatarDataUrl = null;
        persist(); showToast(t("Avatar removed.", "تم حذف الصورة."));
      } else if (act === "rotateOcr" && state.pendingOcrDataUrl) {
        rotateDataUrl90(state.pendingOcrDataUrl, (rotated) => { state.pendingOcrDataUrl = rotated; render(); });
      } else if (act === "createOcrDraft") {
        state.ocrDrafts.push({
          id: "ocr_" + Date.now(), vendorName: "", invoiceNumber: "",
          subtotal: 0, discount: 0, tax: 0, total: 0, reviewStatus: "pending_review"
        });
        state.pendingOcrDataUrl = null;
        persist();
        showToast(t("OCR draft ready for human verification.", "تم إنشاء مسودة الفاتورة للمراجعة والتحقق."));
      } else if (act === "savePrice") {
        const ent = getEntitlements();
        const name = document.getElementById("prName").value.trim();
        const sku = document.getElementById("prSku").value.trim() || "صنف";
        const price = Engine.money(document.getElementById("prVal").value);
        if (!name || price <= 0) return showToast(t("Product name & positive price required.", "يرجى إدخال اسم المنتج وسعر موجب."));
        if (!state.products.some((p) => p.name.toLowerCase() === name.toLowerCase()) && state.products.length >= ent.maxProducts) {
          return showToast(t(`Product limit (${ent.maxProducts}) reached for your plan.`, `وصلت للحد الأقصى للأصناف في باقتك (${ent.maxProducts}).`));
        }
        if (!state.isOnline) {
          enqueueWebOfflineMutation("PRICE", `${name}: ${price}`, { name, sku, price });
        } else {
          recordPriceObservationItem(name, sku, price);
          persist(); showToast(t("Price observation saved.", "تم حفظ السعر بنجاح."));
        }
      } else if (act === "importCsv") {
        const fileEl = document.getElementById("csvFile");
        const pasteText = document.getElementById("csvPaste")?.value?.trim() || "";
        const handleCsvText = (txt) => {
          const lines = txt.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
          let count = 0;
          lines.forEach((line, idx) => {
            const cols = line.split(",").map((c) => c.trim());
            if (idx === 0 && (cols[0].toLowerCase() === "name" || cols[0] === "اسم المنتج")) return;
            const name = cols[0], sku = cols[1] || "مستورد", price = Engine.money(cols[3] || cols[2]);
            if (name && price > 0) { recordPriceObservationItem(name, sku, price); count++; }
          });
          persist(); showToast(count ? t(`Imported ${count} rows.`, `تم استيراد ${count} صفوف بنجاح.`) : t("No valid rows found.", "لم يتم العثور على صفوف صالحة."));
        };
        if (fileEl?.files?.[0]) fileEl.files[0].text().then(handleCsvText);
        else handleCsvText(pasteText);
      } else if (act === "saveInflation") {
        const period = document.getElementById("infPeriod").value.trim();
        const monthlyRate = Engine.pct4(document.getElementById("infMonthly").value);
        const sourceType = document.getElementById("infSource").value;
        if (!period) return;
        state.inflationRecords.unshift({ id: "inf_" + Date.now(), period, monthlyRate, sourceType });
        persist(); showToast(t("Inflation record saved.", "تم حفظ سجل التضخم."));
      } else if (act === "saveBasket") {
        const name = document.getElementById("bskName").value.trim() || t("Basket", "سلة");
        const item = document.getElementById("bskItem").value.trim() || t("Input", "مدخل");
        const res = Engine.calculateWeightedBasket([{
          quantity: Engine.n(document.getElementById("bskQty").value),
          weight: Engine.n(document.getElementById("bskWeight").value),
          basePrice: Engine.n(document.getElementById("bskBase").value),
          currentPrice: Engine.n(document.getElementById("bskCurr").value)
        }]);
        state.baskets.unshift({ id: "bsk_" + Date.now(), name, item, ...res });
        persist(); showToast(t("Weighted basket saved.", "تم حفظ السلة الموزونة."));
      } else if (act === "saveFx") {
        const base = document.getElementById("fxBase").value.trim();
        const quote = document.getElementById("fxQuote").value.trim();
        const rate = Engine.pct4(document.getElementById("fxRate").value);
        const date = document.getElementById("fxDate").value || new Date().toISOString().slice(0, 10);
        const source = document.getElementById("fxSource").value.trim();
        const status = document.getElementById("fxStatus").value;
        if (rate <= 0) return showToast(t("Positive FX rate required.", "يرجى إدخال سعر صرف موجب."));
        state.fxRates.unshift({ id: "fx_" + Date.now(), base, quote, rate, date, source, status });
        persist(); showToast(t("Manual FX rate saved.", "تم حفظ سعر الصرف اليدوي."));
      } else if (act === "saveBom") {
        const productName = document.getElementById("bomProd").value.trim();
        const ingredientName = document.getElementById("bomIng").value.trim();
        const qty = Engine.n(document.getElementById("bomQty").value);
        const cost = Engine.n(document.getElementById("bomCost").value);
        const batch = Math.max(1, Engine.n(document.getElementById("bomBatch").value));
        const scrap = Engine.n(document.getElementById("bomScrap").value);
        if (!productName || !ingredientName) return showToast(t("Product & ingredient required.", "يرجى إدخال المنتج والمادة."));
        const unitMaterialCost = Engine.money(((qty * cost) * (1 + scrap / 100)) / batch);
        state.bomFormulas.push({ id: "bom_" + Date.now(), productName, ingredientName, qty, cost, batch, scrap, unitMaterialCost });
        persist(); showToast(t("BOM recipe saved.", "تم حفظ التركيبة بنجاح."));
      } else if (act === "saveCosting") {
        const method = document.getElementById("costMethod").value;
        const productName = document.getElementById("cProd").value.trim() || t("Product", "منتج");
        const extra = Engine.n(document.getElementById("cExtra").value);
        const input = {
          directMaterial: Engine.n(document.getElementById("cMat").value), indirectMaterial: 0,
          directLabor: Engine.n(document.getElementById("cLab").value), indirectLabor: 0,
          overhead: Engine.n(document.getElementById("cOvh").value),
          energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0,
          fixedCosts: Engine.n(document.getElementById("cFix").value),
          goodUnits: Engine.n(document.getElementById("cUnits").value),
          sellingPrice: Engine.n(document.getElementById("cPrice").value)
        };
        const out = Engine.calculateCostingByMethod(method, input, extra, extra, state.isArabic);
        const latestInf = state.inflationRecords[0]?.monthlyRate ?? null;
        const stmt = renderStatementsAndRatios();
        const risk = Engine.evaluateFinancialRisk(out.coreResult.profitMarginPct, latestInf, stmt.totalLiabilities, stmt.totalEquityWithNetIncome, state.isArabic);
        state.costSnapshots.push({ id: "cs_" + Date.now(), productName, method, input, sellingPrice: input.sellingPrice, coreResult: out.coreResult, methodNote: out.methodNote, risk });
        persist(); showToast(t("Costing snapshot saved.", "تم حفظ لقطة التكلفة والمخاطر."));
      } else if (act === "calcVariance") {
        const d = Engine.computeDetailedVariance({
          goodUnits: document.getElementById("vUnits").value,
          stdMaterialQtyPerUnit: document.getElementById("vSmq").value,
          stdMaterialPrice: document.getElementById("vSmp").value,
          actualMaterialQty: document.getElementById("vAmq").value,
          actualMaterialPrice: document.getElementById("vAmp").value,
          stdLaborHoursPerUnit: document.getElementById("vSlh").value,
          stdLaborRate: document.getElementById("vSlr").value,
          actualLaborHours: document.getElementById("vAlh").value,
          actualLaborRate: document.getElementById("vAlr").value,
          stdOverheadPerUnit: 0, actualOverheadCost: 0
        });
        const box = document.getElementById("varianceResultBox");
        if (box) {
          box.innerHTML = `<div class="card cream"><div>${t("Mat Price Var", "انحراف السعر")}: <strong>${d.materialPriceVariance}</strong> | ${t("Mat Usage Var", "انحراف الكمية")}: <strong>${d.materialUsageVariance}</strong></div><div>${t("Labor Rate Var", "انحراف الأجر")}: <strong>${d.laborRateVariance}</strong> | ${t("Labor Eff Var", "انحراف الكفاءة")}: <strong>${d.laborEfficiencyVariance}</strong></div><div>${t("Total Variance", "إجمالي الانحراف")}: <strong>${d.totalVariance}</strong></div></div>`;
        }
      } else if (act === "runScenario") {
        const snap = state.costSnapshots[state.costSnapshots.length - 1];
        if (!snap) return;
        const sim = Engine.runExtendedScenario({
          baseMaterial: snap.input.directMaterial, baseLabor: snap.input.directLabor,
          baseOverheadAndOther: snap.input.overhead + snap.input.fixedCosts, baseRevenue: snap.coreResult.revenue,
          materialChangePct: document.getElementById("scMat").value, fxChangePct: document.getElementById("scFx").value,
          volumeChangePct: document.getElementById("scVol").value, priceChangePct: document.getElementById("scPrice").value
        });
        state.scenarios.push({ id: "sc_" + Date.now(), name: snap.productName, ...sim });
        persist(); showToast(t("Scenario saved.", "تم حفظ السيناريو."));
      } else if (act === "postJe") {
        const desc = document.getElementById("jeDesc").value.trim();
        const amt = Engine.money(document.getElementById("jeAmt").value);
        const dr = document.getElementById("jeDr").value;
        const cr = document.getElementById("jeCr").value;
        if (!desc || amt <= 0 || dr === cr) return showToast(t("Valid description, positive amount & distinct accounts required.", "يرجى إدخال وصف ومبلغ موجب وحسابين مختلفين."));
        if (!state.isOnline) {
          enqueueWebOfflineMutation("JOURNAL", desc, { desc, amt, dr, cr });
        } else {
          state.journalEntries.push({
            id: "je_" + Date.now(),
            referenceNo: t("JE-", "قيد-") + String(state.journalEntries.length + 1).padStart(3, "0"),
            date: new Date().toISOString().slice(0, 10),
            description: desc, status: "posted", total: amt,
            lines: [{ accountCode: dr, debit: amt, credit: 0 }, { accountCode: cr, debit: 0, credit: amt }]
          });
          persist(); showToast(t("Balanced journal posted.", "تم ترحيل القيد المزدوج."));
        }
      } else if (act === "saveInventory") {
        const sku = document.getElementById("invSku").value.trim();
        const name = document.getElementById("invName").value.trim();
        if (!sku || !name) return showToast(t("SKU & Name required.", "الرمز والاسم مطلوبان."));
        const itemObj = {
          id: "inv_" + Date.now(), sku, name,
          method: document.getElementById("invMethod").value,
          qty: Engine.n(document.getElementById("invQty").value),
          avgCost: Engine.n(document.getElementById("invCost").value),
          marketPrice: Engine.n(document.getElementById("invMkt").value),
          batchNumber: document.getElementById("invBatch").value.trim(),
          expiryDate: document.getElementById("invExpiry").value
        };
        if (!state.isOnline) {
          enqueueWebOfflineMutation("STOCK", `${sku} • ${name}`, itemObj);
        } else {
          state.inventoryItems.push(itemObj);
          persist(); showToast(t("Inventory item saved.", "تم حفظ الصنف المخزني."));
        }
      } else if (act === "runCashRunway") {
        runWebCashRunway();
      } else if (act === "runMultiBranch") {
        const elimRc = Engine.n(document.getElementById("elimRevCost")?.value);
        const elimAa = Engine.n(document.getElementById("elimArAp")?.value);
        const currStmt = renderStatementsAndRatios();
        const scrapTot = state.scrapLosses.reduce((s, x) => s + Engine.n(x.totalLossCost), 0);
        const branchInputs = (state.organizations.length ? state.organizations : [state.activeOrg]).filter(Boolean).map((o) => {
          const isCur = o.id === state.activeOrg?.id;
          return {
            orgId: o.id, branchName: o.name, currency: o.baseCurrency || "SDG",
            revenue: isCur ? currStmt.revenue : 0,
            cogs: isCur ? currStmt.cogs : 0,
            opEx: isCur ? currStmt.operatingExpenses : 0,
            scrapLoss: isCur ? scrapTot : 0,
            assets: isCur ? currStmt.totalAssets : 0,
            liabilities: isCur ? currStmt.totalLiabilities : 0
          };
        });
        state.branchConsolidation = Engine.consolidateMultiBranchPerformance(branchInputs, elimRc, elimAa);
        persist(); showToast(t(`Consolidated ${branchInputs.length} branches.`, `تم توحيد ${branchInputs.length} فروع.`));
      } else if (act === "flushOfflineQueue") {
        flushWebOfflineQueue();
      } else if (act === "parsePaySms") {
        const txt = document.getElementById("paySmsInput")?.value || "";
        state.paySmsText = txt;
        const parsed = Engine.parseSudanTransferSmsOrText(txt);
        if (parsed.paymentMethod && document.getElementById("payMethod")) document.getElementById("payMethod").value = parsed.paymentMethod;
        if (parsed.amount && document.getElementById("payAmt")) document.getElementById("payAmt").value = parsed.amount;
        if (parsed.senderName && document.getElementById("paySender")) document.getElementById("paySender").value = parsed.senderName;
        if (parsed.referenceNo && document.getElementById("payRef")) document.getElementById("payRef").value = parsed.referenceNo;
        if (parsed.last4Digits && document.getElementById("payLast4")) document.getElementById("payLast4").value = parsed.last4Digits;
        updateWebReceiptMatchPreview();
        render();
        showToast(t("SMS parsed & matched.", "تم تحليل رسالة الإشعار ومطابقتها."));
      } else if (act === "saveBudget") {
        const name = document.getElementById("bdgName").value.trim();
        const planned = Engine.money(document.getElementById("bdgPlan").value);
        const actual = Engine.money(document.getElementById("bdgAct").value);
        if (!name || planned <= 0) return showToast(t("Budget name & planned amount required.", "اسم الموازنة والمبلغ المخطط مطلوبان."));
        const variance = Engine.money(actual - planned);
        const variancePct = Engine.pct4((variance / planned) * 100);
        state.budgets.push({ id: "bdg_" + Date.now(), name, planned, actual, variance, variancePct });
        persist(); showToast(t("Budget saved.", "تم حفظ الموازنة."));
      } else if (act === "newConversation") {
        state.activeConversationId = null;
        state.chatTranscript = [];
        state.aiResponse = "";
        state.aiSearchQueries = [];
        state.aiGroundingSources = [];
        persist();
        showToast(t("Started a new conversation.", "تم بدء محادثة جديدة."));
      } else if (act === "askPreLoginAi") {
        const q = document.getElementById("preLoginAiPrompt")?.value?.trim();
        if (!q) return;
        state.preLoginAiQuestion = q;
        state.isAiLoading = true;
        state.preLoginAiAnswer = state.isArabic ? "جاري التفكير..." : "Thinking...";
        render();
        askSmartGeminiOrHonestLocal(q, "general", (partial) => {
          state.preLoginAiAnswer = partial;
          const el = document.querySelector(".fvg-chat-bubble.assistant");
          if (el) el.innerHTML = formatAiMarkdownHtml(partial);
        }, true).then((ans) => {
          state.isAiLoading = false;
          state.preLoginAiAnswer = ans;
          render();
        });
      } else if (act === "askAi" || act === "askDrawerAi") {
        const inputId = act === "askDrawerAi" ? "drawerAiPrompt" : "aiPrompt";
        const mode = act === "askDrawerAi" ? state.aiDrawerMode : state.aiMode;
        const q = document.getElementById(inputId)?.value?.trim();
        if (!q) return;
        const uRole = state.isArabic ? "المستخدم" : "User";
        const aRole = state.isArabic ? "المساعد المالي" : "Assistant";
        state.chatTranscript.push({ role: uRole, text: q }, { role: aRole, text: state.isArabic ? "جاري التفكير..." : "Thinking..." });
        state.isAiLoading = true;
        render();
        askSmartGeminiOrHonestLocal(q, mode, (partial) => {
          if (state.chatTranscript.length > 0) state.chatTranscript[state.chatTranscript.length - 1].text = partial;
          const bubbles = document.querySelectorAll(".fvg-chat-bubble.assistant");
          const lastBubble = bubbles[bubbles.length - 1];
          if (lastBubble) lastBubble.innerHTML = formatAiMarkdownHtml(partial);
        }, false).then((ans) => {
          state.isAiLoading = false;
          state.aiResponse = ans;
          if (state.chatTranscript.length > 0) state.chatTranscript[state.chatTranscript.length - 1].text = ans;
          if (!state.activeConversationId) {
            const id = "conv_" + Date.now();
            state.activeConversationId = id;
            state.aiConversations.unshift({ id, title: q.slice(0, 28), messages: [...state.chatTranscript] });
          } else {
            const c = state.aiConversations.find((x) => x.id === state.activeConversationId);
            if (c) c.messages = [...state.chatTranscript];
          }
          persist(); render();
        });
      } else if (act === "regenAi") {
        const lastUserMsg = [...state.chatTranscript].reverse().find((m) => String(m.role).startsWith("User") || String(m.role).startsWith("المستخدم"));
        if (!lastUserMsg) return;
        state.isAiLoading = true;
        render();
        askSmartGeminiOrHonestLocal(lastUserMsg.text, state.aiMode).then((newAns) => {
          state.isAiLoading = false;
          state.aiResponse = newAns;
          if (state.chatTranscript.length > 0 && !(String(state.chatTranscript[state.chatTranscript.length - 1].role).startsWith("User") || String(state.chatTranscript[state.chatTranscript.length - 1].role).startsWith("المستخدم"))) {
            state.chatTranscript[state.chatTranscript.length - 1].text = newAns;
          }
          persist(); render();
        });
      } else if (act === "exportTranscript") {
        const txt = state.chatTranscript.map((m, i) => `[${i + 1}] ${m.role}:\n${m.text}\n`).join("\n") || t("No transcript yet.", "لا توجد محادثات.");
        downloadFile("fvg_assistant_transcript.txt", txt, "text/plain");
      } else if (act === "exportCsv") {
        const stmt = renderStatementsAndRatios();
        const csv = state.isArabic
          ? `البند,القيمة\nالإيرادات,${stmt.revenue}\nتكلفة المبيعات,${stmt.cogs}\nصافي الربح,${stmt.netProfit}\nإجمالي الأصول,${stmt.totalAssets}\nإجمالي الالتزامات,${stmt.totalLiabilities}\nحقوق الملكية,${stmt.totalEquityWithNetIncome}\n`
          : `Metric,Value\nRevenue,${stmt.revenue}\nCOGS,${stmt.cogs}\nNet Profit,${stmt.netProfit}\nTotal Assets,${stmt.totalAssets}\nTotal Liabilities,${stmt.totalLiabilities}\nEquity,${stmt.totalEquityWithNetIncome}\n`;
        downloadFile("fvg_report.csv", csv, "text/csv");
      } else if (act === "exportJson") {
        downloadFile("fvg_workspace_backup.json", JSON.stringify(state, null, 2), "application/json");
      } else if (act === "printPdf") {
        window.print();
      } else if (act === "submitPayment") {
        const senderName = document.getElementById("paySender").value.trim();
        const referenceNo = document.getElementById("payRef").value.trim();
        const last4Digits = document.getElementById("payLast4").value.trim();
        const notes = document.getElementById("payNotes").value.trim();
        const planTier = document.getElementById("payPlan").value;
        const amount = document.getElementById("payAmt").value;
        const curr = state.activeOrg?.baseCurrency || "SDG";
        if (!senderName || referenceNo.length < 2) return showToast(t("Sender name & reference required.", "اسم المحوّل ورقم المرجع مطلوبان."));
        const match = computeWebPaymentMatch(planTier, amount, curr, referenceNo);
        const payReq = {
          id: "pay_" + Date.now(),
          orgId: state.activeOrg?.id || "",
          planTier,
          paymentMethod: document.getElementById("payMethod").value,
          amount,
          currency: curr,
          senderName, referenceNo, last4Digits, notes,
          receiptMatchStatus: match.status,
          receiptMatchNote: state.isArabic ? match.noteAr : match.noteEn,
          expectedPlanAmount: match.expectedAmountLabel,
          transferDate: new Date().toISOString().slice(0, 10),
          status: "pending_verification"
        };
        state.paymentRequests.unshift(payReq);
        if (isFirebaseWebConfigured() && state.user?.uid && state.activeOrg?.id) {
          submitPaymentRequestToFirestore(payReq).catch(() => {});
        }
        persist(); showToast(t("Payment proof submitted for Owner review.", "تم إرسال إثبات الدفع للمراجعة عبر المالكة زهراء يوسف."));
      } else if (act === "saveCounterparty") {
        const partyName = document.getElementById("cpParty").value.trim();
        const invoiceNo = document.getElementById("cpInv").value.trim() || "ف";
        const amount = Engine.money(document.getElementById("cpAmt").value);
        if (!partyName || amount <= 0) return showToast(t("Party name & positive amount required.", "اسم الجهة ومبلغ موجب مطلوبان."));
        state.counterpartyItems.unshift({
          id: "cp_" + Date.now(), type: document.getElementById("cpType").value,
          partyName, invoiceNo, amount, paidAmount: 0, dueDate: document.getElementById("cpDue").value, status: "open"
        });
        persist(); showToast(t("AR/AP record saved.", "تم حفظ الذمة."));
      } else if (act === "saveScrap") {
        const itemName = document.getElementById("scrItem").value.trim();
        const qty = Engine.n(document.getElementById("scrQty").value);
        const cost = Engine.n(document.getElementById("scrCost").value);
        const reason = document.getElementById("scrReason").value.trim() || t("Waste", "تلف");
        if (!itemName || qty <= 0) return showToast(t("Item & positive qty required.", "الصنف والكمية مطلوبان."));
        state.scrapLosses.unshift({
          id: "scr_" + Date.now(), itemName, lossType: document.getElementById("scrType").value,
          quantity: qty, unitCost: cost, totalLossCost: Engine.money(qty * cost), reason
        });
        persist(); showToast(t("Scrap loss logged.", "تم تسجيل الفاقد."));
      } else if (act === "submitTicket") {
        const msg = document.getElementById("helpMsg")?.value?.trim();
        if (!msg || msg.length < 3) return showToast(t("Enter your message first (at least 3 chars).", "يرجى كتابة الرسالة (3 أحرف على الأقل)."));
        const tk = {
          id: "tk_" + Date.now(),
          senderName: state.user?.displayName || state.user?.email || "مستخدم",
          senderEmail: state.user?.email || "",
          orgId: state.activeOrg?.id || "",
          orgName: state.activeOrg?.name || "",
          message: msg, status: "open", ownerReply: "", createdAt: new Date().toISOString().slice(0, 10)
        };
        state.supportTickets.unshift(tk);
        if (isFirebaseWebConfigured()) submitSupportTicketToFirestore(tk).catch(() => {});
        persist(); showToast(t("Support request sent.", "تم إرسال تذكرة الدعم."));
      } else if (act === "publishAnnouncement" && isPlatformOwner()) {
        const titleAr = document.getElementById("annTitleAr")?.value?.trim() || "";
        const titleEn = document.getElementById("annTitleEn")?.value?.trim() || "";
        const targetAudience = document.getElementById("annAudience")?.value || "all";
        const targetEmail = document.getElementById("annTargetEmail")?.value?.trim() || "";
        const bodyAr = document.getElementById("annBodyAr")?.value?.trim() || "";
        const bodyEn = document.getElementById("annBodyEn")?.value?.trim() || bodyAr;
        const body = bodyAr || bodyEn;
        const alsoMail = Boolean(document.getElementById("annAlsoMail")?.checked);
        if (!titleAr && !titleEn) return showToast(t("Enter notification title.", "أدخل عنوان الإشعار."));
        const ann = {
          id: "ann_" + Date.now(),
          titleAr: titleAr || titleEn, titleEn: titleEn || titleAr,
          bodyAr: bodyAr || bodyEn, bodyEn: bodyEn || bodyAr,
          targetAudience, targetEmail,
          active: true, createdByEmail: Engine.OWNER_EMAIL
        };
        state.announcements.unshift(ann);
        if (isFirebaseWebConfigured()) saveAnnouncementToFirestore(ann).catch(() => {});
        if (alsoMail) {
          const recipients = targetAudience === "specific_email" && targetEmail
            ? [targetEmail]
            : state.registeredUsers.map((u) => u.email).filter((e) => e && e.includes("@")).slice(0, 50);
          if (recipients.length) {
            const mailRec = {
              id: "mail_" + Date.now(),
              from: Engine.OWNER_EMAIL,
              to: recipients,
              createdBy: state.user?.uid || "owner",
              mailType: "notification",
              status: "queued",
              message: {
                subject: titleAr || titleEn,
                text: `${body}\n\n— ${t("Owner Zahra Yousif", "المالكة زهراء يوسف")}`
              },
              createdAt: new Date().toISOString().slice(0, 19)
            };
            state.mailOutbox.unshift(mailRec);
            if (isFirebaseWebConfigured() && state.user?.uid) queueMailInFirestore(mailRec).catch(() => {});
          }
        }
        persist(); showToast(t("Notification published.", "تم نشر الإشعار بنجاح."));
      }
    };
  });

  root.querySelectorAll("[data-reverse-je]").forEach((btn) => {
    btn.onclick = () => {
      const orig = state.journalEntries.find((j) => j.id === btn.dataset.reverseJe);
      if (!orig || orig.status !== "posted") return;
      orig.status = "reversed";
      state.journalEntries.push({
        id: "je_rev_" + Date.now(), referenceNo: orig.referenceNo + t("-REV", "-عكسي"),
        date: new Date().toISOString().slice(0, 10),
        description: t("Reversal of ", "عكس القيد ") + orig.referenceNo + ": " + orig.description,
        status: "posted", total: orig.total,
        lines: orig.lines.map((l) => ({ accountCode: l.accountCode, debit: l.credit, credit: l.debit }))
      });
      persist(); showToast(t("Journal reversed.", "تم إنشاء القيد العكسي."));
    };
  });

  root.querySelectorAll("[data-stock-move]").forEach((btn) => {
    btn.onclick = () => {
      const [dir, itemId] = btn.dataset.stockMove.split(":");
      const item = state.inventoryItems.find((i) => i.id === itemId);
      if (!item) return;
      item.qty = dir === "in" ? Engine.money(Engine.n(item.qty) + 10) : Math.max(0, Engine.money(Engine.n(item.qty) - 10));
      persist(); showToast(t(`${item.sku} stock: ${item.qty}`, `رصيد ${item.sku}: ${item.qty}`));
    };
  });
}

function initLocalWorkspace(email, displayName, orgName) {
  const cleanEmail = email.trim();
  const isNew = !state.user || state.user.email.toLowerCase() !== cleanEmail.toLowerCase();
  if (isNew) {
    state.user = {
      uid: "usr_" + cleanEmail.toLowerCase().replace(/[^a-z0-9]/g, "_"),
      email: cleanEmail,
      displayName,
      trialDurationHours: Engine.TRIAL_DURATION_HOURS,
      trialStartedAtMillis: Date.now(),
      tourCompleted: false
    };
    const org = {
      id: "org_" + Date.now(),
      name: orgName,
      baseCurrency: "SDG",
      sectorType: "manufacturing",
      planTier: "trial",
      subscriptionStatus: "trial",
      trialDurationHours: Engine.TRIAL_DURATION_HOURS,
      trialEndsAtMillis: Date.now() + Engine.TRIAL_DURATION_MILLIS
    };
    state.organizations.push(org);
    state.activeOrg = org;
    logAudit("INIT_WORKSPACE", "organization", `Initialized ${orgName} (168h / 7-day trial)`);
  }
  upsertRegisteredUser(cleanEmail, state.user.displayName, state.activeOrg?.name || orgName);
  ensureWelcomeMailQueued(cleanEmail, state.user.displayName);
  recordPlatformVisitOnce("dashboard");
  state.stage = "app";
  if (isAuthorizedAdminEmailWeb(cleanEmail)) {
    state.adminPreviewUserApp = false;
    state.showWelcomePopup = false;
  } else {
    state.showWelcomePopup = true;
  }
  persist();
  render();
}

function recordPriceObservationItem(name, sku, price) {
  const existing = state.products.find((p) => p.name.toLowerCase() === name.toLowerCase());
  const prev = existing ? existing.currentPrice : 0;
  const delta = Engine.calculatePercentageDelta(prev, price);
  if (existing) existing.currentPrice = price;
  else state.products.push({ id: "p_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6), name, sku, currentPrice: price });
  state.priceObservations.push({
    id: "obs_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    productName: name, sku, previousPrice: prev, price,
    pctChange: delta.pctChange, direction: delta.direction, isUnusual: delta.isUnusual,
    date: new Date().toISOString().slice(0, 10)
  });
  logAudit("SAVE_PRICE", "priceObservation", `${name}: ${price}`);
}

let cachedFb = null;
async function getFirebaseHandles() {
  if (cachedFb) return cachedFb;
  const [fbApp, fbAuth, fbStore, fbAi] = await Promise.all([
    import("https://www.gstatic.com/firebasejs/12.0.0/firebase-app.js"),
    import("https://www.gstatic.com/firebasejs/12.0.0/firebase-auth.js"),
    import("https://www.gstatic.com/firebasejs/12.0.0/firebase-firestore.js"),
    import("https://www.gstatic.com/firebasejs/12.0.0/firebase-ai.js").catch(() => null)
  ]);
  const app = fbApp.getApps().length ? fbApp.getApps()[0] : fbApp.initializeApp(FVG_FIREBASE_CONFIG);
  const auth = fbAuth.getAuth(app);
  const db = fbStore.getFirestore(app, FVG_FIREBASE_CONFIG.databaseId);
  cachedFb = { app, fbApp, fbAuth, fbStore, fbAi, auth, db };
  return cachedFb;
}

async function queueMailInFirestore(entry) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.setDoc(fbStore.doc(db, "mail", entry.id), {
    from: Engine.OWNER_EMAIL,
    to: entry.to,
    createdBy: entry.createdBy,
    recipientName: entry.recipientName || state.user?.displayName || "",
    mailType: entry.mailType,
    message: entry.message,
    createdAt: fbStore.serverTimestamp()
  });
}

async function logVisitInFirestore(visit) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.setDoc(fbStore.doc(db, "platformVisits", visit.id), {
    platform: visit.platform,
    screen: visit.screen,
    visitorKey: visit.visitorKey,
    userId: visit.userId,
    createdAt: fbStore.serverTimestamp()
  });
}

async function savePlanToFirestore(plan) {
  const { db, fbStore } = await getFirebaseHandles();
  const key = plan.key || plan.code || plan.id;
  await fbStore.setDoc(fbStore.doc(db, "platformPlans", key), {
    key,
    nameEn: plan.nameEn,
    nameAr: plan.nameAr,
    priceUsd: String(plan.priceUsd),
    priceSdg: String(plan.priceSdg),
    billingCycle: "monthly",
    tierLevel: Number(plan.tierLevel || 1),
    maxOrgs: Number(plan.maxOrgs || 1),
    maxUsers: Number(plan.maxUsers || 5),
    maxProducts: Number(plan.maxProducts || 25),
    allowAllCostingMethods: Boolean(plan.allowAllCostingMethods),
    allowBomAndProduction: Boolean(plan.allowBomAndProduction),
    allowAiAssistant: Boolean(plan.allowAiAssistant),
    allowOcr: Boolean(plan.allowOcr),
    allowPdfXlsxExport: Boolean(plan.allowPdfXlsxExport),
    allowAuditAndBackup: Boolean(plan.allowAuditAndBackup),
    allowSectorAdvancedTools: Boolean(plan.allowSectorAdvancedTools),
    active: true,
    notesEn: plan.notesEn || "",
    notesAr: plan.notesAr || "",
    featuresEn: plan.featuresEn || "",
    featuresAr: plan.featuresAr || "",
    updatedAt: fbStore.serverTimestamp()
  }, { merge: true });
}

async function deletePlanFromFirestore(planKey) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.deleteDoc(fbStore.doc(db, "platformPlans", planKey));
}

async function saveAnnouncementToFirestore(ann) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.setDoc(fbStore.doc(db, "announcements", ann.id), {
    titleEn: ann.titleEn,
    titleAr: ann.titleAr,
    bodyEn: ann.bodyEn,
    bodyAr: ann.bodyAr,
    targetAudience: ann.targetAudience || "all",
    targetEmail: ann.targetEmail || "",
    active: true,
    createdByEmail: Engine.OWNER_EMAIL,
    createdAt: fbStore.serverTimestamp(),
    updatedAt: fbStore.serverTimestamp()
  });
}

async function submitPaymentRequestToFirestore(req) {
  const { db, fbStore, auth } = await getFirebaseHandles();
  const uid = auth.currentUser?.uid;
  if (!uid || !req.orgId) return;
  await fbStore.setDoc(fbStore.doc(db, "organizations", req.orgId, "paymentRequests", req.id), {
    orgId: req.orgId,
    ownerId: state.activeOrg?.ownerId || uid,
    memberUids: state.activeOrg?.memberUids || [uid],
    createdBy: uid,
    planTier: req.planTier,
    paymentMethod: req.paymentMethod,
    amount: String(req.amount || "0"),
    currency: req.currency || "SDG",
    senderName: req.senderName,
    referenceNo: req.referenceNo,
    last4Digits: req.last4Digits || null,
    transferDate: req.transferDate || new Date().toISOString().slice(0, 10),
    attachmentNote: null,
    notes: req.notes || null,
    receiptMatchStatus: req.receiptMatchStatus || "unverified",
    receiptMatchNote: req.receiptMatchNote || null,
    expectedPlanAmount: req.expectedPlanAmount || null,
    status: "pending_verification",
    reviewedBy: null,
    decisionActorEmail: null,
    reviewNotes: null,
    createdAt: fbStore.serverTimestamp(),
    updatedAt: fbStore.serverTimestamp()
  });
}

async function submitSupportTicketToFirestore(tk) {
  const { db, fbStore, auth } = await getFirebaseHandles();
  const uid = auth.currentUser?.uid || null;
  const payload = {
    senderName: tk.senderName,
    senderEmail: tk.senderEmail || null,
    orgId: tk.orgId || null,
    orgName: tk.orgName || null,
    message: tk.message,
    status: "open",
    createdAt: fbStore.serverTimestamp(),
    updatedAt: fbStore.serverTimestamp()
  };
  if (uid) payload.createdBy = uid;
  await fbStore.setDoc(fbStore.doc(db, "supportTickets", tk.id), payload);
}

async function ownerUpdatePaymentInFirestore(req) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.updateDoc(fbStore.doc(db, "organizations", req.orgId, "paymentRequests", req.id), {
    status: req.status,
    reviewedBy: req.reviewedBy || Engine.OWNER_EMAIL,
    decisionActorEmail: Engine.OWNER_EMAIL,
    reviewNotes: req.reviewNotes || "",
    updatedAt: fbStore.serverTimestamp()
  });
}

async function ownerUpdateOrgInFirestore(org) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.updateDoc(fbStore.doc(db, "organizations", org.id), {
    planTier: org.planTier,
    subscriptionStatus: org.subscriptionStatus,
    trialDurationHours: Number(org.trialDurationHours || 168),
    trialEndsAt: fbStore.Timestamp.fromMillis(Number(org.trialEndsAtMillis || Date.now() + Engine.TRIAL_DURATION_MILLIS)),
    updatedAt: fbStore.serverTimestamp()
  });
}

async function ownerUpdateOrgRolesInFirestore(org) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.updateDoc(fbStore.doc(db, "organizations", org.id), {
    memberUids: org.memberUids,
    roles: org.roles,
    updatedAt: fbStore.serverTimestamp()
  });
}

async function ownerUpdateTicketInFirestore(tk) {
  const { db, fbStore } = await getFirebaseHandles();
  await fbStore.updateDoc(fbStore.doc(db, "supportTickets", tk.id), {
    status: tk.status || "reviewed",
    ownerReply: tk.ownerReply || "",
    updatedAt: fbStore.serverTimestamp()
  });
}

let unsubscribeAdminAlerts = null;
let adminAlertsInitialized = false;

function attachWebAdminNotificationsListener(db, fbStore) {
  if (unsubscribeAdminAlerts) {
    try { unsubscribeAdminAlerts(); } catch {}
    unsubscribeAdminAlerts = null;
  }
  const knownIds = new Set((state.adminNotifications || []).map((x) => x.id));
  adminAlertsInitialized = false;
  unsubscribeAdminAlerts = fbStore.onSnapshot(
    fbStore.collection(db, "adminNotifications"),
    (snap) => {
      const next = snap.docs
        .map((d) => {
          const x = d.data();
          const ms = x.createdAt?.toMillis ? x.createdAt.toMillis() : 0;
          return {
            id: d.id,
            category: x.category || "security_alert",
            severity: x.severity || "info",
            signalType: x.signalType || "",
            titleEn: x.titleEn || "",
            titleAr: x.titleAr || "",
            bodyEn: x.bodyEn || "",
            bodyAr: x.bodyAr || "",
            actorUid: x.actorUid || "",
            actorEmail: x.actorEmail || "",
            orgId: x.orgId || "",
            sourcePlatform: x.sourcePlatform || "cloud_trigger",
            acknowledged: Boolean(x.acknowledged),
            createdAtMs: ms,
            createdAtStr: ms ? new Date(ms).toISOString().slice(0, 16).replace("T", " ") : ""
          };
        })
        .sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0))
        .slice(0, 120);

      if (adminAlertsInitialized) {
        const fresh = next.find((n) => !knownIds.has(n.id) && !n.acknowledged);
        if (fresh) {
          const title = state.isArabic ? (fresh.titleAr || fresh.titleEn) : (fresh.titleEn || fresh.titleAr);
          const body = state.isArabic ? (fresh.bodyAr || fresh.bodyEn) : (fresh.bodyEn || fresh.bodyAr);
          state.toast = `🔔 ${title}`;
          if (typeof Notification !== "undefined" && Notification.permission === "granted") {
            try { new Notification(title, { body, icon: "./assets/fvg_logo_dark.png" }); } catch {}
          }
        }
      }
      knownIds.clear();
      next.forEach((n) => knownIds.add(n.id));
      adminAlertsInitialized = true;
      state.adminNotifications = next;
      if (isPlatformOwner()) render();
    },
    () => {}
  );
}

async function updateWebAdminNotificationRead(notifId, acknowledged) {
  state.adminNotifications = (state.adminNotifications || []).map((n) =>
    n.id === notifId ? { ...n, acknowledged } : n
  );
  render();
  if (!isFirebaseWebConfigured()) return;
  try {
    const { db, fbStore } = await getFirebaseHandles();
    await fbStore.updateDoc(fbStore.doc(db, "adminNotifications", notifId), {
      acknowledged: Boolean(acknowledged),
      updatedAt: fbStore.serverTimestamp()
    });
  } catch {}
}

async function markAllWebAdminNotificationsRead() {
  const unreadIds = (state.adminNotifications || []).filter((n) => !n.acknowledged && !n.synthetic).map((n) => n.id);
  if (!unreadIds.length) return;
  state.adminNotifications = state.adminNotifications.map((n) => ({ ...n, acknowledged: true }));
  render();
  if (!isFirebaseWebConfigured()) return;
  try {
    const { db, fbStore } = await getFirebaseHandles();
    await Promise.all(
      unreadIds.map((id) =>
        fbStore.updateDoc(fbStore.doc(db, "adminNotifications", id), {
          acknowledged: true,
          updatedAt: fbStore.serverTimestamp()
        }).catch(() => {})
      )
    );
    showToast(t("All admin alerts marked as read.", "تم تحديد جميع التنبيهات كمقروءة."));
  } catch {}
}

async function deleteWebAdminNotification(notifId) {
  state.adminNotifications = (state.adminNotifications || []).filter((n) => n.id !== notifId);
  render();
  if (!isFirebaseWebConfigured()) return;
  try {
    const { db, fbStore } = await getFirebaseHandles();
    await fbStore.deleteDoc(fbStore.doc(db, "adminNotifications", notifId));
  } catch {}
}

async function runWebAdminSecurityScan() {
  const now = Date.now();
  const tenMinAgo = now - 10 * 60000;
  const recentRegs = (state.registeredUsers || []).filter((u) => (u.createdAtMs || 0) >= tenMinAgo).length;
  const pendingPays = (state.paymentRequests || []).filter((p) => p.status === "pending_verification").length;
  const expiredTrials = (state.organizations || []).filter(
    (o) => o.subscriptionStatus === "trial" && (o.trialEndsAtMillis || Infinity) < now
  ).length;
  if (recentRegs >= 5) {
    await pushWebAdminSecurityAlert({
      signalType: "rapid_user_registration",
      severity: "critical",
      titleEn: `Rapid Registration Burst (${recentRegs} users / 10m)`,
      titleAr: `رصد تسجيلات سريعة (${recentRegs} خلال 10 دقائق)`,
      bodyEn: `Detected ${recentRegs} registrations within 10 minutes during manual security scan.`,
      bodyAr: `تم رصد ${recentRegs} تسجيلات خلال آخر 10 دقائق أثناء الفحص الأمني.`
    });
  }
  showToast(t(
    `Security scan complete: ${recentRegs} recent signups • ${pendingPays} pending payments • ${expiredTrials} expired trial(s).`,
    `نتيجة الفحص الأمني: ${recentRegs} تسجيل حديث • ${pendingPays} مدفوعات معلقة • ${expiredTrials} تجربة منتهية.`
  ));
}

async function hydrateWorkspaceFromFirebaseUser(cred, fallbackDisplayName, orgName, optionalEmail = "") {
  const { db, fbStore } = await getFirebaseHandles();
  const { doc, getDoc, getDocs, setDoc, collection, collectionGroup, serverTimestamp, Timestamp } = fbStore;
  const uid = cred.user.uid;
  const verifiedAuthEmail = String(cred.user.email || "").trim();
  const cleanOptEmail = String(optionalEmail || "").trim();
  const safeOptEmail = (cleanOptEmail.includes("@") && !isAuthorizedAdminEmailWeb(cleanOptEmail)) ? cleanOptEmail : "";
  const email = verifiedAuthEmail || safeOptEmail;
  const accountIdentifier = email || String(cred.user.phoneNumber || "").trim() || uid;
  const isOwner = verifiedAuthEmail.toLowerCase() === Engine.OWNER_EMAIL.toLowerCase();
  const uRef = doc(db, "users", uid);
  const uSnap = await getDoc(uRef);
  let orgId = uSnap.exists() ? uSnap.data().activeOrgId : null;
  if (!orgId) {
    orgId = "org_" + uid.slice(0, 12);
    const trialEnd = Timestamp.fromMillis(Date.now() + Engine.TRIAL_DURATION_MILLIS);
    await setDoc(doc(db, "organizations", orgId), {
      name: orgName, baseCurrency: "SDG", sectorType: "manufacturing",
      ownerId: uid, memberUids: [uid], roles: { [uid]: "owner" },
      planTier: "trial", subscriptionStatus: "trial",
      trialDurationHours: Engine.TRIAL_DURATION_HOURS,
      trialStartedAt: serverTimestamp(), trialEndsAt: trialEnd,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    });
    await setDoc(uRef, {
      userId: uid, email, phoneNumber: cred.user.phoneNumber || "",
      displayName: fallbackDisplayName || cred.user.displayName || accountIdentifier,
      activeOrgId: orgId, preferredLanguage: state.isArabic ? "ar" : "en",
      themeMode: state.themeMode, darkMode: state.themeMode === "dark",
      fontScale: state.fontScale, tourCompleted: false,
      trialDurationHours: Engine.TRIAL_DURATION_HOURS,
      trialStartedAt: serverTimestamp(),
      createdAt: serverTimestamp(), updatedAt: serverTimestamp()
    });
  }
  const [oSnap, plansSnap, annSnap] = await Promise.all([
    getDoc(doc(db, "organizations", orgId)),
    getDocs(collection(db, "platformPlans")).catch(() => null),
    getDocs(collection(db, "announcements")).catch(() => null)
  ]);
  const od = oSnap.data();
  const uData = uSnap.exists() ? uSnap.data() : {};
  const resolvedStoredEmail = verifiedAuthEmail || safeOptEmail || (uData?.email && !isAuthorizedAdminEmailWeb(uData.email) ? uData.email : "");
  const durHours = od?.trialDurationHours || uData?.trialDurationHours || Engine.TRIAL_DURATION_HOURS;
  const startMs = uData?.trialStartedAt?.toMillis ? uData.trialStartedAt.toMillis() : Date.now();
  const orgEndMs = od?.trialEndsAt?.toMillis ? od.trialEndsAt.toMillis() : (startMs + durHours * 3600000);
  if (plansSnap && !plansSnap.empty) {
    state.platformPlans = plansSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  }
  if (annSnap && !annSnap.empty) {
    state.announcements = annSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  }
  state.user = {
    uid,
    email: resolvedStoredEmail,
    phoneNumber: cred.user.phoneNumber || uData?.phoneNumber || "",
    displayName: uData?.displayName || fallbackDisplayName || cred.user.displayName || accountIdentifier,
    trialDurationHours: durHours,
    trialStartedAtMillis: startMs,
    tourCompleted: Boolean(uData?.tourCompleted)
  };
  state.activeOrg = { id: orgId, name: od?.name || orgName, baseCurrency: od?.baseCurrency || "SDG", sectorType: od?.sectorType || "manufacturing", planTier: od?.planTier || "trial", subscriptionStatus: od?.subscriptionStatus || "trial", trialDurationHours: durHours, trialEndsAtMillis: orgEndMs, ownerId: od?.ownerId || uid, memberUids: od?.memberUids || [uid] };
  if (!state.organizations.some((o) => o.id === orgId)) state.organizations.push(state.activeOrg);
  if (isOwner) {
    const [allUsersSnap, allOrgsSnap, allTicketsSnap, allMailSnap, allVisitsSnap, allPaySnap] = await Promise.all([
      getDocs(collection(db, "users")).catch(() => null),
      getDocs(collection(db, "organizations")).catch(() => null),
      getDocs(collection(db, "supportTickets")).catch(() => null),
      getDocs(collection(db, "mail")).catch(() => null),
      getDocs(collection(db, "platformVisits")).catch(() => null),
      getDocs(collectionGroup(db, "paymentRequests")).catch(() => null)
    ]);
    if (allUsersSnap && !allUsersSnap.empty) {
      state.registeredUsers = allUsersSnap.docs.map((d) => {
        const x = d.data();
        return {
          uid: d.id,
          email: x.email || x.phoneNumber || "",
          displayName: x.displayName || "",
          orgName: x.activeOrgId || "",
          preferredLanguage: x.preferredLanguage || "ar",
          createdAtMs: x.createdAt?.toMillis ? x.createdAt.toMillis() : 0,
          lastSeenAt: ""
        };
      });
    }
    if (allOrgsSnap && !allOrgsSnap.empty) {
      state.organizations = allOrgsSnap.docs.map((d) => {
        const x = d.data();
        return { id: d.id, name: x.name || d.id, baseCurrency: x.baseCurrency || "SDG", sectorType: x.sectorType || "manufacturing", planTier: x.planTier || "trial", subscriptionStatus: x.subscriptionStatus || "trial", trialDurationHours: x.trialDurationHours || 168, trialEndsAtMillis: x.trialEndsAt?.toMillis ? x.trialEndsAt.toMillis() : Date.now(), ownerId: x.ownerId, memberUids: x.memberUids, roles: x.roles || {} };
      });
    }
    if (allTicketsSnap && !allTicketsSnap.empty) {
      state.supportTickets = allTicketsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    }
    if (allMailSnap && !allMailSnap.empty) {
      state.mailOutbox = allMailSnap.docs.map((d) => ({ id: d.id, ...d.data(), status: d.data().delivery?.state || "queued", createdAt: "" }));
    }
    if (allVisitsSnap && !allVisitsSnap.empty) {
      state.platformVisits = allVisitsSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    }
    if (allPaySnap && !allPaySnap.empty) {
      state.paymentRequests = allPaySnap.docs.map((d) => ({ id: d.id, ...d.data() }));
    }
  }
  upsertRegisteredUser(resolvedStoredEmail || accountIdentifier, state.user.displayName, state.activeOrg.name);
  ensureWelcomeMailQueued(resolvedStoredEmail, state.user.displayName);
  recordPlatformVisitOnce("dashboard");
  state.stage = "app";
  if (isOwner) {
    state.adminPreviewUserApp = false;
    state.showWelcomePopup = false;
    resetWebAdminFailState();
    attachWebAdminNotificationsListener(db, fbStore);
    const pendingSec = consumeQueuedWebAdminSecurityEvents();
    if (pendingSec.length) {
      pendingSec.forEach((ev) => {
        pushWebAdminSecurityAlert({
          signalType: "failed_admin_login",
          severity: Number(ev.count || 1) >= 3 ? "critical" : "warning",
          titleEn: `Failed Admin Login Attempt #${ev.count || 1} (${ev.email || "unknown"})`,
          titleAr: `محاولة دخول إدارية فاشلة رقم ${ev.count || 1} (${ev.email || "غير معروف"})`,
          bodyEn: `Blocked invalid admin credential attempt before unlock (${ev.reason || "failed"}).`,
          bodyAr: `تم رصد ومنع محاولة دخول غير صالحة لبوابة الإدارة (${ev.reason || "فشل"}).`,
          actorEmail: ev.email || ""
        });
      });
    }
  } else if (!state.user.tourCompleted) {
    state.showWelcomePopup = true;
  }
  persist();
  render();
  showToast(t("Signed in & synced with Firestore.", "تم تسجيل الدخول والمزامنة مع قاعدة البيانات."));
}

async function openWebOwnerAuditLogs(orgId) {
  const org = state.organizations.find((o) => o.id === orgId);
  state.ownerAuditDialogOrgName = org?.name || orgId;
  state.ownerAuditLogs = [];
  state.ownerAuditLoading = true;
  render();
  if (isFirebaseWebConfigured()) {
    try {
      const { db, fbStore } = await getFirebaseHandles();
      const snap = await fbStore.getDocs(fbStore.collection(db, "organizations", orgId, "auditLogs"));
      state.ownerAuditLogs = snap.docs
        .map((d) => {
          const x = d.data();
          const ms = x.createdAt?.toMillis ? x.createdAt.toMillis() : 0;
          return {
            id: d.id,
            ...x,
            createdAtMs: ms,
            createdAtStr: ms ? new Date(ms).toISOString().slice(0, 16).replace("T", " ") : ""
          };
        })
        .sort((a, b) => (b.createdAtMs || 0) - (a.createdAtMs || 0))
        .slice(0, 80);
    } catch {
      state.ownerAuditLogs = state.auditLogs.slice(0, 40);
    }
  } else {
    state.ownerAuditLogs = state.auditLogs.slice(0, 40);
  }
  state.ownerAuditLoading = false;
  render();
}

function formatWebFirebaseError(err, providerLabel) {
  const code = String(err?.code || "");
  const msg = String(err?.message || "");
  if (code.includes("operation-not-allowed") || msg.includes("operation-not-allowed")) {
    return t(
      `The ${providerLabel} provider is disabled in Firebase Console (Authentication -> Sign-in method).`,
      `طريقة الدخول (${providerLabel}) غير مفعّلة حالياً في Firebase Console من تبويب Authentication -> Sign-in method.`
    );
  }
  if (code.includes("wrong-password") || code.includes("invalid-credential")) {
    return t("Incorrect email or password.", "البريد الإلكتروني أو كلمة المرور غير صحيحة.");
  }
  if (code.includes("user-not-found")) {
    return t("No account found; switch to Register to create one.", "لا يوجد حساب مسجل؛ اختاري 'إنشاء حساب' للتسجيل.");
  }
  if (code.includes("email-already-in-use")) {
    return t("Email already registered; please sign in instead.", "هذا البريد مسجل بالفعل؛ يرجى تسجيل الدخول بدلاً من ذلك.");
  }
  if (code.includes("invalid-phone-number")) {
    return t("Invalid phone number format. Use e.g. +249912345678.", "صيغة رقم الهاتف غير صحيحة؛ أدخل الرمز الدولي مثل +249912345678.");
  }
  if (code.includes("invalid-verification-code")) {
    return t("Invalid SMS verification code.", "رمز التحقق SMS غير صحيح.");
  }
  return msg || t(`Authentication failed via ${providerLabel}.`, `تعذّرت المصادقة عبر ${providerLabel}.`);
}

function normalizeWebPhoneE164(raw) {
  const clean = String(raw || "").trim().replace(/[\s\-()]/g, "");
  if (clean.startsWith("+")) return "+" + clean.slice(1).replace(/\D/g, "");
  if (clean.startsWith("00")) return "+" + clean.slice(2).replace(/\D/g, "");
  const digits = clean.replace(/\D/g, "");
  if (clean.startsWith("0") && digits.length === 10) return "+249" + digits.slice(1);
  return "+" + digits;
}

async function sendWebPhoneOtp(rawPhone, name, orgName, optionalEmail) {
  const e164 = normalizeWebPhoneE164(rawPhone);
  if (e164.length < 9) {
    return showToast(t("Enter a valid international phone number (e.g. +249912345678).", "أدخل رقم هاتف دولي صحيح مثل +249912345678."));
  }
  try {
    const { fbAuth, auth } = await getFirebaseHandles();
    const box = document.getElementById("fvgRecaptchaBox");
    if (!box) return showToast(t("Verification widget unavailable; refresh page.", "تعذر تهيئة عنصر التحقق؛ أعد المحاولة."));
    box.innerHTML = "";
    const verifier = new fbAuth.RecaptchaVerifier(auth, box, { size: "invisible" });
    const confirmation = await fbAuth.signInWithPhoneNumber(auth, e164, verifier);
    state.phonePendingConfirmation = confirmation;
    state.phonePendingE164 = e164;
    state.phonePendingOptionalEmail = optionalEmail || "";
    state.phonePendingName = name || e164;
    state.phonePendingOrg = orgName || t("FVG Workspace", "مساحة عمل المنصة");
    state.phoneOtpStep = "code";
    render();
    showToast(t("SMS verification code sent.", "تم إرسال رمز التحقق SMS إلى هاتفك."));
  } catch (err) {
    showToast(formatWebFirebaseError(err, "Phone"));
  }
}

async function verifyWebPhoneOtp(code) {
  if (!state.phonePendingConfirmation || String(code || "").length < 4) {
    return showToast(t("Enter the SMS verification code.", "أدخل رمز التحقق المرسل في الرسالة النصية."));
  }
  try {
    const cred = await state.phonePendingConfirmation.confirm(code);
    state.phoneOtpStep = "input";
    state.phonePendingConfirmation = null;
    await hydrateWorkspaceFromFirebaseUser(
      cred,
      state.phonePendingName || cred.user.phoneNumber,
      state.phonePendingOrg || t("FVG Workspace", "مساحة عمل المنصة"),
      state.phonePendingOptionalEmail
    );
  } catch (err) {
    showToast(formatWebFirebaseError(err, "Phone OTP"));
  }
}

async function launchWithFirebaseWeb(orgName) {
  try {
    const { fbAuth, auth } = await getFirebaseHandles();
    const cred = await fbAuth.signInWithPopup(auth, new fbAuth.GoogleAuthProvider());
    await hydrateWorkspaceFromFirebaseUser(cred, cred.user.displayName, orgName);
  } catch (err) {
    showToast(formatWebFirebaseError(err, "Google"));
  }
}

async function launchWithFirebaseOAuthProvider(providerId, orgName) {
  try {
    const { fbAuth, auth } = await getFirebaseHandles();
    const provider = providerId === "facebook.com"
      ? new fbAuth.FacebookAuthProvider()
      : new fbAuth.OAuthProvider(providerId);
    const cred = await fbAuth.signInWithPopup(auth, provider);
    await hydrateWorkspaceFromFirebaseUser(cred, cred.user.displayName, orgName);
  } catch (err) {
    showToast(formatWebFirebaseError(err, providerId));
  }
}

async function launchWithFirebaseEmailPassword(email, pass, name, orgName, opts = {}) {
  try {
    const { fbAuth, auth } = await getFirebaseHandles();
    let cred;
    try {
      cred = await fbAuth.signInWithEmailAndPassword(auth, email, pass);
    } catch (signInErr) {
      if (opts.isAdminLogin) {
        if (isWebAdminSetupCompleted() && verifyWebAdminPassword(pass) && isAuthorizedAdminEmailWeb(email)) {
          try {
            cred = await fbAuth.createUserWithEmailAndPassword(auth, email, pass);
          } catch {
            initLocalWorkspace(email, name, orgName);
            return;
          }
        } else {
          throw signInErr;
        }
      } else if (opts.registerMode) {
        cred = await fbAuth.createUserWithEmailAndPassword(auth, email, pass);
      } else {
        throw signInErr;
      }
    }
    await hydrateWorkspaceFromFirebaseUser(cred, name, orgName);
  } catch (err) {
    if (opts.isAdminLogin) {
      const cnt = recordFailedWebAdminAttempt(email, "firebase_auth_failed");
      const rem = getWebAdminLockoutSeconds();
      showToast(rem > 0
        ? t(`Too many failed admin attempts. Locked for ${rem}s.`, `محاولات متكررة فاشلة. تم الحظر مؤقتاً لمدة ${rem} ثانية.`)
        : t(`Invalid administrator credentials (${cnt}/3).`, `بيانات الدخول الإدارية غير صحيحة (${cnt}/3).`));
    } else {
      showToast(formatWebFirebaseError(err, "Email"));
    }
  }
}

async function sendFirebasePasswordReset(email) {
  try {
    const { fbAuth, auth } = await getFirebaseHandles();
    await fbAuth.sendPasswordResetEmail(auth, email);
    showToast(t(`Password reset email sent to ${email}.`, `تم إرسال رابط استعادة كلمة المرور إلى ${email}.`));
  } catch (err) {
    showToast(t("Reset failed: ", "تعذّر إرسال الرابط: ") + (err?.message || ""));
  }
}

function render() {
  document.documentElement.dir = state.isArabic ? "rtl" : "ltr";
  document.documentElement.lang = state.isArabic ? "ar" : "en";
  document.documentElement.setAttribute("data-theme", state.themeMode);
  document.documentElement.style.setProperty("--fvg-font-scale", String(state.fontScale || 1));

  const root = document.getElementById("app");
  if (!root) return;

  if (state.stage === "splash") root.innerHTML = renderSplash();
  else if (state.stage === "public") root.innerHTML = renderPublicSite();
  else if (isPlatformOwner() && !state.adminPreviewUserApp) root.innerHTML = renderOwnerAdminShell();
  else root.innerHTML = renderAppShell();

  bindEvents();
}

setInterval(() => {
  document.querySelectorAll("[data-trial-banner]").forEach((el) => {
    const raw = el.dataset.trialBanner;
    const target = raw === "preview" ? Date.now() + Engine.TRIAL_DURATION_MILLIS : Number(raw);
    if (!Number.isFinite(target)) return;
    const cd = Engine.computeTrialCountdown(target, Date.now());
    const pad = (v) => String(v).padStart(2, "0");
    const setT = (k, val) => { const node = el.querySelector(`[data-cd="${k}"]`); if (node) node.textContent = pad(val); };
    setT("d", cd.days); setT("h", cd.hours); setT("m", cd.minutes); setT("s", cd.seconds);
  });
}, 1000);

function syncAdminRouteFromLocation() {
  const path = window.location.pathname || "";
  const hash = window.location.hash || "";
  if (path.endsWith("/admin") || hash === "#/admin") {
    if (isPlatformOwner() && state.stage === "app") {
      state.adminPreviewUserApp = false;
      state.secondaryView = "owner_admin";
    } else if (state.stage !== "app") {
      state.stage = "public";
      state.publicPage = 0;
      state.showAdminModal = true;
    } else {
      state.toast = t("Restricted /admin route: Authorized Administrators only.", "مسار /admin مقيد ومخصص للإدارة فقط.");
      pushWebAdminSecurityAlert({
        signalType: "unauthorized_admin_attempt",
        severity: "critical",
        titleEn: `Unauthorized /admin Route Access (${state.user?.email || state.user?.phoneNumber || "user"})`,
        titleAr: `محاولة وصول غير مصرح بها لمسار /admin`,
        bodyEn: "A non-administrator account attempted to navigate directly to /admin.",
        bodyAr: "حاول حساب مستخدم عادي الوصول مباشرة إلى لوحة الإدارة /admin وتم حظره."
      });
    }
  }
}

window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  state.deferredInstallPrompt = e;
  render();
});

window.addEventListener("appinstalled", () => {
  state.deferredInstallPrompt = null;
  const v = {
    id: "vis_pwa_install_" + Date.now(),
    platform: "web_pwa",
    screen: "pwa_install",
    visitorKey: getOrCreateVisitorId(),
    userId: state.user?.uid || "",
    createdAt: new Date().toISOString().slice(0, 19)
  };
  state.platformVisits.unshift(v);
  if (isFirebaseWebConfigured()) logVisitInFirestore(v).catch(() => {});
  persist();
});

window.addEventListener("hashchange", () => { syncAdminRouteFromLocation(); render(); });
window.addEventListener("popstate", () => { syncAdminRouteFromLocation(); render(); });
window.addEventListener("online", () => {
  state.isOnline = true;
  if (state.offlineQueue.length) flushWebOfflineQueue();
  else render();
});
window.addEventListener("offline", () => {
  state.isOnline = false;
  render();
});

function computeWebPaymentMatch(planTier, amount, curr, referenceNo) {
  const plan = state.platformPlans.find((p) => (p.key || p.code || p.id) === planTier);
  const existingRefs = state.paymentRequests.map((r) => r.referenceNo).filter(Boolean);
  return Engine.evaluatePaymentReceiptMatch(amount, curr, referenceNo, plan?.priceUsd || 29, plan?.priceSdg || 65000, existingRefs);
}

function updateWebReceiptMatchPreview() {
  const planTier = document.getElementById("payPlan")?.value || "professional";
  const amount = document.getElementById("payAmt")?.value || "";
  const referenceNo = document.getElementById("payRef")?.value || "";
  const curr = state.activeOrg?.baseCurrency || "SDG";
  state.receiptMatchPreview = computeWebPaymentMatch(planTier, amount, curr, referenceNo);
}

function runWebCashRunway() {
  state.runwayParams = {
    startingCash: Engine.n(document.getElementById("rwCash")?.value ?? state.runwayParams.startingCash),
    weeklyInflow: Engine.n(document.getElementById("rwIn")?.value ?? state.runwayParams.weeklyInflow),
    weeklyOutflow: Engine.n(document.getElementById("rwOut")?.value ?? state.runwayParams.weeklyOutflow),
    openAr: Engine.n(document.getElementById("rwAr")?.value ?? state.runwayParams.openAr),
    fxShockPct: Engine.n(document.getElementById("rwFx")?.value ?? state.runwayParams.fxShockPct),
    salesDropPct: state.runwayParams.salesDropPct ?? Engine.n(document.getElementById("rwDrop")?.value ?? 20),
    arDelayWeeks: Number(document.getElementById("rwDelay")?.value ?? state.runwayParams.arDelayWeeks ?? 1)
  };
  const dropElVal = document.getElementById("rwDrop")?.value;
  if (dropElVal !== undefined && dropElVal !== "") {
    state.runwayParams.salesDropPct = Engine.n(dropElVal);
  }
  state.cashRunwayResult = Engine.compute13WeekCashRunway({
    startingCash: state.runwayParams.startingCash,
    weeklyBaselineInflow: state.runwayParams.weeklyInflow,
    weeklyBaselineOutflow: state.runwayParams.weeklyOutflow,
    openReceivables: state.runwayParams.openAr,
    openPayables: 0,
    fxShockPct: state.runwayParams.fxShockPct,
    salesDropPct: state.runwayParams.salesDropPct,
    arDelayWeeks: state.runwayParams.arDelayWeeks
  });
  render();
}

function enqueueWebOfflineMutation(opType, title, payload) {
  state.offlineQueue.push({
    id: "oq_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    opType, title, payload,
    baseServerUpdatedAt: Date.now()
  });
  persist();
  showToast(t("Offline: saved locally & queued for auto-sync.", "بدون اتصال: تم الحفظ محلياً وفي طابور المزامنة."));
}

function flushWebOfflineQueue() {
  if (!state.isOnline) return showToast(t("Still offline; queue preserved.", "ما زال الجهاز غير متصل."));
  const queue = [...state.offlineQueue];
  if (!queue.length) return;
  let synced = 0;
  let conflicts = 0;
  queue.forEach((m) => {
    if (m.opType === "PRICE") {
      const existing = state.products.find((p) => p.name.toLowerCase() === String(m.payload.name).toLowerCase());
      if (existing && (existing.updatedAtMillis || 0) > (m.baseServerUpdatedAt || 0)) {
        state.offlineConflicts.push({
          id: "cf_" + m.id,
          opType: m.opType,
          localSummary: m.title,
          serverSummary: `${existing.name}: ${existing.currentPrice}`,
          mutation: m
        });
        conflicts++;
        return;
      }
      recordPriceObservationItem(m.payload.name, m.payload.sku, m.payload.price);
    } else if (m.opType === "JOURNAL") {
      state.journalEntries.push({
        id: "je_" + Date.now() + "_" + Math.random().toString(36).slice(2, 5),
        referenceNo: t("JE-", "قيد-") + String(state.journalEntries.length + 1).padStart(3, "0"),
        date: new Date().toISOString().slice(0, 10),
        description: m.payload.desc, status: "posted", total: m.payload.amt,
        lines: [{ accountCode: m.payload.dr, debit: m.payload.amt, credit: 0 }, { accountCode: m.payload.cr, debit: 0, credit: m.payload.amt }]
      });
    } else if (m.opType === "STOCK") {
      state.inventoryItems.push(m.payload);
    }
    logAudit("OFFLINE_QUEUE_SYNC", m.opType.toLowerCase(), `Synced offline item: ${m.title}`);
    synced++;
  });
  state.offlineQueue = [];
  persist();
  showToast(t(`Hybrid Sync: ${synced} synced, ${conflicts} conflict(s).`, `المزامنة الهجينة: ${synced} متزامن، ${conflicts} تعارض.`));
}

function resolveWebOfflineConflict(conflictId, keepLocal) {
  const cf = state.offlineConflicts.find((x) => x.id === conflictId);
  if (!cf) return;
  if (keepLocal && cf.mutation?.opType === "PRICE") {
    recordPriceObservationItem(cf.mutation.payload.name, cf.mutation.payload.sku, cf.mutation.payload.price);
  }
  state.offlineConflicts = state.offlineConflicts.filter((x) => x.id !== conflictId);
  logAudit("SYNC_CONFLICT_RESOLVED", cf.opType.toLowerCase(), `${keepLocal ? "KEEP_LOCAL" : "KEEP_SERVER"}: ${cf.localSummary}`);
  persist();
  showToast(keepLocal ? t("Conflict resolved: local change applied.", "تم اعتماد التعديل المحلي.") : t("Conflict resolved: server version kept.", "تم الاحتفاظ بنسخة السحابة."));
}

syncAdminRouteFromLocation();
render();

