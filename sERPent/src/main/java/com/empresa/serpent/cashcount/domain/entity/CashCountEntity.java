package com.empresa.serpent.cashcount.domain.entity;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.users.domain.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A shift's till count: what the drawer was believed to hold, against what was in it.
 *
 * <p>A PHOTO, not a period that gets locked. Nothing else in the system has to ask this
 * record for permission, and no transaction becomes read-only because a count exists after
 * it. The only thing a count does to the future is move the anchor: the next one for this
 * branch covers from this one's {@link #closedAt}.
 *
 * <p>THE RULE THAT MUST NOT BE BROKEN: the expected amounts are frozen in here at close
 * time and never recomputed. This says what the till was believed to hold at that moment.
 * If a correction later changes what the same query would answer today, this record must
 * not move with it — which is also why the lines carry their own copy of the method's name
 * and cash flag instead of reading them through the foreign key.
 *
 * <p>Scope is per BRANCH, not per terminal. Two cashiers sharing a branch share one anchor,
 * so the second count of a shift sweeps in whatever the first already counted. Deliberate:
 * today there is one terminal per branch, and tying the count to a terminal before that
 * case exists would model a problem nobody has.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "cash_counts")
public class CashCountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_count_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private WarehouseEntity warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserEntity createdByUserEntity;

    /** The moment counted, and the anchor the next count of this branch reads. */
    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    /**
     * Where this count started summing.
     *
     * <p>NULL means "from the first record there is", which is what a branch's very first
     * count covers. Stored rather than re-derived from the previous count so the record
     * still explains itself years from now, whatever the anchoring logic looks like then.
     */
    @Column(name = "period_from")
    private LocalDateTime periodFrom;

    /**
     * Cash left in the drawer at the start of the shift to make change.
     *
     * <p>Already included in the cash line's expected amount; kept on its own because it is
     * the one figure the cashier typed rather than the system deriving it. Zero is a real
     * answer, not a missing one.
     */
    @Column(name = "opening_float", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingFloat;

    /**
     * Money that moved in the period but belongs to no payment method: returns and expenses
     * recorded before the method was asked for.
     *
     * <p>Deliberately NOT folded into any line — nobody knows which method they belong to,
     * and putting them somewhere would be inventing it. Kept here so a count whose numbers
     * looked off still says why, long after the rows themselves are forgotten.
     */
    @Builder.Default
    @Column(name = "unattributed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unattributedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "unattributed_count", nullable = false)
    private Integer unattributedCount = 0;

    @Column(name = "note")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "cashCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CashCountLineEntity> lines = new ArrayList<>();
}
