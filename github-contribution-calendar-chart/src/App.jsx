import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Check, Clock3, FolderSearch, GitBranch, Moon, RefreshCw, Search, Sun, SquareTerminal } from 'lucide-react';

const DAY_MS = 86_400_000;
const COLORS = ['#ebedf0', '#9be9a8', '#40c463', '#30a14e', '#216e39'];
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];
const MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

const STORAGE_KEY = 'local-contributions-theme';
const THEME_LIGHT = 'light';
const THEME_DARK = 'dark';

function dateKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function buildCalendar(year, counts) {
  const start = new Date(year, 0, 1);
  start.setDate(start.getDate() - start.getDay());
  const end = new Date(year, 11, 31);
  end.setDate(end.getDate() + (6 - end.getDay()));
  const weeks = [];

  for (let cursor = new Date(start); cursor <= end; cursor = new Date(cursor.getTime() + 7 * DAY_MS)) {
    weeks.push(Array.from({ length: 7 }, (_, day) => {
      const date = new Date(cursor.getTime() + day * DAY_MS);
      const key = dateKey(date);
      return { date: key, count: counts.get(key) || 0, inYear: date.getFullYear() === year };
    }));
  }

  return weeks;
}

function levelFor(count, max) {
  if (!count) return 0;
  if (max <= 4) return count;
  return Math.min(4, Math.max(1, Math.ceil((count / max) * 4)));
}

function formatTimestamp(value) {
  if (!value) return '尚未建立缓存';
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  }).format(new Date(value));
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  const [y, m, d] = dateStr.split('-');
  return `${y}年${parseInt(m)}月${parseInt(d)}日`;
}

function operationSummary(operation) {
  if (!operation) return '';
  const duration = operation.durationMs < 1000
    ? `${operation.durationMs} 毫秒`
    : `${(operation.durationMs / 1000).toFixed(1)} 秒`;
  const unavailable = operation.unavailable ? `，不可用 ${operation.unavailable}` : '';
  return `已检查 ${operation.checked} 个仓库，新增 ${operation.added}，更新 ${operation.updated}${unavailable}，耗时 ${duration}`;
}

