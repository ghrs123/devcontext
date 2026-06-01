import 'dotenv/config';

export const config = {
  shopify: {
    apiKey: process.env.SHOPIFY_API_KEY,
    apiSecret: process.env.SHOPIFY_API_SECRET,
    scopes: ['read_products', 'write_script_tags'],
    hostName: process.env.HOST_NAME,
    apiVersion: '2024-01',
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
