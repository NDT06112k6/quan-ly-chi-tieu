CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    type ENUM('CHI', 'THU') NOT NULL,

    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_user_id_id UNIQUE (user_id, id),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id)
        REFERENCES users (id)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    occurred_on DATE NOT NULL,
    note VARCHAR(1000),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    INDEX idx_transactions_user_occurred_on (user_id, occurred_on),
    INDEX idx_transactions_user_category (user_id, category_id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id)
        REFERENCES users (id),
    -- A transaction can only reference a category owned by the same user.
    CONSTRAINT fk_transactions_category FOREIGN KEY (user_id, category_id)
        REFERENCES categories (user_id, id)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE TABLE budgets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    -- Store each budget month as its first calendar day, for example 2026-10-01.
    `month` DATE NOT NULL,
    limit_amount DECIMAL(15,2) NOT NULL,

    CONSTRAINT pk_budgets PRIMARY KEY (id),
    INDEX idx_budgets_user_category (user_id, category_id),
    CONSTRAINT chk_budgets_month CHECK (DAYOFMONTH(`month`) = 1),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id)
        REFERENCES users (id),
    -- A budget can only reference a category owned by the same user.
    CONSTRAINT fk_budgets_category FOREIGN KEY (user_id, category_id)
        REFERENCES categories (user_id, id)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;
