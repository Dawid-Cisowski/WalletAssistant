package org.dawid.cisowski.walletassistant.investments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface InvestmentsRepository extends JpaRepository<InvestmentSnapshotJpaEntity, Long> {

    Optional<InvestmentSnapshotJpaEntity> findByEventId(String eventId);

    List<InvestmentSnapshotJpaEntity> findByUserIdOrderByRecordedDateDesc(String userId);

    List<InvestmentSnapshotJpaEntity> findByUserIdAndInvestmentTypeOrderByRecordedDateDesc(String userId, String investmentType);

    Optional<InvestmentSnapshotJpaEntity> findFirstByUserIdAndInvestmentTypeOrderByRecordedDateDesc(String userId, String investmentType);
}
