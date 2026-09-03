import { Router } from 'express';
import { shopifyApi, ApiVersion } from '@shopify/shopify-api';
import '@shopify/shopify-api/adapters/node';
import { config } from './config.js';
import { connectShopifyStore } from './fitvision.js';
import { validateShopifyHmac, isValidShopDomain } from './middleware.js';
import { setShopCredentials } from './store.js';
import { registerWebhooks } from './webhooks.js';
import { injectWidget } from './inject.js';
import { syncAllProducts } from './sync.js';

const shopify = shopifyApi({
  apiKey: config.shopify.apiKey,
  apiSecretKey: config.shopify.apiSecret,
  scopes: config.shopify.scopes,
  hostName: config.shopify.hostName,
  hostScheme: config.shopify.hostScheme,
  apiVersion: ApiVersion.July25,
  isEmbeddedApp: true,
});

export const authRouter = Router();

/**
 * Step 1 — Begin OAuth: redirect the store owner to Shopify consent page.
 * GET /auth?shop=<shop>.myshopify.com
 */
authRouter.get('/', async (req, res) => {
  const { shop } = req.query;

  if (!isValidShopDomain(shop)) {
    return res.status(400).send('Invalid or missing shop parameter.');
  }

  try {
    await shopify.auth.begin({
      shop,
      callbackPath: '/auth/callback',
      isOnline: false, // offline token — permanent access
      rawRequest: req,
      rawResponse: res,
    });
  } catch (err) {
    console.error('[auth] begin error:', err.message);
    res.status(500).send('OAuth initiation failed.');
  }
});

/**
 * Step 2 — OAuth callback: exchange code for access token, link to FitVision,
 * register webhooks, inject widget ScriptTag, and kick off product sync.
 * GET /auth/callback
 */
authRouter.get('/callback', async (req, res) => {
  if (!validateShopifyHmac(req.query)) {
    console.warn('[auth] callback rejected — invalid HMAC');
    return res.status(403).send('Invalid HMAC signature.');
  }

  let shopifySession;
  try {
    const callbackResponse = await shopify.auth.callback({
      rawRequest: req,
      rawResponse: res,
    });
    shopifySession = callbackResponse.session;
  } catch (err) {
    console.error('[auth] callback error:', err.message);
    return res.status(500).send('OAuth callback failed.');
  }

  const { shop, accessToken } = shopifySession;

  try {
    const { jwt, apiKeyPublic, storeId } = await connectShopifyStore(
      shop,
      accessToken,
      req.query.shop_name || shop,
    );

    // Store credentials in session (for /app routes) and in-memory store (for webhook handlers)
    req.session.fitvisionJwt = jwt;
    req.session.apiKeyPublic = apiKeyPublic;
    req.session.storeId = storeId;
    req.session.shop = shop;
    req.session.shopifyAccessToken = accessToken;

    setShopCredentials(shop, { jwt, accessToken, apiKeyPublic, storeId });

    console.info(`[auth] store connected: shop=${shop} storeId=${storeId}`);

    // Run post-install tasks asynchronously — don't block the redirect
    setImmediate(() => runPostInstall(shop, accessToken, jwt));

    res.redirect('/app');
  } catch (err) {
    console.error('[auth] FitVision connect error:', err.message);
    res.status(500).send('Failed to link Shopify store to FitVision.');
  }
});

async function runPostInstall(shop, accessToken, jwt) {
  // Register webhooks
  try {
    await registerWebhooks(shop, accessToken);
  } catch (err) {
    console.error('[auth] webhook registration failed:', err.message);
  }

  // Inject widget ScriptTag
  try {
    await injectWidget(shop, accessToken);
  } catch (err) {
    console.error('[auth] widget injection failed:', err.message);
  }

  // Sync products
  try {
    await syncAllProducts(shop, accessToken, jwt);
  } catch (err) {
    console.error('[auth] product sync failed:', err.message);
  }
}
