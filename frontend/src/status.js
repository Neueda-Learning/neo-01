// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "VERIFIED", "CLEAR" and "SIGNED" too.
import { TONES, toneMapper } from './design-system';

export const statusTone = toneMapper({
  ACCEPTED: TONES.POSITIVE,
  REJECTED: TONES.NEGATIVE,
  REFERRED: TONES.WARNING,
  // Kept although the skeleton never stores it: a row is written only once the work is done. If
  // you start recording an application before you have decided about it, this is already coloured.
  'in-progress': TONES.INFO,
});

/**
 * Statuses exposed as board filters.
 */
export const STATUSES = ['PASSED', 'FAILED', 'REVIEW'];

export function time(iso) {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  return `${y}-${m}-${d} ${hh}:${mm}`;
}
