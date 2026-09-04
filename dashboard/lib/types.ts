export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: {
    code: string;
    message: string;
    field?: string | null;
  } | null;
  meta?: {
    requestId?: string;
    timestamp?: string;
  };
}

export interface ApiKeys {
  apiKeyPublic: string;
  apiKeySecret: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  apiKeyPublic: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  platform?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface StoreProfile {
  id: string;
  name: string;
  email: string;
  plan: string;
  platform: string;
  apiKeyPublic: string;
  subscriptionStatus: string;
}

export interface UpdateStoreProfileRequest {
  name?: string;
  platform?: string;
}

export interface Product {
  id: string;
  externalProductId: string;
  name: string;
  category: string;
  genderTarget: string;
  brandId?: string;
  brandName?: string;
  hasSizeChart: boolean;
}

export interface Brand {
  id: string;
  name: string;
  slug: string;
  source: 'store_uploaded' | 'fitvision_managed';
  isGlobal: boolean;
  lastScrapedAt?: string | null;
}

export type ScrapeJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface ScrapeJobResponse {
  id: string;
  brandId: string;
  brandName: string | null;
  status: ScrapeJobStatus;
  startedAt: string | null;
  completedAt: string | null;
  pagesScraped: number | null;
  entriesFound: number | null;
  errorMessage: string | null;
  createdAt: string;
  durationSeconds: number | null;
  isStale: boolean;
}

export interface ProductRequest {
  externalProductId: string;
  name: string;
  category?: string;
  genderTarget?: string;
  brandId?: string;
}

export interface SizeEntryData {
  sizeLabel: string;
  chestMin?: number | null;
  chestMax?: number | null;
  waistMin?: number | null;
  waistMax?: number | null;
  hipMin?: number | null;
  hipMax?: number | null;
  heightMin?: number | null;
  heightMax?: number | null;
}

export interface SizeChartUploadResult {
  sizeChartId: string;
  version: number;
  entriesSaved: number;
  warnings?: string[];
  success?: boolean;
}

export interface ProductRecommendationStat {
  productId: string;
  productName: string;
  recommendationCount: number;
  averageConfidence: number;
}

export interface AnalyticsSummary {
  totalRecommendations: number;
  recommendationsLast30Days: number;
  averageConfidenceScore: number;
  qualityDistribution: Record<string, number>;
  topProducts: ProductRecommendationStat[];
}

export interface ProductHealthRow {
  productId: string;
  productName: string;
  hasSizeChart: boolean;
  totalRecommendations: number;
  noMatchCount: number;
  noMatchRate: number;
  averageConfidence: number;
  attentionScore: number;
  reasons: Array<'NO_SIZE_CHART' | 'HIGH_NO_MATCH' | 'LOW_CONFIDENCE'>;
}

export interface SimulateRequest {
  productId?: string;
  externalProductId?: string;
  heightCm: number;
  weightKg: number;
  gender?: string;
  age?: number;
}

export interface SimulateResponse {
  productName: string;
  brandName: string | null;
  hasSizeChart: boolean;
  recommendedSize: string | null;
  confidenceScore: number;
  confidenceLabel: 'High' | 'Medium' | 'Low';
  quality: 'EXACT' | 'PARTIAL' | 'CLOSEST' | 'NO_MATCH';
  estimatedProfile: { bmi: number; chestCm: number; waistCm: number; hipCm: number };
  sizeChart: Array<{
    size: string;
    chest: { min: number | null; max: number | null };
    waist: { min: number | null; max: number | null };
    hip: { min: number | null; max: number | null };
    height: { min: number | null; max: number | null };
    recommended: boolean;
  }>;
}

export interface SpringPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminBrandRecommendationStat {
  brandId: string | null;
  brandName: string;
  recommendationCount: number;
  averageConfidence: number;
}

export interface AdminMetrics {
  totalStores: number;
  activeStores: number;
  totalRecommendations: number;
  recommendationsLast30Days: number;
  averageConfidenceScore: number;
  qualityDistribution: Record<string, number>;
  topBrands: AdminBrandRecommendationStat[];
}

export interface StoreAdminView {
  id: string;
  name: string;
  email: string;
  plan: string;
  role: string;
  status: 'ACTIVE' | 'INACTIVE' | string;
  platform: string;
  createdAt: string;
  totalProducts: number;
  totalRecommendations: number;
  lastRecommendationAt: string | null;
  subscriptionStatus: string | null;
  stripeCustomerIdMasked: string | null;
  subscriptionCurrentPeriodEnd: string | null;
}

export interface AdminRecommendation {
  id: string;
  tenantId: string;
  storeName: string;
  productId: string;
  productName: string;
  recommendedSize: string;
  confidenceScore: number;
  quality: 'EXACT' | 'PARTIAL' | 'CLOSEST' | 'NO_MATCH' | string;
  createdAt: string;
}

export interface GlobalBrandSizeChartVersion {
  id: string;
  version: number;
  active: boolean;
  source: string;
  createdAt: string;
}

export interface AdminRecommendationFilters {
  tenantId?: string;
  productId?: string;
  quality?: string;
}

export interface BillingStatusResponse {
  plan: 'FREE' | 'STARTER' | 'PRO' | 'TEAM';
  subscriptionStatus: 'active' | 'inactive' | 'past_due' | 'canceled' | string;
  currentPeriodEnd: string | null;
  productsUsed: number;
  productsLimit: number;
  recommendationsUsed: number;
  recommendationsLimit: number;
}

export interface AdminHealthResponse {
  database: { status: 'UP' | 'DOWN' | string; latencyMs: number };
  recommendationEngine: { avgLatencyMs: number | null; p95LatencyMs: number | null };
  scrapeJobs: { running: number; failedLast7Days: number };
  storeActivity: {
    recommendationsLast24h: number;
    activeStoresLast24h: number;
    lastRecommendationAt: string | null;
  };
  brandScrapes: BrandScrapeStatus[];
}

export interface BrandScrapeStatus {
  brandId: string;
  brandName: string;
  status: ScrapeJobStatus | null;
  timestamp: string | null;
  entriesFound: number | null;
  scraperAvailable: boolean;
}

export interface RecommendationStatsResponse {
  p50LatencyMs: number | null;
  p95LatencyMs: number | null;
  p99LatencyMs: number | null;
  qualityDistribution: Record<string, number>;
  topStores: StoreRecommendationStat[];
}

export interface StoreRecommendationStat {
  storeId: string;
  storeName: string;
  recommendationCount: number;
}

export interface ScrapeTriggerAllResponse {
  triggered: number;
  skipped: number;
}
