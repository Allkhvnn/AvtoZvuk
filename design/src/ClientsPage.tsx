import { useEffect, useState, type FormEvent } from 'react';
import { api, type Customer, type CustomerDetail, type VehicleInput } from './api';

const money = new Intl.NumberFormat('ru-KZ', { style: 'currency', currency: 'KZT', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('ru-KZ', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const emptyVehicle = (): VehicleInput => ({ make: '', model: null, productionYear: null, plateNumber: null, notes: null });

export function ClientsPage({ query }: { query: string }) {
  const [clients, setClients] = useState<Customer[]>([]);
  const [editing, setEditing] = useState<CustomerDetail | null | undefined>(undefined);
  const [detail, setDetail] = useState<CustomerDetail | null>(null);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [notes, setNotes] = useState('');
  const [vehicles, setVehicles] = useState<VehicleInput[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function load() {
    try { setClients(await api.clients()); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось загрузить клиентов'); }
  }
  useEffect(() => { void load(); }, []);

  const normalized = query.trim().toLocaleLowerCase('ru');
  const filtered = clients.filter(client => !normalized ||
    `${client.fullName} ${client.phone} ${client.vehicles}`.toLocaleLowerCase('ru').includes(normalized));

  function create() {
    setEditing(null); setFullName(''); setPhone(''); setNotes(''); setVehicles([]); setError('');
  }
  async function edit(id: number) {
    setError('');
    try {
      const client = await api.client(id); setEditing(client); setDetail(null);
      setFullName(client.fullName); setPhone(client.phone); setNotes(client.notes ?? '');
      setVehicles(client.vehicles.map(({ make, model, productionYear, plateNumber, notes: vehicleNotes }) =>
        ({ make, model, productionYear, plateNumber, notes: vehicleNotes })));
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось открыть клиента'); }
  }
  async function show(id: number) {
    setError('');
    try { setDetail(await api.client(id)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось открыть клиента'); }
  }
  function updateVehicle(index: number, patch: Partial<VehicleInput>) {
    setVehicles(current => current.map((vehicle, i) => i === index ? { ...vehicle, ...patch } : vehicle));
  }
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const cleanVehicles = vehicles.map(vehicle => ({
        make: vehicle.make.trim(), model: vehicle.model?.trim() || null,
        productionYear: vehicle.productionYear || null, plateNumber: vehicle.plateNumber?.trim() || null,
        notes: vehicle.notes?.trim() || null,
      }));
      if (editing) await api.updateClient(editing.id, fullName, phone, notes.trim() || null, cleanVehicles);
      else await api.createClient(fullName, phone, notes.trim() || null, cleanVehicles);
      setNotice(editing ? 'Карточка клиента обновлена' : 'Клиент добавлен'); setEditing(undefined); await load();
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось сохранить клиента'); }
    finally { setBusy(false); }
  }
  async function toggle(client: CustomerDetail) {
    const reason = window.prompt(`${client.active ? 'Деактивировать' : 'Активировать'} клиента «${client.fullName}». Укажите причину:`);
    if (!reason) return;
    try { await api.clientStatus(client.id, !client.active, reason); setNotice(client.active ? 'Клиент деактивирован' : 'Клиент активирован'); setDetail(null); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось изменить статус'); }
  }

  return <>
    <section className="sales-heading"><div><p className="eyebrow">КЛИЕНТСКАЯ БАЗА</p><h1>Клиенты</h1><p>Контакты, автомобили и история покупок.</p></div><button className="button primary" onClick={create}><span className="button-plus">+</span>Добавить клиента</button></section>
    {notice && <div className="success-banner">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    {error && editing === undefined && <div className="page-error">{error}</div>}
    <section className="sales-kpis"><div><span>Всего клиентов</span><strong>{clients.length}</strong></div><div><span>Активных</span><strong>{clients.filter(c => c.active).length}</strong></div><div><span>Продаж клиентам</span><strong>{clients.reduce((sum, c) => sum + c.purchaseCount, 0)}</strong></div></section>
    <section className="panel clients-panel"><div className="panel-head"><div><h2>Клиентская база</h2><p>{query ? `Найдено: ${filtered.length}` : 'Нажмите на карточку, чтобы увидеть историю'}</p></div></div><div className="client-grid">
      {filtered.map(client => <article className={`client-card ${!client.active ? 'disabled-card' : ''}`} key={client.id} onClick={() => void show(client.id)}>
        <div className="client-card-head"><div className="client-avatar">{client.fullName.split(' ').map(part => part[0]).join('').slice(0, 2).toUpperCase()}</div><div><strong>{client.fullName}</strong><span>{client.phone}</span></div><span className={`product-state ${client.active ? 'active' : 'disabled'}`}>{client.active ? 'Активен' : 'Неактивен'}</span></div>
        <div className="client-vehicle"><span>Автомобили</span><strong>{client.vehicles || 'Не указаны'}</strong></div>
        <div className="client-stats"><div><span>Покупок</span><strong>{client.purchaseCount}</strong></div><div><span>Сумма покупок</span><strong>{money.format(client.totalSpent)}</strong></div></div>
      </article>)}
      {!filtered.length && <div className="empty-state">Клиенты не найдены</div>}
    </div></section>

    {editing !== undefined && <div className="modal-backdrop sale-backdrop" onMouseDown={() => !busy && setEditing(undefined)}><form className="product-modal client-modal" onSubmit={save} onMouseDown={e => e.stopPropagation()}>
      <div className="sale-modal-head"><div><p className="eyebrow">{editing ? 'РЕДАКТИРОВАНИЕ' : 'НОВЫЙ КЛИЕНТ'}</p><h2>{editing ? 'Карточка клиента' : 'Добавить клиента'}</h2><p>Контактные данные и автомобили клиента.</p></div><button type="button" className="button modal-close" onClick={() => setEditing(undefined)}>×</button></div>
      {error && <div className="form-error product-form-error">{error}</div>}
      <div className="product-form-grid"><label className="field">ФИО<input value={fullName} onChange={e => setFullName(e.target.value)} maxLength={200} required autoFocus /></label><label className="field">Телефон<input value={phone} onChange={e => setPhone(e.target.value)} maxLength={40} required /></label><label className="field wide">Заметка<textarea value={notes} onChange={e => setNotes(e.target.value)} maxLength={2000} placeholder="Предпочтения, особенности автомобиля или договорённости" /></label></div>
      <div className="client-vehicles-editor"><div className="sale-section-title"><div><strong>Автомобили</strong><span>{vehicles.length} добавлено</span></div><button type="button" onClick={() => setVehicles(current => [...current, emptyVehicle()])}>+ Добавить автомобиль</button></div>
        {vehicles.map((vehicle, index) => <div className="vehicle-row" key={index}><label>Марка<input value={vehicle.make} onChange={e => updateVehicle(index, { make: e.target.value })} required /></label><label>Модель<input value={vehicle.model ?? ''} onChange={e => updateVehicle(index, { model: e.target.value })} /></label><label>Год<input type="number" min="1900" max="2100" value={vehicle.productionYear ?? ''} onChange={e => updateVehicle(index, { productionYear: Number(e.target.value) || null })} /></label><label>Госномер<input value={vehicle.plateNumber ?? ''} onChange={e => updateVehicle(index, { plateNumber: e.target.value })} /></label><button type="button" className="remove-line" onClick={() => setVehicles(current => current.filter((_, i) => i !== index))}>×</button></div>)}
        {!vehicles.length && <div className="sale-empty compact">Автомобиль можно добавить сейчас или позже</div>}
      </div>
      <div className="sale-footer"><button type="button" className="button cancel-button" onClick={() => setEditing(undefined)}>Отмена</button><button className="button primary" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить клиента'}</button></div>
    </form></div>}

    {detail && <div className="modal-backdrop sale-backdrop" onMouseDown={() => setDetail(null)}><div className="client-detail" onMouseDown={e => e.stopPropagation()}>
      <div className="sale-modal-head"><div><p className="eyebrow">КАРТОЧКА КЛИЕНТА</p><h2>{detail.fullName}</h2><p>{detail.phone}</p></div><button className="button modal-close" onClick={() => setDetail(null)}>×</button></div>
      <div className="client-detail-body"><section><div className="client-detail-actions"><button onClick={() => void edit(detail.id)}>Редактировать</button><button className="danger-link" onClick={() => void toggle(detail)}>{detail.active ? 'Деактивировать' : 'Активировать'}</button></div>{detail.notes && <div className="client-note"><span>Заметка</span><p>{detail.notes}</p></div>}<h3>Автомобили</h3><div className="detail-vehicles">{detail.vehicles.map(vehicle => <div key={vehicle.id}><strong>{vehicle.make} {vehicle.model}</strong><span>{[vehicle.productionYear, vehicle.plateNumber].filter(Boolean).join(' · ') || 'Без дополнительных данных'}</span></div>)}{!detail.vehicles.length && <div className="empty-state">Автомобили не указаны</div>}</div></section>
        <section><h3>История покупок</h3><div className="client-sales">{detail.sales.map(sale => <div key={sale.id}><div><strong>{sale.documentNumber}</strong><span>{dateTime.format(new Date(sale.soldAt))} · {sale.sellerName}</span></div><strong>{money.format(sale.total)}</strong></div>)}{!detail.sales.length && <div className="empty-state">Покупок пока нет</div>}</div></section></div>
    </div></div>}
  </>;
}
