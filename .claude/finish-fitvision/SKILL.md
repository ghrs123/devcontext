---
name: finish-fitvision
description: Use when working on getting FitVision (this repo) to its first paying Shopify store. Tracks current milestone, what's verified vs unverified, and the next concrete step. Invoke at the start of any FitVision session with "what's next on FitVision" or similar.
---

# Finish FitVision

FitVision is a multi-tenant SaaS for clothing size recommendations (Shopify App Store). This skill tracks progress toward the first paying store — not the long-term "Fit Intelligence" vision. Don't build toward the long-term vision until there is at least one paying store; see CLAUDE.md for architecture and commands.

## Current verified state (update this section as milestones complete)

- ✅ `mvn compile` — clean build, no errors
- ✅ `mvn test` — unit tests passing (recommendation engine, body profile calculator, size chart matcher, CSV/Excel parsers, DatabaseHealthIndicator)
- ✅ `mvn verify` — 75/75 passing (unit + Testcontainers integration incl. `BillingFlowIT`, `ShopifyWebhookIT`, `StoreRegistrationFlowIT`). Milestone 1 done.
- 🔄 Deployed to a real environment (Railway + Neon + Stripe TEST keys, per README) — **milestone 2, in progress**
  - ✅ `prod` profile verified locally against the real Neon DB: boots, Flyway V1–V10 applied,
    `/actuator/health` UP, register/login/billing-status 200, checkout → clean `STRIPE_ERROR` with
    placeholder keys. Fixed 4 pre-existing deploy blockers along the way (Neon URL post-processor was
    mis-registered + `"jdbc:"+url` broke on credentials; health-probe threshold; `postgres://` branch).
  - ⬜ Actual Railway (or Render/Fly) deploy + smoke test — not done. `mvn verify` (Docker) to re-run.
- ⬜ Installed on a Shopify dev store — not yet tested end to end
- ⬜ Widget confirmed working against a real store's products
- ⬜ First scraper (start with Zara) confirmed importing a real size chart
- ⬜ Submitted to Shopify App Store
- ✅ Bloqueadores de código para deploy (health check, env vars) — corrigidos, com teste


## The only goal right now: first paying store

Do not add features, refactor for the "Fit Intelligence" vision, or start on the `dashboard` beyond what's needed for a merchant to configure one product, until this list is checked off. If a task doesn't move an item above from ⬜ to ✅, it's out of scope for now.

## Milestone order (do not skip ahead)

1. **`mvn verify` locally** (needs Docker). Report exact failures if any — don't guess fixes without seeing the real error.
2. **Deploy to Railway + Neon with Stripe TEST keys** (never live keys until step 1 passes and this has been manually smoke-tested). Follow README exactly. If a step in the README is wrong or outdated, fix the README in the same commit.
3. **Create a Shopify dev store**, install the app, complete OAuth, sync one product, set its size chart manually (skip scraper for this first pass).
4. **Confirm the widget** shows a real recommendation on that dev store's product page. Screenshot it.
5. **Test one scraper** (Zara) against a real product URL, confirm it populates a size chart correctly without corrupting existing data.
6. **Only after 1-5 are all ✅**: prepare Shopify App Store listing assets and submit for review.

## When asked "what's next"

1. Read the "Current verified state" checklist above.
2. Find the first ⬜ item in milestone order.
3. Propose that as the next session's scope — nothing beyond it.
4. If the person asks for something later in the list (or outside it, like a new feature), name the gap: "milestone N isn't done yet — do you want to skip ahead anyway, or finish that first?" Then follow their answer.

## Verification discipline

Every session that touches billing, Shopify OAuth, tenant-scoped data, or the widget should end with either:
- `mvn test` (fast, no Docker) for logic-level changes, or
- `mvn verify` (needs Docker) for anything touching the integration flows

Don't mark a milestone ✅ based on "looks right" — only based on a passing test, a real deploy log, or a screenshot of the actual behavior.

## Updating this skill

After completing a milestone, edit the checklist above in the same commit as the code change. This file should always reflect the true current state, not the plan from when it was written.
