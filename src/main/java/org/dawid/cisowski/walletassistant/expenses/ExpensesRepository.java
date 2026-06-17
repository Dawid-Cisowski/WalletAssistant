package org.dawid.cisowski.walletassistant.expenses;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface ExpensesRepository extends JpaRepository<ExpenseProjectionJpaEntity, Long> {

    Optional<ExpenseProjectionJpaEntity> findByEventId(String eventId);

    Optional<ExpenseProjectionJpaEntity> findByExpenseId(String expenseId);

    List<ExpenseProjectionJpaEntity> findByUserIdAndOccurredDateBetween(String userId, LocalDate from, LocalDate to);

    List<ExpenseProjectionJpaEntity> findByUserIdAndCategoryAndOccurredDateBetween(
            String userId,
            String category,
            LocalDate from,
            LocalDate to
    );

    void deleteByExpenseId(String expenseId);
}
