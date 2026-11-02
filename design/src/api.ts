export type Actor = { id: number; employeeId: number | null; role: 'OWNER' | 'SELLER'; username: string };
export type Summary = { from: string; to: string; salesRevenue: number; returns: number; netRevenue: number; discounts: number; netCost: number; grossProfit: number; expenses: number; grossProfitAfterExpenses: number };
export type Sale = { id: number; documentNumber: string; sellerId: number; status: string; soldAt: string; total: number };
export type LowStock = { productId: number; name: string; sku: string; quantity: number; lowStockThreshold: number };
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
  summary: (from: string, to: string) => request<Summary>(`/reports/summary?from=${from}&to=${to}`),
  lowStock: () => request<LowStock[]>('/reports/low-stock'),
};
