# FVG — Financial Vision Guide Website (Installable PWA)

This `/website` directory contains the standalone, editable Progressive Web App (PWA) sharing the design system, 8-method costing engine, 18 financial ratios, double-entry ledger, FIFO/MWA inventory, 3D FVG financial calculator, spotlight walkthrough tour, and 7-day (168-hour) trial workflow with the Android **FVG – Financial Vision Guide (`دليل رؤيتك المالي`)** application.

## 1. Deploy Live & Get Your Shareable HTTPS Link (Firebase Hosting / Netlify / Vercel)
1. In your Firebase Console (`gen-lang-client-0025296187`), register a **Web App**, copy its `apiKey` and `appId` into `website/firebase-config.js`, and verify **Google Sign-In** and **Email/Password** providers are enabled under Firebase Auth -> Sign-in method.
2. Add your hosting domain under Firebase Auth -> Settings -> Authorized domains.
3. Deploy the `/website` folder to Firebase Hosting (`firebase deploy --only hosting`), Netlify Drop, or Vercel.
   *(Built-in SVG branding in `assets/fvg-icon.svg` is bundled cleanly with zero missing files.)*

## 2. How Anyone Can Install on iPhone (iOS), Android, or Desktop
- **iPhone / iPad (Safari):** Open the HTTPS website link in **Safari** -> tap **Share** -> tap **Add to Home Screen (`إضافة إلى الشاشة الرئيسية`)** -> tap **Add**.
- **Android (Chrome) / Desktop (Chrome / Edge):** Tap **Install App** in the browser address bar or menu.
