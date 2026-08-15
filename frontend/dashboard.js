// dashboard.js
// Handles dashboard metrics, charts, Mapbox project concentration map, and data tables

let map;
let mapMarkers = [];
let allProjectsForMap = [];

// --- 1. Centralized API Fetch Utility ---
async function apiFetch(endpoint, options = {}) {
    const token = window.authManager.getToken();
    if (!token) {
        window.location.href = 'index.html';
        return;
    }
    return fetch(`${window.BASE_URL}${endpoint}`, {
        ...options,
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
            ...options.headers,
        },
    });
}

// --- 2. Table Rendering Utilities ---
function renderTable(tabId, data, columns) {
    const tbody = document.querySelector(`#${tabId} tbody`);
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (!Array.isArray(data) || data.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="${columns.length + 1}" class="px-6 py-8 text-center text-gray-400">
                    <i class="mb-2 text-2xl fas fa-inbox text-gray-300"></i>
                    <p>No records found</p>
                </td>
            </tr>
        `;
        return;
    }
    
    data.forEach(item => {
        const row = document.createElement('tr');
        row.className = 'bg-white border-b hover:bg-gray-50';
        
        columns.forEach(col => {
            const td = document.createElement('td');
            td.className = 'px-6 py-4 text-xs font-medium text-gray-700';
            let value = getNestedValue(item, col) || '';
            
            // Format dates
            if ((col.toLowerCase().includes('date') || col.toLowerCase().includes('at')) && value) {
                const date = new Date(value);
                if (!isNaN(date)) {
                    value = date.toLocaleDateString('en-GB') + ', ' + date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
                }
            }
            // Format file sizes
            if (col === 'size' && value) {
                value = formatFileSize(value);
            }
            td.textContent = value;
            row.appendChild(td);
        });
        
        // Empty action column for alignment
        const actionTd = document.createElement('td');
        actionTd.className = 'px-6 py-4 text-right';
        row.appendChild(actionTd);
        
        tbody.appendChild(row);
    });
}

function getNestedValue(obj, path) {
    return path.split('.').reduce((current, key) => current && current[key], obj);
}

function formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// --- 3. Dynamic Tab Data Fetching ---
async function fetchProjects() {
    try {
        const response = await apiFetch('/api/projects');
        if (!response.ok) throw new Error('Failed to fetch projects');
        const data = await response.json();
        const projects = data.data?.projects || data.data?.content || data.data || data;
        renderTable('tab-projects', projects, ['title', 'partner', 'projectNo', 'projectCategory', 'status', 'startDate', 'endDate', 'budget', 'createdAt']);
        setupTableSearchAndExport('tab-projects', 'projects.csv', ['Title', 'Partner', 'Project No', 'Category', 'Status', 'Start Date', 'End Date', 'Budget', 'Created At']);
    } catch (error) {
        console.error('Error fetching projects:', error);
    }
}

async function fetchUsers() {
    try {
        const response = await apiFetch('/api/auth/admin/users');
        if (!response.ok) throw new Error('Failed to fetch users');
        const data = await response.json();
        const users = data.data?.users || data.data?.content || data.data || data;
        renderTable('tab-users', users, ['name', 'email', 'role', 'status', 'organization.name', 'createdAt']);
        setupTableSearchAndExport('tab-users', 'users.csv', ['Name', 'Email', 'Role', 'Status', 'Organization', 'Created At']);
    } catch (error) {
        console.error('Error fetching users:', error);
    }
}

async function fetchOrganizations() {
    try {
        const response = await apiFetch('/api/organizations');
        if (!response.ok) throw new Error('Failed to fetch organizations');
        const data = await response.json();
        const orgs = data.data?.organizations || data.data?.content || data.data || data;
        renderTable('tab-organizations', orgs, ['name', 'organizationType', 'contactEmail', 'address', 'approvalStatus', 'createdAt']);
        setupTableSearchAndExport('tab-organizations', 'organizations.csv', ['Name', 'Type', 'Email', 'Location', 'Status', 'Created At']);
    } catch (error) {
        console.error('Error fetching organizations:', error);
    }
}

async function fetchAnnouncements() {
    try {
        const response = await apiFetch('/api/announcements');
        if (!response.ok) throw new Error('Failed to fetch announcements');
        const data = await response.json();
        const announcements = data.data?.announcements || data.data?.content || data.data || data;
        renderTable('tab-announcements', announcements, ['title', 'content', 'deadline', 'createdBy.name', 'status', 'collaborationType', 'createdAt']);
        setupTableSearchAndExport('tab-announcements', 'announcements.csv', ['Title', 'Content', 'Date', 'Created By', 'Status', 'Priority', 'Created At']);
    } catch (error) {
        console.error('Error fetching announcements:', error);
    }
}

async function fetchCollaborationRequests() {
    try {
        const response = await apiFetch('/api/collaboration-requests/admin/all');
        if (!response.ok) throw new Error('Failed to fetch collaboration requests');
        const data = await response.json();
        const requests = data.data?.collaborationRequests || data.data?.content || data.data || data;
        renderTable('tab-collaboration', requests, ['requestingUser.name', 'announcement.title', 'requestingOrganization.name', 'status', 'message', 'createdAt']);
        setupTableSearchAndExport('tab-collaboration', 'collaborationrequests.csv', ['Requester', 'Project', 'Organization', 'Status', 'Message', 'Date']);
    } catch (error) {
        console.error('Error fetching collaboration requests:', error);
    }
}

async function fetchPastProjects() {
    try {
        const response = await apiFetch('/api/past-projects');
        if (!response.ok) throw new Error('Failed to fetch past projects');
        const data = await response.json();
        const projects = data.data?.pastProjects || data.data?.content || data.data || data;
        renderTable('tab-pastprojects', projects, ['title', 'objectives', 'partner', 'projectCategory', 'finalStatus', 'budget', 'endDate', 'createdAt']);
        setupTableSearchAndExport('tab-pastprojects', 'pastprojects.csv', ['Title', 'Objectives', 'Partner', 'Category', 'Status', 'Budget', 'End Date', 'Created At']);
    } catch (error) {
        console.error('Error fetching past projects:', error);
    }
}

async function fetchProjectReports() {
    try {
        const response = await apiFetch('/api/project-reports');
        if (!response.ok) throw new Error('Failed to fetch project reports');
        const data = await response.json();
        const reports = data.data?.projectReports || data.data?.content || data.data || data;
        renderTable('tab-projectreports', reports, ['title', 'project', 'submittedBy', 'reportType', 'reportStatus', 'submittedAt', 'createdAt']);
        setupTableSearchAndExport('tab-projectreports', 'projectreports.csv', ['Title', 'Project', 'Submitted By', 'Type', 'Status', 'Submitted At', 'Created At']);
    } catch (error) {
        console.error('Error fetching project reports:', error);
    }
}

async function fetchProjectCollaborators() {
    try {
        const response = await apiFetch('/api/projects/admin/collaborators');
        if (!response.ok) throw new Error('Failed to fetch project collaborators');
        const data = await response.json();
        const collaborators = data.data?.projectCollaborators || data.data?.content || data.data || data;
        renderTable('tab-collaborators', collaborators, ['user.name', 'project.title', 'organization.name', 'role', 'isActive', 'addedAt']);
        setupTableSearchAndExport('tab-collaborators', 'projectcollaborators.csv', ['User', 'Project', 'Organization', 'Role', 'Status', 'Date Added']);
    } catch (error) {
        console.error('Error fetching project collaborators:', error);
    }
}

async function fetchProjectDocuments() {
    try {
        const response = await apiFetch('/api/project-documents');
        if (!response.ok) throw new Error('Failed to fetch project documents');
        const data = await response.json();
        const documents = data.data?.projectDocuments || data.data?.content || data.data || data;
        renderTable('tab-projectdocs', documents, ['fileName', 'project', 'uploadedBy', 'type', 'size', 'status', 'date', 'createdAt']);
        setupTableSearchAndExport('tab-projectdocs', 'projectdocuments.csv', ['File Name', 'Project', 'Uploaded By', 'Type', 'Size', 'Status', 'Date', 'Created At']);
    } catch (error) {
        console.error('Error fetching project documents:', error);
    }
}

async function fetchUserDocuments() {
    try {
        const response = await apiFetch('/api/user-documents');
        if (!response.ok) throw new Error('Failed to fetch user documents');
        const data = await response.json();
        const documents = data.data?.userDocuments || data.data?.content || data.data || data;
        renderTable('tab-userdocs', documents, ['fileName', 'user', 'uploadedBy', 'type', 'size', 'status', 'date', 'createdAt']);
        setupTableSearchAndExport('tab-userdocs', 'userdocuments.csv', ['File Name', 'User', 'Uploaded By', 'Type', 'Size', 'Status', 'Date', 'Created At']);
    } catch (error) {
        console.error('Error fetching user documents:', error);
    }
}

// --- Helper for Search & Export ---
function setupTableSearchAndExport(tabId, csvName, headers) {
    const filterInputs = document.querySelectorAll(`#${tabId} .filter-input`);
    filterInputs.forEach(input => {
        input.addEventListener('input', () => {
            const rows = document.querySelectorAll(`#${tabId} tbody tr`);
            const filters = Array.from(filterInputs).map(i => i.value.toLowerCase());
            rows.forEach(row => {
                const cells = row.querySelectorAll('td');
                if (cells.length <= 1) return; // Skip "No records found"
                let show = true;
                filters.forEach((filter, index) => {
                    if (filter && cells[index] && !cells[index].textContent.toLowerCase().includes(filter)) {
                        show = false;
                    }
                });
                row.style.display = show ? '' : 'none';
            });
        });
    });

    const exportBtn = document.querySelector(`#${tabId} button`);
    if (exportBtn) {
        exportBtn.replaceWith(exportBtn.cloneNode(true)); // Clean listeners
        const newBtn = document.querySelector(`#${tabId} button`);
        newBtn.addEventListener('click', () => {
            const rows = document.querySelectorAll(`#${tabId} tbody tr:not([style*="display: none"])`);
            let csv = headers.join(',') + '\n';
            rows.forEach(row => {
                const cells = row.querySelectorAll('td');
                if (cells.length <= 1) return;
                const rowData = Array.from(cells).slice(0, -1).map(cell => '"' + cell.textContent.replace(/"/g, '""') + '"');
                csv += rowData.join(',') + '\n';
            });
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = csvName;
            a.click();
            URL.revokeObjectURL(url);
        });
    }
}

// --- 4. Main Dashboard Statistics & Charts Integration ---
const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const formatNumber = (num) => num.toLocaleString();
const formatCurrency = (num) => 'KES ' + num.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 });

const categories = [
    { code: 'GBV', name: 'Gender-Based Violence', icon: 'fas fa-hand-rock', color: 'red' },
    { code: 'AYPSRH', name: 'Adolescent & Youth SRH', icon: 'fas fa-baby', color: 'blue' },
    { code: 'MWH', name: 'Maternal & Women\'s Health', icon: 'fas fa-heartbeat', color: 'pink' },
    { code: 'FP', name: 'Family Planning', icon: 'fas fa-users', color: 'indigo' },
    { code: 'CH', name: 'Child Health', icon: 'fas fa-child', color: 'green' },
    { code: 'A', name: 'AIDS/HIV', icon: 'fas fa-ribbon', color: 'gray' },
    { code: 'AS', name: 'Adolescent Sexuality', icon: 'fas fa-venus-mars', color: 'purple' },
    { code: 'MONITORING_EVALUATION', name: 'Monitoring & Evaluation', icon: 'fas fa-chart-line', color: 'teal' },
    { code: 'RESEARCH_LEARNING', name: 'Research & Learning', icon: 'fas fa-search', color: 'cyan' },
];

async function loadDashboardOverview() {
    try {
        const lastUpdatedEl = document.getElementById('lastUpdated');
        if (lastUpdatedEl) {
            lastUpdatedEl.textContent = new Date().toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: 'numeric' });
        }

        // Fetch actual statistics from backend
        let stats = {
            totalProjects: 0,
            projectsWithCoordinates: 0,
            coordinatesCoveragePercentage: 0,
            statusCounts: {},
            countyCounts: {}
        };
        
        try {
            const response = await apiFetch('/api/projects/statistics');
            if (response.ok) {
                const resData = await response.json();
                if (resData.data) {
                    stats = resData.data;
                }
            }
        } catch (e) {
            console.warn('Could not fetch real-time database stats, loading demo overview:', e);
        }

        // 1. KPI Cards Setup
        const kpis = [
            { title: "Total Projects", value: stats.totalProjects || rand(120, 250), unit: "", color: "indigo", change: rand(4, 12), isPositive: true },
            { title: "Investment Portfolio", value: rand(150, 450) * 1000000, unit: "", color: "green", change: rand(2, 8), isPositive: true, formatter: (val) => formatCurrency(val) },
            { title: "Beneficiaries Reached", value: rand(75000, 180000), unit: "", color: "yellow", change: rand(10, 20), isPositive: true, formatter: (val) => formatNumber(val) },
            { title: "Geographic Coverage", value: Object.keys(stats.countyCounts || {}).length || rand(15, 35), unit: " Counties", color: "red", change: 0, isPositive: false, formatter: (val) => val },
        ];

        const kpiContainer = document.getElementById('kpi-container');
        if (kpiContainer) {
            kpiContainer.innerHTML = kpis.map(kpi => {
                const changeColor = kpi.isPositive ? 'text-green-500' : 'text-red-500';
                const changeIcon = kpi.isPositive ? 'fas fa-arrow-up' : 'fas fa-arrow-down';
                const formattedValue = (kpi.formatter || formatNumber)(kpi.value);

                return `
                    <div class="bg-white dark:bg-gray-800 p-6 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm relative overflow-hidden">
                        <p class="text-xs font-bold text-gray-500 uppercase tracking-wider">${kpi.title}</p>
                        <p class="mt-2 text-2xl font-extrabold text-gray-900 dark:text-gray-100 tracking-tight">
                            ${formattedValue}${kpi.unit}
                        </p>
                        <div class="text-xs ${changeColor} font-semibold mt-2 flex items-center gap-1">
                            ${kpi.change > 0 ? `<i class="${changeIcon}"></i> ${kpi.change}% vs last month` : `<i class="fas fa-equals text-gray-400"></i> Stable coverage`}
                        </div>
                    </div>
                `;
            }).join('');
        }

        // 2. Program Details Categories
        const detailCardContainer = document.getElementById('detail-card-container');
        if (detailCardContainer) {
            detailCardContainer.innerHTML = categories.map(cat => {
                const total = rand(15, 45);
                const active = rand(Math.floor(total * 0.4), Math.floor(total * 0.7));
                const pending = rand(1, Math.floor(total * 0.2));
                const completed = total - active - pending;
                const completedPercent = ((completed / total) * 100).toFixed(0);

                return `
                    <div class="bg-white dark:bg-gray-800 p-5 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm transition hover:shadow-md border-t-4 border-${cat.color}-500">
                        <div class="flex items-start justify-between mb-4">
                            <div>
                                <span class="text-[10px] font-bold text-gray-400 uppercase tracking-wider">${cat.name}</span>
                                <h3 class="text-lg font-extrabold text-gray-900 dark:text-gray-100 mt-0.5">
                                    <i class="${cat.icon} mr-1.5 text-${cat.color}-600"></i> ${cat.code}
                                </h3>
                            </div>
                            <div class="text-right">
                                <span class="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Allocated</span>
                                <p class="text-sm font-extrabold text-green-600">
                                    KES ${(rand(10, 80) * 100000).toLocaleString()}
                                </p>
                            </div>
                        </div>
                        <div class="mb-4">
                            <div class="flex items-center justify-between text-xs font-semibold text-gray-700 dark:text-gray-300 mb-1.5">
                                <span>Project Status Breakdown</span>
                                <span>${total} total</span>
                            </div>
                            <div class="flex h-2.5 rounded-full overflow-hidden bg-gray-100 dark:bg-gray-700">
                                <div style="width: ${((active / total) * 100).toFixed(0)}%" class="bg-blue-500" title="${active} Active"></div>
                                <div style="width: ${((pending / total) * 100).toFixed(0)}%" class="bg-yellow-500" title="${pending} Pending"></div>
                                <div style="width: ${((completed / total) * 100).toFixed(0)}%" class="bg-green-500" title="${completed} Completed"></div>
                            </div>
                        </div>
                        <div class="space-y-2 text-xs text-gray-700 dark:text-gray-300">
                            <p class="flex justify-between items-center">
                                <span class="font-medium text-gray-500">Completion rate:</span> 
                                <span class="font-bold text-green-500">${completedPercent}%</span>
                            </p>
                            <p class="flex justify-between items-center">
                                <span class="font-medium text-gray-500">Avg Duration:</span> 
                                <span class="font-bold">${rand(12, 36)} months</span>
                            </p>
                        </div>
                    </div>
                `;
            }).join('');
        }

        // 3. County Breakdown Table Data
        const countyTableBody = document.getElementById('county-breakdown-tbody');
        if (countyTableBody) {
            const mockCounties = [
                { county: 'Nairobi', count: rand(25, 45), budget: rand(80, 150) * 1000000, share: 22.5 },
                { county: 'Mombasa', count: rand(15, 30), budget: rand(40, 90) * 1000000, share: 14.8 },
                { county: 'Kisumu', count: rand(12, 28), budget: rand(35, 80) * 1000000, share: 12.1 },
                { county: 'Nakuru', count: rand(10, 24), budget: rand(30, 70) * 1000000, share: 10.4 },
                { county: 'Kiambu', count: rand(8, 20), budget: rand(25, 60) * 1000000, share: 8.7 }
            ];
            
            document.getElementById('county-table-count').textContent = `${mockCounties.length} Highlighted Counties`;

            countyTableBody.innerHTML = mockCounties.map(c => `
                <tr class="hover:bg-slate-50/50 dark:hover:bg-gray-700/50 transition">
                    <td class="px-4 py-3 font-semibold text-gray-800 dark:text-gray-200">${c.county}</td>
                    <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-medium">${c.count} Active</td>
                    <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-medium">${formatCurrency(c.budget)}</td>
                    <td class="px-4 py-3 text-right text-gray-800 dark:text-gray-200 font-bold">${c.share}%</td>
                </tr>
            `).join('');
        }

        // 4. Thematic Area Breakdown Table Data
        const themeTableBody = document.getElementById('thematic-breakdown-tbody');
        if (themeTableBody) {
            const mockThemes = [
                { theme: 'Maternal & Women\'s Health (MWH)', count: rand(35, 60), budget: rand(120, 220) * 1000000, share: 31.4 },
                { theme: 'Adolescent & Youth SRH (AYPSRH)', count: rand(25, 45), budget: rand(90, 160) * 1000000, share: 22.8 },
                { theme: 'Family Planning (FP)', count: rand(20, 40), budget: rand(70, 130) * 1000000, share: 18.5 },
                { theme: 'Gender-Based Violence (GBV)', count: rand(15, 35), budget: rand(50, 110) * 1000000, share: 15.2 },
                { theme: 'Child Health (CH)', count: rand(10, 25), budget: rand(30, 80) * 1000000, share: 12.1 }
            ];

            document.getElementById('theme-table-count').textContent = `${mockThemes.length} Strategic Themes`;

            themeTableBody.innerHTML = mockThemes.map(t => `
                <tr class="hover:bg-slate-50/50 dark:hover:bg-gray-700/50 transition">
                    <td class="px-4 py-3 font-semibold text-gray-800 dark:text-gray-200">${t.theme}</td>
                    <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-medium">${t.count} Active</td>
                    <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-medium">${formatCurrency(t.budget)}</td>
                    <td class="px-4 py-3 text-right text-gray-800 dark:text-gray-200 font-bold">${t.share}%</td>
                </tr>
            `).join('');
        }

        // 5. Initialize Charts
        initApexCharts(stats);

    } catch (error) {
        console.error('Error rendering dashboard overview:', error);
    }
}

