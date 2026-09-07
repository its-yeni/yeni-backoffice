function toggleSidebar() {
    const sidebar = document.getElementById("sidebar");
    if (sidebar) {
        sidebar.classList.toggle("open");
    }
}

/* 관리자 작업 탭과 화면별 작업 상태를 브라우저 세션 동안 유지한다. */
window.AdminWorkspace = (function () {
    const TABS_KEY = "yeni-admin-workspace-tabs-v1";
    const STATE_PREFIX = "yeni-admin-page-state-v1:";
    const MAX_TABS = 8;
    let skipNextPagehideSave = false;
    const TAB_TITLES = {
        "/admin/operations-dashboard":"운영 대시보드", "/admin/commerce/products":"상품 관리",
        "/admin/commerce/categories":"카테고리 관리", "/admin/commerce/options":"옵션 관리",
        "/admin/commerce/product-options":"상품 옵션 설정", "/admin/commerce/preview":"구매·결제 시뮬레이션",
        "/admin/commerce/orders":"주문 관리", "/admin/commerce/stores":"매장 관리",
        "/admin/commerce/suppliers":"공급처 관리", "/admin/commerce/purchase-orders":"발주 관리",
        "/admin/commerce/stock-counts":"재고 실사",
        "/admin/commerce/receiving":"입고 관리", "/admin/commerce/inventory":"재고 현황",
        "/admin/commerce/inventory/transfers":"재고 이동", "/admin/commerce/shipments":"출고 관리",
        "/admin/commerce/deliveries":"배송 관리", "/admin/commerce/returns":"반품 관리",
        "/admin/commerce/inventory/lots":"LOT·유통기한", "/admin/commerce/inventory/replenishment":"발주 제안",
        "/admin/commerce/inventory/insights":"재고 인사이트",
        "/admin/commerce/inventory/transactions":"입출고 내역", "/admin/payment-operations":"결제 예외 처리",
        "/admin/payment-operations/sales-ledger":"매출 원장", "/admin/payment-operations/pending-sales":"미확정 매출",
        "/admin/payment-operations/recovery-tasks":"복구 작업", "/admin/payment-operations/accounting":"회계 · 분개장",
        "/admin/payment-operations/sales-analytics":"매출 분석", "/admin/payment-operations/settlements":"정산 마감",
        "/admin/payment-operations/settlements/reconciliation":"PG 대사", "/admin/payment-operations/pg-reconciliation":"PG 대사",
        "/admin/analytics":"운영 분석", "/admin/analytics/orders":"주문 분석", "/admin/analytics/payments":"결제 분석",
        "/admin/analytics/settlements":"정산 분석", "/admin/analytics/inventory":"재고 분석",
        "/admin/database-spec":"DB 명세", "/admin/audit-logs":"감사 로그", "/admin/navigation":"메뉴 관리"
    };

    // 한 화면 안에서 하위 탭으로 이동하는 URL은 작업 탭(상단) 하나로 묶는다.
    // 예: /admin/analytics/orders·payments·… → 전부 "운영 분석" 탭.
    const TAB_ALIASES = [
        [/^\/admin\/analytics(\/.*)?$/, "/admin/analytics"]
    ];
    function canonicalPath(path) {
        for (const [re, canon] of TAB_ALIASES) if (re.test(path)) return canon;
        return path;
    }

    function pageKey() {
        return location.pathname + "|" + (activeBrandId() || 0) + "|" + (activeStoreId() || 0);
    }

    function readTabs() {
        try {
            const stored = JSON.parse(sessionStorage.getItem(TABS_KEY) || "[]");
            const unique = new Map();
            stored.forEach(tab => {
                let path = tab.path;
                try { path = new URL(tab.url || tab.path, location.origin).pathname; }
                catch (ignore) { path = tab.path; }
                if (!path || !path.startsWith("/admin/")) return;
                const canon = canonicalPath(path);
                const url = canon === path ? (tab.url || path) : canon;
                unique.set(canon, {path:canon, url:url, title:TAB_TITLES[canon] || tab.title || "관리 화면"});
            });
            const normalized = Array.from(unique.values()).slice(-MAX_TABS);
            if (JSON.stringify(stored) !== JSON.stringify(normalized)) sessionStorage.setItem(TABS_KEY, JSON.stringify(normalized));
            return normalized;
        }
        catch (ignore) { return []; }
    }

    function writeTabs(tabs) {
        let list = tabs.slice();
        // MAX_TABS를 넘으면 가장 오래된 것부터 버리되, 지금 보고 있는 탭은 남긴다.
        while (list.length > MAX_TABS) {
            const dropIndex = list[0].path === location.pathname && list.length > 1 ? 1 : 0;
            list.splice(dropIndex, 1);
        }
        sessionStorage.setItem(TABS_KEY, JSON.stringify(list));
    }

    function pageTitle() {
        if (TAB_TITLES[location.pathname]) return TAB_TITLES[location.pathname];
        // 본문 페이지 헤더만 본다 — 상단바의 설계 노트 드로어(h2)를 제목으로 잡지 않도록 제외.
        const candidates = document.querySelectorAll("main .page-header h1, main .page-header h2, main .page-header h3, main .page-title");
        for (const node of candidates) {
            if (node.closest(".topbar") || node.closest(".design-note-drawer")) continue;
            const text = node.textContent.trim();
            if (text) return text;
        }
        return document.title.split("-")[0].trim() || "관리 화면";
    }

    function rememberCurrentTab() {
        if (!location.pathname.startsWith("/admin/")) return [];
        const tabs = readTabs();
        const canon = canonicalPath(location.pathname);
        const aliased = canon !== location.pathname;
        const existing = tabs.find(tab => tab.path === canon);
        if (existing) {
            if (!aliased) existing.url = location.href;
            existing.title = TAB_TITLES[canon] || pageTitle();
        } else {
            tabs.push({ path: canon, url: aliased ? canon : location.href, title: TAB_TITLES[canon] || pageTitle() });
        }
        writeTabs(tabs);
        return readTabs();
    }

    function render() { renderCrumb(); renderTabs(); }

    /* 페이지 제목 자리에 "그룹 › 화면명" 브레드크럼을 넣고, 큰 h1 제목은 감춘다. */
    function renderCrumb() {
        const ph = document.querySelector("main .page-header");
        if (!ph) return;
        const wrap = ph.querySelector(":scope > div:not(.page-header-actions)") || ph;
        const heading = wrap.querySelector("h1, h2, h3, .page-title");

        // 하위 탭으로 들어온 화면(예: /admin/analytics/payments)은 nav 활성 항목이 없다 —
        // 이 경우 대표 경로(/admin/analytics)의 nav 항목을 기준으로 잡아 크럼을 일관되게 유지한다.
        const active = document.querySelector(".nav a.active")
            || document.querySelector('.nav a[href="' + canonicalPath(location.pathname) + '"]');
        let group = "", name = "";
        if (active) {
            name = (active.querySelector(".nav-item-label") || active).textContent.trim();
            const g = active.closest(".nav-group");
            const t = g && g.querySelector(".nav-title");
            if (t) group = t.textContent.trim();
        }
        if (!name) name = heading ? heading.textContent.trim() : (TAB_TITLES[location.pathname] || "");
        // 그룹명이 화면명 앞에 이미 붙어 있으면(예: "결제 예외 처리" / 그룹 "결제 · 정산") 축약 없이 그대로 둔다.

        let crumb = wrap.querySelector(".page-crumb");
        if (!crumb) {
            crumb = document.createElement("nav");
            crumb.className = "page-crumb";
            crumb.setAttribute("aria-label", "현재 위치");
            wrap.insertBefore(crumb, wrap.firstChild);
        }
        crumb.innerHTML = (group ? `<span class="crumb-group">${escapeHtml(group)}</span><span class="crumb-sep">›</span>` : "")
            + `<span class="crumb-current">${escapeHtml(name)}</span>`;
        if (heading) heading.hidden = true;
    }

    /* 열린 작업 화면 탭 (세션 동안 유지, 최대 8개) */
    function renderTabs() {
        const host = document.getElementById("workspaceTabs");
        if (!host) return;
        const tabs = rememberCurrentTab();
        const here = canonicalPath(location.pathname);
        host.innerHTML = tabs.map(tab => {
            const active = tab.path === here;
            return `<div class="workspace-tab${active ? " active" : ""}" data-workspace-path="${escapeHtml(tab.path)}">`
                + `<a class="workspace-tab-label" href="${escapeHtml(tab.url)}"${active ? ' aria-current="page"' : ""}>${escapeHtml(tab.title)}</a>`
                + `<button class="workspace-tab-close" type="button" data-close-workspace="${escapeHtml(tab.path)}" aria-label="${escapeHtml(tab.title)} 탭 닫기">×</button></div>`;
        }).join("")
            + (tabs.length > 1 ? `<button class="workspace-tab-closeall" type="button" data-close-workspace-all aria-label="다른 탭 모두 닫기">다른 탭 닫기</button>` : "");
        const closeAll = host.querySelector("[data-close-workspace-all]");
        if (closeAll) closeAll.addEventListener("click", event => {
            event.preventDefault();
            const current = readTabs().filter(tab => tab.path === here);
            readTabs().forEach(tab => {
                if (tab.path !== here && window.AdminHtmxNavigation) window.AdminHtmxNavigation.dropCachedView(tab.path);
            });
            writeTabs(current);
            renderTabs();
        });
        host.querySelectorAll("[data-close-workspace]").forEach(button => {
            button.addEventListener("click", event => {
                event.preventDefault();
                event.stopPropagation();
                const path = button.dataset.closeWorkspace;
                const currentTabs = readTabs();
                const index = currentTabs.findIndex(tab => tab.path === path);
                const remaining = currentTabs.filter(tab => tab.path !== path);
                writeTabs(remaining);
                if (window.AdminHtmxNavigation) window.AdminHtmxNavigation.dropCachedView(path);
                if (path === here) {
                    const fallback = remaining[Math.min(Math.max(index - 1, 0), remaining.length - 1)];
                    location.href = fallback ? fallback.url : "/admin/operations-dashboard";
                } else renderTabs();
            });
        });
        host.querySelectorAll('.workspace-tab.active .workspace-tab-label').forEach(link => {
            link.addEventListener('click', event => event.preventDefault());
        });
    }

    function serializableControls() {
        return Array.from(document.querySelectorAll("main input, main select, main textarea"))
            .filter(control => !["file", "password"].includes(control.type));
    }

    function savePageState() {
        if (!location.pathname.startsWith("/admin/")) return;
        const controls = [];
        serializableControls().forEach((control, index) => {
            const sameName = control.name
                ? serializableControls().filter(item => item.name === control.name).indexOf(control)
                : -1;
            controls.push({
                id: control.id || null,
                name: control.name || null,
                nameIndex: sameName,
                index: index,
                value: control.type === "checkbox" || control.type === "radio" ? control.checked : control.value
            });
        });
        sessionStorage.setItem(STATE_PREFIX + pageKey(), JSON.stringify({
            controls: controls,
            scrollY: window.scrollY,
            url: location.href,
            savedAt: Date.now()
        }));
        const tabs = readTabs();
        const current = tabs.find(tab => tab.path === location.pathname);
        if (current) current.url = location.href;
        writeTabs(tabs);
    }

    function restorePageState(finalPass) {
        let state;
        try { state = JSON.parse(sessionStorage.getItem(STATE_PREFIX + pageKey()) || "null"); }
        catch (ignore) { return; }
        if (!state || !state.controls) return;
        const currentControls = serializableControls();
        const savedControls = Array.isArray(state.controls)
            ? state.controls
            : Object.entries(state.controls).map(([id, value]) => ({id: id, value: value}));
        savedControls.forEach(saved => {
            let control = saved.id ? document.getElementById(saved.id) : null;
            if (!control && saved.name) {
                control = currentControls.filter(item => item.name === saved.name)[saved.nameIndex];
            }
            if (!control && Number.isInteger(saved.index)) control = currentControls[saved.index];
            if (!control || control.dataset.noWorkspaceRestore === "true") return;
            if (control.type === "checkbox" || control.type === "radio") control.checked = Boolean(saved.value);
            else control.value = saved.value;
        });
        requestAnimationFrame(() => window.scrollTo(0, Number(state.scrollY || 0)));
        document.dispatchEvent(new CustomEvent("workspace:state-restored", { detail: state }));
        if (finalPass) {
            const searchButton = document.querySelector('main button[id*="search"], main button[id*="filter-apply"]');
            if (searchButton && !searchButton.disabled) searchButton.click();
        }
    }

    function initialize() {
        if (!location.pathname.startsWith("/admin/")) return;
        render();
        window.setTimeout(() => restorePageState(false), 0);
        window.setTimeout(() => restorePageState(false), 350);
        window.setTimeout(() => restorePageState(true), 900);
        document.addEventListener("input", event => {
            if (event.target.closest("main")) savePageState();
        });
        document.addEventListener("change", event => {
            if (event.target.closest("main")) savePageState();
        });
        let scrollTimer;
        window.addEventListener("scroll", () => {
            window.clearTimeout(scrollTimer);
            scrollTimer = window.setTimeout(savePageState, 120);
        }, { passive: true });
        window.addEventListener("pagehide", () => {
            if (skipNextPagehideSave) {
                skipNextPagehideSave = false;
                return;
            }
            savePageState();
        });
        document.addEventListener("click", event => {
            const link = event.target.closest("a[href]");
            let adminLink = false;
            try { adminLink = Boolean(link) && new URL(link.href, location.origin).pathname.startsWith("/admin/"); }
            catch (ignore) { adminLink = false; }
            if (adminLink && !link.closest("#workspaceTabs")) {
                if (link.hasAttribute("data-reset-workspace-state")) {
                    sessionStorage.removeItem(STATE_PREFIX + pageKey());
                    skipNextPagehideSave = true;
                } else {
                    savePageState();
                }
            }
        }, true);
    }

    return { initialize: initialize, save: savePageState, restore: restorePageState, refresh: render };
})();

