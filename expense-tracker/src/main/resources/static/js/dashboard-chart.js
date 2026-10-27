"use strict";

const CHART_ENDPOINT = "/api/dashboard/chart";
const HAS_NEXT_HEADER = "X-Has-Next";
const COLOR_ROTATION = 137.508;
const FULL_CIRCLE = 360;
const PERCENT_SCALE = 100;
const chartContainer = document.getElementById("expense-chart-container");
const chartCanvas = document.getElementById("expense-chart");
const chartStatus = document.getElementById("expense-chart-status");
const chartRetry = document.getElementById("expense-chart-retry");

async function fetchExpensePage(page) {
    let url = CHART_ENDPOINT;
    if (page > 0) {
        url = `${CHART_ENDPOINT}?page=${page}`;
    }
    const response = await fetch(url, { headers: { Accept: "application/json" }, cache: "no-store" });
    if (!response.ok || response.redirected) {
        throw new Error("Chart request failed");
    }
    const expenses = await response.json();
    const hasNext = response.headers.get(HAS_NEXT_HEADER);
    if (!Array.isArray(expenses) || (hasNext !== "true" && hasNext !== "false")) {
        throw new Error("Invalid chart response");
    }
    if (hasNext === "true" && expenses.length === 0) {
        throw new Error("Invalid chart pagination");
    }
    const result = { expenses, hasNext: hasNext === "true" };
    return result;
}

async function fetchAllExpenses() {
    const expenses = [];
    let page = 0;
    let hasNext = true;
    while (hasNext) {
        const result = await fetchExpensePage(page);
        expenses.push(...result.expenses);
        hasNext = result.hasNext;
        page += 1;
    }
    return expenses;
}

function buildChartData(expenses) {
    const labels = [];
    const amounts = [];
    const colors = [];
    for (const expense of expenses) {
        const amount = Number(expense.totalAmount);
        if (typeof expense.categoryName !== "string" || !Number.isFinite(amount) || amount <= 0) {
            throw new Error("Invalid expense amount or category");
        }
        const hue = (labels.length * COLOR_ROTATION) % FULL_CIRCLE;
        labels.push(expense.categoryName);
        amounts.push(amount);
        colors.push(`hsl(${hue}, 65%, 50%)`);
    }
    const data = { labels, datasets: [{ data: amounts, backgroundColor: colors, borderWidth: 2 }] };
    return data;
}

function formatExpenseTooltip(context) {
    let total = 0;
    for (const amount of context.dataset.data) {
        total += amount;
    }
    const percentage = ((context.parsed / total) * PERCENT_SCALE).toFixed(1);
    const amount = context.parsed.toLocaleString("en-US", {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
        roundingMode: "halfEven"
    });
    const label = `${context.label}: ${amount} ₫ (${percentage}%)`;
    return label;
}

function renderExpenseChart(expenses) {
    const data = buildChartData(expenses);
    if (typeof Chart !== "function") {
        throw new Error("Chart library unavailable");
    }
    chartContainer.hidden = false;
    new Chart(chartCanvas, {
        type: "pie",
        data,
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: "bottom" },
                tooltip: { callbacks: { label: formatExpenseTooltip } }
            }
        }
    });
}

async function loadDashboardChart() {
    chartRetry.hidden = true;
    chartRetry.disabled = true;
    chartStatus.textContent = "Đang tải biểu đồ...";
    try {
        const expenses = await fetchAllExpenses();
        if (expenses.length === 0) {
            chartStatus.textContent = "Chưa có khoản chi nào trong tháng này.";
            return;
        }
        renderExpenseChart(expenses);
        chartStatus.textContent = "Di chuột hoặc chạm vào biểu đồ để xem số tiền và tỷ lệ.";
    } catch {
        chartContainer.hidden = true;
        chartStatus.textContent = "Không tải được biểu đồ. Vui lòng thử lại hoặc đăng nhập lại.";
        chartRetry.hidden = false;
    } finally {
        chartRetry.disabled = false;
    }
}

chartRetry.addEventListener("click", loadDashboardChart);
loadDashboardChart();
