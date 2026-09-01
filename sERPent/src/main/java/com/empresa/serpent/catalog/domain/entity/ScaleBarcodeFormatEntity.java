package com.empresa.serpent.catalog.domain.entity;

import com.empresa.serpent.catalog.domain.enums.ScaleValueType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * How one scale in this shop lays out the barcode it prints on a label.
 *
 * <p>WHY THIS IS CONFIGURATION AND NOT CODE: GS1 reserves the prefix 2 for the shop's own
 * internal use, which means there is no standard to implement. Every brand splits the 13
 * digits its own way, and a shop can have scales from two brands at once. Hardcoding the
 * layout of the one scale we happen to have seen would be a guess that fails silently on
 * the next one — and failing silently here means charging the wrong weight.
 *
 * <p>The label of the shop's Kretz RPL US30P2CAR reads 2-5-5, which lands here as:
 * prefix "2", total length 13, product code at position 2 for 6 digits, value at position
 * 8 for 5 digits, WEIGHT with 3 decimals (so 00560 means 0,560 kg), check digit validated.
 *
 * <p>Positions are 1-BASED, counted from the left, because that is how the scale's own
 * manual numbers them and this table is meant to be filled in with the manual open.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "scale_barcode_formats")
public class ScaleBarcodeFormatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scale_barcode_format_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** Leading digits that identify a label as coming from this scale. */
    @Column(name = "prefix", nullable = false, length = 4)
    private String prefix;

    @Column(name = "total_length", nullable = false)
    private Integer totalLength;

    @Column(name = "product_code_start", nullable = false)
    private Integer productCodeStart;

    @Column(name = "product_code_length", nullable = false)
    private Integer productCodeLength;

    @Column(name = "value_start", nullable = false)
    private Integer valueStart;

    @Column(name = "value_length", nullable = false)
    private Integer valueLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private ScaleValueType valueType;

    /**
     * Implied decimals in the value field: 5 digits of grams read as kilograms is 3.
     * Kept separate from the type so that "grams" and "cents" are the same rule with a
     * different number, instead of two branches in the decoder.
     */
    @Column(name = "value_decimals", nullable = false)
    private Integer valueDecimals;

    /**
     * Whether the last digit is a standard EAN check digit. Default on: both real labels
     * from the shop's scale carry a correct one, and it is the only defence against a
     * misread digit in the WEIGHT field, which would otherwise resolve to a valid product
     * at ten times the weight. Configurable because some in-store layouts use that digit
     * as data.
     */
    @Builder.Default
    @Column(name = "validate_check_digit", nullable = false)
    private Boolean validateCheckDigit = true;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
