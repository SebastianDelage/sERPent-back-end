package com.empresa.serpent.shared.validation;

/**
 * How much a single line may carry, and why there are two numbers instead of one.
 *
 * <h2>WHY THIS EXISTS</h2>
 *
 * <p>Until this class was written there was <b>no upper bound anywhere</b>: six quantity
 * controls on the front end with {@code required} and {@code min} but no {@code max}, nine
 * request DTOs with {@code @NotNull} and {@code @Positive} but no {@code @Digits}, and the
 * only ceiling in the whole stack was the database's {@code NUMERIC(12,3)} — nine integer
 * digits and three decimals.
 *
 * <p>It surfaced from the display side, not the data side: the sale's quantity field showed
 * six characters while the column accepted thirteen, so a perfectly storable value such as
 * "999,999" was drawn clipped. The tail was lost — the third decimal, i.e. grams in a sale
 * by weight — and the operator read a different number from the one the system held.
 *
 * <p>The stored value was always correct. What broke was the <b>check</b>: the number module
 * deliberately accepts ambiguous input (a lone dot means thousands in an amount and a decimal
 * in a quantity) and promises in exchange that the field is rewritten formatted on blur, so
 * the operator sees what the app understood before confirming anything. A clipped field
 * cannot keep that promise.
 *
 * <h2>WHY TWO CEILINGS</h2>
 *
 * <p>The split is not "sales versus everything else": it is <b>what leaves one at a time
 * versus what arrives by the pallet</b>.
 *
 * <p>A COUNTER line is typed by a cashier with people waiting, where an extra digit is a
 * typo rather than a large order. A counter butcher does not move a tonne on a single line;
 * if it were ever needed, it goes in as two lines.
 *
 * <p>A WAREHOUSE line is entered by someone copying a delivery note, where the large number
 * <i>is</i> the data: restocking a freezer is hundreds of kilos and a stocktake can be
 * thousands. The counter ceiling would reject legitimate receipts and push people to split a
 * real delivery note into fictitious lines — dirtying the data so the screen does not break.
 *
 * <h2>WHAT THIS ANNOTATION IS AND IS NOT FOR</h2>
 *
 * <p>It is a <b>contract guard</b>, not operator feedback. {@code GlobalExceptionHandler}
 * puts bean-validation messages under {@code details} and returns a generic {@code message},
 * and the front end's error interceptor reads only {@code message} — so the text below never
 * reaches a cashier. What it does cover is every caller that does not go through the form:
 * direct API use, the offline sync path, and future clients.
 *
 * <p>The message the operator reads comes from the front-end validators. The single source of
 * truth for the numbers themselves is {@code shared/forms/techos-de-cantidad.ts}; these
 * constants mirror it because {@code @Digits} needs compile-time literals.
 */
public final class QuantityLimits {

    private QuantityLimits() {}

    /**
     * Counter line — sales and returns. Three integer digits and three decimals, so 999.999.
     *
     * <p>Expressed as digit counts rather than a value because that is what {@code @Digits}
     * takes, and because it also pins the decimals: nothing stopped a caller from sending
     * four, which the column then rounded away without a word.
     */
    public static final int COUNTER_INTEGER_DIGITS = 3;

    /**
     * Warehouse line — purchases, transformations, transfers and stock adjustments.
     * Five integer digits and three decimals, so 99999.999.
     */
    public static final int WAREHOUSE_INTEGER_DIGITS = 5;

    /**
     * Three decimals everywhere: it is what {@code NUMERIC(12,3)} stores.
     *
     * <p>The column is deliberately <b>not</b> narrowed to match the ceilings above.
     * {@code transaction_details.quantity} holds sale <i>and</i> purchase lines in one
     * column, so with two ceilings it could carry neither. A column's precision is a storage
     * limit, not a business rule, and putting the rule there hides it in the one place it can
     * neither be read nor tested. And {@code ALTER COLUMN} rewrites the whole table under an
     * exclusive lock and fails outright if any existing row exceeds the new precision —
     * which, with no ceiling until now, cannot be known in advance.
     */
    public static final int FRACTION_DIGITS = 3;
}
