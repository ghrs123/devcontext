import { config } from './config.js';

const API = config.fitvision.apiUrl;
const ADMIN_TOKEN_REFRESH_INTERVAL_MS = 20 * 60 * 60 * 1000;

let adminJwt = null;
let adminRefreshTimer = null;

export async function initializeAdminJwtManagement() {
  await refreshAdminJwt();

  if (adminRefreshTimer) {
    clearInterval(adminRefreshTimer);
  }

  adminRefreshTimer = setInterval(async () => {
    try {
      await refreshAdminJwt();
    } catch (err) {
      console.error('[fitvision] admin JWT refresh failed:', err.message);
    }
  }, ADMIN_TOKEN_REFRESH_INTERVAL_MS);

  // Do not keep the event loop alive just because of this timer.
  if (typeof adminRefreshTimer.unref === 'function') {
    adminRefreshTimer.unref();
  }
}

export function getAdminJwt() {
  return adminJwt;
}

export async function refreshAdminJwt() {
  const email = config.fitvision.adminEmail;
  const password = config.fitvision.adminPassword;

  if (!email || !password) {
    throw new Error('Missing FITVISION_ADMIN_EMAIL or FITVISION_ADMIN_PASSWORD');
  }

  const res = await fetch(`${API}/api/dashboard/v1/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    throw new Error(`Admin login failed [${res.status}]`);
  }

  const envelope = await res.json();
  if (!envelope.success || !envelope.data?.accessToken) {
    throw new Error(`Admin login response invalid: ${envelope.error?.message ?? 'unknown error'}`);
  }

  adminJwt = envelope.data.accessToken;
  console.info('[fitvision] admin JWT refreshed');
  return adminJwt;
}

// ── Shopify connect ───────────────────────────────────────────────────────────

export async function connectShopifyStore(shop, accessToken, shopName) {
  const res = await fetch(`${API}/api/shopify/connect`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-FitVision-Shopify-Secret': config.fitvision.shopifySharedSecret,
    },
    body: JSON.stringify({ shop, accessToken, shopName }),
  });

  if (!res.ok) {
    throw new Error(`FitVision connect failed [${res.status}]: ${await res.text()}`);
  }

  const envelope = await res.json();
  if (!envelope.success) {
    throw new Error(`FitVision connect error: ${envelope.error?.message}`);
  }

  return envelope.data; // { jwt, apiKeyPublic, storeId }
}

export async function getShopifyStatus(shop) {
  const res = await fetch(`${API}/api/shopify/status?shop=${encodeURIComponent(shop)}`);
  if (!res.ok) throw new Error(`FitVision status check failed [${res.status}]`);
  return (await res.json()).data;
}

export async function deactivateStore(storeId) {
  if (!storeId) {
    throw new Error('deactivateStore requires storeId');
  }

  let token = getAdminJwt();
  if (!token) {
    token = await refreshAdminJwt();
  }

  let res = await fetch(`${API}/api/admin/v1/stores/${storeId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ status: 'INACTIVE' }),
  });

  if (res.status === 401) {
    token = await refreshAdminJwt();
    res = await fetch(`${API}/api/admin/v1/stores/${storeId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ status: 'INACTIVE' }),
    });
  }

  if (!res.ok) {
    throw new Error(`Failed to deactivate store [${res.status}]: ${await res.text()}`);
  }

  return (await res.json()).data;
}

// ── Product management ────────────────────────────────────────────────────────

export async function getProducts(jwt) {
  const res = await _authedFetch(jwt, '/api/dashboard/v1/products');
  if (!res.ok) throw new Error(`getProducts failed [${res.status}]`);
  return (await res.json()).data ?? [];
}

export async function createProduct(jwt, { externalProductId, name, category, genderTarget = 'UNISEX' }) {
  const res = await _authedFetch(jwt, '/api/dashboard/v1/products', {
    method: 'POST',
    body: JSON.stringify({ externalProductId, name, category, genderTarget }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(`createProduct failed [${res.status}]: ${body.error?.message ?? ''}`);
  }
  return (await res.json()).data;
}

export async function updateProduct(jwt, externalProductId, updates) {
  const products = await getProducts(jwt);
  const product = products.find((p) => p.externalProductId === externalProductId);
  if (!product) {
    console.warn(`[fitvision] updateProduct: product not found externalProductId=${externalProductId}`);
    return;
  }
  const res = await _authedFetch(jwt, `/api/dashboard/v1/products/${product.id}`, {
    method: 'PUT',
    body: JSON.stringify({ ...updates, externalProductId }),
  });
  if (!res.ok) {
    console.warn(`[fitvision] updateProduct failed [${res.status}] externalProductId=${externalProductId}`);
  }
}

export async function deleteProduct(jwt, externalProductId) {
  const products = await getProducts(jwt);
  const product = products.find((p) => p.externalProductId === externalProductId);
  if (!product) {
    console.warn(`[fitvision] deleteProduct: product not found externalProductId=${externalProductId}`);
    return;
  }
  const res = await _authedFetch(jwt, `/api/dashboard/v1/products/${product.id}`, {
    method: 'DELETE',
  });
  if (!res.ok) {
    console.warn(`[fitvision] deleteProduct failed [${res.status}] externalProductId=${externalProductId}`);
  }
}

// ── Private helpers ───────────────────────────────────────────────────────────

function _authedFetch(jwt, path, options = {}) {
  return fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwt}`,
      ...options.headers,
    },
  });
}
