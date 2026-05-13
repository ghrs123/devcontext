import { clearToken, getToken } from '@/lib/auth';
import type {
  AnalyticsSummary,
  ApiEnvelope,
  ApiKeys,
  AuthResponse,
  Brand,
  LoginRequest,
  Product,
  ProductRequest,
  RegisterRequest,
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

  if (response.status === 401) {
    handleUnauthorized();
    throw new ApiError('Unauthorized. Please login again.', 401, 'UNAUTHORIZED');
  }

  if (!response.ok) {
    const message = payload?.error?.message || 'Request failed.';
    const code = payload?.error?.code || 'HTTP_ERROR';
    throw new ApiError(message, response.status, code);
  }

  if (!payload?.success || payload.data === null) {
    const message = payload?.error?.message || 'Unexpected API response.';
    const code = payload?.error?.code || 'INVALID_API_RESPONSE';
    throw new ApiError(message, response.status, code);
  }

  return payload.data;
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
  }
};