document.addEventListener("DOMContentLoaded", AdminWorkspace.initialize);

/*
 * Thymeleaf 화면을 점진적으로 전환하는 HTMX 작업 셸.
 * 공통 사이드바/상단바는 유지하고 본문, 페이지별 자산, 팝업만 교체한다.
 */
window.AdminHtmxNavigation = (function () {
    const HTMX_SRC = "/webjars/htmx.org/2.0.8/dist/htmx.min.js";
    const SUPPORTED = [
        "/admin/commerce/products",
        "/admin/commerce/categories",
        "/admin/commerce/options",
        "/admin/commerce/product-options",
        "/admin/commerce/preview",
        "/admin/commerce/stores",
        "/admin/commerce/orders",
        "/admin/commerce/receiving",
        "/admin/commerce/inventory",
        "/admin/commerce/inventory/transfers",
        "/admin/commerce/shipments",
        "/admin/commerce/deliveries",
        "/admin/commerce/returns",
        "/admin/commerce/inventory/transactions",
        "/admin/payment-operations",
        "/admin/payment-operations/sales-ledger",
        "/admin/payment-operations/pending-sales",
        "/admin/payment-operations/recovery-tasks",
        "/admin/payment-operations/accounting",
        "/admin/payment-operations/sales-analytics",
        "/admin/payment-operations/settlements",
        "/admin/payment-operations/settlements/reconciliation",
        "/admin/payment-operations/pg-reconciliation",
        "/admin/operations-dashboard",
        "/admin/database-spec",
        "/admin/audit-logs",
        "/admin/navigation"
    ];
    let responseDocument = null;
    let loadingScript = Promise.resolve();
    const scriptSourceCache = new Map();
    const viewCache = new Map();

    function supportedPath(href) {
        try { return SUPPORTED.includes(new URL(href, location.origin).pathname); }
        catch (ignore) { return false; }
    }

    function enhanceLinks(root) {
        (root || document).querySelectorAll('a[href]').forEach(link => {
            if (link.hasAttribute("data-full-navigation") || !supportedPath(link.href)) return;
            link.setAttribute("hx-get", link.getAttribute("href"));
            link.setAttribute("hx-target", "main > section:first-of-type");
            link.setAttribute("hx-select", "main > section:first-of-type");
            link.setAttribute("hx-swap", "outerHTML show:top");
            link.setAttribute("hx-push-url", "true");
            if (window.htmx) window.htmx.process(link);
        });
    }

    function pageAssets(doc) {
        return {
            styles: Array.from(doc.querySelectorAll('link[rel="stylesheet"][href]')).map(link => link.href),
            inlineScripts: Array.from(doc.querySelectorAll('script:not([src])')).map(script => script.textContent)
                .filter(code => code.trim() && !code.includes("sidebar-collapsed") && !code.includes("var aliases=")),
            scripts: Array.from(doc.querySelectorAll('script[src]')).map(script => script.src)
                .filter(src => !src.includes("/js/common.js") && !src.includes("/js/admin-pagination.js") && !src.includes("/js/demo-guide.js"))
        };
    }

    function installStyles(styles) {
        const loaded = new Set(Array.from(document.querySelectorAll('link[rel="stylesheet"][href]')).map(link => link.href));
        styles.forEach(href => {
            if (loaded.has(href)) return;
            const link = document.createElement("link");
            link.rel = "stylesheet";
            link.href = href;
            link.dataset.htmxPageAsset = "true";
            document.head.appendChild(link);
        });
    }

    function executeScript(src) {
        if (!scriptSourceCache.has(src)) {
            scriptSourceCache.set(src, fetch(src).then(response => {
                if (!response.ok) throw new Error("화면 스크립트를 불러오지 못했습니다: " + src);
                return response.text();
            }).catch(error => {
                scriptSourceCache.delete(src);
                throw error;
            }));
        }

        return scriptSourceCache.get(src).then(code => {
            const readyCallbacks = [];
            const loadCallbacks = [];
            const originalDocumentAdd = document.addEventListener;
            const originalWindowAdd = window.addEventListener;

            document.addEventListener = function (type, listener, options) {
                if (type === "DOMContentLoaded") {
                    readyCallbacks.push(listener);
                    return;
                }
                return originalDocumentAdd.call(document, type, listener, options);
            };
            window.addEventListener = function (type, listener, options) {
                if (type === "load") {
                    loadCallbacks.push(listener);
                    return;
                }
                return originalWindowAdd.call(window, type, listener, options);
            };

            try {
                Function(code + "\n//# sourceURL=" + src)();
            } finally {
                document.addEventListener = originalDocumentAdd;
                window.addEventListener = originalWindowAdd;
            }

            const readyEvent = new Event("DOMContentLoaded");
            const loadEvent = new Event("load");
            const invoke = (listener, target, event) => typeof listener === "function"
                ? listener.call(target, event)
                : listener?.handleEvent?.call(listener, event);
            return Promise.all([
                ...readyCallbacks.map(listener => invoke(listener, document, readyEvent)),
                ...loadCallbacks.map(listener => invoke(listener, window, loadEvent))
            ]);
        });
    }

    function installPageExtras(doc) {
        document.querySelectorAll("[data-htmx-page-extra]").forEach(element => element.remove());
        const layout = doc.querySelector(".layout");
        if (!layout || !layout.parentElement) return;
        Array.from(layout.parentElement.children).forEach(element => {
            if (element === layout || element.tagName === "SCRIPT") return;
            const clone = document.importNode(element, true);
            clone.dataset.htmxPageExtra = "true";
            document.body.appendChild(clone);
        });
    }

    function cacheCurrentView() {
        const section = document.querySelector("main > section:first-of-type");
        if (!section || section.dataset.workspacePlaceholder === "true") return null;
        const path = location.pathname;
        const placeholder = section.cloneNode(true);
        placeholder.dataset.workspacePlaceholder = "true";
        placeholder.setAttribute("aria-hidden", "true");
        placeholder.inert = true;
        section.replaceWith(placeholder);
        const extras = Array.from(document.querySelectorAll("[data-htmx-page-extra]"));
        extras.forEach(element => element.remove());
        viewCache.set(path, {
            section: section,
            extras: extras,
            title: document.title,
            url: location.href,
            scrollY: window.scrollY
        });
        return placeholder;
    }

    function restoreCachedView(path, url) {
        const cached = viewCache.get(path);
        if (!cached) return false;
        const placeholder = cacheCurrentView() || document.querySelector("main > section:first-of-type");
        if (!placeholder) return false;
        placeholder.replaceWith(cached.section);
        document.querySelectorAll("[data-htmx-page-extra]").forEach(element => element.remove());
        cached.extras.forEach(element => document.body.appendChild(element));
        history.pushState({workspacePath: path}, "", url || cached.url);
        document.title = cached.title;
        viewCache.delete(path);
        responseDocument = null;
        updateShell();
        cached.section.classList.add("workspace-page-enter");
        document.body.classList.remove("workspace-navigating");
        window.setTimeout(() => cached.section.classList.remove("workspace-page-enter"), 240);
        requestAnimationFrame(() => window.scrollTo(0, Number(cached.scrollY || 0)));
        document.dispatchEvent(new CustomEvent("admin:page-restored", {detail: {path: path}}));
        return true;
    }

    function handleNavigationClick(event) {
        const link = event.target.closest("a[href]");
        if (!link || !supportedPath(link.href)) return;
        const destination = new URL(link.href, location.origin);
        if (destination.pathname === location.pathname) return;

        if (link.closest("#workspaceTabs") && viewCache.has(destination.pathname)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            restoreCachedView(destination.pathname, destination.href);
            return;
        }

        document.body.classList.add("workspace-navigating");
        cacheCurrentView();
    }

    function updateShell() {
        document.title = responseDocument?.title || document.title;
        document.querySelectorAll(".nav a").forEach(link => {
            const href = new URL(link.href).pathname;
            const active = href === location.pathname
                || (href === "/admin/commerce/options" && location.pathname === "/admin/commerce/product-options")
                || (href === "/admin/payment-operations/settlements/reconciliation" && location.pathname === "/admin/payment-operations/pg-reconciliation");
            link.classList.toggle("active", active);
            const group = link.closest(".nav-group");
            if (active && group) setGroupExpanded(group, true);
        });
        if (window.AdminWorkspace) window.AdminWorkspace.refresh();
        enhanceLinks(document);
    }

    function bindHtmxEvents() {
        document.addEventListener("click", handleNavigationClick, true);
        document.body.addEventListener("htmx:beforeSwap", event => {
            if (!event.detail.xhr?.responseText) return;
            responseDocument = new DOMParser().parseFromString(event.detail.xhr.responseText, "text/html");
            installStyles(pageAssets(responseDocument).styles);
        });
        document.body.addEventListener("htmx:afterSwap", () => {
            if (!responseDocument) return;
            const enteredSection = document.querySelector("main > section:first-of-type");
            if (enteredSection) {
                enteredSection.classList.add("workspace-page-enter");
                window.setTimeout(() => enteredSection.classList.remove("workspace-page-enter"), 240);
            }
            document.body.classList.remove("workspace-navigating");
            const assets = pageAssets(responseDocument);
            installPageExtras(responseDocument);
            updateShell();
            assets.inlineScripts.forEach(code => Function(code)());
            loadingScript = assets.scripts.reduce((chain, src) => chain.then(() => executeScript(src)), Promise.resolve())
                .then(() => {
                    if (window.AdminWorkspace) window.AdminWorkspace.restore(true);
                })
                .then(() => document.dispatchEvent(new CustomEvent("admin:page-ready", {detail: {path: location.pathname}})))
                .catch(error => {
                    console.error(error);
                    AppToast.error("화면 초기화에 실패해 전체 화면으로 다시 엽니다.");
                    location.reload();
                });
            responseDocument = null;
        });
        document.body.addEventListener("htmx:afterSettle", () => {
            // hx-push-url 반영이 끝난 실제 경로를 기준으로 탭을 최종 정리한다.
            updateShell();
        });
        document.body.addEventListener("htmx:responseError", () => {
            document.body.classList.remove("workspace-navigating");
            const cached = viewCache.get(location.pathname);
            const placeholder = document.querySelector('[data-workspace-placeholder="true"]');
            if (cached && placeholder) {
                placeholder.replaceWith(cached.section);
                cached.extras.forEach(element => document.body.appendChild(element));
                viewCache.delete(location.pathname);
            }
            AppToast.error("화면을 불러오지 못했습니다.");
        });
        document.body.addEventListener("htmx:historyRestore", () => {
            fetch(location.href, {headers: {"X-Workspace-Assets": "true"}})
                .then(response => response.text())
                .then(html => {
                    const doc = new DOMParser().parseFromString(html, "text/html");
                    const assets = pageAssets(doc);
                    installStyles(assets.styles);
                    installPageExtras(doc);
                    updateShell();
                    assets.inlineScripts.forEach(code => Function(code)());
                    return assets.scripts.reduce((chain, src) => chain.then(() => executeScript(src)), Promise.resolve())
                        .then(() => {
                            if (window.AdminWorkspace) window.AdminWorkspace.restore(true);
                        });
                })
                .catch(error => {
                    console.error(error);
                    location.reload();
                });
        });
    }

    function initialize() {
        enhanceLinks(document);
        const script = document.createElement("script");
        script.src = HTMX_SRC;
        script.onload = () => { bindHtmxEvents(); enhanceLinks(document); };
        script.onerror = () => console.warn("HTMX를 불러오지 못해 일반 페이지 이동을 사용합니다.");
        document.head.appendChild(script);
    }

    function dropCachedView(path) {
        viewCache.delete(path);
    }

    return {
        initialize: initialize,
        enhanceLinks: enhanceLinks,
        dropCachedView: dropCachedView
    };
})();

