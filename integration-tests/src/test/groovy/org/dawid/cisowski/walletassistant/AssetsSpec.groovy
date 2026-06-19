package org.dawid.cisowski.walletassistant

import spock.lang.Title

@Title("Feature: Asset Portfolio Tracking")
class AssetsSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-assets"
    static final String SECRET = TEST_SECRET_BASE64

    static final String PORTFOLIO_PATH = "/v1/assets/portfolio"
    static final String OPEN_POSITIONS_PATH = "/v1/assets/positions?status=OPEN"
    static final String CLOSED_POSITIONS_PATH = "/v1/assets/positions?status=CLOSED"

    def setup() {
        jdbcTemplate.update("DELETE FROM asset_price_history WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM asset_positions WHERE user_id = ?", DEVICE_ID)
        jdbcTemplate.update("DELETE FROM wallet_events WHERE user_id = ?", DEVICE_ID)
    }

    private Map positionPayload(Map overrides) {
        def base = [
                positionId   : UUID.randomUUID().toString(),
                portfolioType: "IKE",
                assetSymbol  : "CDR",
                assetType    : "STOCK",
                assetName    : "CD Projekt",
                quantity     : "10",
                purchasePrice: "100.00",
                currency     : "PLN",
                purchaseDate : "2026-01-10"
        ]
        base.putAll(overrides)
        return base
    }

    private String openPosition(Map overrides) {
        def payload = positionPayload(overrides)
        submitEvent(DEVICE_ID, SECRET, "ASSET_POSITION_OPENED", UUID.randomUUID().toString(), payload)
        return payload.positionId
    }

    private void closePosition(String positionId, Map overrides) {
        def base = [
                positionId: positionId,
                salePrice : "120.00",
                saleDate  : "2026-02-10"
        ]
        base.putAll(overrides)
        submitEvent(DEVICE_ID, SECRET, "ASSET_POSITION_CLOSED", UUID.randomUUID().toString(), base)
    }

    private void recordPrice(Map overrides) {
        def base = [
                assetSymbol: "CDR",
                price      : "120.00",
                currency   : "PLN",
                priceDate  : "2026-02-01"
        ]
        base.putAll(overrides)
        submitEvent(DEVICE_ID, SECRET, "ASSET_PRICE_SNAPSHOT_RECORDED", UUID.randomUUID().toString(), base)
    }

    private def getPortfolio() {
        return authenticatedGet(DEVICE_ID, SECRET, PORTFOLIO_PATH).get(PORTFOLIO_PATH)
    }

    private def getPositions(String path) {
        return authenticatedGet(DEVICE_ID, SECRET, path).get(path)
    }

    private static def positionFrom(def json, String symbol) {
        def list = json.getList("portfolios.positions.flatten().findAll { it.assetSymbol == '${symbol}' }")
        return (list == null || list.isEmpty()) ? null : list[0]
    }

    def "opening a position makes it appear in the portfolio with no current price"() {
        given: "an opened IKE stock position"
        openPosition([assetSymbol: "CDR", quantity: "10", purchasePrice: "100.00"])

        and: "the projection is applied"
        awaitProjection { positionFrom(getPortfolio().jsonPath(), "CDR") != null }

        when: "the portfolio is requested"
        def response = getPortfolio()

        then: "the position is present with invested amount but no current price"
        response.statusCode() == 200
        def position = positionFrom(response.jsonPath(), "CDR")
        position.investedAmount == 1000.00
        position.currentPrice == null
        position.currentValue == null
        position.gainLoss == null
        position.gainLossPercent == null
    }

    def "recording a price computes profit and loss for the position"() {
        given: "an opened position of 10 units bought at 100"
        openPosition([assetSymbol: "CDR", quantity: "10", purchasePrice: "100.00"])
        awaitProjection { positionFrom(getPortfolio().jsonPath(), "CDR") != null }

        when: "a current price of 120 is recorded"
        recordPrice([assetSymbol: "CDR", price: "120.00"])
        awaitProjection { positionFrom(getPortfolio().jsonPath(), "CDR")?.currentPrice == 120.0 }
        def response = getPortfolio()

        then: "gain/loss is 200 and 20 percent"
        response.statusCode() == 200
        def position = positionFrom(response.jsonPath(), "CDR")
        position.currentValue == 1200.00
        position.gainLoss == 200.00
        position.gainLossPercent == 20.00

        and: "portfolio totals reflect the gain"
        def json = response.jsonPath()
        json.getDouble("totalInvested") == 1000.0
        json.getDouble("totalCurrentValue") == 1200.0
        json.getDouble("totalGainLoss") == 200.0
        json.getDouble("totalGainLossPercent") == 20.0
    }

    def "positions are grouped by portfolio type"() {
        given: "an IKE position and a PERSONAL position"
        openPosition([portfolioType: "IKE", assetSymbol: "CDR", assetName: "CD Projekt"])
        openPosition([portfolioType: "PERSONAL", assetSymbol: "GLD", assetType: "GOLD", assetName: "Złoto"])

        and: "both projections are applied"
        awaitProjection {
            def json = getPortfolio().jsonPath()
            positionFrom(json, "CDR") != null && positionFrom(json, "GLD") != null
        }

        when: "the portfolio is requested"
        def response = getPortfolio()

        then: "two portfolio groups are returned"
        response.statusCode() == 200
        def types = response.jsonPath().getList("portfolios.portfolioType")
        types.size() == 2
        types.contains("IKE")
        types.contains("PERSONAL")
    }

    def "closing the full position moves it from open to closed"() {
        given: "an opened position of 10 units"
        def positionId = openPosition([assetSymbol: "CDR", quantity: "10", purchasePrice: "100.00"])
        awaitProjection { positionFrom(getPortfolio().jsonPath(), "CDR") != null }

        when: "the full position is closed at 120"
        closePosition(positionId, [salePrice: "120.00", quantity: "10"])
        awaitProjection { getPositions(CLOSED_POSITIONS_PATH).jsonPath().getList("").size() == 1 }

        then: "the closed position is listed with sale details"
        def closed = getPositions(CLOSED_POSITIONS_PATH)
        closed.statusCode() == 200
        def closedList = closed.jsonPath().getList("")
        closedList.size() == 1
        closedList[0].status == "CLOSED"
        closedList[0].salePrice == 120.00

        and: "it no longer appears as an open position"
        def open = getPositions(OPEN_POSITIONS_PATH)
        open.jsonPath().getList("findAll { it.assetSymbol == 'CDR' }").isEmpty()
    }

    def "partial close splits the position into sold and remaining parts"() {
        given: "an opened position of 1.0 units"
        def positionId = openPosition([assetSymbol: "BTC", assetType: "CRYPTO", assetName: "Bitcoin",
                                       quantity: "1.0", purchasePrice: "200000.00"])
        awaitProjection { positionFrom(getPortfolio().jsonPath(), "BTC") != null }

        when: "0.3 units are sold"
        closePosition(positionId, [salePrice: "250000.00", quantity: "0.3"])
        awaitProjection { getPositions(CLOSED_POSITIONS_PATH).jsonPath().getList("").size() == 1 }

        then: "the closed leg holds the sold 0.3 units"
        def closed = getPositions(CLOSED_POSITIONS_PATH)
        def closedList = closed.jsonPath().getList("findAll { it.assetSymbol == 'BTC' }")
        closedList.size() == 1
        new BigDecimal(closedList[0].quantity.toString()).compareTo(new BigDecimal("0.3")) == 0

        and: "the open leg holds the remaining 0.7 units"
        def open = getPositions(OPEN_POSITIONS_PATH)
        def openList = open.jsonPath().getList("findAll { it.assetSymbol == 'BTC' }")
        openList.size() == 1
        new BigDecimal(openList[0].quantity.toString()).compareTo(new BigDecimal("0.7")) == 0
    }

    def "recording the same price twice keeps only one record"() {
        given: "an opened position so the symbol exists"
        openPosition([assetSymbol: "CDR"])

        when: "the same price is recorded twice for the same symbol and date"
        recordPrice([assetSymbol: "CDR", price: "120.00", priceDate: "2026-02-01"])
        recordPrice([assetSymbol: "CDR", price: "120.00", priceDate: "2026-02-01"])

        and: "the projection has run"
        def pricesPath = "/v1/assets/CDR/prices"
        awaitProjection { authenticatedGet(DEVICE_ID, SECRET, pricesPath).get(pricesPath).jsonPath().getList("").size() == 1 }

        then: "only one price record exists"
        def response = authenticatedGet(DEVICE_ID, SECRET, pricesPath).get(pricesPath)
        response.statusCode() == 200
        response.jsonPath().getList("").size() == 1
    }

    def "requesting the portfolio without authentication is rejected"() {
        when: "the portfolio is requested without HMAC headers"
        def response = io.restassured.RestAssured.given().get(PORTFOLIO_PATH)

        then: "the request is unauthorized"
        response.statusCode() == 401
    }
}
