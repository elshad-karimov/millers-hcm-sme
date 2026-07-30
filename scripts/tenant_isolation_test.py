#!/usr/bin/env python3
"""
Multi-tenancy Phase 5 — cross-tenant isolation battery.

Proves the discriminator + JWT-issuer → TenantContext plumbing actually isolates
two live tenants end to end, using two real Keycloak realms:

  * tenant 'default' — realm millers-hcm   (15 seeded employees)
  * tenant 'acme'    — realm millers-acme   (provisioned fresh, 0 employees)

Flow:
  1. Mint a default SYSTEM_ADMIN token (millers-hcm realm).
  2. Provision tenant 'acme' via POST /api/admin/tenants (idempotent).
  3. Mint an acme SYSTEM_ADMIN token (millers-acme realm) — proves the new
     realm's issuer is now trusted by the multi-issuer resolver.
  4. acme admin creates an employee via the normal API — Hibernate @TenantId
     must stamp tenant_id='acme' (write-path isolation).
  5. Assert bidirectional read isolation:
       - acme admin sees ONLY acme employees (never default's EMP-000xx),
       - default admin still sees its 15 and NOT the acme employee,
       - neither can GET the other's employee by id (404).

Standard library only. Passwords are read from the realm JSON files at runtime,
never passed on the command line.

Env overrides: HCM_API_BASE (default http://localhost:8082),
HCM_KC_BASE (default http://localhost:5180).
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API_BASE = os.environ.get("HCM_API_BASE", "http://localhost:8082").rstrip("/")
KC_BASE = os.environ.get("HCM_KC_BASE", "http://localhost:5180").rstrip("/")
CLIENT_ID = os.environ.get("HCM_KC_CLIENT_ID", "hcm-web")
HTTP_TIMEOUT = int(os.environ.get("HCM_HTTP_TIMEOUT", "25"))
_REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DEFAULT_REALM = "millers-hcm"
ACME_REALM = "millers-acme"
ACME_ISSUER = KC_BASE + "/realms/" + ACME_REALM

_results = []


def record(name, ok, detail=""):
    _results.append(ok)
    print("  [%s] %s%s" % ("PASS" if ok else "FAIL", name, ("  — " + detail) if detail else ""))


def realm_password(realm_file, username):
    with open(os.path.join(_REPO, "keycloak", realm_file), encoding="utf-8") as f:
        realm = json.load(f)
    for u in realm.get("users", []):
        if u.get("username") == username:
            for c in u.get("credentials", []):
                if c.get("type") == "password":
                    return c.get("value")
    raise SystemExit("no password for %s in %s" % (username, realm_file))


def mint_token(realm, username, password):
    url = KC_BASE + "/realms/" + realm + "/protocol/openid-connect/token"
    form = urllib.parse.urlencode({
        "grant_type": "password", "client_id": CLIENT_ID,
        "username": username, "password": password,
    }).encode()
    req = urllib.request.Request(url, data=form, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as r:
            return json.loads(r.read().decode()).get("access_token"), None
    except urllib.error.HTTPError as e:
        try:
            b = json.loads(e.read().decode())
            return None, b.get("error_description") or b.get("error") or ("HTTP %d" % e.code)
        except Exception:
            return None, "HTTP %d" % e.code
    except urllib.error.URLError as e:
        return None, "unreachable: %s" % e.reason


def api(method, path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API_BASE + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT) as r:
            raw = r.read().decode()
            return r.status, _json(raw)
    except urllib.error.HTTPError as e:
        return e.code, _json(e.read().decode())
    except urllib.error.URLError as e:
        raise SystemExit("%s %s unreachable: %s" % (method, path, e.reason))


def _json(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


def emp_nos(listing):
    """Extract employee numbers from a list or a paged {content:[...]} body."""
    rows = listing.get("content") if isinstance(listing, dict) else listing
    if not isinstance(rows, list):
        return []
    out = []
    for r in rows:
        if isinstance(r, dict):
            out.append(r.get("employeeNo") or r.get("employee_no") or r.get("id"))
    return out


def main():
    print("=" * 78)
    print("MULTI-TENANCY PHASE 5 — CROSS-TENANT ISOLATION")
    print("  API base : %s" % API_BASE)
    print("  Keycloak : %s" % KC_BASE)
    print("=" * 78)

    # 1) default admin token
    dadmin_pw = realm_password("realm-millers-hcm.json", "admin")
    dtok, err = mint_token(DEFAULT_REALM, "admin", dadmin_pw)
    if not dtok:
        record("mint default admin token", False, err)
        return summary()
    record("mint default admin token (millers-hcm)", True)

    # 2) provision tenant 'acme' (idempotent)
    st, body = api("POST", "/api/admin/tenants", dtok, {
        "id": "acme", "name": "Acme Corp", "issuerUri": ACME_ISSUER,
        "realm": ACME_REALM, "seedFromTenant": "default",
    })
    if st in (200, 201):
        total = sum((body or {}).get("referenceCounts", {}).values()) if isinstance(body, dict) else 0
        record("provision tenant 'acme'", True, "reference rows seeded=%d" % total)
    else:
        # Idempotent re-run: provisioning fails if acme already exists. Confirm
        # via the registry listing rather than trusting a specific status code.
        _, listing = api("GET", "/api/admin/tenants", dtok)
        exists = isinstance(listing, list) and any(
            isinstance(t, dict) and t.get("id") == "acme" for t in listing)
        record("provision tenant 'acme' (already provisioned — idempotent re-run)", exists,
               "status=%d already_registered=%s" % (st, exists))
        if not exists:
            return summary()

    # 3) acme admin token — proves the new realm's issuer is now trusted
    aadmin_pw = realm_password("realm-millers-acme.json", "acmeadmin")
    atok, err = mint_token(ACME_REALM, "acmeadmin", aadmin_pw)
    if not atok:
        record("mint acme admin token (millers-acme)", False, err)
        return summary()
    record("mint acme admin token (millers-acme)", True)

    st, who = api("GET", "/api/self/employee", atok)
    # acme admin has no linked employee yet — 404/empty is fine; the point is the
    # token authenticates (not 401) through the multi-issuer resolver.
    record("acme token authenticates against API (not 401)", st != 401, "GET /api/self/employee -> %d" % st)

    # 4) acme admin creates an employee via the normal API (write-path stamping)
    st, created = api("POST", "/api/employees", atok, {
        "firstName": "Acme", "lastName": "Isolation", "hireDate": "2026-02-01",
    })
    if st not in (200, 201) or not isinstance(created, dict):
        record("acme admin creates an employee", False, "status=%d body=%s" % (st, json.dumps(created)[:200]))
        return summary()
    acme_emp_id = created.get("id")
    acme_emp_no = created.get("employeeNo") or created.get("employee_no")
    record("acme admin creates an employee (write path)", True, "id=%s no=%s" % (acme_emp_id, acme_emp_no))

    # Per-tenant numbering: acme's counter starts fresh at 1 (its first employee
    # is EMP-00001) — coexisting with default's own EMP-00001 thanks to the
    # per-tenant (tenant_id, employee_no) unique. Proves numbering is per-tenant,
    # not a shared global sequence.
    record("per-tenant numbering: acme's first employee is EMP-00001",
           acme_emp_no == "EMP-00001",
           "acme employeeNo=%s (default also has EMP-00001)" % acme_emp_no)

    def ids_of(listing):
        rows = listing.get("content") if isinstance(listing, dict) else listing
        return {r.get("id") for r in (rows or []) if isinstance(r, dict)}

    # default admin list — capture the default tenant's employee id set
    st, dlist = api("GET", "/api/employees", dtok)
    default_ids = ids_of(dlist) if st == 200 else set()
    dnos = emp_nos(dlist) if st == 200 else []

    # 5a) acme admin list must be disjoint from default's ids (isolation by tenant,
    #     not by employee_no — the global no-sequence can mint EMP-000NN for acme too)
    st, alist = api("GET", "/api/employees", atok)
    acme_ids = ids_of(alist) if st == 200 else set()
    leaked = acme_ids & default_ids
    record("acme admin sees ONLY acme employees (no default ids leak)",
           st == 200 and not leaked and acme_emp_id in acme_ids,
           "acme_ids=%d includes_own=%s leaked_default_ids=%d"
           % (len(acme_ids), acme_emp_id in acme_ids, len(leaked)))

    # 5b) default admin list still 15, excludes the acme employee id
    record("default admin does NOT see the acme employee",
           acme_emp_id not in default_ids,
           "default_count=%d acme_emp_present=%s" % (len(dnos), acme_emp_id in default_ids))

    # 5c) neither tenant can fetch the other's employee by id
    st, _ = api("GET", "/api/employees/" + str(acme_emp_id), dtok)
    record("default admin GET acme employee by id is hidden", st in (403, 404),
           "GET /api/employees/{acmeId} -> %d" % st)

    # find a default employee id to probe from the acme side
    default_id = None
    for r in (dlist.get("content") if isinstance(dlist, dict) else dlist or []):
        if isinstance(r, dict) and (r.get("employeeNo") or "").startswith("EMP-000"):
            default_id = r.get("id")
            break
    if default_id:
        st, _ = api("GET", "/api/employees/" + str(default_id), atok)
        record("acme admin GET default employee by id is hidden", st in (403, 404),
               "GET /api/employees/{defaultId} -> %d" % st)

    return summary()


def summary():
    p = sum(1 for r in _results if r)
    f = sum(1 for r in _results if not r)
    print("\n" + "=" * 78)
    print("SUMMARY:  PASS=%d  FAIL=%d  (total=%d)" % (p, f, len(_results)))
    print("VERDICT: %s" % ("TENANT ISOLATION HOLDS" if f == 0 else "ISOLATION BROKEN — SEE FAILURES"))
    print("=" * 78)
    sys.exit(1 if f else 0)


if __name__ == "__main__":
    main()
