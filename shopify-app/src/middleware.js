import crypto from 'crypto';
import { config } from './config.js';

/**
 * Validates the HMAC signature on Shopify OAuth callback requests.
 * Rejects the request if the HMAC is missing or does not match.
 */
export function validateShopifyHmac(query) {
  const { hmac, ...rest } = query;
  if (!hmac) return false;

  const message = Object.keys(rest)
    .sort()
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(rest[k])}`)
    .join('&');

  const computed = crypto
    .createHmac('sha256', config.shopify.apiSecret)
    .update(message)
    .digest('hex');

  try {
    return crypto.timingSafeEqual(Buffer.from(computed, 'hex'), Buffer.from(hmac, 'hex'));
  } catch {
    return false;
  }
}

/**
 * Validates that the shop parameter is a valid *.myshopify.com domain.
 */
export function isValidShopDomain(shop) {
  return typeof shop === 'string' && /^[a-zA-Z0-9][a-zA-Z0-9-]*\.myshopify\.com$/.test(shop);
}

/**
 * Express middleware that requires a valid session with a FitVision JWT.
 * Redirects to /auth if the session is missing.
 */
export function requireSession(req, res, next) {
  if (!req.session?.fitvisionJwt || !req.session?.shop) {
    const shop = req.query.shop;
    if (shop && isValidShopDomain(shop)) {
      return res.redirect(`/auth?shop=${shop}`);
    }
    return res.status(401).json({ error: 'Not authenticated. Visit /auth?shop=<shop>' });
  }
  next();
}
