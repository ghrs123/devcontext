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
