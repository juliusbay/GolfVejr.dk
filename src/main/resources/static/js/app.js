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
  if (name === 'selector' && map) setTimeout(() => map.invalidateSize(), 50);
}

// ── Status helpers ──────────────────────────────────────────────────────────

function statusCls(s)  { return { GREEN: 'good', YELLOW: 'warn', RED: 'bad', Nat: 'night', Solnedgang: 'status-night' }[s] || 'bad'; }
function statusIcon(s) { return { GREEN: '✓', YELLOW: '~', RED: '✕', Nat: '🌙', Solnedgang: '🌙' }[s] || ''; }
function statusText(s) { return { GREEN: 'Ideelt', YELLOW: 'Spilbart', RED: 'Frarådes', Nat: 'Nat', Solnedgang: 'Solnedgang' }[s] || s; }

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
  return { good: '#16a34a', warn: '#d97706', bad: '#dc2626', night: '#6b7280', 'status-night': '#4338ca' }[cls] || '#16a34a';
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

// ── Denmark map (Leaflet) ────────────────────────────────────────────────────

const MAP_COLORS = { GREEN: '#16a34a', YELLOW: '#d97706', RED: '#dc2626' };

let map = null;
let mapMarkers = [];

function initMap() {
  if (typeof L === 'undefined') {
    console.error('Leaflet failed to load — map disabled');
    return;
  }
  try {
    map = L.map('denmark-map', { zoomControl: true }).setView([56.2, 10.4], 6);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 18,
    }).addTo(map);
  } catch (err) {
    console.error('Map initialization failed:', err);
    map = null;
  }
}

function buildPopupHtml(club) {
  const addr = [club.street, club.city].filter(Boolean).join(', ');
  const addrLine  = addr    ? `<p class="mp-addr">${escapeHtml(addr)}</p>` : '';
  const webLine   = club.website
    ? `<a class="mp-link" href="${escapeHtml(club.website)}" target="_blank" rel="noopener">🌐 Hjemmeside</a>` : '';
  const phoneLine = club.phone
    ? `<a class="mp-link" href="tel:${escapeHtml(club.phone)}">${escapeHtml(club.phone)}</a>` : '';
  const linksRow  = (webLine || phoneLine)
    ? `<div class="mp-links">${webLine}${phoneLine}</div>` : '';
  return `
    <div class="map-popup">
      <h4 class="mp-name">${escapeHtml(club.name)}</h4>
      ${addrLine}
      ${linksRow}
      <button class="mp-btn" data-id="${escapeHtml(String(club.id))}" data-name="${escapeHtml(club.name)}">Se vejrudsigt →</button>
    </div>`;
}

