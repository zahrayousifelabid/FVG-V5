function sanitizeText(raw, maxLen = 200) {
  return String(raw ?? "").replace(/[\r\n]+/g, " ").trim().slice(0, maxLen);
}

function buildNewUserAdminNotification({ userId, displayName = "", email = "", phoneNumber = "" } = {}) {
  const safeUid = sanitizeText(userId, 128) || "unknown";
  const safeName = sanitizeText(displayName, 120) || sanitizeText(email, 120) || sanitizeText(phoneNumber, 32) || "New User";
  const safeEmail = sanitizeText(email, 200);
  const safePhone = sanitizeText(phoneNumber, 32);
  const contact = safeEmail || safePhone || safeUid;

  return {
    category: "new_user",
    severity: "info",
    signalType: "new_registration",
    titleEn: `New user registered: ${safeName}`.slice(0, 180),
    titleAr: `تسجيل مستخدم جديد: ${safeName}`.slice(0, 180),
    bodyEn: `${safeName} (${contact}) created an FVG workspace and started their 7-day trial.`.slice(0, 1000),
    bodyAr: `انضم ${safeName} (${contact}) إلى المنصة وتم تفعيل فترته التجريبية لمدة 7 أيام.`.slice(0, 1000),
    actorUid: safeUid,
    actorEmail: safeEmail || safePhone,
    orgId: "",
    sourcePlatform: "cloud_trigger",
    acknowledged: false
  };
}

function buildRapidRegistrationBurstAlert({ userId, recentCount, windowMinutes = 10, email = "" } = {}) {
  if (Number(recentCount || 0) < 5) return null;
  const safeUid = sanitizeText(userId, 128) || "unknown";
  const safeEmail = sanitizeText(email, 200);
  return {
    category: "security_alert",
    severity: "critical",
    signalType: "rapid_user_registration",
    titleEn: `Suspicious sign-up burst detected (${recentCount} in ${windowMinutes}m)`.slice(0, 180),
    titleAr: `رصد موجة تسجيلات سريعة مريبة (${recentCount} خلال ${windowMinutes} دقائق)`.slice(0, 180),
    bodyEn: `${recentCount} new accounts were created within ${windowMinutes} minutes (latest: ${safeEmail || safeUid}). Verify for automated sign-ups.`.slice(0, 1000),
    bodyAr: `تم إنشاء ${recentCount} حسابات جديدة خلال ${windowMinutes} دقائق (الأحدث: ${safeEmail || safeUid}). يُنصح بمراجعة سجل المستخدمين للتأكد من عدم وجود نشاط آلي مريب.`.slice(0, 1000),
    actorUid: safeUid,
    actorEmail: safeEmail,
    orgId: "",
    sourcePlatform: "cloud_trigger",
    acknowledged: false
  };
}

function evaluateSuspiciousAuditLog({ orgId = "", logData = {} } = {}) {
  const action = sanitizeText(logData.action, 80).toUpperCase();
  const summary = sanitizeText(logData.summary, 400);
  const actorEmail = sanitizeText(logData.actorEmail, 200);
  const actorUid = sanitizeText(logData.createdBy, 128);
  const safeOrgId = sanitizeText(orgId, 128);

  const highRiskActions = new Set([
    "SECURITY_FAILED_ADMIN_LOGIN",
    "SECURITY_UNAUTHORIZED_ADMIN",
    "SUSPICIOUS_ACTIVITY",
    "DELETE_ORGANIZATION",
    "REVOKE_ALL_SESSIONS",
    "ADMIN_PASSWORD_CHANGED",
    "OWNER_RBAC_REMOVE"
  ]);

  const isSuspiciousKeyword =
    action.includes("FAILED") ||
    action.includes("UNAUTHORIZED") ||
    action.includes("TAMPER") ||
    action.includes("SUSPICIOUS") ||
    action.includes("BREACH");

  if (!highRiskActions.has(action) && !isSuspiciousKeyword) {
    return null;
  }

  const isCritical =
    action.includes("FAILED") ||
    action.includes("UNAUTHORIZED") ||
    action.includes("TAMPER") ||
    action.includes("BREACH") ||
    action === "DELETE_ORGANIZATION";

  const signalType =
    action === "SECURITY_FAILED_ADMIN_LOGIN"
      ? "failed_admin_login"
      : action === "SECURITY_UNAUTHORIZED_ADMIN"
        ? "unauthorized_admin_attempt"
        : "suspicious_audit_event";

  return {
    category: "security_alert",
    severity: isCritical ? "critical" : "warning",
    signalType,
    titleEn: `Security Alert [${action}]`.slice(0, 180),
    titleAr: `تنبيه أمني [${action}]`.slice(0, 180),
    bodyEn: `${summary || action} • Actor: ${actorEmail || actorUid || "Unknown"} • Org: ${safeOrgId || "-"}`.slice(0, 1000),
    bodyAr: `${summary || action} • المنفذ: ${actorEmail || actorUid || "غير محدد"} • المنظمة: ${safeOrgId || "-"}`.slice(0, 1000),
    actorUid,
    actorEmail,
    orgId: safeOrgId,
    sourcePlatform: "cloud_trigger",
    acknowledged: false
  };
}