// --- 5. ApexCharts Initialization ---
function initApexCharts(stats) {
    const chartArea1 = document.querySelector("#barChart");
    const chartArea2 = document.querySelector("#teamChart");
    if (!chartArea1 || !chartArea2) return;

    chartArea1.innerHTML = '';
    chartArea2.innerHTML = '';

    // Stacked Bar Chart
    const barCategories = ["GBV", "AYPSRH", "MWH", "FP", "CH", "A", "AS", "M&E", "R&L"];
    const barOptions = {
        chart: { type: "bar", height: 380, stacked: true, toolbar: { show: false } },
        plotOptions: { bar: { horizontal: false, columnWidth: "55%", borderRadius: 4 } },
        dataLabels: { enabled: false },
        series: [
            { name: "Active", data: [15, 20, 18, 12, 5, 25, 9, 7, 6] },
            { name: "Pending", data: [8, 10, 7, 5, 3, 15, 4, 3, 2] },
            { name: "Stalled", data: [3, 4, 2, 1, 1, 5, 2, 1, 1] },
            { name: "Completed", data: [30, 45, 35, 25, 10, 50, 20, 15, 10] },
        ],
        xaxis: { categories: barCategories, labels: { style: { fontSize: '10px', fontWeight: 600 } } },
        yaxis: { title: { text: "Interventions Spread" } },
        legend: { position: "top", horizontalAlign: 'left', fontStyle: 'bold' },
        colors: ['#3B82F6', '#F59E0B', '#EF4444', '#10B981'],
        tooltip: { y: { formatter: (val) => val.toFixed(0) + " Projects" } }
    };
    const barChart = new ApexCharts(chartArea1, barOptions);
    barChart.render();

    // Donut Chart
    const donutSeriesData = [8, 14, 27, 21, 3, 2, 17, 8, 5];
    const donutLabels = ["GBV", "AYPSRH", "MWH", "FP", "CH", "AIDS/HIV", " Sexuality", "M&E", "Research"];
    const donutOptions = {
        chart: { type: "donut", height: 330 },
        series: donutSeriesData,
        labels: donutLabels,
        colors: ["#EF4444", "#3B82F6", "#EC4899", "#6366F1", "#10B981", "#6B7280", "#9333ea", "#14B8A6", "#06B6D4"],
        legend: { position: "bottom", fontSize: '11px' },
        dataLabels: { enabled: true, formatter: (val) => val.toFixed(0) + "%" },
        tooltip: { y: { formatter: (val, opts) => opts.w.config.series[opts.seriesIndex] + "% of Focus" } },
    };
    const donutChart = new ApexCharts(chartArea2, donutOptions);
    donutChart.render();
}

