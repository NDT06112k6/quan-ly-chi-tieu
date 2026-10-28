"use strict";

(function initializeTransactionAmount() {
    const form = document.getElementById("transaction-form");
    const amountInput = document.getElementById("amount");
    const realAmount = document.getElementById("realAmount");
    const saveButton = document.getElementById("save-transaction");
    if (!form || !amountInput || !realAmount || !saveButton) {
        return;
    }

    const maximumIntegerDigits = 13;
    const maximumFractionDigits = 2;
    const requiredMessage = "Vui lòng nhập số tiền.";
    const invalidMessage = "Nhập số tiền hợp lệ; dùng dấu phẩy cho phần thập phân.";
    const positiveMessage = "Số tiền phải lớn hơn 0.";
    const precisionMessage = "Số tiền tối đa 13 chữ số phần nguyên và 2 chữ số thập phân.";

    function groupIntegerDigits(digits) {
        const formatted = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
        return formatted;
    }

    function parseDisplayAmount(value) {
        const compact = value.replace(/\s/g, "");
        if (compact === "") {
            return { error: requiredMessage };
        }
        const parts = compact.split(",");
        const integerDigits = parts[0].replace(/\./g, "");
        const fractionDigits = parts[1] ?? "";
        if (parts.length > 2 || !/^\d*$/.test(integerDigits) || !/^\d*$/.test(fractionDigits)
                || (integerDigits === "" && fractionDigits === "")) {
            return { error: invalidMessage };
        }
        const integer = integerDigits.replace(/^0+(?=\d)/, "") || "0";
        if (integer.length > maximumIntegerDigits || fractionDigits.length > maximumFractionDigits) {
            return { error: precisionMessage };
        }
        const raw = integer + (fractionDigits === "" ? "" : "." + fractionDigits);
        const formatted = groupIntegerDigits(integer) + (parts.length === 2 ? "," + fractionDigits : "");
        if (!/[1-9]/.test(raw)) {
            return { formatted, error: positiveMessage };
        }
        return { raw, formatted, error: "" };
    }

    function restoreCaret(previousValue, previousPosition) {
        const prefix = previousValue.slice(0, previousPosition).replace(/[.\s]/g, "");
        let meaningfulCharacters = prefix.length;
        if (prefix.startsWith(",")) {
            meaningfulCharacters += 1;
        }
        let consumedCharacters = 0;
        let position = 0;
        while (position < amountInput.value.length && consumedCharacters < meaningfulCharacters) {
            if (amountInput.value[position] !== ".") {
                consumedCharacters += 1;
            }
            position += 1;
        }
        amountInput.setSelectionRange(position, position);
    }

    function synchronizeAmount(preserveCaret) {
        const previousValue = amountInput.value;
        const previousPosition = amountInput.selectionStart ?? previousValue.length;
        const parsed = parseDisplayAmount(previousValue);
        realAmount.value = parsed.raw ?? "";
        if (parsed.formatted !== undefined) {
            amountInput.value = parsed.formatted;
        }
        amountInput.setCustomValidity(parsed.error);
        if (preserveCaret && parsed.formatted !== undefined) {
            restoreCaret(previousValue, previousPosition);
        }
        const valid = parsed.error === "";
        return valid;
    }

    function applySuggestion(event) {
        amountInput.value = event.currentTarget.dataset.amount;
        synchronizeAmount(false);
        amountInput.focus();
        amountInput.setSelectionRange(amountInput.value.length, amountInput.value.length);
    }

    function prepareSubmission(event) {
        const valid = synchronizeAmount(false);
        if (!valid) {
            event.preventDefault();
            amountInput.reportValidity();
        }
    }

    // Stored decimals use a dot; the visible field reserves dots for thousands.
    amountInput.value = realAmount.value.replace(".", ",");
    synchronizeAmount(false);
    amountInput.addEventListener("input", () => {
        synchronizeAmount(true);
    });
    form.addEventListener("submit", prepareSubmission);
    for (const button of form.querySelectorAll("[data-amount]")) {
        button.addEventListener("click", applySuggestion);
    }
    saveButton.disabled = saveButton.dataset.noCategories === "true";
})();