function loadMapData() {
  if (!map) return;
  fetch(`${API}/map-data`)
    .then(r => r.ok ? r.json() : Promise.reject())
    .then(data => {
      mapMarkers.forEach(m => m.remove());
      mapMarkers = [];
      data.forEach(club => {
        const color = MAP_COLORS[club.status] || '#6b7280';
        const marker = L.circleMarker([club.lat, club.lon], {
          radius: 7,
          fillColor: color,
          color: '#1f2937',
          weight: 1.5,
          opacity: 1,
          fillOpacity: 0.88,
        }).addTo(map);
        marker.bindTooltip(club.name, { permanent: false, direction: 'top', className: 'map-tooltip' });
        marker.bindPopup(buildPopupHtml(club), { maxWidth: 260, className: 'map-popup-wrap' });
        mapMarkers.push(marker);
      });
    })
    .catch(() => {}); // Cache may still be empty on first startup — fail silently.

  // Delegate forecast-button clicks from any open popup.
  map.on('popupopen', e => {
    const btn = e.popup.getElement().querySelector('.mp-btn');
    if (!btn) return;
    btn.onclick = () => {
      map.closePopup();
      loadForecast(btn.dataset.id, btn.dataset.name);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
  });
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
let currentTimePref  = 'all';

let cmpClub1Id  = null;
let cmpClub2Id  = null;
let cmpDateStr  = null;

async function loadClubs() {
  const input    = document.getElementById('club-search');
  const dropdown = document.getElementById('club-dropdown');
  const btn      = document.getElementById('btn-go');

  try {
    allClubs = await fetch(`${API}/golfclubs`).then(r => {
      if (!r.ok) throw new Error('Server fejl');
      return r.json();
    });

    populateCompareSelects();
    populateCompareDates();

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
      fetch(`${API}/forecast/${clubId}?timePref=${currentTimePref}`).then(r => {
        if (!r.ok) throw new Error('Forecast fejl');
        return r.json();
      }),
      fetch(`${API}/forecast/${clubId}/best-days?timePref=${currentTimePref}`).then(r => {
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
        <div class="today-date">${escapeHtml(day.dayOfWeek)} ${day.date}</div>
        ${renderBadge(day.overallStatus, true)}
        <h2 class="today-summary">${escapeHtml(day.summary)}</h2>
      </div>
      ${renderScoreRing(day.score, day.overallStatus, tip)}
    </div>
    ${windowHtml}
    ${renderFactors(day.goodFactors, day.badFactors)}
    <div class="today-hourly-section">
      ${renderHourlyTable(day, false)}
    </div>`;
}

// ── Best days ────────────────────────────────────────────────────────────────

function renderBestDays(days) {
  const el = document.getElementById('best-days-list');
  el.innerHTML = days.map((d, i) => {
    const isIntervalDay = d.hourlyForecasts && d.hourlyForecasts.length > 0 && d.hourlyForecasts[0].time.includes('–');
    const showWindow    = d.bestWindow && !isIntervalDay;
    return `
    <div class="best-day-row">
      <div class="rank-circle${i === 0 ? ' top' : ''}">${i + 1}</div>
      <div class="bd-info">
        <span class="day-name">${escapeHtml(d.dayOfWeek)} ${d.date}</span>
        <div class="bd-meta">
          ${renderBadge(d.overallStatus)}
          <span class="day-score-text">${d.score}/100</span>
          ${showWindow ? `<span class="window-tag">&#9201; ${escapeHtml(d.bestWindow)}</span>` : ''}
        </div>
      </div>
    </div>`;
  }).join('');
}

// ── Weather icon helper ──────────────────────────────────────────────────────

function weatherEmoji(symbolCode, isNight) {
  if (!symbolCode) return '';
  const base = symbolCode.replace(/_(day|night|polartwilight)$/, '');
  if (base.includes('thunder'))                    return isNight ? '🌙⛈️' : '⛈️';
  if (base.includes('snow'))                       return '❄️';
  if (base.includes('sleet'))                      return '🌨️';
  if (base.includes('heavyrain'))                  return isNight ? '🌙🌧️' : '🌧️';
  if (base.includes('rain') || base === 'drizzle') return isNight ? '🌙🌦️' : '🌦️';
  if (base === 'fog')                              return isNight ? '🌙🌫️' : '🌫️';
  if (base === 'cloudy')                           return isNight ? '🌙☁️'  : '☁️';
  if (base === 'partlycloudy') return isNight ? '🌙⛅' : '⛅';
  if (base === 'fair')         return isNight ? '🌙'   : '🌤️';
  if (base === 'clearsky')     return isNight ? '🌙'   : '☀️';
  return '';
}

function isAfterSunset(timeStr, sunsetTime) {
  if (!sunsetTime) return false;
  const [sh, sm] = sunsetTime.split(':').map(Number);
  const rowHour = parseInt(timeStr, 10);
  return rowHour * 60 > sh * 60 + sm;
}

// ── Hourly table (shared) ─────────────────────────────────────────────────────

function renderHourlyRows(day, teaser = false) {
  const hours = day.hourlyForecasts.filter(h => h.time >= '06:00' && h.time <= '22:00');
  return hours.map((h, idx) => {
    const hTip = buildTooltip(h.goodFactors, h.badFactors);
    const night = isAfterSunset(h.time, day.sunsetTime);
    const icon  = weatherEmoji(h.symbolCode, night);
    const hasRealGust = h.windGust != null && Math.abs(h.windGust - h.windSpeed) > 0.1;
    const windSpeeds = hasRealGust
      ? `${h.windSpeed.toFixed(1)} (${h.windGust.toFixed(1)})`
      : h.windSpeed.toFixed(1);
    const dirArrow = h.windDirection != null
      ? `<span class="wind-dir-arrow" style="transform:rotate(${h.windDirection}deg)" title="${Math.round(h.windDirection)}°">↓</span>`
      : '';
    const windCell = `${windSpeeds}${dirArrow}`;
    const cls = teaser && idx >= 2 ? ' class="extra-hour hidden"' : '';
    return `
      <tr${cls}>
        <td class="weather-icon-cell">${icon}</td>
        <td>${h.time}</td>
        <td>${h.temperature.toFixed(1)}</td>
        <td class="wind-cell">${windCell}</td>
        <td>${h.precipitation < 0 ? '–' : h.precipitation.toFixed(1)}</td>
        <td><span class="score-tip" data-tip="${escapeHtml(hTip)}">${h.score}<span class="score-mini-bar"><span style="width:${h.score}%;background:${scoreColor(statusCls(h.status))}"></span></span></span></td>
        <td>${renderBadge(h.status)}</td>
      </tr>`;
  }).join('');
}

const HOURLY_THEAD = `<tr>
  <th class="weather-icon-cell"></th><th>Tidspunkt</th><th>Temp (°C)</th><th>Vind (m/s)</th>
  <th>Regn (mm)</th><th>Score</th><th>Status</th>
</tr>`;

function renderHourlyTable(day, teaser) {
  const rows = renderHourlyRows(day, teaser);
  const tableHtml = `
    <div class="table-wrap">
      <table>
        <thead>${HOURLY_THEAD}</thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`;
  if (!teaser) return tableHtml;
  const hoursCount = day.hourlyForecasts.filter(h => h.time >= '06:00' && h.time <= '22:00').length;
  const toggle = hoursCount > 2 ? `
    <div class="expand-hours-toggle" onclick="toggleHours(this)">
      <span class="toggle-chevron">▾</span>
      <span class="toggle-text">Vis alle timer</span>
    </div>` : '';
  return tableHtml + toggle;
}

window.toggleHours = function(btn) {
  const clickedCard = btn.closest('.card');
  const firstExtra  = clickedCard.querySelector('.extra-hour');
  const expanding   = firstExtra && firstExtra.classList.contains('hidden');

  // Sync all cards in the same visual row (same offsetTop within the grid).
  const grid     = clickedCard.closest('.daily-grid');
  const rowCards = grid
    ? Array.from(grid.querySelectorAll('.card')).filter(c => c.offsetTop === clickedCard.offsetTop)
    : [clickedCard];

  rowCards.forEach(card => {
    card.querySelectorAll('.extra-hour').forEach(row => {
      if (expanding) row.classList.remove('hidden');
      else           row.classList.add('hidden');
    });
    const toggle = card.querySelector('.expand-hours-toggle');
    if (toggle) {
      toggle.querySelector('.toggle-chevron').textContent = expanding ? '▴' : '▾';
      toggle.querySelector('.toggle-text').textContent    = expanding ? 'Skjul timer' : 'Vis alle timer';
    }
  });
};

// ── Daily sections ───────────────────────────────────────────────────────────

function renderDailySections(forecast) {
  const el = document.getElementById('daily-sections');
  el.innerHTML = forecast.slice(1).map(day => {
    const tip = buildTooltip(day.goodFactors, day.badFactors);
    const isIntervalDay = day.hourlyForecasts.length > 0 && day.hourlyForecasts[0].time.includes('–');
    const showWindow    = day.bestWindow && !isIntervalDay;
    return `
      <div class="card">
        <div class="card-header">
          ${renderBadge(day.overallStatus)}
          <div class="day-header-text">
            <strong>${escapeHtml(day.dayOfWeek)} ${day.date}</strong>
            <span class="day-summary-inline">${escapeHtml(day.summary)}</span>
          </div>
          <span class="day-score-badge score-tip" data-tip="${escapeHtml(tip)}">${day.score}/100</span>
        </div>
        <div class="day-window-row${showWindow ? '' : ' day-window-empty'}">
          ${showWindow ? `&#9201; Bedste golfvindue: <strong>${escapeHtml(day.bestWindow)}</strong>` : ''}
        </div>
        <div class="card-factors">${renderFactors(day.goodFactors, day.badFactors)}</div>
        ${renderHourlyTable(day, true)}
      </div>`;
  }).join('');
}

// ── Compare clubs ────────────────────────────────────────────────────────────

function populateCompareSelects() {
  [1, 2].forEach(n => {
    const sel = document.getElementById(`cmp-select-${n}`);
    sel.innerHTML = '<option value="">Vælg klub...</option>' +
      allClubs.map(c => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
    sel.addEventListener('change', () => {
      if (n === 1) cmpClub1Id = sel.value || null;
      else         cmpClub2Id = sel.value || null;
      updateCompareBtn();
    });
  });
}

function populateCompareDates() {
  const container = document.getElementById('compare-date-pills');
  const today = new Date();
  const pad = n => String(n).padStart(2, '0');
  const days = [];
  for (let i = 0; i < 9; i++) {
    const d = new Date(today);
    d.setDate(today.getDate() + i);
    const label = d.toLocaleDateString('da-DK', { weekday: 'short', day: 'numeric', month: 'short' });
    const value = `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()}`;
    days.push({ label, value });
  }
  container.innerHTML = days.map(d =>
    `<button class="date-pill" data-date="${d.value}">${escapeHtml(d.label)}</button>`
  ).join('');
  container.querySelectorAll('.date-pill').forEach(btn => {
    btn.addEventListener('click', () => {
      container.querySelectorAll('.date-pill').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      cmpDateStr = btn.dataset.date;
      updateCompareBtn();
    });
  });
}

function updateCompareBtn() {
  document.getElementById('btn-compare').disabled =
    !(cmpClub1Id && cmpClub2Id && cmpDateStr && cmpClub1Id !== cmpClub2Id);
}

function openCompareModal() {
  document.getElementById('compare-modal').classList.remove('hidden');
}

function closeCompareModal() {
  document.getElementById('compare-modal').classList.add('hidden');
}

async function loadCompare() {
  const body = document.getElementById('compare-modal-body');
  body.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div><p>Henter vejrdata...</p></div>';
  openCompareModal();
  try {
    const [f1, f2] = await Promise.all([
      fetch(`${API}/forecast/${cmpClub1Id}`).then(r => { if (!r.ok) throw new Error(); return r.json(); }),
      fetch(`${API}/forecast/${cmpClub2Id}`).then(r => { if (!r.ok) throw new Error(); return r.json(); }),
    ]);
    const day1 = f1.find(d => d.date === cmpDateStr);
    const day2 = f2.find(d => d.date === cmpDateStr);
    if (!day1 || !day2) {
      body.innerHTML = '<div class="card error-card"><p>Ingen vejrdata for den valgte dato.</p></div>';
      return;
    }
    const club1 = allClubs.find(c => String(c.id) === String(cmpClub1Id));
    const club2 = allClubs.find(c => String(c.id) === String(cmpClub2Id));
    body.innerHTML = renderComparisonCard(club1?.name || 'Klub 1', day1, club2?.name || 'Klub 2', day2);
  } catch {
    body.innerHTML = '<div class="card error-card"><p>Kunne ikke hente sammenligningsdata. Prøv igen.</p></div>';
  }
}

function vsRow(left, label, right) {
  return `
    <div class="vs-row">
      <div class="vs-left">${left}</div>
      <div class="vs-label">${escapeHtml(label)}</div>
      <div class="vs-right">${right}</div>
    </div>`;
}

function renderComparisonCard(name1, day1, name2, day2) {
  const winner = day1.score > day2.score ? 1 : day2.score > day1.score ? 2 : 0;
  const scoreHtml = (day, w) =>
    `<span class="vs-score${w ? ' winner' : ''}">${day.score}<span class="vs-den">/100</span></span>`;
  const windowHtml = day =>
    day.bestWindow
      ? `<span class="window-tag">&#9201; ${escapeHtml(day.bestWindow)}</span>`
      : `<span class="vs-none">—</span>`;

  return `
    <div class="vs-card">
      <div class="vs-header">
        <div class="vs-club-name${winner === 1 ? ' winner-highlight' : ''}">${escapeHtml(name1)}</div>
        <div class="vs-header-center">vs</div>
        <div class="vs-club-name right${winner === 2 ? ' winner-highlight' : ''}">${escapeHtml(name2)}</div>
      </div>
      ${vsRow(scoreHtml(day1, winner === 1), 'Score', scoreHtml(day2, winner === 2))}
      ${vsRow(renderBadge(day1.overallStatus), 'Status', renderBadge(day2.overallStatus))}
      ${vsRow(
        `<span class="vs-summary">${escapeHtml(day1.summary)}</span>`,
        'Vurdering',
        `<span class="vs-summary">${escapeHtml(day2.summary)}</span>`
      )}
      ${vsRow(windowHtml(day1), 'Bedste vindue', windowHtml(day2))}
      ${vsRow(
        renderFactors(day1.goodFactors, day1.badFactors),
        'Faktorer',
        renderFactors(day2.goodFactors, day2.badFactors)
      )}
    </div>`;
}

// ── Error / init ─────────────────────────────────────────────────────────────

function showError(msg) {
  document.getElementById('error-message').textContent = msg;
  showView('error');
}

document.getElementById('btn-back').addEventListener('click',    () => showView('selector'));
document.getElementById('btn-retry').addEventListener('click',   () => showView('selector'));
document.getElementById('btn-compare').addEventListener('click', loadCompare);

document.querySelector('.close-modal').addEventListener('click',  closeCompareModal);
document.querySelector('.modal-overlay').addEventListener('click', closeCompareModal);
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeCompareModal(); });
document.getElementById('header-logo-link').addEventListener('click', e => {
  e.preventDefault();
  showView('selector');
});

document.getElementById('btn-favorite').addEventListener('click', () => {
  toggleFavorite(currentClubId, currentClubName);
  updateStarButton(currentClubId);
  renderFavoritesPanel();
});

document.querySelectorAll('.tp-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    if (btn.dataset.pref === currentTimePref) return;
    currentTimePref = btn.dataset.pref;
    document.querySelectorAll('.tp-btn').forEach(b => b.classList.toggle('active', b === btn));
    if (currentClubId) loadForecast(currentClubId, currentClubName);
  });
});

initMap();
loadMapData();
loadClubs();
renderFavoritesPanel();
