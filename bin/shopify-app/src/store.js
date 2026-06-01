/**
 * In-memory shop credentials store.
 * Maps shop domain → { jwt, accessToken, apiKeyPublic, storeId }.
 *
 * In production, replace with Redis or a database-backed session store
 * so credentials survive server restarts and horizontal scaling.
 */
const shopCredentials = new Map();

export function setShopCredentials(shop, credentials) {
  shopCredentials.set(shop, credentials);
}

export function getShopCredentials(shop) {
  return shopCredentials.get(shop);
}

export function clearShopCredentials(shop) {
  shopCredentials.delete(shop);
}
