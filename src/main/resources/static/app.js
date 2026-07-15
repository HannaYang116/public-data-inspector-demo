const portalTab = document.getElementById("portalTab");
const uploadTab = document.getElementById("uploadTab");
const urlTab = document.getElementById("urlTab");
const portalForm = document.getElementById("portalForm");
const uploadForm = document.getElementById("uploadForm");
const urlForm = document.getElementById("urlForm");
const portalResults = document.getElementById("portalResults");
const emptyState = document.getElementById("emptyState");
const result = document.getElementById("result");
const statBox = document.getElementById("statBox");
const statColumn = document.getElementById("statColumn");
const statType = document.getElementById("statType");
const statResult = document.getElementById("statResult");
const topStats = document.getElementById("topStats");
const saveStatButton = document.getElementById("saveStatButton");
const archiveList = document.getElementById("archiveList");
const projectList = document.getElementById("projectList");
const projectOutput = document.getElementById("projectOutput");
const dashboardOutput = document.getElementById("dashboardOutput");

let currentDataset = null;
let currentPreview = null;
let currentStatistic = null;
let currentArchive = null;
let currentProject = null;

uploadTab.addEventListener("click", () => switchMode("upload"));
urlTab.addEventListener("click", () => switchMode("url"));
portalTab.addEventListener("click", () => switchMode("portal"));
document.getElementById("statButton").addEventListener("click", analyzeStatistic);
saveStatButton.addEventListener("click", markCurrentStatisticSaved);
document.getElementById("archiveForm").addEventListener("submit", createArchive);
document.getElementById("projectForm").addEventListener("submit", createProject);
document.getElementById("connectGithubButton").addEventListener("click", connectGithub);
document.getElementById("addDatasetButton").addEventListener("click", addDatasetToProject);
document.getElementById("readmeButton").addEventListener("click", loadReadme);
document.getElementById("issueButton").addEventListener("click", loadIssueDraft);
document.getElementById("dashboardButton").addEventListener("click", loadDashboard);

portalForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const keyword = document.getElementById("keywordInput").value.trim();

    if (!keyword) {
        alert("검색어를 입력해주세요.");
        return;
    }

    portalResults.textContent = "공공데이터포털에서 검색 중입니다...";
    const response = await fetch(`/api/data-go-kr/search?keyword=${encodeURIComponent(keyword)}`);
    const data = await response.json();

    if (!response.ok) {
        portalResults.textContent = data.msg || "검색에 실패했습니다.";
        return;
    }

    renderPortalResults(data);
});

uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const file = document.getElementById("fileInput").files[0];

    if (!file) {
        alert("분석할 파일을 선택해주세요.");
        return;
    }

    const body = new FormData();
    body.append("file", file);

    const response = await fetch("/api/analyze/upload", {
        method: "POST",
        body
    });

    await handleResponse(response);
});

urlForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const url = document.getElementById("urlInput").value.trim();

    if (!url) {
        alert("분석할 URL을 입력해주세요.");
        return;
    }

    const response = await fetch("/api/analyze/url", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ url })
    });

    await handleResponse(response);
});

function switchMode(mode) {
    const portal = mode === "portal";
    const upload = mode === "upload";

    portalTab.classList.toggle("is-active", portal);
    uploadTab.classList.toggle("is-active", upload);
    urlTab.classList.toggle("is-active", mode === "url");
    portalForm.classList.toggle("is-hidden", !portal);
    portalResults.classList.toggle("is-hidden", !portal);
    uploadForm.classList.toggle("is-hidden", !upload);
    urlForm.classList.toggle("is-hidden", mode !== "url");
}

async function handleResponse(response) {
    const data = await response.json();

    if (!response.ok) {
        alert(data.msg || "분석에 실패했습니다.");
        return;
    }

    renderResult(data);
}

