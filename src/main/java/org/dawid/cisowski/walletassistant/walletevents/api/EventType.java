package org.dawid.cisowski.walletassistant.walletevents.api;

import java.util.Arrays;

public enum EventType {

    EXPENSE_RECORDED("ExpenseRecorded.v1"),
    EXPENSE_CORRECTED("ExpenseCorrected.v1"),
    EXPENSE_DELETED("ExpenseDeleted.v1"),
    ACCOUNT_BALANCE_SNAPSHOT_RECORDED("AccountBalanceSnapshotRecorded.v1"),
    INVESTMENT_SNAPSHOT_RECORDED("InvestmentSnapshotRecorded.v1");

    private final String typeName;

    EventType(String typeName) {
        this.typeName = typeName;
    }

    public String typeName() {
        return typeName;
    }

    public static EventType fromTypeName(String typeName) {
        return Arrays.stream(values())
                .filter(eventType -> eventType.typeName.equals(typeName) || eventType.name().equals(typeName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + typeName));
    }
}
