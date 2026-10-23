-- A category has at most one budget per user and month, including concurrent writes.
ALTER TABLE budgets
    ADD CONSTRAINT uk_budgets_user_category_month UNIQUE (user_id, category_id, `month`);
