import { config } from './config.js';
import { createProduct } from './fitvision.js';

const SHOPIFY_API_VERSION = config.shopify.apiVersion;

const CATEGORY_MAP = {
  'T-Shirts': 'tops', Shirts: 'tops', Blouses: 'tops', Tops: 'tops',
  Pants: 'bottoms', Jeans: 'bottoms', Shorts: 'bottoms', Skirts: 'bottoms', Trousers: 'bottoms',
  Dresses: 'dresses',
  Jackets: 'outerwear', Coats: 'outerwear', Sweaters: 'outerwear',
};

function mapCategory(productType) {
  return CATEGORY_MAP[productType] ?? 'other';
}

/**
 * Fetches all products from Shopify and creates them in FitVision.
 * Idempotent: if a product already exists (duplicate externalProductId), the error is logged and skipped.
 * Returns { synced, failed, total }.
 */
export async function syncAllProducts(shop, accessToken, jwt) {
  let synced = 0;
  let failed = 0;
  const shopifyProducts = await fetchShopifyProducts(shop, accessToken);
  const total = shopifyProducts.length;

  console.info(`[sync] starting product sync for shop=${shop}, total=${total}`);

  for (const product of shopifyProducts) {
    try {
      await createProduct(jwt, {
        externalProductId: String(product.id),
        name: product.title,
        category: mapCategory(product.product_type),
        genderTarget: 'UNISEX',
      });
      synced++;
      console.info(`[sync] ${synced}/${total} — synced: ${product.title}`);
    } catch (err) {
      failed++;
      // Duplicate externalProductId is expected on re-sync — not a hard failure
      console.warn(`[sync] skipped product id=${product.id} title="${product.title}": ${err.message}`);
    }
  }

  console.info(`[sync] complete for shop=${shop}: synced=${synced} failed=${failed} total=${total}`);
  return { synced, failed, total };
}

/**
 * Fetches all products from the Shopify Admin REST API.
 * Handles pagination via the Link header (up to 250 per page).
 */
async function fetchShopifyProducts(shop, accessToken) {
  const headers = { 'X-Shopify-Access-Token': accessToken };
  const products = [];

  let url = `https://${shop}/admin/api/${SHOPIFY_API_VERSION}/products.json?limit=250&fields=id,title,product_type`;

  while (url) {
    const res = await fetch(url, { headers });
    if (!res.ok) {
      throw new Error(`Shopify products fetch failed [${res.status}] for shop=${shop}`);
    }
    const { products: page } = await res.json();
    products.push(...page);

    // Follow rel="next" pagination link if present
    const linkHeader = res.headers.get('link') ?? '';
    const nextMatch = linkHeader.match(/<([^>]+)>;\s*rel="next"/);
    url = nextMatch ? nextMatch[1] : null;
  }

  return products;
}
