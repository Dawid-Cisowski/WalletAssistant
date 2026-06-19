package org.dawid.cisowski.walletassistant.walletevents;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Wallet Events", description = "Submit financial events to the event store (write side)")
@RestController
@RequestMapping("/v1/wallet-events")
@RequiredArgsConstructor
class WalletEventsController {

    private final WalletEventsFacade walletEventsFacade;

    @Operation(
            summary = "Submit wallet events",
            description = """
                    Stores one or more financial events. Supported event types:
                    - `ExpenseRecorded.v1` — new expense
                    - `ExpenseCorrected.v1` — correct an existing expense
                    - `ExpenseDeleted.v1` — delete an expense
                    - `AccountBalanceSnapshotRecorded.v1` — account balance snapshot
                    - `AssetPositionOpened.v1` — open an asset position (purchase)
                    - `AssetPositionClosed.v1` — close an asset position (sale, supports partial)
                    - `AssetPriceSnapshotRecorded.v1` — record market price for an asset symbol
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events processed — check each result status (STORED/DUPLICATE/INVALID)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid HMAC / API-key authentication")
    })
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

    @Schema(description = "Batch of events to submit")
    record SubmitEventsRequest(
            @Schema(description = "One or more event envelopes") List<EventEnvelope> events
    ) {
    }

    @Schema(description = "Single event envelope")
    record EventEnvelope(
            @Schema(description = "Client-generated unique key; duplicate submissions are deduplicated", example = "550e8400-e29b-41d4-a716-446655440000")
            String idempotencyKey,
            @Schema(description = "Event type name, e.g. ExpenseRecorded.v1", example = "AssetPositionOpened.v1")
            String eventType,
            @Schema(description = "When the business event occurred (ISO-8601)", example = "2026-01-15T12:00:00Z")
            Instant occurredAt,
            @Schema(description = "Event-specific payload fields")
            Map<String, Object> payload
    ) {
    }
}
