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
const listGenresFn = httpsCallable(functions, "listGenres");
const getOnboardingFilmsFn = httpsCallable(functions, "getOnboardingFilms");

// Mirrors OnboardingConfig.kt — the client only decides UX (how many genres
// before "Start rating" enables, how big a deck to ask for); the server is
// the authority on when onboarding actually counts as complete.
const RATING_TARGET = 10;
const MIN_GENRES = 1;
const DECK_SIZE = 20;
const DEFAULT_SCORE = 50;

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

  onboarding: document.getElementById("onboarding"),
  obGenres: document.getElementById("obGenres"),
  obDeck: document.getElementById("obDeck"),
  obExhausted: document.getElementById("obExhausted"),
  genreChips: document.getElementById("genreChips"),
  startDeckBtn: document.getElementById("startDeckBtn"),
  obProgress: document.getElementById("obProgress"),
  obProgressFill: document.getElementById("obProgressFill"),
  obPoster: document.getElementById("obPoster"),
  obFilmTitle: document.getElementById("obFilmTitle"),
  obFilmMeta: document.getElementById("obFilmMeta"),
  obScore: document.getElementById("obScore"),
  obScoreLabel: document.getElementById("obScoreLabel"),
  obDial: document.getElementById("obDial"),
  obRateBtn: document.getElementById("obRateBtn"),
  obSkipBtn: document.getElementById("obSkipBtn"),
  obExtendBtn: document.getElementById("obExtendBtn"),
  obStatus: document.getElementById("obStatus"),

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
  continueFromInviteBtn: document.getElementById("continueFromInviteBtn"),
  continueFromDoneBtn: document.getElementById("continueFromDoneBtn"),
  partnerText: document.getElementById("partnerText"),
  pairingStatus: document.getElementById("pairingStatus"),

  matchPlaceholder: document.getElementById("matchPlaceholder"),
};

let unsubscribeUserDoc = null;
let latestUserData = null;
let routeDecided = false;

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

/* ============================================================
   ONBOARDING DRAFT BUFFER — mirrors OnboardingDraftStore.kt.
   Ratings live at pairs/{pairId}/ratings, so they need a pair; the product
   order is rate-first-invite-second, so scores are buffered locally until
   there is a pair to flush them into.
   ============================================================ */
const DRAFT_KEY = "moviemate_onboarding_draft";

function draftRatings() {
  try {
    return JSON.parse(localStorage.getItem(DRAFT_KEY) || "[]");
  } catch {
    return [];
  }
}
function draftRecord(filmId, score) {
  const updated = draftRatings().filter((r) => r.filmId !== filmId);
  updated.push({ filmId, score });
  localStorage.setItem(DRAFT_KEY, JSON.stringify(updated));
}
function draftCount() {
  return draftRatings().length;
}
function draftClear() {
  localStorage.removeItem(DRAFT_KEY);
}

/**
 * Write the buffered scores into the pair, then clear the buffer — mirrors
 * OnboardingDraftStore.flush. Sequential, not parallel, for the same reason
 * the Kotlin version is: this runs once per user, ever, not a hot path
 * worth parallelizing at the cost of ten redundant onRatingComplete races.
 */
async function flushDraftIntoPair(pairId, uid) {
  const pending = draftRatings();
  for (const draft of pending) {
    await setDoc(doc(db, "pairs", pairId, "ratings", `${uid}_${draft.filmId}`), {
      userId: uid,
      filmId: draft.filmId,
      score: draft.score,
      isInitialOnboarding: true,
      reactionEmoji: null,
      ratedAt: serverTimestamp(),
    });
  }
  draftClear();
}

/* ============================================================
   PROFILE (sign-in, profile doc, save)
   ============================================================ */

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

/* ============================================================
   ROUTING — decided ONCE per sign-in, like AppEntryViewModel.startRouteFor,
   not continuously recomputed. Recomputing on every Firestore snapshot
   would yank a user out of (say) the invite-code screen the instant their
   own pairId loads, before they have had a chance to copy it. Screens
   navigate FORWARD from here explicitly (see goToPairing/goToMatch below),
   the same way Android's screens do.
   ============================================================ */

function showSection(name) {
  els.onboarding.hidden = name !== "onboarding";
  els.pairing.hidden = name !== "pairing";
  els.matchPlaceholder.hidden = name !== "match";
}

function decideInitialSection(userData, pairData) {
  const isPaired = !!userData.pairId && !!pairData;
  const onboardingComplete = !!userData.onboardingComplete;
  const bothOnboarded = !!pairData?.aBothOnboarded;
  if (isPaired && onboardingComplete) return "match";
  if (bothOnboarded) return "match";
  if (isPaired) return "onboarding";
  if (draftCount() >= RATING_TARGET) return "pairing";
  return "onboarding";
}

