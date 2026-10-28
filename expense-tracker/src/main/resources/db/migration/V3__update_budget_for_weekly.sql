-- Keep applied V1/V2 checksums and preserve every existing monthly budget.
ALTER TABLE budgets
    DROP CHECK chk_budgets_month,
    DROP INDEX uk_budgets_user_category_month,
    CHANGE COLUMN `month` period VARCHAR(10) NOT NULL,
    ADD COLUMN type VARCHAR(7) NOT NULL DEFAULT 'MONTHLY';

-- All existing owners participate in this schema backfill.
UPDATE budgets
SET period = LEFT(period, 7)
WHERE user_id IS NOT NULL;

ALTER TABLE budgets
    MODIFY COLUMN period VARCHAR(8) NOT NULL,
    ADD CONSTRAINT chk_budgets_type CHECK (type IN ('MONTHLY', 'WEEKLY')),
    ADD CONSTRAINT uk_budgets_user_category_period_type UNIQUE (user_id, category_id, period, type);
