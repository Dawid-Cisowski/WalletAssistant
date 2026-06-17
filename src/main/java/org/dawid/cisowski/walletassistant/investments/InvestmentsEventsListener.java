package org.dawid.cisowski.walletassistant.investments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.EventType;
import org.dawid.cisowski.walletassistant.walletevents.api.InvestmentSnapshotsStoredEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InvestmentsEventsListener {

    private final InvestmentsProjector projector;

    @ApplicationModuleListener
    public void onInvestmentSnapshotsStored(InvestmentSnapshotsStoredEvent event) {
        event.events().stream()
                .filter(stored -> stored.eventType() == EventType.INVESTMENT_SNAPSHOT_RECORDED)
                .forEach(projector::projectSnapshot);
    }
}
