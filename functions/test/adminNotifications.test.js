const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildNewUserAdminNotification,
  buildRapidRegistrationBurstAlert,
  evaluateSuspiciousAuditLog,
  evaluatePriceSpikeAnomaly,
  buildPaymentAlertNotification
} = require("../adminNotificationHelpers");

test("buildNewUserAdminNotification generates bilingual new_user alert", () => {
  const n = buildNewUserAdminNotification({
    userId: "u_999",
    displayName: "سارة محمد",
    email: "sara@fvg.test"
  });
  assert.equal(n.category, "new_user");
  assert.equal(n.severity, "info");
  assert.equal(n.signalType, "new_registration");
  assert.equal(n.acknowledged, false);
  assert.match(n.titleAr, /سارة محمد/);
  assert.match(n.bodyEn, /sara@fvg\.test/);
});

test("buildRapidRegistrationBurstAlert triggers only when recentCount >= 5", () => {
  assert.equal(buildRapidRegistrationBurstAlert({ userId: "u1", recentCount: 4 }), null);
  const burst = buildRapidRegistrationBurstAlert({ userId: "u5", recentCount: 6, email: "bot@test.com" });
  assert.ok(burst);
  assert.equal(burst.category, "security_alert");
  assert.equal(burst.severity, "critical");
  assert.equal(burst.signalType, "rapid_user_registration");
});

test("evaluateSuspiciousAuditLog filters benign logs and flags high-risk actions", () => {
  assert.equal(evaluateSuspiciousAuditLog({ orgId: "org_1", logData: { action: "SAVE_PRODUCT" } }), null);

  const failed = evaluateSuspiciousAuditLog({
    orgId: "org_1",
    logData: {
      action: "SECURITY_FAILED_ADMIN_LOGIN",
      summary: "3 consecutive failed attempts",
      actorEmail: "attacker@test.com",
      createdBy: "u_bad"
    }
  });
  assert.ok(failed);
  assert.equal(failed.category, "security_alert");
  assert.equal(failed.severity, "critical");
  assert.equal(failed.signalType, "failed_admin_login");
});

test("evaluatePriceSpikeAnomaly triggers at >= 40% movement", () => {
  assert.equal(evaluatePriceSpikeAnomaly({ orgId: "org_1", obsData: { pctChange: "12.5" } }), null);
  const spike = evaluatePriceSpikeAnomaly({
    orgId: "org_1",
    obsData: {
      productName: "Sugar 50kg",
      previousPrice: "50000",
      price: "125000",
      currency: "SDG",
      pctChange: "150",
      createdBy: "u_1"
    }
  });
  assert.ok(spike);
  assert.equal(spike.severity, "critical");
  assert.equal(spike.signalType, "unusual_price_spike");
});

test("buildPaymentAlertNotification creates pending_payment alert", () => {
  const pay = buildPaymentAlertNotification({
    orgId: "org_2",
    requestId: "pay_77",
    payData: {
      senderName: "Ali Hassan",
      amount: "65000",
      currency: "SDG",
      planTier: "professional",
      referenceNo: "BNK-9910",
      createdBy: "u_2"
    }
  });
  assert.equal(pay.category, "payment_request");
  assert.equal(pay.severity, "warning");
  assert.match(pay.bodyEn, /BNK-9910/);
});
