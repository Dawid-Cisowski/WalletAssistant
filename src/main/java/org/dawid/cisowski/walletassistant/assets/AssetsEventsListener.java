package org.dawid.cisowski.walletassistant.assets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.AssetsStoredEvent;
import org.dawid.cisowski.walletassistant.walletevents.api.StoredEventData;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AssetsEventsListener {

    private final AssetsProjector projector;

    @ApplicationModuleListener
    public void onAssetsStored(AssetsStoredEvent event) {
        event.events().forEach(this::route);
    }

    private void route(StoredEventData event) {
        switch (event.eventType()) {
            case ASSET_POSITION_OPENED -> projector.projectPositionOpened(event);
            case ASSET_POSITION_CLOSED -> projector.projectPositionClosed(event);
            case ASSET_PRICE_SNAPSHOT_RECORDED -> projector.projectPriceSnapshot(event);
            default -> log.warn("Unexpected event type in assets listener: {}", event.eventType());
        }
    }
}
