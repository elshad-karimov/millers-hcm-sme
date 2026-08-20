-- ----------------------------------------------------------------------------
-- V316 — Point the seeded tenant at the SME edition's own issuer.
--
-- V310 seeded config.tenant with 'http://localhost:5180/realms/millers-hcm',
-- the ENTERPRISE SPA origin. This fork serves its SPA on 5181 and its backend
-- expects that issuer (spring.security.oauth2.resourceserver.jwt.issuer-uri,
-- KC_HOSTNAME_URL). With the 5180 value in place, TenantResolutionFilter finds
-- no tenant for an SME token and every authenticated request fails.
--
-- Idempotent and narrow: rewrites ONLY the stale enterprise value, so a tenant
-- already pointed at a real issuer (staging, production) is left alone.
-- ----------------------------------------------------------------------------

UPDATE config.tenant
   SET issuer_uri = 'http://localhost:5181/realms/millers-hcm',
       updated_at = now(),
       updated_by = 'flyway-V316'
 WHERE issuer_uri = 'http://localhost:5180/realms/millers-hcm';

-- The Phase-5 second tenant realm ('acme') used for cross-tenant isolation
-- testing, if a prior migration or operator seeded it on the enterprise port.
UPDATE config.tenant
   SET issuer_uri = 'http://localhost:5181/realms/millers-acme',
       updated_at = now(),
       updated_by = 'flyway-V316'
 WHERE issuer_uri = 'http://localhost:5180/realms/millers-acme';