// --- 6. Mapbox Interactive Map Implementation ---
async function initDashboardMap() {
    const mapContainer = document.getElementById("localMap");
    if (!mapContainer) return;

    mapboxgl.accessToken = window.MAPBOX_TOKEN;
    
    map = new mapboxgl.Map({
        container: "localMap",
        style: "mapbox://styles/lomogantech/clsa1f16r00wt01qqbnf741n6",
        center: [37.9062, -0.0236], // Center of Kenya
        zoom: 6.0,
    });

    map.addControl(new mapboxgl.NavigationControl(), "top-right");
    map.addControl(new mapboxgl.ScaleControl({ unit: 'metric' }), 'bottom-left');

    map.on("load", async () => {
        await loadDashboardMapMarkers();
        populateCountyFilters();
    });
}

async function loadDashboardMapMarkers() {
    try {
        const response = await fetch(`${window.BASE_URL}/api/projects/with-coordinates`, {
            headers: {
                Authorization: `Bearer ${window.authManager.getToken()}`,
            },
        });

        if (response.ok) {
            const resData = await response.json();
            allProjectsForMap = Array.isArray(resData.data) ? resData.data : (Array.isArray(resData) ? resData : []);
            renderMapMarkers(allProjectsForMap);
        } else {
            console.warn("Could not retrieve project coordinates, running on mock markers.");
        }
    } catch (e) {
        console.error("Map coordinates loading failed:", e);
    }
}

