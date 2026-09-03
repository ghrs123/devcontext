import crypto from 'crypto';
import { Router } from 'express';
import { config } from './config.js';
import { clearShopCredentials, getShopCredentials } from './store.js';
import { createProduct, deactivateStore, deleteProduct, getShopifyStatus, updateProduct } from './fitvision.js';

const SHOPIFY_API_VERSION = config.shopify.apiVersion;
const WIDGET_HOST = config.shopify.hostUrl;

/**
 * Registers webhook subscriptions with Shopify for the given shop.
 * Safe to call multiple times — Shopify deduplicates by address.
 */
export async function registerWebhooks(shop, accessToken) {
  const topics = ['products/create', 'products/update', 'products/delete', 'app/uninstalled'];
  const headers = {
    'X-Shopify-Access-Token': accessToken,
    'Content-Type': 'application/json',
  };

  for (const topic of topics) {
    const address = `${WIDGET_HOST}/webhooks/${topic}`;
    try {
      const res = await fetch(
        `https://${shop}/admin/api/${SHOPIFY_API_VERSION}/webhooks.json`,
        {
          method: 'POST',
          headers,
          body: JSON.stringify({ webhook: { topic, address, format: 'json' } }),
        },
      );

      if (res.ok) {
        console.info(`[webhooks] registered topic=${topic} address=${address}`);
      } else {
        // 422 means webhook already exists — not an error
        const body = await res.json();
        const alreadyExists = body.errors?.address?.includes('for this topic has already been taken');
        if (alreadyExists) {
          console.info(`[webhooks] already registered topic=${topic}`);
        } else {
          console.warn(`[webhooks] registration failed topic=${topic} status=${res.status}`, body.errors);
        }
      }
    } catch (err) {
      console.error(`[webhooks] register error topic=${topic}:`, err.message);
    }
  }
}

// ── HMAC validation ───────────────────────────────────────────────────────────

function isValidWebhookHmac(rawBody, hmacHeader) {
  if (!hmacHeader || !rawBody) return false;
  const computed = crypto
    .createHmac('sha256', config.shopify.apiSecret)
    .update(rawBody)
    .digest('base64');
  try {
    return crypto.timingSafeEqual(
      Buffer.from(computed, 'base64'),
      Buffer.from(hmacHeader, 'base64'),
    );
  } catch {
    return false;
  }
}

function validateWebhook(req, res) {
  const hmac = req.headers['x-shopify-hmac-sha256'];
  if (!isValidWebhookHmac(req.rawBody, hmac)) {
    console.warn('[webhooks] rejected — invalid HMAC');
    res.status(401).send('Invalid HMAC');
    return false;
  }
  return true;
}

function getShopJwt(req) {
  const shop = req.headers['x-shopify-shop-domain'];
  if (!shop) return null;
  return getShopCredentials(shop)?.jwt ?? null;
}

// ── Webhook router ────────────────────────────────────────────────────────────

export const webhooksRouter = Router();

webhooksRouter.post('/products/create', (req, res) => {
  if (!validateWebhook(req, res)) return;
  res.sendStatus(200); // Respond immediately — Shopify requires < 5s

  setImmediate(async () => {
    const jwt = getShopJwt(req);
    if (!jwt) return console.warn('[webhooks] products/create — no JWT for shop');
    const product = req.body;
    try {
      await createProduct(jwt, {
        externalProductId: String(product.id),
        name: product.title,
        category: product.product_type ?? 'other',
        genderTarget: 'UNISEX',
      });
      console.info(`[webhooks] products/create synced id=${product.id}`);
    } catch (err) {
      console.error(`[webhooks] products/create error id=${product.id}:`, err.message);
    }
  });
});

webhooksRouter.post('/products/update', (req, res) => {
  if (!validateWebhook(req, res)) return;
  res.sendStatus(200);

  setImmediate(async () => {
    const jwt = getShopJwt(req);
    if (!jwt) return console.warn('[webhooks] products/update — no JWT for shop');
    const product = req.body;
    try {
      await updateProduct(jwt, String(product.id), {
        name: product.title,
        category: product.product_type ?? 'other',
      });
      console.info(`[webhooks] products/update synced id=${product.id}`);
    } catch (err) {
      console.error(`[webhooks] products/update error id=${product.id}:`, err.message);
    }
  });
});

webhooksRouter.post('/products/delete', (req, res) => {
  if (!validateWebhook(req, res)) return;
  res.sendStatus(200);

  setImmediate(async () => {
    const jwt = getShopJwt(req);
    if (!jwt) return console.warn('[webhooks] products/delete — no JWT for shop');
    const product = req.body;
    try {
      await deleteProduct(jwt, String(product.id));
      console.info(`[webhooks] products/delete synced id=${product.id}`);
    } catch (err) {
      console.error(`[webhooks] products/delete error id=${product.id}:`, err.message);
    }
  });
});

webhooksRouter.post('/app/uninstalled', (req, res) => {
  if (!validateWebhook(req, res)) return;
  res.sendStatus(200);

  setImmediate(async () => {
    const shop = req.headers['x-shopify-shop-domain'];
    if (!shop) {
      console.warn('[webhooks] app/uninstalled — missing shop domain header');
      return;
    }

    try {
      let storeId = getShopCredentials(shop)?.storeId;
      if (!storeId) {
        const status = await getShopifyStatus(shop);
        storeId = status?.storeId ?? null;
      }

      if (!storeId) {
        console.warn(`[webhooks] app/uninstalled — no storeId found for shop=${shop}`);
        clearShopCredentials(shop);
        return;
      }

      await deactivateStore(storeId);
      clearShopCredentials(shop);
      console.info(`[webhooks] Store ${shop} uninstalled — account deactivated`);
    } catch (err) {
      console.error(`[webhooks] app/uninstalled error shop=${shop}:`, err.message);
    }
  });
});