function renderResult(data) {
    currentPreview = data;
    currentStatistic = null;
    saveStatButton.classList.add("is-hidden");
    statResult.textContent = "";
    emptyState.classList.add("is-hidden");
    result.classList.remove("is-hidden");
    statBox.classList.remove("is-hidden");

    document.getElementById("fileType").textContent = data.fileType;
    document.getElementById("filename").textContent = data.filename;
    document.getElementById("rowCount").textContent = data.rowCount.toLocaleString();

    const columns = document.getElementById("columns");
    columns.replaceChildren(
        ...data.columns.map((column) => {
            const chip = document.createElement("span");
            chip.className = "chip";
            chip.textContent = `${column} · ${data.columnTypes[column] || "unknown"}`;
            return chip;
        })
    );

    statColumn.replaceChildren(
        ...data.columns.map((column) => {
            const option = document.createElement("option");
            option.value = column;
            option.textContent = column;
            return option;
        })
    );

    const previewHead = document.getElementById("previewHead");
    const previewBody = document.getElementById("previewBody");

    const headerRow = document.createElement("tr");
    data.columns.forEach((column) => {
        const th = document.createElement("th");
        th.textContent = column;
        headerRow.append(th);
    });
    previewHead.replaceChildren(headerRow);

    previewBody.replaceChildren(
        ...data.previewRows.map((row) => {
            const tr = document.createElement("tr");
            data.columns.forEach((column) => {
                const td = document.createElement("td");
                td.textContent = row[column] ?? "";
                tr.append(td);
            });
            return tr;
        })
    );

    loadTopStats();
}

function renderPortalResults(items) {
    if (items.length === 0) {
        portalResults.textContent = "검색 결과가 없습니다.";
        return;
    }

    portalResults.replaceChildren(
        ...items.map((item) => {
            const article = document.createElement("article");
            article.className = "dataset";

            const title = document.createElement("strong");
            title.textContent = item.title || "제목 없음";

            const desc = document.createElement("p");
            desc.textContent = item.description || "설명 없음";

            const meta = document.createElement("small");
            meta.textContent = `${item.provider || "제공기관 미상"} · ${item.updatedDate || "수정일 미상"}`;

            const actions = document.createElement("div");
            actions.className = "dataset-actions";

            const analyzeButton = document.createElement("button");
            analyzeButton.type = "button";
            analyzeButton.textContent = "실제 파일 분석";
            analyzeButton.addEventListener("click", () => analyzePortalDataset(item));

            const link = document.createElement("a");
            link.href = item.detailUrl;
            link.target = "_blank";
            link.rel = "noreferrer";
            link.textContent = "포털 상세";

            actions.append(analyzeButton, link);
            article.append(title, desc, meta, actions);
            return article;
        })
    );
}

async function analyzePortalDataset(item) {
    currentDataset = item;
    const response = await fetch("/api/data-go-kr/analyze", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            publicDataPk: item.publicDataPk,
            fileDetailSn: item.fileDetailSn
        })
    });

    await handleResponse(response);
}

async function analyzeStatistic() {
    if (!currentDataset) {
        alert("공공데이터 검색 결과에서 실제 파일 분석을 먼저 실행해주세요.");
        return;
    }

    statResult.textContent = "통계 분석 중입니다...";
    const response = await fetch("/api/statistics", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            publicDataPk: currentDataset.publicDataPk,
            fileDetailSn: currentDataset.fileDetailSn,
            datasetTitle: currentDataset.title,
            columnName: statColumn.value,
            statType: statType.value,
            forceRefresh: false
        })
    });
    const data = await response.json();

    if (!response.ok) {
        statResult.textContent = data.msg || "통계 분석에 실패했습니다.";
        return;
    }

    currentStatistic = data;
    statResult.textContent = JSON.stringify(data.result, null, 2);
    saveStatButton.classList.remove("is-hidden");
    await loadTopStats();
}

async function markCurrentStatisticSaved() {
    if (!currentStatistic) return;

    const response = await fetch(`/api/statistics/${encodeURIComponent(currentStatistic.id)}/saved`, {
        method: "POST"
    });
    const data = await response.json();

    if (!response.ok) {
        alert(data.msg || "저장 카운트 갱신에 실패했습니다.");
        return;
    }

    currentStatistic = data;
    await loadTopStats();
    await loadDashboard();
}

