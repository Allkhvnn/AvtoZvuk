import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, type Actor, type Category, type Product, type ProductInput } from './api';

const money = new Intl.NumberFormat('ru-KZ', { style: 'currency', currency: 'KZT', maximumFractionDigits: 0 });
const emptyForm: ProductInput = { name: '', sku: '', barcode: null, categoryId: null, sellingPrice: 0, lowStockThreshold: 3 };

export function ProductsPage({ actor, query }: { actor: Actor; query: string }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [form, setForm] = useState<ProductInput>(emptyForm);
  const [editing, setEditing] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [categoryName, setCategoryName] = useState('');
  const [showCategory, setShowCategory] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [openingProduct, setOpeningProduct] = useState<Product | null>(null);
  const [openingQuantity, setOpeningQuantity] = useState(1);
  const [openingCost, setOpeningCost] = useState(0);
  const [openingReason, setOpeningReason] = useState('Начальный пересчёт остатков');

  async function load() {
    setLoading(true);
    try { const [catalog, groups] = await Promise.all([api.products(), api.categories()]); setProducts(catalog); setCategories(groups); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось загрузить товары'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  const categoryNames = useMemo(() => new Map(categories.map(item => [item.id, item.name])), [categories]);
  const filtered = products.filter(product => `${product.name} ${product.sku} ${product.barcode ?? ''} ${categoryNames.get(product.categoryId ?? -1) ?? ''}`.toLowerCase().includes(query.toLowerCase()));
  const activeCount = products.filter(product => product.active).length;
  const lowCount = products.filter(product => product.active && product.lowStock).length;
  const totalUnits = products.filter(product => product.active).reduce((sum, product) => sum + Number(product.quantity), 0);

  function create() { setEditing(null); setForm(emptyForm); setError(''); setOpen(true); }
  function edit(product: Product) {
    setEditing(product.id); setError(''); setForm({ name: product.name, sku: product.sku, barcode: product.barcode, categoryId: product.categoryId, sellingPrice: Number(product.sellingPrice), lowStockThreshold: product.lowStockThreshold }); setOpen(true);
  }
  function field<K extends keyof ProductInput>(name: K, value: ProductInput[K]) { setForm(current => ({ ...current, [name]: value })); }

  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      if (editing) await api.updateProduct(editing, form); else await api.createProduct(form);
      setNotice(editing ? 'Карточка товара обновлена' : 'Товар добавлен в каталог'); setOpen(false); await load();
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось сохранить товар'); }
    finally { setBusy(false); }
  }
  async function addCategory() {
    if (!categoryName.trim()) return;
    setBusy(true); setError('');
    try { const created = await api.createCategory(categoryName.trim()); const next = { id: created.id, name: categoryName.trim() }; setCategories(current => [...current, next].sort((a,b) => a.name.localeCompare(b.name))); field('categoryId', created.id); setCategoryName(''); setShowCategory(false); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось создать категорию'); }
    finally { setBusy(false); }
  }
  async function deactivate(product: Product) {
    if (!window.confirm(`Отключить товар «${product.name}»? История операций сохранится.`)) return;
    try { await api.deactivateProduct(product.id); setNotice('Товар отключён'); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось отключить товар'); }
  }
  function beginOpening(product: Product) { setOpeningProduct(product); setOpeningQuantity(1); setOpeningCost(0); setOpeningReason('Начальный пересчёт остатков'); setError(''); }
  async function saveOpening(event: FormEvent) {
    event.preventDefault(); if (!openingProduct) return; setBusy(true); setError('');
    try {
      await api.openingStock({ requestKey: crypto.randomUUID(), productId: openingProduct.id, quantity: openingQuantity, unitCost: openingCost, reason: openingReason });
      setNotice(`Начальный остаток для «${openingProduct.name}» внесён`); setOpeningProduct(null); await load();
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось внести начальный остаток'); }
    finally { setBusy(false); }
  }

  return <>
    <section className="sales-heading"><div><p className="eyebrow">КАТАЛОГ И ОСТАТКИ</p><h1>Товары</h1><p>Карточки товаров, цены и текущие остатки магазина.</p></div>{actor.role === 'OWNER' && <button className="button primary" onClick={create}><span className="button-plus">+</span>Добавить товар</button>}</section>
    {notice && <div className="success-banner">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    {error && !open && <div className="page-error">{error}</div>}
    <section className="sales-kpis product-kpis"><div><span>Активных товаров</span><strong>{activeCount}</strong></div><div><span>Единиц на складе</span><strong>{totalUnits}</strong></div><div><span>Низкий остаток</span><strong className={lowCount ? 'danger-value' : ''}>{lowCount}</strong></div></section>
    <section className="panel products-panel"><div className="panel-head"><div><h2>Каталог</h2><p>{query ? `Найдено: ${filtered.length}` : `${products.length} карточек товаров`}</p></div><div className="catalog-legend"><span><i className="legend-dot available"/>В наличии</span><span><i className="legend-dot low"/>Заканчивается</span></div></div>
      <div className="table-wrap products-table"><table><thead><tr><th>ТОВАР</th><th>КАТЕГОРИЯ</th><th>АРТИКУЛ / ШТРИХКОД</th><th>ЦЕНА</th><th>ОСТАТОК</th><th>СОСТОЯНИЕ</th><th/></tr></thead><tbody>{filtered.map(product => <tr key={product.id} className={!product.active ? 'inactive-row' : ''}><td><div className="product-cell"><div className="catalog-thumb">AZ</div><div><strong>{product.name}</strong><span>ID {product.id}</span></div></div></td><td>{categoryNames.get(product.categoryId ?? -1) ?? 'Без категории'}</td><td><strong>{product.sku}</strong><span className="cell-secondary">{product.barcode || 'Штрихкод не указан'}</span></td><td><strong>{money.format(product.sellingPrice)}</strong></td><td><span className={`quantity-badge ${product.lowStock ? 'low' : ''}`}>{product.quantity} шт.</span></td><td><span className={`product-state ${!product.active ? 'disabled' : product.lowStock ? 'low' : 'active'}`}>{!product.active ? 'Отключён' : product.lowStock ? 'Заканчивается' : 'Активен'}</span></td><td>{actor.role === 'OWNER' && <div className="row-actions">{product.active && product.quantity === 0 && <button className="button row-button stock-action" onClick={() => beginOpening(product)} title="Внести начальный остаток">+</button>}<button className="button row-button" onClick={() => edit(product)} title="Редактировать">✎</button>{product.active && <button className="button row-button danger" onClick={() => deactivate(product)} title="Отключить">×</button>}</div>}</td></tr>)}</tbody></table>{loading && <div className="empty-state">Загружаем каталог…</div>}{!loading && !filtered.length && <div className="empty-state table-empty">Товары не найдены</div>}</div>
    </section>

    {open && <div className="modal-backdrop" onMouseDown={() => !busy && setOpen(false)}><form className="product-modal" onSubmit={submit} onMouseDown={event => event.stopPropagation()}><div className="sale-modal-head"><div><p className="eyebrow">{editing ? 'РЕДАКТИРОВАНИЕ' : 'НОВЫЙ ТОВАР'}</p><h2>{editing ? 'Карточка товара' : 'Добавить товар'}</h2><p>Цена продажи хранится отдельно от себестоимости поставок.</p></div><button type="button" className="button modal-close" onClick={() => setOpen(false)}>×</button></div>
      {error && <div className="form-error product-form-error">{error}</div>}
      <div className="product-form-grid"><label className="field wide">Название<input value={form.name} onChange={e => field('name', e.target.value)} maxLength={250} placeholder="Например, сабвуфер Ural Molot 12" autoFocus required/></label><label className="field">Артикул<input value={form.sku} onChange={e => field('sku', e.target.value)} maxLength={100} placeholder="URAL-MOLOT-12" required/></label><label className="field">Штрихкод<input value={form.barcode ?? ''} onChange={e => field('barcode', e.target.value || null)} maxLength={100} placeholder="Необязательно"/></label>
        <div className="category-field"><label className="field">Категория<select value={form.categoryId ?? ''} onChange={e => field('categoryId', Number(e.target.value) || null)}><option value="">Без категории</option>{categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label><button type="button" className="add-category-link" onClick={() => setShowCategory(value => !value)}>+ новая</button></div>
        {showCategory && <div className="new-category"><input value={categoryName} onChange={e => setCategoryName(e.target.value)} placeholder="Название категории" maxLength={150}/><button type="button" onClick={addCategory} disabled={busy}>Создать</button></div>}
        <label className="field">Цена продажи, ₸<input type="number" min="0" step="0.01" value={form.sellingPrice} onChange={e => field('sellingPrice', Math.max(0, Number(e.target.value)))} required/></label><label className="field">Порог низкого остатка<input type="number" min="0" max="1000000000" value={form.lowStockThreshold} onChange={e => field('lowStockThreshold', Math.max(0, Number(e.target.value)))} required/></label>
      </div><div className="product-form-note"><strong>Остаток не редактируется в карточке.</strong><span>После создания товара внесите начальный остаток или оформите поступление — так история склада останется корректной.</span></div><div className="sale-footer"><button type="button" className="button cancel-button" onClick={() => setOpen(false)}>Отмена</button><button className="button primary" disabled={busy}>{busy ? 'Сохраняем…' : editing ? 'Сохранить изменения' : 'Создать товар'}</button></div>
    </form></div>}
    {openingProduct && <div className="modal-backdrop" onMouseDown={() => !busy && setOpeningProduct(null)}><form className="opening-modal" onSubmit={saveOpening} onMouseDown={event => event.stopPropagation()}><div className="sale-modal-head"><div><p className="eyebrow">СКЛАДСКАЯ ОПЕРАЦИЯ</p><h2>Начальный остаток</h2><p>{openingProduct.name} · {openingProduct.sku}</p></div><button type="button" className="button modal-close" onClick={() => setOpeningProduct(null)}>×</button></div>{error && <div className="form-error product-form-error">{error}</div>}<div className="opening-form"><div className="opening-product-summary"><div className="catalog-thumb">AZ</div><div><strong>{openingProduct.name}</strong><span>Цена продажи: {money.format(openingProduct.sellingPrice)}</span></div></div><div className="opening-fields"><label className="field">Количество<input type="number" min="1" max="1000000" value={openingQuantity} onChange={e => setOpeningQuantity(Math.max(1, Number(e.target.value)))} required autoFocus/></label><label className="field">Себестоимость единицы, ₸<input type="number" min="0" step="0.01" value={openingCost} onChange={e => setOpeningCost(Math.max(0, Number(e.target.value)))} required/></label></div><label className="field">Причина<textarea value={openingReason} onChange={e => setOpeningReason(e.target.value)} maxLength={2000} required/></label><div className="opening-total"><span>Стоимость начального запаса</span><strong>{money.format(openingQuantity * openingCost)}</strong></div><div className="product-form-note opening-note"><strong>Операция попадёт в историю склада.</strong><span>После проведения количество нельзя исправить редактированием карточки — только инвентаризацией или списанием.</span></div></div><div className="sale-footer"><button type="button" className="button cancel-button" onClick={() => setOpeningProduct(null)}>Отмена</button><button className="button primary" disabled={busy}>{busy ? 'Проводим…' : 'Внести остаток'}</button></div></form></div>}
  </>;
}
