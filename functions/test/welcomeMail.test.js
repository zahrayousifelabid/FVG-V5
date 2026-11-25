const test = require("node:test");
const assert = require("node:assert/strict");
const {
  OWNER_EMAIL,
  OWNER_SIGNATURE_AR,
  escapeHtml,
  isValidEmail,
  buildWelcomeEmailContent,
  buildTrialExpiredEmailContent,
  buildMailMessageFromOutboxDoc
} = require("../mailTemplate");
const { _internals } = require("../index");

test("welcome email template sends from Financialvisionguide@gmail.com with warm greeting and Zahra signature", () => {
  const email = buildWelcomeEmailContent({
    displayName: "أحمد علي",
    email: "ahmed@example.com"
  });

  assert.equal(email.from, "Financialvisionguide@gmail.com");
  assert.equal(email.replyTo, "Financialvisionguide@gmail.com");
  assert.match(email.subject, /مرحباً بك/);
  assert.match(email.text, /أحمد علي/);
  assert.match(email.text, /المالكة زهراء يوسف/);
  assert.match(email.html, /أحمد علي/);
  assert.match(email.html, /المالكة زهراء يوسف/);
  assert.match(email.html, /168/);
});

test("welcome email template escapes malicious HTML in user displayName", () => {
  const email = buildWelcomeEmailContent({
    displayName: `<script>alert("xss")</script>سارة`,
    email: "sara@example.com"
  });

  assert.equal(email.html.includes("<script>"), false);
  assert.match(email.html, /&lt;script&gt;alert\(&quot;xss&quot;\)&lt;\/script&gt;سارة/);
});

test("isValidEmail validates format and rejects CRLF header injection", () => {
  assert.equal(isValidEmail("user@company.com"), true);
  assert.equal(isValidEmail("bad\r\nBcc:evil@attacker.com"), false);
  assert.equal(isValidEmail("not-an-email"), false);
  assert.equal(isValidEmail(""), false);
});

test("outbox message builder preserves signature and escapes plain text for HTML", () => {
  const msg = buildMailMessageFromOutboxDoc({
    from: OWNER_EMAIL,
    message: {
      subject: "تحديث مهم <FVG>",
      text: "تم اعتماد اشتراككم بنجاح."
    }
  });
  assert.equal(msg.from, OWNER_EMAIL);
  assert.match(msg.text, new RegExp(OWNER_SIGNATURE_AR));
  assert.equal(msg.html.includes("<FVG>"), false);
  assert.equal(escapeHtml("<FVG>"), "&lt;FVG&gt;");
});

test("trial expired email template uses official sender, Zahra signature, and escapes XSS", () => {
  const mail = buildTrialExpiredEmailContent({
    displayName: "خالد عمر",
    orgName: `شركة <b>النيل</b>`,
    email: "khaled@example.com"
  });
  assert.equal(mail.from, "Financialvisionguide@gmail.com");
  assert.match(mail.subject, /انتهت الفترة التجريبية/);
  assert.match(mail.text, /المالكة زهراء يوسف/);
  assert.match(mail.text, /خالد عمر/);
  assert.equal(mail.html.includes("<b>النيل</b>"), false);
  assert.match(mail.html, /&lt;b&gt;النيل&lt;\/b&gt;/);
});

test("Functions module exports auth, /mail, and trial-expired triggers bound to named database", () => {
  const fnModule = require("../index");
  assert.equal(_internals.DATABASE_ID, "ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866");
  assert.equal(typeof fnModule.onUserRegisteredSendWelcomeEmail, "function");
  assert.equal(typeof fnModule.processMailOutbox, "function");
  assert.equal(typeof fnModule.onOrgTrialExpiredSendEmail, "function");
  assert.equal(typeof fnModule.checkExpiredTrialsAndNotify, "function");
});

test("claimAndSendMailDoc dispatches once, marks SUCCESS, and skips duplicates cleanly", async () => {
  const store = new Map();
  const mockRef = {
    path: "mail/welcome_u123",
    async set(data, opts) {
      const prev = opts?.merge ? (store.get(this.path) || {}) : {};
      store.set(this.path, { ...prev, ...data });
    }
  };
  let sentCount = 0;
  let lastMailPayload = null;
  const mockTransport = {
    async sendMail(msg) {
      sentCount += 1;
      lastMailPayload = msg;
      return { messageId: "<msg-1@fvg>", accepted: ["newuser@example.com"] };
    }
  };

  // Inject minimal db.runTransaction stub via temporary patch on index internals test seam
  const prevRunTx = _internals.runTransaction;
  _internals.setRunTransactionForTest(async (fn) => {
    const tx = {
      async get(ref) {
        const d = store.get(ref.path);
        return { exists: Boolean(d), data: () => d };
      },
      set(ref, data, opts) {
        const prev = opts?.merge ? (store.get(ref.path) || {}) : {};
        store.set(ref.path, { ...prev, ...data });
      }
    };
    return fn(tx);
  });

  try {
    const builder = () => {
      const c = buildWelcomeEmailContent({ displayName: "نور الهدى", email: "newuser@example.com" });
      return {
        from: OWNER_EMAIL,
        to: ["newuser@example.com"],
        createdBy: "u123",
        recipientName: "نور الهدى",
        mailType: "welcome",
        message: { subject: c.subject, text: c.text, html: c.html }
      };
    };

    const res1 = await _internals.claimAndSendMailDoc(mockRef, builder, mockTransport);
    assert.equal(res1.status, "sent");
    assert.equal(sentCount, 1);
    assert.match(lastMailPayload.from, /Financialvisionguide@gmail\.com/);
    assert.match(lastMailPayload.from, /المالكة زهراء يوسف/);
    assert.equal(store.get(mockRef.path).delivery.state, "SUCCESS");

    const res2 = await _internals.claimAndSendMailDoc(mockRef, builder, mockTransport);
    assert.equal(res2.status, "skipped");
    assert.equal(sentCount, 1);

    // Verify missing SMTP transport queues cleanly without failing
    const mockRefNoSmtp = { ...mockRef, path: "mail/welcome_u456" };
    const res3 = await _internals.claimAndSendMailDoc(mockRefNoSmtp, builder, null);
    assert.equal(res3.status, "pending_smtp_config");
    assert.equal(store.get(mockRefNoSmtp.path).delivery.state, "PENDING_SMTP_CONFIG");
  } finally {
    _internals.setRunTransactionForTest(prevRunTx);
  }
});
