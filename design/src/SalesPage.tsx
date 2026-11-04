import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, type Actor, type Employee, type Product, type Sale, type SaleDocument } from './api';

type Line = { productId: number; quantity: number; discountAmount: number };
type ReturnLine = { saleItemId: number; quantity: number; restock: boolean };
type PaymentMode = 'CASH' | 'KASPI_PAY' | 'MIXED';

const money = new Intl.NumberFormat('ru-KZ', { style: 'currency', currency: 'KZT', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('ru-KZ', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
const roundMoney = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100;

export function SalesPage({ actor, sales, query, createRequest, onCreated }: { actor: Actor; sales: Sale[]; query: string; createRequest: number; onCreated: () => Promise<void> }) {
  const [open, setOpen] = useState(false);
  const [products, setProducts] = useState<Product[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [sellerId, setSellerId] = useState<number | null>(actor.employeeId);
  const [lines, setLines] = useState<Line[]>([]);
  const [selected, setSelected] = useState('');
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [cash, setCash] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [detail, setDetail] = useState<SaleDocument | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [returnLines, setReturnLines] = useState<ReturnLine[]>([]);
  const [returnReason, setReturnReason] = useState('');
  const [returnPayment, setReturnPayment] = useState<PaymentMode>('CASH');
  const [returnCash, setReturnCash] = useState(0);

  useEffect(() => { if (createRequest > 0) setOpen(true); }, [createRequest]);

  useEffect(() => {
    if (!open) return;
    Promise.all([api.products(), actor.role === 'OWNER' ? api.employees() : Promise.resolve([])])
      .then(([catalog, staff]) => { setProducts(catalog.filter(p => p.active && p.quantity > 0)); setEmployees(staff.filter(e => e.active)); })
      .catch(e => setError(e.message));
  }, [open, actor.role]);

  const productById = useMemo(() => new Map(products.map(p => [p.id, p])), [products]);
  const total = roundMoney(lines.reduce((sum, line) => {
    const product = productById.get(line.productId);
    return sum + (product ? product.sellingPrice * line.quantity - line.discountAmount : 0);
  }, 0));
  const filtered = sales.filter(s => `${s.documentNumber} ${s.total} ${s.sellerId}`.toLowerCase().includes(query.toLowerCase()));
  const detailItems = useMemo(() => new Map(detail?.items.map(item => [item.id, item]) ?? []), [detail]);
  const returnTotal = roundMoney(returnLines.reduce((sum, line) => {
    const item = detailItems.get(line.saleItemId); if (!item || line.quantity <= 0) return sum;
    const cumulative = roundMoney(Number(item.lineTotal) * (Number(item.returnedQuantity) + line.quantity) / item.quantity);
    return sum + roundMoney(cumulative - Number(item.returnedAmount));
  }, 0));

  function addLine() {
    const id = Number(selected); if (!id || lines.some(line => line.productId === id)) return;
    setLines(current => [...current, { productId: id, quantity: 1, discountAmount: 0 }]); setSelected('');
  }
  function updateLine(id: number, patch: Partial<Line>) { setLines(current => current.map(line => line.productId === id ? { ...line, ...patch } : line)); }
  function reset() { setLines([]); setSelected(''); setPaymentMode('CASH'); setCash(0); setError(''); }
  async function openDetail(id: number) {
    setDetailLoading(true); setError('');
    try { const value = await api.sale(id); setDetail(value); setReturnLines([]); setReturnReason(''); setReturnPayment('CASH'); setReturnCash(0); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось открыть продажу'); }
    finally { setDetailLoading(false); }
  }
  function setReturnItem(saleItemId:number, quantity:number, restock?:boolean) {
    const item=detailItems.get(saleItemId); if(!item)return; const safe=Math.max(0,Math.min(item.availableQuantity,quantity));
    setReturnLines(current=>{const old=current.find(line=>line.saleItemId===saleItemId);if(safe===0)return current.filter(line=>line.saleItemId!==saleItemId);if(old)return current.map(line=>line.saleItemId===saleItemId?{...line,quantity:safe,restock:restock??line.restock}:line);return [...current,{saleItemId,quantity:safe,restock:restock??true}];});
  }
  async function submitReturn(event:FormEvent){event.preventDefault();if(!detail)return;if(!returnLines.length)return setError('Укажите количество хотя бы для одной позиции');if(!returnReason.trim())return setError('Укажите причину возврата');const kaspi=roundMoney(returnTotal-returnCash);if(returnPayment==='MIXED'&&(returnCash<=0||kaspi<=0))return setError('Для смешанного возврата обе суммы должны быть больше нуля');setBusy(true);setError('');try{const payments=returnTotal===0?[]:returnPayment==='MIXED'?[{method:'CASH' as const,amount:roundMoney(returnCash)},{method:'KASPI_PAY' as const,amount:kaspi}]:[{method:returnPayment as 'CASH'|'KASPI_PAY',amount:returnTotal}];const result=await api.createReturn({requestKey:crypto.randomUUID(),saleId:detail.id,reason:returnReason.trim(),items:returnLines,payments});setSuccess(`Возврат №${result.id} успешно проведён`);await onCreated();setDetail(await api.sale(detail.id));setReturnLines([]);setReturnReason('');}catch(e){setError(e instanceof Error?e.message:'Не удалось провести возврат');}finally{setBusy(false);}}

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setSuccess('');
    if (!lines.length) return setError('Добавьте хотя бы один товар');
    if (actor.role === 'OWNER' && !sellerId) return setError('Выберите продавца');
    const kaspi = roundMoney(total - cash);
    if (paymentMode === 'MIXED' && (cash <= 0 || kaspi <= 0)) return setError('Для смешанной оплаты обе суммы должны быть больше нуля');
    setBusy(true);
    try {
      const result = await api.createSale({
        requestKey: crypto.randomUUID(), sellerId: actor.role === 'OWNER' ? sellerId : null,
        items: lines.map(line => ({ productId: line.productId, quantity: line.quantity, unitPrice: productById.get(line.productId)!.sellingPrice, discountAmount: line.discountAmount })),
        payments: paymentMode === 'MIXED'
          ? [{ method: 'CASH', amount: roundMoney(cash) }, { method: 'KASPI_PAY', amount: kaspi }]
          : [{ method: paymentMode, amount: total }],
      });
      setSuccess(`Продажа №${result.id} успешно проведена`); await onCreated(); reset(); setOpen(false);
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось провести продажу'); }
    finally { setBusy(false); }
  }

  return <>
    <section className="sales-heading"><div><p className="eyebrow">ЕЖЕДНЕВНЫЕ ОПЕРАЦИИ</p><h1>Продажи</h1><p>Оформление и история проведённых продаж.</p></div><button className="button primary" onClick={() => { setOpen(true); setSuccess(''); }}><span className="button-plus">+</span>Новая продажа</button></section>
    {success && <div className="success-banner">{success}</div>}
    <section className="sales-kpis"><div><span>Показано операций</span><strong>{filtered.length}</strong></div><div><span>Сумма в списке</span><strong>{money.format(filtered.reduce((sum, sale) => sum + Number(sale.total), 0))}</strong></div><div><span>Текущая роль</span><strong>{actor.role === 'OWNER' ? 'Владелец' : 'Продавец'}</strong></div></section>
    <section className="panel sales-list-panel"><div className="panel-head"><div><h2>История продаж</h2><p>{query ? `Результаты поиска: ${filtered.length}` : 'Сначала отображаются последние операции'}</p></div></div><div className="table-wrap"><table><thead><tr><th>ДОКУМЕНТ</th><th>ДАТА</th><th>ПРОДАВЕЦ</th><th>СУММА</th><th>СТАТУС</th><th></th></tr></thead><tbody>{filtered.map(sale => <tr className="clickable-row" onClick={()=>void openDetail(sale.id)} key={sale.id}><td><strong>{sale.documentNumber}</strong></td><td className="muted">{dateTime.format(new Date(sale.soldAt))}</td><td>№ {sale.sellerId}</td><td><strong>{money.format(sale.total)}</strong></td><td><span className="status">Проведена</span></td><td><button className="row-button" disabled={detailLoading}>Открыть</button></td></tr>)}</tbody></table>{!filtered.length && <div className="empty-state table-empty">Продажи не найдены</div>}</div></section>

    {open && <div className="modal-backdrop sale-backdrop" onMouseDown={() => !busy && setOpen(false)}><form className="sale-modal" onSubmit={submit} onMouseDown={event => event.stopPropagation()}>
      <div className="sale-modal-head"><div><p className="eyebrow">НОВАЯ ОПЕРАЦИЯ</p><h2>Оформление продажи</h2><p>Остаток будет списан после подтверждения.</p></div><button type="button" className="button modal-close" onClick={() => setOpen(false)} aria-label="Закрыть">×</button></div>
      {error && <div className="form-error" role="alert">{error}</div>}
      {actor.role === 'OWNER' && <label className="field">Продавец<select value={sellerId ?? ''} onChange={e => setSellerId(Number(e.target.value) || null)} required><option value="">Выберите сотрудника</option>{employees.map(employee => <option key={employee.id} value={employee.id}>{employee.fullName}</option>)}</select></label>}
      <div className="sale-section"><div className="sale-section-title"><div><strong>Товары</strong><span>{lines.length} позиций</span></div></div><div className="product-picker"><select value={selected} onChange={e => setSelected(e.target.value)}><option value="">Найти товар по названию или артикулу</option>{products.filter(p => !lines.some(line => line.productId === p.id)).map(product => <option key={product.id} value={product.id}>{product.name} · {product.sku} · остаток {product.quantity}</option>)}</select><button type="button" className="button secondary-button" onClick={addLine} disabled={!selected}>Добавить</button></div>
        <div className="sale-lines">{lines.map(line => { const product = productById.get(line.productId)!; const maxDiscount = product.sellingPrice * line.quantity; return <div className="sale-line" key={line.productId}><div className="sale-product"><strong>{product.name}</strong><span>{product.sku} · {money.format(product.sellingPrice)} · доступно {product.quantity}</span></div><label>Кол-во<input type="number" min="1" max={product.quantity} value={line.quantity} onChange={e => updateLine(line.productId, { quantity: Math.max(1, Math.min(product.quantity, Number(e.target.value))) })}/></label><label>Скидка<input type="number" min="0" max={maxDiscount} step="0.01" value={line.discountAmount} onChange={e => updateLine(line.productId, { discountAmount: Math.min(maxDiscount, Math.max(0, Number(e.target.value))) })}/></label><div className="line-total"><span>Итого</span><strong>{money.format(maxDiscount - line.discountAmount)}</strong></div><button type="button" className="remove-line" onClick={() => setLines(current => current.filter(item => item.productId !== line.productId))}>×</button></div>; })}{!lines.length && <div className="sale-empty">Выберите первый товар из списка</div>}</div>
      </div>
      <div className="sale-section"><div className="sale-section-title"><div><strong>Оплата</strong><span>Сумма способов оплаты должна совпасть с итогом</span></div></div><div className="payment-tabs">{([['CASH','Наличные'],['KASPI_PAY','Kaspi Pay'],['MIXED','Смешанная']] as const).map(([value,label]) => <button type="button" className={paymentMode === value ? 'active' : ''} onClick={() => { setPaymentMode(value); if (value === 'MIXED') setCash(Math.floor(total / 2)); }} key={value}>{label}</button>)}</div>{paymentMode === 'MIXED' && <div className="mixed-payment"><label className="field">Наличными<input type="number" min="0.01" max={Math.max(.01,total-.01)} step="0.01" value={cash} onChange={e => setCash(Number(e.target.value))}/></label><div><span>Kaspi Pay</span><strong>{money.format(Math.max(0,total-cash))}</strong></div></div>}</div>
      <div className="sale-footer"><div><span>К оплате</span><strong>{money.format(total)}</strong></div><button type="submit" className="button primary" disabled={busy || total <= 0}>{busy ? 'Проводим…' : 'Провести продажу'}</button></div>
    </form></div>}
    {detail&&<div className="modal-backdrop sale-backdrop" onMouseDown={()=>!busy&&setDetail(null)}><div className="sale-modal return-modal" onMouseDown={e=>e.stopPropagation()}><div className="sale-modal-head"><div><p className="eyebrow">ПРОВЕДЁННАЯ ПРОДАЖА</p><h2>{detail.documentNumber}</h2><p>Итого {money.format(detail.total)} · бонус {money.format(detail.bonus)}</p></div><button className="button modal-close" onClick={()=>setDetail(null)}>×</button></div>{error&&<div className="form-error product-form-error">{error}</div>}<section className="sale-section"><div className="sale-section-title"><div><strong>Состав продажи</strong><span>{detail.items.length} позиций</span></div></div><div className="return-items">{detail.items.map(item=><div className="return-item" key={item.id}><div><strong>{item.productName}</strong><span>{item.productSku} · {money.format(item.unitPrice)} × {item.quantity}</span></div><div><span>Скидка</span><strong>{money.format(item.discountAmount)}</strong></div><div><span>Сумма</span><strong>{money.format(item.lineTotal)}</strong></div><div><span>Возвращено</span><strong>{item.returnedQuantity} из {item.quantity}</strong></div></div>)}</div><div className="sale-payments">{detail.payments.map(p=><span key={p.method}>{p.method==='CASH'?'Наличные':'Kaspi Pay'}: <strong>{money.format(p.amount)}</strong></span>)}</div></section>
      {actor.role==='OWNER'&&detail.items.some(item=>item.availableQuantity>0)&&<form onSubmit={submitReturn}><section className="sale-section"><div className="sale-section-title"><div><strong>Оформить возврат</strong><span>Выберите количество возвращаемых товаров</span></div></div><div className="return-editor">{detail.items.filter(item=>item.availableQuantity>0).map(item=>{const line=returnLines.find(x=>x.saleItemId===item.id);return <div className="return-edit-row" key={item.id}><div><strong>{item.productName}</strong><span>Доступно к возврату: {item.availableQuantity}</span></div><label>Количество<input type="number" min="0" max={item.availableQuantity} value={line?.quantity??0} onChange={e=>setReturnItem(item.id,Number(e.target.value))}/></label><label className="restock-check"><input type="checkbox" checked={line?.restock??true} disabled={!line} onChange={e=>setReturnItem(item.id,line?.quantity??0,e.target.checked)}/><span>Вернуть на склад</span></label></div>})}</div><label className="field return-reason">Причина возврата<textarea value={returnReason} onChange={e=>setReturnReason(e.target.value)} maxLength={2000} placeholder="Например: не подошёл товар" required/></label></section>{returnTotal>0&&<section className="sale-section"><div className="sale-section-title"><div><strong>Возврат денег</strong><span>Сумма рассчитывается по исходной скидке</span></div></div><div className="payment-tabs">{([['CASH','Наличные'],['KASPI_PAY','Kaspi Pay'],['MIXED','Смешанный']] as const).map(([value,label])=><button type="button" className={returnPayment===value?'active':''} onClick={()=>{setReturnPayment(value);if(value==='MIXED')setReturnCash(Math.floor(returnTotal/2));}} key={value}>{label}</button>)}</div>{returnPayment==='MIXED'&&<div className="mixed-payment"><label className="field">Наличными<input type="number" min="0.01" max={Math.max(.01,returnTotal-.01)} step="0.01" value={returnCash} onChange={e=>setReturnCash(Number(e.target.value))}/></label><div><span>Kaspi Pay</span><strong>{money.format(Math.max(0,returnTotal-returnCash))}</strong></div></div>}</section>}<div className="sale-footer"><div><span>К возврату</span><strong>{money.format(returnTotal)}</strong></div><button className="button primary" disabled={busy||!returnLines.length}>{busy?'Проводим…':'Провести возврат'}</button></div></form>}{actor.role==='OWNER'&&!detail.items.some(item=>item.availableQuantity>0)&&<div className="return-complete">Все товары из этой продажи уже возвращены.</div>}{actor.role==='SELLER'&&<div className="return-complete">Возвраты доступны владельцу магазина.</div>}</div></div>}
  </>;
}
