"use strict";

(function initializeBudgetPeriod() {
    const form = document.getElementById("budget-form");
    const typeSelect = document.getElementById("type");
    const monthlyField = document.getElementById("monthly-period-field");
    const weeklyField = document.getElementById("weekly-period-field");
    const monthlyInput = document.getElementById("monthlyPeriod");
    const weeklyInput = document.getElementById("weeklyPeriod");
    if (!form || !typeSelect || !monthlyField || !weeklyField || !monthlyInput || !weeklyInput) {
        return;
    }

    const monthlyType = "MONTHLY";
    const weeklyType = "WEEKLY";

    function updatePeriodFields() {
        const monthlySelected = typeSelect.value === monthlyType;
        const weeklySelected = typeSelect.value === weeklyType;
        monthlyField.hidden = !monthlySelected;
        weeklyField.hidden = !weeklySelected;
        monthlyInput.disabled = !monthlySelected;
        weeklyInput.disabled = !weeklySelected;
        monthlyInput.required = monthlySelected;
        weeklyInput.required = weeklySelected;
    }

    typeSelect.addEventListener("change", updatePeriodFields);
    updatePeriodFields();
})();
