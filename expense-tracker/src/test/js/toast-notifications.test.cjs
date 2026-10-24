const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

const source = readFileSync(resolve(__dirname, "../../main/resources/static/js/toast-notifications.js"), "utf8");

function runNotifications(elements, swal) {
    const result = runInNewContext(source, {
        document: {
            querySelectorAll(selector) {
                assert.equal(selector, "#toast-notifications [data-toast-icon]");
                return elements;
            }
        },
        Swal: swal
    });
    return result;
}

test("pages without notifications do not invoke SweetAlert2", async () => {
    await runNotifications([], undefined);
});

test("notifications use nonblocking top-right toasts with a three-second timer and plain text", async () => {
    const messages = [
        { dataset: { toastIcon: "success" }, textContent: "Saved successfully" },
        { dataset: { toastIcon: "error" }, textContent: "<img src=x onerror=alert(1)>" }
    ];
    let configuration;
    const displayed = [];
    await runNotifications(messages, {
        mixin(options) {
            configuration = options;
            return {
                async fire(notification) {
                    displayed.push(notification);
                }
            };
        }
    });

    assert.equal(configuration.toast, true);
    assert.equal(configuration.position, "top-end");
    assert.equal(configuration.timer, 3000);
    assert.equal(configuration.timerProgressBar, true);
    assert.equal(configuration.showConfirmButton, false);
    assert.equal(configuration.backdrop, false);
    assert.equal(configuration.heightAuto, false);
    assert.equal(displayed.length, 2);
    assert.equal(displayed[0].icon, "success");
    assert.equal(displayed[1].icon, "error");
    assert.equal(displayed[1].titleText, messages[1].textContent);
    assert.equal(displayed[1].html, undefined);
    assert.equal(displayed[1].title, undefined);
});

test("multiple messages wait for the previous toast instead of replacing it", async () => {
    let finishFirstToast;
    const firstToast = new Promise((resolveToast) => {
        finishFirstToast = resolveToast;
    });
    const displayed = [];
    const result = runNotifications([
        { dataset: { toastIcon: "error" }, textContent: "First" },
        { dataset: { toastIcon: "success" }, textContent: "Second" }
    ], {
        mixin() {
            return {
                fire(notification) {
                    displayed.push(notification.titleText);
                    return displayed.length === 1 ? firstToast : Promise.resolve();
                }
            };
        }
    });

    assert.deepEqual(displayed, ["First"]);
    finishFirstToast();
    await result;
    assert.deepEqual(displayed, ["First", "Second"]);
});
