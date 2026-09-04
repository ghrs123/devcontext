import { clearToken, getToken } from '@/lib/auth';
import type {
  AdminMetrics,
  AdminRecommendation,
  AdminRecommendationFilters,
  AdminHealthResponse,
  AnalyticsSummary,
  ApiEnvelope,
  ApiKeys,
  AuthResponse,
  BillingStatusResponse,
  Brand,
  GlobalBrandSizeChartVersion,
  LoginRequest,
  Product,
  ProductHealthRow,
  ProductRequest,
  ScrapeJobResponse,
  SimulateRequest,
  SimulateResponse,
  SpringPage,
  StoreAdminView,
  RegisterRequest,
  RecommendationStatsResponse,
  ScrapeTriggerAllResponse,
  SizeChartUploadResult,
  SizeEntryData,
  StoreProfile,
  UpdateStoreProfileRequest
} from '@/lib/types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(message: string, status: number, code = 'API_ERROR') {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

function handleUnauthorized() {
  clearToken();
  if (globalThis.window !== undefined) {
    globalThis.location.href = '/login';
  }
}

async function parseEnvelope<T>(response: Response): Promise<T> {
  let payload: ApiEnvelope<T> | null = null;
  try {
    payload = (await response.json()) as ApiEnvelope<T>;
  } catch {
    payload = null;
  }

  if (response.status === 204) {
    return null as T;
  }

  if (response.status === 401) {
    handleUnauthorized();
    throw new ApiError('Unauthorized. Please login again.', 401, 'UNAUTHORIZED');
  }

  if (!response.ok) {
    const message = payload?.error?.message || 'Request failed.';
    const code = payload?.error?.code || 'HTTP_ERROR';
    throw new ApiError(message, response.status, code);
  }

  // A 2xx with a non-JSON / empty body is a valid no-content response.
  if (payload === null && response.ok) {
    return null as T;
  }

  if (!payload?.success) {
    const message = payload?.error?.message || 'Unexpected API response.';
    const code = payload?.error?.code || 'INVALID_API_RESPONSE';
    throw new ApiError(message, response.status, code);
  }

  // success:true with data:null is legitimate for no-content actions
  // (e.g. admin plan override). Callers that need data type T accordingly.
  return payload.data as T;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  authRequired = true,
  expectJson = true
): Promise<T> {
  const headers = new Headers(init.headers || {});

  if (expectJson && !headers.has('Content-Type') && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (authRequired) {
    const token = getToken();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    cache: 'no-store'
  });

  return parseEnvelope<T>(response);
}

