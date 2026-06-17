package org.dawid.cisowski.walletassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.awaitility.Awaitility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Shared
import spock.lang.Specification

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = [WalletAssistantApplication])
@ActiveProfiles("test")
abstract class BaseIntegrationSpec extends Specification {

    static final String TEST_DEVICE_ID = "test-device"
    static final String TEST_SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"  // "test-secret-123"
    static final String DIFFERENT_DEVICE_ID = "different-device-id"
    static final String DIFFERENT_SECRET_BASE64 = "ZGlmZmVyZW50LXNlY3JldC0xMjM="  // "different-secret-123"

    @LocalServerPort
    int port

    @Autowired
    JdbcTemplate jdbcTemplate

    @Shared
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)

    static {
        postgres.start()

        // Configure datasource to use Testcontainers
        System.setProperty("SPRING_DATASOURCE_URL", postgres.jdbcUrl)
        System.setProperty("SPRING_DATASOURCE_USERNAME", postgres.username)
        System.setProperty("SPRING_DATASOURCE_PASSWORD", postgres.password)

        // All device IDs used by test classes (all share base64 secret "test-secret-123",
        // except different-device-id which uses "different-secret-123").
        def devicesJson = """{"test-device":"dGVzdC1zZWNyZXQtMTIz","different-device-id":"ZGlmZmVyZW50LXNlY3JldC0xMjM=","test-expenses":"dGVzdC1zZWNyZXQtMTIz","test-accounts":"dGVzdC1zZWNyZXQtMTIz","test-investments":"dGVzdC1zZWNyZXQtMTIz"}"""
        System.setProperty("HMAC_DEVICES_JSON", devicesJson)
        System.setProperty("HMAC_TOLERANCE_SEC", "600")
    }

    def setup() {
        RestAssured.port = port
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
        jdbcTemplate.update("DELETE FROM event_publication WHERE completion_date IS NULL")
    }

    // HMAC signing helpers

    protected io.restassured.specification.RequestSpecification authenticatedPost(
            String deviceId, String secretBase64, String path, String body) {
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = sign("POST", path, timestamp, nonce, deviceId, body, secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }

    protected io.restassured.specification.RequestSpecification authenticatedGet(
            String deviceId, String secretBase64, String path) {
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = sign("GET", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
    }

    protected String submitEvent(String deviceId, String secretBase64, String eventType,
                                  String idempotencyKey, Map payload) {
        def body = groovy.json.JsonOutput.toJson([
                events: [[
                    idempotencyKey: idempotencyKey,
                    eventType     : eventType,
                    occurredAt    : "2026-01-15T12:00:00Z",
                    payload       : payload
                ]]
        ])

        return authenticatedPost(deviceId, secretBase64, "/v1/wallet-events", body)
                .post("/v1/wallet-events")
                .then()
                .statusCode(200)
                .extract()
                .body().jsonPath().getString("results[0].status")
    }

    /**
     * Submit an event and wait until the supplied read closure reports the projection
     * is visible. Projections are applied asynchronously after commit (Spring Modulith).
     */
    protected void awaitProjection(Closure<Boolean> condition) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until({ condition.call() } as java.util.concurrent.Callable<Boolean>)
    }

    protected static String sign(String method, String path, String timestamp, String nonce,
                                  String deviceId, String body, String secretBase64) {
        def canonical = "${method}\n${path}\n${timestamp}\n${nonce}\n${deviceId}\n${body}"
        def secret = Base64.decoder.decode(secretBase64)
        def mac = Mac.getInstance("HmacSHA256")
        mac.init(new SecretKeySpec(secret, "HmacSHA256"))
        return Base64.encoder.encodeToString(mac.doFinal(canonical.getBytes("UTF-8")))
    }

    protected static String generateTimestamp() {
        DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }

    protected static String generateNonce() {
        UUID.randomUUID().toString()
    }
}
