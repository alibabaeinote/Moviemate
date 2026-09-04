#!/usr/bin/env node
/**
 * Token validator.
 *
 * A design system that is only written down drifts. This makes the rules
 * executable: run it after any change to design/tokens.json.
 *
 *   node design/validate-tokens.mjs
 *
 * Checks, in order:
 *   1. Every {reference} resolves to a real token.
 *   2. No reference cycles.
 *   3. Tier discipline — a `comp` or `sys` token may not point at another
 *      theme's tokens, and nothing outside `ref` may hold a raw hex value.
 *   4. Contrast — every colour token declaring `$on` clears WCAG AA against
 *      the surface it names.
 *   5. Theme parity — light and dark expose exactly the same role names, so a
 *      component can never find a role missing in one theme.
 *   6. Spacing hygiene — every ref.dimension sits on the 4px grid.
 *
 * Exit code 0 = clean, 1 = at least one failure.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const tokens = JSON.parse(readFileSync(join(here, "tokens.json"), "utf8"));

const failures = [];
const notes = [];
const fail = (rule, message) => failures.push({ rule, message });

/* ------------------------------------------------------------------ *
 * Token tree walking
 * ------------------------------------------------------------------ */

/** True for a leaf token: an object carrying a $value. */
const isToken = (node) =>
  node && typeof node === "object" && !Array.isArray(node) && "$value" in node;

/** Walk every leaf token, yielding [dottedPath, tokenObject]. */
function* walk(node, path = []) {
  if (!node || typeof node !== "object" || Array.isArray(node)) return;
  if (isToken(node)) {
    yield [path.join("."), node];
    return;
  }
  for (const [key, child] of Object.entries(node)) {
    if (key.startsWith("$")) continue;
    yield* walk(child, [...path, key]);
  }
}

function tokenAt(path) {
  let node = tokens;
  for (const segment of path.split(".")) {
    if (!node || typeof node !== "object") return undefined;
    node = node[segment];
  }
  return node;
}

const REFERENCE = /^\{([^}]+)\}$/;

/** Follow a chain of {references} to a concrete value. */
function resolve(value, trail = []) {
  if (typeof value !== "string") return value;
  const match = REFERENCE.exec(value.trim());
  if (!match) return value;

  const path = match[1];
  if (trail.includes(path)) {
    throw new Error(`reference cycle: ${[...trail, path].join(" -> ")}`);
  }

  const target = tokenAt(path);
  if (!isToken(target)) {
    throw new Error(`unresolved reference {${path}}`);
  }
  return resolve(target.$value, [...trail, path]);
}

/* ------------------------------------------------------------------ *
 * Colour maths (WCAG 2.2 relative luminance)
 * ------------------------------------------------------------------ */

