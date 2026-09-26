import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  onAuthStateChanged,
  getAdditionalUserInfo,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  deleteDoc,
  limit,
  orderBy,
  query,
  setDoc,
  updateDoc,
  onSnapshot,
  serverTimestamp,
  writeBatch,
  arrayUnion,
  arrayRemove,
  Timestamp,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";
import { fetchFilmById, fetchGenres, fetchOnboardingFilms } from "./tmdb.js";
import { advancePairStreak, generateTodaysMatch, isBothOnboarded, onboardingRatingCount } from "./match.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

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
  backToFriendsFromChoiceBtn: document.getElementById("backToFriendsFromChoiceBtn"),
  joinBtn: document.getElementById("joinBtn"),
  joinCodeInput: document.getElementById("joinCodeInput"),
  inviteCodeDisplay: document.getElementById("inviteCodeDisplay"),
  copyInviteBtn: document.getElementById("copyInviteBtn"),
  continueFromInviteBtn: document.getElementById("continueFromInviteBtn"),
  continueFromDoneBtn: document.getElementById("continueFromDoneBtn"),
  partnerText: document.getElementById("partnerText"),
  pairingStatus: document.getElementById("pairingStatus"),

  friends: document.getElementById("friends"),
  friendsList: document.getElementById("friendsList"),
  friendsEmpty: document.getElementById("friendsEmpty"),
  addFriendBtn: document.getElementById("addFriendBtn"),
  friendsStatus: document.getElementById("friendsStatus"),

  match: document.getElementById("match"),
  backToFriendsBtn: document.getElementById("backToFriendsBtn"),
  matchWaiting: document.getElementById("matchWaiting"),
  matchWaitingText: document.getElementById("matchWaitingText"),
  matchNotYet: document.getElementById("matchNotYet"),
  findMatchBtn: document.getElementById("findMatchBtn"),
  matchNoMatches: document.getElementById("matchNoMatches"),
  matchNoMatchesReason: document.getElementById("matchNoMatchesReason"),
  matchSuggested: document.getElementById("matchSuggested"),
  matchScoreLabel: document.getElementById("matchScoreLabel"),
  matchPoster: document.getElementById("matchPoster"),
  matchFilmTitle: document.getElementById("matchFilmTitle"),
  matchReason: document.getElementById("matchReason"),
  matchCommitBtn: document.getElementById("matchCommitBtn"),
  matchWaitingOnPartner: document.getElementById("matchWaitingOnPartner"),
  matchConfirmed: document.getElementById("matchConfirmed"),
  confirmedPoster: document.getElementById("confirmedPoster"),
  confirmedFilmTitle: document.getElementById("confirmedFilmTitle"),
  markWatchedBtn: document.getElementById("markWatchedBtn"),
  matchWatchedView: document.getElementById("matchWatchedView"),
  streakText: document.getElementById("streakText"),
  matchRateDialWrap: document.getElementById("matchRateDialWrap"),
  matchRateScore: document.getElementById("matchRateScore"),
  matchRateScoreLabel: document.getElementById("matchRateScoreLabel"),
  matchRateDial: document.getElementById("matchRateDial"),
  matchRateBtn: document.getElementById("matchRateBtn"),
  matchRatedDone: document.getElementById("matchRatedDone"),
  matchRetryBtn: document.getElementById("matchRetryBtn"),
  matchStatus: document.getElementById("matchStatus"),
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
    pairIds: [],
    activePairId: null,
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
  els.friends.hidden = name !== "friends";
  els.match.hidden = name !== "match";
}

/**
 * Which pair's onboarding rating deck is currently running, if any: null
 * means the pre-pairing draft buffer (the very first pair a user ever
 * makes — see the DRAFT_KEY block above), a pairId means this is a repeat
 * trip through the deck for an ADDITIONAL friend, who starts that pair's
 * ratings at zero regardless of how many other friends this user already
 * has (see the multi-friend note above initFriendsSection).
 */
let onboardingPairId = null;

/**
 * One-time upgrade for users who signed up before multi-friend support:
 * their single users/{uid}.pairId becomes the first entry of pairIds, and
 * their current pair becomes their active one. Mutates `data` in place (the
 * caller's about-to-be-`latestUserData` object) so routing decided right
 * after this call sees the migrated shape without waiting for the snapshot
 * this write triggers to come back around.
 */
async function migrateLegacyPairId(uid, data) {
  if (!data.pairId || (data.pairIds && data.pairIds.length > 0)) return;
  data.pairIds = [data.pairId];
  data.activePairId = data.activePairId || data.pairId;
  try {
    await updateDoc(doc(db, "users", uid), {
      pairIds: arrayUnion(data.pairId),
      activePairId: data.activePairId,
    });
  } catch {
    // Best-effort — the next snapshot retries this from scratch.
  }
}