async function maybeDecideInitialRoute() {
  if (routeDecided || !latestUserData) return;
  const pairId = latestUserData.pairId;
  let pairData = null;
  if (pairId) {
    // One-time read, not a listener — this is a launch-time decision, not
    // something that should keep re-firing.
    const snap = await getDoc(doc(db, "pairs", pairId));
    pairData = snap.exists() ? snap.data() : null;
  }
  routeDecided = true;
  const section = decideInitialSection(latestUserData, pairData);
  showSection(section);
  if (section === "onboarding") initOnboarding();
}

/** Called by the onboarding/pairing screens themselves once they're done. */
function goToPairing() {
  showSection("pairing");
  showPairingView("choice");
}
function goToMatch() {
  showSection("match");
}

function watchUserDoc(uid) {
  if (unsubscribeUserDoc) unsubscribeUserDoc();
  unsubscribeUserDoc = onSnapshot(
    doc(db, "users", uid),
    (snap) => {
      if (!snap.exists()) return;
      const data = snap.data();
      latestUserData = data;
      els.nameInput.value = data.name || "";
      renderAvatar(data.name, data.avatarUrl || auth.currentUser?.photoURL);
      maybeDecideInitialRoute();
    },
    (err) => showStatus(els.status, permissionHint(err) || err.message, true),
  );
}

