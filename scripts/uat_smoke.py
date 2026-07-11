#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Millers HCM — Automated API invariant / smoke suite.

WHAT THIS IS
------------
A black-box confidentiality regression net. It logs in to the RUNNING backend
as each UAT persona (via Keycloak Resource-Owner-Password grant) and asserts the
HCM security invariants — self-service scoping, manager-hierarchy scoping, salary
confidentiality, and "no payroll execution by low-privilege roles" — over real
HTTP against real controllers. It reads only the app's own config (the Keycloak
realm file) to wire itself; it never touches or mutates application code, and it
never executes a real payroll run.

It replaces manual, role-by-role UAT clicking with one command that exits NON-ZERO
on any invariant breach, so it can guard every change in CI.

INVARIANTS ASSERTED  (endpoint each maps to — all controller-verified)
-----------------------------------------------------------------------
 1. Auth & self-identity — every persona gets a token and GET /api/self/employee
    returns 200 with THEIR OWN employee_no (never someone else's).
 2. Self-service is self-scoped (IDOR) — as `employee`:
      * GET /api/self/payslips returns only Aliya's rows (all employeeId == self)
      * GET /api/employees/{managerId}                       -> denied (no HR role)
      * GET /api/payroll/compensation?employeeId={managerId} -> denied
      * GET /api/compensation/employees/{managerId}/profile  -> denied
 3. Manager hierarchy scoping — as `manager`:
      * GET /api/self/team == exactly {EMP-00001, EMP-00013, EMP-00019}
      * team must NOT include non-reports (EMP-00004, EMP-00012)
      * GET /api/employees/{nonReportId}  -> 404 (ABAC hides existence)
      * GET /api/employees/{reportId}     -> 200 (positive control: sees own report)
 4. Salary confidentiality / masking:
      * admin  GET /api/payroll/compensation?employeeId={EMP-00001} -> 200 + salary  (positive control)
      * employee / manager GET another person's compensation        -> denied
      * manager GET /api/self/team/compensation -> 403, OR 200 but only team rows (never company-wide)
      * manager's in-scope employee detail (GET /api/employees/{reportId}) carries NO salary field
 5. No payroll execution by non-payroll roles:
      * employee / manager POST /api/payroll/runs                 -> denied (401/403)
      * employee / manager POST /api/payroll/runs/{id}/calculate  -> denied (401/403)
    (A real run is NEVER POSTed as anyone — only the DENIAL is asserted.)
 6. Tenant isolation (best-effort, single tenant) — every fetched record that
    carries a tenant marker must read 'default'. Response DTOs deliberately do
    not surface tenant_id, so with one seeded tenant this is typically SKIPPED
    with a note: true cross-tenant isolation needs a 2nd tenant seeded.
 7. Positive controls (proves the suite isn't just asserting universal denial):
      * admin GET /api/employees -> 200, non-empty, contains EMP-00001
      * admin salary IS visible (see 4)
      * hrspec GET /api/employees is org-unit-scoped (strictly fewer rows than admin)

HOW TO RUN
----------
    python3 scripts/uat_smoke.py

Prerequisites: the backend (:8082), Keycloak (:8080) and the Vite dev proxy
(:5180) must all be UP. Tokens are minted from the :5180 proxy so the JWT `iss`
claim matches the backend's configured issuer — a token from :8080 directly is
rejected (401).

No secrets or URLs are passed on the command line. Persona passwords are read at
runtime from keycloak/realm-millers-hcm.json (the single source of truth).

Exit code = number of FAILing checks (0 = all invariants hold). A hard
environment blocker (backend/Keycloak unreachable) exits 2.

ENV OVERRIDES (all optional)
    HCM_OIDC_ISSUER   default http://localhost:5180/realms/millers-hcm
    HCM_API_BASE      default http://localhost:8082
    HCM_REALM_FILE    default <repo>/keycloak/realm-millers-hcm.json
    HCM_KC_CLIENT_ID  default hcm-web
    HCM_PW_<USER>     per-persona password override (e.g. HCM_PW_ADMIN), else realm file

Standard library only. No pip installs, no `requests`.
"""

import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

# --------------------------------------------------------------------------- #
# Config (env-overridable)
# --------------------------------------------------------------------------- #
ISSUER = os.environ.get("HCM_OIDC_ISSUER", "http://localhost:5180/realms/millers-hcm")
TOKEN_URL = ISSUER.rstrip("/") + "/protocol/openid-connect/token"
API_BASE = os.environ.get("HCM_API_BASE", "http://localhost:8082").rstrip("/")
CLIENT_ID = os.environ.get("HCM_KC_CLIENT_ID", "hcm-web")

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REALM_FILE = os.environ.get(
    "HCM_REALM_FILE", os.path.join(_REPO_ROOT, "keycloak", "realm-millers-hcm.json")
)

HTTP_TIMEOUT = int(os.environ.get("HCM_HTTP_TIMEOUT", "20"))

# Codes that count as a legitimate "you may not do / see this".
DENY_CODES = {401, 403, 404}
AUTHZ_DENY_CODES = {401, 403}  # for state-changing POSTs the gate is auth, not 404

# Personas -> the employee_no each one MUST resolve to (from seed-uat.sql / UAT_LOGINS.md)
PERSONAS = {
    "admin":    "EMP-00000",
    "hrspec":   "EMP-00004",
    "manager":  "EMP-00002",
    "employee": "EMP-00001",
    "mfauser":  "EMP-00012",  # requires TOTP; direct-grant expected to fail -> SKIP
}
# Personas allowed to SKIP (not FAIL) if their token cannot be minted.
OPTIONAL_PERSONAS = {"mfauser"}

# Manager's exact direct-report set and a couple of known non-reports.
MANAGER_REPORTS = {"EMP-00001", "EMP-00013", "EMP-00019"}
MANAGER_NON_REPORTS = {"EMP-00004", "EMP-00012"}

# --------------------------------------------------------------------------- #
# Result collection
# --------------------------------------------------------------------------- #
PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"
_results = []          # list of (group, name, status, detail)
_tenant_markers = []   # every ('field', value) tenant marker seen across all bodies


def record(group, name, status, detail=""):
    _results.append((group, name, status, detail))
    tag = {PASS: "PASS", FAIL: "FAIL", SKIP: "SKIP"}[status]
    line = "  [%s] %s" % (tag, name)
    if detail:
        line += "  — " + detail
    print(line)


class Unreachable(Exception):
    """Raised when the network endpoint cannot be contacted at all."""


# --------------------------------------------------------------------------- #
# HTTP helpers (stdlib only)
# --------------------------------------------------------------------------- #
def mint_token(username, password):
    """Resource-Owner-Password grant against the :5180 proxy token endpoint.
    Returns (token, None) on success or (None, error_string) on an auth failure.
    Raises Unreachable if the endpoint itself cannot be contacted."""
    form = urllib.parse.urlencode({
        "grant_type": "password",
        "client_id": CLIENT_ID,
        "username": username,
        "password": password,
    }).encode()
    req = urllib.request.Request(TOKEN_URL, data=form, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as r:
            payload = json.loads(r.read().decode())
            return payload.get("access_token"), None
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode())
            msg = body.get("error_description") or body.get("error") or str(e.code)
        except Exception:
            msg = "HTTP %d" % e.code
        return None, msg
    except urllib.error.URLError as e:
        raise Unreachable("token endpoint %s unreachable: %s" % (TOKEN_URL, e.reason))


def api(method, path, token, body=None):
    """Call the backend. Returns (status_code, parsed_json_or_None, raw_text).
    Raises Unreachable on a connection-level failure."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API_BASE + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as r:
            raw = r.read().decode()
            return r.status, _parse(raw), raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        return e.code, _parse(raw), raw
    except urllib.error.URLError as e:
        raise Unreachable("%s %s unreachable: %s" % (method, path, e.reason))


def _parse(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


def scan_tenant_markers(obj):
    """Walk a parsed JSON body and remember any tenant markers seen so the
    tenant-isolation check can assert they all read 'default'."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("tenantId", "tenant_id", "tenant") and isinstance(v, str):
                _tenant_markers.append((k, v))
            else:
                scan_tenant_markers(v)
    elif isinstance(obj, list):
        for item in obj:
            scan_tenant_markers(item)


# --------------------------------------------------------------------------- #
# Realm-file password loading
# --------------------------------------------------------------------------- #
def load_passwords():
    with open(REALM_FILE, "r", encoding="utf-8") as f:
        realm = json.load(f)
    pw = {}
    for u in realm.get("users", []):
        name = u.get("username")
        for c in u.get("credentials", []):
            if c.get("type") == "password" and c.get("value"):
                pw[name] = c["value"]
                break
    # per-persona env override (optional)
    for name in PERSONAS:
        env = os.environ.get("HCM_PW_" + name.upper())
        if env:
            pw[name] = env
    return pw


# --------------------------------------------------------------------------- #
# Small assertion helpers wired to record()
# --------------------------------------------------------------------------- #
def check_ok(group, name, status, ok, detail=""):
    record(group, name, PASS if ok else FAIL, detail)
    return ok


def expect_deny(group, name, token, method, path, allowed=DENY_CODES, body=None):
    st, _, raw = api(method, path, token, body)
    ok = st in allowed
    detail = "%s %s -> %d (want %s)" % (
        method, path, st, "/".join(str(c) for c in sorted(allowed)))
    record(group, name, PASS if ok else FAIL, detail)
    return ok


# --------------------------------------------------------------------------- #
# Suite
# --------------------------------------------------------------------------- #
def main():
    print("=" * 78)
    print("Millers HCM — API invariant / smoke suite")
    print("  API base : %s" % API_BASE)
    print("  Issuer   : %s" % ISSUER)
    print("  Realm    : %s" % REALM_FILE)
    print("=" * 78)

    try:
        passwords = load_passwords()
    except Exception as e:
        print("ENV BLOCKER: cannot read realm file %s: %s" % (REALM_FILE, e))
        return 2

    missing = [p for p in PERSONAS if p not in passwords]
    if missing:
        print("ENV BLOCKER: realm file missing passwords for: %s" % ", ".join(missing))
        return 2

    # ----- Mint tokens (with hard-blocker detection) ----------------------- #
    print("\n[setup] Minting persona tokens (%s)" % TOKEN_URL)
    tokens = {}
    try:
        for persona in PERSONAS:
            tok, err = mint_token(persona, passwords[persona])
            if tok:
                tokens[persona] = tok
            else:
                tokens[persona] = None
                if persona in OPTIONAL_PERSONAS:
                    print("  [SKIP] token %-9s — %s (optional; skipping its checks)"
                          % (persona, err))
                else:
                    print("  [....] token %-9s — FAILED: %s" % (persona, err))
    except Unreachable as e:
        print("\nENV BLOCKER: %s" % e)
        print("The backend, Keycloak, or the Vite :5180 proxy is not reachable.")
        print("Start all three, then re-run: python3 scripts/uat_smoke.py")
        return 2

    # A single hard connectivity probe to the API before asserting anything.
    if tokens.get("admin"):
        try:
            api("GET", "/api/self/employee", tokens["admin"])
        except Unreachable as e:
            print("\nENV BLOCKER: %s" % e)
            print("Keycloak issued a token but the API (%s) is unreachable." % API_BASE)
            return 2

    # ---------------------------------------------------------------------- #
    # Group 1 — Auth & self-identity
    # ---------------------------------------------------------------------- #
    print("\n[1] Auth & self-identity (each persona sees THEIR OWN profile)")
    self_profiles = {}
    for persona, expected_no in PERSONAS.items():
        tok = tokens.get(persona)
        if tok is None:
            if persona in OPTIONAL_PERSONAS:
                record("1", "%s token+profile" % persona, SKIP,
                       "token not obtainable (TOTP-gated)")
            else:
                record("1", "%s token+profile" % persona, FAIL, "no token minted")
            continue
        st, body, _ = api("GET", "/api/self/employee", tok)
        scan_tenant_markers(body)
        got_no = body.get("employeeNo") if isinstance(body, dict) else None
        self_profiles[persona] = body if isinstance(body, dict) else {}
        ok = st == 200 and got_no == expected_no
        record("1", "%s -> /api/self/employee is %s" % (persona, expected_no),
               PASS if ok else FAIL,
               "status=%d employeeNo=%s" % (st, got_no))

    # Build employeeNo -> id map from admin's directory (needed for by-id tests).
    admin = tokens.get("admin")
    idmap = {}
    admin_total = None
    if admin:
        st, body, _ = api("GET", "/api/employees?size=200&page=0", admin)
        scan_tenant_markers(body)
        if st == 200 and isinstance(body, dict):
            for e in body.get("content", []):
                idmap[e.get("employeeNo")] = e.get("id")
            admin_total = body.get("totalElements")

    def emp_id(no):
        return idmap.get(no)

    # ---------------------------------------------------------------------- #
    # Group 2 — Self-service is self-scoped (IDOR)
    # ---------------------------------------------------------------------- #
    print("\n[2] Self-service self-scoping / IDOR (persona: employee)")
    emp = tokens.get("employee")
    mgr_id = emp_id("EMP-00002")
    self_emp_id = emp_id("EMP-00001")
    if emp is None:
        record("2", "employee self-scoping", FAIL, "employee token missing")
    else:
        # 2a — own payslips only
        st, body, _ = api("GET", "/api/self/payslips", emp)
        scan_tenant_markers(body)
        owners = {r.get("employeeId") for r in body} if isinstance(body, list) else set()
        ok = st == 200 and (not owners or owners == {self_emp_id})
        record("2", "employee /api/self/payslips returns only own rows",
               PASS if ok else FAIL,
               "status=%d rows=%s owners=%s" %
               (st, len(body) if isinstance(body, list) else "n/a",
                "self-only" if ok else owners))

        if mgr_id:
            # 2b — cannot read another employee's HR detail by id
            expect_deny("2", "employee cannot GET /api/employees/{managerId}",
                        emp, "GET", "/api/employees/" + mgr_id)
            # 2c — cannot read another employee's salary by id
            expect_deny("2", "employee cannot GET compensation of {managerId}",
                        emp, "GET", "/api/payroll/compensation?employeeId=" + mgr_id)
            # 2d — cannot read another employee's comp profile by id
            expect_deny("2", "employee cannot GET comp-profile of {managerId}",
                        emp, "GET",
                        "/api/compensation/employees/%s/profile" % mgr_id)
        else:
            record("2", "employee by-id IDOR probes", SKIP,
                   "manager id not resolved from directory")

        # cross-check: employee's own managerId links to the manager persona
        prof = self_profiles.get("employee", {})
        if mgr_id and prof.get("managerId"):
            ok = prof.get("managerId") == mgr_id
            record("2", "employee.managerId links to EMP-00002",
                   PASS if ok else FAIL,
                   "managerId=%s" % prof.get("managerId"))

    # ---------------------------------------------------------------------- #
    # Group 3 — Manager hierarchy scoping
    # ---------------------------------------------------------------------- #
    print("\n[3] Manager hierarchy scoping (persona: manager)")
    mgr = tokens.get("manager")
    if mgr is None:
        record("3", "manager hierarchy scoping", FAIL, "manager token missing")
    else:
        st, body, _ = api("GET", "/api/self/team", mgr)
        scan_tenant_markers(body)
        team_nos = {m.get("employeeNo") for m in body} if isinstance(body, list) else set()
        # 3a — exact team
        record("3", "team == {EMP-00001,13,19}",
               PASS if (st == 200 and team_nos == MANAGER_REPORTS) else FAIL,
               "status=%d team=%s" % (st, sorted(team_nos)))
        # 3b — excludes non-reports
        leaked = team_nos & MANAGER_NON_REPORTS
        record("3", "team excludes non-reports (EMP-00004/00012)",
               PASS if not leaked else FAIL,
               "leaked=%s" % (sorted(leaked) if leaked else "none"))
        # 3c — non-report detail hidden
        nonrep = emp_id("EMP-00004")
        if nonrep:
            expect_deny("3", "manager GET non-report detail is hidden",
                        mgr, "GET", "/api/employees/" + nonrep)
        else:
            record("3", "manager non-report detail probe", SKIP, "EMP-00004 id missing")
        # 3d — positive control: manager CAN see own report's detail
        if self_emp_id:
            st, body, _ = api("GET", "/api/employees/" + self_emp_id, mgr)
            scan_tenant_markers(body)
            ok = st == 200 and isinstance(body, dict) and body.get("employeeNo") == "EMP-00001"
            record("3", "manager CAN see own report detail (positive control)",
                   PASS if ok else FAIL, "status=%d" % st)

    # ---------------------------------------------------------------------- #
    # Group 4 — Salary confidentiality / masking
    # ---------------------------------------------------------------------- #
    print("\n[4] Salary confidentiality / masking by role")
    # 4a — positive control: admin sees a salary figure
    if admin and self_emp_id:
        st, body, _ = api("GET", "/api/payroll/compensation?employeeId=" + self_emp_id, admin)
        scan_tenant_markers(body)
        has_salary = isinstance(body, list) and any(
            (r.get("monthlyBaseSalary") is not None) for r in body)
        ok = st == 200 and has_salary
        record("4", "admin CAN see EMP-00001 salary (positive control)",
               PASS if ok else FAIL,
               "status=%d salaryPresent=%s" % (st, has_salary))
    else:
        record("4", "admin salary positive control", SKIP, "admin token or id missing")

    # 4b — employee cannot see anyone's salary
    if emp and mgr_id:
        expect_deny("4", "employee cannot see others' salary",
                    emp, "GET", "/api/payroll/compensation?employeeId=" + mgr_id)
    # 4c — manager cannot see a non-report's salary
    if mgr:
        nonrep = emp_id("EMP-00004")
        if nonrep:
            expect_deny("4", "manager cannot see non-report salary",
                        mgr, "GET", "/api/payroll/compensation?employeeId=" + nonrep)
        # 4d — manager team-compensation is never company-wide
        st, body, _ = api("GET", "/api/self/team/compensation", mgr)
        scan_tenant_markers(body)
        if st in DENY_CODES:
            record("4", "manager team-compensation gated (403) or team-only",
                   PASS, "status=%d (salary hidden)" % st)
        elif st == 200 and isinstance(body, list):
            rows = {r.get("employeeNo") for r in body}
            ok = rows.issubset(MANAGER_REPORTS)
            record("4", "manager team-compensation gated (403) or team-only",
                   PASS if ok else FAIL,
                   "status=200 rows=%s (must be subset of team)" % sorted(rows))
        else:
            record("4", "manager team-compensation gated (403) or team-only",
                   FAIL, "unexpected status=%d" % st)
        # 4e — the directory detail a manager CAN see carries no salary field
        if self_emp_id:
            st, body, _ = api("GET", "/api/employees/" + self_emp_id, mgr)
            keys = set(body.keys()) if isinstance(body, dict) else set()
            leaked_keys = keys & {"monthlyBaseSalary", "salary", "baseSalary",
                                  "netAmount", "grossAmount"}
            record("4", "employee-detail DTO exposes no salary field",
                   PASS if (st == 200 and not leaked_keys) else FAIL,
                   "leaked=%s" % (sorted(leaked_keys) if leaked_keys else "none"))

    # ---------------------------------------------------------------------- #
    # Group 5 — No payroll execution by non-payroll roles
    # ---------------------------------------------------------------------- #
    print("\n[5] No payroll execution by low-privilege roles (no real run is triggered)")
    run_body = {"periodYear": 2099, "periodMonth": 12, "currency": "AZN"}
    zero_uuid = "00000000-0000-0000-0000-000000000000"
    for persona in ("employee", "manager"):
        tok = tokens.get(persona)
        if tok is None:
            record("5", "%s cannot start a payroll run" % persona, FAIL, "no token")
            continue
        expect_deny("5", "%s POST /api/payroll/runs is denied" % persona,
                    tok, "POST", "/api/payroll/runs",
                    allowed=AUTHZ_DENY_CODES, body=run_body)
        expect_deny("5", "%s POST /api/payroll/runs/{id}/calculate is denied" % persona,
                    tok, "POST", "/api/payroll/runs/%s/calculate" % zero_uuid,
                    allowed=AUTHZ_DENY_CODES)

    # ---------------------------------------------------------------------- #
    # Group 6 — Tenant isolation (best-effort, single tenant)
    # ---------------------------------------------------------------------- #
    print("\n[6] Tenant isolation (best-effort)")
    if _tenant_markers:
        bad = [(k, v) for (k, v) in _tenant_markers if v != "default"]
        record("6", "all tenant markers read 'default'",
               PASS if not bad else FAIL,
               "seen=%d non-default=%s" % (len(_tenant_markers), bad or "none"))
    else:
        record("6", "tenant marker consistency", SKIP,
               "no response DTO surfaced a tenant marker; true cross-tenant "
               "isolation needs a 2nd tenant seeded (documented limitation)")

    # ---------------------------------------------------------------------- #
    # Group 7 — Positive controls
    # ---------------------------------------------------------------------- #
    print("\n[7] Positive controls (suite is not just asserting universal denial)")
    if admin:
        ok = admin_total is not None and admin_total > 0 and "EMP-00001" in idmap
        record("7", "admin CAN list employees (contains EMP-00001)",
               PASS if ok else FAIL,
               "total=%s" % admin_total)
    else:
        record("7", "admin list employees", FAIL, "admin token missing")

    hrs = tokens.get("hrspec")
    if hrs and admin_total is not None:
        st, body, _ = api("GET", "/api/employees?size=200&page=0", hrs)
        scan_tenant_markers(body)
        hrs_total = body.get("totalElements") if isinstance(body, dict) else None
        ok = st == 200 and hrs_total is not None and 0 < hrs_total < admin_total
        record("7", "hrspec directory is org-unit-scoped (fewer rows than admin)",
               PASS if ok else FAIL,
               "hrspec=%s admin=%s" % (hrs_total, admin_total))
    else:
        record("7", "hrspec org-unit scoping", SKIP, "hrspec token or admin count missing")

    # ---------------------------------------------------------------------- #
    # Summary
    # ---------------------------------------------------------------------- #
    n_pass = sum(1 for r in _results if r[2] == PASS)
    n_fail = sum(1 for r in _results if r[2] == FAIL)
    n_skip = sum(1 for r in _results if r[2] == SKIP)

    print("\n" + "=" * 78)
    print("SUMMARY:  PASS=%d  FAIL=%d  SKIP=%d  (total=%d)"
          % (n_pass, n_fail, n_skip, len(_results)))
    if n_fail:
        print("\nFAILURES:")
        for group, name, status, detail in _results:
            if status == FAIL:
                print("  - [%s] %s  (%s)" % (group, name, detail))
    if n_skip:
        print("\nSKIPPED:")
        for group, name, status, detail in _results:
            if status == SKIP:
                print("  - [%s] %s  (%s)" % (group, name, detail))
    verdict = "INVARIANTS HOLD" if n_fail == 0 else "INVARIANT BREACH"
    print("\nVERDICT: %s" % verdict)
    print("=" * 78)
    return n_fail


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Unreachable as e:
        print("\nENV BLOCKER: %s" % e)
        sys.exit(2)
    except KeyboardInterrupt:
        sys.exit(130)
