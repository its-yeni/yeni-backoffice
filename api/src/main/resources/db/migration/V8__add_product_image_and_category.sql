ALTER TABLE product ADD COLUMN image_url VARCHAR(500) NULL;
CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL,
    exposed BIT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT uk_product_category_name UNIQUE(category_name)
);
