const { initializeApp, getApps } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const functionsV1 = require("firebase-functions/v1");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const nodemailer = require("nodemailer");
const {
  OWNER_EMAIL,
  OWNER_SIGNATURE_AR,
  isValidEmail,
  buildWelcomeEmailContent,
  buildTrialExpiredEmailContent,
  buildMailMessageFromOutboxDoc
} = require("./mailTemplate");
const {
  buildNewUserAdminNotification,
  buildRapidRegistrationBurstAlert,
  evaluateSuspiciousAuditLog,
  evaluatePriceSpikeAnomaly,
  buildPaymentAlertNotification
} = require("./adminNotificationHelpers");

const DATABASE_ID = "ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866";
const fvgSmtpAppPassword = defineSecret("FVG_SMTP_APP_PASSWORD");

if (!getApps().length) {
  initializeApp();
}

const db = getFirestore(DATABASE_ID);

function createSmtpTransport(passOverride) {
  const pass = (passOverride || process.env.FVG_SMTP_APP_PASSWORD || "").trim();
  if (!pass) {
    return null;
  }
  const host = (process.env.FVG_SMTP_HOST || "smtp.gmail.com").trim();
  const port = Number(process.env.FVG_SMTP_PORT || 465);
  const secure = process.env.FVG_SMTP_SECURE ? process.env.FVG_SMTP_SECURE === "true" : port === 465;
  const user = (process.env.FVG_SMTP_USER || OWNER_EMAIL).trim();

  return nodemailer.createTransport({
    host,
    port,
    secure,
    disableFileAccess: true,
    disableUrlAccess: true,
    auth: { user, pass }
  });
}

let runTransactionFn = (fn) => db.runTransaction(fn);

async function claimAndSendMailDoc(mailRef, payloadBuilder, transport) {
  const claimed = await runTransactionFn(async (tx) => {
    const snap = await tx.get(mailRef);
    const existing = snap.exists ? snap.data() : null;
    const state = existing?.delivery?.state;
    if (state === "SUCCESS" || state === "PROCESSING") {
      return { skip: true, data: existing };
    }
    const baseData = existing || payloadBuilder();
    tx.set(
      mailRef,
      {
        ...baseData,
        from: OWNER_EMAIL,
        delivery: {
          state: "PROCESSING",
          attempts: Number(existing?.delivery?.attempts || 0) + 1,
          startTime: FieldValue.serverTimestamp()
        }
      },
      { merge: true }
    );
    return { skip: false, data: baseData };
  });

  if (claimed.skip) {
    return { status: "skipped", reason: claimed.data?.delivery?.state };
  }

  const recipients = Array.isArray(claimed.data.to)
    ? claimed.data.to.filter((addr) => isValidEmail(addr)).slice(0, 50)
    : [];

  if (recipients.length === 0) {
    await mailRef.set(
      {
        delivery: {
          state: "ERROR",
          error: "No valid recipient email addresses",
          endTime: FieldValue.serverTimestamp()
        }
      },
      { merge: true }
    );
    return { status: "error", reason: "invalid_recipients" };
  }

  if (!transport) {
    console.warn("FVG_SMTP_APP_PASSWORD secret is not configured; email queued in /mail without external SMTP dispatch.");
    await mailRef.set(
      {
        delivery: {
          state: "PENDING_SMTP_CONFIG",
          error: "Missing FVG_SMTP_APP_PASSWORD secret in Firebase Functions",
          endTime: FieldValue.serverTimestamp()
        }
      },
      { merge: true }
    );
    return { status: "pending_smtp_config" };
  }

  const composed = claimed.data.mailType === "welcome" && !claimed.data.message?.html
    ? buildWelcomeEmailContent({
        displayName: claimed.data.recipientName || "",
        email: recipients[0]
      })
    : buildMailMessageFromOutboxDoc(claimed.data);

  try {
    const info = await transport.sendMail({
      from: `"FVG — دليل رؤيتك المالي | ${OWNER_SIGNATURE_AR}" <${OWNER_EMAIL}>`,
      replyTo: OWNER_EMAIL,
      to: recipients.join(", "),
      subject: composed.subject,
      text: composed.text,
      html: composed.html
    });

    await mailRef.set(
      {
        delivery: {
          state: "SUCCESS",
          messageId: info?.messageId || null,
          accepted: Array.isArray(info?.accepted) ? info.accepted : recipients,
          endTime: FieldValue.serverTimestamp()
        }
      },
      { merge: true }
    );
    return { status: "sent", messageId: info?.messageId || null };
  } catch (err) {
    console.error("Failed to dispatch FVG email:", err);
    await mailRef.set(
      {
        delivery: {
          state: "ERROR",
          error: String(err?.message || err).slice(0, 400),
          endTime: FieldValue.serverTimestamp()
        }
      },
      { merge: true }
    );
    throw err;
  }
}

