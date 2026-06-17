package org.dawid.cisowski.walletassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.Instant
import java.time.format.DateTimeFormatter

@Title("Feature: HMAC Authentication Security")
class HmacAuthenticationSpec extends BaseIntegrationSpec {

    static final String EVENTS_PATH = "/v1/wallet-events"

    static final String VALID_EVENTS_BODY = '''
{
    "events": [{
        "idempotencyKey": "hmac-test-static-key-1",
        "eventType": "EXPENSE_RECORDED",
        "occurredAt": "2026-01-15T12:00:00Z",
        "payload": {
            "expenseId": "exp-hmac-test-1",
            "amount": 26.00,
            "currency": "PLN",
            "category": "FOOD_AND_DRINKS",
            "description": "Test expense",
            "accountType": "PERSONAL_SPENDING",
            "occurredAt": "2026-01-15T12:00:00Z"
        }
    }]
}
'''

    def "POST without X-Device-Id returns 401 with HMAC_AUTH_FAILED"() {
        given: "a request missing the X-Device-Id header"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
        response.body().asString().contains("HMAC_AUTH_FAILED")
    }

    def "POST without X-Timestamp returns 401"() {
        given: "a request missing the X-Timestamp header"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "POST without X-Nonce returns 401"() {
        given: "a request missing the X-Nonce header"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "POST without X-Signature returns 401"() {
        given: "a request missing the X-Signature header"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "POST with no auth headers returns 401"() {
        when: "a request is submitted with no authentication headers"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "POST with invalid signature returns 401 with invalid signature message"() {
        given: "a request with a bogus signature"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", "this-is-not-a-valid-signature")
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized for a signature reason"
        response.statusCode() == 401
        response.body().asString().toLowerCase().contains("signature")
    }

    def "POST with signature for different body returns 401"() {
        given: "a signature computed over a different body than is actually sent"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signedBody = '{"events": []}'
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, signedBody, TEST_SECRET_BASE64)

        when: "the request is sent with a tampered body"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "POST with expired timestamp (700s old) returns 401 with timestamp message"() {
        given: "a request signed with a timestamp 700 seconds in the past"
        def timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().minusSeconds(700))
        def nonce = generateNonce()
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected for a timestamp reason"
        response.statusCode() == 401
        response.body().asString().toLowerCase().contains("timestamp")
    }

    def "POST with timestamp too far in future (700s) returns 401"() {
        given: "a request signed with a timestamp 700 seconds in the future"
        def timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(700))
        def nonce = generateNonce()
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "POST with unknown device ID returns 401"() {
        given: "a request from a device ID not registered in the HMAC device store"
        def unknownDevice = "ghost-device-never-registered"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, unknownDevice, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "the request is submitted"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", unknownDevice)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(EVENTS_PATH)

        then: "the request is rejected as unauthorized"
        response.statusCode() == 401
    }

    def "Replayed nonce returns 401 on second request"() {
        given: "a fully valid request reusing a single timestamp/nonce/signature"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def body = groovy.json.JsonOutput.toJson([
                events: [[
                    idempotencyKey: "hmac-replay-" + UUID.randomUUID().toString(),
                    eventType     : "EXPENSE_RECORDED",
                    occurredAt    : "2026-01-15T12:00:00Z",
                    payload       : [
                        expenseId  : "exp-replay-" + UUID.randomUUID().toString(),
                        amount     : 26.00,
                        currency   : "PLN",
                        category   : "FOOD_AND_DRINKS",
                        description: "Replay test",
                        accountType: "PERSONAL_SPENDING",
                        occurredAt : "2026-01-15T12:00:00Z"
                    ]
                ]]
        ])
        def signature = sign("POST", EVENTS_PATH, timestamp, nonce, TEST_DEVICE_ID, body, TEST_SECRET_BASE64)

        def buildRequest = {
            RestAssured.given()
                    .contentType(ContentType.JSON)
                    .header("X-Device-Id", TEST_DEVICE_ID)
                    .header("X-Timestamp", timestamp)
                    .header("X-Nonce", nonce)
                    .header("X-Signature", signature)
                    .body(body)
        }

        when: "the first request is submitted"
        def first = buildRequest().post(EVENTS_PATH)

        then: "it is accepted"
        first.statusCode() == 200

        when: "the identical request (same nonce) is replayed"
        def second = buildRequest().post(EVENTS_PATH)

        then: "the replay is rejected as unauthorized"
        second.statusCode() == 401
    }

    def "Valid POST request returns 200 with STORED status"() {
        given: "a correctly signed request with a unique idempotency key"
        def body = groovy.json.JsonOutput.toJson([
                events: [[
                    idempotencyKey: "hmac-valid-" + UUID.randomUUID().toString(),
                    eventType     : "EXPENSE_RECORDED",
                    occurredAt    : "2026-01-15T12:00:00Z",
                    payload       : [
                        expenseId  : "exp-valid-" + UUID.randomUUID().toString(),
                        amount     : 26.00,
                        currency   : "PLN",
                        category   : "FOOD_AND_DRINKS",
                        description: "Valid test",
                        accountType: "PERSONAL_SPENDING",
                        occurredAt : "2026-01-15T12:00:00Z"
                    ]
                ]]
        ])

        when: "the request is submitted"
        def response = authenticatedPost(TEST_DEVICE_ID, TEST_SECRET_BASE64, EVENTS_PATH, body)
                .post(EVENTS_PATH)

        then: "the event is accepted and stored"
        response.statusCode() == 200
        response.jsonPath().getString("results[0].status") == "STORED"
    }

    def "Unauthenticated GET /actuator/health returns 200"() {
        when: "the public health endpoint is requested without any auth headers"
        def response = RestAssured.given()
                .get("/actuator/health")

        then: "it responds successfully"
        response.statusCode() == 200
    }
}
