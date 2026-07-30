import { useCallback, useEffect, useMemo, useState } from 'react';
import { Check, GitBranch, RefreshCw, Search, SquareTerminal } from 'lucide-react';

const DAY_MS = 86_400_000;
const COLORS = ['#ebedf0', '#9be9a8', '#40c463', '#30a14e', '#216e39'];
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];
const MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

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

function App() {
  const [repos, setRepos] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [year, setYear] = useState(new Date().getFullYear());
  const [data, setData] = useState({ days: [], total: 0, repoStats: [] });
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState('');

  const loadRepos = useCallback(async (refresh = false) => {
    setScanning(true);
    setError('');
    try {
      const response = await fetch(`/api/repos${refresh ? '?refresh=1' : ''}`);
      if (!response.ok) throw new Error('无法扫描本地 Git 仓库');
      const payload = await response.json();
      setRepos(payload.repositories);
      setSelected(previous => {
        if (previous.size) return new Set([...previous].filter(id => payload.repositories.some(repo => repo.id === id)));
        return new Set(payload.repositories.map(repo => repo.id));
      });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setScanning(false);
    }
  }, []);

  useEffect(() => {
    loadRepos();
  }, [loadRepos]);

  useEffect(() => {
    if (!repos.length) {
      return;
    }
    const controller = new AbortController();
    const loadContributions = async () => {
      setLoading(true);
      setError('');
      try {
        const params = new URLSearchParams({ year: String(year), repos: [...selected].join(',') });
        const response = await fetch(`/api/contributions?${params}`, { signal: controller.signal });
        if (!response.ok) throw new Error('无法读取 Git 提交记录');
        setData(await response.json());
      } catch (requestError) {
        if (requestError.name !== 'AbortError') setError(requestError.message);
      } finally {
        setLoading(false);
      }
    };
    loadContributions();
    return () => controller.abort();
  }, [repos, selected, year, scanning]);

  const counts = useMemo(() => new Map(data.days.map(day => [day.date, day.count])), [data.days]);
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
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark"><SquareTerminal size={19} /></span>
          <div><strong>Local Contributions</strong><span>D 盘 Git 活动</span></div>
        </div>
        <button className="icon-button" onClick={() => loadRepos(true)} disabled={scanning} title="重新扫描 D 盘">
          <RefreshCw size={17} className={scanning ? 'spin' : ''} />
        </button>
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

          {error && <div className="error-banner">{error}</div>}

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
                        return <span className={`day ${day.inYear ? '' : 'outside'}`} key={day.date} style={{ backgroundColor: COLORS[level] }} title={`${day.date} · ${day.count} 次提交`} />;
                      })}
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="legend"><span>少</span>{COLORS.map(color => <i key={color} style={{ backgroundColor: color }} />)}<span>多</span></div>
            {loading && <div className="loading-overlay">正在读取 Git 历史…</div>}
          </div>

          <div className="ranking">
            <div className="section-title"><h2>项目提交</h2><span>当前筛选范围</span></div>
            {data.repoStats.length ? data.repoStats.map(repo => (
              <div className="ranking-row" key={repo.id}>
                <span>{repo.name}</span><div><i style={{ width: `${Math.max(3, (repo.count / Math.max(...data.repoStats.map(item => item.count))) * 100)}%` }} /></div><strong>{repo.count}</strong>
              </div>
            )) : <p className="empty">所选年份没有匹配的本地提交。</p>}
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