/**
 * Trigger 1: Automatically executes when a new user registers via Firebase Auth.
 * Generates the warm welcome email from Financialvisionguide@gmail.com signed by "المالكة زهراء يوسف",
 * persists an idempotent /mail/welcome_{uid} record on database ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866,
 * and sends it over SMTP.
 */
exports.onUserRegisteredSendWelcomeEmail = functionsV1
  .region("us-central1")
  .runWith({ secrets: [fvgSmtpAppPassword] })
  .auth.user()
  .onCreate(async (user) => {
    if (!user || !isValidEmail(user.email)) {
      console.info("Skipping welcome email: user has no valid email address.", { uid: user?.uid || null });
      return null;
    }
    const mailRef = db.collection("mail").doc(`welcome_${user.uid}`);
    const welcome = buildWelcomeEmailContent({
      displayName: user.displayName || "",
      email: user.email
    });

    const transport = createSmtpTransport(fvgSmtpAppPassword.value());
    return claimAndSendMailDoc(
      mailRef,
      () => ({
        from: OWNER_EMAIL,
        to: [user.email.trim()],
        createdBy: user.uid,
        recipientName: user.displayName || "",
        mailType: "welcome",
        message: {
          subject: welcome.subject,
          text: welcome.text,
          html: welcome.html
        },
        createdAt: FieldValue.serverTimestamp()
      }),
      transport
    );
  });

/**
 * Trigger 2: Processes queued documents written to /mail/{mailId}
 * (including owner announcements/notifications or client-queued records).
 */
exports.processMailOutbox = onDocumentCreated(
  {
    document: "mail/{mailId}",
    database: DATABASE_ID,
    region: "us-central1",
    secrets: [fvgSmtpAppPassword]
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;
    const data = snap.data() || {};
    if (data.from !== OWNER_EMAIL) {
      await snap.ref.set(
        {
          delivery: {
            state: "ERROR",
            error: "Unauthorized sender address",
            endTime: FieldValue.serverTimestamp()
          }
        },
        { merge: true }
      );
      return null;
    }
    const transport = createSmtpTransport(fvgSmtpAppPassword.value());
    return claimAndSendMailDoc(snap.ref, () => data, transport);
  }
);

async function dispatchTrialExpiredEmailForOrg(orgId, orgData, transport) {
  if (!orgId || !orgData?.ownerId) return null;
  const userSnap = await db.collection("users").doc(orgData.ownerId).get();
  const ownerEmail = userSnap.exists ? userSnap.data()?.email : "";
  const ownerName = userSnap.exists ? userSnap.data()?.displayName : "";
  if (!isValidEmail(ownerEmail)) return null;

  const mailRef = db.collection("mail").doc(`trial_expired_${orgId}`);
  const notice = buildTrialExpiredEmailContent({
    displayName: ownerName || "",
    orgName: orgData.name || "",
    email: ownerEmail
  });
  return claimAndSendMailDoc(
    mailRef,
    () => ({
      from: OWNER_EMAIL,
      to: [ownerEmail.trim()],
      createdBy: orgData.ownerId,
      recipientName: ownerName || "",
      mailType: "trial_expired",
      message: {
        subject: notice.subject,
        text: notice.text,
        html: notice.html
      },
      createdAt: FieldValue.serverTimestamp()
    }),
    transport
  );
}

/**
 * Trigger 3: Automatically sends a trial-expired email via /mail/trial_expired_{orgId}
 * when an organization transitions to subscriptionStatus == "expired".
 */
exports.onOrgTrialExpiredSendEmail = onDocumentUpdated(
  {
    document: "organizations/{orgId}",
    database: DATABASE_ID,
    region: "us-central1",
    secrets: [fvgSmtpAppPassword]
  },
  async (event) => {
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};
    if (before.subscriptionStatus === "expired" || after.subscriptionStatus !== "expired") {
      return null;
    }
    const transport = createSmtpTransport(fvgSmtpAppPassword.value());
    return dispatchTrialExpiredEmailForOrg(event.params.orgId, after, transport);
  }
);

/**
 * Trigger 4: Scheduled sweep every 12 hours to mark expired trials and send
 * trial-ended follow-up emails once per organization.
 */
