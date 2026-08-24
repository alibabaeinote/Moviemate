/**
 * Copy for the seven notification types.
 *
 * App language is English (PRD §2). Emoji appear only here and in the optional
 * post-watch quick reaction — never as UI iconography (Design System §6).
 */
export const messages = {
  dailyMatch: () => ({
    title: "Today's match is ready 🎬",
    body: "One pick, chosen for both of you.",
  }),

  partnerJoined: (partnerName: string) => ({
    title: `${partnerName} joined!`,
    body: "Rate your films to get your first match.",
  }),

  partnerRated: (partnerName: string) => ({
    title: `${partnerName} finished rating`,
    body: "Your turn — a few films and you're both set.",
  }),

  partnerCommitted: (partnerName: string, filmTitle: string) => ({
    title: `${partnerName} wants to watch ${filmTitle}`,
    body: "Say you're in and we'll set a time.",
  }),

  bothConfirmed: (filmTitle: string) => ({
    title: "You're both in!",
    body: `${filmTitle} it is. We'll remind you tonight.`,
  }),

  scheduledReminder: (filmTitle: string) => ({
    title: `Ready to watch ${filmTitle}? 🍿`,
    body: "Starting in 15 minutes.",
  }),

  watchlistActivity: (partnerName: string, filmTitle: string) => ({
    title: `${partnerName} added ${filmTitle}`,
    body: "Tap to say you're in too.",
  }),
} as const;
