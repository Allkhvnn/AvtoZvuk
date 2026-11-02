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

  return <>
    <section className="sales-heading"><div><p className="eyebrow">КАТАЛОГ И ОСТАТКИ</p><h1>Товары</h1><p>Карточки товаров, цены и текущие остатки магазина.</p></div>{actor.role === 'OWNER' && <button className="button primary" onClick={create}><span className="button-plus">+</span>Добавить товар</button>}</section>
    {notice && <div className="success-banner">{notice}<button onClick={() => setNotice('')}>×</button></div>}
    {error && !open && <div className="page-error">{error}</div>}
    <section className="sales-kpis product-kpis"><div><span>Активных товаров</span><strong>{activeCount}</strong></div><div><span>Единиц на складе</span><strong>{totalUnits}</strong></div><div><span>Низкий остаток</span><strong className={lowCount ? 'danger-value' : ''}>{lowCount}</strong></div></section>
    <section className="panel products-panel"><div className="panel-head"><div><h2>Каталог</h2><p>{query ? `Найдено: ${filtered.length}` : `${products.length} карточек товаров`}</p></div><div className="catalog-legend"><span><i className="legend-dot available"/>В наличии</span><span><i className="legend-dot low"/>Заканчивается</span></div></div>
      <div className="table-wrap products-table"><table><thead><tr><th>ТОВАР</th><th>КАТЕГОРИЯ</th><th>АРТИКУЛ / ШТРИХКОД</th><th>ЦЕНА</th><th>ОСТАТОК</th><th>СОСТОЯНИЕ</th><th/></tr></thead><tbody>{filtered.map(product => <tr key={product.id} className={!product.active ? 'inactive-row' : ''}><td><div className="product-cell"><div className="catalog-thumb">AZ</div><div><strong>{product.name}</strong><span>ID {product.id}</span></div></div></td><td>{categoryNames.get(product.categoryId ?? -1) ?? 'Без категории'}</td><td><strong>{product.sku}</strong><span className="cell-secondary">{product.barcode || 'Штрихкод не указан'}</span></td><td><strong>{money.format(product.sellingPrice)}</strong></td><td><span className={`quantity-badge ${product.lowStock ? 'low' : ''}`}>{product.quantity} шт.</span></td><td><span className={`product-state ${!product.active ? 'disabled' : product.lowStock ? 'low' : 'active'}`}>{!product.active ? 'Отключён' : product.lowStock ? 'Заканчивается' : 'Активен'}</span></td><td>{actor.role === 'OWNER' && <div className="row-actions"><button className="button row-button" onClick={() => edit(product)} title="Редактировать">✎</button>{product.active && <button className="button row-button danger" onClick={() => deactivate(product)} title="Отключить">×</button>}</div>}</td></tr>)}</tbody></table>{loading && <div className="empty-state">Загружаем каталог…</div>}{!loading && !filtered.length && <div className="empty-state table-empty">Товары не найдены</div>}</div>
    </section>

    {open && <div className="modal-backdrop" onMouseDown={() => !busy && setOpen(false)}><form className="product-modal" onSubmit={submit} onMouseDown={event => event.stopPropagation()}><div className="sale-modal-head"><div><p className="eyebrow">{editing ? 'РЕДАКТИРОВАНИЕ' : 'НОВЫЙ ТОВАР'}</p><h2>{editing ? 'Карточка товара' : 'Добавить товар'}</h2><p>Цена продажи хранится отдельно от себестоимости поставок.</p></div><button type="button" className="button modal-close" onClick={() => setOpen(false)}>×</button></div>
      {error && <div className="form-error product-form-error">{error}</div>}
      <div className="product-form-grid"><label className="field wide">Название<input value={form.name} onChange={e => field('name', e.target.value)} maxLength={250} placeholder="Например, сабвуфер Ural Molot 12" autoFocus required/></label><label className="field">Артикул<input value={form.sku} onChange={e => field('sku', e.target.value)} maxLength={100} placeholder="URAL-MOLOT-12" required/></label><label className="field">Штрихкод<input value={form.barcode ?? ''} onChange={e => field('barcode', e.target.value || null)} maxLength={100} placeholder="Необязательно"/></label>
        <div className="category-field"><label className="field">Категория<select value={form.categoryId ?? ''} onChange={e => field('categoryId', Number(e.target.value) || null)}><option value="">Без категории</option>{categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label><button type="button" className="add-category-link" onClick={() => setShowCategory(value => !value)}>+ новая</button></div>
        {showCategory && <div className="new-category"><input value={categoryName} onChange={e => setCategoryName(e.target.value)} placeholder="Название категории" maxLength={150}/><button type="button" onClick={addCategory} disabled={busy}>Создать</button></div>}
        <label className="field">Цена продажи, ₸<input type="number" min="0" step="0.01" value={form.sellingPrice} onChange={e => field('sellingPrice', Math.max(0, Number(e.target.value)))} required/></label><label className="field">Порог низкого остатка<input type="number" min="0" max="1000000000" value={form.lowStockThreshold} onChange={e => field('lowStockThreshold', Math.max(0, Number(e.target.value)))} required/></label>
      </div><div className="product-form-note"><strong>Остаток не редактируется в карточке.</strong><span>После создания товара внесите начальный остаток или оформите поступление — так история склада останется корректной.</span></div><div className="sale-footer"><button type="button" className="button cancel-button" onClick={() => setOpen(false)}>Отмена</button><button className="button primary" disabled={busy}>{busy ? 'Сохраняем…' : editing ? 'Сохранить изменения' : 'Создать товар'}</button></div>
    </form></div>}
  </>;
}
