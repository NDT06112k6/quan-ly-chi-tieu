const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

const source = readFileSync(resolve(__dirname, "../../main/resources/static/js/transaction-form.js"), "utf8");

function createForm(responses) {
    const select = {
        value: "1",
        options: [{ value: "1", text: "Existing category" }],
        add(option) {
            this.options.push(option);
        }
    };
    const button = {
        dataset: { url: "/transactions/categories?page=1" },
        disabled: false,
        hidden: false,
        addEventListener(event, listener) {
            this.listener = listener;
        }
    };
    const error = { hidden: true, textContent: "" };
    const elements = { "categoryId": select, "load-categories": button, "category-load-error": error };
    runInNewContext(source, {
        document: { getElementById: (id) => elements[id] },
        window: { location: { origin: "http://localhost" } },
        URL,
        Option: function (text, value) {
            this.text = text;
            this.value = value;
        },
        fetch: async () => responses.shift()
    });
    return { select, button, error };
}

function categoryResponse(options) {
    return {
        ok: true,
        redirected: false,
        json: async () => options
    };
}

test("loading options preserves selection, avoids duplicates and advances pagination", async () => {
    const response = categoryResponse({
        content: [{ id: 1, name: "Existing category" }, { id: 2, name: "New category" }],
        page: 1,
        hasNext: true
    });
    const form = createForm([response]);

    await form.button.listener();

    assert.equal(form.select.value, "1");
    assert.deepEqual(form.select.options.map((option) => option.value), ["1", "2"]);
    assert.equal(new URL(form.button.dataset.url).searchParams.get("page"), "2");
    assert.equal(form.error.hidden, true);
    assert.equal(form.button.disabled, false);
    assert.equal(form.button.hidden, false);
});

test("failed loading remains visible, retains options and allows retry of the same page", async () => {
    const form = createForm([
        { ok: false, redirected: false },
        categoryResponse({ content: [{ id: 2, name: "New category" }], page: 1, hasNext: false })
    ]);

    await form.button.listener();

    assert.equal(form.error.hidden, false);
    assert.match(form.error.textContent, /Không tải được danh mục/);
    assert.equal(form.button.dataset.url, "/transactions/categories?page=1");
    assert.equal(form.select.options.length, 1);
    assert.equal(form.button.disabled, false);

    await form.button.listener();

    assert.equal(form.select.value, "1");
    assert.equal(form.select.options.length, 2);
    assert.equal(form.error.hidden, true);
    assert.equal(form.button.hidden, true);
});

test("an expired session redirect reports failure without replacing existing options", async () => {
    const form = createForm([{ ok: true, redirected: true }]);

    await form.button.listener();

    assert.equal(form.error.hidden, false);
    assert.equal(form.select.options.length, 1);
    assert.equal(form.button.disabled, false);
});
