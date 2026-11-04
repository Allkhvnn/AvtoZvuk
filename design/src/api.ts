export type Actor = { id: number; employeeId: number | null; role: 'OWNER' | 'SELLER'; username: string };
export type Summary = { from: string; to: string; salesRevenue: number; returns: number; netRevenue: number; discounts: number; saleCost: number; restoredCost: number; netCost: number; grossProfit: number; expenses: number; grossProfitAfterExpenses: number; bonusAccrued: number; bonusPaid: number; salaryPaid: number };
export type ExpenseCategory = { id: number; name: string };
export type Expense = { id: number; categoryId: number; categoryName: string; amount: number; method: 'CASH' | 'KASPI_PAY' | 'BANK_TRANSFER'; description: string; incurredAt: string; reversalOfId: number | null; reversed: boolean };
export type ExpenseTotals = { total: number; cash: number; kaspiPay: number; bankTransfer: number };
export type PaymentReport = { method: 'CASH' | 'KASPI_PAY'; incoming: number; refunds: number; net: number };
export type ProductReport = { productId: number; name: string; sku: string; soldQuantity: number; returnedQuantity: number; netQuantity: number; salesRevenue: number; returns: number; netRevenue: number; discounts: number; netCost: number; grossProfit: number };
export type EmployeeReport = { employeeId: number; fullName: string; active: boolean; salesCount: number; salesRevenue: number; returnsCount: number; returns: number; netRevenue: number; bonusAccrued: number; bonusPaid: number; salaryPaid: number };
export type Sale = { id: number; documentNumber: string; sellerId: number; status: string; soldAt: string; total: number };
export type SaleDocument = { id: number; documentNumber: string; status: string; total: number; bonus: number; items: SaleDocumentItem[]; payments: { method: 'CASH' | 'KASPI_PAY'; amount: number }[] };
export type SaleDocumentItem = { id: number; productId: number; productName: string; productSku: string; quantity: number; unitPrice: number; discountAmount: number; lineTotal: number; returnedQuantity: number; returnedAmount: number; availableQuantity: number };
export type NewReturn = { requestKey: string; saleId: number; reason: string; items: { saleItemId: number; quantity: number; restock: boolean }[]; payments: { method: 'CASH' | 'KASPI_PAY'; amount: number }[] };
export type LowStock = { productId: number; name: string; sku: string; quantity: number; lowStockThreshold: number };
export type Product = { id: number; categoryId: number | null; name: string; sku: string; barcode: string | null; sellingPrice: number; lowStockThreshold: number; quantity: number; active: boolean; lowStock: boolean };
export type Category = { id: number; name: string };
export type ProductInput = { name: string; sku: string; barcode: string | null; categoryId: number | null; sellingPrice: number; lowStockThreshold: number };
export type OpeningStockInput = { requestKey: string; productId: number; quantity: number; unitCost: number; reason: string };
export type Supplier = { id: number; name: string; phone: string | null };
export type PurchaseSummary = { id: number; documentNumber: string; status: string; purchasedAt: string; supplierId: number; supplierName: string; itemCount: number; total: number };
export type PurchaseDocument = { id: number; documentNumber: string; status: string; total: number; items: { id: number; productId: number; quantity: number; unitCost: number; lineTotal: number }[] };
export type NewPurchase = { requestKey: string; supplierId: number; items: { productId: number; quantity: number; unitCost: number }[] };
export type InventorySummary = { id: number; operationType: string; reason: string; createdAt: string; itemCount: number; absoluteDifference: number };
export type StockMovement = { id: number; type: string; adjustmentType: string | null; quantity: number; unitCost: number; balance: number; reason: string; createdAt: string; username: string; purchaseItemId: number | null; saleId: number | null; returnItemId: number | null; adjustmentId: number | null };
export type Employee = { id: number; fullName: string; phone: string | null; bonusRate: number; active: boolean };
export type UserAccount = { id: number; employeeId: number | null; employeeName: string | null; username: string; role: 'OWNER' | 'SELLER'; active: boolean; employeeActive: boolean; createdAt: string };
export type BonusBalance = { employeeId: number; fullName: string; active: boolean; accrued: number; paid: number; balance: number };
export type BonusEntry = { id: number; type: string; saleId: number | null; returnId: number | null; amount: number; reason: string; createdAt: string };
export type StaffPayment = { id: number; type: 'BONUS' | 'SALARY'; amount: number; method: string; paidAt: string; reason: string; reversalOfId: number | null; reversed: boolean };
export type AuditLog = { id: number; action: string; entityType: string; entityId: number; reason: string | null; username: string; createdAt: string };
export type NewSale = {
  requestKey: string;
  sellerId: number | null;
  items: { productId: number; quantity: number; unitPrice: number; discountAmount: number }[];
  payments: { method: 'CASH' | 'KASPI_PAY'; amount: number }[];
};
type Csrf = { token: string; headerName: string };

let csrf: Csrf | null = null;

async function readError(response: Response) {
  try {
    const body = await response.json();
    return body.message || body.error || `Ошибка ${response.status}`;
  } catch {
    return `Ошибка ${response.status}`;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, { credentials: 'include', ...init });
  if (!response.ok) throw new Error(await readError(response));
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function mutate<T>(path: string, body: unknown, method = 'POST'): Promise<T> {
  const token = await getCsrf();
  return request<T>(path, {
    method,
    headers: { 'Content-Type': 'application/json', [token.headerName]: token.token },
    body: JSON.stringify(body),
  });
}

export async function getCsrf(refresh = false) {
  if (!csrf || refresh) csrf = await request<Csrf>('/csrf');
  return csrf;
}

export async function login(username: string, password: string) {
  const token = await getCsrf(true);
  await request<void>('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.headerName]: token.token },
    body: new URLSearchParams({ username, password }),
  });
  await getCsrf(true);
}

