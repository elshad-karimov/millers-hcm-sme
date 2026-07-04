-- Change token_hash from CHAR(64) to VARCHAR(64) so Hibernate schema-validation passes.
ALTER TABLE core_hr.preboarding_invite
    ALTER COLUMN token_hash TYPE varchar(64) USING token_hash::varchar;
