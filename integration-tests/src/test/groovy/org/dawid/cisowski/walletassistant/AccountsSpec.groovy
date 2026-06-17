package org.dawid.cisowski.walletassistant

import spock.lang.Title

@Title("Feature: Account Balance Tracking")
class AccountsSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-accounts"
    static final String SECRET = TEST_SECRET_BASE64

    def setup() {
        jdbcTemplate.update("DELETE FROM account_balance_snapshots WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM account_balance_snapshots WHERE user_id = ?", DIFFERENT_DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DIFFERENT_DEVICE_ID)
    }

    private Map snapshotPayload(Map overrides) {
        def base = [
                snapshotId : UUID.randomUUID().toString(),
                accountType: "BUSINESS",
                accountName: "Firmowe mBank",
                balance    : 15000.00,
                currency   : "PLN",
                recordedAt : "2026-01-15T10:00:00Z"
        ]
        base.putAll(overrides)
        return base
    }

    private void submitSnapshot(String deviceId, String secret, Map overrides) {
        submitEvent(deviceId, secret, "ACCOUNT_BALANCE_SNAPSHOT_RECORDED", UUID.randomUUID().toString(), snapshotPayload(overrides))
    }

    private def getBalances(String deviceId = DEVICE_ID, String secret = SECRET) {
        return authenticatedGet(deviceId, secret, "/v1/accounts/balances").get("/v1/accounts/balances")
    }

    private static double balanceFor(def json, String accountType) {
        def list = json.getList("findAll { it.accountType == '${accountType}' }.balance")
        return (list == null || list.isEmpty()) ? Double.NaN : (list[0] as double)
    }

    def "recording account balance snapshot makes it visible in current balances"() {
        given: "a BUSINESS account balance snapshot"
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "BUSINESS", accountName: "Firmowe mBank", balance: 15000.00])

        and: "the projection is applied"
        awaitProjection { !Double.isNaN(balanceFor(getBalances().jsonPath(), "BUSINESS")) }

        when: "current balances are requested"
        def response = getBalances()

        then: "the BUSINESS account shows the recorded balance"
        response.statusCode() == 200
        balanceFor(response.jsonPath(), "BUSINESS") == 15000.0
    }

    def "recording second snapshot updates the balance"() {
        given: "an initial BUSINESS snapshot at 15000 PLN"
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "BUSINESS", balance: 15000.00, recordedAt: "2026-01-15T10:00:00Z"])
        awaitProjection { balanceFor(getBalances().jsonPath(), "BUSINESS") == 15000.0 }

        when: "a later BUSINESS snapshot of 16000 PLN is recorded"
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "BUSINESS", balance: 16000.00, recordedAt: "2026-01-16T10:00:00Z"])
        awaitProjection { balanceFor(getBalances().jsonPath(), "BUSINESS") == 16000.0 }
        def response = getBalances()

        then: "the current balance reflects the latest snapshot"
        response.statusCode() == 200
        balanceFor(response.jsonPath(), "BUSINESS") == 16000.0
    }

    def "different account types are tracked separately"() {
        given: "a BUSINESS snapshot and a PERSONAL_SAVINGS snapshot"
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "BUSINESS", accountName: "Firmowe mBank", balance: 15000.00])
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "PERSONAL_SAVINGS", accountName: "Oszczednosci", balance: 50000.00])

        and: "both projections are applied"
        awaitProjection {
            def json = getBalances().jsonPath()
            !Double.isNaN(balanceFor(json, "BUSINESS")) && !Double.isNaN(balanceFor(json, "PERSONAL_SAVINGS"))
        }

        when: "current balances are requested"
        def response = getBalances()

        then: "both accounts appear with their own balances"
        response.statusCode() == 200
        def json = response.jsonPath()
        balanceFor(json, "BUSINESS") == 15000.0
        balanceFor(json, "PERSONAL_SAVINGS") == 50000.0
    }

    def "balance history for account type shows all snapshots"() {
        given: "three PERSONAL_SAVINGS snapshots at different dates"
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "PERSONAL_SAVINGS", balance: 50000.00, recordedAt: "2026-01-10T10:00:00Z"])
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "PERSONAL_SAVINGS", balance: 51000.00, recordedAt: "2026-02-10T10:00:00Z"])
        submitSnapshot(DEVICE_ID, SECRET, [accountType: "PERSONAL_SAVINGS", balance: 52000.00, recordedAt: "2026-03-10T10:00:00Z"])

        and: "all three projections are applied"
        def historyPath = "/v1/accounts/PERSONAL_SAVINGS/history"
        awaitProjection {
            authenticatedGet(DEVICE_ID, SECRET, historyPath).get(historyPath).jsonPath().getList("").size() == 3
        }

        when: "the history for PERSONAL_SAVINGS is requested"
        def response = authenticatedGet(DEVICE_ID, SECRET, historyPath).get(historyPath)

        then: "all three snapshots are returned"
        response.statusCode() == 200
        response.jsonPath().getList("").size() == 3
    }

    def "balances from other users are not visible"() {
        given: "a BUSINESS snapshot for a different device"
        submitSnapshot(DIFFERENT_DEVICE_ID, DIFFERENT_SECRET_BASE64, [accountType: "BUSINESS", accountName: "Other Business", balance: 99999.00])

        and: "the other user's projection is applied"
        awaitProjection { !Double.isNaN(balanceFor(getBalances(DIFFERENT_DEVICE_ID, DIFFERENT_SECRET_BASE64).jsonPath(), "BUSINESS")) }

        when: "the test device requests its balances"
        def response = getBalances()

        then: "the other user's account is not visible"
        response.statusCode() == 200
        def names = response.jsonPath().getList("accountName")
        names == null || !names.contains("Other Business")
    }
}
