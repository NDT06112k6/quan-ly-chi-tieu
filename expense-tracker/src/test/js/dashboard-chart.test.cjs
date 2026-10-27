const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

const source = readFileSync(resolve(__dirname, "../../main/resources/static/js/dashboard-chart.js"), "utf8");

function chartResponse(expenses, hasNext = false) {
    return {
        ok: true,
        redirected: false,
        headers: { get: () => String(hasNext) },
        json: async () => expenses
    };
}

function createDashboard(responses, libraryAvailable = true) {
    const container = { hidden: true };
    const canvas = {};
    const status = { textContent: "" };
    const retry = {
        hidden: true,
        disabled: false,
        addEventListener(event, listener) {
            this.listener = listener;
        }
    };
    const elements = {
        "expense-chart-container": container,
        "expense-chart": canvas,
        "expense-chart-status": status,
        "expense-chart-retry": retry
    };
    const requests = [];
    const charts = [];
    const chartConstructor = function (element, configuration) {
        charts.push({ element, configuration });
    };
    const ready = runInNewContext(source, {
        document: { getElementById: (id) => elements[id] },
        Chart: libraryAvailable ? chartConstructor : undefined,
        fetch: async (url, options) => {
            requests.push({ url, options });
            return responses.shift();
        }
    });
    return { container, canvas, status, retry, requests, charts, ready };
}

test("fetches every page before drawing a pie chart with matching names and amounts", async () => {
    const dashboard = createDashboard([
        chartResponse([{ categoryName: "Ăn uống", totalAmount: 30 }], true),
        chartResponse([{ categoryName: "Đi lại", totalAmount: 70 }])
    ]);
    await dashboard.ready;
    assert.deepEqual(dashboard.requests.map((request) => request.url), [
        "/api/dashboard/chart", "/api/dashboard/chart?page=1"
    ]);
    assert.equal(dashboard.requests[0].options.cache, "no-store");
    assert.equal(dashboard.charts.length, 1);
    const { element, configuration } = dashboard.charts[0];
    assert.equal(element, dashboard.canvas);
    assert.equal(configuration.type, "pie");
    assert.deepEqual(Array.from(configuration.data.labels), ["Ăn uống", "Đi lại"]);
    assert.deepEqual(Array.from(configuration.data.datasets[0].data), [30, 70]);
    const tooltip = configuration.options.plugins.tooltip.callbacks.label;
    assert.match(tooltip({ dataset: configuration.data.datasets[0], parsed: 30, label: "Ăn uống" }), /30\.0%/);
    assert.equal(dashboard.container.hidden, false);
    assert.equal(dashboard.retry.hidden, true);
});

test("shows an empty month only after a successful empty API response", async () => {
    const dashboard = createDashboard([chartResponse([])]);
    await dashboard.ready;
    assert.equal(dashboard.charts.length, 0);
    assert.equal(dashboard.container.hidden, true);
    assert.match(dashboard.status.textContent, /Chưa có khoản chi/);
    assert.equal(dashboard.retry.hidden, true);
});

test("tooltips format dong with comma grouping and no decimal digits without changing chart data", async () => {
    const amount = 1250000.75;
    const dashboard = createDashboard([chartResponse([{ categoryName: "Ăn uống", totalAmount: amount }])]);
    await dashboard.ready;
    const configuration = dashboard.charts[0].configuration;
    const tooltip = configuration.options.plugins.tooltip.callbacks.label;
    const label = tooltip({ dataset: configuration.data.datasets[0], parsed: amount, label: "Ăn uống" });

    assert.equal(label, "Ăn uống: 1,250,001 ₫ (100.0%)");
    assert.equal(configuration.data.datasets[0].data[0], amount);
    assert.equal(tooltip({ dataset: { data: [50000.50] }, parsed: 50000.50, label: "Tie" }),
        "Tie: 50,000 ₫ (100.0%)");
});

test("a later page failure does not draw partial data and retry starts with the first page", async () => {
    const responses = [
        chartResponse([{ categoryName: "Partial result", totalAmount: 10 }], true),
        { ok: false, redirected: false }
    ];
    const dashboard = createDashboard(responses);
    await dashboard.ready;
    assert.equal(dashboard.charts.length, 0);
    assert.match(dashboard.status.textContent, /Không tải được biểu đồ/);
    assert.equal(dashboard.retry.hidden, false);
    assert.equal(dashboard.retry.disabled, false);
    responses.push(chartResponse([{ categoryName: "Complete result", totalAmount: 15 }]));
    await dashboard.retry.listener();
    assert.equal(dashboard.requests[2].url, "/api/dashboard/chart");
    assert.equal(dashboard.charts.length, 1);
    assert.deepEqual(Array.from(dashboard.charts[0].configuration.data.labels), ["Complete result"]);
});

test("an expired session is displayed as a failure rather than an empty month", async () => {
    const dashboard = createDashboard([{ ok: true, redirected: true }]);
    await dashboard.ready;
    assert.match(dashboard.status.textContent, /đăng nhập lại/);
    assert.equal(dashboard.retry.hidden, false);
    assert.equal(dashboard.charts.length, 0);
});

test("a missing Chart.js CDN library produces a visible retryable failure", async () => {
    const dashboard = createDashboard([chartResponse([{ categoryName: "Food", totalAmount: 1 }])], false);
    await dashboard.ready;
    assert.equal(dashboard.container.hidden, true);
    assert.equal(dashboard.retry.hidden, false);
    assert.match(dashboard.status.textContent, /Không tải được biểu đồ/);
});

for (const expenses of [
    { message: "failure" },
    [{ categoryName: "Invalid amount", totalAmount: "not-a-number" }],
    [{ categoryName: "Invalid amount", totalAmount: -1 }]
]) {
    test(`invalid data is rejected without drawing a misleading chart: ${JSON.stringify(expenses)}`, async () => {
        const dashboard = createDashboard([chartResponse(expenses)]);
        await dashboard.ready;
        assert.equal(dashboard.charts.length, 0);
        assert.equal(dashboard.retry.hidden, false);
        assert.match(dashboard.status.textContent, /Không tải được biểu đồ/);
    });
}
