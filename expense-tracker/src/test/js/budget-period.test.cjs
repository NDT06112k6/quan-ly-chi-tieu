const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

const source = readFileSync(resolve(__dirname, "../../main/resources/static/js/budget-period.js"), "utf8");

function createPeriodForm(type = "MONTHLY") {
    const typeSelect = {
        value: type,
        addEventListener(event, listener) {
            assert.equal(event, "change");
            this.change = listener;
        }
    };
    const monthlyField = {};
    const weeklyField = {};
    const monthlyInput = { value: "2026-10", name: "period" };
    const weeklyInput = { value: "2026-W43", name: "period" };
    const elements = {
        "budget-form": {}, "type": typeSelect,
        "monthly-period-field": monthlyField, "weekly-period-field": weeklyField,
        "monthlyPeriod": monthlyInput, "weeklyPeriod": weeklyInput
    };
    runInNewContext(source, { document: { getElementById: (id) => elements[id] } });
    return { typeSelect, monthlyField, weeklyField, monthlyInput, weeklyInput };
}

function submittedPeriods(state) {
    const values = [state.monthlyInput, state.weeklyInput]
        .filter((input) => !input.disabled)
        .map((input) => input.value);
    return values;
}

test("monthly selection shows and requires only the month input", () => {
    const state = createPeriodForm();

    assert.equal(state.monthlyField.hidden, false);
    assert.equal(state.weeklyField.hidden, true);
    assert.equal(state.monthlyInput.required, true);
    assert.equal(state.weeklyInput.required, false);
    assert.deepEqual(submittedPeriods(state), ["2026-10"]);
});

test("weekly selection initializes correctly after validation errors", () => {
    const state = createPeriodForm("WEEKLY");

    assert.equal(state.monthlyField.hidden, true);
    assert.equal(state.weeklyField.hidden, false);
    assert.equal(state.monthlyInput.required, false);
    assert.equal(state.weeklyInput.required, true);
    assert.deepEqual(submittedPeriods(state), ["2026-W43"]);
});

test("switching types submits only the active canonical period", () => {
    const state = createPeriodForm();
    state.typeSelect.value = "WEEKLY";
    state.typeSelect.change();

    assert.deepEqual(submittedPeriods(state), ["2026-W43"]);
    assert.equal(state.monthlyField.hidden, true);
    assert.equal(state.weeklyField.hidden, false);

    state.typeSelect.value = "MONTHLY";
    state.typeSelect.change();

    assert.deepEqual(submittedPeriods(state), ["2026-10"]);
});

test("switching back preserves the user's previous selection for both input types", () => {
    const state = createPeriodForm();
    state.monthlyInput.value = "2027-01";
    state.typeSelect.value = "WEEKLY";
    state.typeSelect.change();
    state.weeklyInput.value = "2020-W53";
    state.typeSelect.value = "MONTHLY";
    state.typeSelect.change();

    assert.deepEqual(submittedPeriods(state), ["2027-01"]);

    state.typeSelect.value = "WEEKLY";
    state.typeSelect.change();
    assert.deepEqual(submittedPeriods(state), ["2020-W53"]);
});

test("a missing or unknown type disables both periods instead of submitting a stale value", () => {
    for (const type of ["", "UNKNOWN"]) {
        const state = createPeriodForm(type);
        assert.equal(state.monthlyField.hidden, true);
        assert.equal(state.weeklyField.hidden, true);
        assert.equal(state.monthlyInput.required, false);
        assert.equal(state.weeklyInput.required, false);
        assert.deepEqual(submittedPeriods(state), []);
    }
});

test("does not fail when loaded outside the budget form", () => {
    assert.doesNotThrow(() => runInNewContext(source, { document: { getElementById: () => null } }));
});
