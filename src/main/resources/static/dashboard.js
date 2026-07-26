const BASE = 'https://student-community-platform-ietscroll.onrender.com';
// const BASE = 'http://localhost:4040';

const token = localStorage.getItem('iet_token');
if (!token) {
    window.location.replace('login.html');
    throw new Error('Not authenticated — redirecting to login.');
}

localStorage.removeItem('iet_token');
window.location.replace('login.html');


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
    document.getElementById('sidebar').classList.toggle('open');
}

function show(id, el) {
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    if (el) el.classList.add('active');
    document.getElementById('header-title').innerText = id.charAt(0).toUpperCase() + id.slice(1);
    if (window.innerWidth < 991) document.getElementById('sidebar').classList.remove('open');

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
    const img = item[imageKey]
        ? `<a href="${item[imageKey]}" target="_blank" rel="noopener noreferrer">
			<img src="${item[imageKey]}" alt="" style="cursor: pointer;">
		   </a>`
        : `<div class="avatar" style="width:70px;height:70px;flex-shrink:0;"><i class="bi bi-image"></i></div>`;
    const emailLine = emailKey && item[emailKey]
        ? `<div class="item-meta mt-1">Contact: ${item[emailKey]}</div>`
        : '';
    wrap.innerHTML = `
		${img}
		<div class="flex-grow-1">
			<div class="item-title">${item[nameKey] || 'Untitled'}</div>
			<div class="item-desc">${item.description || ''}</div>
			<div class="item-meta">${item[locationKey] || ''} ${extra ? '· ' + extra(item) : ''}</div>
			${emailLine}
		</div>
		${onClose ? `<button class="btn-danger-soft">Close</button>` : ''}
	`;
    if (onClose) {
        wrap.querySelector('button').addEventListener('click', () => onClose(item[publicIdKey]));
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
    const prize = document.getElementById('lostItemPrize').value.trim();
    const imageFile = document.getElementById('lostItemImage').files[0];

    if (!name || !desc) {
        return toast('Item name and description are required', 'error');
    }
    const imgError = validateImageFile(imageFile);
    if (imgError) return toast(imgError, 'error');

    const data = { lostItemname: name, description: desc, predictedLocation: location, prize: prize };
    const fd = new FormData();
    fd.append('data', JSON.stringify(data));
    fd.append('image', imageFile);

    const btn = document.getElementById('lostSubmitBtn');
    btn.disabled = true;
    btn.innerText = 'Submitting…';
    try {
        await api('/api/v1/lost-item', { method: 'POST', formData: fd });
        toast('Lost item reported successfully');
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
    } finally {
        btn.disabled = false;
        btn.innerText = 'Submit Report';
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
    const imgError = validateImageFile(imageFile);
    if (imgError) return toast(imgError, 'error');

    const data = { foundItemname: name, description: desc, predictedLocation: location };
    const fd = new FormData();
    fd.append('data', JSON.stringify(data));
    fd.append('image', imageFile);

    const btn = document.getElementById('foundSubmitBtn');
    btn.disabled = true;
    btn.innerText = 'Submitting…';
    try {
        await api('/api/v1/found-item', { method: 'POST', formData: fd });
        toast('Found item reported successfully');
        document.getElementById('foundItemName').value = '';
        document.getElementById('foundItemDesc').value = '';
        document.getElementById('foundItemLocation').value = '';
        document.getElementById('foundItemImage').value = '';
        loadDashboardStats();
        switchTab('found', 'found-mine');
    } catch (e) {
        toast(e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Submit Report';
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
    btn.disabled = true;
    btn.innerText = 'Creating…';
    try {
        await api('/api/v1/team', { method: 'POST', jsonBody: { purpose: purpose, teamSize: sizeNum, privacy: privacy } });
        toast('Team created');
        document.getElementById('teamPurpose').value = '';
        loadDashboardStats();
        switchTab('team', 'team-mine');
    } catch (e) {
        toast(e.message, 'error');
    } finally {
        btn.disabled = false;
        btn.innerText = 'Create Team';
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
function logout() {
	localStorage.removeItem('iet_token');
	window.location.replace('login.html');
}

loadProfile();
loadDashboardStats();
wireImagePreview('lostItemImage', 'lostItemImagePreview');
wireImagePreview('foundItemImage', 'foundItemImagePreview');