document.addEventListener("DOMContentLoaded", AdminHtmxNavigation.initialize);

window.activeStoreCode=function(){return localStorage.getItem("commerce-store-code")||"";};
window.activeBrandId=function(){return Number(new URLSearchParams(location.search).get("brandId")||localStorage.getItem("commerce-brand-id")||0);};
window.activeStoreId=function(){return Number(new URLSearchParams(location.search).get("storeId")||localStorage.getItem("commerce-store-id")||0);};
window.operationalStoreLabel=function(storeId){if(!storeId)return "매장 미지정";try{const stores=JSON.parse(localStorage.getItem("commerce-store-directory")||"[]");return stores.find(store=>Number(store.id)===Number(storeId))?.storeName||`매장 #${storeId}`;}catch(ignore){return `매장 #${storeId}`;}};

/* 상단바 브랜드·매장 선택기. "전체"(값 0)를 포함하며, 이 값이 화면 전반의 조회 범위를 결정한다.
   - 매장을 고르면 그 매장 데이터만, "전체 매장"이면 모든 매장 합산.
   - activeStoreId()가 0이면 withOperationalStore()가 storeId를 붙이지 않아 서버가 전체를 반환한다. */
document.addEventListener("DOMContentLoaded",async function(){
    if(!location.pathname.startsWith("/admin/"))return;
    const SERVER_SCOPED=["/admin/operations-dashboard"]; // 서버 렌더링 → storeId 쿼리로만 좁혀짐
    try{
        const brands=await apiGet("/admin/api/brands");
        if(!brands.length)return;
        let brand=activeBrandId()?brands.find(b=>b.id===activeBrandId())||null:null;
        const allStores=await apiGet("/admin/api/commerce/stores");
        localStorage.setItem("commerce-store-directory",JSON.stringify(allStores));
        let stores=brand?allStores.filter(store=>Number(store.brandId)===Number(brand.id)):allStores;
        localStorage.setItem("commerce-brand-store-ids",JSON.stringify(stores.map(store=>Number(store.id))));
        localStorage.setItem("commerce-brand-store-scope",String(brand?brand.id:0));
        let store=activeStoreId()?stores.find(s=>s.id===activeStoreId())||null:null;
        persistCommerceContext(brand,store);

        const context=document.createElement("div");context.className="global-commerce-context";
        const brandSelect=document.createElement("select");brandSelect.className="global-brand-select";brandSelect.setAttribute("aria-label","현재 브랜드");
        brandSelect.innerHTML='<option value="0">전체 브랜드</option>'+brands.map(b=>`<option value="${b.id}">${escapeHtml(b.brandName)}</option>`).join("");
        brandSelect.value=String(brand?brand.id:0);
        const storeSelect=document.createElement("select");storeSelect.className="global-store-select";storeSelect.setAttribute("aria-label","현재 매장");
        const storeOptions=list=>'<option value="0">전체 매장</option>'+list.filter(s=>s.active).map(s=>`<option value="${s.id}">${escapeHtml(s.storeName)}</option>`).join("");
        storeSelect.innerHTML=storeOptions(stores);
        storeSelect.value=String(store?store.id:0);

        function applyContext(brandId,storeId){
            const url=new URL(location.href);
            storeId?url.searchParams.set("storeId",storeId):url.searchParams.delete("storeId");
            brandId?url.searchParams.set("brandId",brandId):url.searchParams.delete("brandId");
            location.href=url.toString();
        }
        brandSelect.onchange=()=>{
            const bid=Number(brandSelect.value);
            const scopedStores=bid?allStores.filter(store=>Number(store.brandId)===bid):allStores;
            localStorage.setItem("commerce-brand-store-ids",JSON.stringify(scopedStores.map(store=>Number(store.id))));
            localStorage.setItem("commerce-brand-store-scope",String(bid));
            persistCommerceContext(bid?brands.find(b=>b.id===bid):null,null);
            applyContext(bid,0);
        };
        storeSelect.onchange=()=>{
            const sid=Number(storeSelect.value);
            persistCommerceContext(brand,sid?stores.find(s=>s.id===sid):null);
            applyContext(brand?brand.id:0,sid);
        };

        const brandField=document.createElement("label");brandField.innerHTML="<span>브랜드</span>";brandField.append(brandSelect);
        const storeField=document.createElement("label");storeField.innerHTML="<span>매장</span>";storeField.append(storeSelect);
        context.append(brandField,storeField);document.querySelector(".topbar-context")?.append(context);

        const brandLabel=brand?brand.brandName:"전체 브랜드", storeLabel=store?store.storeName:"전체 매장";
        if(document.getElementById("console-brand"))document.getElementById("console-brand").textContent=brandLabel;
        if(document.getElementById("console-store"))document.getElementById("console-store").textContent=storeLabel;
        document.querySelectorAll("[data-operational-scope]").forEach(scope=>{
            const title=scope.querySelector("strong");
            if(title)title.textContent=`${brandLabel} · ${storeLabel}`;
        });

        if(store&&SERVER_SCOPED.includes(location.pathname)&&!new URLSearchParams(location.search).has("storeId")){
            const url=new URL(location.href);url.searchParams.set("storeId",store.id);location.replace(url.toString());
        }
    }catch(ignore){}
});
function persistCommerceContext(brand,store){
    localStorage.setItem("commerce-brand-id",String(brand&&brand.id?brand.id:0));
    localStorage.setItem("commerce-store-id",String(store&&store.id?store.id:0));
    if(store&&store.storeCode)localStorage.setItem("commerce-store-code",store.storeCode);
    else localStorage.removeItem("commerce-store-code");
}
function reloadWithContext(brandId,storeId){const url=new URL(location.href);url.searchParams.set("brandId",brandId);url.searchParams.set("storeId",storeId);location.href=url.toString();}

