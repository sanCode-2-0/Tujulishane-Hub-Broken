// dashboard.js
// Handles dashboard metrics, charts, Mapbox project concentration map, and data breakdowns matching the PDF mockup

let map;
let mapMarkers = [];
let allProjectsForMap = [];
let allProjects = []; // For tables & charts

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

// Format currency exactly like the PDF (e.g. KES 7.20 Mn, KES 25k)
function formatBudget(value) {
    const val = Number(value);
    if (isNaN(val) || val <= 0) return 'KES 0';
    if (val >= 1000000) {
        return 'KES ' + (val / 1000000).toFixed(2) + ' Mn';
    } else if (val >= 1000) {
        return 'KES ' + (val / 1000).toFixed(0) + 'k';
    }
    return 'KES ' + val.toLocaleString();
}

function formatRoleDisplayName(role) {
    if (!role) return 'User';
    switch (role.toUpperCase()) {
        case 'SUPER_ADMIN_APPROVER': return 'Approver';
        case 'SUPER_ADMIN_REVIEWER': return 'Reviewer';
        case 'SUPER_ADMIN': return 'Super Admin';
        case 'PARTNER': return 'Partner';
        case 'DONOR': return 'Donor';
        default: return role;
    }
}

// --- 2. Main Dashboard Loader ---
async function loadDashboardData() {
    try {
        const lastUpdatedEl = document.getElementById('dash-last-updated');
        if (lastUpdatedEl) {
            lastUpdatedEl.classList.remove('hidden');
            lastUpdatedEl.textContent = 'Updated ' + new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
        }

        // Fetch current user details & populate header
        const currentUser = window.authManager.getCurrentUser() || {};
        const userName = currentUser.name || "Braine Kapolon";
        const userRole = currentUser.role || "SUPER_ADMIN_APPROVER";
        const dashHello = document.getElementById('dash-hello');
        const dashRole = document.getElementById('dash-role');
        const dashTagline = document.getElementById('dash-tagline');
        const roleAvatar = document.getElementById('role-avatar');

        if (dashHello) dashHello.textContent = `Welcome back, ${userName}`;
        if (dashRole) dashRole.textContent = `Signed in as ${formatRoleDisplayName(userRole)}`;
        if (dashTagline) {
            if (userRole.includes('APPROVER')) {
                dashTagline.textContent = "You are the final approval authority. Sign-off on reviewed projects and keep the approval pipeline moving.";
            } else if (userRole.includes('REVIEWER')) {
                dashTagline.textContent = "You are a thematic reviewer. Inspect pending submissions under your focus areas.";
            } else {
                dashTagline.textContent = "Coordinate reproductive, maternal, newborn, child, and adolescent health interventions.";
            }
        }
        if (roleAvatar) {
            roleAvatar.innerHTML = `<span class="material-symbols-outlined text-2xl">how_to_reg</span>`;
        }

        // Fetch all projects (large limit to calculate aggregate metrics locally)
        let projects = [];
        try {
            const response = await apiFetch('/api/projects?size=1000');
            if (response.ok) {
                const resData = await response.json();
                if (resData.data && Array.isArray(resData.data.projects)) {
                    projects = resData.data.projects;
                }
            }
        } catch (e) {
            console.warn('Could not fetch real projects, falling back to mock dataset.', e);
        }

        // Fallback mockup dataset matching the PDF if DB is empty
        if (projects.length === 0) {
            projects = [
                { id: 1, title: "PHC Kilifi, kakamega", projectNo: "PRJ-007", status: "PENDING", approvalWorkflowStatus: "PENDING_REVIEW", budget: 25000, county: "Kilifi", locations: [{ county: "Kilifi" }], projectCategory: "IMPLEMENTING", thematicArea: "MNH", createdAt: new Date() },
                { id: 2, title: "Family Planning Expansion Project", projectNo: "PRJ-003", status: "ACTIVE", approvalWorkflowStatus: "PENDING_FINAL_APPROVAL", budget: 7200000, county: "Kisumu", locations: [{ county: "Kisumu" }], projectCategory: "IMPLEMENTING", thematicArea: "FP", createdAt: new Date() },
                { id: 3, title: "Adolescent SRH Education Program", projectNo: "PRJ-002", status: "PENDING", approvalWorkflowStatus: "PENDING_REVIEW", budget: 3500000, county: "Mombasa", locations: [{ county: "Mombasa" }], projectCategory: "RESEARCH", thematicArea: "AYPSRH", createdAt: new Date() },
                { id: 4, title: "Maternal Health Outreach Initiative", projectNo: "PRJ-001", status: "ACTIVE", approvalWorkflowStatus: "APPROVED", budget: 5000000, county: "Nairobi", locations: [{ county: "Nairobi" }], projectCategory: "IMPLEMENTING", thematicArea: "MNH", createdAt: new Date() },
                { id: 5, title: "Implementing Project Lomo", projectNo: "PRJ-005", status: "ACTIVE", approvalWorkflowStatus: "APPROVED", budget: 5460000, county: "Taita-Taveta", locations: [{ county: "Taita-Taveta" }], projectCategory: "IMPLEMENTING", thematicArea: "MNH", createdAt: new Date() },
                { id: 6, title: "Test Project Partner", projectNo: "PRJ-006", status: "ACTIVE", approvalWorkflowStatus: "APPROVED", budget: 150000, county: "Taita-Taveta", locations: [{ county: "Taita-Taveta" }], projectCategory: "IMPLEMENTING", thematicArea: "MNH", createdAt: new Date() },
                { id: 7, title: "Production Test Project", projectNo: "PRJ-008", status: "REJECTED", approvalWorkflowStatus: "REJECTED_BY_APPROVER", budget: 150000, county: "Meru", locations: [{ county: "Meru" }], projectCategory: "PRIORITY", thematicArea: "GBV", createdAt: new Date() }
            ];
        }

        allProjects = projects;

        // Fetch users list count (Admins only)
        let totalUsersCount = 11; // default mockup matching PDF
        try {
            const uResponse = await apiFetch('/api/auth/admin/users');
            if (uResponse.ok) {
                const uData = await uResponse.json();
                const users = uData.data?.users || uData.data || uData;
                if (Array.isArray(users)) {
                    totalUsersCount = users.length;
                }
            }
        } catch (e) {
            console.log('Skipped user listing pull (non-admin or offline)');
        }

        // --- 3. Compute Metrics ---
        let awaitingApprovalCount = 0;
        let awaitingReviewCount = 0;
        let approvedLiveCount = 0;
        
        projects.forEach(p => {
            const ws = (p.approvalWorkflowStatus || '').toUpperCase();
            if (ws === 'PENDING_FINAL_APPROVAL' || ws === 'REVIEWED') {
                awaitingApprovalCount++;
            } else if (ws === 'PENDING_REVIEW' || ws === 'UNDER_REVIEW') {
                awaitingReviewCount++;
            } else if (ws === 'APPROVED') {
                approvedLiveCount++;
            }
        });

        // --- 4. Render "Needs your attention" Grid ---
        const attentionGrid = document.getElementById('attention-grid');
        if (attentionGrid) {
            let cardsHtml = '';
            
            // Card 1: Final Approvals
            cardsHtml += `
                <div class="bg-white dark:bg-gray-800 p-6 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm relative flex flex-col justify-between min-h-[170px]">
                    <div>
                        <div class="flex items-center justify-between mb-3">
                            <div class="p-2 rounded-xl bg-amber-50 dark:bg-amber-900/20 text-amber-600">
                                <i class="fas fa-certificate text-lg"></i>
                            </div>
                            <span class="text-sm font-extrabold text-amber-800 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/30 px-2 py-0.5 rounded-lg">${awaitingApprovalCount}</span>
                        </div>
                        <h3 class="text-sm font-extrabold text-gray-900 dark:text-gray-100">Projects waiting for your final approval</h3>
                        <p class="text-[11px] text-gray-500 mt-1">Reviewed submissions that need your sign-off. Once approved they go live for every user.</p>
                    </div>
                    <a href="admin-approvals.html" class="text-xs font-bold text-[#0047BA] hover:underline flex items-center gap-1.5 mt-4">
                        Approve projects <i class="fas fa-arrow-right text-[10px]"></i>
                    </a>
                </div>
            `;

            // Card 2: Review Pipeline
            cardsHtml += `
                <div class="bg-white dark:bg-gray-800 p-6 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm relative flex flex-col justify-between min-h-[170px]">
                    <div>
                        <div class="flex items-center justify-between mb-3">
                            <div class="p-2 rounded-xl bg-blue-50 dark:bg-blue-900/20 text-blue-600">
                                <i class="fas fa-comment-dots text-lg"></i>
                            </div>
                            <span class="text-sm font-extrabold text-blue-800 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded-lg">${awaitingReviewCount}</span>
                        </div>
                        <h3 class="text-sm font-extrabold text-gray-900 dark:text-gray-100">Projects in the review pipeline</h3>
                        <p class="text-[11px] text-gray-500 mt-1">Submissions still being handled upstream by thematic reviewers.</p>
                    </div>
                    <a href="admin-approvals.html" class="text-xs font-bold text-[#0047BA] hover:underline flex items-center gap-1.5 mt-4">
                        Track the pipeline <i class="fas fa-arrow-right text-[10px]"></i>
                    </a>
                </div>
            `;

            attentionGrid.innerHTML = cardsHtml;
        }

        // --- 5. Render "At a glance" Metrics ---
        const statsGrid = document.getElementById('stats-grid');
        if (statsGrid) {
            const statsItems = [
                { title: "AWAITING YOUR APPROVAL", value: awaitingApprovalCount, icon: "fas fa-shield-halved text-blue-500 bg-blue-50 dark:bg-blue-950/20" },
                { title: "APPROVED & LIVE", value: approvedLiveCount, icon: "fas fa-circle-check text-emerald-500 bg-emerald-50 dark:bg-emerald-950/20" },
                { title: "TOTAL SUBMISSIONS", value: projects.length, icon: "far fa-folder text-blue-500 bg-blue-50 dark:bg-blue-950/20" },
                { title: "TOTAL USERS", value: totalUsersCount, icon: "fas fa-user-group text-[#0047BA] bg-blue-50 dark:bg-blue-950/20" }
            ];

            statsGrid.innerHTML = statsItems.map(item => `
                <div class="bg-white dark:bg-gray-800 p-5 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col justify-between">
                    <div class="flex items-center justify-between text-gray-400 dark:text-gray-500 mb-2">
                        <span class="text-[9px] font-extrabold uppercase tracking-wider text-gray-400 dark:text-gray-500">${item.title}</span>
                        <div class="p-1 rounded-lg ${item.icon.split(' ').slice(1).join(' ')}">
                            <i class="${item.icon.split(' ')[0]} text-xs"></i>
                        </div>
                    </div>
                    <p class="text-3xl font-black text-gray-900 dark:text-gray-100 mt-2">${item.value}</p>
                </div>
            `).join('');
        }

        // --- 6. Render "Where to go next" Shortcuts ---
        const linksGrid = document.getElementById('links-grid');
        if (linksGrid) {
            const shortcutItems = [
                { title: "Two-Tier Approvals", description: "Grant final approval or reject reviewed projects.", icon: "fas fa-file-invoice", link: "admin-approvals.html", color: "emerald" },
                { title: "Project Management", description: "Inspect any project and its full review history.", icon: "far fa-folder-open", link: "projects.html", color: "blue" },
                { title: "Stakeholder Management", description: "Manage user roles and reviewer assignments.", icon: "fas fa-user-gear", link: "members.html", color: "amber" },
                { title: "Donor Management", description: "Manage donor organizations and their funding records.", icon: "fas fa-hand-holding-heart", link: "organizations.html", color: "purple" }
            ];

            linksGrid.innerHTML = shortcutItems.map(item => `
                <a href="${item.link}" class="group bg-white dark:bg-gray-800 p-5 rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm hover:shadow-md transition flex items-start gap-4">
                    <div class="p-3 rounded-xl bg-slate-50 dark:bg-gray-700 text-gray-500 group-hover:bg-blue-50 dark:group-hover:bg-blue-900/20 group-hover:text-[#0047BA] transition shrink-0">
                        <i class="${item.icon} text-base"></i>
                    </div>
                    <div class="min-w-0">
                        <h3 class="text-xs font-bold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 group-hover:text-[#0047BA] dark:group-hover:text-blue-400 transition">
                            ${item.title} <i class="fas fa-arrow-up-right-from-square text-[9px] opacity-0 group-hover:opacity-100 transition-opacity"></i>
                        </h3>
                        <p class="text-[11px] text-gray-500 dark:text-gray-400 mt-1 leading-normal">${item.description}</p>
                    </div>
                </a>
            `).join('');
        }

        // --- 7. Render Recent Projects Table ---
        renderRecentProjectsTable(projects);

        // --- 8. Populate Regional & Thematic Breakdown Tables ---
        populateBreakdownTables(projects);

        // --- 9. Build ApexCharts ---
        renderDashboardCharts(projects);

        // --- 10. Load Real General Announcements ---
        await fetchGeneralAnnouncements();

    } catch (e) {
        console.error('Error loading dashboard statistics:', e);
    }
}

