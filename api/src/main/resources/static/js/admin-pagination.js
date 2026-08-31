(function () {
    const PAGE_SIZES = [10, 20, 50, 100];

    function pageNumbers(current, total) {
        const start = Math.max(1, Math.min(current - 2, Math.max(1, total - 4)));
        const end = Math.min(total, start + 4);
        return Array.from({length: Math.max(0, end - start + 1)}, (_, index) => start + index);
    }

    window.AdminPagination = {
        mount(container, options) {
            const storageKey = "yeni-admin-pagination-v1:" + location.pathname + ":"
                + (container.id || "default") + ":"
                + (localStorage.getItem("commerce-brand-id") || "0") + ":"
                + (localStorage.getItem("commerce-store-id") || "0");
            let saved = {};
            try { saved = JSON.parse(sessionStorage.getItem(storageKey) || "{}"); }
            catch (ignore) { saved = {}; }
            const state = {
                page: Math.max(1, Number(saved.page || options.page || 1)),
                size: PAGE_SIZES.includes(Number(saved.size || options.size)) ? Number(saved.size || options.size) : 20,
                total: Math.max(0, Number(options.total || 0))
            };

            function persist() {
                sessionStorage.setItem(storageKey, JSON.stringify({page: state.page, size: state.size}));
            }

            function notify() {
                if (typeof options.onChange === "function") options.onChange({...state});
            }

            function render() {
                const totalPages = Math.max(1, Math.ceil(state.total / state.size));
                state.page = Math.min(state.page, totalPages);
                container.classList.toggle("pagination-footer", !options.inline);
                container.classList.toggle("inline-pagination", Boolean(options.inline));
                container.innerHTML = `<span class="pagination-total">총 ${state.total.toLocaleString("ko-KR")}개</span><label class="pagination-size"><span>페이지당</span><select aria-label="페이지당 표시 개수">${PAGE_SIZES.map(size => `<option value="${size}" ${size === state.size ? "selected" : ""}>${size}</option>`).join("")}</select></label><nav class="pagination-controls" aria-label="페이지 이동"><button class="pagination-button" data-page="prev" aria-label="이전 페이지" ${state.page === 1 ? "disabled" : ""}>‹</button><span class="pagination-pages">${pageNumbers(state.page, totalPages).map(page => `<button class="pagination-button ${page === state.page ? "active" : ""}" data-page="${page}" ${page === state.page ? 'aria-current="page"' : ""}>${page}</button>`).join("")}</span><button class="pagination-button" data-page="next" aria-label="다음 페이지" ${state.page === totalPages ? "disabled" : ""}>›</button></nav>`;
                container.querySelector("select").onchange = event => { state.size = Number(event.target.value); state.page = 1; persist(); render(); notify(); };
                container.querySelectorAll("[data-page]").forEach(button => button.onclick = () => {
                    const value = button.dataset.page;
                    state.page = value === "prev" ? state.page - 1 : value === "next" ? state.page + 1 : Number(value);
                    persist(); render(); notify();
                });
            }

            render();
            return {
                setTotal(total) { state.total = Math.max(0, Number(total || 0)); render(); },
                setPage(page) { state.page = Math.max(1, Number(page || 1)); persist(); render(); },
                getPage() { return state.page; },
                getSize() { return state.size; },
                reset() { state.page = 1; persist(); render(); },
                state() { return {...state}; },
                slice(items) { const start = (state.page - 1) * state.size; return items.slice(start, start + state.size); }
            };
        }
    };
})();
