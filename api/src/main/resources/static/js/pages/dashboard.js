const rawProjects = Array.isArray(window.portfolioProjects) ? window.portfolioProjects : [];

document.addEventListener("DOMContentLoaded", function () {
    const navLinks = document.querySelectorAll(".site-nav-links .nav-link");
    const items = [["경력", "#career-summary"], ["포트폴리오", "#featured-build"], ["구현 상세", "#operations"], ["프로젝트 기록", "#table-wrap"]];
    navLinks.forEach(function (link, index) { if (items[index]) { link.textContent = items[index][0]; link.href = items[index][1]; } });
    const cta = document.querySelector(".site-nav-cta");
    if (cta) { cta.textContent = "구현 화면 보기 →"; cta.href = "/admin/commerce/preview"; }
});

function toArray(value) {
    if (Array.isArray(value)) {
        return value;
    }

    if (value === undefined || value === null || value === "") {
        return [];
    }

    if (typeof value === "string") {
        return value
            .split(",")
            .map(function (item) { return item.trim(); })
            .filter(function (item) { return item.length > 0; });
    }

    return [String(value)];
}

function normalizeProject(project, index) {
    project = project || {};

    const tech = toArray(project.tech);

    return {
        key: project.key || "project-" + String(index + 1).padStart(2, "0"),
        no: project.no || index + 1,
        period: project.period || "",
        company: project.company || "",
        title: project.title || "",
        desc: project.desc || "",
        role: project.role || "",
        domain: project.domain || "",
        tech: tech,

        overview: project.overview || project.desc || "",
        overviewParagraphs: toArray(project.overviewParagraphs),

        responsibilities: toArray(project.responsibilities),
        implementation: toArray(project.implementation),
        automation: toArray(project.automation),
        manualScope: toArray(project.manualScope),
        returnCancelScope: toArray(project.returnCancelScope),
        considerations: toArray(project.considerations),
        problemSolving: toArray(project.problemSolving),
        achievements: toArray(project.achievements),
        improvements: toArray(project.improvements),

        processFlows: Array.isArray(project.processFlows) ? project.processFlows : [],

        diagramImage: project.diagramImage || "",
        diagramAlt: project.diagramAlt || ""
    };
}

const projects = rawProjects.map(function (project, index) {
    return normalizeProject(project, index);
});

const projectTableEl = document.getElementById("projectTable");
let projectTable = null;

const keywordInput = document.getElementById("keywordInput");
const domainFilter = document.getElementById("domainFilter");
const techFilter = document.getElementById("techFilter");
const filterToggle = document.getElementById("filterToggle");
const filterPopover = document.getElementById("filterPopover");
const applyBtn = document.getElementById("applyBtn");
const resetBtn = document.getElementById("resetBtn");
const resultCount = document.getElementById("resultCount");
const filterChips = document.getElementById("filterChips");
const modalBackdrop = document.getElementById("modalBackdrop");
const modalTitle = document.getElementById("modalTitle");
const modalSub = document.getElementById("modalSub");
const modalBody = document.getElementById("modalBody");
const modalClose = document.getElementById("modalClose");
let dashboardLoading = null;

function showDashboardLoading(message) {
    if (!dashboardLoading) {
        dashboardLoading = document.createElement("div");
        dashboardLoading.className = "dashboard-loading";
        dashboardLoading.setAttribute("role", "status");
        dashboardLoading.setAttribute("aria-live", "polite");
        dashboardLoading.innerHTML = '<div class="dashboard-loading-box">'
            + '<div class="dashboard-progress"><span></span></div>'
            + '<strong></strong>'
            + '<p>목록과 상세 팝업을 곧 표시합니다.</p>'
            + '</div>';
        document.body.appendChild(dashboardLoading);
    }

    dashboardLoading.querySelector("strong").textContent = message || "프로젝트 목록을 준비하고 있어요";
    dashboardLoading.classList.add("open");
}

function hideDashboardLoading() {
    if (dashboardLoading) {
        dashboardLoading.classList.remove("open");
    }
}

function escapeHtml(value) {
    return String(value || "").replace(/[&<>\"]/g, function (char) {
        return {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            "\"": "&quot;"
        }[char];
    });
}

function formatInline(value) {
    return escapeHtml(value).replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
}

function paragraphMarkup(project) {
    const paragraphs = project.overviewParagraphs && project.overviewParagraphs.length
        ? project.overviewParagraphs
        : [project.overview];

    return paragraphs.map(function (paragraph) {
        return "<p>" + formatInline(paragraph) + "</p>";
    }).join("");
}

