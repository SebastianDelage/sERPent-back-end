-- V17__add_scale_barcode_formats.sql
--
-- H2 twin of V27 in the Postgres set. The two sets are numbered independently; this is
-- the same change, written for the dev database.
--
-- See the Postgres file for why the layout is configuration and not code.

CREATE TABLE scale_barcode_formats (
    scale_barcode_format_id BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(80)  NOT NULL,
    prefix                  VARCHAR(4)   NOT NULL,
    total_length            INT          NOT NULL,
    product_code_start      INT          NOT NULL,
    product_code_length     INT          NOT NULL,
    value_start             INT          NOT NULL,
    value_length            INT          NOT NULL,
    value_type              VARCHAR(20)  NOT NULL,
    value_decimals          INT          NOT NULL,
    validate_check_digit    BOOLEAN      NOT NULL DEFAULT TRUE,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT ux_scale_barcode_formats_prefix_length UNIQUE (prefix, total_length),

    CONSTRAINT ck_scale_barcode_formats_value_type
        CHECK (value_type IN ('WEIGHT', 'AMOUNT')),
    CONSTRAINT ck_scale_barcode_formats_positions
        CHECK (product_code_start >= 1 AND product_code_length >= 1
               AND value_start >= 1 AND value_length >= 1
               AND value_decimals >= 0
               AND product_code_start + product_code_length - 1 <= total_length
               AND value_start + value_length - 1 <= total_length)
);

ALTER TABLE products
    ADD COLUMN scale_code VARCHAR(20);

ALTER TABLE products
    ADD CONSTRAINT ux_products_scale_code UNIQUE (scale_code);

-- =========================================================================
-- DEV SEED
-- =========================================================================
-- The dev database is H2 in memory: it is rebuilt from these files on every boot, so
-- anything not seeded here has to be typed in by hand before it can be tried out.
--
-- This is the real scale at the shop, a Kretz RPL US30P2CAR, in the 2-5-5 layout read off
-- four real tickets:
--
--     2 000016 00560 8
--     | |      |     +-- 13: EAN-13 check digit
--     | |      +-------- 8..12: weight, 5 digits of grams, so 3 implied decimals -> 0,560 kg
--     | +--------------- 2..7: product code, 6 digits
--     +----------------- 1: prefix
--
-- The seed goes in this file and NOT in V2__seed_data.sql: that one is already applied
-- and Flyway checksums it.
INSERT INTO scale_barcode_formats (
    scale_barcode_format_id, name, prefix, total_length,
    product_code_start, product_code_length,
    value_start, value_length,
    value_type, value_decimals, validate_check_digit, active
)
VALUES
    (1, 'Kretz RPL US30P2CAR (2-5-5)', '2', 13, 2, 6, 8, 5, 'WEIGHT', 3, TRUE, TRUE);

-- Codes off the scale listing, where they are printed as "C:". NOT the PLU: the milanesa
-- is PLU 1 and code 16. Stored stripped of leading zeros, which is how the service
-- normalizes anything typed into the product form.
--
-- With these two, the verified labels 2000016005608 and 2000030013351 scan in dev without
-- anybody loading data first.
UPDATE products SET scale_code = '16' WHERE product_id = 3;  -- Milanesa de pollo (KG)
UPDATE products SET scale_code = '30' WHERE product_id = 2;  -- Pata muslo (KG)