async function maybeDecideInitialRoute() {
  if (routeDecided || !latestUserData) return;
  routeDecided = true;
  // The profile card (name + sign out) is only the very first, momentary
  // step — it's for the one-time "here's how I want to be known to my
  // friends" edit right after signing in, not a permanent header sitting
  // above onboarding/pairing/friends/match. Hide it the instant routing
  // lands somewhere; editing the name again is a "sign out and back in"
  // affair for now, same as this client not having a separate profile
  // screen yet.
  els.signedIn.hidden = true;

  const pairIds = latestUserData.pairIds || [];
  if (pairIds.length === 0) {
    if (draftCount() >= RATING_TARGET) {
      goToPairing();
    } else {
      onboardingPairId = null;
      showSection("onboarding");
      initOnboarding();
    }
    return;
  }
  showSection("friends");
  initFriendsSection();
}

/** Called by the onboarding/pairing screens themselves once they're done. */
function goToPairing() {
  showSection("pairing");
  els.backToFriendsFromChoiceBtn.hidden = !(latestUserData?.pairIds?.length > 0);
  showPairingView("choice");
}

/**
 * Enter a specific pair by id: this user's own rating history for THAT
 * pair decides whether they land in its onboarding deck (a brand new
 * friend always starts at zero, no matter how many other friends this user
 * already has — see the multi-friend note above initFriendsSection) or
 * straight into its match section.
 */
async function enterPair(pairId) {
  if (!pairId) return;
  const myCount = await onboardingRatingCount(db, pairId, auth.currentUser.uid);
  if (myCount < RATING_TARGET) {
    onboardingPairId = pairId;
    showSection("onboarding");
    initOnboarding();
    return;
  }
  await goToMatch(pairId);
}

async function goToMatch(pairId) {
  showSection("match");
  if (!pairId) return;
  if (latestUserData?.activePairId !== pairId) {
    try {
      await updateDoc(doc(db, "users", auth.currentUser.uid), { activePairId: pairId });
    } catch {
      // Non-fatal — activePairId is only a UI convenience (see firestore.rules).
    }
  }
  const snap = await getDoc(doc(db, "pairs", pairId));
  if (snap.exists()) initMatchSection(pairId, snap.data());
}

function backToFriends() {
  stopMatchSection();
  showSection("friends");
  initFriendsSection();
}

function watchUserDoc(uid) {
  if (unsubscribeUserDoc) unsubscribeUserDoc();
  unsubscribeUserDoc = onSnapshot(
    doc(db, "users", uid),
    async (snap) => {
      if (!snap.exists()) return;
      const data = snap.data();
      await migrateLegacyPairId(uid, data);
      latestUserData = data;
      els.nameInput.value = data.name || "";
      renderAvatar(data.name, data.avatarUrl || auth.currentUser?.photoURL);
      maybeDecideInitialRoute();
      if (!els.friends.hidden) initFriendsSection();
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
    onboardingPairId = null;
    if (unsubscribeUserDoc) unsubscribeUserDoc();
    stopMatchSection();
    stopWatchingAllFriends();
    return;
  }
  els.signedOut.hidden = true;
  els.signedIn.hidden = false;
  els.email.textContent = user.email || "";
  renderAvatar(user.displayName, user.photoURL);
  watchUserDoc(user.uid);
});

// Mobile browsers (Safari on iOS especially) frequently tear down the
// signInWithPopup window before the OAuth round trip finishes — Firebase
// surfaces that as auth/popup-closed-by-user even though the person never
// touched anything. signInWithRedirect sidesteps the popup entirely (full
// navigation to Google and back), which is Firebase's own recommendation
// for mobile web. Desktop keeps the popup: it's the nicer UX there and
// doesn't have this failure mode.
const isMobileBrowser = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

