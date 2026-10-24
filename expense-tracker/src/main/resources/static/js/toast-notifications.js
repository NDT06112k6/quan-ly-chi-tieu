"use strict";

(async function showToastNotifications() {
    const notificationElements = document.querySelectorAll("#toast-notifications [data-toast-icon]");
    if (notificationElements.length === 0) {
        return;
    }

    const toastDurationMilliseconds = 3000;
    const notificationToast = Swal.mixin({
        toast: true,
        position: "top-end",
        showConfirmButton: false,
        showCloseButton: true,
        closeButtonAriaLabel: "Đóng thông báo",
        timer: toastDurationMilliseconds,
        timerProgressBar: true,
        backdrop: false,
        heightAuto: false
    });

    for (const notificationElement of notificationElements) {
        await notificationToast.fire({
            icon: notificationElement.dataset.toastIcon,
            titleText: notificationElement.textContent
        });
    }
})();
