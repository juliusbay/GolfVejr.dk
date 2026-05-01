const API = '/api';

const views = {
  selector: document.getElementById('view-selector'),
  forecast: document.getElementById('view-forecast'),
  loading:  document.getElementById('view-loading'),
  error:    document.getElementById('view-error'),
};

function showView(name) {
  Object.values(views).forEach(v => v.classList.add('hidden'));
  views[name].classList.remove('hidden');
}

// ── Status helpers ──────────────────────────────────────────────────────────

function statusCls(s)  { return { GREEN: 'good', YELLOW: 'warn', RED: 'bad' }[s] || 'bad'; }
function statusIcon(s) { return { GREEN: '✓', YELLOW: '~', RED: '✕' }[s] || ''; }
function statusText(s) { return { GREEN: 'Ideelt', YELLOW: 'Spilbart', RED: 'Frarådes' }[s] || s; }

function renderBadge(status, large) {
  return `<span class="status-badge ${statusCls(status)}${large ? ' large' : ''}">${statusIcon(status)} ${statusText(status)}</span>`;
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ── Tooltip ─────────────────────────────────────────────────────────────────

const tipEl = document.createElement('div');
tipEl.id = 'tip';
document.body.appendChild(tipEl);

document.addEventListener('mouseover', e => {
  const target = e.target.closest('[data-tip]');
  if (!target) return;
  tipEl.innerHTML = target.dataset.tip.split('\n').map(l => escapeHtml(l)).join('<br>');
  tipEl.style.display = 'block';
});

document.addEventListener('mouseout', e => {
  if (!e.target.closest('[data-tip]')) return;
  const related = e.relatedTarget;
  if (!related || !related.closest('[data-tip]')) tipEl.style.display = 'none';
});

document.addEventListener('mousemove', e => {
  if (tipEl.style.display === 'none') return;
  const x = e.clientX + 14;
  const y = e.clientY + 16;
  tipEl.style.left = Math.min(x, window.innerWidth  - 260) + 'px';
  tipEl.style.top  = Math.min(y, window.innerHeight - 120) + 'px';
});

// ── Score arc (SVG gauge) ────────────────────────────────────────────────────

function scoreColor(cls) {
  return { good: '#16a34a', warn: '#d97706', bad: '#dc2626' }[cls] || '#16a34a';
}

function renderScoreRing(score, status, tooltip) {
  const c     = statusCls(status);
  const color = scoreColor(c);
  const pct   = Math.min(Math.max(score, 0), 100);
  const r     = 46, cx = 56, cy = 56;
  const circ  = 2 * Math.PI * r;
  const arc   = circ * 0.75;
  const fill  = arc * pct / 100;
  const rot   = `rotate(-225 ${cx} ${cy})`;
  return `
    <div class="score-ring-wrap">
      <svg class="score-arc" viewBox="0 0 112 112" width="96" height="96" aria-hidden="true">
        <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#e5e7eb" stroke-width="9"
          stroke-dasharray="${arc.toFixed(2)} ${(circ - arc).toFixed(2)}"
          stroke-linecap="round" transform="${rot}"/>
        <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${color}" stroke-width="9"
          stroke-dasharray="${fill.toFixed(2)} ${(circ - fill).toFixed(2)}"
          stroke-linecap="round" transform="${rot}"/>
        <text x="${cx}" y="${cy - 5}" text-anchor="middle" dominant-baseline="middle"
          font-family="'DM Mono',monospace" font-size="22" font-weight="700" fill="${color}">${score}</text>
        <text x="${cx}" y="${cy + 13}" text-anchor="middle"
          font-family="'DM Sans',sans-serif" font-size="10" fill="#9ca3af">/100</text>
      </svg>
      <div class="score-ring-label" data-tip="${escapeHtml(tooltip)}">Golfscore ⓘ</div>
    </div>`;
}

// ── Factor tags ──────────────────────────────────────────────────────────────

function buildTooltip(good, bad) {
  const lines = [
    ...(good || []).map(f => '✓ ' + f),
    ...(bad  || []).map(f => '⚠ ' + f),
  ];
  return lines.join('\n') || 'Ingen faktorer';
}

function renderFactors(good, bad) {
  const tags = [
    ...(good || []).map(f => `<span class="factor-tag good">&#9989; ${escapeHtml(f)}</span>`),
    ...(bad  || []).map(f => `<span class="factor-tag bad">&#9888; ${escapeHtml(f)}</span>`),
  ];
  if (!tags.length) return '<div class="factor-tags"><span class="factor-tag good">&#9989; Perfekte golfforhold</span></div>';
  return `<div class="factor-tags">${tags.join('')}</div>`;
}

// ── Favourite clubs (localStorage) ───────────────────────────────────────────

const FAV_KEY = 'favoriteClubs';

// Migrate old single-favourite key to the new array format
(function migrateFavorites() {
  const old = localStorage.getItem('favoriteClub');
  if (!old) return;
  try {
    const parsed = JSON.parse(old);
    if (parsed && parsed.clubId) {
      const existing = getFavorites();
      if (!existing.some(f => f.id === parsed.clubId)) {
        existing.push({ id: parsed.clubId, name: parsed.clubName });
        localStorage.setItem(FAV_KEY, JSON.stringify(existing));
      }
    }
  } catch {}
  localStorage.removeItem('favoriteClub');
})();

function getFavorites() {
  try { return JSON.parse(localStorage.getItem(FAV_KEY)) || []; } catch { return []; }
}

function isFavorite(id) {
  return getFavorites().some(f => f.id === String(id));
}

function toggleFavorite(id, name) {
  const favs = getFavorites();
  const idx  = favs.findIndex(f => f.id === String(id));
  if (idx >= 0) favs.splice(idx, 1); else favs.push({ id: String(id), name });
  localStorage.setItem(FAV_KEY, JSON.stringify(favs));
}

function updateStarButton(clubId) {
  const btn    = document.getElementById('btn-favorite');
  const active = isFavorite(clubId);
  btn.textContent = active ? '★' : '☆';
  btn.classList.toggle('active', active);
  btn.title = active ? 'Fjern fra favoritter' : 'Gem som favorit';
}

function renderFavoritesPanel() {
  const container = document.getElementById('favorites-list-container');
  const favs = getFavorites();
  if (!favs.length) {
    container.innerHTML = '';
    return;
  }
  container.innerHTML = `
    <p class="favorites-header">Dine favoritter:</p>
    <div class="favorites-list">
      ${favs.map(f => `
        <button class="fav-club-btn" data-id="${escapeHtml(f.id)}" data-name="${escapeHtml(f.name)}">
          <span class="fav-club-name">${escapeHtml(f.name)}</span>
          <span class="fav-club-star" data-id="${escapeHtml(f.id)}" data-name="${escapeHtml(f.name)}">&#9733;</span>
        </button>`).join('')}
    </div>`;

  container.querySelectorAll('.fav-club-btn').forEach(btn => {
    btn.addEventListener('click', e => {
      const star = e.target.closest('.fav-club-star');
      if (star) {
        toggleFavorite(star.dataset.id, star.dataset.name);
        renderFavoritesPanel();
        const dropdown = document.getElementById('club-dropdown');
        if (!dropdown.classList.contains('hidden')) {
          renderDropdown(document.getElementById('club-search').value, dropdown, document.getElementById('btn-go'));
        }
        return;
      }
      loadForecast(btn.dataset.id, btn.dataset.name);
    });
  });
}

// ── Club selector ────────────────────────────────────────────────────────────

let allClubs = [];
let selectedClubId   = null;
let selectedClubName = null;
let currentClubId    = null;
let currentClubName  = null;

async function loadClubs() {
  const input    = document.getElementById('club-search');
  const dropdown = document.getElementById('club-dropdown');
  const btn      = document.getElementById('btn-go');

  try {
    allClubs = await fetch(`${API}/golfclubs`).then(r => {
      if (!r.ok) throw new Error('Server fejl');
      return r.json();
    });

    input.addEventListener('focus', () => renderDropdown(input.value, dropdown, btn));
    input.addEventListener('input', () => {
      if (selectedClubId && input.value !== selectedClubName) {
        selectedClubId = null;
        selectedClubName = null;
        btn.disabled = true;
      }
      renderDropdown(input.value, dropdown, btn);
    });
    document.addEventListener('click', e => {
      if (!input.contains(e.target) && !dropdown.contains(e.target))
        dropdown.classList.add('hidden');
    });
    btn.addEventListener('click', () => {
      if (selectedClubId) loadForecast(selectedClubId, selectedClubName);
    });
  } catch {
    showError('Kunne ikke hente golfklubber. Tjek at serveren kører.');
  }
}

function renderDropdown(query, dropdown, btn) {
  const q = query.trim().toLowerCase();
  const matches = q ? allClubs.filter(c => c.name.toLowerCase().includes(q)) : allClubs;

  if (!matches.length) {
    dropdown.innerHTML = '<div class="dropdown-empty">Ingen klubber fundet</div>';
    dropdown.classList.remove('hidden');
    return;
  }

  dropdown.innerHTML = matches.map(c => {
    const isFav = isFavorite(c.id);
    const name  = escapeHtml(c.name);
    return `<div class="dropdown-item" data-id="${c.id}" data-name="${name}">
      <span class="dropdown-item-name">${name}</span>
      <button class="dropdown-star${isFav ? ' active' : ''}" data-id="${c.id}" data-name="${name}" tabindex="-1">${isFav ? '&#9733;' : '&#9734;'}</button>
    </div>`;
  }).join('');

  dropdown.querySelectorAll('.dropdown-item').forEach(item => {
    item.addEventListener('mousedown', e => {
      if (e.target.closest('.dropdown-star')) return;
      e.preventDefault();
      selectedClubId   = item.dataset.id;
      selectedClubName = item.dataset.name;
      document.getElementById('club-search').value = selectedClubName;
      dropdown.classList.add('hidden');
      btn.disabled = false;
    });
  });

  dropdown.querySelectorAll('.dropdown-star').forEach(star => {
    star.addEventListener('mousedown', e => {
      e.preventDefault();
      e.stopPropagation();
      const id   = star.dataset.id;
      const name = star.dataset.name;
      toggleFavorite(id, name);
      renderDropdown(document.getElementById('club-search').value, dropdown, btn);
      renderFavoritesPanel();
    });
  });

  dropdown.classList.remove('hidden');
}

// ── Forecast loader ──────────────────────────────────────────────────────────

async function loadForecast(clubId, clubName) {
  currentClubId   = String(clubId);
  currentClubName = clubName;
  showView('loading');
  try {
    const [forecast, bestDays] = await Promise.all([
      fetch(`${API}/forecast/${clubId}`).then(r => {
        if (!r.ok) throw new Error('Forecast fejl');
        return r.json();
      }),
      fetch(`${API}/forecast/${clubId}/best-days`).then(r => {
        if (!r.ok) throw new Error('Best-days fejl');
        return r.json();
      }),
    ]);

    document.getElementById('club-name-display').textContent = clubName;
    renderTodayCard(forecast[0]);
    renderBestDays(bestDays);
    renderDailySections(forecast);
    updateStarButton(clubId);
    showView('forecast');
  } catch {
    showError('Kunne ikke hente vejrdata. Prøv igen om lidt.');
  }
}

// ── Today card ───────────────────────────────────────────────────────────────

function renderTodayCard(day) {
  if (!day) return;
  const el  = document.getElementById('today-card');
  const tip = buildTooltip(day.goodFactors, day.badFactors);

  const windowHtml = day.bestWindow ? `
    <div class="best-window-hero">
      <div class="bw-icon">&#9201;</div>
      <div class="bw-content">
        <div class="bw-label">Bedste golfvindue i dag</div>
        <div class="bw-time">${escapeHtml(day.bestWindow)}</div>
      </div>
    </div>` : '';

  el.innerHTML = `
    <div class="today-grid">
      <div class="today-status-col">
        ${renderBadge(day.overallStatus, true)}
        <h2 class="today-summary">${escapeHtml(day.summary)}</h2>
        <div class="today-date">${escapeHtml(day.dayOfWeek)} ${day.date}</div>
      </div>
      ${renderScoreRing(day.score, day.overallStatus, tip)}
    </div>
    ${windowHtml}
    ${renderFactors(day.goodFactors, day.badFactors)}`;
}

// ── Best days ────────────────────────────────────────────────────────────────

function renderBestDays(days) {
  const el = document.getElementById('best-days-list');
  el.innerHTML = days.map((d, i) => `
    <div class="best-day-row">
      <div class="rank-circle${i === 0 ? ' top' : ''}">${i + 1}</div>
      <div class="bd-info">
        <span class="day-name">${escapeHtml(d.dayOfWeek)} ${d.date}</span>
        <div class="bd-meta">
          ${renderBadge(d.overallStatus)}
          <span class="day-score-text">${d.score}/100</span>
          ${d.bestWindow ? `<span class="window-tag">&#9201; ${escapeHtml(d.bestWindow)}</span>` : ''}
        </div>
      </div>
    </div>`
  ).join('');
}

// ── Daily sections ───────────────────────────────────────────────────────────

function renderDailySections(forecast) {
  const el = document.getElementById('daily-sections');
  el.innerHTML = forecast.map(day => {
    const tip   = buildTooltip(day.goodFactors, day.badFactors);
    const hours = day.hourlyForecasts.filter(h => h.time >= '07:00' && h.time <= '22:00');

    const windowHtml = day.bestWindow ? `
      <div class="day-window-row">&#9201; Bedste golfvindue: <strong>${escapeHtml(day.bestWindow)}</strong></div>` : '';

    const rows = hours.map(h => {
      const hTip = buildTooltip(h.goodFactors, h.badFactors);
      const hasRealGust = h.windGust != null && Math.abs(h.windGust - h.windSpeed) > 0.1;
      const windCell = hasRealGust
        ? `${h.windSpeed.toFixed(1)} (${h.windGust.toFixed(1)})`
        : h.windSpeed.toFixed(1);
      return `
        <tr>
          <td>${h.time}</td>
          <td>${h.temperature.toFixed(1)}</td>
          <td>${windCell}</td>
          <td>${h.precipitation < 0 ? '–' : h.precipitation.toFixed(1)}</td>
          <td><span class="score-tip" data-tip="${escapeHtml(hTip)}">${h.score}<span class="score-mini-bar"><span style="width:${h.score}%;background:${scoreColor(statusCls(h.status))}"></span></span></span></td>
          <td>${renderBadge(h.status)}</td>
        </tr>`;
    }).join('');

    return `
      <div class="card">
        <div class="day-header">
          ${renderBadge(day.overallStatus)}
          <div class="day-header-text">
            <strong>${escapeHtml(day.dayOfWeek)} ${day.date}</strong>
            <span class="day-summary-inline">${escapeHtml(day.summary)}</span>
          </div>
          <span class="day-score-badge score-tip" data-tip="${escapeHtml(tip)}">${day.score}/100</span>
        </div>
        ${windowHtml}
        <div class="day-factors">${renderFactors(day.goodFactors, day.badFactors)}</div>
        <details class="day-hours">
          <summary>Vis timeprognose (07:00–22:00)</summary>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Tidspunkt</th><th>Temp (°C)</th><th>Vind (m/s)</th>
                  <th>Regn (mm)</th><th>Score</th><th>Status</th>
                </tr>
              </thead>
              <tbody>${rows}</tbody>
            </table>
          </div>
        </details>
      </div>`
  }).join('');
}

// ── Error / init ─────────────────────────────────────────────────────────────

function showError(msg) {
  document.getElementById('error-message').textContent = msg;
  showView('error');
}

document.getElementById('btn-back').addEventListener('click',  () => showView('selector'));
document.getElementById('btn-retry').addEventListener('click', () => showView('selector'));

document.getElementById('btn-favorite').addEventListener('click', () => {
  toggleFavorite(currentClubId, currentClubName);
  updateStarButton(currentClubId);
  renderFavoritesPanel();
});

loadClubs();
renderFavoritesPanel();