function listMarkup(items) {
    items = toArray(items);

    if (!items.length) {
        return "<ul><li>등록된 내용이 없습니다.</li></ul>";
    }

    return "<ul>" + items.map(function (item) {
        return "<li>" + formatInline(item) + "</li>";
    }).join("") + "</ul>";
}

function optionalCardMarkup(title, items) {
    items = toArray(items);

    if (!items.length) {
        return "";
    }

    return '<div class="modal-section">' +
        '<div class="modal-card"><h3>' + escapeHtml(title) + '</h3>' + listMarkup(items) + '</div>' +
    '</div>';
}

function flowMarkup(project) {
    if (!project.processFlows || project.processFlows.length === 0) {
        return "";
    }

    const flowGroups = project.processFlows.map(function (flow) {
        const steps = toArray(flow.steps).map(function (step, index) {
            const arrow = index < flow.steps.length - 1 ? '<span class="flow-arrow">→</span>' : "";

            return '<div class="flow-step">' +
                '<span class="flow-index">' + String(index + 1).padStart(2, "0") + '</span>' +
                '<strong>' + formatInline(step) + '</strong>' +
            '</div>' + arrow;
        }).join("");

        return '<div class="flow-group">' +
            '<h4>' + escapeHtml(flow.title) + '</h4>' +
            '<div class="flow-track">' + steps + '</div>' +
        '</div>';
    }).join("");

    return '<div class="modal-section">' +
        '<h3>주요 처리 흐름</h3>' +
        '<div class="process-flow">' + flowGroups + '</div>' +
    '</div>';
}

function diagramMarkup(project) {
    if (!project.diagramImage) {
        return "";
    }

    return '<div class="modal-section">' +
        '<h3>처리 구조</h3>' +
        '<figure class="modal-diagram">' +
            '<img src="' + escapeHtml(project.diagramImage) + '" alt="' + escapeHtml(project.diagramAlt || project.title + " 구조도") + '" />' +
        '</figure>' +
    '</div>';
}

function tagListMarkup(tags) {
    tags = toArray(tags);

    return tags.map(function (tag, index) {
        return '<span class="tag ' + (index === 0 ? "primary" : "") + '">' + escapeHtml(tag) + '</span>';
    }).join("");
}

function renderPortfolioArchive(items) {
    if (!projectTableEl) return;
    if (!items.length) {
        projectTableEl.innerHTML = '<div class="archive-empty">조건에 맞는 프로젝트가 없습니다.</div>';
        return;
    }

    projectTableEl.innerHTML = items.map(function (project, index) {
        const tech = toArray(project.tech).slice(0, 4).map(function (item) {
            return '<span>' + escapeHtml(item) + '</span>';
        }).join("");
        return '<button type="button" class="archive-record" data-project-key="' + escapeHtml(project.key) + '">' +
            '<b class="archive-index">' + String(index + 1).padStart(2, "0") + '</b>' +
            '<time>' + escapeHtml(project.period) + '</time>' +
            '<div class="archive-project"><strong>' + escapeHtml(project.title) + '</strong><p>' + escapeHtml(project.desc) + '</p></div>' +
            '<div class="archive-role"><span>' + escapeHtml(project.company) + '</span><small>' + escapeHtml(project.role) + '</small></div>' +
            '<div class="archive-tech">' + tech + '</div>' +
            '<i aria-hidden="true">↗</i>' +
        '</button>';
    }).join("");

    Array.prototype.forEach.call(projectTableEl.querySelectorAll(".archive-record"), function (record) {
        record.addEventListener("click", function () { openProject(record.dataset.projectKey); });
    });
}