function evaluatePriceSpikeAnomaly({ orgId = "", obsData = {} } = {}) {
  const pct = Number(obsData.pctChange);
  if (!Number.isFinite(pct) || Math.abs(pct) < 40) return null;

  const productName = sanitizeText(obsData.productName, 120) || "Product";
  const currency = sanitizeText(obsData.currency, 8) || "SDG";
  const prev = sanitizeText(obsData.previousPrice, 32);
  const curr = sanitizeText(obsData.price, 32);
  const actorUid = sanitizeText(obsData.createdBy, 128);
  const safeOrgId = sanitizeText(orgId, 128);
  const isCritical = Math.abs(pct) >= 100;

  return {
    category: "security_alert",
    severity: isCritical ? "critical" : "warning",
    signalType: "unusual_price_spike",
    titleEn: `Abnormal Price Spike (${pct > 0 ? "+" : ""}${pct}%): ${productName}`.slice(0, 180),
    titleAr: `قفزة سعرية غير طبيعية (${pct > 0 ? "+" : ""}${pct}%): ${productName}`.slice(0, 180),
    bodyEn: `${productName} changed from ${prev} to ${curr} ${currency} in org ${safeOrgId}. Check for data entry tampering or severe inflation shock.`.slice(0, 1000),
    bodyAr: `تغيّر سعر ${productName} من ${prev} إلى ${curr} ${currency} في المنظمة ${safeOrgId}. تحقّق من صحة الإدخال أو وجود تلاعب بالبيانات.`.slice(0, 1000),
    actorUid,
    actorEmail: "",
    orgId: safeOrgId,
    sourcePlatform: "cloud_trigger",
    acknowledged: false
  };
}

function buildPaymentAlertNotification({ orgId = "", requestId = "", payData = {} } = {}) {
  const sender = sanitizeText(payData.senderName, 120) || "Subscriber";
  const amount = sanitizeText(payData.amount, 32) || "0";
  const curr = sanitizeText(payData.currency, 8) || "SDG";
  const tier = sanitizeText(payData.planTier, 40) || "professional";
  const ref = sanitizeText(payData.referenceNo, 64) || requestId;
  const safeOrgId = sanitizeText(orgId, 128);
  const actorUid = sanitizeText(payData.createdBy, 128);

  return {
    category: "payment_request",
    severity: "warning",
    signalType: "pending_payment",
    titleEn: `New Payment Proof (${amount} ${curr}) — ${tier}`.slice(0, 180),
    titleAr: `طلب توثيق دفع جديد (${amount} ${curr}) — ${tier}`.slice(0, 180),
    bodyEn: `${sender} submitted reference ${ref} for organization ${safeOrgId}. Open Pay & Roles to verify.`.slice(0, 1000),
    bodyAr: `أرسل ${sender} إشعار دفع برقم مرجعي ${ref} للمنظمة ${safeOrgId}. يرجى المراجعة والاعتماد.`.slice(0, 1000),
    actorUid,
    actorEmail: "",
    orgId: safeOrgId,
    sourcePlatform: "cloud_trigger",
    acknowledged: false
  };
}

module.exports = {
  buildNewUserAdminNotification,
  buildRapidRegistrationBurstAlert,
  evaluateSuspiciousAuditLog,
  evaluatePriceSpikeAnomaly,
  buildPaymentAlertNotification
};
