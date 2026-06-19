package org.dawid.cisowski.walletassistant.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface AssetPositionsRepository extends JpaRepository<AssetPositionJpaEntity, Long> {

    Optional<AssetPositionJpaEntity> findByEventId(String eventId);

    Optional<AssetPositionJpaEntity> findByPositionId(String positionId);

    List<AssetPositionJpaEntity> findByUserIdAndStatusOrderByPurchasedAtDesc(String userId, String status);

    List<AssetPositionJpaEntity> findByUserIdOrderByPurchasedAtDesc(String userId);
}
