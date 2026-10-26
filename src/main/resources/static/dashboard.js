const RENDER_BASE = 'https://student-community-platform-ietscroll.onrender.com';
const BASE = (window.location.protocol === 'file:' || window.location.origin === 'null' || !window.location.origin)
    ? RENDER_BASE
    : (window.location.port === '4040' ? window.location.origin : RENDER_BASE);
const token = localStorage.getItem('iet_token');
if (!token) {
    window.location.replace('login.html');
    throw new Error('Not authenticated — redirecting to login.');
}

function toast(msg, type = 'success') {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.className = 'toast-msg show ' + type;
    setTimeout(() => el.classList.remove('show'), 3500);
}

// Central fetch wrapper. jsonBody adds Content-Type + JSON.stringify. formData is sent as-is (multipart).
async function api(path, { method = 'GET', jsonBody, formData, params } = {}) {
    let url = BASE + path;
    if (params) {
        const qs = new URLSearchParams(params).toString();
        url += (url.includes('?') ? '&' : '?') + qs;
    }
    const headers = { 'Authorization': 'Bearer ' + token };
    let body;
    if (jsonBody !== undefined) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(jsonBody);
    } else if (formData) {
        body = formData; // browser sets multipart boundary itself
    }

    let res;
    try {
        res = await fetch(url, { method, headers, body });
    } catch (networkErr) {
        throw new Error('Could not reach the server. Check your connection and try again.');
    }
	
    if (res.status === 401) {
        logout();
        throw new Error('Session expired');
    }
    if (!res.ok) {
        let msg = 'Request failed (' + res.status + ')';
        try {
            const errBody = await res.json();
            msg = errBody.message || errBody.error || msg;
        } catch {}
        throw new Error(msg);
    }
    const text = await res.text();
    return text ? JSON.parse(text) : null;
}

const PAGE_SIZE = 10;

function updatePaginationButtons(prevId, nextId, page, itemCount, res) {
    const prevBtn = document.getElementById(prevId);
    const nextBtn = document.getElementById(nextId);
    if (prevBtn) prevBtn.disabled = page <= 0;

    let isLastPage;
    if (res && typeof res.last === 'boolean') {
        isLastPage = res.last;
    } else if (res && typeof res.totalPages === 'number') {
        isLastPage = page >= res.totalPages - 1;
    } else {
        isLastPage = itemCount < PAGE_SIZE;
    }
    if (nextBtn) nextBtn.disabled = isLastPage;
}

function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    if (!sidebar) return;
    const isOpen = sidebar.classList.toggle('open');
    if (overlay) overlay.classList.toggle('active', isOpen);
}

function show(id, el) {
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    const targetSection = document.getElementById(id);
    if (targetSection) {
        targetSection.classList.add('active');
    }

    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    let targetLink = el;
    if (!targetLink) {
        targetLink = document.querySelector(`.nav-link[onclick*="'${id}'"]`);
    }
    if (targetLink) targetLink.classList.add('active');

    const titles = {
        dashboard: 'Overview',
        profile: 'Profile',
        notifications: 'Activity Log',
        lost: 'Lost Items',
        found: 'Found Items',
        team: 'Team Finder',
        resume: 'Resume Checker'
    };
    const titleEl = document.getElementById('header-title');
    if (titleEl) titleEl.innerText = titles[id] || (id.charAt(0).toUpperCase() + id.slice(1));

    // Close mobile sidebar and overlay
    const sidebar = document.getElementById('sidebar');
    if (sidebar) sidebar.classList.remove('open');
    const overlay = document.getElementById('sidebarOverlay');
    if (overlay) overlay.classList.remove('active');

    // Scroll to top
    window.scrollTo({ top: 0, behavior: 'smooth' });

    if (id === 'notifications') { markAllNotificationsRead(); renderNotifications(); }
    if (id === 'lost') loadLostBrowse();
    if (id === 'found') loadFoundBrowse();
    if (id === 'team') loadTeamBrowse();
}

function switchTab(section, tabId) {
    document.querySelectorAll('#' + section + ' .tab-pane').forEach(p => p.classList.add('d-none'));
    document.getElementById(tabId).classList.remove('d-none');
    document.querySelectorAll('#' + section + ' .tab-btn').forEach(b => b.classList.remove('active'));
    document.querySelector('#' + section + ' [data-tab="' + tabId + '"]').classList.add('active');

    if (tabId === 'lost-browse') loadLostBrowse();
    if (tabId === 'lost-mine') loadLostMine();
    if (tabId === 'found-browse') loadFoundBrowse();
    if (tabId === 'found-mine') loadFoundMine();
    if (tabId === 'team-browse') loadTeamBrowse();
    if (tabId === 'team-mine') loadTeamMine();
    if (tabId === 'team-requests') loadTeamRequests();
    if (tabId === 'team-applications') loadMyApplications();
}

