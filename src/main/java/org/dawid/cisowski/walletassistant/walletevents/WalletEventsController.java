package org.dawid.cisowski.walletassistant.walletevents;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade.StoreEventsCommand;
import org.dawid.cisowski.walletassistant.walletevents.api.WalletEventsFacade.StoreEventsResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/wallet-events")
@RequiredArgsConstructor
class WalletEventsController {

    private final WalletEventsFacade walletEventsFacade;

    @PostMapping
    ResponseEntity<StoreEventsResult> submitEvents(
            @RequestBody SubmitEventsRequest request,
            HttpServletRequest httpRequest
    ) {
        return Optional.ofNullable((String) httpRequest.getAttribute("deviceId"))
                .map(userId -> store(userId, request))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private ResponseEntity<StoreEventsResult> store(String userId, SubmitEventsRequest request) {
        var envelopes = request.events().stream()
                .map(event -> new WalletEventsFacade.EventEnvelope(
                        event.idempotencyKey(),
                        event.eventType(),
                        event.occurredAt(),
                        event.payload()))
                .toList();
        return ResponseEntity.ok(walletEventsFacade.storeEvents(new StoreEventsCommand(userId, envelopes)));
    }

    record SubmitEventsRequest(List<EventEnvelope> events) {
    }

    record EventEnvelope(
            String idempotencyKey,
            String eventType,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
    }
}
