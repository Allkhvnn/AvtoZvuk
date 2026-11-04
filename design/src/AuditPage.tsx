import { useEffect, useMemo, useState } from 'react';
import { api, type AuditLog } from './api';

const dateTime = new Intl.DateTimeFormat('ru-KZ', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const actionNames: Record<string, string> = {
  SALE_POST: 'Продажа проведена', RETURN_POST: 'Возврат проведён', PURCHASE_POST: 'Поступление принято',
  INVENTORY_POST: 'Инвентаризация проведена', STOCK_WRITE_OFF: 'Товар списан', OPENING_STOCK: 'Начальный остаток внесён',
  PRODUCT_CREATE: 'Товар создан', PRODUCT_UPDATE: 'Товар изменён', PRODUCT_UPDATE_BEFORE: 'Состояние товара до изменения',
  PRODUCT_DEACTIVATE: 'Товар деактивирован', PRODUCT_DEACTIVATE_BEFORE: 'Состояние товара до деактивации',
  CATEGORY_CREATE: 'Категория создана', SUPPLIER_CREATE: 'Поставщик создан', EMPLOYEE_CREATE: 'Сотрудник создан',
  EMPLOYEE_UPDATE: 'Сотрудник изменён', EMPLOYEE_ACTIVATE: 'Сотрудник активирован', EMPLOYEE_DEACTIVATE: 'Сотрудник деактивирован',
  USER_CREATE: 'Учётная запись создана', USER_ACTIVATE: 'Учётная запись активирована', USER_DEACTIVATE: 'Учётная запись деактивирована',
  EXPENSE_CATEGORY_CREATE: 'Категория расходов создана', EXPENSE_CREATE: 'Расход зарегистрирован', EXPENSE_REVERSE: 'Расход отменён',
  BONUS_ADJUST: 'Бонус скорректирован', STAFF_PAYMENT_CREATE: 'Выплата зарегистрирована', STAFF_PAYMENT_REVERSE: 'Выплата отменена',
  OWNER_BOOTSTRAP: 'Владелец системы создан',
};

const entityNames: Record<string, string> = {
  Sale: 'Продажа', SaleReturn: 'Возврат', Purchase: 'Поступление', StockAdjustment: 'Складская операция',
  Product: 'Товар', Category: 'Категория', Supplier: 'Поставщик', Employee: 'Сотрудник', User: 'Пользователь',
  Expense: 'Расход', ExpenseCategory: 'Категория расходов', BonusEntry: 'Начисление бонуса', SalaryPayment: 'Выплата',
};

export function AuditPage({ query }: { query: string }) {
  const [entries, setEntries] = useState<AuditLog[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => { api.auditLog().then(setEntries).catch(e => setError(e.message)).finally(() => setLoading(false)); }, []);
  const filtered = useMemo(() => {
    const value = query.trim().toLocaleLowerCase('ru');
    if (!value) return entries;
    return entries.filter(entry => `${actionNames[entry.action] ?? entry.action} ${entityNames[entry.entityType] ?? entry.entityType} ${entry.entityId} ${entry.username} ${entry.reason ?? ''}`.toLocaleLowerCase('ru').includes(value));
  }, [entries, query]);

  return <>
    <section className="sales-heading"><div><p className="eyebrow">КОНТРОЛЬ И БЕЗОПАСНОСТЬ</p><h1>История действий</h1><p>Важные операции пользователей сохраняются без возможности бесследного удаления.</p></div></section>
    <section className="sales-kpis"><div><span>Загружено событий</span><strong>{entries.length}</strong></div><div><span>Пользователей в истории</span><strong>{new Set(entries.map(entry => entry.username)).size}</strong></div><div><span>Результаты поиска</span><strong>{filtered.length}</strong></div></section>
    <section className="panel sales-list-panel"><div className="panel-head"><div><h2>Журнал операций</h2><p>Сначала отображаются последние действия</p></div></div>
      {error && <div className="page-error">Не удалось загрузить историю: {error}</div>}
      <div className="table-wrap"><table className="audit-table"><thead><tr><th>ДАТА</th><th>ПОЛЬЗОВАТЕЛЬ</th><th>ДЕЙСТВИЕ</th><th>ОБЪЕКТ</th><th>ПРИЧИНА</th></tr></thead><tbody>{filtered.map(entry => <tr key={entry.id}><td className="muted">{dateTime.format(new Date(entry.createdAt))}</td><td><strong>{entry.username}</strong></td><td><span className="audit-action">{actionNames[entry.action] ?? entry.action}</span></td><td>{entityNames[entry.entityType] ?? entry.entityType} №{entry.entityId}</td><td className="audit-reason">{entry.reason || '—'}</td></tr>)}</tbody></table>{!loading && !filtered.length && <div className="empty-state table-empty">Действия не найдены</div>}{loading && <div className="empty-state table-empty">Загрузка истории…</div>}</div>
    </section>
  </>;
}