/* ============ IMAGE VALIDATION ============ */
// Mirrors LostItemServiceImpl / FoundItemServiceImpl ALLOWED_TYPES exactly.
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const MAX_IMAGE_SIZE_MB = 8;

function validateImageFile(file) {
    if (!file) return 'Please choose an image.';
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
        return 'Unsupported file type. Use JPEG, PNG, WEBP, or GIF.';
    }
    if (file.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
        return `Image is too large. Max size is ${MAX_IMAGE_SIZE_MB}MB.`;
    }
    return null;
}

function wireImagePreview(inputId, previewId) {
    const input = document.getElementById(inputId);
    const preview = document.getElementById(previewId);
    if (!input || !preview) return;
    input.addEventListener('change', () => {
        const file = input.files[0];
        preview.innerHTML = '';
        if (!file) return;
        const err = validateImageFile(file);
        if (err) {
            preview.innerHTML = `<div class="text-danger small mt-2">${err}</div>`;
            input.value = '';
            return;
        }
        const url = URL.createObjectURL(file);
        preview.innerHTML = `<img src="${url}" class="img-preview mt-2" alt="Preview">`;
    });
}

/* ============ PROFILE / USER ============ */
async function loadProfile() {
    try {
        const user = await api('/api/v1/user');
		currentUserEmail = user.email || null;
        document.getElementById('welcome-name').innerText = user.fullName || user.username || 'Student';
        document.getElementById('top-username').innerText = user.fullName || user.username || '';
        document.getElementById('top-email').innerText = user.email || '';
        document.getElementById('p-name').innerText = user.fullName || '—';
        document.getElementById('p-username').innerText = user.username || '—';
        document.getElementById('p-email').innerText = user.email || '—';
        const initial = (user.fullName || user.username || '?').charAt(0).toUpperCase();
        document.getElementById('avatar-icon').innerText = initial;
        document.getElementById('avatar-icon-mobile').innerText = initial;
    } catch (e) {
        toast('Could not load profile: ' + e.message, 'error');
    }
}

