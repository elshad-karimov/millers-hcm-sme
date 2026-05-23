-- Employee Self-Service support (PRD 14.6 + 11.x).
-- Maps the authenticated username to a single Employee row so that the
-- /api/self/* endpoints can resolve "the current employee" without
-- needing a separate auth schema yet (Keycloak is a later milestone).

ALTER TABLE core_hr.employee
    ADD COLUMN username VARCHAR(120);

CREATE UNIQUE INDEX uq_employee_username
    ON core_hr.employee (username)
    WHERE username IS NOT NULL;

-- Link the seeded 'employee' in-memory user to EMP-00001 (Aliya).
-- If the row isn't present yet (fresh install with no seed) this is a no-op.
UPDATE core_hr.employee
   SET username = 'employee'
 WHERE employee_no = 'EMP-00001';
