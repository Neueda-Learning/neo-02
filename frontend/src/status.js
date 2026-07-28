// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "VERIFIED", "CLEAR" and "SIGNED" too.
import { TONES, toneMapper } from './design-system';

export const statusTone = toneMapper({
  ACCEPTED: TONES.POSITIVE,
  APPROVED: TONES.POSITIVE,
  REJECTED: TONES.NEGATIVE,
  REFERRED: TONES.WARNING,
  IN_PROGRESS: TONES.INFO,
});

/**
 * UC00 plus the three policy outcomes used by later use cases.
 */
export const STATUSES = ['IN_PROGRESS', 'APPROVED', 'REJECTED', 'REFERRED'];

export function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString() : '—';
}
