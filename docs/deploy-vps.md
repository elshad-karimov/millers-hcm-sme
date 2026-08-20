# Deploying the SME edition to the VPS

Runs **alongside** the enterprise edition on the same box: separate containers,
separate Postgres, separate Keycloak, different ports, its own Docker network.
Nothing here touches `hcm-*` containers, the enterprise database, or
`hcm-test.millers-software.com`.

Target box: **49.13.18.119**, public hostname
**https://hcm-sme.millers-software.com**.

Artifacts: [`deploy/docker-compose.yml`](../deploy/docker-compose.yml),
[`deploy/Caddyfile.snippet`](../deploy/Caddyfile.snippet),
[`deploy/env.example`](../deploy/env.example).

> **TLS and routing are not in this stack.** The box already runs a shared
> Caddy (`millerscrm-caddy-1`) that owns :80/:443 and fronts every app on it,
> issuing certificates automatically. A second thing binding :80 would fight it
> for the ACME challenge and break every other site on the machine.

---

## How a release reaches the VPS

```
push to main ──► CI: checkstyle, tests, package, docker build
                        │
                        └─► ghcr.io/<owner>/millers-hcm-sme:latest + :<sha>
                                    │
        Actions ▸ "Deploy to Test" ▸ Run workflow  (manual)
                                    │
                    ssh → cd $TEST_COMPOSE_DIR
                          docker compose up -d --no-build hcm-backend
```

Only `hcm-backend` is rolled. Postgres, Keycloak and Redis keep running
across deploys. The image bundles the React SPA, so one roll ships API and UI
together.

CI publishes an image **only from `main`** (`ci.yml`: `github.ref ==
'refs/heads/main'`). A branch build runs the tests but produces nothing to
deploy.

---

## Port map (both editions, one box)

| | Enterprise | **SME** |
|---|---|---|
| Postgres | 5433 | **5533** |
| Keycloak DB | 5434 | **5534** |
| Keycloak HTTP | 8090 | **8190** |
| Redis | 6380 | **6480** |
| Backend | 8080 (internal) | **8083** (loopback) |
| TLS / routing | shared Caddy | shared Caddy |

**Nothing in this stack binds `0.0.0.0`.** Every host port sits on `127.0.0.1`
for psql and the Keycloak admin console over an SSH tunnel. The only public
door is Caddy.

---

## First-time setup

### 1. On the VPS

```bash
sudo mkdir -p /opt/hcm-sme && cd /opt/hcm-sme
git clone https://github.com/<owner>/millers-hcm-sme.git .
cp deploy/env.example deploy/.env
```

Fill in `deploy/.env`. Generate the encryption key once and keep a copy
**off** this box:

```bash
openssl rand -base64 32
```

> `HCM_SECURITY_DATA_KEY` encrypts national IDs and bank details. Change it or
> lose it and every existing encrypted row becomes unreadable. There is no
> recovery.

`PUBLIC_URL` must be the exact origin browsers use — scheme, host and port. It
becomes the OIDC issuer *and* Keycloak's advertised hostname; a mismatch means
every token is rejected as an untrusted issuer.

### 2. DNS

Add an A record before anything else — Caddy cannot issue a certificate for a
name that does not resolve:

```
hcm-sme.millers-software.com.  A  49.13.18.119
```

Check with `dig +short hcm-sme.millers-software.com`.

### 3. Start the supporting stack

```bash
cd /opt/hcm-sme/deploy
docker compose up -d postgres keycloak-pg keycloak redis
docker compose ps
```

Wait for `sme-keycloak` to report healthy — the first start imports the realm
and can take a couple of minutes.

### 3b. Wire it into Caddy

```bash
docker network connect sme-net millerscrm-caddy-1
cat /opt/hcm-sme/deploy/Caddyfile.snippet >> /opt/millerscrm/caddy/Caddyfile
docker exec millerscrm-caddy-1 caddy validate --config /etc/caddy/Caddyfile
docker exec millerscrm-caddy-1 caddy reload   --config /etc/caddy/Caddyfile
```

> **Back the Caddyfile up first.** It serves every site on this box, so a
> syntax error takes them all down. Always `validate` before `reload`.

### 4. GitHub secrets

*Settings ▸ Secrets and variables ▸ Actions* on `millers-hcm-sme`. **The repo
currently has none of these**, which is why the deploy workflows cannot run:

| Secret | Value |
|---|---|
| `TEST_HOST` | VPS hostname or IP |
| `TEST_USER` | SSH user (needs Docker rights) |
| `TEST_SSH_KEY` | Private key for that user — paste into GitHub, share with nobody |
| `TEST_COMPOSE_DIR` | `/opt/hcm-sme/deploy` |
| `GHCR_PAT` | PAT with `read:packages`. **The package is public today**, so a plain `docker pull` works and this is only needed if you make it private. |

`deploy-prod.yml` uses the same names with a `PROD_` prefix and additionally
needs a **`production` environment** (*Settings ▸ Environments*) with required
reviewers — that is the approval gate, and it does not exist yet either.

### 5. First deploy

Push to `main`, wait for CI to publish the image, then *Actions ▸ Deploy to
Test ▸ Run workflow*. Or roll by hand on the box:

```bash
cd /opt/hcm-sme/deploy
echo "$GHCR_PAT" | docker login ghcr.io -u <owner> --password-stdin
docker compose pull hcm-backend
docker compose up -d --no-build hcm-backend
docker compose logs -f hcm-backend
```

Flyway runs on startup. The first boot applies every migration and takes a few
minutes.

### 6. Seed the tenant

The registry maps a Keycloak issuer to a tenant; the seeded row points at the
dev URL and must be corrected to the public one, or no token resolves:

```bash
docker compose exec postgres psql -U hcm -d hcm -c \
  "UPDATE config.tenant
      SET issuer_uri = 'https://hcm-sme.millers-software.com/realms/millers-hcm'
    WHERE id = 'default';"
```

Then create the base employee records so the logins resolve to people:

```bash
docker compose exec -T postgres psql -U hcm -d hcm \
  < /opt/hcm-sme/scripts/seed-sme-base-users.sql
```

---

## Before you deploy: the plan default

`V315` adds `config.tenant.plan NOT NULL DEFAULT 'LITE'`.

On a **fresh** SME database that is exactly right — the tenant starts on LITE
and 13 modules are correctly out of plan. On a database that already carries a
tenant, that tenant becomes LITE the moment migrations run and those modules
begin returning 403. To put a tenant on the full product:

```sql
UPDATE config.tenant SET plan = 'ENTERPRISE' WHERE id = 'default';
```

or `PUT /api/admin/tenants/{id}/plan` as SYSTEM_ADMIN, which is audit-logged
and takes effect without a restart.

---

## Verifying a deploy

```bash
# Container up and reporting ready
docker compose ps hcm-backend
docker compose exec -T hcm-backend wget -qO- http://localhost:8080/actuator/health/readiness

# Public surface (401 unauthenticated is CORRECT — it means security is on)
curl -o /dev/null -w '%{http_code}\n' https://hcm-sme.millers-software.com/api/module-settings

# The login page renders (not a 502)
curl -s https://hcm-sme.millers-software.com/realms/millers-hcm/.well-known/openid-configuration \
  | head -c 120

# Migrations landed
docker compose exec postgres psql -U hcm -d hcm -c \
  "SELECT version, description, success
     FROM core_hr.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

## Rolling back

Images are tagged by commit SHA, so a rollback is a redeploy of the previous
tag — via the workflow's `image_tag` input, or by hand:

```bash
HCM_IMAGE_TAG=<previous-sha> docker compose up -d --no-build hcm-backend
```

> **Flyway does not roll back.** An image rollback reverts code, not schema. If
> a release ships a destructive migration, restore the database from a dump
> taken before the deploy — take one first:
> `docker compose exec -T postgres pg_dump -U hcm hcm | gzip > backup-$(date +%F).sql.gz`

---

## Troubleshooting

**502 for this host only.** Caddy is not on the SME network:
`docker network connect sme-net millerscrm-caddy-1`.

**502 on the login page specifically.** Caddy sizes proxy buffers dynamically,
so the large-`Set-Cookie` problem that 502s nginx does not apply here. If you
ever front this with nginx instead, it needs `proxy_buffer_size 32k` or the
login page fails while JWKS keeps working — which reads as a broken login
rather than a proxy misconfiguration.

**Every token rejected / 401 on all API calls.** `PUBLIC_URL`,
`KC_HOSTNAME_URL` and `config.tenant.issuer_uri` disagree. All three must be
the same origin, character for character.

**Container marked unhealthy but the service works.** Check whether the probe
uses `localhost`; inside a container that resolves to `::1` first while these
services listen on IPv4. Use `127.0.0.1`.
