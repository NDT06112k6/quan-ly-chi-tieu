"use strict";

const loadCategoriesButton = document.getElementById("load-categories");
const categorySelect = document.getElementById("categoryId");
const categoryLoadError = document.getElementById("category-load-error");

function appendCategoryOptions(categories) {
    for (const category of categories) {
        const categoryId = String(category.id);
        let alreadyLoaded = false;
        for (const option of categorySelect.options) {
            if (option.value === categoryId) {
                alreadyLoaded = true;
                break;
            }
        }
        if (!alreadyLoaded) {
            const option = new Option(category.name, categoryId);
            categorySelect.add(option);
        }
    }
}

async function loadMoreCategories() {
    loadCategoriesButton.disabled = true;
    categoryLoadError.hidden = true;
    try {
        const response = await fetch(loadCategoriesButton.dataset.url, {
            headers: { Accept: "application/json" }
        });
        if (!response.ok || response.redirected) {
            throw new Error("Category request failed");
        }
        const options = await response.json();
        appendCategoryOptions(options.content);
        loadCategoriesButton.hidden = !options.hasNext;
        const nextUrl = new URL(loadCategoriesButton.dataset.url, window.location.origin);
        nextUrl.searchParams.set("page", String(options.page + 1));
        loadCategoriesButton.dataset.url = nextUrl.toString();
    } catch {
        categoryLoadError.textContent = "Không tải được danh mục. Vui lòng thử lại hoặc đăng nhập lại.";
        categoryLoadError.hidden = false;
    } finally {
        loadCategoriesButton.disabled = false;
    }
}

if (loadCategoriesButton) {
    loadCategoriesButton.addEventListener("click", loadMoreCategories);
}