function initProjectTable() {
    if (!projectTableEl) {
        console.warn("projectTable 요소가 없습니다.");
        return;
    }

    projectTable = {
        setData: function (data) { renderPortfolioArchive(data); },
        redraw: function () {}
    };
    renderPortfolioArchive(projects);
    return;

    if (typeof Tabulator === "undefined") {
        console.error("Tabulator library is not loaded.");
        return;
    }

    projectTable = new Tabulator(projectTableEl, {
        data: projects,
        layout: "fitColumns",
        responsiveLayout: "collapse",

        renderVertical: "basic",
        placeholder: "검색 조건에 맞는 프로젝트가 없습니다.",
        index: "key",

        rowFormatter: function (row) {
            const position = Number(row.getPosition(true)) || 1;
            row.getElement().style.setProperty("--row-index", Math.max(position - 1, 0));
        },

        columns: [
            {
                title: "No",
                field: "no",
                width: 64,
                minWidth: 64,
                headerSort: true,
                cssClass: "num-cell",
                hozAlign: "center"
            },
            {
                title: "기간",
                field: "period",
                width: 140,
                minWidth: 132,
                headerSort: true,
                cssClass: "period-cell"
            },
            {
                title: "회사",
                field: "company",
                width: 140,
                minWidth: 126,
                headerSort: true,
                cssClass: "company-cell"
            },
            {
                title: "프로젝트",
                field: "title",
                widthGrow: 3,
                minWidth: 300,
                formatter: function (cell) {
                    const project = cell.getData();

                    return '<div class="project-title">' + escapeHtml(project.title) + '</div>' +
                        '<div class="project-desc">' + escapeHtml(project.desc) + '</div>';
                }
            },
            {
                title: "역할",
                field: "role",
                width: 140,
                minWidth: 132,
                formatter: function (cell) {
                    return '<span class="role-pill primary">' + escapeHtml(cell.getValue()) + '</span>';
                }
            },
            {
                title: "도메인",
                field: "domain",
                width: 150,
                minWidth: 142,
                formatter: function (cell) {
                    return '<span class="role-pill">' + escapeHtml(cell.getValue()) + '</span>';
                }
            },
            {
                title: "기술",
                field: "tech",
                width: 220,
                minWidth: 210,
                formatter: function (cell) {
                    return '<div class="tag-list">' + tagListMarkup(cell.getValue()) + '</div>';
                },
                sorter: function (a, b) {
                    return toArray(a).join(" ").localeCompare(toArray(b).join(" "));
                }
            },
            {
                title: "",
                field: "key",
                width: 58,
                minWidth: 58,
                hozAlign: "center",
                headerSort: false,
                formatter: function () {
                    return '<button class="detail-btn" type="button" aria-label="상세 보기">›</button>';
                },
                cellClick: function (event, cell) {
                    event.stopPropagation();

                    const data = cell.getData();

                    if (!data || !data.key) {
                        console.error("상세보기 key가 없습니다.", data);
                        return;
                    }

                    openProject(data.key);
                }
            }
        ]
    });

    projectTable.on("rowClick", function (event, row) {
        const data = row.getData();

        if (!data || !data.key) {
            return;
        }

        openProject(data.key);
    });
}

function renderProjects() {
    const keyword = keywordInput ? keywordInput.value.trim().toLowerCase() : "";
    const domain = domainFilter ? domainFilter.value : "";
    const tech = techFilter ? techFilter.value : "";

    const filtered = projects.filter(function (project) {
        const searchable = [
            project.title,
            project.desc,
            project.company,
            project.role,
            project.domain
        ].concat(project.tech || []).join(" ").toLowerCase();

        const matchesKeyword = !keyword || searchable.indexOf(keyword) > -1;
        const matchesDomain = !domain || project.domain === domain;
        const matchesTech = !tech || toArray(project.tech).join(" ").indexOf(tech) > -1;

        return matchesKeyword && matchesDomain && matchesTech;
    });

    if (projectTable) {
        const dataUpdate = projectTable.setData(filtered);

        if (dataUpdate && typeof dataUpdate.then === "function") {
            dataUpdate.then(function () {
                projectTable.redraw(true);
            });
        } else {
            projectTable.redraw(true);
        }
    }

    if (resultCount) {
        resultCount.textContent = filtered.length;
    }

    renderChips(keyword, domain, tech);
}

function renderChips(keyword, domain, tech) {
    if (!filterChips) {
        return;
    }

    const chips = [];

    if (keyword) {
        chips.push({
            label: "검색",
            value: keyword,
            clear: function () {
                if (keywordInput) keywordInput.value = "";
            }
        });
    }

    if (domain) {
        chips.push({
            label: "도메인",
            value: domain,
            clear: function () {
                if (domainFilter) domainFilter.value = "";
            }
        });
    }

    if (tech) {
        chips.push({
            label: "기술",
            value: tech,
            clear: function () {
                if (techFilter) techFilter.value = "";
            }
        });
    }

    if (!chips.length) {
        filterChips.innerHTML = '<span class="chip"><strong>전체 프로젝트</strong></span>';
        return;
    }

    filterChips.innerHTML = chips.map(function (chip, index) {
        return '<span class="chip">' +
            '<strong>' + escapeHtml(chip.label) + '</strong> ' +
            escapeHtml(chip.value) +
            ' <button type="button" data-chip="' + index + '" aria-label="필터 제거">×</button>' +
        '</span>';
    }).join("");

    Array.prototype.forEach.call(filterChips.querySelectorAll("button"), function (button) {
        button.addEventListener("click", function () {
            const index = Number(button.dataset.chip);

            if (chips[index]) {
                chips[index].clear();
                renderProjects();
            }
        });
    });
}

