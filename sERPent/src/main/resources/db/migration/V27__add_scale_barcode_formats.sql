-- V27__add_scale_barcode_formats.sql
--
-- Support for the labels a weighing scale prints.
--
-- GS1 reserves the barcode prefix 2 for a shop's own internal use, which means there is
-- no standard layout to implement: each brand splits the 13 digits its own way, and one
-- shop can have scales from two brands at once. So the layout is configuration, not code.
--
-- Positions are 1-BASED from the left, the way the scale manuals number them.

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

    -- Prefix plus length is what decides which format claims a scanned code. Two rows
    -- sharing both would make the result depend on row order, which is not a thing a
    -- cashier can be asked to debug. ScaleBarcodeFormatService refuses it first with an
    -- explanation; this is the net for anything that writes without going through it.
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

-- The product number the SCALE prints inside a label, shown as "C:" in its listing.
-- Separate from barcode because they are different things and a product can have both,
-- and separate from the PLU, which is a third number again (on the shop's Kretz the
-- milanesa is PLU 1 and code 16).
--
-- Stored with leading zeros stripped: the label carries 000016 and the listing says 16,
-- and unless both collapse to one value the UNIQUE below does not mean anything.
ALTER TABLE products
    ADD COLUMN scale_code VARCHAR(20);

-- Nullable + UNIQUE lets Postgres allow any number of NULLs, same pattern as sku and
-- barcode: most products have no scale code.
ALTER TABLE products
    ADD CONSTRAINT ux_products_scale_code UNIQUE (scale_code);
