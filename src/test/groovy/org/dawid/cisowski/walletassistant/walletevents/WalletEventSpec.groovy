package org.dawid.cisowski.walletassistant.walletevents

import org.dawid.cisowski.walletassistant.walletevents.api.EventType
import spock.lang.Specification
import spock.lang.Title

import java.time.Instant

@Title("WalletEvent value object")
class WalletEventSpec extends Specification {

    static final String EVENT_ID = "event-1"
    static final String IDEMPOTENCY_KEY = "idem-1"
    static final EventType EVENT_TYPE = EventType.EXPENSE_RECORDED
    static final Instant OCCURRED_AT = Instant.parse("2025-01-15T10:00:00Z")
    static final Map<String, Object> PAYLOAD = [amount: "100", currency: "PLN"]
    static final String USER_ID = "user-1"
    static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:01Z")

    def "should throw NullPointerException when required field '#fieldName' is null"() {
        when: "a WalletEvent is constructed with a null required field"
        new WalletEvent(eventId, idempotencyKey, eventType, occurredAt, payload, userId, createdAt, null, null)

        then: "construction fails with a descriptive NullPointerException"
        def exception = thrown(NullPointerException)
        exception.message == "${fieldName} must not be null"

        where:
        fieldName         | eventId  | idempotencyKey  | eventType  | occurredAt  | payload  | userId  | createdAt
        "eventId"         | null     | IDEMPOTENCY_KEY | EVENT_TYPE | OCCURRED_AT | PAYLOAD  | USER_ID | CREATED_AT
        "idempotencyKey"  | EVENT_ID | null            | EVENT_TYPE | OCCURRED_AT | PAYLOAD  | USER_ID | CREATED_AT
        "eventType"       | EVENT_ID | IDEMPOTENCY_KEY | null       | OCCURRED_AT | PAYLOAD  | USER_ID | CREATED_AT
        "occurredAt"      | EVENT_ID | IDEMPOTENCY_KEY | EVENT_TYPE | null        | PAYLOAD  | USER_ID | CREATED_AT
        "payload"         | EVENT_ID | IDEMPOTENCY_KEY | EVENT_TYPE | OCCURRED_AT | null     | USER_ID | CREATED_AT
        "userId"          | EVENT_ID | IDEMPOTENCY_KEY | EVENT_TYPE | OCCURRED_AT | PAYLOAD  | null    | CREATED_AT
        "createdAt"       | EVENT_ID | IDEMPOTENCY_KEY | EVENT_TYPE | OCCURRED_AT | PAYLOAD  | USER_ID | null
    }

    def "should be active when neither deletedByEventId nor supersededByEventId is set"() {
        given: "an event with no deletion or supersession markers"
        def event = new WalletEvent(EVENT_ID, IDEMPOTENCY_KEY, EVENT_TYPE, OCCURRED_AT, PAYLOAD, USER_ID, CREATED_AT, null, null)

        expect: "the event reports itself as active"
        event.isActive()
    }

    def "should not be active when deletedByEventId is set"() {
        given: "an event marked as deleted by another event"
        def event = new WalletEvent(EVENT_ID, IDEMPOTENCY_KEY, EVENT_TYPE, OCCURRED_AT, PAYLOAD, USER_ID, CREATED_AT, "deletion-event", null)

        expect: "the event reports itself as inactive"
        !event.isActive()
    }

    def "should not be active when supersededByEventId is set"() {
        given: "an event superseded by another event"
        def event = new WalletEvent(EVENT_ID, IDEMPOTENCY_KEY, EVENT_TYPE, OCCURRED_AT, PAYLOAD, USER_ID, CREATED_AT, null, "superseding-event")

        expect: "the event reports itself as inactive"
        !event.isActive()
    }

    def "should create an event with a generated id, a creation timestamp and all provided fields"() {
        given: "the moment just before creation"
        def beforeCreation = Instant.now()

        when: "an event is created via the factory method"
        def event = WalletEvent.create(IDEMPOTENCY_KEY, EVENT_TYPE, OCCURRED_AT, PAYLOAD, USER_ID)

        then: "a non-null eventId is generated"
        event.eventId() != null
        !event.eventId().isBlank()

        and: "createdAt is set to the current time"
        event.createdAt() != null
        !event.createdAt().isBefore(beforeCreation)
        !event.createdAt().isAfter(Instant.now())

        and: "all provided fields are set correctly"
        event.idempotencyKey() == IDEMPOTENCY_KEY
        event.eventType() == EVENT_TYPE
        event.occurredAt() == OCCURRED_AT
        event.payload() == PAYLOAD
        event.userId() == USER_ID

        and: "the event starts out active with no deletion or supersession markers"
        event.deletedByEventId() == null
        event.supersededByEventId() == null
        event.isActive()
    }

    def "should generate a unique eventId for each created event"() {
        when: "two events are created with identical input"
        def first = WalletEvent.create(IDEMPOTENCY_KEY, EVENT_TYPE, OCCURRED_AT, PAYLOAD, USER_ID)
        def second = WalletEvent.create(IDEMPOTENCY_KEY, EVENT_TYPE, OCCURRED_AT, PAYLOAD, USER_ID)

        then: "their generated event ids differ"
        first.eventId() != second.eventId()
    }
}
