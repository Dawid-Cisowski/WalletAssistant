package org.dawid.cisowski.walletassistant.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface AssetPriceHistoryRepository extends JpaRepository<AssetPriceHistoryJpaEntity, Long> {

    Optional<AssetPriceHistoryJpaEntity> findByEventId(String eventId);

    Optional<AssetPriceHistoryJpaEntity> findTopByUserIdAndAssetSymbolOrderByPriceDateDesc(String userId, String assetSymbol);

    List<AssetPriceHistoryJpaEntity> findByUserIdAndAssetSymbolOrderByPriceDateDesc(String userId, String assetSymbol);
}
