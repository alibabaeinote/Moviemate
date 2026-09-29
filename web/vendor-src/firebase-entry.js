// Source for web/firebase-bundle.js (built by `npm run build:firebase`, see
// package.json). Not imported by anything at runtime itself — this is the
// esbuild entry point, re-exporting exactly the symbols app.js needs.
//
// Why this exists at all: app.js used to import straight from
// https://www.gstatic.com/firebasejs/... (Firebase's own CDN bundles, built
// for exactly this same-syntax browser import). That's fine on an ordinary
// network, but on at least one real deployment, www.gstatic.com itself was
// unreachable — not accounts.google.com (the actual sign-in provider), just
// this one CDN host — which meant the SDK import threw before any of
// app.js's code ran, so "Continue with Google" did visibly nothing. Bundling
// the same npm `firebase` package ourselves and serving it from our own
// domain (moviemate-prod-2026.web.app, already reachable — it's serving this
// very file) removes that dependency entirely.
export { initializeApp } from "firebase/app";
export {
  getAuth,
  GoogleAuthProvider,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  onAuthStateChanged,
} from "firebase/auth";
export {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  getCountFromServer,
  deleteDoc,
  limit,
  orderBy,
  query,
  runTransaction,
  setDoc,
  updateDoc,
  onSnapshot,
  serverTimestamp,
  writeBatch,
  arrayUnion,
  arrayRemove,
  where,
  Timestamp,
} from "firebase/firestore";