function openProject(key) {
    const project = projects.find(function (item) {
        return item.key === key;
    });

    if (!project) {
        console.error("프로젝트 key를 찾을 수 없습니다:", key);
        return;
    }

    if (!modalBackdrop || !modalTitle || !modalSub || !modalBody || !modalClose) {
        console.error("모달 DOM 요소가 없습니다. modalBackdrop, modalTitle, modalSub, modalBody, modalClose를 확인하세요.");
        return;
    }

    const techList = toArray(project.tech);

    const modalTags = techList.map(function (tag, index) {
        return '<span class="modal-tag ' + (index === 0 ? "primary" : "") + '">' + escapeHtml(tag) + '</span>';
    }).join("");

    modalTitle.textContent = project.title;
    modalSub.textContent = project.period + " · " + project.company + " · " + project.domain;

    modalBody.innerHTML =
        '<div class="modal-layout">' +
            '<aside class="modal-info">' +
                '<span class="modal-role">' + escapeHtml(project.role) + '</span>' +
                '<p class="modal-info-desc">' + escapeHtml(project.desc) + '</p>' +
                '<div class="modal-info-list">' +
                    '<div class="modal-info-row"><span>Company</span><strong>' + escapeHtml(project.company) + '</strong></div>' +
                    '<div class="modal-info-row"><span>Period</span><strong>' + escapeHtml(project.period) + '</strong></div>' +
                    '<div class="modal-info-row"><span>Domain</span><strong>' + escapeHtml(project.domain) + '</strong></div>' +
                    '<div class="modal-info-row"><span>Tech</span><strong>' + escapeHtml(techList.join(" · ")) + '</strong></div>' +
                '</div>' +
            '</aside>' +
            '<div class="modal-main">' +
                '<div class="modal-section"><h3>프로젝트 개요</h3>' + paragraphMarkup(project) + '</div>' +
                '<div class="modal-grid modal-section">' +
                    '<div class="modal-card"><h3>담당 역할</h3>' + listMarkup(project.responsibilities) + '</div>' +
                    '<div class="modal-card"><h3>주요 구현 내용</h3>' + listMarkup(project.implementation) + '</div>' +
                '</div>' +
                optionalCardMarkup("자동화 도구 개발", project.automation) +
                optionalCardMarkup("자동화하지 않은 범위", project.manualScope) +
                flowMarkup(project) +
                optionalCardMarkup("반품/취소 관련 담당 범위", project.returnCancelScope) +
                optionalCardMarkup("업무상 고민한 부분", project.considerations) +
                optionalCardMarkup("문제 해결 포인트", project.problemSolving) +
                optionalCardMarkup("주요 성과", project.achievements) +
                diagramMarkup(project) +
                optionalCardMarkup("개선 방향 및 회고", project.improvements) +
                '<div class="modal-section"><h3>기술 키워드</h3><div class="modal-tags">' + modalTags + '</div></div>' +
            '</div>' +
        '</div>';

    modalBackdrop.classList.add("open");
    document.body.classList.add("modal-open");

    if (modalClose && typeof modalClose.focus === "function") {
        modalClose.focus();
    }
}

function closeModal() {
    if (modalBackdrop) {
        modalBackdrop.classList.remove("open");
    }

    document.body.classList.remove("modal-open");
}

function animateCounters() {
    Array.prototype.forEach.call(document.querySelectorAll(".count-value"), function (counter) {
        const target = Number(counter.dataset.count || "0");
        const duration = 760;
        const start = performance.now();

        function tick(now) {
            const progress = Math.min((now - start) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3);

            counter.textContent = String(Math.round(target * eased));

            if (progress < 1) {
                requestAnimationFrame(tick);
            }
        }

        requestAnimationFrame(tick);
    });
}

function setupScrollSpy() {
    const sections = Array.prototype.slice.call(
        document.querySelectorAll("#projects, #overview, #operations, #scope, #workflow")
    );

    if (!sections.length || !("IntersectionObserver" in window)) {
        return;
    }

    const observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            if (!entry.isIntersecting) {
                return;
            }

            const id = entry.target.id;

            Array.prototype.forEach.call(document.querySelectorAll(".nav-link"), function (link) {
                const isActive = link.getAttribute("href") === "#" + id;
                link.classList.toggle("active", isActive);
            });
        });
    }, {
        rootMargin: "-34% 0px -58% 0px",
        threshold: 0.01
    });

    sections.forEach(function (section) {
        observer.observe(section);
    });
}