function App() {
  const [repos, setRepos] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [year, setYear] = useState(new Date().getFullYear());
  const [data, setData] = useState({ days: [], total: 0, repoStats: [] });
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [repositoryTask, setRepositoryTask] = useState('initial');
  const [cacheMeta, setCacheMeta] = useState({ lastRefreshAt: null, lastDiscoveryAt: null });
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [colorScheme, setColorScheme] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === THEME_LIGHT || stored === THEME_DARK) return stored;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? THEME_DARK : THEME_LIGHT;
  });
  const [tooltip, setTooltip] = useState(null);
  const tooltipRef = useRef(null);
  const dayRefs = useRef(new Map());
  const selectionInitialized = useRef(false);

  // 应用主题
  useEffect(() => {
    document.documentElement.setAttribute('data-color-scheme', colorScheme);
    localStorage.setItem(STORAGE_KEY, colorScheme);
  }, [colorScheme]);

  // 监听系统主题变化
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e) => {
      if (!localStorage.getItem(STORAGE_KEY)) {
        setColorScheme(e.matches ? THEME_DARK : THEME_LIGHT);
      }
    };
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  // tooltip 位置跟踪
  useEffect(() => {
    if (!tooltip) return;
    const el = dayRefs.current.get(tooltip.key);
    if (!el) return;
    const rect = el.getBoundingClientRect();
    tooltipRef.current.style.left = `${rect.left + rect.width / 2}px`;
    tooltipRef.current.style.top = `${rect.top - 8}px`;
    tooltipRef.current.style.transform = 'translate(-50%, -100%)';
  }, [tooltip]);

  const loadRepos = useCallback(async (task = 'initial') => {
    setRepositoryTask(task);
    setError('');
    if (task !== 'initial') setStatus('');
    try {
      const endpoint = task === 'refresh' ? '/api/refresh' : task === 'discover' ? '/api/discover' : '/api/repos';
      const response = await fetch(endpoint, { method: task === 'initial' ? 'GET' : 'POST' });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.error || '请求失败，请稍后重试');
      setRepos(payload.repositories);
      setCacheMeta({ lastRefreshAt: payload.lastRefreshAt, lastDiscoveryAt: payload.lastDiscoveryAt });
      setStatus(operationSummary(payload.operation));
      setSelected(previous => {
        if (!selectionInitialized.current) {
          selectionInitialized.current = true;
          return new Set(payload.repositories.map(repo => repo.id));
        }
        return new Set([...previous].filter(id => payload.repositories.some(repo => repo.id === id)));
      });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setRepositoryTask('');
    }
  }, []);

  useEffect(() => {
    loadRepos('initial');
  }, [loadRepos]);

  useEffect(() => {
    if (!repos.length) return;
    const controller = new AbortController();
    const loadContributions = async () => {
      setLoading(true);
      setError('');
      try {
        const params = new URLSearchParams({ year: String(year), repos: [...selected].join(',') });
        const response = await fetch(`/api/contributions?${params}`, { signal: controller.signal });
        if (!response.ok) throw new Error('无法读取 Git 提交记录');
        const result = await response.json();
        setData(result);
      } catch (requestError) {
        if (requestError.name !== 'AbortError') setError(requestError.message);
      } finally {
        setLoading(false);
      }
    };
    loadContributions();
    return () => controller.abort();
  }, [repos, selected, year]);

  const counts = useMemo(() => {
    const m = new Map();
    for (const day of data.days) m.set(day.date, day.count);
    return m;
  }, [data.days]);

  const daysMap = useMemo(() => {
    const m = new Map();
    for (const day of data.days) m.set(day.date, day);
    return m;
  }, [data.days]);

  const weeks = useMemo(() => buildCalendar(year, counts), [year, counts]);
  const max = useMemo(() => Math.max(0, ...data.days.map(day => day.count)), [data.days]);
  const activeDays = data.days.filter(day => day.count > 0).length;
  const filteredRepos = repos.filter(repo => `${repo.name} ${repo.path}`.toLowerCase().includes(query.toLowerCase()));

  const toggleRepo = id => {
    setSelected(previous => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleDayMouseEnter = (e, day) => {
    const dayData = daysMap.get(day.date);
    if (!dayData || !dayData.count) return;
    const reposStr = dayData.details
      ?.filter(d => d.count > 0)
      .map(d => `${d.repoName}(${d.count})`)
      .join('、');
    setTooltip({
      key: day.date,
      date: formatDate(day.date),
      count: dayData.count,
      repos: reposStr || '无详情'
    });
  };

  const handleDayMouseLeave = () => setTooltip(null);

  const monthLabels = weeks.map((week, index) => {
    const firstDayInYear = week.find(day => day.inYear);
    if (!firstDayInYear) return '';
    const current = new Date(`${firstDayInYear.date}T00:00:00`);
    const previousDayInYear = index ? weeks[index - 1].find(day => day.inYear) : null;
    if (!previousDayInYear) return MONTHS[current.getMonth()];
    const previous = new Date(`${previousDayInYear.date}T00:00:00`);
    return current.getMonth() !== previous.getMonth() ? MONTHS[current.getMonth()] : '';
  });

  return (
    <div className="app-shell">
      {/* Tooltip */}
      {tooltip && (
        <div ref={tooltipRef} className="tooltip-wrapper">
          <div className="tooltip">
            <div className="tooltip-date">{tooltip.date}</div>
            <div className="tooltip-count">{tooltip.count} 次提交</div>
            <div className="tooltip-repos">{tooltip.repos}</div>
          </div>
        </div>
      )}

      <header className="topbar">
        <div className="brand">
          <span className="brand-mark"><SquareTerminal size={19} /></span>
          <div><strong>Local Contributions</strong><span>本地 Git 活动缓存</span></div>
        </div>
        <div className="topbar-actions">
          <span className="cache-time" title={cacheMeta.lastRefreshAt ? new Date(cacheMeta.lastRefreshAt).toLocaleString('zh-CN') : ''}>
            <Clock3 size={14} />{formatTimestamp(cacheMeta.lastRefreshAt)}
          </span>
          <button className="action-button" onClick={() => loadRepos('refresh')} disabled={Boolean(repositoryTask)} title="检查已有仓库的新增或修改提交">
            <RefreshCw size={15} className={repositoryTask === 'refresh' ? 'spin' : ''} />
            <span>刷新提交</span>
          </button>
          <button className="icon-button" onClick={() => loadRepos('discover')} disabled={Boolean(repositoryTask)} title="扫描所有磁盘以发现新仓库">
            <FolderSearch size={17} className={repositoryTask === 'discover' ? 'pulse' : ''} />
          </button>
          <button
            className="dark-toggle"
            onClick={() => setColorScheme(prev => prev === THEME_LIGHT ? THEME_DARK : THEME_LIGHT)}
            title={colorScheme === THEME_LIGHT ? '切换到深色模式' : '切换到亮色模式'}
          >
            {colorScheme === THEME_LIGHT ? <Moon size={16} /> : <Sun size={16} />}
          </button>
        </div>
      </header>

      <main className="workspace">
        <aside className="sidebar">
          <div className="sidebar-heading">
            <span>项目</span><span className="count-badge">{repos.length}</span>
          </div>
          <label className="search-field">
            <Search size={15} />
            <input value={query} onChange={event => setQuery(event.target.value)} placeholder="筛选仓库" />
          </label>
          <button className="select-all" onClick={() => setSelected(selected.size === repos.length ? new Set() : new Set(repos.map(repo => repo.id)))}>
            <span className={`checkbox ${selected.size === repos.length && repos.length ? 'checked' : ''}`}><Check size={12} /></span>
            全部项目
          </button>
          <div className="repo-list">
            {filteredRepos.map(repo => (
              <button className="repo-row" key={repo.id} onClick={() => toggleRepo(repo.id)} title={repo.path}>
                <span className={`checkbox ${selected.has(repo.id) ? 'checked' : ''}`}><Check size={12} /></span>
                <GitBranch size={14} />
                <span>{repo.name}</span>
              </button>
            ))}
          </div>
        </aside>

        <section className="content">
          <div className="page-heading">
            <div><p className="eyebrow">COMMIT ACTIVITY</p><h1>{year} 提交记录</h1></div>
            <select value={year} onChange={event => setYear(Number(event.target.value))} aria-label="选择年份">
              {Array.from({ length: 10 }, (_, index) => new Date().getFullYear() - index).map(option => <option key={option}>{option}</option>)}
            </select>
          </div>

          {error && <div className="error-banner" role="alert">{error}</div>}
          {status && <div className="status-banner" role="status">{status}</div>}

          <div className="metrics">
            <div><strong>{loading ? '—' : data.total.toLocaleString()}</strong><span>次提交</span></div>
            <div><strong>{loading ? '—' : activeDays}</strong><span>个活跃日</span></div>
            <div><strong>{selected.size}</strong><span>个已选项目</span></div>
          </div>

          <div className="calendar-panel" aria-busy={loading}>
            <div className="calendar-scroll" style={{ '--calendar-width': `${25 + weeks.length * 15}px` }}>
              <div className="month-row" style={{ gridTemplateColumns: `25px repeat(${weeks.length}, 11px)` }}><span />{monthLabels.map((label, index) => <span key={index}>{label}</span>)}</div>
              <div className="calendar-body">
                <div className="weekday-column">{WEEKDAYS.map((day, index) => <span key={day}>{index % 2 ? day : ''}</span>)}</div>
                <div className="weeks">
                  {weeks.map((week, weekIndex) => (
                    <div className="week" key={weekIndex}>
                      {week.map(day => {
                        const level = day.inYear ? levelFor(day.count, max) : 0;
                        return (
                          <span
                            className={`day ${day.inYear ? '' : 'outside'}`}
                            key={day.date}
                            ref={el => { if (el) dayRefs.current.set(day.date, el); }}
                            style={{ backgroundColor: COLORS[level] }}
                            onMouseEnter={e => { e.stopPropagation(); handleDayMouseEnter(e, day); }}
                            onMouseLeave={handleDayMouseLeave}
                            onFocus={e => { e.stopPropagation(); handleDayMouseEnter(e, day); }}
                            onBlur={handleDayMouseLeave}
                            tabIndex={day.inYear ? 0 : -1}
                            title={`${day.date} · ${day.count} 次提交`}
                          />
                        );
                      })}
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="legend"><span>少</span>{COLORS.map(color => <i key={color} style={{ backgroundColor: color }} />)}<span>多</span></div>
            {(loading || repositoryTask === 'initial') && <div className="loading-overlay">{repositoryTask === 'initial' ? '正在载入本地缓存…' : '正在读取缓存…'}</div>}
          </div>

          <div className="ranking">
            <div className="section-title"><h2>项目提交</h2><span>当前筛选范围</span></div>
            {data.repoStats.length ? data.repoStats.map(repo => (
              <div className="ranking-row" key={repo.id}>
                <span title={repo.name}>{repo.name}</span>
                <div><i style={{ width: `${Math.max(3, (repo.count / Math.max(...data.repoStats.map(item => item.count))) * 100)}%` }} /></div>
                <strong>{repo.count}</strong>
              </div>
            )) : <p className="empty">所选年份没有匹配的本地提交。</p>}
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
