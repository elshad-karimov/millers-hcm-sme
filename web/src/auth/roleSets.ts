// Single source of truth for HCM frontend role tuples (M91).
//
// Mirrors the backend `az.millers.hcm.security.SecurityRoles` Java class.
// When the backend grants a new role to a capability, edit ONE tuple here
// and every page that uses it updates automatically. Inline `hasRole('A',
// 'B', 'C')` calls have the same DRY problem they had on the backend —
// 56 call sites in the codebase, ~7 distinct repeated tuples.
//
// Use with the AuthContext `hasRole` API:
//
//     const { hasRole } = useAuth()
//     const canSubmit = hasRole(...RoleSets.HR_TEAM_WRITE)
//
// The spread (`...RoleSets.X`) is required because `hasRole` takes varargs.

/** Bare role names — keep in sync with backend SecurityRoles.R_* and Keycloak realm. */
export const Roles = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  HR_ADMIN: 'HR_ADMIN',
  HR_SPECIALIST: 'HR_SPECIALIST',
  AUDITOR: 'AUDITOR',
  RECRUITER: 'RECRUITER',
  DEPARTMENT_MANAGER: 'DEPARTMENT_MANAGER',
  EMPLOYEE: 'EMPLOYEE',
  OCCUPATIONAL_HEALTH: 'OCCUPATIONAL_HEALTH',
  PAYROLL_SPECIALIST: 'PAYROLL_SPECIALIST',
  FINANCE_USER: 'FINANCE_USER',
} as const

/**
 * Capability-named role tuples for `hasRole(...)`. Names mirror the
 * backend SecurityRoles constants where the capability is the same.
 *
 * Adding a new role to a capability: edit one tuple here. Adding a new
 * capability: add one entry. Both ripple to every consumer immediately.
 */
export const RoleSets = {
  // ── HR / employee directory ─────────────────────────────────────────────

  /** HR-only read — directory, leave admin, sensitive HR docs. */
  HR_READ: [
    Roles.SYSTEM_ADMIN, Roles.HR_ADMIN, Roles.HR_SPECIALIST, Roles.AUDITOR,
  ] as const,

  /** HR read + direct-line department managers — profile, lifecycle, reports. */
  HR_PLUS_MANAGERS_READ: [
    Roles.SYSTEM_ADMIN, Roles.HR_ADMIN, Roles.HR_SPECIALIST,
    Roles.AUDITOR, Roles.DEPARTMENT_MANAGER,
  ] as const,

  /** HR write — most "edit employee X" actions. */
  HR_WRITE: [
    Roles.HR_ADMIN, Roles.HR_SPECIALIST, Roles.SYSTEM_ADMIN,
  ] as const,

  /** HR team write — narrower; no SYSTEM_ADMIN. Used for team-scoped UI. */
  HR_TEAM_WRITE: [
    Roles.HR_ADMIN, Roles.HR_SPECIALIST,
  ] as const,

  /** HR admin-only write — leave-group master, holiday seed, bank accounts. */
  HR_ADMIN_WRITE: [
    Roles.HR_ADMIN, Roles.SYSTEM_ADMIN,
  ] as const,

  /** HR write + department managers — e.g. probation review submission. */
  HR_PLUS_MANAGERS_WRITE: [
    Roles.HR_ADMIN, Roles.HR_SPECIALIST, Roles.SYSTEM_ADMIN, Roles.DEPARTMENT_MANAGER,
  ] as const,

  // ── Recruitment ─────────────────────────────────────────────────────────

  /** Recruitment team write — manage candidates, kits, talent pool. */
  RECRUITMENT_TEAM: [
    Roles.HR_ADMIN, Roles.HR_SPECIALIST, Roles.RECRUITER,
  ] as const,

  // ── Payroll ─────────────────────────────────────────────────────────────

  /** Payroll calculation / approval write. */
  PAYROLL_WRITE: [
    Roles.HR_ADMIN, Roles.SYSTEM_ADMIN, Roles.PAYROLL_SPECIALIST,
  ] as const,

  // ── Compensation ────────────────────────────────────────────────────────

  /** Compensation profile read — HR can view compensation profiles. */
  COMPENSATION_READ: [
    Roles.SYSTEM_ADMIN, Roles.HR_ADMIN, Roles.HR_SPECIALIST, Roles.AUDITOR,
  ] as const,

  /** Compensation config / band / reason write — HR admin only. */
  COMPENSATION_WRITE: [
    Roles.HR_ADMIN, Roles.SYSTEM_ADMIN,
  ] as const,

  // ── Single-role gates (kept for readability) ────────────────────────────

  /** SYSTEM_ADMIN only — destructive admin endpoints. */
  SYS_ADMIN_ONLY: [Roles.SYSTEM_ADMIN] as const,

  /** Department manager only — used by the manager-self-service surface. */
  MANAGER_ONLY: [Roles.DEPARTMENT_MANAGER] as const,

  // ── Reporting dashboard tabs ────────────────────────────────────────────

  /** Manager dashboard tab visibility — HR can see it for support. */
  MANAGER_TAB: [
    Roles.SYSTEM_ADMIN, Roles.HR_ADMIN, Roles.HR_SPECIALIST, Roles.DEPARTMENT_MANAGER,
  ] as const,

  /** Executive tab — adds FINANCE_USER but NOT department managers. */
  EXECUTIVE_TAB: [
    Roles.SYSTEM_ADMIN, Roles.HR_ADMIN, Roles.FINANCE_USER, Roles.AUDITOR,
  ] as const,
} as const
