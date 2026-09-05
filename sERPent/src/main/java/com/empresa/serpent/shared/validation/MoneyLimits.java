package com.empresa.serpent.shared.validation;

/**
 * How much money a single field may carry, and why the slack runs the other way than it does
 * for quantities.
 *
 * <h2>WHY THIS EXISTS</h2>
 *
 * <p>Until this class was written there was <b>no upper bound on any amount anywhere</b>:
 * seven front-end controls with {@code required} and {@code min} but no {@code max}, and
 * <b>zero {@code @Digits} on a money field</b> in the whole backend — the eight that existed
 * were all quantities. The only ceiling was the column's {@code NUMERIC(19,4)}, fifteen
 * integer digits.
 *
 * <p>It is the same hole quantities had, found the same way: going to declare the width of
 * the cart's Price column, that width had to derive from a ceiling and the ceiling was not
 * there. A column's precision is a storage limit, not a business rule — already written in
 * {@link QuantityLimits} and just as true here.
 *
 * <h2>THE SLACK RUNS THE OTHER WAY</h2>
 *
 * <p>For a quantity the risk is the TYPO: the cashier who types 100 where 1,00 belonged, and
 * a tight ceiling catches it. That is why the counter ceiling is 999.999 and no more.
 *
 * <p>For an amount the risk is the opposite. A low ceiling BLOCKS A LEGITIMATE SALE the day
 * prices reach it, and with Argentine inflation that day arrives on its own. Raising it
 * afterwards costs code, a migration and a deploy, with the shop unable to sell meanwhile. A
 * high ceiling only costs pixels of column width.
 *
 * <p><b>Err upwards.</b>
 *
 * <h2>WHERE THE NUMBER COMES FROM</h2>
 *
 * <p>The dearest product in the catalogue today is $17.000 per kilo: five digits. Seven
 * covers nearly three orders of magnitude over that, which at the rate of recent years is
 * several years of headroom.
 *
 * <p>It is a declared business decision, not a measurement.
 *
 * <h2>WHAT THIS ANNOTATION IS AND IS NOT FOR</h2>
 *
 * <p>Same as {@link QuantityLimits}: it is a <b>contract guard</b>, not operator feedback.
 * {@code GlobalExceptionHandler} puts bean-validation messages under {@code details} and
 * returns a generic {@code message}, and the front end's error interceptor reads only
 * {@code message} — so the text below never reaches a cashier. What it covers is every
 * caller that does not go through the form: direct API use, the offline sync path, and
 * future clients. The message the operator reads comes from the front-end validators, whose
 * single source of truth is {@code shared/forms/techos-de-importe.ts}; these constants
 * mirror it because {@code @Digits} needs compile-time literals.
 */
public final class MoneyLimits {

    private MoneyLimits() {}

    /**
     * Seven integer digits, so 9.999.999,99.
     *
     * <p>One ceiling for every typed amount and not one per screen, unlike quantities: there
     * the split was real —what leaves one at a time versus what arrives by the pallet— and
     * here there is no reason for a product's price to admit a different order of magnitude
     * than an expense or a payment.
     */
    public static final int INTEGER_DIGITS = 7;

    /**
     * Two decimals: it is what a peso amount has, and what every screen formats.
     *
     * <p>The column is {@code NUMERIC(19,4)} and stays that way. Four decimals is where a
     * per-line surcharge lands before rounding, so narrowing the column to two would round
     * away a calculation the app deliberately keeps at full precision. As with quantities,
     * the column's precision is storage and this is the rule.
     */
    public static final int FRACTION_DIGITS = 2;
}
