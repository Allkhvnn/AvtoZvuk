import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { api, login, logout, type Actor, type LowStock, type Sale, type Summary } from './api';
import { SalesPage } from './SalesPage';
import { ProductsPage } from './ProductsPage';
import { PurchasesPage } from './PurchasesPage';
import { StockPage } from './StockPage';

type IconName = 'grid' | 'cart' | 'box' | 'users' | 'chart' | 'settings' | 'search' | 'bell' | 'plus' | 'trend' | 'wallet' | 'receipt' | 'warehouse' | 'chevron';
const icons: Record<IconName, ReactNode> = {
  grid: <><rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/></>,
  cart: <><path d="M3 4h2l2.3 10.2a2 2 0 0 0 2 1.6h7.8a2 2 0 0 0 1.9-1.4L21 8H7"/><circle cx="10" cy="20" r="1"/><circle cx="18" cy="20" r="1"/></>,
  box: <><path d="m4 7 8-4 8 4-8 4-8-4Z"/><path d="m4 7 8 4 8-4v10l-8 4-8-4V7Z"/><path d="M12 11v10"/></>,
  users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9"/></>,
  chart: <path d="M4 19V9M10 19V5M16 19v-7M22 19V3"/>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="M4 12a8 8 0 1 0 16 0 8 8 0 1 0-16 0"/></>,
  search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
  bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></>,
  plus: <path d="M12 5v14M5 12h14"/>,
  trend: <><path d="m3 17 6-6 4 4 8-9"/><path d="M15 6h6v6"/></>,
  wallet: <><path d="M4 6h15a2 2 0 0 1 2 2v10H4a2 2 0 0 1-2-2V6a3 3 0 0 1 3-3h13"/><path d="M16 11h5v4h-5a2 2 0 0 1 0-4Z"/></>,
  receipt: <><path d="M6 2h12v20l-3-2-3 2-3-2-3 2V2Z"/><path d="M9 7h6M9 11h6M9 15h3"/></>,
  warehouse: <><path d="m3 10 9-7 9 7v11H3V10Z"/><path d="M7 21v-7h10v7"/></>,
  chevron: <path d="m9 18 6-6-6-6"/>,
};

function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>{icons[name]}</svg>;
}

