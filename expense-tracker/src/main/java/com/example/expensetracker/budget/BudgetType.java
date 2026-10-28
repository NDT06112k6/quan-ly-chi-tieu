package com.example.expensetracker.budget;

public enum BudgetType {
    MONTHLY("tháng", "Tháng"),
    WEEKLY("tuần", "Tuần");

    private final String label;
    private final String displayName;

    BudgetType(String label, String displayName) {
        this.label = label;
        this.displayName = displayName;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayName() {
        return displayName;
    }
}
