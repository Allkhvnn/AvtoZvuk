import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, type Product, type PurchaseDocument, type PurchaseSummary, type Supplier } from './api';

type Line = { productId: number; quantity: number; unitCost: number };
const money = new Intl.NumberFormat('ru-KZ', { style: 'currency', currency: 'KZT', minimumFractionDigits: 0, maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat('ru-KZ', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export function PurchasesPage({ query }: { query: string }) {
  const [purchases, setPurchases] = useState<PurchaseSummary[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [open, setOpen] = useState(false);
  const [supplierId, setSupplierId] = useState<number | null>(null);
  const [selected, setSelected] = useState('');
  const [lines, setLines] = useState<Line[]>([]);
  const [newSupplier, setNewSupplier] = useState(false);
  const [supplierName, setSupplierName] = useState('');
  const [supplierPhone, setSupplierPhone] = useState('');
  const [detail, setDetail] = useState<PurchaseDocument | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function load() {
    try { const [docs, vendors, catalog] = await Promise.all([api.purchases(), api.suppliers(), api.products()]); setPurchases(docs); setSuppliers(vendors); setProducts(catalog.filter(product => product.active)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось загрузить поступления'); }
  }
  useEffect(() => { void load(); }, []);
  const productsById = useMemo(() => new Map(products.map(product => [product.id, product])), [products]);
  const filtered = purchases.filter(item => `${item.documentNumber} ${item.supplierName} ${item.total}`.toLowerCase().includes(query.toLowerCase()));
  const total = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0);
  const receivedUnits = purchases.reduce((sum, item) => sum + Number(item.itemCount), 0);

  function addLine() { const id = Number(selected); if (!id || lines.some(line => line.productId === id)) return; setLines(current => [...current, { productId: id, quantity: 1, unitCost: 0 }]); setSelected(''); }
  function updateLine(id: number, patch: Partial<Line>) { setLines(current => current.map(line => line.productId === id ? { ...line, ...patch } : line)); }
  function start() { setSupplierId(null); setLines([]); setSelected(''); setError(''); setOpen(true); }
  async function addSupplier() {
    if (!supplierName.trim()) return; setBusy(true); setError('');
    try { const created = await api.createSupplier(supplierName.trim(), supplierPhone.trim() || null); const supplier = { id: created.id, name: supplierName.trim(), phone: supplierPhone.trim() || null }; setSuppliers(current => [...current, supplier].sort((a,b) => a.name.localeCompare(b.name))); setSupplierId(created.id); setSupplierName(''); setSupplierPhone(''); setNewSupplier(false); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось создать поставщика'); }
    finally { setBusy(false); }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (!supplierId) return setError('Выберите поставщика');
    if (!lines.length) return setError('Добавьте хотя бы один товар');
    setBusy(true);
    try { const result = await api.createPurchase({ requestKey: crypto.randomUUID(), supplierId, items: lines }); setNotice(`Поступление №${result.id} проведено, остатки обновлены`); setOpen(false); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось провести поступление'); }
    finally { setBusy(false); }
  }
  async function showDetail(id: number) { try { setDetail(await api.purchase(id)); } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось открыть документ'); } }

  return <>
    <section className="sales-heading"><div><p className="eyebrow">РУЧНОЙ УЧЁТ ПОСТАВОК</p><h1>Поступления</h1><p>Заказ оформляется вне системы, здесь фиксируется фактически полученный товар.</p></div><button className="button primary" onClick={start}><span className="button-plus">+</span>Новое поступление</button></section>
    {notice && <div className="success-banner">{notice}<button onClick={() => setNotice('')}>×</button></div>}{error && !open && <div className="page-error">{error}</div>}
    <section className="sales-kpis"><div><span>Документов поступления</span><strong>{purchases.length}</strong></div><div><span>Позиций принято</span><strong>{receivedUnits}</strong></div><div><span>Сумма закупок</span><strong>{money.format(purchases.reduce((sum,item) => sum + Number(item.total), 0))}</strong></div></section>
    <section className="panel sales-list-panel"><div className="panel-head"><div><h2>История поступлений</h2><p>{query ? `Найдено: ${filtered.length}` : 'Проведённые документы'}</p></div></div><div className="table-wrap"><table><thead><tr><th>ДОКУМЕНТ</th><th>ДАТА</th><th>ПОСТАВЩИК</th><th>ПОЗИЦИЙ</th><th>СУММА</th><th>СТАТУС</th><th/></tr></thead><tbody>{filtered.map(item => <tr key={item.id} className="clickable-row" onClick={() => showDetail(item.id)}><td><strong>{item.documentNumber}</strong></td><td className="muted">{dateTime.format(new Date(item.purchasedAt))}</td><td>{item.supplierName}</td><td>{item.itemCount}</td><td><strong>{money.format(item.total)}</strong></td><td><span className="status">Принято</span></td><td><button className="button row-button">›</button></td></tr>)}</tbody></table>{!filtered.length && <div className="empty-state table-empty">Поступлений пока нет</div>}</div></section>

    {open && <div className="modal-backdrop sale-backdrop" onMouseDown={() => !busy && setOpen(false)}><form className="sale-modal purchase-modal" onSubmit={submit} onMouseDown={event => event.stopPropagation()}><div className="sale-modal-head"><div><p className="eyebrow">СКЛАДСКИЙ ДОКУМЕНТ</p><h2>Новое поступление</h2><p>Укажите только фактически полученные товары и закупочные цены.</p></div><button type="button" className="button modal-close" onClick={() => setOpen(false)}>×</button></div>{error && <div className="form-error product-form-error">{error}</div>}
      <div className="purchase-supplier"><div className="category-field"><label className="field">Поставщик<select value={supplierId ?? ''} onChange={e => setSupplierId(Number(e.target.value) || null)} required><option value="">Выберите поставщика</option>{suppliers.map(supplier => <option key={supplier.id} value={supplier.id}>{supplier.name}{supplier.phone ? ` · ${supplier.phone}` : ''}</option>)}</select></label><button type="button" className="add-category-link" onClick={() => setNewSupplier(value => !value)}>+ новый</button></div>{newSupplier && <div className="supplier-create"><input value={supplierName} onChange={e => setSupplierName(e.target.value)} placeholder="Название поставщика" maxLength={200}/><input value={supplierPhone} onChange={e => setSupplierPhone(e.target.value)} placeholder="Телефон, необязательно" maxLength={40}/><button type="button" onClick={addSupplier} disabled={busy}>Создать</button></div>}</div>
      <div className="sale-section"><div className="sale-section-title"><div><strong>Полученные товары</strong><span>{lines.length} позиций</span></div></div><div className="product-picker"><select value={selected} onChange={e => setSelected(e.target.value)}><option value="">Выберите товар из каталога</option>{products.filter(product => !lines.some(line => line.productId === product.id)).map(product => <option key={product.id} value={product.id}>{product.name} · {product.sku}</option>)}</select><button type="button" className="button secondary-button" onClick={addLine} disabled={!selected}>Добавить</button></div><div className="sale-lines">{lines.map(line => { const product = productsById.get(line.productId)!; return <div className="purchase-line" key={line.productId}><div className="sale-product"><strong>{product.name}</strong><span>{product.sku} · текущий остаток {product.quantity}</span></div><label>Количество<input type="number" min="1" max="1000000" value={line.quantity} onChange={e => updateLine(line.productId,{quantity:Math.max(1,Number(e.target.value))})}/></label><label>Закупочная цена, ₸<input type="number" min="0" step="0.01" value={line.unitCost} onChange={e => updateLine(line.productId,{unitCost:Math.max(0,Number(e.target.value))})}/></label><div className="line-total"><span>Сумма</span><strong>{money.format(line.quantity * line.unitCost)}</strong></div><button type="button" className="remove-line" onClick={() => setLines(current => current.filter(item => item.productId !== line.productId))}>×</button></div>; })}{!lines.length && <div className="sale-empty">Добавьте полученные товары</div>}</div></div>
      <div className="purchase-help"><strong>Что произойдёт после проведения?</strong><span>Для каждой позиции создастся отдельная FIFO-партия с указанной себестоимостью, а остаток товара увеличится.</span></div><div className="sale-footer"><div><span>Сумма поступления</span><strong>{money.format(total)}</strong></div><button className="button primary" disabled={busy || !lines.length}>{busy ? 'Проводим…' : 'Принять на склад'}</button></div>
    </form></div>}

    {detail && <div className="modal-backdrop" onMouseDown={() => setDetail(null)}><div className="purchase-detail" onMouseDown={event => event.stopPropagation()}><div className="sale-modal-head"><div><p className="eyebrow">ПРОВЕДЁННЫЙ ДОКУМЕНТ</p><h2>{detail.documentNumber}</h2><p>{detail.items.length} позиций · {money.format(detail.total)}</p></div><button className="button modal-close" onClick={() => setDetail(null)}>×</button></div><div className="detail-lines">{detail.items.map(item => <div key={item.id}><div><strong>{productsById.get(item.productId)?.name ?? `Товар №${item.productId}`}</strong><span>{item.quantity} шт. × {money.format(item.unitCost)}</span></div><strong>{money.format(item.lineTotal)}</strong></div>)}</div><div className="detail-total"><span>Итого</span><strong>{money.format(detail.total)}</strong></div></div></div>}
  </>;
}
