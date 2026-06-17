package org.dawid.cisowski.walletassistant

import spock.lang.Title

@Title("Feature: Investment Portfolio Tracking")
class InvestmentsSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-investments"
    static final String SECRET = TEST_SECRET_BASE64

    def setup() {
        jdbcTemplate.update("DELETE FROM investment_snapshots WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM investment_snapshots WHERE user_id = ?", DIFFERENT_DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DIFFERENT_DEVICE_ID)
    }

    private Map snapshotPayload(Map overrides) {
        def base = [
                snapshotId    : UUID.randomUUID().toString(),
                investmentType: "IKE",
                investmentName: "IKE XTB",
                currentValue  : 25000.00,
                investedAmount: 20000.00,
                currency      : "PLN",
                recordedAt    : "2026-01-15T10:00:00Z"
        ]
        base.putAll(overrides)
        return base
    }

    private void submitSnapshot(String deviceId, String secret, Map overrides) {
        submitEvent(deviceId, secret, "INVESTMENT_SNAPSHOT_RECORDED", UUID.randomUUID().toString(), snapshotPayload(overrides))
    }

    private def getPortfolio(String deviceId = DEVICE_ID, String secret = SECRET) {
        return authenticatedGet(deviceId, secret, "/v1/investments/portfolio").get("/v1/investments/portfolio")
    }

    private static Map investmentEntry(def json, String type) {
        def list = json.getList("investments.findAll { it.investmentType == '${type}' }")
        return (list == null || list.isEmpty()) ? null : (list[0] as Map)
    }

    def "recording investment snapshot makes it visible in portfolio"() {
        given: "an IKE investment snapshot"
        submitSnapshot(DEVICE_ID, SECRET, [investmentType: "IKE", investmentName: "IKE XTB", currentValue: 25000.00, investedAmount: 20000.00])

        and: "the projection is applied"
        awaitProjection { investmentEntry(getPortfolio().jsonPath(), "IKE") != null }

        when: "the portfolio is requested"
        def response = getPortfolio()

        then: "the IKE entry shows current value and gain/loss"
        response.statusCode() == 200
        def ike = investmentEntry(response.jsonPath(), "IKE")
        (ike.currentValue as double) == 25000.0
        (ike.gainLoss as double) == 5000.0
    }

    def "portfolio gain/loss percentage is correctly calculated"() {
        given: "an IKE snapshot with 25000 current value over 20000 invested"
        submitSnapshot(DEVICE_ID, SECRET, [investmentType: "IKE", currentValue: 25000.00, investedAmount: 20000.00])

        and: "the projection is applied"
        awaitProjection { investmentEntry(getPortfolio().jsonPath(), "IKE") != null }

        when: "the portfolio is requested"
        def response = getPortfolio()

        then: "the gain/loss percentage is 25 percent"
        response.statusCode() == 200
        def ike = investmentEntry(response.jsonPath(), "IKE")
        (ike.gainLossPercent as double) == 25.0
    }

    def "totalValue and totalInvested aggregate across investment types"() {
        given: "an IKE snapshot and an XTB_STOCKS snapshot"
        submitSnapshot(DEVICE_ID, SECRET, [investmentType: "IKE", investmentName: "IKE XTB", currentValue: 25000.00, investedAmount: 20000.00])
        submitSnapshot(DEVICE_ID, SECRET, [investmentType: "XTB_STOCKS", investmentName: "XTB Stocks", currentValue: 10000.00, investedAmount: 8000.00])

        and: "both projections are applied"
        awaitProjection {
            def json = getPortfolio().jsonPath()
            investmentEntry(json, "IKE") != null && investmentEntry(json, "XTB_STOCKS") != null
        }

        when: "the portfolio is requested"
        def response = getPortfolio()

        then: "the totals aggregate both investment types"
        response.statusCode() == 200
        def json = response.jsonPath()
        json.getDouble("totalValue") == 35000.0
        json.getDouble("totalInvested") == 28000.0
    }

    def "investment history for type shows all snapshots"() {
        given: "two IKE snapshots at different dates"
        submitSnapshot(DEVICE_ID, SECRET, [investmentType: "IKE", currentValue: 25000.00, investedAmount: 20000.00, recordedAt: "2026-01-15T10:00:00Z"])
        submitSnapshot(DEVICE_ID, SECRET, [investmentType: "IKE", currentValue: 26000.00, investedAmount: 20000.00, recordedAt: "2026-02-15T10:00:00Z"])

        and: "both projections are applied"
        def historyPath = "/v1/investments/IKE/history"
        awaitProjection {
            authenticatedGet(DEVICE_ID, SECRET, historyPath).get(historyPath).jsonPath().getList("").size() == 2
        }

        when: "the IKE history is requested"
        def response = authenticatedGet(DEVICE_ID, SECRET, historyPath).get(historyPath)

        then: "both snapshots are returned"
        response.statusCode() == 200
        response.jsonPath().getList("").size() == 2
    }

    def "investments from other users are not visible"() {
        given: "an IKE snapshot for a different device"
        submitSnapshot(DIFFERENT_DEVICE_ID, DIFFERENT_SECRET_BASE64, [investmentType: "IKE", investmentName: "Other IKE", currentValue: 99999.00, investedAmount: 88888.00])

        and: "the other user's projection is applied"
        awaitProjection { investmentEntry(getPortfolio(DIFFERENT_DEVICE_ID, DIFFERENT_SECRET_BASE64).jsonPath(), "IKE") != null }

        when: "the test device requests its portfolio"
        def response = getPortfolio()

        then: "the other user's investment is not visible"
        response.statusCode() == 200
        def ike = investmentEntry(response.jsonPath(), "IKE")
        ike == null || ike.investmentName != "Other IKE"
    }
}
