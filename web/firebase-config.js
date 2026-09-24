// Public client identifiers, not secrets — the same values Firebase itself
// shows in the console under Project settings → Your apps. Safe to commit.
//
// authDomain is deliberately the Hosting domain, not the default
// *.firebaseapp.com one Firebase's console shows: signInWithRedirect bounces
// the browser through authDomain and back, and Safari's cross-site tracking
// prevention can drop the pending-redirect state stored there when that hop
// is to a different domain than the app itself. Firebase Hosting serves the
// same /__/auth/* handler on *.web.app, so pointing authDomain here keeps
// the whole redirect on one origin and avoids that failure mode.
export const firebaseConfig = {
  apiKey: "AIzaSyCw_2yA5NBy5UcM5330uGNthgnUzXsV7ZU",
  authDomain: "moviemate-prod-2026.web.app",
  projectId: "moviemate-prod-2026",
  storageBucket: "moviemate-prod-2026.firebasestorage.app",
  messagingSenderId: "134216388127",
  appId: "1:134216388127:web:57bd97dab21bd476d58f2a",
};
