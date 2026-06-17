package org.dawid.cisowski.walletassistant.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface AccountsRepository extends JpaRepository<BalanceSnapshotJpaEntity, Long> {

    Optional<BalanceSnapshotJpaEntity> findByEventId(String eventId);

    List<BalanceSnapshotJpaEntity> findByUserIdOrderByRecordedDateDesc(String userId);

    Optional<BalanceSnapshotJpaEntity> findFirstByUserIdAndAccountTypeOrderByRecordedDateDesc(String userId, String accountType);
}