els.signInBtn.addEventListener("click", async () => {
  showStatus(els.status, "", false);
  els.signInBtn.disabled = true;
  els.signInBtn.textContent = "Opening Google sign-in…";
  try {
    if (isMobileBrowser) {
      // Navigates away; the result is picked up by getRedirectResult()
      // below once Google sends the browser back here.
      await signInWithRedirect(auth, new GoogleAuthProvider());
      return;
    }
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

// Completes the signInWithRedirect flow above once Google sends the
// browser back to this page. A no-op (resolves to null) on every load
// that isn't returning from a redirect, including the very first visit.
getRedirectResult(auth)
  .then(async (result) => {
    if (!result) return;
    const isNewUser = getAdditionalUserInfo(result)?.isNewUser ?? false;
    await ensureUserDocument(result.user, isNewUser);
  })
  .catch((err) => {
    showStatus(els.status, permissionHint(err) || `Sign-in failed: ${err.message}`, true);
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
  genreNamesById: new Map(),
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
    ob.genres = await fetchGenres();
    ob.genreNamesById = new Map(ob.genres.map((g) => [g.id, g.name]));
    renderGenreChips();
  } catch (err) {
    showStatus(els.obStatus, `Couldn't load genres: ${err.message}`, true);
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
    ob.films = await fetchOnboardingFilms([...ob.selected], DECK_SIZE, ob.genreNamesById);
    ob.index = 0;
    ob.recorded = onboardingPairId
      ? await onboardingRatingCount(db, onboardingPairId, auth.currentUser.uid)
      : draftCount();
    ob.step = "deck";
    renderOnboarding();
    renderDeckCard();
  } catch (err) {
    showStatus(els.obStatus, `Couldn't load films: ${err.message}`, true);
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
    if (onboardingPairId) {
      goToMatch(onboardingPairId);
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

  const pairId = onboardingPairId;
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
    const seen = new Set(ob.films.map((f) => f.filmId));
    const fresh = await fetchOnboardingFilms([...ob.selected], DECK_SIZE, ob.genreNamesById, seen);
    if (fresh.length === 0) {
      showStatus(els.obStatus, "No new films for these genres right now.", true);
      return;
    }
    ob.films = ob.films.concat(fresh);
    ob.step = "deck";
    renderOnboarding();
    renderDeckCard();
  } catch (err) {
    showStatus(els.obStatus, `Couldn't load more films: ${err.message}`, true);
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
let pendingPairId = null;

function showPairingView(view) {
  els.pairingChoice.hidden = view !== "choice";
  els.pairingInvite.hidden = view !== "invite";
  els.pairingJoin.hidden = view !== "join";
  els.pairingDone.hidden = view !== "done";
}

// Mirrors functions/src/lib/pairs.ts's generateInviteCode/INVITE_CODE_TTL_MS —
// createPair/joinPair are Cloud Functions, which require the Blaze plan to
// deploy at all (see firestore.rules deviation d). createPairDirect and
// joinPairDirect below do the same two writes those callables' Admin-SDK
// transactions would, as direct client writes gated by claimsOwnPair() and
// joinsOpenSeat().
const INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1
const INVITE_CODE_LENGTH = 6;
const INVITE_CODE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days, ALI-73

function generateInviteCode() {
  let code = "";
  for (let i = 0; i < INVITE_CODE_LENGTH; i += 1) {
    code += INVITE_CODE_ALPHABET[Math.floor(Math.random() * INVITE_CODE_ALPHABET.length)];
  }
  return `MVMT-${code}`;
}

/**
 * Creates the pair + its /inviteCodes lookup entry in one batch, then claims
 * the creator's own users/{uid}.pairId as a second write — claimsOwnPair()
 * requires the pair to already exist, so that claim genuinely can't be part
 * of the same batch. Retries on the (unlikely) invite-code collision, same
 * as createPair.ts: a collision shows up here as the inviteCodes write being
 * evaluated as an update (the doc already exists) against a rule that never
 * allows update, so it fails with permission-denied.
 *
 * Multi-friend note: 'pairId' is only ever claimable while it's still null
 * (claimsOwnPair() in firestore.rules), so it's written once, for a user's
 * very first pair, and left alone for every pair after that — it's a legacy
 * field now, superseded by pairIds/activePairId, which are plain self-serve
 * fields and get appended to on every pair regardless. The two updates stay
 * separate calls because they're matched by two different rule branches
 * (claimsOwnPair vs. the field whitelist) that each reject the other's keys.
 */
async function createPairDirect(uid, timezone, name, avatarUrl) {
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const inviteCode = generateInviteCode();
    const pairRef = doc(collection(db, "pairs"));
    const inviteCodeExpiresAt = Timestamp.fromMillis(Date.now() + INVITE_CODE_TTL_MS);

    const batch = writeBatch(db);
    batch.set(pairRef, {
      userA: uid,
      userB: null,
      inviteCode,
      inviteCodeExpiresAt,
      status: "waiting_partner",
      createdAt: serverTimestamp(),
      aBothOnboarded: false,
      streakCount: 0,
      lastMatchGeneratedAt: null,
      lastWatchAt: null,
      timezone,
      // Denormalized the same way joinPairDirect denormalizes userB's —
      // there's no onUserProfileUpdated trigger on this no-Blaze path (it's
      // Cloud-Functions-only) to backfill this later, so it has to be set
      // right here at creation or the friends list never has a name to show
      // the other side (see friendDisplayName in the FRIENDS section).
      userAName: name,
      userAAvatarUrl: avatarUrl,
    });
    batch.set(doc(db, "inviteCodes", inviteCode), {
      pairId: pairRef.id,
      expiresAt: inviteCodeExpiresAt,
    });

    try {
      await batch.commit();
    } catch (err) {
      if (err?.code === "permission-denied") continue;
      throw err;
    }

    if (!latestUserData?.pairId) {
      await updateDoc(doc(db, "users", uid), { pairId: pairRef.id });
    }
    await updateDoc(doc(db, "users", uid), {
      pairIds: arrayUnion(pairRef.id),
      activePairId: pairRef.id,
    });
    return { pairId: pairRef.id, inviteCode };
  }
  throw new Error("Could not allocate an invite code. Try again.");
}

/**
 * Resolves the code via /inviteCodes, claims the open seat, then claims the
 * joiner's own pairId — the same facts joinPair's transaction establishes
 * atomically, as three separate client writes. The pre-checks below exist
 * for a friendly error message; joinsOpenSeat() re-checks all of this
 * server-side regardless, so a race with someone else joining first is still
 * safe even though these reads are not.
 */
async function joinPairDirect(uid, inviteCode, name, avatarUrl) {
  const codeSnap = await getDoc(doc(db, "inviteCodes", inviteCode));
  if (!codeSnap.exists()) throw new Error("That code doesn't match any invite.");

  const { pairId } = codeSnap.data();
  const pairSnap = await getDoc(doc(db, "pairs", pairId));
  if (!pairSnap.exists()) throw new Error("That code doesn't match any invite.");

  const pair = pairSnap.data();
  if (pair.userA === uid) throw new Error("That's your own invite code.");
  if (pair.userB !== null) throw new Error("This invite has already been used.");
  if (pair.inviteCodeExpiresAt.toMillis() <= Date.now()) {
    throw new Error("This invite code has expired.");
  }

  try {
    await updateDoc(doc(db, "pairs", pairId), {
      userB: uid,
      status: "both_rating",
      joinedAt: serverTimestamp(),
      userBName: name,
      userBAvatarUrl: avatarUrl,
    });
  } catch (err) {
    if (err?.code === "permission-denied") {
      throw new Error("This invite has already been used.");
    }
    throw err;
  }

  if (!latestUserData?.pairId) {
    await updateDoc(doc(db, "users", uid), { pairId });
  }
  await updateDoc(doc(db, "users", uid), {
    pairIds: arrayUnion(pairId),
    activePairId: pairId,
  });
  return { pairId, partnerUid: pair.userA };
}

els.getInviteBtn.addEventListener("click", async () => {
  showStatus(els.pairingStatus, "", false);
  els.getInviteBtn.disabled = true;
  els.getInviteBtn.textContent = "Creating…";
  try {
    const uid = auth.currentUser.uid;
    const timezone =
      latestUserData?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
    const user = auth.currentUser;
    const { pairId, inviteCode } = await createPairDirect(
      uid,
      timezone,
      latestUserData?.name || user.displayName || "",
      user.photoURL || null
    );
    pendingInviteCode = inviteCode;
    pendingPairId = pairId;
    await flushDraftIntoPair(pairId, uid);
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
els.backToFriendsFromChoiceBtn.addEventListener("click", backToFriends);

els.copyInviteBtn.addEventListener("click", async () => {
  if (!pendingInviteCode) return;
  await navigator.clipboard.writeText(pendingInviteCode);
  showStatus(els.pairingStatus, "Copied.", false);
});

els.continueFromInviteBtn.addEventListener("click", () => enterPair(pendingPairId));
els.continueFromDoneBtn.addEventListener("click", () => enterPair(pendingPairId));

els.joinBtn.addEventListener("click", async () => {
  const inviteCode = els.joinCodeInput.value.trim().toUpperCase();
  if (!inviteCode) return;
  showStatus(els.pairingStatus, "", false);
  els.joinBtn.disabled = true;
  els.joinBtn.textContent = "Joining…";
  try {
    const user = auth.currentUser;
    const { pairId } = await joinPairDirect(
      user.uid,
      inviteCode,
      latestUserData?.name || user.displayName || "",
      user.photoURL || null
    );
    pendingPairId = pairId;
    await flushDraftIntoPair(pairId, user.uid);
    els.partnerText.textContent = "You're paired up.";
    showPairingView("done");
  } catch (err) {
    showStatus(els.pairingStatus, permissionHint(err) || err.message, true);
  } finally {
    els.joinBtn.disabled = false;
    els.joinBtn.textContent = "Join";
  }
});

/* ============================================================
   MATCH — the daily loop. Mirrors MatchPhase.kt's state hierarchy, scoped
   to what this pass builds: NotYet, WaitingForPartner, NoMatches, Suggested,
   Confirmed (both committed) and Watched. NOT built yet, same as the
   Android app's more advanced states this omits on purpose: the 3-up
   reject/fallback sequence and the schedule-watch time picker — real next
   slices, not skipped by accident (see README).

   Generation itself is a no-Blaze fallback (firestore.rules deviation e):
   there's no scheduled function to run it automatically, so it's a client
   action — "Find tonight's movie" — rather than something that's just
   there at 9am. web/match.js does the actual work (build taste profiles,
   score a TMDB candidate pool, write the match).
   ============================================================ */

let mtUnsubPair = null;
let mtUnsubMatch = null;
let mtRenderToken = 0;
const mt = {
  pairId: null,
  pair: null,
  match: null,
  matchId: null,
  // Once true, both-onboarded can never become false again for this pair —
  // cached so re-renders (every commit, every streak update, ...) don't
  // re-run two aggregate-count queries that can only ever confirm the same
  // answer again.
  bothOnboardedConfirmed: false,
  // Tracks which match's watchlist promotion has already been attempted, so
  // a re-render while sitting in the "confirmed" phase doesn't retry a
  // write the rules will reject anyway (the doc already exists by then).
  promotedMatchId: null,
};
const filmDetailsCache = new Map();

async function getFilmDetails(filmId) {
  if (!filmId) return null;
  if (filmDetailsCache.has(filmId)) return filmDetailsCache.get(filmId);
  const film = await fetchFilmById(filmId);
  filmDetailsCache.set(filmId, film);
  return film;
}

function showMatchView(name) {
  els.matchWaiting.hidden = name !== "waiting";
  els.matchNotYet.hidden = name !== "notYet";
  els.matchNoMatches.hidden = name !== "noMatches";
  els.matchSuggested.hidden = name !== "suggested";
  els.matchConfirmed.hidden = name !== "confirmed";
  els.matchWatchedView.hidden = name !== "watched";
}

function setPoster(el, film) {
  if (film?.posterPath) {
    el.style.backgroundImage = `url(https://image.tmdb.org/t/p/w342${film.posterPath})`;
    el.textContent = "";
  } else {
    el.style.backgroundImage = "none";
    el.textContent = film?.title || "";
  }
}

async function renderSuggestedView(match, mySide) {
  els.matchScoreLabel.textContent = `${match.score}% shared taste`;
  els.matchReason.textContent = match.reason;
  const film = await getFilmDetails(match.filmId);
  els.matchFilmTitle.textContent = film?.title || "Tonight's pick";
  setPoster(els.matchPoster, film);

  const iCommitted = !!match.commitStatus[mySide];
  els.matchCommitBtn.hidden = iCommitted;
  els.matchWaitingOnPartner.hidden = !iCommitted;
}

async function renderConfirmedView(match) {
  const film = await getFilmDetails(match.filmId);
  els.confirmedFilmTitle.textContent = film?.title || "Tonight's pick";
  setPoster(els.confirmedPoster, film);
}

async function renderWatchedView(match) {
  els.streakText.textContent = mt.pair.streakCount
    ? `${mt.pair.streakCount} day streak`
    : "Watched.";

  const uid = auth.currentUser.uid;
  const ratingSnap = await getDoc(doc(db, "pairs", mt.pairId, "ratings", `${uid}_${match.filmId}`));
  const alreadyRated = ratingSnap.exists();
  els.matchRateDialWrap.hidden = alreadyRated;
  els.matchRateBtn.hidden = alreadyRated;
  els.matchRatedDone.hidden = !alreadyRated;
}

/**
 * Mirrors onMatchUpdate's handleMutualCommit: a film both partners committed
 * to belongs on the shared watchlist. The watchlist doc id is the match id
 * (not an auto-id) specifically so this is idempotent — both partners'
 * clients reach this branch when the second commit's snapshot propagates,
 * and the second create attempt lands on an existing doc, gets evaluated as
 * an update against a rule that only ever allows a commitStatus-only change,
 * and fails harmlessly.
 */
async function ensurePromotedToWatchlist(match, matchId) {
  if (mt.promotedMatchId === matchId) return; // already attempted for this match
  mt.promotedMatchId = matchId;
  try {
    await setDoc(doc(db, "pairs", mt.pairId, "watchlist", matchId), {
      filmId: match.filmId,
      addedBy: auth.currentUser.uid,
      addedAt: serverTimestamp(),
      source: "match",
      status: "waiting",
      commitStatus: match.commitStatus,
      watchedAt: null,
      mutualScore: null,
    });
  } catch {
    // Already promoted, by this client or the partner's — expected and safe.
  }
}

async function renderMatchSection() {
  if (!mt.pair) return;
  const token = ++mtRenderToken;
  if (!mt.bothOnboardedConfirmed) {
    mt.bothOnboardedConfirmed = await isBothOnboarded(db, mt.pairId, mt.pair);
    if (token !== mtRenderToken) return; // a newer render started while this awaited
  }
  const ready = mt.bothOnboardedConfirmed;

  if (!ready) {
    els.matchWaitingText.textContent = "Waiting for your partner to finish rating their films.";
    showMatchView("waiting");
    els.matchRetryBtn.hidden = true;
    return;
  }

  const match = mt.match;
  if (!match) {
    showMatchView("notYet");
    els.matchRetryBtn.hidden = true;
    return;
  }

  if (match.watchedConfirmedAt) {
    showMatchView("watched");
    renderWatchedView(match);
    els.matchRetryBtn.hidden = !canGenerateAgain(mt.pair);
    return;
  }

  els.matchRetryBtn.hidden = true;

  if (match.commitStatus.userA && match.commitStatus.userB) {
    showMatchView("confirmed");
    renderConfirmedView(match);
    ensurePromotedToWatchlist(match, mt.matchId);
    return;
  }

  if (!match.filmId) {
    els.matchNoMatchesReason.textContent =
      match.noMatchesReason || "Nothing scored high enough for both of you today.";
    showMatchView("noMatches");
    els.matchRetryBtn.hidden = !canGenerateAgain(mt.pair);
    return;
  }

  const uid = auth.currentUser.uid;
  const mySide = mt.pair.userA === uid ? "userA" : "userB";
  showMatchView("suggested");
  renderSuggestedView(match, mySide);
}

/**
 * Mirrors createsTodaysMatch()'s 20h gate client-side, purely so the "Find
 * another match" retry button (shown once today's match reaches a terminal
 * state — watched, or no match found) doesn't invite a write the rules will
 * just reject. The rule is still the actual authority; this is UX only.
 */
function canGenerateAgain(pair) {
  if (!pair?.lastMatchGeneratedAt) return true;
  const last = pair.lastMatchGeneratedAt.toDate
    ? pair.lastMatchGeneratedAt.toDate()
    : new Date(pair.lastMatchGeneratedAt);
  return Date.now() - last.getTime() > 20 * 60 * 60 * 1000;
}

/** Tears down the match section's listeners — leaving it for the friends list, or signing out. */
function stopMatchSection() {
  if (mtUnsubPair) mtUnsubPair();
  if (mtUnsubMatch) mtUnsubMatch();
  mtUnsubPair = null;
  mtUnsubMatch = null;
}

function initMatchSection(pairId, pair) {
  stopMatchSection();

  mt.pairId = pairId;
  mt.pair = pair;
  mt.match = null;
  mt.matchId = null;
  mt.bothOnboardedConfirmed = false;
  mt.promotedMatchId = null;
  showStatus(els.matchStatus, "", false);

  mtUnsubPair = onSnapshot(
    doc(db, "pairs", pairId),
    (snap) => {
      if (!snap.exists()) {
        // This friend was removed (by either side — see removeFriend) while
        // their match section was open. Nothing left to show here.
        backToFriends();
        return;
      }
      mt.pair = snap.data();
      renderMatchSection();
    },
    (err) => showStatus(els.matchStatus, permissionHint(err) || err.message, true)
  );

  const latestMatchQuery = query(
    collection(db, "pairs", pairId, "matches"),
    orderBy("suggestedAt", "desc"),
    limit(1)
  );
  mtUnsubMatch = onSnapshot(
    latestMatchQuery,
    (snap) => {
      const matchDoc = snap.docs[0];
      mt.match = matchDoc ? matchDoc.data() : null;
      mt.matchId = matchDoc ? matchDoc.id : null;
      renderMatchSection();
    },
    (err) => showStatus(els.matchStatus, permissionHint(err) || err.message, true)
  );
}

/** Shared by the first-time "Find tonight's movie" button and the "Find another match" retry. */
async function runFindMatch(button, busyLabel, restLabel) {
  if (!mt.pairId || !mt.pair) return;
  showStatus(els.matchStatus, "", false);
  button.disabled = true;
  button.textContent = busyLabel;
  try {
    await generateTodaysMatch(db, mt.pairId, mt.pair);
    // The matches onSnapshot listener re-renders once the write lands.
  } catch (err) {
    showStatus(els.matchStatus, permissionHint(err) || `Couldn't find a match: ${err.message}`, true);
  } finally {
    button.disabled = false;
    button.textContent = restLabel;
  }
}

els.backToFriendsBtn.addEventListener("click", backToFriends);

els.findMatchBtn.addEventListener("click", () =>
  runFindMatch(els.findMatchBtn, "Finding something…", "Find tonight's movie")
);
els.matchRetryBtn.addEventListener("click", () =>
  runFindMatch(els.matchRetryBtn, "Finding something…", "Find another match")
);

els.matchCommitBtn.addEventListener("click", async () => {
  if (!mt.match || !mt.matchId || !mt.pair) return;
  const uid = auth.currentUser.uid;
  const mySide = mt.pair.userA === uid ? "userA" : "userB";
  showStatus(els.matchStatus, "", false);
  els.matchCommitBtn.disabled = true;
  try {
    await updateDoc(doc(db, "pairs", mt.pairId, "matches", mt.matchId), {
      [`commitStatus.${mySide}`]: true,
    });
  } catch (err) {
    showStatus(els.matchStatus, permissionHint(err) || `Couldn't commit: ${err.message}`, true);
  } finally {
    els.matchCommitBtn.disabled = false;
  }
});

els.markWatchedBtn.addEventListener("click", async () => {
  if (!mt.matchId || !mt.pair || !mt.pairId) return;
  const uid = auth.currentUser.uid;
  showStatus(els.matchStatus, "", false);
  els.markWatchedBtn.disabled = true;
  try {
    await updateDoc(doc(db, "pairs", mt.pairId, "matches", mt.matchId), {
      watchedConfirmedAt: serverTimestamp(),
      watchedConfirmedBy: uid,
    });
    await advancePairStreak(db, mt.pairId, mt.pair);
  } catch (err) {
    showStatus(els.matchStatus, permissionHint(err) || `Couldn't mark it watched: ${err.message}`, true);
  } finally {
    els.markWatchedBtn.disabled = false;
  }
});

let matchRateScoreValue = DEFAULT_SCORE;
els.matchRateDial.addEventListener("input", () => {
  matchRateScoreValue = Number(els.matchRateDial.value);
  els.matchRateScore.textContent = String(matchRateScoreValue);
  els.matchRateScoreLabel.textContent = dialLabel(matchRateScoreValue);
});

els.matchRateBtn.addEventListener("click", async () => {
  if (!mt.match || !mt.pairId) return;
  const uid = auth.currentUser.uid;
  showStatus(els.matchStatus, "", false);
  els.matchRateBtn.disabled = true;
  try {
    await setDoc(doc(db, "pairs", mt.pairId, "ratings", `${uid}_${mt.match.filmId}`), {
      userId: uid,
      filmId: mt.match.filmId,
      score: matchRateScoreValue,
      isInitialOnboarding: false,
      reactionEmoji: null,
      ratedAt: serverTimestamp(),
    });
    els.matchRateDialWrap.hidden = true;
    els.matchRateBtn.hidden = true;
    els.matchRatedDone.hidden = false;
  } catch (err) {
    showStatus(els.matchStatus, permissionHint(err) || `Couldn't save that rating: ${err.message}`, true);
  } finally {
    els.matchRateBtn.disabled = false;
  }
});

/* ============================================================
   FRIENDS — the home screen once a user has at least one pair. Multi-friend
   support (ALI-empty-nest): a user can be a member of any number of /pairs
   documents, tracked in users/{uid}.pairIds; each pair is still a strict
   two-person document exactly as before (see firestore.rules deviation f),
   with its own independent ratings/matches/watchlist/streak — a new friend
   always starts a fresh onboarding deck for THAT pair, same as this app's
   very first pair always has, rather than reusing a taste profile built for
   someone else. That's a deliberate scope call, not an oversight: sharing
   one taste profile across friends would mean moving ratings out of
   pairs/{pairId}/ratings into a per-user collection and reworking
   match-engine.js and firestore.rules around it — a bigger change than this
   pass makes.

   Every friend needing your attention (a match they're waiting on you to
   confirm) is surfaced right here rather than behind a filter — see
   friendStatus() below — so this screen doubles as the "someone found a
   match" inbox the product asked for.
   ============================================================ */

// pairId -> { unsubPair, unsubMatch }
const fsUnsubscribers = new Map();
// pairId -> { pair, match, matchId }
const fsPairs = new Map();

function stopWatchingFriend(pairId) {
  const subs = fsUnsubscribers.get(pairId);
  if (subs) {
    subs.unsubPair();
    subs.unsubMatch();
    fsUnsubscribers.delete(pairId);
  }
  fsPairs.delete(pairId);
}

function stopWatchingAllFriends() {
  for (const pairId of [...fsUnsubscribers.keys()]) stopWatchingFriend(pairId);
}

/**
 * A pair that disappeared from Firestore (removeFriend() below, run by
 * either side) but is still listed in this user's own pairIds — there's no
 * Cloud Function here to clean up the other member's copy the instant it
 * happens, so each side's own client quietly drops the stale id the next
 * time it notices the pair is gone.
 */
async function selfHealRemovedPair(pairId) {
  const uid = auth.currentUser?.uid;
  if (!uid) return;
  const updates = { pairIds: arrayRemove(pairId) };
  if (latestUserData?.activePairId === pairId) updates.activePairId = null;
  try {
    await updateDoc(doc(db, "users", uid), updates);
  } catch {
    // Best-effort — it'll try again on the next load if this failed.
  }
}

/** Keeps this user's set of watched pairs in sync with users/{uid}.pairIds. */
function initFriendsSection() {
  showStatus(els.friendsStatus, "", false);
  const pairIds = latestUserData?.pairIds || [];

  for (const pairId of [...fsUnsubscribers.keys()]) {
    if (!pairIds.includes(pairId)) stopWatchingFriend(pairId);
  }

  for (const pairId of pairIds) {
    if (fsUnsubscribers.has(pairId)) continue;

    const unsubPair = onSnapshot(
      doc(db, "pairs", pairId),
      (snap) => {
        if (!snap.exists()) {
          stopWatchingFriend(pairId);
          selfHealRemovedPair(pairId);
          renderFriendsList();
          return;
        }
        const entry = fsPairs.get(pairId) || {};
        entry.pair = snap.data();
        fsPairs.set(pairId, entry);
        renderFriendsList();
      },
      (err) => showStatus(els.friendsStatus, permissionHint(err) || err.message, true)
    );

    const latestMatchQuery = query(
      collection(db, "pairs", pairId, "matches"),
      orderBy("suggestedAt", "desc"),
      limit(1)
    );
    const unsubMatch = onSnapshot(
      latestMatchQuery,
      (snap) => {
        const matchDoc = snap.docs[0];
        const entry = fsPairs.get(pairId) || {};
        entry.match = matchDoc ? matchDoc.data() : null;
        entry.matchId = matchDoc ? matchDoc.id : null;
        fsPairs.set(pairId, entry);
        renderFriendsList();
      },
      () => {} // A missing match is a normal, common state — nothing to surface here.
    );

    fsUnsubscribers.set(pairId, { unsubPair, unsubMatch });
  }

  renderFriendsList();
}

/**
 * A one-line status per friend, computed from data already loaded for the
 * list (the pair doc + its latest match) rather than an extra per-row
 * aggregate query — "still onboarding" vs. "ready to generate" gets sorted
 * out precisely once you actually open a friend (see enterPair). needsAction
 * is what a match waiting on YOUR commit sets, so those rows can float to
 * the top — the "tell your friend you found something" case the product
 * asked for is exactly this state on their side.
 */
function friendStatus(uid, pair, match) {
  if (!pair.userB) {
    return { label: "Waiting for them to join", needsAction: false };
  }
  if (!match) {
    return { label: "No match yet", needsAction: false };
  }
  if (match.watchedConfirmedAt) {
    return {
      label: pair.streakCount ? `Watched · ${pair.streakCount} day streak` : "Watched",
      needsAction: false,
    };
  }
  if (!match.filmId) {
    return { label: match.noMatchesReason || "No match today", needsAction: false };
  }
  const mySide = pair.userA === uid ? "userA" : "userB";
  const partnerSide = mySide === "userA" ? "userB" : "userA";
  const iCommitted = !!match.commitStatus?.[mySide];
  const partnerCommitted = !!match.commitStatus?.[partnerSide];
  if (iCommitted && partnerCommitted) {
    return { label: "You're both in — watch it!", needsAction: true };
  }
  if (iCommitted) {
    return { label: "Waiting on them to confirm", needsAction: false };
  }
  return { label: "Found a match — waiting on your OK", needsAction: true };
}

function friendDisplayName(uid, pair) {
  const isUserA = pair.userA === uid;
  return (isUserA ? pair.userBName : pair.userAName) || "Your friend";
}

function friendAvatarUrl(uid, pair) {
  const isUserA = pair.userA === uid;
  return (isUserA ? pair.userBAvatarUrl : pair.userAAvatarUrl) || null;
}

/**
 * Safe for both text-node and quoted-attribute contexts — this is used for
 * both (friend-status text, and the name/avatar URL inside style="" and
 * aria-label="" below). The textContent/innerHTML round trip alone only
 * escapes &, < and > (quotes are never special in text-node content), so a
 * crafted display name or avatarUrl containing a literal `"` could still
 * break out of a quoted attribute without the extra replace here.
 */
function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML.replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function renderFriendsList() {
  const uid = auth.currentUser?.uid;
  if (!uid) return;
  const pairIds = latestUserData?.pairIds || [];
  const rows = pairIds
    .map((pairId) => ({ pairId, entry: fsPairs.get(pairId) }))
    .filter((row) => row.entry?.pair);

  els.friendsEmpty.hidden = rows.length > 0;
  if (rows.length === 0) {
    els.friendsList.innerHTML = "";
    return;
  }

  const decorated = rows.map(({ pairId, entry }) => ({
    pairId,
    pair: entry.pair,
    status: friendStatus(uid, entry.pair, entry.match),
  }));
  decorated.sort((a, b) => Number(b.status.needsAction) - Number(a.status.needsAction));

  els.friendsList.innerHTML = decorated
    .map(({ pairId, pair, status }) => {
      const name = escapeHtml(friendDisplayName(uid, pair));
      const avatarUrl = friendAvatarUrl(uid, pair);
      const initial = escapeHtml((friendDisplayName(uid, pair) || "?").trim().charAt(0).toUpperCase() || "?");
      const avatarStyle = avatarUrl ? ` style="background-image:url(${escapeHtml(avatarUrl)})"` : "";
      return `
        <div class="friend-row" data-pair-id="${pairId}">
          <button type="button" class="friend-open" data-open-pair="${pairId}">
            <div class="avatar friend-avatar"${avatarStyle}>${avatarUrl ? "" : initial}</div>
            <div class="friend-info">
              <div class="friend-name">${name}</div>
              <div class="friend-status${status.needsAction ? " is-action" : ""}">${escapeHtml(status.label)}</div>
            </div>
          </button>
          <button type="button" class="friend-remove" data-remove-pair="${pairId}" aria-label="Remove ${name}">✕</button>
        </div>`;
    })
    .join("");
}

els.friendsList.addEventListener("click", async (event) => {
  const openBtn = event.target.closest("[data-open-pair]");
  if (openBtn) {
    enterPair(openBtn.dataset.openPair);
    return;
  }
  const removeBtn = event.target.closest("[data-remove-pair]");
  if (removeBtn) {
    const pairId = removeBtn.dataset.removePair;
    const entry = fsPairs.get(pairId);
    const name = entry?.pair ? friendDisplayName(auth.currentUser.uid, entry.pair) : "this friend";
    const confirmed = window.confirm(
      `Remove ${name} and delete everything you've rated and matched together? This can't be undone.`
    );
    if (confirmed) removeFriend(pairId);
  }
});

els.addFriendBtn.addEventListener("click", goToPairing);

/** Deletes every doc in a subcollection. Fine at this app's per-pair scale (well under Firestore's 500-op batch limit). */
async function deleteSubcollection(colRef) {
  const snap = await getDocs(colRef);
  if (snap.empty) return;
  const batch = writeBatch(db);
  snap.docs.forEach((docSnap) => batch.delete(docSnap.ref));
  await batch.commit();
}

/**
 * Removing a friend deletes the pair and everything scoped under it —
 * ratings, matches, watchlist — then drops it from this user's own
 * pairIds. The other member's own pairIds still lists it until their next
 * load notices the pair is gone (selfHealRemovedPair above); there's no
 * Cloud Function here to tell them right away.
 */
async function removeFriend(pairId) {
  showStatus(els.friendsStatus, "Removing…", false);
  try {
    await deleteSubcollection(collection(db, "pairs", pairId, "ratings"));
    await deleteSubcollection(collection(db, "pairs", pairId, "matches"));
    await deleteSubcollection(collection(db, "pairs", pairId, "watchlist"));
    await deleteDoc(doc(db, "pairs", pairId));
  } catch (err) {
    showStatus(els.friendsStatus, permissionHint(err) || `Couldn't remove: ${err.message}`, true);
    return;
  }
  stopWatchingFriend(pairId);
  const updates = { pairIds: arrayRemove(pairId) };
  if (latestUserData?.activePairId === pairId) updates.activePairId = null;
  try {
    await updateDoc(doc(db, "users", auth.currentUser.uid), updates);
  } catch (err) {
    showStatus(els.friendsStatus, permissionHint(err) || err.message, true);
    return;
  }
  showStatus(els.friendsStatus, "Removed.", false);
}
