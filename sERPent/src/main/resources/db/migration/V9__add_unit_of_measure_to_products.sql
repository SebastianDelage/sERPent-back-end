ALTER TABLE products
    ADD COLUMN unit_of_measure VARCHAR(20);

UPDATE products
SET unit_of_measure = 'UNIT'
WHERE unit_of_measure IS NULL;

ALTER TABLE products
    ALTER COLUMN unit_of_measure SET NOT NULL;