async function createArchive(event) {
    event.preventDefault();
    if (!currentDataset) {
        alert("아카이빙할 공공데이터를 먼저 분석해주세요.");
        return;
    }

    const response = await fetch("/api/workspace/archives", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            datasetId: currentDataset.publicDataPk,
            datasetTitle: currentDataset.title,
            provider: currentDataset.provider,
            detailUrl: currentDataset.detailUrl,
            category: document.getElementById("archiveCategory").value,
            tags: document.getElementById("archiveTags").value.split(",").map((tag) => tag.trim()).filter(Boolean),
            memo: document.getElementById("archiveMemo").value,
            status: "후보"
        })
    });
    const data = await response.json();

    if (!response.ok) {
        alert(data.msg || "아카이브 저장에 실패했습니다.");
        return;
    }

    currentArchive = data;
    if (currentStatistic) {
        await fetch(`/api/workspace/archives/${currentArchive.id}/statistics`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ statisticId: currentStatistic.id })
        });
    }
    await loadArchives();
    await loadDashboard();
}

async function createProject(event) {
    event.preventDefault();
    const response = await fetch("/api/workspace/projects", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            title: document.getElementById("projectTitle").value,
            description: document.getElementById("projectDesc").value
        })
    });
    const data = await response.json();

    if (!response.ok) {
        alert(data.msg || "프로젝트 생성에 실패했습니다.");
        return;
    }

    currentProject = data;
    await loadProjects();
}

async function connectGithub() {
    if (!currentProject) {
        alert("프로젝트를 먼저 생성해주세요.");
        return;
    }

    const response = await fetch(`/api/workspace/projects/${currentProject.id}/github`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            repositoryUrl: document.getElementById("githubUrl").value
        })
    });
    const data = await response.json();

    if (!response.ok) {
        alert(data.msg || "GitHub 연결에 실패했습니다.");
        return;
    }

    currentProject = data;
    await loadProjects();
}

async function addDatasetToProject() {
    if (!currentProject || !currentArchive) {
        alert("프로젝트와 아카이브가 모두 필요합니다.");
        return;
    }

    const response = await fetch(`/api/workspace/projects/${currentProject.id}/datasets`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ archiveId: currentArchive.id })
    });
    const data = await response.json();

    if (!response.ok) {
        alert(data.msg || "프로젝트에 데이터 추가를 실패했습니다.");
        return;
    }

    currentProject = data;
    await loadProjects();
}

async function loadReadme() {
    if (!currentProject) return;
    const response = await fetch(`/api/workspace/projects/${currentProject.id}/readme`);
    const data = await response.json();
    projectOutput.textContent = data.markdown || JSON.stringify(data, null, 2);
}

async function loadIssueDraft() {
    if (!currentArchive) return;
    const response = await fetch(`/api/workspace/archives/${currentArchive.id}/issue-draft`);
    const data = await response.json();
    projectOutput.textContent = `제목: ${data.title}\n\n${data.body}`;
}

async function loadTopStats() {
    if (!currentDataset) return;
    const response = await fetch(`/api/statistics/top3?datasetId=${encodeURIComponent(currentDataset.publicDataPk)}`);
    const data = await response.json();
    topStats.replaceChildren(titleLine("많이 쓰인 통계 Top 3"), ...data.map((item) => line(`${item.columnName} · ${item.statType} · 저장 ${item.savedCount} / 요청 ${item.requestCount}`)));
}

async function loadArchives() {
    const response = await fetch("/api/workspace/archives");
    const data = await response.json();
    archiveList.replaceChildren(...data.map((item) => line(`${item.datasetTitle} · ${item.category} · ${item.status}`)));
}

async function loadProjects() {
    const response = await fetch("/api/workspace/projects");
    const data = await response.json();
    projectList.replaceChildren(...data.map((item) => {
        const github = item.githubRepository ? ` · ${item.githubRepository.fullName}` : "";
        return line(`${item.title}${github} · 데이터 ${item.datasets.length}개`);
    }));
}

async function loadDashboard() {
    const response = await fetch("/api/workspace/dashboard");
    const data = await response.json();
    dashboardOutput.textContent = JSON.stringify(data, null, 2);
}

function titleLine(text) {
    const strong = document.createElement("strong");
    strong.textContent = text;
    return strong;
}

function line(text) {
    const item = document.createElement("p");
    item.textContent = text;
    return item;
}

loadArchives();
loadProjects();
loadDashboard();
