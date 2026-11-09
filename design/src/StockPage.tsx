import { useEffect, useMemo, useState, type FormEvent } from "react"
import {
  api,
  type InventoryDetail,
  type InventorySummary,
  type Product,
  type StockMovement,
} from "./api"

type Tab = "inventory" | "writeoff" | "history"
type CountLine = {
  productId: number
  actualQuantity: number
  excessUnitCost: number | null
}
const money = new Intl.NumberFormat("ru-KZ", {
  style: "currency",
  currency: "KZT",
  maximumFractionDigits: 2,
})
const dateTime = new Intl.DateTimeFormat("ru-KZ", {
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
})

export function StockPage({ query }: { query: string }) {
  const [tab, setTab] = useState<Tab>("inventory")
  const [products, setProducts] = useState<Product[]>([])
  const [inventories, setInventories] = useState<InventorySummary[]>([])
  const [inventoryDetail, setInventoryDetail] =
    useState<InventoryDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [countLines, setCountLines] = useState<CountLine[]>([])
  const [selectedCount, setSelectedCount] = useState("")
  const [inventoryReason, setInventoryReason] = useState(
    "Плановая инвентаризация",
  )
  const [writeProduct, setWriteProduct] = useState("")
  const [writeQuantity, setWriteQuantity] = useState(1)
  const [writeReason, setWriteReason] = useState("")
  const [historyProduct, setHistoryProduct] = useState("")
  const [movements, setMovements] = useState<StockMovement[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  async function load() {
    try {
      const [catalog, docs] = await Promise.all([
        api.products(),
        api.inventories(),
      ])
      setProducts(catalog)
      setInventories(docs)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось загрузить склад")
    }
  }
  useEffect(() => {
    void load()
  }, [])
  const productsById = useMemo(
    () => new Map(products.map((product) => [product.id, product])),
    [products],
  )
  const activeProducts = products.filter(
    (product) =>
      product.active &&
      `${product.name} ${product.sku}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  )
  const stockUnits = products
    .filter((product) => product.active)
    .reduce((sum, product) => sum + Number(product.quantity), 0)
  const lowCount = products.filter(
    (product) => product.active && product.lowStock,
  ).length

  function addCountLine() {
    const id = Number(selectedCount)
    const product = productsById.get(id)
    if (!product || countLines.some((line) => line.productId === id)) return
    setCountLines((current) => [
      ...current,
      { productId: id, actualQuantity: product.quantity, excessUnitCost: null },
    ])
    setSelectedCount("")
  }
  function updateCount(id: number, patch: Partial<CountLine>) {
    setCountLines((current) =>
      current.map((line) =>
        line.productId === id ? { ...line, ...patch } : line,
      ),
    )
  }
  async function submitInventory(event: FormEvent) {
    event.preventDefault()
    setError("")
    if (!countLines.length) return setError("Добавьте пересчитанные товары")
    setBusy(true)
    try {
      const result = await api.createInventory(inventoryReason, countLines)
      setNotice(`Инвентаризация №${result.id} проведена`)
      setCountLines([])
      await load()
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Не удалось провести инвентаризацию",
      )
    } finally {
      setBusy(false)
    }
  }
  async function submitWriteOff(event: FormEvent) {
    event.preventDefault()
    setError("")
    const id = Number(writeProduct)
    if (!id) return setError("Выберите товар")
    setBusy(true)
    try {
      const result = await api.writeOff(id, writeQuantity, writeReason)
      setNotice(`Списание №${result.id} проведено`)
      setWriteProduct("")
      setWriteQuantity(1)
      setWriteReason("")
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось списать товар")
    } finally {
      setBusy(false)
    }
  }
  async function selectHistory(value: string) {
    setHistoryProduct(value)
    setError("")
    if (!value) {
      setMovements([])
      return
    }
    try {
      setMovements(await api.stockMovements(Number(value)))
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось загрузить движения")
    }
  }
  async function openInventory(id: number) {
    setDetailLoading(true)
    setError("")
    try {
      setInventoryDetail(await api.inventory(id))
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Не удалось открыть инвентаризацию",
      )
    } finally {
      setDetailLoading(false)
    }
  }
  const movementLabel = (movement: StockMovement) =>
    movement.adjustmentType === "OPENING"
      ? "Начальный остаток"
      : movement.adjustmentType === "INVENTORY"
        ? "Инвентаризация"
        : movement.adjustmentType === "WRITE_OFF"
          ? "Списание"
          : movement.type === "PURCHASE"
            ? "Поступление"
            : movement.type === "SALE"
              ? "Продажа"
              : movement.type === "RETURN"
                ? "Возврат"
                : movement.type

  return (
    <>
      <section className="sales-heading">
        <div>
          <p className="eyebrow">КОНТРОЛЬ ОСТАТКОВ</p>
          <h1>Склад</h1>
          <p>Фактический пересчёт, списания и полный журнал движений.</p>
        </div>
      </section>
      {notice && (
        <div className="success-banner">
          {notice}
          <button onClick={() => setNotice("")}>×</button>
        </div>
      )}
      {error && <div className="page-error">{error}</div>}
      <section className="sales-kpis">
        <div>
          <span>Единиц на складе</span>
          <strong>{stockUnits}</strong>
        </div>
        <div>
          <span>Активных товаров</span>
          <strong>{products.filter((p) => p.active).length}</strong>
        </div>
        <div>
          <span>Низкий остаток</span>
          <strong className={lowCount ? "danger-value" : ""}>{lowCount}</strong>
        </div>
      </section>
      <div className="warehouse-tabs">
        <button
          className={tab === "inventory" ? "active" : ""}
          onClick={() => setTab("inventory")}
        >
          Инвентаризация
        </button>
        <button
          className={tab === "writeoff" ? "active" : ""}
          onClick={() => setTab("writeoff")}
        >
          Списание
        </button>
        <button
          className={tab === "history" ? "active" : ""}
          onClick={() => setTab("history")}
        >
          История движений
        </button>
      </div>

      {tab === "inventory" && (
        <>
          <section className="panel warehouse-form">
            <div className="panel-head">
              <div>
                <h2>Новый фактический пересчёт</h2>
                <p>
                  Добавьте только товары, которые вы действительно пересчитали
                </p>
              </div>
            </div>
            <form onSubmit={submitInventory}>
              <div className="product-picker">
                <select
                  value={selectedCount}
                  onChange={(e) => setSelectedCount(e.target.value)}
                >
                  <option value="">Выберите пересчитанный товар</option>
                  {activeProducts
                    .filter(
                      (p) =>
                        !countLines.some((line) => line.productId === p.id),
                    )
                    .map((p) => (
                      <option value={p.id} key={p.id}>
                        {p.name} · в системе {p.quantity}
                      </option>
                    ))}
                </select>
                <button
                  type="button"
                  className="button secondary-button"
                  onClick={addCountLine}
                  disabled={!selectedCount}
                >
                  Добавить
                </button>
              </div>
              <div className="inventory-lines">
                {countLines.map((line) => {
                  const product = productsById.get(line.productId)!
                  const difference = line.actualQuantity - product.quantity
                  return (
                    <div className="inventory-line" key={line.productId}>
                      <div>
                        <strong>{product.name}</strong>
                        <span>{product.sku}</span>
                      </div>
                      <div className="system-count">
                        <span>В системе</span>
                        <strong>{product.quantity}</strong>
                      </div>
                      <label>
                        Фактически
                        <input
                          type="number"
                          min="0"
                          max="1000000000"
                          value={line.actualQuantity}
                          onChange={(e) => {
                            const actual = Math.max(0, Number(e.target.value))
                            updateCount(line.productId, {
                              actualQuantity: actual,
                              excessUnitCost:
                                actual > product.quantity
                                  ? line.excessUnitCost
                                  : null,
                            })
                          }}
                        />
                      </label>
                      <div
                        className={`count-difference ${
                          difference < 0
                            ? "negative"
                            : difference > 0
                              ? "positive"
                              : ""
                        }`}
                      >
                        <span>Разница</span>
                        <strong>
                          {difference > 0 ? `+${difference}` : difference}
                        </strong>
                      </div>
                      {difference > 0 && (
                        <label>
                          Себестоимость излишка
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={line.excessUnitCost ?? ""}
                            onChange={(e) =>
                              updateCount(line.productId, {
                                excessUnitCost: Number(e.target.value),
                              })
                            }
                            required
                          />
                        </label>
                      )}
                      <button
                        type="button"
                        className="remove-line"
                        onClick={() =>
                          setCountLines((current) =>
                            current.filter(
                              (item) => item.productId !== line.productId,
                            ),
                          )
                        }
                      >
                        ×
                      </button>
                    </div>
                  )
                })}
                {!countLines.length && (
                  <div className="sale-empty">
                    Добавьте товары для пересчёта
                  </div>
                )}
              </div>
              <label className="field inventory-reason">
                Причина
                <textarea
                  value={inventoryReason}
                  onChange={(e) => setInventoryReason(e.target.value)}
                  required
                  maxLength={2000}
                />
              </label>
              <div className="warehouse-submit">
                <span>Все изменения применятся одной атомарной операцией</span>
                <button
                  className="button primary"
                  disabled={busy || !countLines.length}
                >
                  {busy ? "Проводим…" : "Провести инвентаризацию"}
                </button>
              </div>
            </form>
          </section>
          <section className="panel inventory-history">
            <div className="panel-head">
              <div>
                <h2>Последние инвентаризации</h2>
                <p>Документы нельзя удалить или изменить</p>
              </div>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>№</th>
                    <th>ДАТА</th>
                    <th>ПРИЧИНА</th>
                    <th>ПОЗИЦИЙ</th>
                    <th>РАСХОЖДЕНИЕ</th>
                  </tr>
                </thead>
                <tbody>
                  {inventories.map((doc) => (
                    <tr
                      className="clickable-row"
                      key={doc.id}
                      onClick={() => void openInventory(doc.id)}
                    >
                      <td>
                        <strong>#{doc.id}</strong>
                      </td>
                      <td className="muted">
                        {dateTime.format(new Date(doc.createdAt))}
                      </td>
                      <td>{doc.reason}</td>
                      <td>{doc.itemCount}</td>
                      <td>
                        <strong>{doc.absoluteDifference} шт.</strong>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!inventories.length && (
                <div className="empty-state">Инвентаризаций пока нет</div>
              )}
            </div>
          </section>
        </>
      )}

      {tab === "writeoff" && (
        <section className="panel writeoff-panel">
          <div className="panel-head">
            <div>
              <h2>Списание товара</h2>
              <p>Для брака, повреждения, утери и других обоснованных случаев</p>
            </div>
          </div>
          <form onSubmit={submitWriteOff}>
            <label className="field">
              Товар
              <select
                value={writeProduct}
                onChange={(e) => {
                  setWriteProduct(e.target.value)
                  setWriteQuantity(1)
                }}
                required
              >
                <option value="">Выберите товар</option>
                {activeProducts
                  .filter((p) => p.quantity > 0)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} · доступно {p.quantity}
                    </option>
                  ))}
              </select>
            </label>
            <label className="field">
              Количество
              <input
                type="number"
                min="1"
                max={
                  writeProduct
                    ? productsById.get(Number(writeProduct))?.quantity
                    : 1
                }
                value={writeQuantity}
                onChange={(e) =>
                  setWriteQuantity(Math.max(1, Number(e.target.value)))
                }
                required
              />
            </label>
            <label className="field wide">
              Причина
              <textarea
                value={writeReason}
                onChange={(e) => setWriteReason(e.target.value)}
                placeholder="Например: повреждён при хранении"
                maxLength={2000}
                required
              />
            </label>
            <div className="writeoff-warning">
              <strong>Списание уменьшит остаток по FIFO.</strong>
              <span>
                Операция сохранится в журнале и не может быть удалена.
              </span>
            </div>
            <button className="button primary" disabled={busy}>
              {busy ? "Проводим…" : "Списать товар"}
            </button>
          </form>
        </section>
      )}

      {tab === "history" && (
        <section className="panel movement-panel">
          <div className="panel-head">
            <div>
              <h2>История движений товара</h2>
              <p>Поступления, продажи, возвраты и корректировки</p>
            </div>
            <label className="field movement-select">
              Товар
              <select
                value={historyProduct}
                onChange={(e) => selectHistory(e.target.value)}
              >
                <option value="">Выберите товар</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} · остаток {p.quantity}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="movement-list">
            {movements.map((m) => (
              <div className="movement-row" key={m.id}>
                <div
                  className={`movement-sign ${m.quantity > 0 ? "in" : "out"}`}
                >
                  {m.quantity > 0 ? "+" : "−"}
                </div>
                <div>
                  <strong>{movementLabel(m)}</strong>
                  <span>
                    {m.reason} · {m.username} ·{" "}
                    {dateTime.format(new Date(m.createdAt))}
                  </span>
                </div>
                <div>
                  <strong
                    className={
                      m.quantity > 0 ? "positive-text" : "negative-text"
                    }
                  >
                    {m.quantity > 0 ? "+" : ""}
                    {m.quantity} шт.
                  </strong>
                  <span>{money.format(m.unitCost)} / шт.</span>
                </div>
                <div className="movement-balance">
                  <span>Остаток</span>
                  <strong>{m.balance}</strong>
                </div>
              </div>
            ))}
            {historyProduct && !movements.length && (
              <div className="empty-state">Движений по товару пока нет</div>
            )}
            {!historyProduct && (
              <div className="empty-state">
                Выберите товар для просмотра журнала
              </div>
            )}
          </div>
        </section>
      )}

      {inventoryDetail && (
        <div
          className="modal-backdrop sale-backdrop"
          onMouseDown={() => setInventoryDetail(null)}
        >
          <section
            className="purchase-detail inventory-detail-modal"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="sale-modal-head">
              <div>
                <p className="eyebrow">ДОКУМЕНТ СКЛАДА</p>
                <h2>Инвентаризация #{inventoryDetail.id}</h2>
                <p>
                  {inventoryDetail.reason} ·{" "}
                  {dateTime.format(new Date(inventoryDetail.createdAt))}
                </p>
              </div>
              <button
                className="modal-close"
                type="button"
                aria-label="Закрыть"
                onClick={() => setInventoryDetail(null)}
              >
                ×
              </button>
            </div>
            <div className="inventory-detail-lines">
              <div className="inventory-detail-row inventory-detail-labels">
                <span>Товар</span>
                <span>По системе</span>
                <span>Фактически</span>
                <span>Разница</span>
              </div>
              {inventoryDetail.items.map((item) => (
                <div className="inventory-detail-row" key={item.productId}>
                  <div>
                    <strong>{item.name}</strong>
                    <span>{item.sku}</span>
                  </div>
                  <strong>{item.systemQuantity}</strong>
                  <strong>{item.actualQuantity}</strong>
                  <div>
                    <strong
                      className={
                        item.difference > 0
                          ? "positive-text"
                          : item.difference < 0
                            ? "negative-text"
                            : ""
                      }
                    >
                      {item.difference > 0 ? "+" : ""}
                      {item.difference} шт.
                    </strong>
                    {item.unitCost !== null && item.difference > 0 && (
                      <span>{money.format(item.unitCost)} / шт.</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>
        </div>
      )}

      {detailLoading && (
        <div className="detail-loading">Загрузка документа…</div>
      )}
    </>
  )
}
