-- Convert remaining CHAR(n) columns to VARCHAR(n) for Hibernate schema-validation compatibility.
ALTER TABLE payroll.erp_export_line
    ALTER COLUMN currency TYPE varchar(3) USING currency::varchar;

ALTER TABLE security.api_key
    ALTER COLUMN key_hash TYPE varchar(64) USING key_hash::varchar;