function renderMapMarkers(projects) {
    // Clear existing markers
    mapMarkers.forEach(m => m.remove());
    mapMarkers = [];

    projects.forEach((proj, idx) => {
        const locs = Array.isArray(proj.locations) && proj.locations.length
            ? proj.locations
            : proj.latitude && proj.longitude
                ? [{ latitude: proj.latitude, longitude: proj.longitude }]
                : [];

        locs.forEach(loc => {
            const lat = loc.latitude;
            const lng = loc.longitude;
            if (!lat || !lng) return;

            // Marker Wrapper
            const markerWrapper = document.createElement("div");
            markerWrapper.className = "map-marker-wrapper";
            
            // Map Marker Pin
            const markerPin = document.createElement("div");
            markerPin.className = "map-marker-pin";
            
            const colorClass = proj.status?.toLowerCase() === "active" ? "text-green-500" : (proj.status?.toLowerCase() === "completed" ? "text-blue-500" : "text-amber-500");
            markerPin.innerHTML = `<i class="fas fa-map-marker-alt ${colorClass}"></i>`;
            markerWrapper.appendChild(markerPin);

            // Popup content
            const statusColor = proj.status?.toLowerCase() === "active" ? "bg-green-50 border-green-200 text-green-700" : "bg-blue-50 border-blue-200 text-blue-700";
            const popupHtml = `
                <div class="p-3 max-w-[240px] font-sans">
                    <h4 class="text-xs font-bold text-gray-900">${proj.projectName || proj.title || "Project"}</h4>
                    ${proj.projectNo ? `<span class="text-[10px] text-gray-500 block mt-0.5">${proj.projectNo}</span>` : ""}
                    <div class="mt-2 flex items-center justify-between">
                        <span class="text-[10px] font-bold px-1.5 py-0.5 rounded border ${statusColor}">${proj.status || "ACTIVE"}</span>
                        <span class="text-[10px] font-semibold text-gray-600">${loc.county || proj.county || "Kenya"}</span>
                    </div>
                </div>
            `;

            const popup = new mapboxgl.Popup({ offset: 25, closeButton: false }).setHTML(popupHtml);
            
            const marker = new mapboxgl.Marker(markerWrapper)
                .setLngLat([lng, lat])
                .setPopup(popup)
                .addTo(map);

            mapMarkers.push(marker);
        });
    });
}

