package org.dawid.cisowski.walletassistant.accounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dawid.cisowski.walletassistant.walletevents.api.AccountSnapshotsStoredEvent;
import org.dawid.cisowski.walletassistant.walletevents.api.EventType;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AccountsEventsListener {

    private final AccountsProjector projector;

    @ApplicationModuleListener
    public void onAccountSnapshotsStored(AccountSnapshotsStoredEvent event) {
        event.events().stream()
                .filter(stored -> stored.eventType() == EventType.ACCOUNT_BALANCE_SNAPSHOT_RECORDED)
                .forEach(projector::projectSnapshot);
    }
}
