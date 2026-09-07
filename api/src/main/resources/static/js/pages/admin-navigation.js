document.addEventListener("DOMContentLoaded", function() {
    bindNavigationEvents();
});

function bindNavigationEvents() {
    bindClick("searchBtn", filterRows);
    bindClick("resetBtn", resetFilters);
    bindClick("addBtn", openCreateModal);
    bindClick("modalCloseBtn", closeModal);
    bindClick("modalCancelBtn", closeModal);
    bindClick("modalSaveBtn", saveNavigation);

    document.querySelectorAll(".btn-edit").forEach(function(button) {
        button.addEventListener("click", openEditModal);
    });

    document.querySelectorAll(".btn-delete").forEach(function(button) {
        button.addEventListener("click", deleteNavigation);
    });

    document.querySelectorAll(".status-toggle").forEach(function(button) {
        button.addEventListener("click", toggleStatus);
    });

    const modal = document.getElementById("navigationModal");
    if (modal) {
        modal.addEventListener("click", function(event) {
            if (event.target === modal) {
                closeModal();
            }
        });
    }
}

function bindClick(id, handler) {
    const element = document.getElementById(id);
    if (element) {
        element.addEventListener("click", handler);
    }
}

function filterRows() {
    const groupCode = document.getElementById("groupFilter").value;
    const itemName = document.getElementById("itemNameFilter").value.trim().toLowerCase();
    const useYn = document.getElementById("useYnFilter").value;
    const displayYn = document.getElementById("displayYnFilter").value;

    document.querySelectorAll("#navigationTableBody tr[data-id]").forEach(function(row) {
        const matchesGroup = !groupCode || row.dataset.groupCode === groupCode;
        const matchesName = !itemName || row.dataset.itemName.toLowerCase().includes(itemName);
        const matchesUse = !useYn || row.dataset.useYn === useYn;
        const matchesDisplay = !displayYn || row.dataset.displayYn === displayYn;

        row.style.display = matchesGroup && matchesName && matchesUse && matchesDisplay ? "" : "none";
    });
}

function resetFilters() {
    document.getElementById("groupFilter").value = "";
    document.getElementById("itemNameFilter").value = "";
    document.getElementById("useYnFilter").value = "";
    document.getElementById("displayYnFilter").value = "";
    filterRows();
}

function openCreateModal() {
    resetForm();
    document.getElementById("modalTitle").textContent = "메뉴 추가";
    openModal();
}

function openEditModal(event) {
    const row = event.target.closest("tr");
    if (!row) {
        return;
    }

    resetForm();
    document.getElementById("modalTitle").textContent = "메뉴 수정";
    document.getElementById("formNavigationItemId").value = row.dataset.id;
    document.getElementById("formNavigationGroupId").value = row.dataset.groupId;
    document.getElementById("formIcon").value = row.dataset.icon || "";
    document.getElementById("formItemName").value = row.dataset.itemName || "";
    document.getElementById("formItemUrl").value = row.dataset.itemUrl || "";
    document.getElementById("formDepth").value = row.dataset.depth || "1";
    document.getElementById("formSortOrder").value = row.dataset.sortOrder || "1";
    document.getElementById("formUseYn").checked = row.dataset.useYn === "true";
    document.getElementById("formDisplayYn").checked = row.dataset.displayYn === "true";
    document.getElementById("formRequiredRole").value = row.dataset.requiredRole || "USER";
    openModal();
}

async function saveNavigation() {
    const form = document.getElementById("navigationForm");
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const id = document.getElementById("formNavigationItemId").value;
    const payload = {
        navigationGroupId: toNumber(document.getElementById("formNavigationGroupId").value),
        parentNavigationItemId: null,
        itemName: document.getElementById("formItemName").value.trim(),
        itemUrl: document.getElementById("formItemUrl").value.trim(),
        icon: document.getElementById("formIcon").value.trim(),
        depth: toNumber(document.getElementById("formDepth").value),
        sortOrder: toNumber(document.getElementById("formSortOrder").value),
        useYn: document.getElementById("formUseYn").checked,
        displayYn: document.getElementById("formDisplayYn").checked,
        requiredRole: document.getElementById("formRequiredRole").value
    };

    const url = id ? `/api/admin/navigation/${id}` : "/api/admin/navigation";
    const method = id ? "PUT" : "POST";
    await requestJson(url, method, payload);
    window.location.reload();
}

async function deleteNavigation(event) {
    const id = event.target.dataset.id;
    if (!id || !confirm("Delete this menu?")) {
        return;
    }

    await requestJson(`/api/admin/navigation/${id}`, "DELETE");
    window.location.reload();
}

async function toggleStatus(event) {
    const button = event.target;
    const id = button.dataset.id;
    const field = button.dataset.field;
    const enabled = button.dataset.enabled !== "true";

    const endpoint = field === "use" ? "use-yn" : "display-yn";
    await requestJson(`/api/admin/navigation/${id}/${endpoint}`, "PATCH", { enabled });
    window.location.reload();
}

function openModal() {
    const modal = document.getElementById("navigationModal");
    modal.classList.add("show");
    modal.style.display = "flex";
}

function closeModal() {
    const modal = document.getElementById("navigationModal");
    modal.classList.remove("show");
    modal.style.display = "none";
}

function resetForm() {
    const form = document.getElementById("navigationForm");
    form.reset();
    document.getElementById("formNavigationItemId").value = "";
    document.getElementById("formDepth").value = "1";
    document.getElementById("formSortOrder").value = "1";
    document.getElementById("formUseYn").checked = true;
    document.getElementById("formDisplayYn").checked = true;
    document.getElementById("formRequiredRole").value = "USER";
}

function toNumber(value) {
    return value ? Number(value) : null;
}

async function requestJson(url, method, payload) {
    const options = {
        method,
        headers: {
            "Content-Type": "application/json"
        }
    };

    if (payload !== undefined) {
        options.body = JSON.stringify(payload);
    }

    const response = await fetch(url, options);
    if (!response.ok) {
        const message = await response.text();
        alert(message || "요청이 실패했습니다.");
        throw new Error(message || "요청이 실패했습니다.");
    }
}
