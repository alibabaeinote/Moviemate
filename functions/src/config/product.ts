/**
 * Tunable product rules that are not part of the scoring engine.
 *
 * Same principle as config/algorithm.ts: these are assumptions, so they live in
 * one place rather than scattered through the handlers.
 */
export interface ProductConfig {
  /**
   * How long a pair can go between completed watches before the streak resets.
   *
   * ⚠️ PRODUCT DECISION NEEDED. The schema doc defines streakCount as
   * "روزهای متوالی rating مشترک" — consecutive days of shared rating — but the
   * PRD's own target user watches together "1-2 times a week" (§2). A strict
   * consecutive-day streak would therefore reset for essentially every real
   * user, turning the Us screen's headline number into a permanent 1.
   *
   * The default here is 7 days: the streak survives as long as the pair keeps
   * up their stated habit, and breaks when they genuinely lapse. That is a
   * deliberate reinterpretation of the doc, not an implementation of it — see
   * README §"Deviations". Set to 1 for the literal consecutive-day reading.
   */
  readonly streakGraceDays: number;

  /**
   * A watch only counts toward the streak once per local day, so watching two
   * films in one evening does not inflate it.
   */
  readonly maxStreakIncrementsPerDay: number;

  /** Films offered in the onboarding rating deck (docs: "12-15 films"). */
  readonly onboardingDeckSize: number;

  /**
   * Minimum TMDB vote count for a film to enter any pool. Filters out obscure
   * entries whose single 10/10 vote would otherwise ride the quality bonus
   * straight to the top.
   */
  readonly minVoteCount: number;
}

export const PRODUCT_CONFIG: ProductConfig = {
  streakGraceDays: 7,
  maxStreakIncrementsPerDay: 1,
  onboardingDeckSize: 15,
  minVoteCount: 200,
};