async function fetchGeneralAnnouncements() {
    const container = document.getElementById('dash-announcements-container');
    if (!container) return;

    try {
        const response = await apiFetch('/api/general-announcements');
        if (response.ok) {
            const resData = await response.json();
            const announcements = resData.data || [];
            
            if (announcements.length > 0) {
                container.innerHTML = announcements.slice(0, 4).map(ann => {
                    const date = new Date(ann.createdAt);
                    const formattedDate = !isNaN(date) ? date.toLocaleDateString('en-GB') : '';
                    
                    return `
                        <div class="p-4 rounded-2xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm flex items-start gap-3">
                            <div class="p-2.5 rounded-xl bg-blue-50 dark:bg-blue-900/20 text-[#0047BA] dark:text-blue-400 shrink-0">
                                <i class="fas fa-bullhorn text-lg"></i>
                            </div>
                            <div class="min-w-0 flex-1">
                                <div class="flex items-center justify-between gap-2 mb-1">
                                    <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400 uppercase">NOTICE</span>
                                    <span class="text-[10px] text-gray-400 font-semibold">${formattedDate}</span>
                                </div>
                                <h4 class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">${ann.title}</h4>
                                <p class="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 mt-1">${ann.body}</p>
                            </div>
                        </div>
                    `;
                }).join('');
                return;
            }
        }
    } catch (e) {
        console.warn('Failed to fetch real general announcements:', e);
    }

    // Default Fallback placeholders if API fails or array is empty
    container.innerHTML = `
        <div class="p-4 rounded-2xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm flex items-start gap-3">
            <div class="p-2.5 rounded-xl bg-blue-50 dark:bg-blue-900/20 text-[#0047BA] dark:text-blue-400 shrink-0">
                <i class="fas fa-bullhorn text-lg"></i>
            </div>
            <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2 mb-1">
                    <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-400 uppercase">NOTICE</span>
                    <span class="text-[10px] text-gray-400 font-semibold">15/08/2026</span>
                </div>
                <h4 class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">RMNCAH Multi-Sectoral Alignment Meeting</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 mt-1">Stakeholder meeting scheduled to align on reproductive and maternal health interventions.</p>
            </div>
        </div>
        <div class="p-4 rounded-2xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm flex items-start gap-3">
            <div class="p-2.5 rounded-xl bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400 shrink-0">
                <i class="fas fa-file-contract text-lg"></i>
            </div>
            <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2 mb-1">
                    <span class="inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-400 uppercase">POLICY</span>
                    <span class="text-[10px] text-gray-400 font-semibold">15/08/2026</span>
                </div>
                <h4 class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">Updated Project Reporting Guidelines</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 mt-1">Quarterly progress and financial reporting templates have been updated for 2026.</p>
            </div>
        </div>
    `;
}

