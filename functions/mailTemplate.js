const OWNER_EMAIL = "Financialvisionguide@gmail.com";
const OWNER_SIGNATURE_AR = "المالكة زهراء يوسف";
const OWNER_SIGNATURE_EN = "Owner Zahra Yousif";
const SUPPORT_PHONE = "00249911569966";

function escapeHtml(raw) {
  return String(raw ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function sanitizeHeaderText(raw) {
  return String(raw ?? "").replace(/[\r\n]+/g, " ").trim();
}

function isValidEmail(email) {
  if (typeof email !== "string") return false;
  const trimmed = email.trim();
  if (!trimmed || trimmed.length > 200 || /[\r\n\s]/.test(trimmed)) return false;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed);
}

function buildWelcomeEmailContent({ displayName = "", email = "" } = {}) {
  const cleanName = sanitizeHeaderText(displayName) || "عميلنا العزيز";
  const safeNameHtml = escapeHtml(cleanName);
  const safeEmailHtml = escapeHtml(sanitizeHeaderText(email));

  const subject = "مرحباً بك في دليل رؤيتك المالي (FVG) | Welcome to FVG";

  const text = [
    `مرحباً بك ${cleanName}،`,
    "",
    "أهلاً وسهلاً بك في منصة دليل رؤيتك المالي (FVG — Financial Vision Guide). يسعدنا انضمامك إلى نظام الإدارة المالية والمحاسبية والتكاليف والتسعير واتخاذ القرار المالي في البيئات التضخمية.",
    "",
    "تم تفعيل فترتك التجريبية المجانية الكاملة لمدة 7 أيام (168 ساعة) لتجربة كافة الأدوات: مراقبة الأسعار والتضخم، 8 أساليب لحساب التكاليف، القيود المزدوجة والقوائم المالية، 18 نسبة مالية، جرد FIFO والمتوسط المرجح، تصوير الفواتير، والمساعد المالي الذكي.",
    "",
    `لأي استفسار أو دعم فني أو تأكيد اشتراك، يسعدنا تواصلك عبر البريد الرسمي ${OWNER_EMAIL} أو الرقم ${SUPPORT_PHONE}.`,
    "",
    "مع أطيب التحيات والتمنيات لك بالتوفيق والنجاح،",
    OWNER_SIGNATURE_AR,
    OWNER_EMAIL,
    "",
    "----------------------------------------",
    "",
    `Warm welcome ${cleanName},`,
    "",
    "Welcome to FVG — Financial Vision Guide. Your full 7-day (168-hour) free trial workspace is active for Price Monitoring, 8 Costing Methods, Double-Entry Accounting, 18 Financial Ratios, FIFO/MWA Stock, and the Financial Assistant.",
    "",
    `Support & Subscription Verification: ${OWNER_EMAIL} | ${SUPPORT_PHONE}`,
    "",
    "With warm regards,",
    `${OWNER_SIGNATURE_AR} (${OWNER_SIGNATURE_EN})`,
    OWNER_EMAIL
  ].join("\n");

  const html = `<!doctype html>
<html lang="ar" dir="rtl">
<head><meta charset="utf-8" /></head>
<body style="margin:0;padding:24px;background-color:#FAF2E8;font-family:'Cairo','Segoe UI',Tahoma,Arial,sans-serif;color:#112421;direction:rtl;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:620px;margin:0 auto;background-color:#FFFFFF;border:2px solid #DDB065;border-radius:18px;overflow:hidden;">
    <tr>
      <td style="background-color:#022A27;padding:22px 26px;text-align:right;">
        <div style="color:#DDB065;font-size:13px;font-weight:700;letter-spacing:0.6px;">FVG — Financial Vision Guide</div>
        <h1 style="margin:6px 0 0;color:#FAF2E8;font-size:21px;line-height:1.4;">مرحباً بك في دليل رؤيتك المالي</h1>
      </td>
    </tr>
    <tr>
      <td style="padding:24px 26px;font-size:15px;line-height:1.8;color:#112421;">
        <p style="margin:0 0 12px;">أهلاً وسهلاً بك <strong>${safeNameHtml}</strong>${safeEmailHtml ? ` (${safeEmailHtml})` : ""}،</p>
        <p style="margin:0 0 12px;">
          يسعدنا جداً انضمامك إلى منصة <strong>دليل رؤيتك المالي (FVG)</strong> المصممة لتمكين المؤسسات ورواد الأعمال من ضبط التكاليف والتسعير وإعداد القوائم المالية وتحليل المخاطر التضخمية بدقة واحترافية.
        </p>
        <div style="background-color:#FAF2E8;border-right:4px solid #DDB065;padding:12px 16px;border-radius:10px;margin:16px 0;">
          <strong>فترة التجربة المجانية:</strong> مفعّلة بالكامل لمدة <strong>7 أيام (168 ساعة)</strong> عبر تطبيق أندرويد والويب والآيفون.
        </div>
        <p style="margin:0 0 14px;">
          لأي استفسار أو مساعدة في الاستخدام أو تفعيل الباقات، يمكنك الرد على هذا البريد (<strong>${OWNER_EMAIL}</strong>) أو التواصل على الرقم <strong>${SUPPORT_PHONE}</strong>.
        </p>
        <p style="margin:18px 0 0;font-weight:700;color:#022A27;">
          مع خالص التمنيات بالنجاح والتوفيق،<br />
          ${OWNER_SIGNATURE_AR}<br />
          <span style="font-size:13px;color:#52615E;font-weight:600;">${OWNER_EMAIL}</span>
        </p>
      </td>
    </tr>
    <tr>
      <td style="background-color:#F3ECE2;padding:14px 26px;font-size:12px;color:#52615E;direction:ltr;text-align:left;border-top:1px solid #E5D7C3;">
        <strong>Welcome ${safeNameHtml}</strong> — Your 7-day (168h) FVG workspace trial is active. Signed by ${OWNER_SIGNATURE_AR} (${OWNER_SIGNATURE_EN}) • ${OWNER_EMAIL}
      </td>
    </tr>
  </table>
</body>
</html>`;

  return {
    from: OWNER_EMAIL,
    replyTo: OWNER_EMAIL,
    subject,
    text,
    html
  };
}

function buildTrialExpiredEmailContent({ displayName = "", orgName = "", email = "" } = {}) {
  const cleanName = sanitizeHeaderText(displayName) || "عميلنا العزيز";
  const cleanOrg = sanitizeHeaderText(orgName) || "منشأتك";
  const safeNameHtml = escapeHtml(cleanName);
  const safeOrgHtml = escapeHtml(cleanOrg);
  const safeEmailHtml = escapeHtml(sanitizeHeaderText(email));

  const subject = `انتهت الفترة التجريبية (${cleanOrg}) — باقات دليل رؤيتك المالي FVG | Trial Ended`;

  const text = [
    `مرحباً ${cleanName}،`,
    "",
    `انتهت الفترة التجريبية المجانية (7 أيام / 168 ساعة) لمنظمة "${cleanOrg}" في منصة دليل رؤيتك المالي (FVG). جميع بياناتك وأسعارك وقيودك محفوظة بأمان تام.`,
    "",
    "لاختيار الباقة المناسبة ومتابعة العمل:",
    "• الباقة الأساسية (Starter — 28,000 ج.س / $12 شهرياً): الأسعار، التضخم، الصرف اليدوي، طريقتا تكلفة (الفعلي والمعياري)، القيود الأساسية (منظمة واحدة / 25 صنفاً).",
    "• الباقة الاحترافية (Professional — 65,000 ج.س / $29 شهرياً): جميع طرق التكاليف الـ 8، التركيبات BOM والانحرافات، أدوات القطاعات المفعّلة، المساعد المالي الذكي، تصوير الفواتير بالكاميرا، وPDF/XLSX (3 منظمات / 250 صنفاً).",
    "• باقة المؤسسات (Enterprise — 175,000 ج.س / $79 شهرياً): كل مزايا الاحترافية + سعة مفتوحة، صلاحيات الفريق RBAC، سجل التدقيق والنسخ الاحتياطي JSON ودعم أولوية.",
    "",
    `لتفعيل الاشتراك: ارفع مرجع التحويل وآخر 4 أرقام واسم المرسل عبر (بنكك / أوكاش / تحويل بنكي) من تبويب المنظمة والدفع داخل التطبيق أو تواصل معنا على ${OWNER_EMAIL} أو ${SUPPORT_PHONE}.`,
    "",
    "مع أطيب التحيات والتمنيات بالنجاح،",
    OWNER_SIGNATURE_AR,
    OWNER_EMAIL,
    "",
    "----------------------------------------",
    "",
    `Hello ${cleanName},`,
    "",
    `The 7-day (168h) free trial for "${cleanOrg}" has ended. All your records stay safely preserved.`,
    `Upgrade via Starter ($12), Professional ($29), or Enterprise ($79) by submitting Bankak / O-Cash / Bank Transfer proof in FVG -> Org & Billing or contacting ${OWNER_EMAIL} | ${SUPPORT_PHONE}.`,
    "",
    "Warm regards,",
    `${OWNER_SIGNATURE_AR} (${OWNER_SIGNATURE_EN})`,
    OWNER_EMAIL
  ].join("\n");

  const html = `<!doctype html>
<html lang="ar" dir="rtl">
<head><meta charset="utf-8" /></head>
<body style="margin:0;padding:24px;background-color:#FAF2E8;font-family:'Cairo','Segoe UI',Tahoma,Arial,sans-serif;color:#112421;direction:rtl;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:620px;margin:0 auto;background-color:#FFFFFF;border:2px solid #DDB065;border-radius:18px;overflow:hidden;">
    <tr>
      <td style="background-color:#022A27;padding:22px 26px;text-align:right;">
        <div style="color:#DDB065;font-size:13px;font-weight:700;">FVG — Financial Vision Guide</div>
        <h1 style="margin:6px 0 0;color:#FAF2E8;font-size:20px;">انتهت الفترة التجريبية — اختر باقتك للمتابعة</h1>
      </td>
    </tr>
    <tr>
      <td style="padding:24px 26px;font-size:15px;line-height:1.8;color:#112421;">
        <p style="margin:0 0 12px;">مرحباً <strong>${safeNameHtml}</strong>${safeEmailHtml ? ` (${safeEmailHtml})` : ""}،</p>
        <p style="margin:0 0 12px;">انتهت مدة التجربة المجانية (7 أيام) لمنظمة <strong>${safeOrgHtml}</strong>، ولا تزال كافة سجلاتك المحاسبية والمخزنية محفوظة بأمان.</p>
        <div style="background-color:#FAF2E8;border-right:4px solid #DDB065;padding:12px 16px;border-radius:10px;margin:14px 0;">
          <div><strong>• الأساسية:</strong> 28,000 ج.س / $12 — الأسعار، الصرف اليدوي، طريقتا تكلفة، 25 صنفاً.</div>
          <div><strong>• الاحترافية:</strong> 65,000 ج.س / $29 — الأساليب الـ 8، التركيبات، أدوات القطاعات، المساعد الذكي، الكاميرا، PDF/XLSX.</div>
          <div><strong>• المؤسسات:</strong> 175,000 ج.س / $79 — سعة مفتوحة، RBAC، التدقيق والنسخ الاحتياطي، وأولوية الدعم.</div>
        </div>
        <p style="margin:0 0 14px;">أرسل مرجع التحويل وآخر 4 أرقام واسم المرسل في قسم <strong>المنظمة والدفع</strong> ليتم الاعتماد والتفعيل فوراً، أو تواصل عبر <strong>${OWNER_EMAIL}</strong> / <strong>${SUPPORT_PHONE}</strong>.</p>
        <p style="margin:18px 0 0;font-weight:700;color:#022A27;">
          مع خالص التحيات،<br />
          ${OWNER_SIGNATURE_AR}<br />
          <span style="font-size:13px;color:#52615E;font-weight:600;">${OWNER_EMAIL}</span>
        </p>
      </td>
    </tr>
  </table>
</body>
</html>`;

  return {
    from: OWNER_EMAIL,
    replyTo: OWNER_EMAIL,
    subject,
    text,
    html
  };
}

function buildMailMessageFromOutboxDoc(docData = {}) {
  const message = docData.message && typeof docData.message === "object" ? docData.message : {};
  const subject = sanitizeHeaderText(message.subject) || "إشعار من دليل رؤيتك المالي | FVG";
  const rawText = typeof message.text === "string" && message.text.trim()
    ? message.text.trim()
    : `رسالة من منصة دليل رؤيتك المالي\n\n${OWNER_SIGNATURE_AR}\n${OWNER_EMAIL}`;

  const signedText = rawText.includes(OWNER_SIGNATURE_AR)
    ? rawText
    : `${rawText}\n\n--\n${OWNER_SIGNATURE_AR}\n${OWNER_EMAIL} | ${SUPPORT_PHONE}`;

  const htmlBody = typeof message.html === "string" && message.html.trim()
    ? message.html
    : `<!doctype html><html lang="ar" dir="rtl"><body style="margin:0;padding:24px;background:#FAF2E8;font-family:'Cairo','Segoe UI',Tahoma,Arial,sans-serif;color:#112421;direction:rtl;"><div style="max-width:600px;margin:0 auto;background:#FFFFFF;border:2px solid #DDB065;border-radius:16px;padding:22px;"><div style="color:#022A27;font-weight:800;font-size:18px;margin-bottom:10px;">${escapeHtml(subject)}</div><div style="white-space:pre-line;line-height:1.8;font-size:15px;">${escapeHtml(signedText)}</div></div></body></html>`;

  return {
    from: OWNER_EMAIL,
    replyTo: OWNER_EMAIL,
    subject,
    text: signedText,
    html: htmlBody
  };
}

module.exports = {
  OWNER_EMAIL,
  OWNER_SIGNATURE_AR,
  OWNER_SIGNATURE_EN,
  SUPPORT_PHONE,
  escapeHtml,
  sanitizeHeaderText,
  isValidEmail,
  buildWelcomeEmailContent,
  buildTrialExpiredEmailContent,
  buildMailMessageFromOutboxDoc
};
