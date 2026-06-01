import { config } from './config.js';

const WIDGET_SRC = 'https://cdn.fitvision.io/widget/fitvision-widget.min.js';
const SHOPIFY_API_VERSION = config.shopify.apiVersion;

/**
 * Injects the FitVision widget into the Shopify store via ScriptTag API.
 * Idempotent — checks for an existing ScriptTag with the same src before creating.
 */
export async function injectWidget(shop, accessToken) {
  const headers = {
    'X-Shopify-Access-Token': accessToken,
    'Content-Type': 'application/json',
  };
  const baseUrl = `https://${shop}/admin/api/${SHOPIFY_API_VERSION}`;

  // Check if ScriptTag already exists
  const listRes = await fetch(
    `${baseUrl}/script_tags.json?src=${encodeURIComponent(WIDGET_SRC)}`,
    { headers },
  );

  if (!listRes.ok) {
    throw new Error(`ScriptTag list failed [${listRes.status}] for shop=${shop}`);
  }

  const { script_tags: existing } = await listRes.json();
  if (existing.length > 0) {
    console.info(`[inject] Widget ScriptTag already exists for shop=${shop}`);
    return;
  }

  // Create ScriptTag
  const createRes = await fetch(`${baseUrl}/script_tags.json`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      script_tag: {
        event: 'onload',
        src: WIDGET_SRC,
        display_scope: 'online_store',
      },
    }),
  });

  if (!createRes.ok) {
    const text = await createRes.text();
    throw new Error(`ScriptTag creation failed [${createRes.status}]: ${text}`);
  }

  console.info(`[inject] Widget ScriptTag created for shop=${shop}`);
}

/**
 * Removes the FitVision ScriptTag from the store (called on app uninstall).
 */
export async function removeWidget(shop, accessToken) {
  const headers = { 'X-Shopify-Access-Token': accessToken };
  const baseUrl = `https://${shop}/admin/api/${SHOPIFY_API_VERSION}`;

  const listRes = await fetch(
    `${baseUrl}/script_tags.json?src=${encodeURIComponent(WIDGET_SRC)}`,
    { headers },
  );
  if (!listRes.ok) return;

  const { script_tags: existing } = await listRes.json();
  for (const tag of existing) {
    await fetch(`${baseUrl}/script_tags/${tag.id}.json`, {
      method: 'DELETE',
      headers,
    });
    console.info(`[inject] Widget ScriptTag removed id=${tag.id} shop=${shop}`);
  }
}