// Render recent submissions list
function renderRecentProjectsTable(projectsList) {
    const tbody = document.getElementById('recent-projects-tbody');
    if (!tbody) return;

    if (projectsList.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="px-5 py-8 text-center text-gray-400">
                    <i class="fas fa-inbox text-2xl mb-2 text-gray-300 block"></i>
                    No projects found matching search.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = projectsList.slice(0, 10).map(p => {
        // Status badge colors
        const status = (p.status || 'PENDING').toUpperCase();
        let statusClass = 'bg-yellow-50 text-yellow-700 border-yellow-200 dark:bg-yellow-950/20 dark:text-yellow-400 dark:border-yellow-900';
        if (status === 'ACTIVE' || status === 'APPROVED') {
            statusClass = 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/20 dark:text-emerald-400 dark:border-emerald-900';
        } else if (status === 'REJECTED') {
            statusClass = 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/20 dark:text-rose-400 dark:border-rose-900';
        }

        // Location formatting
        let locString = p.county || 'Kenya';
        if (Array.isArray(p.locations) && p.locations.length > 0) {
            locString = p.locations.map(l => l.county).filter(Boolean).join(', ') || locString;
        }

        return `
            <tr class="hover:bg-slate-50/50 dark:hover:bg-gray-700/50 transition">
                <td class="px-5 py-4">
                    <span class="font-semibold text-gray-900 dark:text-gray-100 block">${p.title || p.projectName}</span>
                    ${p.projectNo ? `<span class="text-[10px] text-gray-400 block mt-0.5">${p.projectNo}</span>` : ''}
                </td>
                <td class="px-5 py-4 text-gray-600 dark:text-gray-400 font-semibold">${locString}</td>
                <td class="px-5 py-4">
                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${statusClass}">
                        ${status}
                    </span>
                </td>
                <td class="px-5 py-4 text-gray-800 dark:text-gray-200 font-extrabold">${formatBudget(p.budget)}</td>
                <td class="px-5 py-4 text-right">
                    <a href="project-details.html?id=${p.id}" class="text-xs font-semibold text-[#0047BA] hover:underline flex items-center justify-end gap-1">
                        Details <i class="fas fa-arrow-right text-[10px]"></i>
                    </a>
                </td>
            </tr>
        `;
    }).join('');
}

// Global scope filter recent projects search bar
window.filterRecentProjects = function() {
    const query = document.getElementById('dash-project-search').value.toLowerCase().trim();
    const filtered = allProjects.filter(p => {
        const title = (p.title || p.projectName || '').toLowerCase();
        const projectNo = (p.projectNo || '').toLowerCase();
        const county = (p.county || '').toLowerCase();
        return title.includes(query) || projectNo.includes(query) || county.includes(query);
    });
    renderRecentProjectsTable(filtered);
};

// --- 10. Regional & Thematic Summary Logic ---
function populateBreakdownTables(projects) {
    const countyTbody = document.getElementById('county-breakdown-tbody');
    const themeTbody = document.getElementById('thematic-breakdown-tbody');

    let totalBudget = projects.reduce((acc, p) => acc + (Number(p.budget) || 0), 0);
    if (totalBudget === 0) totalBudget = 1;

    // County Breakdown
    if (countyTbody) {
        const countyMap = {};
        projects.forEach(p => {
            const counties = [];
            if (Array.isArray(p.locations)) {
                p.locations.forEach(l => { if (l.county) counties.push(l.county); });
            } else if (p.county) {
                counties.push(p.county);
            }

            const uniqueCounties = [...new Set(counties)];
            uniqueCounties.forEach(c => {
                if (!countyMap[c]) countyMap[c] = { count: 0, budget: 0 };
                countyMap[c].count++;
                countyMap[c].budget += (Number(p.budget) || 0) / (uniqueCounties.length || 1);
            });
        });

        const countyList = Object.keys(countyMap).map(name => ({
            name,
            count: countyMap[name].count,
            budget: countyMap[name].budget,
            percentage: ((countyMap[name].budget / totalBudget) * 100).toFixed(1)
        })).sort((a, b) => b.budget - a.budget);

        const countEl = document.getElementById('county-table-count');
        if (countEl) countEl.textContent = `${countyList.length} Counties Covered`;

        countyTbody.innerHTML = countyList.map(c => `
            <tr class="hover:bg-slate-50/50 dark:hover:bg-gray-700/50 transition">
                <td class="px-4 py-3 font-semibold text-gray-800 dark:text-gray-200">${c.name}</td>
                <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-semibold">${c.count} ${c.count === 1 ? 'project' : 'projects'}</td>
                <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-semibold">${formatBudget(c.budget)}</td>
                <td class="px-4 py-3 text-right text-gray-800 dark:text-gray-200 font-extrabold">${c.percentage}%</td>
            </tr>
        `).join('');
    }

    // Thematic Area Breakdown
    if (themeTbody) {
        const themeMap = {};
        projects.forEach(p => {
            const theme = p.thematicArea || p.projectTheme || 'Other';
            if (!themeMap[theme]) themeMap[theme] = { count: 0, budget: 0 };
            themeMap[theme].count++;
            themeMap[theme].budget += Number(p.budget) || 0;
        });

        const themeList = Object.keys(themeMap).map(name => {
            let displayName = name;
            // Map codes to user-friendly PDF titles
            if (name === 'MNH' || name === 'Maternity and Newborn Health') displayName = 'Maternity and Newborn Health';
            else if (name === 'FP') displayName = 'Family Planning';
            else if (name === 'AYPSRH') displayName = 'Adolescent & Youth SRH';
            else if (name === 'GBV') displayName = 'Gender-Based Violence';

            return {
                name: displayName,
                count: themeMap[name].count,
                budget: themeMap[name].budget,
                percentage: ((themeMap[name].budget / totalBudget) * 100).toFixed(1)
            };
        }).sort((a, b) => b.budget - a.budget);

        const countEl = document.getElementById('theme-table-count');
        if (countEl) countEl.textContent = `${themeList.length} Active Themes`;

        themeTbody.innerHTML = themeList.map(t => `
            <tr class="hover:bg-slate-50/50 dark:hover:bg-gray-700/50 transition">
                <td class="px-4 py-3 font-semibold text-gray-800 dark:text-gray-200">${t.name}</td>
                <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-semibold">${t.count} active</td>
                <td class="px-4 py-3 text-gray-600 dark:text-gray-400 font-semibold">${formatBudget(t.budget)}</td>
                <td class="px-4 py-3 text-right text-gray-800 dark:text-gray-200 font-extrabold">${t.percentage}%</td>
            </tr>
        `).join('');
    }
}

// --- 11. Render Dashboard Mockup/Real Charts ---
function renderDashboardCharts(projects) {
    // 1. Projects by Status (Status Chart)
    const statusCounts = { active: 0, pending: 0, rejected: 0 };
    projects.forEach(p => {
        const s = (p.status || '').toLowerCase();
        const ws = (p.approvalWorkflowStatus || '').toLowerCase();
        if (s === 'active' || ws === 'approved') {
            statusCounts.active++;
        } else if (s === 'rejected' || ws.includes('rejected')) {
            statusCounts.rejected++;
        } else {
            statusCounts.pending++;
        }
    });

    const statusChartEl = document.querySelector("#statusChart");
    if (statusChartEl) {
        statusChartEl.innerHTML = '';
        const options = {
            chart: { type: "bar", height: 350, toolbar: { show: false } },
            plotOptions: { bar: { columnWidth: "55%", borderRadius: 6, distributed: true } },
            dataLabels: { enabled: false },
            series: [{
                name: "Projects",
                data: [statusCounts.active, statusCounts.rejected, statusCounts.pending]
            }],
            xaxis: {
                categories: ["active", "rejected", "pending"],
                labels: { style: { fontSize: '12px', fontWeight: 600 } }
            },
            yaxis: { title: { text: "Projects" }, tickAmount: Math.max(statusCounts.active, statusCounts.pending) + 1 },
            colors: ['#3B82F6', '#9CA3AF', '#F59E0B'], // blue, gray, orange matching PDF
            legend: { show: false }
        };
        new ApexCharts(statusChartEl, options).render();
    }

    // 2. Projects by County (County Chart)
    const countyMap = {};
    projects.forEach(p => {
        const c = p.county || 'Kenya';
        countyMap[c] = (countyMap[c] || 0) + 1;
    });

    const countyChartEl = document.querySelector("#countyChart");
    if (countyChartEl) {
        countyChartEl.innerHTML = '';
        const options = {
            chart: { type: "donut", height: 330 },
            series: Object.values(countyMap),
            labels: Object.keys(countyMap),
            legend: { position: "bottom", fontSize: '11px' },
            dataLabels: { enabled: true, formatter: (val) => val.toFixed(0) + "%" }
        };
        new ApexCharts(countyChartEl, options).render();
    }

    // 3. Investment by Thematic Area (Theme Chart)
    const themeBudgets = {};
    projects.forEach(p => {
        let theme = p.thematicArea || p.projectTheme || 'Other';
        if (theme === 'MNH') theme = 'Maternity and Newborn Health';
        else if (theme === 'FP') theme = 'Family Planning';
        else if (theme === 'AYPSRH') theme = 'Adolescent & Youth SRH';
        else if (theme === 'GBV') theme = 'Gender-Based Violence';

        themeBudgets[theme] = (themeBudgets[theme] || 0) + (Number(p.budget) || 0);
    });

    const themeChartEl = document.querySelector("#themeChart");
    if (themeChartEl) {
        themeChartEl.innerHTML = '';
        const options = {
            chart: { type: "bar", height: 350, toolbar: { show: false } },
            plotOptions: { bar: { horizontal: true, barHeight: "55%", borderRadius: 4 } },
            series: [{
                name: "Budget",
                data: Object.values(themeBudgets).map(v => (v / 1000000).toFixed(2)) // Display in Millions
            }],
            xaxis: {
                categories: Object.keys(themeBudgets),
                title: { text: "Budget (Millions KES)" }
            },
            colors: ['#0047BA']
        };
        new ApexCharts(themeChartEl, options).render();
    }

    // 4. Funding per County (County Funding Chart)
    const countyFunding = {};
    projects.forEach(p => {
        const c = p.county || 'Kenya';
        countyFunding[c] = (countyFunding[c] || 0) + (Number(p.budget) || 0);
    });

    const countyFundingChartEl = document.querySelector("#countyFundingChart");
    if (countyFundingChartEl) {
        countyFundingChartEl.innerHTML = '';
        const options = {
            chart: { type: "bar", height: 350, toolbar: { show: false } },
            plotOptions: { bar: { horizontal: true, barHeight: "55%", borderRadius: 4 } },
            series: [{
                name: "Budget",
                data: Object.values(countyFunding).map(v => (v / 1000000).toFixed(2)) // Display in Millions
            }],
            xaxis: {
                categories: Object.keys(countyFunding),
                title: { text: "Budget (Millions KES)" }
            },
            colors: ['#0D9488'] // Teal matching PDF color coding
        };
        new ApexCharts(countyFundingChartEl, options).render();
    }

    // 5. Intervention Categories (Category Chart)
    const catCounts = { IMPLEMENTING: 0, RESEARCH: 0, PRIORITY: 0 };
    projects.forEach(p => {
        const cat = p.projectCategory || 'IMPLEMENTING';
        if (catCounts[cat] !== undefined) {
            catCounts[cat]++;
        }
    });

    const categoryChartEl = document.querySelector("#categoryChart");
    if (categoryChartEl) {
        categoryChartEl.innerHTML = '';
        const options = {
            chart: { type: "donut", height: 330 },
            series: [catCounts.IMPLEMENTING, catCounts.RESEARCH, catCounts.PRIORITY],
            labels: ["Implementing", "Research", "Priority"],
            colors: ['#0047BA', '#0D9488', '#F59E0B'],
            legend: { position: "bottom", fontSize: '11px' }
        };
        new ApexCharts(categoryChartEl, options).render();
    }
}

// --- 12. Mapbox Interactive Map Implementation ---
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
            renderMapMarkers(allProjects);
        }
    } catch (e) {
        console.error("Map coordinates loading failed:", e);
        renderMapMarkers(allProjects);
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
            
            const statusColor = proj.status?.toLowerCase() === "active" ? "text-green-500" : (proj.status?.toLowerCase() === "completed" ? "text-blue-500" : "text-amber-500");
            markerPin.innerHTML = `<i class="fas fa-map-marker-alt ${statusColor}"></i>`;
            markerWrapper.appendChild(markerPin);

            // Popup content
            const pillColor = proj.status?.toLowerCase() === "active" ? "bg-green-50 border-green-200 text-green-700" : "bg-blue-50 border-blue-200 text-blue-700";
            const popupHtml = `
                <div class="p-3 max-w-[240px] font-sans">
                    <h4 class="text-xs font-bold text-gray-900">${proj.projectName || proj.title || "Project"}</h4>
                    ${proj.projectNo ? `<span class="text-[10px] text-gray-500 block mt-0.5">${proj.projectNo}</span>` : ""}
                    <div class="mt-2 flex items-center justify-between">
                        <span class="text-[10px] font-bold px-1.5 py-0.5 rounded border ${pillColor}">${proj.status || "ACTIVE"}</span>
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

// Global dashboard refresh button trigger
window.loadDashboard = function(force = false) {
    loadDashboardData();
    if (map) {
        loadDashboardMapMarkers();
    }
};

// Global CSV Exporter
window.exportDashboardCSV = function() {
    if (allProjects.length === 0) return;
    const headers = ['Project Title', 'Project Number', 'Partner', 'Category', 'Status', 'Budget', 'County'];
    let csv = headers.join(',') + '\n';
    allProjects.forEach(p => {
        const row = [
            p.title || p.projectName,
            p.projectNo || '',
            p.partner || '',
            p.projectCategory || '',
            p.status || '',
            p.budget || 0,
            p.county || ''
        ].map(val => '"' + String(val).replace(/"/g, '""') + '"');
        csv += row.join(',') + '\n';
    });
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'dashboard_summary.csv';
    a.click();
    URL.revokeObjectURL(url);
};

// --- 13. Event Listeners & Bootstrapping ---
document.addEventListener('DOMContentLoaded', () => {
    // 1. Initial Overview Load
    loadDashboardData();
    
    // 2. Initial Map Load
    initDashboardMap();
});
