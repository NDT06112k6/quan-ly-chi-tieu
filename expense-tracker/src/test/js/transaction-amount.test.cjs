const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

const source = readFileSync(resolve(__dirname, "../../main/resources/static/js/transaction-amount.js"), "utf8");

function createAmountInput() {
    return {
        value: "",
        selectionStart: 0,
        listeners: {},
        validationMessage: "",
        addEventListener(event, listener) {
            this.listeners[event] = listener;
        },
        setCustomValidity(message) {
            this.validationMessage = message;
        },
        setSelectionRange(start, end) {
            this.selectionStart = start;
            this.selectionEnd = end;
        },
        focus() {
            this.focused = true;
        },
        reportValidity() {
            this.validityReported = true;
        }
    };
}

function createSuggestion(amount) {
    return {
        dataset: { amount },
        addEventListener(event, listener) {
            assert.equal(event, "click");
            this.click = () => listener({ currentTarget: this });
        }
    };
}

function createAmountForm(rawAmount = "", noCategories = false) {
    const amountInput = createAmountInput();
    const realAmount = { value: rawAmount };
    const saveButton = { disabled: true, dataset: { noCategories: String(noCategories) } };
    const suggestions = ["100000", "500000", "1000000", "5000000", "10000000"].map(createSuggestion);
    const form = {
        listeners: {},
        addEventListener(event, listener) {
            this.listeners[event] = listener;
        },
        querySelectorAll(selector) {
            assert.equal(selector, "[data-amount]");
            return suggestions;
        }
    };
    const elements = { "transaction-form": form, "amount": amountInput, "realAmount": realAmount,
        "save-transaction": saveButton };
    runInNewContext(source, { document: { getElementById: (id) => elements[id] } });
    return { amountInput, realAmount, saveButton, suggestions, form };
}

function inputValue(state, value, caret = value.length) {
    state.amountInput.value = value;
    state.amountInput.selectionStart = caret;
    state.amountInput.listeners.input();
}

function submitForm(state) {
    let prevented = false;
    state.form.listeners.submit({
        preventDefault() {
            prevented = true;
        }
    });
    return prevented;
}

test("adds dot grouping while typing and keeps the raw hidden amount synchronized", () => {
    const state = createAmountForm();
    for (const digit of "400000") {
        inputValue(state, state.amountInput.value + digit);
    }

    assert.equal(state.amountInput.value, "400.000");
    assert.equal(state.realAmount.value, "400000");
    assert.equal(state.amountInput.validationMessage, "");
    assert.equal(state.amountInput.selectionStart, 7);
    assert.equal(state.saveButton.disabled, false);
});

test("all five suggestions fill the display and hidden input without submitting", () => {
    const state = createAmountForm();
    const expected = ["100.000", "500.000", "1.000.000", "5.000.000", "10.000.000"];
    for (let index = 0; index < state.suggestions.length; index++) {
        state.suggestions[index].click();
        assert.equal(state.amountInput.value, expected[index]);
        assert.equal(state.realAmount.value, state.suggestions[index].dataset.amount);
        assert.equal(state.amountInput.focused, true);
    }
});

test("strips grouping again on submit even if the last change did not emit input", () => {
    const state = createAmountForm("100000");
    state.amountInput.value = "1.234.567";

    assert.equal(submitForm(state), false);
    assert.equal(state.realAmount.value, "1234567");
});

test("uses comma decimals to preserve existing fractional amounts during editing", () => {
    const state = createAmountForm("123.45");
    assert.equal(state.amountInput.value, "123,45");
    assert.equal(state.realAmount.value, "123.45");

    inputValue(state, "400.000,50");

    assert.equal(state.amountInput.value, "400.000,50");
    assert.equal(state.realAmount.value, "400000.50");
    assert.equal(submitForm(state), false);
});

test("preserves every digit of the maximum supported decimal amount", () => {
    const state = createAmountForm("9999999999999.99");

    assert.equal(state.amountInput.value, "9.999.999.999.999,99");
    assert.equal(state.realAmount.value, "9999999999999.99");
    assert.equal(submitForm(state), false);
});

test("supports entering fractional dong without a leading zero and keeps the caret after the fraction", () => {
    const state = createAmountForm();

    inputValue(state, ",01");

    assert.equal(state.amountInput.value, "0,01");
    assert.equal(state.realAmount.value, "0.01");
    assert.equal(state.amountInput.selectionStart, 4);
});

test("keeps the caret near an insertion in the middle instead of jumping to the end", () => {
    const state = createAmountForm("1500000");

    inputValue(state, "1.2500.000", 3);

    assert.equal(state.amountInput.value, "12.500.000");
    assert.equal(state.realAmount.value, "12500000");
    assert.equal(state.amountInput.selectionStart, 2);
});

test("normalizes leading zeros and pasted whitespace without converting through Number", () => {
    const state = createAmountForm();

    inputValue(state, " 000400 000 ");

    assert.equal(state.amountInput.value, "400.000");
    assert.equal(state.realAmount.value, "400000");
});

for (const value of ["", "0", "0,00", "-400.000", "12abc", "10000000000000", "1,234", "1,2,3", "..."]) {
    test(`invalid amount clears stale hidden data and prevents submission: ${JSON.stringify(value)}`, () => {
        const state = createAmountForm("400000");

        inputValue(state, value);

        assert.equal(state.realAmount.value, "");
        assert.notEqual(state.amountInput.validationMessage, "");
        assert.equal(submitForm(state), true);
        assert.equal(state.amountInput.validityReported, true);
    });
}

test("a server-rejected negative value is not silently changed to a positive amount", () => {
    const state = createAmountForm("-1.00");

    assert.equal(state.amountInput.value, "-1,00");
    assert.equal(state.realAmount.value, "");
    assert.equal(submitForm(state), true);
});

test("keeps saving disabled when the user has no categories", () => {
    const state = createAmountForm("400000", true);

    assert.equal(state.saveButton.disabled, true);
});

test("safely skips initialization on pages without the transaction form", () => {
    assert.doesNotThrow(() => runInNewContext(source, { document: { getElementById: () => null } }));
});