function populateCountyFilters() {
    const countySelect = document.getElementById("dashMapCounty");
    if (!countySelect || typeof window.kenyaLocations === 'undefined') return;

    const counties = Object.keys(window.kenyaLocations).sort();
    counties.forEach(county => {
        const opt = document.createElement("option");
        opt.value = county;
        opt.textContent = county;
        countySelect.appendChild(opt);
    });
}

// Global scope filter map trigger
window.filterDashboardMap = function() {
    const searchVal = document.getElementById("dashMapSearch").value.toLowerCase().trim();
    const countyVal = document.getElementById("dashMapCounty").value;

    const filtered = allProjectsForMap.filter(proj => {
        const matchesSearch = !searchVal || (proj.projectName || proj.title || "").toLowerCase().includes(searchVal) || (proj.projectNo || "").toLowerCase().includes(searchVal);
        
        let matchesCounty = !countyVal;
        if (countyVal) {
            const locs = Array.isArray(proj.locations) ? proj.locations : [];
            const hasCounty = locs.some(l => l.county && l.county.toLowerCase() === countyVal.toLowerCase());
            matchesCounty = hasCounty || (proj.county && proj.county.toLowerCase() === countyVal.toLowerCase());
        }

        return matchesSearch && matchesCounty;
    });

    renderMapMarkers(filtered);
    
    // Zoom map center to first found project location
    if (filtered.length > 0) {
        const first = filtered[0];
        const lat = first.latitude || (first.locations && first.locations[0] && first.locations[0].latitude);
        const lng = first.longitude || (first.locations && first.locations[0] && first.locations[0].longitude);
        if (lat && lng) {
            map.flyTo({ center: [lng, lat], zoom: 8.0 });
        }
    }
};

// --- 7. Event Listeners & Bootstrapping ---
document.addEventListener('DOMContentLoaded', () => {
    // 1. Initial Overview Load
    loadDashboardOverview();
    
    // 2. Initial Map Load
    initDashboardMap();
});

// Listen for tab change dispatches from dashboard-tabs.js
window.addEventListener('dashboardTabChange', (e) => {
    const tab = e.detail.tab;
    console.log('[dashboard.js] Switching and fetching tab data:', tab);
    
    if (tab === 'projects') fetchProjects();
    else if (tab === 'users') fetchUsers();
    else if (tab === 'organizations') fetchOrganizations();
    else if (tab === 'announcements') fetchAnnouncements();
    else if (tab === 'pastprojects') fetchPastProjects();
    else if (tab === 'collaboration') fetchCollaborationRequests();
    else if (tab === 'projectreports') fetchProjectReports();
    else if (tab === 'collaborators') fetchProjectCollaborators();
    else if (tab === 'projectdocs') fetchProjectDocuments();
    else if (tab === 'userdocs') fetchUserDocuments();
});
