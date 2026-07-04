-- M128 — Asset depreciation schedule.
--
-- Extends the M124 employee_asset row with the financial-accounting
-- fields needed to compute a monthly depreciation schedule. None of
-- the existing rows have these values, so all defaults are NULL +
-- method = NONE — assets only depreciate once HR fills the cost +
-- life + method.

ALTER TABLE core_hr.employee_asset
    ADD COLUMN purchase_cost           NUMERIC(14,2),
    ADD COLUMN purchase_date           DATE,
    ADD COLUMN useful_life_months      INTEGER,
    ADD COLUMN salvage_value           NUMERIC(14,2),
    /* STRAIGHT_LINE, DECLINING_BALANCE, NONE */
    ADD COLUMN depreciation_method     VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    /* Annual percentage used for DECLINING_BALANCE; SL ignores this.
     * Example: 20.00 means each year deducts 20% of the asset's
     * current book value. NULL falls back to 2/useful_life (the
     * common 2x straight-line assumption — "double declining"). */
    ADD COLUMN declining_rate_percent  NUMERIC(5,2);

ALTER TABLE core_hr.employee_asset
    ADD CONSTRAINT chk_asset_depreciation_method CHECK (
        depreciation_method IN ('STRAIGHT_LINE', 'DECLINING_BALANCE', 'NONE'));

ALTER TABLE core_hr.employee_asset
    ADD CONSTRAINT chk_asset_purchase_cost_nonneg CHECK (
        purchase_cost IS NULL OR purchase_cost >= 0);

ALTER TABLE core_hr.employee_asset
    ADD CONSTRAINT chk_asset_useful_life_positive CHECK (
        useful_life_months IS NULL OR useful_life_months > 0);

ALTER TABLE core_hr.employee_asset
    ADD CONSTRAINT chk_asset_salvage_nonneg CHECK (
        salvage_value IS NULL OR salvage_value >= 0);

ALTER TABLE core_hr.employee_asset
    ADD CONSTRAINT chk_asset_declining_rate_range CHECK (
        declining_rate_percent IS NULL
        OR (declining_rate_percent > 0 AND declining_rate_percent <= 100));