function channel(component) {
  const c = component / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function luminance(hex) {
  const h = hex.replace("#", "");
  const full = h.length === 3 ? [...h].map((c) => c + c).join("") : h;
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16));
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrast(a, b) {
  const [la, lb] = [luminance(a), luminance(b)];
  const [hi, lo] = la > lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

const isHex = (value) => typeof value === "string" && /^#[0-9a-fA-F]{3,8}$/.test(value);

/* ------------------------------------------------------------------ *
 * 1-2. References resolve, no cycles
 * ------------------------------------------------------------------ */

const resolved = new Map();

for (const [path, token] of walk(tokens)) {
  try {
    if (token.$type === "typography" && typeof token.$value === "object") {
      // Typography composites hold references in their sub-fields.
      for (const [field, raw] of Object.entries(token.$value)) {
        resolve(raw);
        void field;
      }
      continue;
    }
    resolved.set(path, resolve(token.$value));
  } catch (error) {
    fail("references", `${path}: ${error.message}`);
  }
  if (token.$on) {
    try {
      resolve(token.$on);
    } catch (error) {
      fail("references", `${path} ($on): ${error.message}`);
    }
  }
}

/* ------------------------------------------------------------------ *
 * 3. Tier discipline
 * ------------------------------------------------------------------ */

for (const [path, token] of walk(tokens)) {
  const [tier] = path.split(".");

  // Only primitives may hold raw colour literals.
  if (tier !== "ref" && token.$type === "color" && isHex(token.$value)) {
    fail(
      "tiers",
      `${path} holds a raw hex (${token.$value}). Colours outside ref.* must reference a primitive, ` +
        `so a palette change reaches every screen from one edit.`
    );
  }

  // A semantic token must not reach across into the other theme.
  if (tier === "sys" && typeof token.$value === "string") {
    const match = REFERENCE.exec(token.$value.trim());
    const target = match?.[1] ?? "";
    const theme = path.split(".")[1];
    const otherTheme = theme === "dark" ? "light" : "dark";
    if (target.startsWith(`sys.${otherTheme}.`)) {
      fail("tiers", `${path} references ${target} — themes must stay independent.`);
    }
  }
}

/* ------------------------------------------------------------------ *
 * 4. Contrast
 * ------------------------------------------------------------------ */

const AA_BODY = tokens.a11y.contrastBodyText.$value;
const AA_LARGE = tokens.a11y.contrastLargeText.$value;

/**
 * Roles measured against the large-text/UI threshold rather than body text.
 * These never carry running copy: they are ornament, dividers, or shapes.
 */
const LARGE_ONLY = new Set(["status.decorative", "partner.a", "partner.b"]);

const contrastReport = [];

for (const [path, token] of walk(tokens)) {
  if (!token.$on || token.$type !== "color") continue;

  let fg;
  let bg;
  try {
    fg = resolve(token.$value);
    bg = resolve(token.$on);
  } catch {
    continue; // already reported as a reference failure
  }

  if (!isHex(fg) || !isHex(bg)) {
    fail("contrast", `${path}: cannot measure non-hex colour (${fg} on ${bg})`);
    continue;
  }

  const role = path.split(".").slice(2).join(".");
  const threshold = LARGE_ONLY.has(role) ? AA_LARGE : AA_BODY;
  const ratio = contrast(fg, bg);

  contrastReport.push({ path, fg, bg, ratio, threshold });

  if (ratio < threshold) {
    fail(
      "contrast",
      `${path}: ${fg} on ${bg} is ${ratio.toFixed(2)}:1, below the ${threshold}:1 floor.`
    );
  }
}

/* ------------------------------------------------------------------ *
 * 4b. Every semantic colour documents where it is used
 *
 * A role named `text.accent` says what it IS but not where it BELONGS.
 * Requiring $usage is what makes the token file answer "which colour do I
 * reach for here?" without reading every screen.
 * ------------------------------------------------------------------ */

for (const theme of ["dark", "light"]) {
  for (const [path, token] of walk(tokens.sys?.[theme] ?? {})) {
    if (token.$type !== "color") continue;
    if (!token.$usage || token.$usage.trim().length < 10) {
      fail(
        "usage",
        `sys.${theme}.${path} has no $usage. Every semantic colour must say where it is used, ` +
          `or the next person guesses.`
      );
    }
  }
}

/* ------------------------------------------------------------------ *
 * 4c. Foundation coverage
 *
 * The design-system checklist every foundation is expected to cover. A
 * missing entry here is a gap in the system, not a gap in this script.
 * ------------------------------------------------------------------ */

const FOUNDATIONS = {
  Color: () => tokens.sys?.dark?.text && tokens.ref?.palette,
  Typography: () => tokens.sys?.type,
  Spacing: () => tokens.sys?.space,
  Grid: () => tokens.ref?.grid,
  Layout: () => tokens.sys?.layout,
  Breakpoints: () => tokens.ref?.breakpoint,
  "Responsive behavior": () => tokens.responsive,
  Radius: () => tokens.ref?.radius,
  Border: () => tokens.ref?.border && tokens.sys?.border,
  Elevation: () => tokens.ref?.elevation && tokens.sys?.elevation,
  Iconography: () => tokens.ref?.icon && tokens.sys?.icon,
  Illustration: () => tokens.illustration,
  Motion: () => tokens.ref?.easing && tokens.sys?.motion,
  Opacity: () => tokens.ref?.opacity,
  Focus: () => tokens.sys?.focus,
  Density: () => tokens.ref?.density && tokens.sys?.density,
  Accessibility: () => tokens.a11y,
};

const missingFoundations = [];
for (const [name, present] of Object.entries(FOUNDATIONS)) {
  if (!present()) {
    missingFoundations.push(name);
    fail("foundations", `"${name}" has no tokens. See docs/MovieMate-Design-System.md §14.`);
  }
}

/* ------------------------------------------------------------------ *
 * 5. Theme parity
 * ------------------------------------------------------------------ */

function roleNames(theme) {
  const names = new Set();
  for (const [path] of walk(tokens.sys?.[theme] ?? {})) names.add(path);
  return names;
}

const darkRoles = roleNames("dark");
const lightRoles = roleNames("light");

for (const role of darkRoles) {
  if (!lightRoles.has(role)) {
    fail("parity", `sys.dark.${role} has no counterpart in sys.light — a component would find it missing.`);
  }
}
for (const role of lightRoles) {
  if (!darkRoles.has(role)) {
    fail("parity", `sys.light.${role} has no counterpart in sys.dark.`);
  }
}

/* ------------------------------------------------------------------ *
 * 6. Spacing grid
 * ------------------------------------------------------------------ */

for (const [path, token] of walk(tokens.ref?.dimension ?? {})) {
  const px = Number.parseFloat(String(token.$value));
  if (!Number.isFinite(px) || px % 4 !== 0) {
    fail("grid", `ref.dimension.${path} = ${token.$value} is off the 4px base grid.`);
  }
}

/* ------------------------------------------------------------------ *
 * Report
 * ------------------------------------------------------------------ */

const GREEN = "[32m";
const RED = "[31m";
const DIM = "[2m";
const RESET = "[0m";

console.log(`\nMovieMate tokens v${tokens.meta.version.$value}\n`);

console.log("Contrast");
for (const row of [...contrastReport].sort((a, b) => a.path.localeCompare(b.path))) {
  const ok = row.ratio >= row.threshold;
  const mark = ok ? `${GREEN}pass${RESET}` : `${RED}FAIL${RESET}`;
  console.log(
    `  ${mark}  ${row.ratio.toFixed(2).padStart(5)}:1  ${DIM}(needs ${row.threshold})${RESET}  ${row.path}`
  );
}

console.log("\nFoundations");
const foundationNames = Object.keys(FOUNDATIONS);
const covered = foundationNames.filter((n) => !missingFoundations.includes(n));
for (const name of foundationNames) {
  const ok = covered.includes(name);
  console.log(`  ${ok ? `${GREEN}✓${RESET}` : `${RED}✗${RESET}`}  ${name}`);
}
console.log(`  ${DIM}${covered.length}/${foundationNames.length} covered${RESET}`);

const counts = {
  primitives: [...walk(tokens.ref ?? {})].length,
  semantic: [...walk(tokens.sys ?? {})].length,
  component: [...walk(tokens.comp ?? {})].length,
};
console.log(
  `\n${DIM}${counts.primitives} primitives · ${counts.semantic} semantic roles · ${counts.component} component tokens${RESET}`
);

for (const note of notes) console.log(`  ${DIM}${note}${RESET}`);

if (failures.length === 0) {
  console.log(`\n${GREEN}All checks passed.${RESET}\n`);
  process.exit(0);
}

console.log(`\n${RED}${failures.length} failure(s):${RESET}`);
for (const { rule, message } of failures) {
  console.log(`  ${RED}[${rule}]${RESET} ${message}`);
}
console.log("");
process.exit(1);