async function updateUsername() {
    const val = document.getElementById('newUsernameInput').value.trim();
    if (!val) return toast('Enter a username first', 'error');
    try {
        await api('/api/v1/user/username/' + encodeURIComponent(val), { method: 'PATCH' });
        toast('Username updated');
        document.getElementById('newUsernameInput').value = '';
        loadProfile();
updateNotifBadges();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function updateFullname() {
    const val = document.getElementById('newFullnameInput').value.trim();
    if (!val) return toast('Enter a full name first', 'error');
    try {
        await api('/api/v1/user/fullname/' + encodeURIComponent(val), { method: 'PATCH' });
        toast('Full name updated');
        document.getElementById('newFullnameInput').value = '';
        loadProfile();
    } catch (e) {
        toast(e.message, 'error');
    }
}

/* ============ DASHBOARD STATS ============ */
async function loadDashboardStats() {
    try {
        const [myLost, myFound] = await Promise.all([
            api('/api/v1/lost-item/me'),
            api('/api/v1/found-item/me'),
        ]);
        document.getElementById('lostCount').innerText = (myLost || []).length;
        document.getElementById('foundCount').innerText = (myFound || []).length;
    } catch (e) {
        document.getElementById('lostCount').innerText = '–';
        document.getElementById('foundCount').innerText = '–';
    }
    try {
        const myTeam = await api('/api/v1/team/me');
        document.getElementById('teamStatus').innerText = myTeam ? 'Active Member' : 'No Active Team';
    } catch {
        document.getElementById('teamStatus').innerText = 'No Active Team';
    }
}

/* ============ SHARED ITEM CARD RENDERER ============ */
// Single source of truth — used by lost AND found (browse + mine) lists.
// emailKey lets each caller point at whichever field holds the contact email (both DTOs use "contactTo").
function renderItemCard(item, { nameKey, publicIdKey, imageKey, locationKey, extra, onClose, emailKey }) {
    const wrap = document.createElement('div');
    wrap.className = 'item-card';

    const imgHtml = item[imageKey]
        ? '<a href="' + item[imageKey] + '" target="_blank" rel="noopener noreferrer"><img src="' + item[imageKey] + '" alt="Item Photo"></a>'
        : '<div class="item-card-fallback-img"><i class="bi bi-box-seam"></i></div>';

    const locVal = (item[locationKey] && String(item[locationKey]).trim())
        ? String(item[locationKey]).trim()
        : 'Location not specified';

    const locationBadge = '<span class="meta-chip location"><i class="bi bi-geo-alt-fill text-danger me-1"></i>Location: ' + locVal + '</span>';

    const rewardBadge = (item.prize && item.prize > 0)
        ? '<span class="meta-chip reward"><i class="bi bi-gift-fill"></i>Reward: ₹' + item.prize + '</span>'
        : '';

    const emailBadge = (emailKey && item[emailKey])
        ? '<a href="mailto:' + item[emailKey] + '" class="meta-chip contact"><i class="bi bi-envelope-fill"></i>' + item[emailKey] + '</a>'
        : '';

    const closeBtn = onClose
        ? '<button class="btn-danger-soft ms-auto flex-shrink-0 close-card-btn"><i class="bi bi-x-circle"></i> Close</button>'
        : '';

    wrap.innerHTML = 
        imgHtml +
        '<div class="flex-grow-1">' +
            '<div class="item-title">' +
                '<span>' + (item[nameKey] || 'Untitled Item') + '</span>' +
                rewardBadge +
            '</div>' +
            '<div class="item-desc">' + (item.description || 'No description provided.') + '</div>' +
            '<div class="item-meta-row">' +
                locationBadge +
                emailBadge +
            '</div>' +
        '</div>' +
        closeBtn;

    if (onClose) {
        const btn = wrap.querySelector('.close-card-btn');
        if (btn) {
            btn.addEventListener('click', () => onClose(item[publicIdKey]));
        }
    }
    return wrap;
}

/* ============ LOST ITEMS ============ */
let lostPage = 0;

async function loadLostBrowse() {
    const container = document.getElementById('lostBrowseList');
    container.innerHTML = '<div class="spinner-inline"></div>';
    try {
        const res = await api('/api/v1/lost-item', { params: { page: lostPage, size: PAGE_SIZE } });
        const items = res.content || res.items || [];
        container.innerHTML = '';
        document.getElementById('lostPageLabel').innerText = 'Page ' + (lostPage + 1);
        updatePaginationButtons('lostPrevBtn', 'lostNextBtn', lostPage, items.length, res);
        if (!items.length) {
            container.innerHTML = `<div class="empty-state"><i class="bi bi-search"></i>No open lost item requests right now.</div>`;
            return;
        }
        items.forEach(item => {
            container.appendChild(renderItemCard(item, {
                nameKey: 'lostItemname', publicIdKey: 'publicIdOfLostRequest',
                imageKey: 'imageURLOfItem', locationKey: 'predictedLocation',
                emailKey: 'contactTo',
                extra: i => i.prize ? 'Reward: ' + i.prize : ''
            }));
        });
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Could not load lost items: ${e.message}</div>`;
    }
}
function changeLostPage(delta) {
    const next = lostPage + delta;
    if (next < 0) return;
    lostPage = next;
    loadLostBrowse();
}

async function loadLostMine() {
    const container = document.getElementById('lostMineList');
    container.innerHTML = '<div class="spinner-inline"></div>';
    try {
        const items = await api('/api/v1/lost-item/me');
        container.innerHTML = '';
        if (!items.length) {
            container.innerHTML = `<div class="empty-state"><i class="bi bi-inbox"></i>You haven't reported any lost items yet.</div>`;
            return;
        }
        items.forEach(item => {
            container.appendChild(renderItemCard(item, {
                nameKey: 'lostItemname', publicIdKey: 'publicIdOfLostRequest',
                imageKey: 'imageURLOfItem', locationKey: 'predictedLocation',
                emailKey: 'contactTo',
                extra: i => i.prize ? 'Reward: ' + i.prize : '',
                onClose: closeLostItem
            }));
        });
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">${e.message}</div>`;
    }
}

async function closeLostItem(publicId) {
    try {
        await api('/api/v1/lost-item/close', { method: 'PATCH', params: { lostItemId: publicId } });
        toast('Lost item request closed');
        loadLostMine();
        loadDashboardStats();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function submitLostItem() {
    const name = document.getElementById('lostItemName').value.trim();
    const desc = document.getElementById('lostItemDesc').value.trim();
    const location = document.getElementById('lostItemLocation').value.trim();
    const prizeRaw = document.getElementById('lostItemPrize').value.trim();
    const parsedPrize = parseInt(prizeRaw, 10);
    const prize = (!isNaN(parsedPrize) && parsedPrize >= 0) ? parsedPrize : 0;
    const imageFile = document.getElementById('lostItemImage').files[0];

        if (!name || !desc) {
        return toast('Item name and description are required', 'error');
    }
    if (name.length < 3) {
        return toast('Item name must be at least 3 characters', 'error');
    }
    if (desc.length < 20) {
        return toast('Please provide a detailed description (at least 20 characters) describing the item color, brand, condition, etc.', 'error');
    }
    const imgError = validateImageFile(imageFile);
    if (imgError) return toast(imgError, 'error');

    const data = { lostItemname: name, description: desc, predictedLocation: location, prize: prize };
    const fd = new FormData();
    fd.append('data', JSON.stringify(data));
    fd.append('image', imageFile);

    const btn = document.getElementById('lostSubmitBtn');
    const lostStatusEl = document.getElementById('lostSubmitStatus');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-inline me-2"></span>Analyzing…';
    if (lostStatusEl) { lostStatusEl.innerHTML = '<span class="spinner-inline me-2"></span>We are analyzing your information, once it\'s found correct and secure it will be uploaded...'; lostStatusEl.style.display = 'flex'; }
    toast("We are analyzing your information, once it's found correct and secure it will be uploaded...", 'info');
    try {
        await api('/api/v1/lost-item', { method: 'POST', formData: fd });
        toast('Lost item reported successfully');
        addNotification({
            type: 'lost',
            title: name,
            status: 'UPLOADED',
            message: 'Lost item report verified and uploaded successfully. Location: ' + (location || 'Campus / Unspecified'),
            time: new Date()
        });
        document.getElementById('lostItemName').value = '';
        document.getElementById('lostItemDesc').value = '';
        document.getElementById('lostItemLocation').value = '';
        document.getElementById('lostItemPrize').value = '';
        document.getElementById('lostItemImage').value = '';
        lostPage = 0;
        loadDashboardStats();
        switchTab('lost', 'lost-mine');
    } catch (e) {
        toast(e.message, 'error');
        addNotification({
            type: 'lost',
            title: name || 'Lost Item Report',
            status: 'REJECTED',
            message: e.message || 'Report was rejected during safety verification.',
            time: new Date()
        });
    } finally {
        if (lostStatusEl) lostStatusEl.style.display = 'none';
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-upload me-1"></i> Submit Report';
    }
}

/* ============ FOUND ITEMS ============ */
let foundPage = 0;

async function loadFoundBrowse() {
    const container = document.getElementById('foundBrowseList');
    container.innerHTML = '<div class="spinner-inline"></div>';
    try {
        const res = await api('/api/v1/found-item', { params: { page: foundPage, size: PAGE_SIZE } });
        const items = res.content || res.items || [];
        container.innerHTML = '';
        document.getElementById('foundPageLabel').innerText = 'Page ' + (foundPage + 1);
        updatePaginationButtons('foundPrevBtn', 'foundNextBtn', foundPage, items.length, res);
        if (!items.length) {
            container.innerHTML = `<div class="empty-state"><i class="bi bi-check-lg"></i>No pending found items right now.</div>`;
            return;
        }
        items.forEach(item => {
            container.appendChild(renderItemCard(item, {
                nameKey: 'foundItemName', publicIdKey: 'publicIdOfFoundItem',
                imageKey: 'imageURL', locationKey: 'predictedLocation',
                emailKey: 'contactTo'
            }));
        });
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">Could not load found items: ${e.message}</div>`;
    }
}
function changeFoundPage(delta) {
    const next = foundPage + delta;
    if (next < 0) return;
    foundPage = next;
    loadFoundBrowse();
}

async function loadFoundMine() {
    const container = document.getElementById('foundMineList');
    container.innerHTML = '<div class="spinner-inline"></div>';
    try {
        const items = await api('/api/v1/found-item/me');
        container.innerHTML = '';
        if (!items.length) {
            container.innerHTML = `<div class="empty-state"><i class="bi bi-inbox"></i>You haven't reported any found items yet.</div>`;
            return;
        }
        items.forEach(item => {
            container.appendChild(renderItemCard(item, {
                nameKey: 'foundItemName', publicIdKey: 'publicIdOfFoundItem',
                imageKey: 'imageURL', locationKey: 'predictedLocation',
                emailKey: 'contactTo',
                onClose: closeFoundItem
            }));
        });
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">${e.message}</div>`;
    }
}

async function closeFoundItem(publicId) {
    try {
        await api('/api/v1/found-item/close', { method: 'PATCH', params: { foundItemId: publicId } });
        toast('Found item request closed');
        loadFoundMine();
        loadDashboardStats();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function submitFoundItem() {
    const name = document.getElementById('foundItemName').value.trim();
    const desc = document.getElementById('foundItemDesc').value.trim();
    const location = document.getElementById('foundItemLocation').value.trim();
    const imageFile = document.getElementById('foundItemImage').files[0];

        if (!name || !desc) {
        return toast('Item name and description are required', 'error');
    }
    if (name.length < 3) {
        return toast('Item name must be at least 3 characters', 'error');
    }
    if (desc.length < 20) {
        return toast('Please provide a detailed description (at least 20 characters) describing the item color, brand, condition, etc.', 'error');
    }
    const imgError = validateImageFile(imageFile);
    if (imgError) return toast(imgError, 'error');

    const data = { foundItemname: name, description: desc, predictedLocation: location };
    const fd = new FormData();
    fd.append('data', JSON.stringify(data));
    fd.append('image', imageFile);

    const btn = document.getElementById('foundSubmitBtn');
    const foundStatusEl = document.getElementById('foundSubmitStatus');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-inline me-2"></span>Analyzing…';
    if (foundStatusEl) { foundStatusEl.innerHTML = '<span class="spinner-inline me-2"></span>We are analyzing your information, once it\'s found correct and secure it will be uploaded...'; foundStatusEl.style.display = 'flex'; }
    toast("We are analyzing your information, once it's found correct and secure it will be uploaded...", 'info');
    try {
        await api('/api/v1/found-item', { method: 'POST', formData: fd });
        toast('Found item reported successfully');
        addNotification({
            type: 'found',
            title: name,
            status: 'UPLOADED',
            message: 'Found item report verified and uploaded successfully. Location: ' + (location || 'Campus / Unspecified'),
            time: new Date()
        });
        document.getElementById('foundItemName').value = '';
        document.getElementById('foundItemDesc').value = '';
        document.getElementById('foundItemLocation').value = '';
        document.getElementById('foundItemImage').value = '';
        loadDashboardStats();
        switchTab('found', 'found-mine');
    } catch (e) {
        toast(e.message, 'error');
        addNotification({
            type: 'found',
            title: name || 'Found Item Report',
            status: 'REJECTED',
            message: e.message || 'Report was rejected during safety verification.',
            time: new Date()
        });
    } finally {
        if (foundStatusEl) foundStatusEl.style.display = 'none';
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-upload me-1"></i> Submit Report';
    }
}

/* ============ TEAM FINDER ============ */
let teamPage = 0;

async function loadTeamBrowse() {
	const container = document.getElementById('teamBrowseList');
	container.innerHTML = '<div class="spinner-inline"></div>';
	try {
		const res = await api('/api/v1/team', { params: { page: teamPage, size: PAGE_SIZE } });
		const teams = res.content || res.items || [];
		container.innerHTML = '';
		document.getElementById('teamPageLabel').innerText = 'Page ' + (teamPage + 1);
		updatePaginationButtons('teamPrevBtn', 'teamNextBtn', teamPage, teams.length, res);
		if (!teams.length) {
			container.innerHTML = `<div class="empty-state"><i class="bi bi-people"></i>No active teams looking for members right now.</div>`;
			return;
		}
		teams.forEach(t => {
			const isOwnTeam = currentUserEmail && t.createdBy === currentUserEmail;
			const wrap = document.createElement('div');
			wrap.className = 'item-card';
			wrap.innerHTML = `
				<div class="flex-grow-1">
					<div class="item-title">${t.purpose || 'Team'}</div>
					<div class="item-meta">Owner: ${t.createdBy || '—'} · Members: ${t.currentMember ?? '—'}/${t.maxMember ?? '—'}</div>
				</div>
				${isOwnTeam
					? `<span class="badge-status badge-accepted">Your Team</span>`
					: `<button class="btn-outline-accent">Request to Join</button>`}
			`;
			if (!isOwnTeam) {
				wrap.querySelector('button').addEventListener('click', () => promptJoinRequest(t.publicId));
			}
			container.appendChild(wrap);
		});
	} catch (e) {
		container.innerHTML = `<div class="empty-state text-danger">Could not load teams: ${e.message}</div>`;
	}
}
function changeTeamPage(delta) {
    const next = teamPage + delta;
    if (next < 0) return;
    teamPage = next;
    loadTeamBrowse();
}

async function loadTeamMine() {
    const container = document.getElementById('teamMineCard');
    container.innerHTML = '<div class="spinner-inline"></div>';
    try {
        const t = await api('/api/v1/team/me');
        if (!t) {
            container.innerHTML = `<div class="empty-state"><i class="bi bi-people"></i>You don't have an active team. Create one from the "Create Team" tab.</div>`;
            return;
        }
        container.innerHTML = `
			<div class="stat-card">
				<h6 class="fw-bold mb-2">${t.purpose || 'Your Team'}</h6>
				<div class="item-meta mb-3">Status: ${t.status || '—'} · Members: ${t.currentMember ?? '—'}/${t.maxMember ?? '—'}</div>
				<div class="d-flex gap-2">
					<button class="btn-danger-soft" id="closeTeamBtn">Close Team</button>
				</div>
				<div class="row g-3 mt-3">
					<div class="col-md-6">
						<label class="field-label">Update Max Team Size</label>
						<input type="number" class="form-control-dark" id="updateTeamSizeInput" min="3" value="${t.maxMember || 4}">
					</div>
					<div class="col-md-6 d-flex align-items-end">
						<button class="btn-accent" id="updateTeamSizeBtn">Update Size</button>
					</div>
				</div>
			</div>
		`;
        document.getElementById('closeTeamBtn').addEventListener('click', closeTeam);
        document.getElementById('updateTeamSizeBtn').addEventListener('click', updateTeamSize);
    } catch (e) {
        container.innerHTML = `<div class="empty-state"><i class="bi bi-people"></i>You don't have an active team yet.</div>`;
    }
}

async function createTeam() {
    const purpose = document.getElementById('teamPurpose').value.trim();
    const size = document.getElementById('teamSize').value;
    const privacy = document.getElementById('teamPrivacy').value;
    const sizeNum = parseInt(size, 10);
    if (purpose.length < 10 || purpose.length > 300) return toast('Purpose must be 10–300 characters', 'error');
    if (isNaN(sizeNum) || sizeNum < 3) return toast('Enter a valid team size (min. 3)', 'error');

    const btn = document.getElementById('teamCreateBtn');
    const teamStatusEl = document.getElementById('teamCreateStatus');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-inline me-2"></span>Analyzing…';
    if (teamStatusEl) { teamStatusEl.innerHTML = '<span class="spinner-inline me-2"></span>We are analyzing your information, once it\'s found correct and secure it will be uploaded...'; teamStatusEl.style.display = 'flex'; }
    toast("We are analyzing your information, once it's found correct and secure it will be uploaded...", 'info');
    try {
        await api('/api/v1/team', { method: 'POST', jsonBody: { purpose: purpose, teamSize: sizeNum, privacy: privacy } });
        toast('Team created');
        document.getElementById('teamPurpose').value = '';
        loadDashboardStats();
        switchTab('team', 'team-mine');
    } catch (e) {
        toast(e.message, 'error');
        addNotification({
            type: 'team',
            title: 'Team: ' + purpose.slice(0, 35) + '...',
            status: 'REJECTED',
            message: e.message || 'Team creation rejected by campus moderation.',
            time: new Date()
        });
    } finally {
        if (teamStatusEl) teamStatusEl.style.display = 'none';
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-people-fill me-1"></i> Create Team';
    }
}

async function closeTeam() {
    try {
        await api('/api/v1/team/close', { method: 'PATCH' });
        toast('Team closed');
        loadTeamMine();
        loadDashboardStats();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function updateTeamSize() {
    const size = document.getElementById('updateTeamSizeInput').value;
    const sizeNum = parseInt(size, 10);
    if (isNaN(sizeNum) || sizeNum < 3) return toast('Enter a valid team size (min. 3)', 'error');
    try {
        await api('/api/v1/team/team-size', { method: 'PATCH', params: { teamSize: sizeNum } });
        toast('Team size updated');
        loadTeamMine();
    } catch (e) {
        toast(e.message, 'error');
    }
}

function promptJoinRequest(teamPublicId) {
    const message = window.prompt('Add a short message to your join request:');
    if (message === null) return;
    submitJoinRequest(teamPublicId, message);
}

async function submitJoinRequest(teamPublicId, message) {
    try {
        await api('/api/v1/request-team', { method: 'POST', jsonBody: { teamId: teamPublicId, message: message } });
        toast('Join request sent');
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function loadTeamRequests() {
    const reqContainer = document.getElementById('teamRequestsList');
    const memContainer = document.getElementById('teamMembersList');
    reqContainer.innerHTML = '<div class="spinner-inline"></div>';
    memContainer.innerHTML = '';
    // Note: /requests always returns WAIT-status requests server-side (hardcoded), no status param needed.
    try {
        const requests = await api('/api/v1/request-team/requests');
        reqContainer.innerHTML = '';
        if (!requests || !requests.length) {
            reqContainer.innerHTML = `<div class="empty-state"><i class="bi bi-inbox"></i>No pending join requests.</div>`;
        } else {
            requests.forEach(r => {
                const wrap = document.createElement('div');
                wrap.className = 'item-card';
                wrap.innerHTML = `
					<div class="flex-grow-1">
						<div class="item-title">${r.applicantFullName || r.applicantUsername || r.applicantEmail}</div>
						<div class="item-meta mb-1">${r.applicantEmail}</div>
						<div class="item-desc">${r.message || ''}</div>
					</div>
					<div class="d-flex gap-2">
						<button class="btn-success-soft">Accept</button>
						<button class="btn-danger-soft">Reject</button>
					</div>
				`;
                const [acceptBtn, rejectBtn] = wrap.querySelectorAll('button');
                acceptBtn.addEventListener('click', () => respondToRequest(r.applicantEmail, 'accept'));
                rejectBtn.addEventListener('click', () => respondToRequest(r.applicantEmail, 'reject'));
                reqContainer.appendChild(wrap);
            });
        }
    } catch (e) {
        reqContainer.innerHTML = `<div class="empty-state text-danger">${e.message}</div>`;
    }

    // /team-members always returns ACCEPTED-status entries server-side.
    try {
        const members = await api('/api/v1/request-team/team-members');
        memContainer.innerHTML = '';
        if (!members || !members.length) {
            memContainer.innerHTML = `<div class="empty-state"><i class="bi bi-people"></i>No accepted members yet.</div>`;
        } else {
            members.forEach(m => {
                const wrap = document.createElement('div');
                wrap.className = 'item-card';
                wrap.innerHTML = `
					<div class="flex-grow-1">
						<div class="item-title">${m.applicantFullName || m.applicantUsername || m.applicantEmail}</div>
						<div class="item-meta">${m.applicantEmail}</div>
					</div>
					<button class="btn-danger-soft">Remove</button>
				`;
                wrap.querySelector('button').addEventListener('click', () => removeMember(m.applicantEmail));
                memContainer.appendChild(wrap);
            });
        }
    } catch (e) {
        memContainer.innerHTML = `<div class="empty-state text-danger">${e.message}</div>`;
    }
}

async function respondToRequest(email, action) {
    try {
        await api(`/api/v1/request-team/${action}/${encodeURIComponent(email)}`, { method: 'PATCH' });
        toast(action === 'accept' ? 'Request accepted' : 'Request rejected');
        loadTeamRequests();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function removeMember(email) {
    try {
        await api(`/api/v1/request-team/remove/${encodeURIComponent(email)}`, { method: 'PATCH' });
        toast('Member removed');
        loadTeamRequests();
    } catch (e) {
        toast(e.message, 'error');
    }
}

async function loadMyApplications() {
    const container = document.getElementById('teamApplicationsList');
    container.innerHTML = '<div class="spinner-inline"></div>';
    try {
        const apps = await api('/api/v1/request-team/my-application');
        container.innerHTML = '';
        if (!apps || !apps.length) {
            container.innerHTML = `<div class="empty-state"><i class="bi bi-send"></i>You haven't applied to any teams yet.</div>`;
            return;
        }
        apps.forEach(a => {
            // TeamRequestStatus enum values: WAIT, ACCEPTED, REJECTED
            const status = (a.status || 'WAIT').toLowerCase();
            const badgeClass = status === 'accepted' ? 'badge-accepted' : status === 'rejected' ? 'badge-rejected' : 'badge-pending';
            const label = status === 'wait' ? 'pending' : status;
            const wrap = document.createElement('div');
            wrap.className = 'item-card';
            wrap.innerHTML = `
				<div class="flex-grow-1">
					<div class="item-title">Team ${a.teamId ? a.teamId.slice(0, 8) : ''}</div>
					<div class="item-desc">${a.yourMessage || ''}</div>
					<div class="item-meta">${a.requestedAt ? new Date(a.requestedAt).toLocaleDateString() : ''}</div>
				</div>
				<span class="badge-status ${badgeClass}">${label}</span>
			`;
            container.appendChild(wrap);
        });
    } catch (e) {
        container.innerHTML = `<div class="empty-state text-danger">${e.message}</div>`;
    }
}

/* ============ RESUME CHECKER ============ */
async function submitResume() {
    const file = document.getElementById('resumeFile').files[0];
    const role = document.getElementById('resumeRole').value.trim();
    const experience = document.getElementById('resumeExperience').value;
    if (!file) return toast('Choose a PDF or DOCX file first', 'error');
    if (!role) return toast('Enter a target role first', 'error');
    const expNum = parseInt(experience, 10);
    if (isNaN(expNum) || expNum < 0) return toast('Enter a valid number of years', 'error');

    // Controller expects file, exp, and role as multipart form parts (all @RequestPart) — not query params.
    const fd = new FormData();
    fd.append('file', file);
    fd.append('exp', expNum);
    fd.append('role', role);

    const btn = document.getElementById('resumeSubmitBtn');
    const resultBox = document.getElementById('resumeResult');
    btn.disabled = true;
    btn.innerText = 'Analyzing…';
    resultBox.innerHTML = '';
    try {
        const result = await api('/api/v1/ietscroll/resume/quality', { method: 'POST', formData: fd });
        const skillsMatched = (result.topFiveSkillsMatched || []).map(s => `<span class="badge-status badge-accepted me-1 mb-1 d-inline-block">${s}</span>`).join('');
        const keywordsMissed = (result.topTenKeywordsMissed || []).map(s => `<span class="badge-status badge-pending me-1 mb-1 d-inline-block">${s}</span>`).join('');
        resultBox.innerHTML = `
			<div class="row g-3">
				<div class="col-md-4">
					<div class="stat-card">
						<div class="info-label mb-2">Overall Rating</div>
						<div class="fs-2 fw-bold">${result.overAllRating ?? '—'} <span class="text-muted fs-6">/10</span></div>
					</div>
				</div>
				<div class="col-md-4">
					<div class="stat-card">
						<div class="info-label mb-2">Content Quality</div>
						<div class="fs-2 fw-bold">${result.rateContentQuality ?? '—'} <span class="text-muted fs-6">/10</span></div>
					</div>
				</div>
				<div class="col-md-4">
					<div class="stat-card">
						<div class="info-label mb-2">Projects Rating</div>
						<div class="fs-2 fw-bold">${result.rateProjects ?? '—'} <span class="text-muted fs-6">/10</span></div>
					</div>
				</div>
				<div class="col-12">
					<div class="stat-card">
						<div class="info-label mb-2">Skills Matched</div>
						<div class="mb-3">${skillsMatched || '<span class="text-muted small">None detected</span>'}</div>
						<div class="info-label mb-2">Keywords Missed</div>
						<div class="mb-3">${keywordsMissed || '<span class="text-muted small">None</span>'}</div>
						<div class="info-label mb-2">Improvement Suggestions</div>
						<p class="text-muted small mb-3">${result.improvement || '—'}</p>
						<div class="info-label mb-2">Suggested Unique Project</div>
						<p class="text-muted small">${result.suggestedUnqiueProject || '—'}</p>
					</div>
				</div>
			</div>
		`;
        toast('Resume analyzed');
    } catch (e) {
        toast(e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Analyze Resume';
    }
}

/* ============ NOTIFICATIONS & ACTIVITY AUDIT ============ */
function getNotifKey() {
    return 'iet_notifs_' + (currentUserEmail || 'global');
}

function getNotifications() {
    try {
        const raw = localStorage.getItem(getNotifKey());
        return raw ? JSON.parse(raw) : [];
    } catch {
        return [];
    }
}

function saveNotifications(list) {
    try {
        localStorage.setItem(getNotifKey(), JSON.stringify(list));
    } catch {}
    updateNotifBadges();
}

function addNotification({ type, title, status, message, time }) {
    const list = getNotifications();
    const entry = {
        id: 'notif_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4),
        type, // 'lost', 'found', 'team'
        title: title || 'Submission',
        status, // 'UPLOADED' or 'REJECTED'
        message: message || '',
        time: time ? new Date(time).toISOString() : new Date().toISOString(),
        read: false
    };
    list.unshift(entry);
    if (list.length > 50) list.pop();
    saveNotifications(list);
    renderNotifications();
}

function formatNotifTime(isoString) {
    try {
        const d = new Date(isoString);
        return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) + 
               ' at ' + d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    } catch {
        return isoString || 'Recently';
    }
}

function renderNotifications() {
    const container = document.getElementById('notificationsList');
    if (!container) return;
    const list = getNotifications();
    if (!list.length) {
        container.innerHTML = `
            <div class="empty-state">
                <i class="bi bi-bell-slash"></i>
                <div class="fw-bold fs-5 text-white mb-1">No Activity Logged Yet</div>
                <div class="text-muted small">Your report upload history, safety approvals, and rejection notices will appear here with timestamps.</div>
            </div>`;
        return;
    }

    container.innerHTML = '';
    list.forEach(n => {
        const isSuccess = n.status === 'UPLOADED' || n.status === 'SUCCESS';
        const card = document.createElement('div');
        card.className = 'notif-card ' + (isSuccess ? 'success' : 'rejected');

        let iconClass = 'bi-file-earmark-text';
        if (n.type === 'lost') iconClass = 'bi-search-heart';
        if (n.type === 'found') iconClass = 'bi-box-seam';
        if (n.type === 'team') iconClass = 'bi-people';

        const statusBadge = isSuccess
            ? '<span class="badge-status open"><i class="bi bi-check-circle-fill me-1"></i> UPLOADED</span>'
            : '<span class="badge-status closed"><i class="bi bi-x-circle-fill me-1"></i> REJECTED</span>';

        card.innerHTML = `
            <div class="notif-icon-box ${isSuccess ? 'success' : 'rejected'}">
                <i class="bi ${iconClass}"></i>
            </div>
            <div class="flex-grow-1">
                <div class="notif-title">
                    <span>${n.title}</span>
                    ${statusBadge}
                </div>
                <div class="notif-time">
                    <i class="bi bi-clock"></i> ${formatNotifTime(n.time)}
                </div>
                <div class="notif-desc ${isSuccess ? 'success' : 'rejected'}">
                    <strong>${isSuccess ? 'Status:' : 'Rejection Reason:'}</strong> ${n.message}
                </div>
            </div>
        `;
        container.appendChild(card);
    });
}

function updateNotifBadges() {
    const list = getNotifications();
    const unread = list.filter(n => !n.read).length;

    const b1 = document.getElementById('notifBadge');
    const b2 = document.getElementById('notifBadgeMobile');
    const b3 = document.getElementById('notifNavBadge');

    [b1, b2, b3].forEach(el => {
        if (!el) return;
        if (unread > 0) {
            el.innerText = unread;
            el.classList.remove('d-none');
        } else {
            el.classList.add('d-none');
        }
    });
}

function clearAllNotifications() {
    if (!confirm('Clear your entire upload activity history?')) return;
    saveNotifications([]);
    renderNotifications();
    toast('Activity history cleared', 'info');
}

function markAllNotificationsRead() {
    const list = getNotifications();
    list.forEach(n => n.read = true);
    saveNotifications(list);
}

function logout() {
	localStorage.removeItem('iet_token');
	window.location.replace('login.html');
}

loadProfile();
loadDashboardStats();
wireImagePreview('lostItemImage', 'lostItemImagePreview');
wireImagePreview('foundItemImage', 'foundItemImagePreview');