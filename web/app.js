import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged,
  getAdditionalUserInfo,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  onSnapshot,
  serverTimestamp,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import {
  getFunctions,
  httpsCallable,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-functions.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
// Must match functions/src/index.ts's setGlobalOptions region exactly, or
// the callable SDK builds a URL for a region nothing is deployed to.
const functions = getFunctions(app, "europe-west1");
const createPairFn = httpsCallable(functions, "createPair");
const joinPairFn = httpsCallable(functions, "joinPair");

const els = {
  signedOut: document.getElementById("signedOut"),
  signedIn: document.getElementById("signedIn"),
  signInBtn: document.getElementById("signInBtn"),
  signOutBtn: document.getElementById("signOutBtn"),
  saveBtn: document.getElementById("saveBtn"),
  nameInput: document.getElementById("nameInput"),
  avatar: document.getElementById("avatar"),
  email: document.getElementById("email"),
  status: document.getElementById("status"),
  saveStatus: document.getElementById("saveStatus"),
  pairing: document.getElementById("pairing"),
  pairingChoice: document.getElementById("pairingChoice"),
  pairingInvite: document.getElementById("pairingInvite"),
  pairingJoin: document.getElementById("pairingJoin"),
  pairingDone: document.getElementById("pairingDone"),
  getInviteBtn: document.getElementById("getInviteBtn"),
  showJoinBtn: document.getElementById("showJoinBtn"),
  backFromJoinBtn: document.getElementById("backFromJoinBtn"),
  joinBtn: document.getElementById("joinBtn"),
  joinCodeInput: document.getElementById("joinCodeInput"),
  inviteCodeDisplay: document.getElementById("inviteCodeDisplay"),
  copyInviteBtn: document.getElementById("copyInviteBtn"),
  partnerText: document.getElementById("partnerText"),
  pairingStatus: document.getElementById("pairingStatus"),
};

let unsubscribeUserDoc = null;
let unsubscribePairDoc = null;
let pendingInviteCode = null;

function showStatus(el, message, isError) {
  el.textContent = message;
  el.style.color = isError ? "var(--decorative)" : "var(--muted)";
  el.style.display = message ? "block" : "none";
}

function permissionHint(err) {
  if (err?.code === "permission-denied") {
    return "Firestore denied the read/write — the real firestore.rules from the repo haven't been deployed to this project yet (see the README next to this file).";
  }
  if (err?.code === "auth/unauthorized-domain") {
    return "This domain isn't in Firebase Auth's Authorized domains list yet — add it under Authentication → Settings → Authorized domains.";
  }
  if (err?.code === "functions/not-found" || err?.code === "not-found") {
    return "This function isn't deployed to the project yet — see web/README.md for the functions deploy steps (Blaze plan required for 2nd-gen functions).";
  }
  return null;
}

/**
 * Seeds users/{uid} on the account's first sign-in only — mirrors
 * FirebaseAuthRepository.signInWithGoogle in the Android app exactly, field
 * for field, so the web and Android clients write the same document shape
 * and firestore.rules' `allow create` checks pass either way.
 */
async function ensureUserDocument(user, isNewUser) {
  if (!isNewUser) return;
  const profile = {
    uid: user.uid,
    name: (user.displayName || "").trim(),
    email: user.email || "",
    avatarUrl: user.photoURL || null,
    createdAt: serverTimestamp(),
    pairId: null,
    onboardingComplete: false,
    ratingCount: 0,
    notificationSettings: { dailyMatch: true, partnerActivity: true, reminders: true },
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
  };
  await setDoc(doc(db, "users", user.uid), profile);
}

function renderAvatar(name, photoUrl) {
  if (photoUrl) {
    els.avatar.style.backgroundImage = `url(${photoUrl})`;
    els.avatar.textContent = "";
  } else {
    els.avatar.style.backgroundImage = "none";
    els.avatar.textContent = (name || "?").trim().charAt(0).toUpperCase() || "?";
  }
}

/** One of: choice, invite, join, done — everything else in #pairing hides. */
function showPairingView(view) {
  els.pairingChoice.hidden = view !== "choice";
  els.pairingInvite.hidden = view !== "invite";
  els.pairingJoin.hidden = view !== "join";
  els.pairingDone.hidden = view !== "done";
}

function watchPairDoc(pairId, myUid) {
  if (unsubscribePairDoc) unsubscribePairDoc();
  unsubscribePairDoc = onSnapshot(
    doc(db, "pairs", pairId),
    (snap) => {
      if (!snap.exists()) return;
      const pair = snap.data();
      const isUserA = pair.userA === myUid;
      const partnerJoined = isUserA ? !!pair.userB : true;
      if (!partnerJoined) {
        pendingInviteCode = pair.inviteCode;
        els.inviteCodeDisplay.textContent = pair.inviteCode;
        showPairingView("invite");
        return;
      }
      const partnerName = isUserA ? pair.userBName : pair.userAName;
      els.partnerText.textContent = partnerName
        ? `You and ${partnerName} are paired up.`
        : "You're paired up.";
      showPairingView("done");
    },
    (err) => showStatus(els.pairingStatus, permissionHint(err) || err.message, true),
  );
}

function watchUserDoc(uid) {
  if (unsubscribeUserDoc) unsubscribeUserDoc();
  unsubscribeUserDoc = onSnapshot(
    doc(db, "users", uid),
    (snap) => {
      if (!snap.exists()) return;
      const data = snap.data();
      els.nameInput.value = data.name || "";
      renderAvatar(data.name, data.avatarUrl || auth.currentUser?.photoURL);

      if (data.pairId) {
        watchPairDoc(data.pairId, uid);
      } else {
        if (unsubscribePairDoc) unsubscribePairDoc();
        showPairingView("choice");
      }
    },
    (err) => showStatus(els.status, permissionHint(err) || err.message, true),
  );
}

onAuthStateChanged(auth, (user) => {
  if (!user) {
    els.signedOut.hidden = false;
    els.signedIn.hidden = true;
    els.pairing.hidden = true;
    if (unsubscribeUserDoc) unsubscribeUserDoc();
    if (unsubscribePairDoc) unsubscribePairDoc();
    return;
  }
  els.signedOut.hidden = true;
  els.signedIn.hidden = false;
  els.pairing.hidden = false;
  els.email.textContent = user.email || "";
  renderAvatar(user.displayName, user.photoURL);
  watchUserDoc(user.uid);
});

els.signInBtn.addEventListener("click", async () => {
  showStatus(els.status, "", false);
  els.signInBtn.disabled = true;
  els.signInBtn.textContent = "Opening Google sign-in…";
  try {
    const result = await signInWithPopup(auth, new GoogleAuthProvider());
    const isNewUser = getAdditionalUserInfo(result)?.isNewUser ?? false;
    await ensureUserDocument(result.user, isNewUser);
  } catch (err) {
    showStatus(els.status, permissionHint(err) || `Sign-in failed: ${err.message}`, true);
  } finally {
    els.signInBtn.disabled = false;
    els.signInBtn.textContent = "Continue with Google";
  }
});

els.signOutBtn.addEventListener("click", () => signOut(auth));

els.saveBtn.addEventListener("click", async () => {
  const user = auth.currentUser;
  if (!user) return;
  const name = els.nameInput.value.trim();
  showStatus(els.saveStatus, "", false);
  els.saveBtn.disabled = true;
  els.saveBtn.textContent = "Saving…";
  try {
    // Only name/avatarUrl — the same whitelist firestore.rules' `allow
    // update` enforces; everything else on this document belongs to Cloud
    // Functions.
    await updateDoc(doc(db, "users", user.uid), { name, avatarUrl: user.photoURL || null });
    renderAvatar(name, user.photoURL);
    showStatus(els.saveStatus, "Saved.", false);
  } catch (err) {
    showStatus(els.saveStatus, permissionHint(err) || `Couldn't save: ${err.message}`, true);
  } finally {
    els.saveBtn.disabled = false;
    els.saveBtn.textContent = "Save";
  }
});

els.getInviteBtn.addEventListener("click", async () => {
  showStatus(els.pairingStatus, "", false);
  els.getInviteBtn.disabled = true;
  els.getInviteBtn.textContent = "Creating…";
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const result = await createPairFn({ timezone });
    pendingInviteCode = result.data.inviteCode;
    els.inviteCodeDisplay.textContent = pendingInviteCode;
    showPairingView("invite");
    // watchUserDoc's own onSnapshot will also pick up pairId once it lands
    // and start watching the pair doc — this just avoids a blank flash
    // between the callable returning and that snapshot arriving.
    watchPairDoc(result.data.pairId, auth.currentUser.uid);
  } catch (err) {
    showStatus(els.pairingStatus, permissionHint(err) || `Couldn't create an invite: ${err.message}`, true);
  } finally {
    els.getInviteBtn.disabled = false;
    els.getInviteBtn.textContent = "Get an invite code";
  }
});

els.showJoinBtn.addEventListener("click", () => showPairingView("join"));
els.backFromJoinBtn.addEventListener("click", () => showPairingView("choice"));

els.copyInviteBtn.addEventListener("click", async () => {
  if (!pendingInviteCode) return;
  await navigator.clipboard.writeText(pendingInviteCode);
  showStatus(els.pairingStatus, "Copied.", false);
});

els.joinBtn.addEventListener("click", async () => {
  const inviteCode = els.joinCodeInput.value.trim().toUpperCase();
  if (!inviteCode) return;
  showStatus(els.pairingStatus, "", false);
  els.joinBtn.disabled = true;
  els.joinBtn.textContent = "Joining…";
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const result = await joinPairFn({ inviteCode, timezone });
    watchPairDoc(result.data.pairId, auth.currentUser.uid);
  } catch (err) {
    showStatus(els.pairingStatus, permissionHint(err) || `Couldn't join: ${err.message}`, true);
  } finally {
    els.joinBtn.disabled = false;
    els.joinBtn.textContent = "Join";
  }
});