export async function logout() {
  const token = await getCsrf();
  await request<void>('/logout', { method: 'POST', headers: { [token.headerName]: token.token } });
  csrf = null;
}

export const api = {
  me: () => request<Actor>('/me'),
  sales: (limit = 6) => request<Sale[]>(`/sales?limit=${limit}&offset=0`),
  sale: (id: number) => request<SaleDocument>(`/sales/${id}`),
  createReturn: (returned: NewReturn) => mutate<{ id: number }>('/returns', returned),
  summary: (from: string, to: string) => request<Summary>(`/reports/summary?from=${from}&to=${to}`),
  paymentReport: (from: string, to: string) => request<PaymentReport[]>(`/reports/payment-methods?from=${from}&to=${to}`),
  productReport: (from: string, to: string) => request<ProductReport[]>(`/reports/products?from=${from}&to=${to}`),
  employeeReport: (from: string, to: string) => request<EmployeeReport[]>(`/reports/employees?from=${from}&to=${to}`),
  expenseCategories: () => request<ExpenseCategory[]>('/expense-categories'),
  createExpenseCategory: (name: string) => mutate<{ id: number }>('/expense-categories', { name }),
  expenses: () => request<Expense[]>('/expenses?limit=100&offset=0'),
  expenseTotals: () => request<ExpenseTotals>('/expenses/totals'),
  createExpense: (categoryId: number, amount: number, method: Expense['method'], description: string, incurredAt: string | null) => mutate<{ id: number }>('/expenses', { requestKey: crypto.randomUUID(), categoryId, amount, method, description, incurredAt }),
  reverseExpense: (id: number, reason: string) => mutate<{ id: number }>(`/expenses/${id}/reversal`, { requestKey: crypto.randomUUID(), reason }),
  lowStock: () => request<LowStock[]>('/reports/low-stock'),
  products: () => request<Product[]>('/products?limit=100&offset=0'),
  categories: () => request<Category[]>('/categories'),
  createCategory: (name: string) => mutate<{ id: number }>('/categories', { name }),
  createProduct: (product: ProductInput) => mutate<Product>('/products', product),
  updateProduct: (id: number, product: ProductInput) => mutate<void>(`/products/${id}`, product, 'PUT'),
  deactivateProduct: (id: number) => mutate<void>(`/products/${id}/deactivate`, null),
  openingStock: (opening: OpeningStockInput) => mutate<{ id: number }>('/stock/opening', opening),
  suppliers: () => request<Supplier[]>('/suppliers'),
  createSupplier: (name: string, phone: string | null) => mutate<{ id: number }>('/suppliers', { name, phone }),
  purchases: () => request<PurchaseSummary[]>('/purchases?limit=100&offset=0'),
  purchase: (id: number) => request<PurchaseDocument>(`/purchases/${id}`),
  createPurchase: (purchase: NewPurchase) => mutate<{ id: number }>('/purchases', purchase),
  inventories: () => request<InventorySummary[]>('/inventories?limit=100&offset=0'),
  createInventory: (reason: string, items: { productId: number; actualQuantity: number; excessUnitCost: number | null }[]) => mutate<{ id: number }>('/inventories', { requestKey: crypto.randomUUID(), reason, items }),
  writeOff: (productId: number, quantity: number, reason: string) => mutate<{ id: number }>('/stock/write-offs', { requestKey: crypto.randomUUID(), productId, quantity, reason }),
  stockMovements: (productId: number) => request<StockMovement[]>(`/products/${productId}/stock-movements?limit=100&offset=0`),
  employees: () => request<Employee[]>('/employees'),
  createEmployee: (fullName: string, phone: string | null, bonusRate: number) => mutate<{ id: number }>('/employees', { fullName, phone, bonusRate }),
  updateEmployee: (id: number, fullName: string, phone: string | null, bonusRate: number) => mutate<void>(`/employees/${id}`, { fullName, phone, bonusRate }, 'PUT'),
  employeeStatus: (id: number, active: boolean, reason: string) => mutate<void>(`/employees/${id}/status`, { active, reason }, 'PUT'),
  users: () => request<UserAccount[]>('/users'),
  createUser: (employeeId: number, username: string, password: string, role: 'OWNER' | 'SELLER') => mutate<{ id: number }>('/users', { employeeId, username, password, role }),
  userStatus: (id: number, active: boolean, reason: string) => mutate<void>(`/users/${id}/status`, { active, reason }, 'PUT'),
  bonusBalances: () => request<BonusBalance[]>('/bonuses/balances'),
  bonusEntries: (employeeId: number) => request<BonusEntry[]>(`/employees/${employeeId}/bonuses?limit=100&offset=0`),
  staffPayments: (employeeId: number) => request<StaffPayment[]>(`/employees/${employeeId}/staff-payments?limit=100&offset=0`),
  payStaff: (employeeId: number, type: 'BONUS' | 'SALARY', amount: number, method: 'CASH' | 'KASPI_PAY' | 'BANK_TRANSFER', reason: string) => mutate<{ id: number }>('/staff-payments', { requestKey: crypto.randomUUID(), employeeId, type, amount, method, reason, paidAt: null }),
  auditLog: () => request<AuditLog[]>('/audit-log?limit=200&offset=0'),
  createSale: (sale: NewSale) => mutate<{ id: number }>('/sales', sale),
};
