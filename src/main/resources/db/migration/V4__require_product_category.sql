INSERT INTO categories (name, description, created_at, updated_at, created_by, last_modified_by)
SELECT 'Uncategorised', 'Products created before categories became mandatory',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'system', 'system'
FROM DUAL
WHERE EXISTS (SELECT 1 FROM products WHERE category_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Uncategorised');

UPDATE products
SET category_id = (SELECT id FROM categories WHERE name = 'Uncategorised')
WHERE category_id IS NULL;

ALTER TABLE products MODIFY category_id BIGINT NOT NULL;

DROP INDEX idx_products_active ON products;
CREATE INDEX idx_products_active_category ON products (active, category_id);
