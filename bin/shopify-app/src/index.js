import 'dotenv/config';
import path from 'path';
import { fileURLToPath } from 'url';
import express from 'express';
import session from 'express-session';
import { authRouter } from './auth.js';
import { webhooksRouter } from './webhooks.js';
import { requireSession } from './middleware.js';
import { getShopifyStatus, initializeAdminJwtManagement } from './fitvision.js';
import { syncAllProducts } from './sync.js';
import { config } from './config.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();

// ── Raw body capture for webhooks (must be before express.json) ───────────────
// Webhook HMAC validation requires the raw bytes — store them in req.rawBody.
app.use(
  express.json({
    verify: (req, _res, buf) => {
      if (req.path.startsWith('/webhooks')) {
        req.rawBody = buf;
      }
    },
  }),
);
app.use(express.urlencoded({ extended: false }));

// ── Session ───────────────────────────────────────────────────────────────────
app.use(
  session({
    secret: config.session.secret,
    resave: false,
    saveUninitialized: false,
    cookie: {
      secure: process.env.NODE_ENV === 'production',
      httpOnly: true,
      maxAge: 24 * 60 * 60 * 1000, // 24h
    },
  }),
);

// ── Routes ────────────────────────────────────────────────────────────────────

// OAuth — no session required
app.use('/auth', authRouter);

// Webhooks — HMAC validated inside the router
app.use('/webhooks', webhooksRouter);

// Embedded app UI
app.get('/app', requireSession, (_req, res) => {
  res.sendFile(path.join(__dirname, 'ui', 'app.html'));
});

// Sync trigger — called by the "Sync products now" button in app.html
app.post('/app/sync', requireSession, async (req, res) => {
  const { shop, shopifyAccessToken, fitvisionJwt } = req.session;
  try {
    const result = await syncAllProducts(shop, shopifyAccessToken, fitvisionJwt);
    res.json(result);
  } catch (err) {
    console.error('[/app/sync] error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// Connection status — used by app.html
app.get('/status', async (req, res) => {
  const shop = req.query.shop || req.session?.shop;
  if (!shop) return res.status(400).json({ error: 'shop parameter required' });
  try {
    const status = await getShopifyStatus(shop);
    res.json(status);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── Start ─────────────────────────────────────────────────────────────────────
initializeAdminJwtManagement().catch((err) => {
  console.error('[fitvision-shopify-app] admin JWT bootstrap failed:', err.message);
});

app.listen(config.port, () => {
  console.log(`[fitvision-shopify-app] running on port ${config.port}`);
  console.log(`[fitvision-shopify-app] Shopify API key: ${config.shopify.apiKey}`);
  console.log(`[fitvision-shopify-app] FitVision API:   ${config.fitvision.apiUrl}`);
  console.log(`[fitvision-shopify-app] Host:            ${config.shopify.hostName}`);
});