export const api = {
  register(payload: RegisterRequest): Promise<AuthResponse> {
    return request<AuthResponse>(
      '/api/dashboard/v1/auth/register',
      {
        method: 'POST',
        body: JSON.stringify(payload)
      },
      false
    );
  },

  login(payload: LoginRequest): Promise<AuthResponse> {
    return request<AuthResponse>(
      '/api/dashboard/v1/auth/login',
      {
        method: 'POST',
        body: JSON.stringify(payload)
      },
      false
    );
  },

  getProfile(): Promise<StoreProfile> {
    return request<StoreProfile>('/api/dashboard/v1/store/profile');
  },

  updateProfile(payload: UpdateStoreProfileRequest): Promise<StoreProfile> {
    return request<StoreProfile>('/api/dashboard/v1/store/profile', {
      method: 'PATCH',
      body: JSON.stringify(payload)
    });
  },

  getApiKeys(): Promise<ApiKeys> {
    return request<ApiKeys>('/api/dashboard/v1/store/api-keys');
  },

  regenerateApiKeys(): Promise<ApiKeys> {
    return request<ApiKeys>('/api/dashboard/v1/store/api-keys/regenerate', {
      method: 'POST'
    });
  },

  getProducts(): Promise<Product[]> {
    return request<Product[]>('/api/dashboard/v1/products');
  },

  getBrands(): Promise<Brand[]> {
    return request<Brand[]>('/api/dashboard/v1/brands');
  },

  createBrand(name: string): Promise<Brand> {
    return request<Brand>('/api/dashboard/v1/brands', {
      method: 'POST',
      body: JSON.stringify({ name })
    });
  },

  async deleteBrand(brandId: string): Promise<void> {
    const token = getToken();
    const headers = new Headers();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    const response = await fetch(`${API_BASE_URL}/api/dashboard/v1/brands/${brandId}`, {
      method: 'DELETE',
      headers,
      cache: 'no-store'
    });

    if (response.status === 401) {
      handleUnauthorized();
      throw new ApiError('Unauthorized. Please login again.', 401, 'UNAUTHORIZED');
    }

    if (!response.ok) {
      let payload: ApiEnvelope<null> | null = null;
      try {
        payload = (await response.json()) as ApiEnvelope<null>;
      } catch {
        payload = null;
      }

      const message = payload?.error?.message || 'Unable to delete brand.';
      const code = payload?.error?.code || 'HTTP_ERROR';
      throw new ApiError(message, response.status, code);
    }
  },

  createProduct(payload: ProductRequest): Promise<Product> {
    return request<Product>('/api/dashboard/v1/products', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  updateProduct(productId: string, payload: ProductRequest): Promise<Product> {
    return request<Product>(`/api/dashboard/v1/products/${productId}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    });
  },

  async deleteProduct(productId: string): Promise<void> {
    await request<null>(`/api/dashboard/v1/products/${productId}`, {
      method: 'DELETE'
    });
  },

  uploadSizeChart(productId: string, file: File): Promise<SizeChartUploadResult> {
    const formData = new FormData();
    formData.append('file', file);

    return request<SizeChartUploadResult>(
      `/api/dashboard/v1/size-charts/${productId}/upload`,
      {
        method: 'POST',
        body: formData
      }
    );
  },

  uploadManualSizeChart(productId: string, entries: SizeEntryData[]): Promise<SizeChartUploadResult> {
    return request<SizeChartUploadResult>(`/api/dashboard/v1/size-charts/${productId}/manual`, {
      method: 'POST',
      body: JSON.stringify(entries)
    });
  },

  getActiveSizeChart(productId: string): Promise<SizeEntryData[]> {
    return request<SizeEntryData[]>(`/api/dashboard/v1/size-charts/${productId}/active`);
  },

  async deactivateActiveSizeChart(productId: string): Promise<void> {
    await request<null>(`/api/dashboard/v1/size-charts/${productId}/active`, {
      method: 'DELETE'
    });
  },

  getAnalyticsSummary(): Promise<AnalyticsSummary> {
    return request<AnalyticsSummary>('/api/dashboard/v1/analytics/summary');
  },

  getProductHealth(): Promise<ProductHealthRow[]> {
    return request<ProductHealthRow[]>('/api/dashboard/v1/analytics/product-health');
  },

  simulateRecommendation(body: SimulateRequest): Promise<SimulateResponse> {
    return request<SimulateResponse>('/api/dashboard/v1/recommendations/simulate', {
      method: 'POST',
      body: JSON.stringify(body)
    });
  },

  adminGetMetrics(): Promise<AdminMetrics> {
    return request<AdminMetrics>('/api/admin/v1/metrics');
  },

  adminGetStores(page = 0, size = 20, status = 'ACTIVE', search = ''): Promise<SpringPage<StoreAdminView>> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      status,
      search
    });
    return request<SpringPage<StoreAdminView>>(`/api/admin/v1/stores?${params.toString()}`);
  },

  adminGetStore(storeId: string): Promise<StoreAdminView> {
    return request<StoreAdminView>(`/api/admin/v1/stores/${storeId}`);
  },

  adminUpdateStoreStatus(storeId: string, status: 'ACTIVE' | 'INACTIVE'): Promise<StoreAdminView> {
    return request<StoreAdminView>(`/api/admin/v1/stores/${storeId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status })
    });
  },

  adminGetBrands(): Promise<Brand[]> {
    return request<Brand[]>('/api/admin/v1/brands');
  },

  adminCreateBrand(name: string): Promise<Brand> {
    return request<Brand>('/api/admin/v1/brands', {
      method: 'POST',
      body: JSON.stringify({ name })
    });
  },

  adminUpdateBrand(brandId: string, name: string): Promise<Brand> {
    return request<Brand>(`/api/admin/v1/brands/${brandId}`, {
      method: 'PUT',
      body: JSON.stringify({ name })
    });
  },

  async adminDeleteBrand(brandId: string): Promise<void> {
    await request<null>(`/api/admin/v1/brands/${brandId}`, {
      method: 'DELETE'
    });
  },

  adminUploadGlobalSizeChart(brandId: string, file: File): Promise<SizeChartUploadResult> {
    const formData = new FormData();
    formData.append('file', file);

    return request<SizeChartUploadResult>(`/api/admin/v1/brands/${brandId}/size-charts/upload`, {
      method: 'POST',
      body: formData
    });
  },

  adminGetGlobalBrandSizeCharts(brandId: string): Promise<GlobalBrandSizeChartVersion[]> {
    return request<GlobalBrandSizeChartVersion[]>(`/api/admin/v1/brands/${brandId}/size-charts`);
  },

  async adminDeactivateGlobalBrandActiveSizeChart(brandId: string): Promise<void> {
    await request<null>(`/api/admin/v1/brands/${brandId}/size-charts/active`, {
      method: 'DELETE'
    });
  },

  adminGetRecommendations(
    page = 0,
    size = 20,
    filters: AdminRecommendationFilters = {}
  ): Promise<SpringPage<AdminRecommendation>> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size)
    });

    if (filters.tenantId) {
      params.set('tenantId', filters.tenantId);
    }
    if (filters.productId) {
      params.set('productId', filters.productId);
    }
    if (filters.quality) {
      params.set('quality', filters.quality);
    }

    return request<SpringPage<AdminRecommendation>>(`/api/admin/v1/recommendations?${params.toString()}`);
  },

  adminTriggerScrape(brandId: string): Promise<ScrapeJobResponse> {
    return request<ScrapeJobResponse>(`/api/admin/v1/brands/${brandId}/scrape`, {
      method: 'POST'
    });
  },

  adminGetScrapeJobs(brandId: string): Promise<ScrapeJobResponse[]> {
    return request<ScrapeJobResponse[]>(`/api/admin/v1/brands/${brandId}/scrape-jobs`);
  },

  adminGetScrapeJob(brandId: string, jobId: string): Promise<ScrapeJobResponse> {
    return request<ScrapeJobResponse>(`/api/admin/v1/brands/${brandId}/scrape-jobs/${jobId}`);
  },

  adminGetAllScrapeJobs(status?: string, page = 0, size = 20): Promise<SpringPage<ScrapeJobResponse>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return request<SpringPage<ScrapeJobResponse>>(`/api/admin/v1/scrape-jobs?${params.toString()}`);
  },

  async adminOverrideStorePlan(storeId: string, plan: string): Promise<void> {
    await request<null>(`/api/admin/v1/stores/${storeId}/plan`, {
      method: 'PATCH',
      body: JSON.stringify({ plan })
    });
  },

  getBillingStatus(): Promise<BillingStatusResponse> {
    return request<BillingStatusResponse>('/api/dashboard/v1/billing/status');
  },

  createCheckoutSession(plan: string): Promise<{ checkoutUrl: string }> {
    return request<{ checkoutUrl: string }>('/api/dashboard/v1/billing/checkout', {
      method: 'POST',
      body: JSON.stringify({ plan })
    });
  },

  createPortalSession(): Promise<{ portalUrl: string }> {
    return request<{ portalUrl: string }>('/api/dashboard/v1/billing/portal', {
      method: 'POST'
    });
  },

  adminGetHealth(): Promise<AdminHealthResponse> {
    return request<AdminHealthResponse>('/api/admin/v1/health');
  },

  adminGetRecommendationStats(): Promise<RecommendationStatsResponse> {
    return request<RecommendationStatsResponse>('/api/admin/v1/recommendations/stats');
  },

  adminTriggerAllScrapes(): Promise<ScrapeTriggerAllResponse> {
    return request<ScrapeTriggerAllResponse>('/api/admin/v1/scrape-jobs/trigger-all', {
      method: 'POST'
    });
  }
};
