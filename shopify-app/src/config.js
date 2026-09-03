import 'dotenv/config';

// HOST_NAME may be given with or without a scheme (the .env.example shows it with
// https://). @shopify/shopify-api wants a bare host in `hostName` + a separate
// `hostScheme`, while our webhook/ScriptTag code needs the full origin.
const rawHost = (process.env.HOST_NAME || '').trim().replace(/\/+$/, '');
const hostScheme = rawHost.startsWith('http://') ? 'http' : 'https';
const hostName = rawHost.replace(/^https?:\/\//, '');

export const config = {
  shopify: {
    apiKey: process.env.SHOPIFY_API_KEY,
    apiSecret: process.env.SHOPIFY_API_SECRET,
    scopes: ['read_products', 'write_script_tags'],
    hostName,
    hostScheme,
    hostUrl: hostName ? `${hostScheme}://${hostName}` : '',
    apiVersion: '2025-07',
  },
  fitvision: {
    apiUrl: process.env.FITVISION_API_URL || 'http://localhost:8080',
    shopifySharedSecret: process.env.FITVISION_SHOPIFY_SHARED_SECRET,
    adminEmail: process.env.FITVISION_ADMIN_EMAIL,
    adminPassword: process.env.FITVISION_ADMIN_PASSWORD,
  },
  session: {
    secret: process.env.SESSION_SECRET || 'fitvision-dev-session-secret-change-in-production',
  },
  port: parseInt(process.env.PORT || '3001', 10),
};