exports.checkExpiredTrialsAndNotify = onSchedule(
  {
    schedule: "every 12 hours",
    region: "us-central1",
    secrets: [fvgSmtpAppPassword]
  },
  async () => {
    const now = new Date();
    const expiredSnap = await db
      .collection("organizations")
      .where("subscriptionStatus", "==", "trial")
      .where("trialEndsAt", "<=", now)
      .limit(100)
      .get();

    if (expiredSnap.empty) return null;
    const transport = createSmtpTransport(fvgSmtpAppPassword.value());
    for (const orgDoc of expiredSnap.docs) {
      const orgData = orgDoc.data() || {};
      await orgDoc.ref.set(
        {
          subscriptionStatus: "expired",
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );
      await dispatchTrialExpiredEmailForOrg(orgDoc.id, orgData, transport);
    }
    return { processed: expiredSnap.size };
  }
);

/**
 * Trigger 5: Real-time admin alert whenever a new /users/{userId} profile document is created,
 * plus automated rapid-registration burst detection (>= 5 accounts in 10 minutes).
 */
exports.onUserDocCreatedNotifyAdmin = onDocumentCreated(
  {
    document: "users/{userId}",
    database: DATABASE_ID,
    region: "us-central1"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;
    const data = snap.data() || {};
    const userId = event.params.userId;

    const notif = buildNewUserAdminNotification({
      userId,
      displayName: data.displayName || "",
      email: data.email || "",
      phoneNumber: data.phoneNumber || ""
    });

    await db.collection("adminNotifications").doc(`reg_${userId}`).set(
      {
        ...notif,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );

    const tenMinutesAgo = new Date(Date.now() - 10 * 60 * 1000);
    const recentSnap = await db
      .collection("users")
      .where("createdAt", ">=", tenMinutesAgo)
      .limit(20)
      .get();

    const burstAlert = buildRapidRegistrationBurstAlert({
      userId,
      recentCount: recentSnap.size,
      windowMinutes: 10,
      email: data.email || ""
    });

    if (burstAlert) {
      await db.collection("adminNotifications").doc(`sec_burst_${userId}`).set(
        {
          ...burstAlert,
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );
    }
    return { notified: true, burst: Boolean(burstAlert) };
  }
);

/**
 * Trigger 6: Real-time suspicious-activity detection on /organizations/{orgId}/auditLogs/{logId}.
 */
exports.onAuditLogCreatedDetectSuspicious = onDocumentCreated(
  {
    document: "organizations/{orgId}/auditLogs/{logId}",
    database: DATABASE_ID,
    region: "us-central1"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;
    const alert = evaluateSuspiciousAuditLog({
      orgId: event.params.orgId,
      logData: snap.data() || {}
    });
    if (!alert) return null;

    await db.collection("adminNotifications").doc(`sec_audit_${event.params.logId}`).set(
      {
        ...alert,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );
    return { alerted: true, signalType: alert.signalType };
  }
);

/**
 * Trigger 7: Real-time abnormal price spike / tampering anomaly trigger on priceObservations.
 */
exports.onPriceObservationCreatedDetectAnomaly = onDocumentCreated(
  {
    document: "organizations/{orgId}/priceObservations/{obsId}",
    database: DATABASE_ID,
    region: "us-central1"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;
    const alert = evaluatePriceSpikeAnomaly({
      orgId: event.params.orgId,
      obsData: snap.data() || {}
    });
    if (!alert) return null;

    await db.collection("adminNotifications").doc(`sec_price_${event.params.obsId}`).set(
      {
        ...alert,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );
    return { alerted: true, signalType: alert.signalType };
  }
);

/**
 * Trigger 8: Real-time payment verification alert on new /organizations/{orgId}/paymentRequests/{requestId}.
 */
exports.onPaymentRequestCreatedNotifyAdmin = onDocumentCreated(
  {
    document: "organizations/{orgId}/paymentRequests/{requestId}",
    database: DATABASE_ID,
    region: "us-central1"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;
    const alert = buildPaymentAlertNotification({
      orgId: event.params.orgId,
      requestId: event.params.requestId,
      payData: snap.data() || {}
    });
    await db.collection("adminNotifications").doc(`pay_${event.params.requestId}`).set(
      {
        ...alert,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      },
      { merge: true }
    );
    return { alerted: true };
  }
);

exports._internals = {
  DATABASE_ID,
  createSmtpTransport,
  claimAndSendMailDoc,
  dispatchTrialExpiredEmailForOrg,
  get runTransaction() {
    return runTransactionFn;
  },
  setRunTransactionForTest(fn) {
    runTransactionFn = fn || ((cb) => db.runTransaction(cb));
  }
};
