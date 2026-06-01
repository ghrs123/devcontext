# FitVision Shopify App - Local Development

## Prerequisites
- Node.js 20+
- ngrok account (free tier works)
- Shopify Partner account
- FitVision backend running at http://localhost:8080

## Setup

1. Install dependencies

```bash
npm install
```

2. Expose local server with ngrok

```bash
ngrok http 3001
```

Copy the https URL (example: https://abc123.ngrok.io)

3. Create Shopify App in Partner Dashboard
- Go to partners.shopify.com
- Create app -> Custom app
- App URL: https://abc123.ngrok.io
- Redirect URL: https://abc123.ngrok.io/auth/callback
- Scopes: read_products, write_script_tags
- Copy API Key and API Secret

4. Configure environment

```bash
cp .env.example .env
```

Fill in at least:
- SHOPIFY_API_KEY
- SHOPIFY_API_SECRET
- HOST_NAME
- FITVISION_SHOPIFY_SHARED_SECRET
- FITVISION_ADMIN_EMAIL
- FITVISION_ADMIN_PASSWORD

5. Start the app

```bash
npm start
```

6. Install on development store

Visit:

https://abc123.ngrok.io/auth?shop=your-dev-store.myshopify.com

## Useful scripts

```bash
npm run dev      # Run with nodemon
npm run tunnel   # Open ngrok tunnel on port 3001
```