document.addEventListener("click", function(event) {
    const sidebar = document.getElementById("sidebar");
    const mobileMenuBtn = document.querySelector(".mobile-menu-btn");

    if (sidebar && mobileMenuBtn && window.innerWidth <= 768) {
        if (!sidebar.contains(event.target) && !mobileMenuBtn.contains(event.target)) {
            sidebar.classList.remove("open");
        }
    }
});

document.addEventListener("DOMContentLoaded", function() {
    initializeSkipLink();
    initializeSidebarGroups();
    initializeSidebarCollapse();
    showDashboardFilterContext();
    initializeDatePresets();
    initializeDataViewport();
    initializeAggregationBasis();
});

function initializeSkipLink() {
    const main = document.querySelector("main > section");
    if (!main || document.querySelector(".skip-to-content")) return;
    main.id = main.id || "main-content";
    main.tabIndex = -1;
    const link = document.createElement("a");
    link.className = "skip-to-content";
    link.href = `#${main.id}`;
    link.textContent = "본문 업무로 바로가기";
    document.body.prepend(link);
}

function initializeAggregationBasis() {
    const path = location.pathname;
    const configs = [
        [/\/admin\/operations-dashboard$/, "매출 원장 기준", "영업일 오늘 · SALE은 더하고 CANCEL은 차감한 순매출 · 현재 브랜드/매장 범위"],
        [/\/sales-ledger$/, "불변 원장 기준", "영업일 기준 · 승인(SALE)과 취소(CANCEL)를 별도 보관 · 취소 금액은 음수로 합산"],
        [/\/sales-analytics$/, "매출 명세 기준", "상품 명세의 SALE·CANCEL 순액 · ‘확정 매출만’ 선택 시 구매확정 건으로 제한"],
        [/\/settlements$/, "정산서 기준", "구매확정 원장만 포함 · 수수료와 VAT 차감 · DRAFT → CONFIRMED → PAID 상태 기준"],
        [/\/analytics\//, "운영 분석 기준", "선택 기간과 현재 브랜드/매장 범위 · 화면별 주문/결제/정산/재고 원천 데이터 기준"]
    ];
    const matched = configs.find(([pattern]) => pattern.test(path));
    if (!matched) return;
    const section = document.querySelector("main > section"), header = section?.querySelector(":scope > .page-header");
    if (!section || !header || section.querySelector(":scope > .aggregation-basis")) return;
    const note = document.createElement("aside");
    note.className = "aggregation-basis";
    note.setAttribute("aria-label", "집계 기준");
    note.innerHTML = `<strong>${matched[1]}</strong><span>${matched[2]}</span><time>조회 ${new Date().toLocaleString("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false })}</time>`;
    header.insertAdjacentElement("afterend", note);
}

function initializeDataViewport() {
    // 과거엔 목록 섹션/탭패널/표를 인라인 display:flex + flex:1 1 0 + height 로 잠가
    // "표 본문만 스크롤"시켰으나, 이 방식이 첫 페인트에서 0높이로 무너져(리사이즈해야
    // 데이터가 보임) 문제가 잦았다. 이제는 콘텐츠 섹션이 통째로 스크롤되므로(레이아웃
    // 셸 + components.css) 여기서 인라인 스타일을 강제하지 않는다. 혹시 이전 세션에서
    // 남은 인라인 스타일이 있으면 걷어낸다.
    document.querySelectorAll("main > section, main > section .page-tab-panel, main > section .data-table-wrap").forEach(el => {
        ["display", "flex", "height", "min-height", "overflow", "flex-direction", "padding-bottom"].forEach(prop => {
            if (el.style.getPropertyPriority(prop) === "important") el.style.removeProperty(prop);
        });
    });
}

function showDashboardFilterContext() {
    const params = new URLSearchParams(location.search);
    if (params.get("from") !== "dashboard") return;
    const labels = {
        "today-orders": "오늘 주문",
        "today-sales": "오늘 매출",
        "pending-shipments": "출고 대기",
        "requested-returns": "접수 대기 반품",
        "queue-item": "운영 예외 항목",
        "unknown-payments": "결과 불명 거래",
        "delayed-deliveries": "배송 지연",
        "draft-settlements": "정산 초안 검토"
    };
    const section = document.querySelector("main > section");
    const anchor = section && (section.querySelector(".filter-bar, .ops-filter-bar, .cmc-toolbar, .pg-filter-bar") || section.firstElementChild);
    if (!section || !anchor) return;
    const notice = document.createElement("div");
    notice.className = "dashboard-filter-context";
    notice.innerHTML = `<strong>대시보드 선택 조건</strong><span>${labels[params.get("focus")] || "선택 항목"} 데이터만 표시 중입니다.</span><a href="${location.pathname}">전체 보기</a>`;
    anchor.insertAdjacentElement("beforebegin", notice);
    anchor.querySelectorAll("input, select").forEach(control => control.addEventListener("change", clearContext, { once: true }));
    function clearContext() {
        notice.remove();
        history.replaceState(null, "", location.pathname);
    }
}

function initializeDatePresets() {
    const configs = [
        ["ledger-start", "ledger-end", "ledger-search"], ["payment-start", "payment-end", null],
        ["settlement-start", "settlement-end", "settlement-search"], ["ps-start", "ps-end", "ps-search"],
        ["sa-start", "sa-end", "sa-search"]
    ];
    const format = date => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
    configs.forEach(([startId, endId, searchId]) => {
        const start = document.getElementById(startId), end = document.getElementById(endId);
        if (!start || !end || start.closest(".filter-period-owner")?.querySelector(".filter-period-presets")) return;
        const host = document.createElement("div"); host.className = "filter-period-presets";
        const buttons = [];
        const apply = (button, days, silent) => {
            const now = new Date(), from = new Date(); from.setDate(now.getDate() - days);
            start.value = format(from); end.value = format(now);
            host.querySelectorAll("button").forEach(item => item.classList.toggle("active", item === button));
            if (silent) return;
            start.dispatchEvent(new Event("change", { bubbles: true })); end.dispatchEvent(new Event("change", { bubbles: true }));
            if (searchId) document.getElementById(searchId)?.click();
        };
        [[0, "오늘"], [6, "7일"], [29, "30일"]].forEach(([days, label]) => {
            const button = document.createElement("button"); button.type = "button"; button.textContent = label;
            button.onclick = () => apply(button, days, false);
            host.appendChild(button); buttons.push([button, days]);
        });
        const range = start.parentElement;
        range.classList.add("filter-period-owner");
        range.insertAdjacentElement("beforebegin", host);
        // 목록 화면 날짜 필터 기본값은 "오늘"으로 통일 — 아직 비어 있을 때만 채운다.
        if (!start.value && !end.value) apply(buttons[0][0], 0, true);
        else {
            const now = format(new Date());
            const match = buttons.find(([, days]) => {
                const from = new Date(); from.setDate(new Date().getDate() - days);
                return start.value === format(from) && end.value === now;
            });
            if (match) match[0].classList.add("active");
        }
    });
}

function initializeSidebarCollapse() {
    const button = document.getElementById("sidebarCollapseBtn");
    const collapsed = localStorage.getItem("sidebar-collapsed") === "true";
    document.body.classList.toggle("sidebar-collapsed", collapsed);
    if (!button) return;
    button.addEventListener("click", function() {
        const next = !document.body.classList.contains("sidebar-collapsed");
        document.body.classList.toggle("sidebar-collapsed", next);
        localStorage.setItem("sidebar-collapsed", String(next));
        button.setAttribute("aria-label", next ? "사이드바 펼치기" : "사이드바 접기");
    });
}

window.AppToast = (function () {
    let host;
    function ensureHost() {
        if (host) return host;
        host = document.createElement("div");
        host.className = "toast-host";
        host.setAttribute("aria-live", "polite");
        document.body.appendChild(host);
        return host;
    }
    function show(message, type) {
        const toast = document.createElement("div");
        toast.className = "app-toast " + (type || "info");
        toast.setAttribute("role", type === "error" ? "alert" : "status");
        toast.textContent = message || "요청을 처리했습니다.";
        ensureHost().appendChild(toast);
        requestAnimationFrame(function () { toast.classList.add("open"); });
        window.setTimeout(function () {
            toast.classList.remove("open");
            window.setTimeout(function () { toast.remove(); }, 180);
        }, type === "error" ? 5000 : 3000);
    }
    return {
        show: show,
        success: function (message) { show(message, "success"); },
        error: function (message) { show(message, "error"); }
    };
})();

function initializeSidebarGroups() {
    document.querySelectorAll(".nav-group").forEach(function(group) {
        const groupCode = group.dataset.groupCode;
        const hasActive = group.dataset.hasActive === "true";
        const toggle = group.querySelector(".nav-group-toggle");
        const storageKey = "sidebar-group-" + groupCode;
        const saved = localStorage.getItem(storageKey);
        const expanded = hasActive || saved !== "collapsed";

        setGroupExpanded(group, expanded);

        if (toggle) {
            toggle.addEventListener("click", function() {
                const nextExpanded = group.classList.contains("is-collapsed");
                setGroupExpanded(group, nextExpanded);
                localStorage.setItem(storageKey, nextExpanded ? "expanded" : "collapsed");
            });
        }
    });
}

function setGroupExpanded(group, expanded) {
    const toggle = group.querySelector(".nav-group-toggle");
    group.classList.toggle("is-collapsed", !expanded);
    if (toggle) {
        toggle.setAttribute("aria-expanded", String(expanded));
    }
}

async function parseApiResponse(response) {
    const text = await response.text();
    const contentType = response.headers.get("content-type") || "";
    if (response.redirected || contentType.includes("text/html")) {
        throw new Error("서버가 JSON 대신 화면 응답을 반환했습니다. 세션이 만료됐을 수 있습니다.");
    }

    let data = {};
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (error) {
            throw new Error("서버 응답을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
    if (response.ok) {
        return data;
    }
    const fieldMessages = Array.isArray(data.fieldErrors) && data.fieldErrors.length > 0
        ? " " + data.fieldErrors.map(function (fieldError) {
            return fieldError.field + ": " + fieldError.message;
        }).join(" / ")
        : "";
    const requestId = data.requestId ? " (requestId: " + data.requestId + ")" : "";
    const error = new Error((data.message || "요청 처리에 실패했습니다.") + fieldMessages + requestId);
    error.response = data;
    throw error;
}

/* 여러 admin 페이지가 각자 다시 만들던 숫자·금액 포맷. */
window.AppFormat = Object.freeze({
    number(value) {
        return Number(value || 0).toLocaleString("ko-KR");
    },
    money(value) {
        return this.number(value) + "원";
    },
    compactNumber(value) {
        const number = Number(value || 0);
        if (Math.abs(number) >= 100000000) return (number / 100000000).toFixed(1).replace(".0", "") + "억";
        if (Math.abs(number) >= 10000) return Math.round(number / 10000).toLocaleString("ko-KR") + "만";
        return number.toLocaleString("ko-KR");
    }
});

function money(value) { return AppFormat.money(value); }

function escapeHtml(value) {
    const div = document.createElement("div");
    div.textContent = value === undefined || value === null ? "" : String(value);
    return div.innerHTML;
}

/* 결제ID·TID처럼 운영자가 자주 복사해서 쓰는 값을 렌더링하는 공통 헬퍼.
   페이지마다 복사 버튼을 따로 만들지 말고 이걸 사용한다. 클릭 이벤트는
   document 레벨에서 한 번만 위임 처리한다(아래 참고). */
function copyableValue(value, emptyLabel) {
    if (!value) return escapeHtml(emptyLabel || "-");
    return '<span class="copy-chip" data-copy="' + escapeHtml(value) + '" title="클릭해서 복사">'
        + '<span class="copy-chip-text">' + escapeHtml(value) + '</span>'
        + '<svg class="copy-chip-icon" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="11" height="11" rx="2"></rect><path d="M5 15V5a2 2 0 0 1 2-2h10"></path></svg>'
        + '</span>';
}

document.addEventListener("click", function (event) {
    const chip = event.target.closest(".copy-chip");
    if (!chip) return;
    // 복사칩이 클릭 가능한 행/카드 안에 있는 경우가 많아, 상세 열기 같은
    // 상위 클릭 핸들러가 같이 동작하지 않도록 여기서 막는다.
    event.stopPropagation();
    const value = chip.dataset.copy;
    if (!value || !navigator.clipboard) return;
    navigator.clipboard.writeText(value)
        .then(function () { AppToast.success("복사했습니다: " + value); })
        .catch(function () { AppToast.error("복사에 실패했습니다."); });
});

/* Mock 시나리오로 만들어진 데모 주문/거래를 실데이터와 구분해 보여주는 공통 배지.
   DemoOrderSeedInitializer/Mock 주문 생성이 공통으로 쓰는 기본 연락처를 기준으로 판단한다. */
function isDemoBuyerPhone(phone) {
    return phone === "010-0000-0000";
}
function demoBadge() {
    return '<span class="badge-demo" title="Mock 시나리오로 생성된 데모 데이터입니다">TEST</span>';
}

/* 데이터 요청 중 로딩 표시 — 상단 진행 바 + 0.5초 넘어가면 "잠시만 기다려 주세요" 안내.
   apiGet/apiPost 가 자동으로 호출하므로 페이지 코드는 신경 쓰지 않아도 된다. */
window.AppLoading = (function () {
    let active = 0, bar, pill, pillTimer;
    function ensure() {
        if (bar || !document.body) return;
        bar = document.createElement("div");
        bar.className = "app-loading-bar";
        pill = document.createElement("div");
        pill.className = "app-loading-pill";
        pill.textContent = "데이터를 불러오는 중입니다. 잠시만 기다려 주세요…";
        document.body.appendChild(bar);
        document.body.appendChild(pill);
    }
    function begin() {
        ensure();
        active++;
        if (bar) bar.classList.add("is-on");
        clearTimeout(pillTimer);
        pillTimer = setTimeout(function () { if (active > 0 && pill) pill.classList.add("is-on"); }, 500);
    }
    function end() {
        active = Math.max(0, active - 1);
        if (active > 0) return;
        clearTimeout(pillTimer);
        if (bar) bar.classList.remove("is-on");
        if (pill) pill.classList.remove("is-on");
    }
    async function track(promise) {
        begin();
        try {
            return await promise;
        } finally {
            end();
        }
    }
    return {
        begin: begin,
        end: end,
        show: begin,
        hide: end,
        track: track
    };
})();

window.AdminApi = (function () {
    const OPERATIONAL_PATHS = [
        "/commerce/orders",
        "/commerce/location-inventory",
        "/commerce/shipments",
        "/commerce/deliveries",
        "/commerce/returns",
        "/payment-operations/payments",
        "/sales-ledger",
        "/settlements",
        "/api/analytics/",
        "/admin/api/dashboard"
    ];

    function isOperationalScopedUrl(url) {
        return OPERATIONAL_PATHS.some(path => url.includes(path))
            || (url.includes("/admin/api/gl/") && !url.includes("/income-by-store"));
    }

    function withOperationalScope(url) {
        if (!isOperationalScopedUrl(url)) return url;
        const target = new URL(url, location.origin);
        const storeId = activeStoreId();
        const brandId = activeBrandId();
        if (storeId && !target.searchParams.has("storeId")) target.searchParams.set("storeId", storeId);
        if (brandId && !target.searchParams.has("brandId")) target.searchParams.set("brandId", brandId);
        return target.pathname + target.search;
    }

    async function ensureStoreDirectory(url) {
        if (!isOperationalScopedUrl(url) || localStorage.getItem("commerce-store-directory")) return;
        try {
            const stores = await parseApiResponse(await fetch("/admin/api/commerce/stores"));
            localStorage.setItem("commerce-store-directory", JSON.stringify(stores));
        } catch (ignore) {}
    }

    async function ensureBrandStoreIds(url) {
        const brandId = activeBrandId();
        if (!brandId || activeStoreId() || !isOperationalScopedUrl(url)) return;
        if (localStorage.getItem("commerce-brand-store-scope") === String(brandId)
            && localStorage.getItem("commerce-brand-store-ids")) return;
        try {
            const stores = await parseApiResponse(await fetch(`/admin/api/brands/${brandId}/stores`));
            localStorage.setItem("commerce-brand-store-ids", JSON.stringify(stores.map(store => Number(store.id))));
            localStorage.setItem("commerce-brand-store-scope", String(brandId));
        } catch (ignore) {}
    }

    function applyBrandScope(url, data) {
        if (!activeBrandId() || activeStoreId() || !isOperationalScopedUrl(url)) return data;
        let allowed = [];
        try {
            allowed = JSON.parse(localStorage.getItem("commerce-brand-store-ids") || "[]").map(Number);
        } catch (ignore) {}
        if (!allowed.length) return data;
        const filter = rows => rows.filter(row => !row
            || !Object.prototype.hasOwnProperty.call(row, "storeId")
            || (row.storeId != null && allowed.includes(Number(row.storeId))));
        if (Array.isArray(data)) return filter(data);
        if (data && Array.isArray(data.data)) return {...data, data: filter(data.data)};
        if (data && Array.isArray(data.rows)) return {...data, rows: filter(data.rows)};
        return data;
    }

    async function get(url) {
        AppLoading.begin();
        try {
            const data = await parseApiResponse(await fetch(withOperationalScope(url)));
            await ensureStoreDirectory(url);
            await ensureBrandStoreIds(url);
            return applyBrandScope(url, data);
        } finally {
            AppLoading.end();
        }
    }

    async function send(url, body, method) {
        const requestBody = {...(body || {})};
        if (url.includes("/settlements/batch/run") && activeStoreId()) requestBody.storeId = activeStoreId();
        AppLoading.begin();
        try {
            return await parseApiResponse(await fetch(withOperationalScope(url), {
                method: method || "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(requestBody)
            }));
        } finally {
            AppLoading.end();
        }
    }

    return {
        get: get,
        send: send,
        withOperationalScope: withOperationalScope,
        isOperationalScopedUrl: isOperationalScopedUrl
    };
})();

// 기존 페이지 전역 API를 유지하면서 신규 코드는 AdminApi를 사용한다.
async function apiGet(url) { return AdminApi.get(url); }
async function apiPost(url, body, method) { return AdminApi.send(url, body, method); }
function withOperationalStore(url) { return AdminApi.withOperationalScope(url); }

/* 화면 우상단 "설계 노트" 버튼으로 여는 drawer. 설계 의도를 남겨둘 만한 화면에만 메모를 붙인다. */
const AdminDesignNotes = (function () {
    const notes = [
        { match: "/admin/operations-dashboard", title: "예외부터 보이는 운영 대시보드",
          why: "전체 건수보다, 지금 안 보면 고객·재고·정산에 영향 가는 게 뭔지가 먼저다.",
          how: "결과불명·복구대기·안전재고·정산초안을 한 큐로 모으고, 각 항목에서 필터 걸린 원본 화면으로 바로 이동. 숫자는 전부 현재 데이터에서 계산." },
        { match: "/admin/commerce/orders", title: "주문 상태 ≠ 결제 상태",
          why: "주문 처리랑 PG 응답은 서로 다른 시점에 실패한다. 하나의 상태값으로 못 묶는다.",
          how: "둘을 따로 추적. 주문 생성 때 서버에서 판매가 재검증 + SKU 재고 예약. PG 타임아웃/결과불명은 실패로 단정 안 하고 복구 대상으로 분리." },
        { match: "/admin/payment-operations", title: "결과불명을 운영 상태로 — 워크리스트",
          why: "타임아웃·응답 유실은 승인 실패가 아니다. 실패로 단정하면 승인된 결제를 취소하고, 성공으로 처리하면 미결제 주문이 진행된다.",
          how: "APPROVE_UNKNOWN / CANCEL_UNKNOWN 상태를 두고 확정 전까지 매출원장·정산·알림톡을 생성하지 않는다. 확정 안 된 건을 발생 경과 순 큐로 세우고, 각 건의 PG 로그와 조치를 한 자리에 둔다. 재조회 성공 시 누락된 원장·알림톡이 unique 제약으로 1회만 생성. 실제 PG 대신 Mock Gateway." },
        { match: "/admin/payment-operations/sales-ledger", title: "매출을 수정하지 않고 누적",
          why: "매출 행을 덮어쓰면 부분취소 이력이랑 정산 근거가 사라진다.",
          how: "승인(SALE)/취소(CANCEL)를 별도 불변 행으로. 주문번호·결제ID·TID로 원거래 연결. 확정 매출의 승인액 − 누적취소액만 정산 대상." },
        { match: "/admin/payment-operations/settlements/reconciliation", title: "외부 자료와 내부 원장 대사",
          why: "내부 DB가 일관돼도 실제 PG 처리 결과랑 맞는지는 별개다.",
          how: "PG CSV와 내부 SALE/CANCEL을 거래 단위로 비교. 누락·외부단독·금액차이로 분류하고, 불일치 해소 전에는 정산 확정을 막는다." },
        { match: "/admin/payment-operations/settlements", title: "원장 기준 정산",
          why: "결제 상태를 직접 합산하면 취소·재처리 시 정산 근거가 흔들린다.",
          how: "SALE/CANCEL 원장을 기준으로 초안 → 확정 → 지급. 동일 정산일·MID 중복 배치 방지. 대상액 − 수수료·VAT·조정 = 지급액이 맞아야 확정." },
        { match: "/admin/commerce/inventory", title: "현재 수량이 아니라 변동 근거",
          why: "숫자 하나만 저장하면 재고가 왜 달라졌는지 설명을 못 한다.",
          how: "현재·예약·가용을 분리하고 입고·예약·해제·출고·조정·이동을 전부 변동 이력으로. 전역 수량은 매장 재고 합계 파생값. 가용 = 현재 − 예약(음수 불가)." },
        { match: "/admin/commerce/products", title: "상품 원장과 판매 SKU 분리",
          why: "상품 정보랑 옵션별 가격·재고·판매상태는 바뀌는 주기가 다르다.",
          how: "상품은 공통 원장, 실제 판매/재고 단위는 옵션 조합(SKU). 주문은 상품 ID가 아니라 SKU + 서버 계산가에 붙는다." }
    ];
    function current() {
        const path = location.pathname;
        return notes.find(note => path.startsWith(note.match)) || {
            title: "이 화면의 설계 메모",
            why: "운영 화면은 데이터 나열로 끝나지 않고 현재 상태와 다음 행동을 같이 보여줘야 한다.",
            how: "목록은 테이블 중심, 예외를 정상보다 먼저 배치, 브랜드·매장 컨텍스트를 요청에 일관되게 전달. 화면 값보다 서버 도메인 규칙이 최종 기준."
        };
    }
    function render(note) {
        return `<section><h3>왜</h3><p>${escapeHtml(note.why)}</p></section>`
            + `<section><h3>어떻게</h3><p>${escapeHtml(note.how)}</p></section>`
            + `<p class="design-note-scope">인증·권한·개인정보 마스킹·인프라는 공개 데모에서 단순화했습니다.</p>`;
    }
    function init() {
        const trigger = document.getElementById("designNoteTrigger"), drawer = document.getElementById("designNoteDrawer"),
            dim = document.getElementById("designNoteDim"), close = document.getElementById("designNoteClose"),
            body = document.getElementById("designNoteBody"), title = document.getElementById("designNoteTitle");
        if (!trigger || !drawer || !dim || !close || !body || !title) return;
        const note = current();
        title.textContent = note.title;
        body.innerHTML = render(note);
        const setOpen = open => {
            drawer.classList.toggle("open", open);
            drawer.setAttribute("aria-hidden", String(!open));
            trigger.setAttribute("aria-expanded", String(open));
            dim.hidden = !open;
            document.body.classList.toggle("design-note-open", open);
        };
        trigger.addEventListener("click", () => setOpen(true));
        close.addEventListener("click", () => setOpen(false));
        dim.addEventListener("click", () => setOpen(false));
        document.addEventListener("keydown", event => {
            if (event.key === "Escape" && drawer.classList.contains("open")) setOpen(false);
        });
    }
    return { init };
})();
document.addEventListener("DOMContentLoaded", AdminDesignNotes.init);

/* 결제·정산 화면 상단에 전체 흐름 스트립을 삽입한다 — 사이드바 메뉴가 각각 어느 단계인지
   운영자가 한눈에 파악하도록. 현재 화면 단계를 강조한다. */
const AdminPaymentFlow = (function () {
  const steps = [
    { key: "approve", label: "① PG 승인·취소", href: "/admin/payment-operations", desc: "Mock PG 승인/취소, 결과불명·복구" },
    { key: "ledger", label: "② 매출 원장", href: "/admin/payment-operations/sales-ledger", desc: "확정된 SALE/CANCEL 불변 기록" },
    { key: "confirm", label: "③ 구매 확정", href: "/admin/payment-operations/pending-sales", desc: "배송 완료 시 매출 확정 → 정산 대상" },
    { key: "recon", label: "④ PG 대사", href: "/admin/payment-operations/settlements/reconciliation", desc: "외부 PG 파일과 내부 원장 비교" },
    { key: "settle", label: "⑤ 정산", href: "/admin/payment-operations/settlements", desc: "초안 → 확정 → 지급" },
  ];
  function activeKey() {
    const p = location.pathname;
    // 재설계된 featured 화면(결제 예외 처리 / 정산 마감)에는 붙이지 않는다 — 자체 흐름 UI 사용.
    if (p === "/admin/payment-operations" || p === "/admin/payment-operations/settlements") return null;
    if (p.startsWith("/admin/payment-operations/settlements/reconciliation")) return "recon";
    if (p.startsWith("/admin/payment-operations/pending-sales")) return "confirm";
    if (p.startsWith("/admin/payment-operations/sales-ledger") || p.startsWith("/admin/payment-operations/sales-analytics")) return "ledger";
    return null;
  }
  function init() {
    const active = activeKey();
    if (!active) return;
    const header = document.querySelector(".content > .page-header, .content > header.page-header, section.content .page-header");
    if (!header) return;
    const nav = document.createElement("nav");
    nav.className = "payflow-strip";
    nav.setAttribute("aria-label", "결제·정산 흐름");
    nav.innerHTML = steps.map((s, i) =>
      `${i ? '<span class="payflow-arrow">→</span>' : ''}<a class="payflow-step${s.key === active ? ' current' : ''}" href="${s.href}" title="${s.desc}">${s.label}</a>`
    ).join("");
    header.insertAdjacentElement("afterend", nav);
  }
  return { init };
})();
document.addEventListener("DOMContentLoaded", AdminPaymentFlow.init);

/* ── 목록 표 정렬 헤더 ──────────────────────────────────────────────────────────
   <th data-sort="key"> 를 클릭하면 asc↔desc 토글하고 sort-asc/sort-desc 클래스로 표시한다.
   render 쪽에서 sorter.apply(rows) 로 정렬된 배열을 얻어 그린다.
     const sorter = AdminTableSort.attach(tableEl, { defaultKey:'createdAt', defaultDir:'desc', onSort: render });
     const shown = sorter.apply(filtered); */
window.AdminTableSort = (function () {
  function attach(tableEl, opts) {
    opts = opts || {};
    const state = { key: opts.defaultKey || null, dir: opts.defaultDir || 'asc' };
    const accessor = opts.accessor || ((row, key) => row[key]);
    function paint() {
      tableEl.querySelectorAll('th[data-sort]').forEach(th => {
        const on = th.dataset.sort === state.key;
        th.classList.toggle('sort-asc', on && state.dir === 'asc');
        th.classList.toggle('sort-desc', on && state.dir === 'desc');
      });
    }
    tableEl.querySelectorAll('th[data-sort]').forEach(th => {
      th.classList.add('sortable');
      th.addEventListener('click', () => {
        const k = th.dataset.sort;
        if (state.key === k) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
        else { state.key = k; state.dir = 'asc'; }
        paint();
        if (typeof opts.onSort === 'function') opts.onSort(state);
      });
    });
    paint();
    return {
      get state() { return { key: state.key, dir: state.dir }; },
      apply(rows) {
        if (!state.key) return rows;
        const dir = state.dir === 'asc' ? 1 : -1;
        return [...rows].sort((a, b) => {
          let av = accessor(a, state.key), bv = accessor(b, state.key);
          if (av == null && bv == null) return 0;
          if (av == null) return 1;
          if (bv == null) return -1;
          if (typeof av !== 'number' && typeof bv !== 'number') {
            const na = Number(av), nb = Number(bv);
            if (!Number.isNaN(na) && !Number.isNaN(nb) && String(av).trim() !== '' && String(bv).trim() !== '') { av = na; bv = nb; }
          }
          if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * dir;
          return String(av).localeCompare(String(bv), 'ko') * dir;
        });
      }
    };
  }
  return { attach };
})();