const money = new Intl.NumberFormat('ru-KZ', { style: 'currency', currency: 'KZT', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('ru-KZ', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
const fullDate = new Intl.DateTimeFormat('ru-KZ', { weekday: 'long', day: 'numeric', month: 'long' });
const iso = (date: Date) => date.toISOString().slice(0, 10);

function Login({ onSuccess }: { onSuccess: (actor: Actor) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await login(username, password); onSuccess(await api.me()); }
    catch { setError('Неверный логин или пароль'); }
    finally { setBusy(false); }
  }

  return <main className="login-page"><form className="login-card" onSubmit={submit}>
    <div className="login-brand"><div className="brand-mark"><span>AZ</span></div><div><div className="brand-name">Avto<span>Zvuk</span></div><div className="brand-subtitle">учёт магазина</div></div></div>
    <div><p className="eyebrow">РАБОЧАЯ СИСТЕМА</p><h1>Вход в магазин</h1><p>Введите данные своей учётной записи.</p></div>
    <label>Логин<input className="input" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" autoFocus required /></label>
    <label>Пароль<input className="input" type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required /></label>
    {error && <div className="form-error" role="alert">{error}</div>}
    <button className="button primary login-submit" disabled={busy}>{busy ? 'Входим…' : 'Войти'}</button>
  </form></main>;
}

export default function App() {
  const [actor, setActor] = useState<Actor | null>(null);
  const [checking, setChecking] = useState(true);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [sales, setSales] = useState<Sale[]>([]);
  const [stock, setStock] = useState<LowStock[]>([]);
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [activePage, setActivePage] = useState<'dashboard' | 'sales' | 'products' | 'purchases' | 'stock'>('dashboard');
  const [createSaleRequest, setCreateSaleRequest] = useState(0);

  useEffect(() => { api.me().then(setActor).catch(() => setActor(null)).finally(() => setChecking(false)); }, []);
  useEffect(() => {
    if (!actor) return;
    const now = new Date(); const from = new Date(now.getFullYear(), now.getMonth(), 1);
    const common = api.sales(8);
    const owner = actor.role === 'OWNER' ? Promise.all([api.summary(iso(from), iso(now)), api.lowStock()]) : Promise.resolve([null, []] as const);
    Promise.all([common, owner]).then(([recent, [totals, low]]) => { setSales(recent); setSummary(totals); setStock([...low]); }).catch(e => setError(e.message));
  }, [actor]);

  const filtered = useMemo(() => sales.filter(s => `${s.documentNumber} ${s.total}`.toLowerCase().includes(query.toLowerCase())), [sales, query]);
  if (checking) return <div className="splash"><div className="brand-mark"><span>AZ</span></div><span>Загрузка…</span></div>;
  if (!actor) return <Login onSuccess={setActor}/>;

  const metrics = [
    { label: 'Выручка за месяц', value: summary ? money.format(summary.netRevenue) : 'Доступно владельцу', note: summary ? `Возвраты: ${money.format(summary.returns)}` : 'Ваши продажи ниже', icon: 'wallet' as IconName, tone: 'orange' },
    { label: 'Последние продажи', value: String(sales.length), note: 'показаны в таблице', icon: 'receipt' as IconName, tone: 'blue' },
    { label: 'Валовая прибыль', value: summary ? money.format(summary.grossProfit) : '—', note: summary ? `Расходы: ${money.format(summary.expenses)}` : 'Только для владельца', icon: 'trend' as IconName, tone: 'violet' },
    { label: 'Низкий остаток', value: actor.role === 'OWNER' ? String(stock.length) : '—', note: actor.role === 'OWNER' ? 'товаров требуют внимания' : 'Только для владельца', icon: 'warehouse' as IconName, tone: 'green' },
  ];

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><span>AZ</span></div><div><div className="brand-name">Avto<span>Zvuk</span></div><div className="brand-subtitle">учёт магазина</div></div></div>
      <nav className="nav"><p className="nav-caption">РАБОЧЕЕ ПРОСТРАНСТВО</p>
        {[['Обзор','grid','dashboard'],['Продажи','cart','sales'],['Товары','box','products'],['Поступления','receipt','purchases'],['Склад','warehouse','stock'],['Сотрудники','users',''],['Отчёты','chart','']].filter(([, , page]) => !['purchases','stock'].includes(page) || actor.role === 'OWNER').map(([label, icon, page]) => <button onClick={() => page && setActivePage(page as 'dashboard' | 'sales' | 'products' | 'purchases' | 'stock')} className={`button nav-item ${activePage === page ? 'active' : ''}`} key={label}><Icon name={icon as IconName}/><span>{label}</span></button>)}
        <p className="nav-caption secondary">УПРАВЛЕНИЕ</p><button className="button nav-item"><Icon name="settings"/><span>Настройки</span></button>
      </nav>
      <div className="sidebar-card"><div className="pulse-dot"/><div><strong>Система работает</strong><span>Backend подключён</span></div></div>
      <button className="profile profile-button" onClick={async () => { await logout(); setActor(null); }} title="Выйти"><div className="avatar">{actor.username.slice(0,2).toUpperCase()}</div><div><strong>{actor.username}</strong><span>{actor.role === 'OWNER' ? 'Владелец' : 'Продавец'}</span></div><Icon name="chevron" size={16}/></button>
    </aside>
    <main className="main"><header className="topbar"><div className="mobile-brand">AZ</div><div className="search"><Icon name="search" size={18}/><input className="input" placeholder={activePage === 'products' || activePage === 'stock' ? 'Поиск по товарам или артикулу…' : activePage === 'purchases' ? 'Поиск по поставщику или документу…' : 'Поиск по продажам…'} value={query} onChange={e => setQuery(e.target.value)}/></div><div className="header-actions"><button className="button icon-button" aria-label="Уведомления"><Icon name="bell"/></button><button className="button primary" onClick={() => { setActivePage('sales'); setCreateSaleRequest(value => value + 1); }}><Icon name="plus" size={18}/>Новая продажа</button></div></header>
      <div className="content">{error && <div className="page-error">Не удалось загрузить данные: {error}</div>}
        {activePage === 'sales' ? <SalesPage actor={actor} sales={sales} query={query} createRequest={createSaleRequest} onCreated={async () => { setSales(await api.sales(50)); }} /> : activePage === 'products' ? <ProductsPage actor={actor} query={query} /> : activePage === 'purchases' ? <PurchasesPage query={query} /> : activePage === 'stock' ? <StockPage query={query} /> : <>
        <section className="page-heading"><div><p className="eyebrow">{fullDate.format(new Date()).toUpperCase()}</p><h1>Добрый день, {actor.username}</h1><p>Актуальная информация по вашему магазину.</p></div><div className="period-button">Текущий месяц</div></section>
        <section className="metrics-grid">{metrics.map(m => <article className="metric-card" key={m.label}><div className={`metric-icon ${m.tone}`}><Icon name={m.icon}/></div><div className="metric-label">{m.label}</div><div className="metric-value">{m.value}</div><div className={`metric-note ${m.tone === 'green' ? 'warning' : ''}`}>{m.note}</div></article>)}</section>
        <section className="dashboard-row"><article className="panel chart-panel"><div className="panel-head"><div><h2>Финансовый результат</h2><p>Текущий месяц с учётом возвратов</p></div></div><div className="finance-summary"><div><span>Чистая выручка</span><strong>{summary ? money.format(summary.netRevenue) : '—'}</strong></div><div><span>Себестоимость</span><strong>{summary ? money.format(summary.netCost) : '—'}</strong></div><div><span>После расходов</span><strong className="accent-value">{summary ? money.format(summary.grossProfitAfterExpenses) : '—'}</strong></div></div></article>
          <article className="panel stock-panel"><div className="panel-head"><div><h2>Заканчиваются</h2><p>Товары с низким остатком</p></div></div><div className="stock-list">{stock.slice(0,3).map(item => <div className="stock-item" key={item.productId}><div className="product-thumb"><Icon name="box"/></div><div className="stock-info"><strong>{item.name}</strong><span>{item.sku}</span><div className="stock-track"><i className="orange" style={{width: `${Math.min(100, item.quantity / Math.max(1,item.lowStockThreshold) * 100)}%`}}/></div></div><div className="stock-count"><strong>{item.quantity}</strong><span>шт.</span></div></div>)}{!stock.length && <div className="empty-state">Нет товаров с низким остатком</div>}</div></article></section>
        <section className="panel orders-panel"><div className="panel-head"><div><h2>Последние продажи</h2><p>{query ? `Найдено: ${filtered.length}` : 'Проведённые операции'}</p></div></div><div className="table-wrap"><table><thead><tr><th>ДОКУМЕНТ</th><th>ДАТА</th><th>ПРОДАВЕЦ</th><th>СУММА</th><th>СТАТУС</th><th/></tr></thead><tbody>{filtered.map(s => <tr key={s.id}><td><strong>{s.documentNumber}</strong></td><td className="muted">{dateTime.format(new Date(s.soldAt))}</td><td>№ {s.sellerId}</td><td><strong>{money.format(s.total)}</strong></td><td><span className="status">Проведена</span></td><td><button className="button row-button"><Icon name="chevron" size={16}/></button></td></tr>)}</tbody></table>{!filtered.length && <div className="empty-state table-empty">Продаж пока нет</div>}</div></section>
        </>}
      </div></main>
  </div>;
}
