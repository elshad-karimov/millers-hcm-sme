#!/bin/bash
# HCM Quality Gate — Stop hook (CLAUDE.md BOOTSTRAP step 5).
#
# Purpose: enforce the non-negotiable GLOBAL RULES with FAST, static checks on
# every turn. The heavy gates live in CI and are run explicitly, NOT here, so
# this script stays sub-second and never slows the loop:
#   * full unit suite ...... `mvn test`               (GitHub Actions ci.yml, every push)
#   * API invariants ....... `python3 scripts/uat_smoke.py`  (local / pre-merge, needs the stack)
#   * payroll math ......... StatutoryCalculatorTest  (part of `mvn test`)
#
# This hook BLOCKS (exit 2) on ONE unambiguous rule only, to avoid false-positive
# friction: an added `DELETE FROM <payroll table>` — GLOBAL RULE 12, payroll
# records are never physically deleted (corrections use reversal/adjustment).
# Everything else is a non-blocking advisory reminder.
#
# Exit codes (Claude Code Stop hook): 2 = block + feed stderr back; 0 = allow.

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ -z "$ROOT" ] && exit 0            # not a git repo → nothing to gate
cd "$ROOT" || exit 0

# Added (+) lines from uncommitted changes AND the most recent commit
# (so a just-committed-but-unpushed dangerous change is still caught).
# Scope the payroll-delete scan to SQL migrations and Java sources only — a
# real hard delete lives there, and this avoids matching this script's own
# text, docs, or the smoke suite that legitimately mention "payroll".
CODE_CHANGES="$( { git diff HEAD -- '*.sql' '*.java'; git diff 'HEAD~1' HEAD -- '*.sql' '*.java'; } 2>/dev/null \
              | grep -E '^\+' | grep -vE '^\+\+\+' )"

# ── (1) BLOCK — hard delete of payroll data (GLOBAL RULE 12) ─────────────────
# Matches real SQL `DELETE FROM payroll…` / `DELETE FROM `payroll`…` and the
# partitioned result tables / payslip. Reversal & adjustment are the only paths.
if printf '%s\n' "$CODE_CHANGES" \
     | grep -iqE 'delete[[:space:]]+from[[:space:]]+[`"'"'"']?(payroll[._]|payroll\b|payslip|payroll_result)'; then
  echo "GATE BLOCKED: hard DELETE of payroll data detected in the diff." >&2
  echo "  GLOBAL RULE 12 — payroll records are never physically deleted;" >&2
  echo "  corrections use adjustment/reversal logic. Remove the delete or" >&2
  echo "  replace it with a reversal before finishing." >&2
  exit 2
fi

# ── (2) Advisory — remind to run the gate when code / migrations changed ─────
# Only fires on UNCOMMITTED work (committed+pushed work is presumed gated), so
# it stays quiet once a change is landed.
UNCOMMITTED="$(git diff HEAD --name-only 2>/dev/null)"
if printf '%s\n' "$UNCOMMITTED" | grep -qE '\.(java|sql)$'; then
  # Touched tenant/permission-sensitive areas? Nudge the specific invariants.
  if printf '%s\n' "$CHANGES" | grep -iqE '(employee|payroll|attendance|salary|compensation|tenant)'; then
    echo "Quality-gate reminder: payroll/employee/tenant code changed — before merging run" >&2
    echo "  mvn test   +   python3 scripts/uat_smoke.py   (confirm tenant_id filtering, hierarchy scoping, salary masking)." >&2
  else
    echo "Quality-gate reminder: source changed — run 'mvn test' before merging." >&2
  fi
fi

exit 0
