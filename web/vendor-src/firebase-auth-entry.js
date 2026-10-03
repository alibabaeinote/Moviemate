// Source for web/firebase-auth-bundle.js (built by `npm run build:firebase`).
// Split out from firebase-firestore-entry.js specifically so app.js can wire
// up "Continue with Google" after downloading only this (~126kb), not the
// full ~400kb app+auth+firestore bundle. Firestore isn't needed until after
// sign-in actually completes — a full page round-trip through Google's own
// servers — so there's no reason the click itself should wait on it.
export { initializeApp } from "firebase/app";
export {
  getAuth,
  GoogleAuthProvider,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  onAuthStateChanged,
} from "firebase/auth";
