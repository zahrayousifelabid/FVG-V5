/**
 * FVG Web App — Firebase Configuration
 * Connected to Firebase Project & Named Firestore Database:
 * - Project ID: gen-lang-client-0025296187
 * - Web App ID: 1:450806209679:web:aad2970f33e8f2f1cbe4fa
 * - Firestore Database ID: ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866
 * - Auth Domain: gen-lang-client-0025296187.firebaseapp.com
 * - Storage Bucket: gen-lang-client-0025296187.firebasestorage.app
 * - Messaging Sender ID: 450806209679
 */
export const FVG_FIREBASE_CONFIG = {
  apiKey: "AIzaSyA1J_8PbE8n-3WRpF0A5v36d5wXlVFYxV4",
  authDomain: "gen-lang-client-0025296187.firebaseapp.com",
  projectId: "gen-lang-client-0025296187",
  storageBucket: "gen-lang-client-0025296187.firebasestorage.app",
  messagingSenderId: "450806209679",
  appId: "1:450806209679:web:aad2970f33e8f2f1cbe4fa",
  databaseId: "ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866"
};

export function isFirebaseWebConfigured() {
  return Boolean(FVG_FIREBASE_CONFIG.apiKey && FVG_FIREBASE_CONFIG.appId);
}