onAuthStateChanged(auth, (user) => {
  if (!user) {
    els.signedOut.hidden = false;
    els.signedIn.hidden = true;
    showSection(null);
    routeDecided = false;
    latestUserData = null;
    if (unsubscribeUserDoc) unsubscribeUserDoc();
    return;
  }
  els.signedOut.hidden = true;
  els.signedIn.hidden = false;
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

/* ============================================================
   ONBOARDING — genre pick, then a Taste Dial rate deck. Mirrors
   OnboardingRateViewModel's RateStep state machine.
   ============================================================ */

const ob = {
  step: "genres", // "genres" | "deck" | "exhausted"
  genres: [],
  selected: new Set(),
  films: [],
  index: 0,
  score: DEFAULT_SCORE,
  recorded: 0,
};

function dialLabel(v) {
  if (v <= 20) return "Not for us";
  if (v <= 50) return "It was fine";
  if (v <= 75) return "Really good";
  return "Obsessed";
}

async function initOnboarding() {
  showStatus(els.obStatus, "", false);
  ob.step = "genres";
  renderOnboarding();
  try {
    const result = await listGenresFn();
    ob.genres = result.data.genres;
    renderGenreChips();
  } catch (err) {
    showStatus(els.obStatus, permissionHint(err) || `Couldn't load genres: ${err.message}`, true);
  }
}

function renderGenreChips() {
  els.genreChips.innerHTML = "";
  ob.genres.forEach((genre) => {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip" + (ob.selected.has(genre.id) ? " is-selected" : "");
    chip.textContent = genre.name;
    chip.addEventListener("click", () => {
      if (ob.selected.has(genre.id)) ob.selected.delete(genre.id);
      else ob.selected.add(genre.id);
      renderGenreChips();
      els.startDeckBtn.disabled = ob.selected.size < MIN_GENRES;
    });
    els.genreChips.appendChild(chip);
  });
  els.startDeckBtn.disabled = ob.selected.size < MIN_GENRES;
}

function renderOnboarding() {
  els.obGenres.hidden = ob.step !== "genres";
  els.obDeck.hidden = ob.step !== "deck";
  els.obExhausted.hidden = ob.step !== "exhausted";
}

function renderDeckCard() {
  const film = ob.films[ob.index];
  if (!film) return;
  els.obProgress.textContent = `${ob.recorded} of ${RATING_TARGET}`;
  els.obProgressFill.style.width = `${Math.min(100, (ob.recorded / RATING_TARGET) * 100)}%`;
  els.obFilmTitle.textContent = film.title;
  els.obFilmMeta.textContent = `${film.releaseYear || ""}${film.genres?.length ? " · " + film.genres.slice(0, 2).join(" · ") : ""}`;
  if (film.posterPath) {
    els.obPoster.style.backgroundImage = `url(https://image.tmdb.org/t/p/w342${film.posterPath})`;
    els.obPoster.textContent = "";
  } else {
    els.obPoster.style.backgroundImage = "none";
    els.obPoster.textContent = film.title;
  }
  ob.score = DEFAULT_SCORE;
  els.obDial.value = String(DEFAULT_SCORE);
  els.obScore.textContent = String(DEFAULT_SCORE);
  els.obScoreLabel.textContent = dialLabel(DEFAULT_SCORE);
}

els.startDeckBtn.addEventListener("click", async () => {
  if (ob.selected.size < MIN_GENRES) return;
  showStatus(els.obStatus, "", false);
  els.startDeckBtn.disabled = true;
  els.startDeckBtn.textContent = "Loading…";
  try {
    const result = await getOnboardingFilmsFn({
      genreIds: [...ob.selected],
      size: DECK_SIZE,
    });
    ob.films = result.data.films;
    ob.index = 0;
    ob.recorded = latestUserData?.pairId ? latestUserData.ratingCount || 0 : draftCount();
    ob.step = "deck";
    renderOnboarding();
    renderDeckCard();
  } catch (err) {
    showStatus(els.obStatus, permissionHint(err) || `Couldn't load films: ${err.message}`, true);
  } finally {
    els.startDeckBtn.disabled = false;
    els.startDeckBtn.textContent = "Start rating";
  }
});

els.obDial.addEventListener("input", () => {
  ob.score = Number(els.obDial.value);
  els.obScore.textContent = String(ob.score);
  els.obScoreLabel.textContent = dialLabel(ob.score);
});

/** Advances the deck immediately — a rating is not something to block a spinner on. */
function advanceDeck(recorded) {
  ob.recorded = recorded;
  if (recorded >= RATING_TARGET) {
    if (latestUserData?.pairId) {
      goToMatch();
    } else {
      goToPairing();
    }
    return;
  }
  ob.index += 1;
  if (ob.index >= ob.films.length) {
    ob.step = "exhausted";
    renderOnboarding();
    return;
  }
  renderDeckCard();
}

els.obRateBtn.addEventListener("click", async () => {
  const film = ob.films[ob.index];
  if (!film) return;
  const score = ob.score;
  const recordedBefore = ob.recorded;
  advanceDeck(recordedBefore + 1);

  const pairId = latestUserData?.pairId;
  const uid = auth.currentUser?.uid;
  try {
    if (pairId) {
      await setDoc(doc(db, "pairs", pairId, "ratings", `${uid}_${film.filmId}`), {
        userId: uid,
        filmId: film.filmId,
        score,
        isInitialOnboarding: true,
        reactionEmoji: null,
        ratedAt: serverTimestamp(),
      });
    } else {
      draftRecord(film.filmId, score);
    }
  } catch (err) {
    showStatus(els.obStatus, permissionHint(err) || `Couldn't save that rating: ${err.message}`, true);
  }
});

els.obSkipBtn.addEventListener("click", () => advanceDeck(ob.recorded));

els.obExtendBtn.addEventListener("click", async () => {
  showStatus(els.obStatus, "", false);
  els.obExtendBtn.disabled = true;
  try {
    const result = await getOnboardingFilmsFn({ genreIds: [...ob.selected], size: DECK_SIZE });
    const seen = new Set(ob.films.map((f) => f.filmId));
    const fresh = result.data.films.filter((f) => !seen.has(f.filmId));
    if (fresh.length === 0) {
      showStatus(els.obStatus, "No new films for these genres right now.", true);
      return;
    }
    ob.films = ob.films.concat(fresh);
    ob.step = "deck";
    renderOnboarding();
    renderDeckCard();
  } catch (err) {
    showStatus(els.obStatus, permissionHint(err) || `Couldn't load more films: ${err.message}`, true);
  } finally {
    els.obExtendBtn.disabled = false;
  }
});

/* ============================================================
   PAIRING — invite/join. Reached only once RATING_TARGET is hit (or
   already paired with onboarding still in progress never reaches this
   screen at all — see decideInitialSection).
   ============================================================ */

let pendingInviteCode = null;

function showPairingView(view) {
  els.pairingChoice.hidden = view !== "choice";
  els.pairingInvite.hidden = view !== "invite";
  els.pairingJoin.hidden = view !== "join";
  els.pairingDone.hidden = view !== "done";
}

els.getInviteBtn.addEventListener("click", async () => {
  showStatus(els.pairingStatus, "", false);
  els.getInviteBtn.disabled = true;
  els.getInviteBtn.textContent = "Creating…";
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const result = await createPairFn({ timezone });
    pendingInviteCode = result.data.inviteCode;
    await flushDraftIntoPair(result.data.pairId, auth.currentUser.uid);
    els.inviteCodeDisplay.textContent = pendingInviteCode;
    showPairingView("invite");
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

els.continueFromInviteBtn.addEventListener("click", goToMatch);
els.continueFromDoneBtn.addEventListener("click", goToMatch);

els.joinBtn.addEventListener("click", async () => {
  const inviteCode = els.joinCodeInput.value.trim().toUpperCase();
  if (!inviteCode) return;
  showStatus(els.pairingStatus, "", false);
  els.joinBtn.disabled = true;
  els.joinBtn.textContent = "Joining…";
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const result = await joinPairFn({ inviteCode, timezone });
    await flushDraftIntoPair(result.data.pairId, auth.currentUser.uid);
    els.partnerText.textContent = "You're paired up.";
    showPairingView("done");
  } catch (err) {
    showStatus(els.pairingStatus, permissionHint(err) || `Couldn't join: ${err.message}`, true);
  } finally {
    els.joinBtn.disabled = false;
    els.joinBtn.textContent = "Join";
  }
});