function setupLandingMotion() {
    const targets = Array.prototype.slice.call(document.querySelectorAll(
        ".boundary-v3, .career-v3, .product-story, .operations-v3, .architecture-v3, .projects-v3, .project-list-v3, .closing-v3"
    ));

    if (!targets.length || location.hash || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        targets.forEach(function (target) { target.classList.add("motion-visible"); });
        return;
    }

    document.documentElement.classList.add("motion-ready");
    const observer = new IntersectionObserver(function (entries, currentObserver) {
        entries.forEach(function (entry) {
            if (!entry.isIntersecting) return;
            entry.target.classList.add("motion-visible");
            currentObserver.unobserve(entry.target);
        });
    }, { threshold: 0.08, rootMargin: "0px 0px -48px 0px" });

    targets.forEach(function (target) { observer.observe(target); });
}

function setupProductStory() {
    const buttons = Array.prototype.slice.call(document.querySelectorAll(".story-steps button"));
    const panels = Array.prototype.slice.call(document.querySelectorAll(".story-panel"));
    if (!buttons.length || !panels.length) return;

    function activate(key) {
        buttons.forEach(function (button) { button.classList.toggle("active", button.dataset.story === key); });
        panels.forEach(function (panel) { panel.classList.toggle("active", panel.dataset.panel === key); });
    }

    buttons.forEach(function (button) {
        button.addEventListener("click", function () { activate(button.dataset.story); });
    });
}

function bindEvents() {
    Array.prototype.forEach.call(document.querySelectorAll('.nav-link[href^="#"]'), function (link) {
        link.addEventListener("click", function () {
            Array.prototype.forEach.call(document.querySelectorAll(".nav-link"), function (item) {
                item.classList.remove("active");
            });

            link.classList.add("active");
        });
    });

    if (filterToggle && filterPopover) {
        filterToggle.addEventListener("click", function (event) {
            event.stopPropagation();
            filterPopover.classList.toggle("open");
        });

        filterPopover.addEventListener("click", function (event) {
            event.stopPropagation();
        });
    }

    if (applyBtn && filterPopover) {
        applyBtn.addEventListener("click", function () {
            filterPopover.classList.remove("open");
            renderProjects();
        });
    }

    if (resetBtn) {
        resetBtn.addEventListener("click", function () {
            if (keywordInput) keywordInput.value = "";
            if (domainFilter) domainFilter.value = "";
            if (techFilter) techFilter.value = "";

            renderProjects();
        });
    }

    if (keywordInput) {
        keywordInput.addEventListener("input", renderProjects);
    }

    document.addEventListener("click", function () {
        if (filterPopover) {
            filterPopover.classList.remove("open");
        }
    });

    if (modalClose) {
        modalClose.addEventListener("click", closeModal);
    }

    if (modalBackdrop) {
        modalBackdrop.addEventListener("click", function (event) {
            if (event.target === modalBackdrop) {
                closeModal();
            }
        });
    }

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            if (filterPopover) {
                filterPopover.classList.remove("open");
            }

            if (modalBackdrop && modalBackdrop.classList.contains("open")) {
                closeModal();
            }
        }
    });
}

function setupAccordion() {
    Array.prototype.forEach.call(document.querySelectorAll('.acc-header'), function(btn) {
        btn.addEventListener('click', function() {
            var item = btn.closest('.acc-item');
            if (!item) { return; }
            var isOpen = item.classList.contains('open');
            item.classList.toggle('open', !isOpen);
            btn.setAttribute('aria-expanded', String(!isOpen));
        });
    });
}

showDashboardLoading("프로젝트 목록을 준비하고 있어요");
function initializeRevealSections() {
    const sections = document.querySelectorAll(".reveal-section");
    if (!sections.length) {
        return;
    }

    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reducedMotion || !("IntersectionObserver" in window)) {
        sections.forEach(function (section) {
            section.classList.add("is-visible");
        });
        return;
    }

    const observer = new IntersectionObserver(function (entries, currentObserver) {
        entries.forEach(function (entry) {
            if (!entry.isIntersecting) {
                return;
            }
            entry.target.classList.add("is-visible");
            currentObserver.unobserve(entry.target);
        });
    }, {
        threshold: 0.12,
        rootMargin: "0px 0px -40px 0px"
    });

    sections.forEach(function (section) {
        observer.observe(section);
    });
}

initializeRevealSections();
initProjectTable();
animateCounters();
setupScrollSpy();
setupLandingMotion();
setupProductStory();
setupAccordion();
bindEvents();

if (projectTable) {
    setTimeout(function () {
        renderProjects();
        hideDashboardLoading();
    }, 0);
} else {
    renderProjects();
    hideDashboardLoading();
}
