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
  collection,
  doc,
  getDoc,
  limit,
  orderBy,
  query,
  setDoc,
  updateDoc,
  onSnapshot,
  serverTimestamp,
  writeBatch,
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
  joinBtn: document.getElementById("joinBtn"),
  joinCodeInput: document.getElementById("joinCodeInput"),
  inviteCodeDisplay: document.getElementById("inviteCodeDisplay"),
  copyInviteBtn: document.getElementById("copyInviteBtn"),
  continueFromInviteBtn: document.getElementById("continueFromInviteBtn"),
  continueFromDoneBtn: document.getElementById("continueFromDoneBtn"),
  partnerText: document.getElementById("partnerText"),
  pairingStatus: document.getElementById("pairingStatus"),

  match: document.getElementById("match"),
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
  els.match.hidden = name !== "match";
}

/**
 * Whether THIS user is done onboarding, checked live against their actual
 * rating history rather than trusting users.onboardingComplete — that field
 * is only ever set by the (Blaze-only) onRatingComplete trigger, so on the
 * no-Blaze path it would stay false forever and strand a paired user back in
 * onboarding on every visit. See web/match.js and the README's "no-Blaze"
 * section.
 */
async function decideInitialSection(userData, pairId) {
  if (pairId) {
    const myCount = await onboardingRatingCount(db, pairId, auth.currentUser.uid);
    return myCount >= RATING_TARGET ? "match" : "onboarding";
  }
  if (draftCount() >= RATING_TARGET) return "pairing";
  return "onboarding";
}

async function maybeDecideInitialRoute() {
  if (routeDecided || !latestUserData) return;
  const pairId = latestUserData.pairId;
  routeDecided = true;
  const section = await decideInitialSection(latestUserData, pairId);
  // The profile card (name + sign out) is only the very first, momentary
  // step — it's for the one-time "here's how I want to be known to my
  // partner" edit right after signing in, not a permanent header sitting
  // above onboarding/pairing/match. Hide it the instant routing lands
  // somewhere; editing the name again is a "sign out and back in" affair
  // for now, same as this client not having a separate profile screen yet.
  els.signedIn.hidden = true;
  showSection(section);
  if (section === "onboarding") initOnboarding();
  if (section === "match") goToMatch();
}

/** Called by the onboarding/pairing screens themselves once they're done. */
function goToPairing() {
  showSection("pairing");
  showPairingView("choice");
}

async function goToMatch() {
  showSection("match");
  const pairId = latestUserData?.pairId;
  if (!pairId) return;
  const snap = await getDoc(doc(db, "pairs", pairId));
  if (snap.exists()) initMatchSection(pairId, snap.data());
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
    if (mtUnsubPair) mtUnsubPair();
    if (mtUnsubMatch) mtUnsubMatch();
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
    ob.recorded = latestUserData?.pairId
      ? await onboardingRatingCount(db, latestUserData.pairId, auth.currentUser.uid)
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
 */
async function createPairDirect(uid, timezone) {
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

    await updateDoc(doc(db, "users", uid), { pairId: pairRef.id });
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

  await updateDoc(doc(db, "users", uid), { pairId });
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
    const { pairId, inviteCode } = await createPairDirect(uid, timezone);
    pendingInviteCode = inviteCode;
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
    const user = auth.currentUser;
    const { pairId } = await joinPairDirect(
      user.uid,
      inviteCode,
      latestUserData?.name || user.displayName || "",
      user.photoURL || null
    );
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

function initMatchSection(pairId, pair) {
  if (mtUnsubPair) mtUnsubPair();
  if (mtUnsubMatch) mtUnsubMatch();

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
      if (!snap.exists()) return;
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
