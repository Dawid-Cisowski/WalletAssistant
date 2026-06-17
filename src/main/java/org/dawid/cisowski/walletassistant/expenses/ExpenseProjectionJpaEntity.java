package org.dawid.cisowski.walletassistant.expenses;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expense_projections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ExpenseProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false, unique = true)
    private String expenseId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "merchant")
    private String merchant;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "occurred_date", nullable = false)
    private LocalDate occurredDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private ExpenseProjectionJpaEntity(
            String expenseId,
            String eventId,
            String userId,
            BigDecimal amount,
            String currency,
            String category,
            String description,
            String merchant,
            String accountType,
            Instant occurredAt,
            LocalDate occurredDate
    ) {
        this.expenseId = expenseId;
        this.eventId = eventId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.description = description;
        this.merchant = merchant;
        this.accountType = accountType;
        this.occurredAt = occurredAt;
        this.occurredDate = occurredDate;
    }

    static ExpenseProjectionJpaEntity from(Expense expense) {
        return new ExpenseProjectionJpaEntity(
                expense.expenseId(),
                expense.eventId(),
                expense.userId(),
                expense.amount(),
                expense.currency(),
                expense.category().name(),
                expense.description(),
                expense.merchant(),
                expense.accountType(),
                expense.occurredAt(),
                expense.occurredDate()
        );
    }

    void applyCorrection(Expense expense) {
        this.eventId = expense.eventId();
        this.amount = expense.amount();
        this.currency = expense.currency();
        this.category = expense.category().name();
        this.description = expense.description();
        this.merchant = expense.merchant();
        this.accountType = expense.accountType();
        this.occurredAt = expense.occurredAt();
        this.occurredDate = expense.occurredDate();
    }

    String getExpenseId() {
        return expenseId;
    }

    String getEventId() {
        return eventId;
    }

    String getUserId() {
        return userId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    String getCategory() {
        return category;
    }

    String getDescription() {
        return description;
    }

    String getMerchant() {
        return merchant;
    }

    String getAccountType() {
        return accountType;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    LocalDate getOccurredDate() {
        return occurredDate;
    }

    Long getVersion() {
        return version;
    }
}
