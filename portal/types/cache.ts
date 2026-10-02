export type CacheDataType = "STRING" | "JSON" | "NUMBER" | "HASH" | "LIST" | "SET";
export type TtlPolicy = "NO_EXPIRY" | "SLIDING" | "ABSOLUTE" | "CUSTOM";

export interface CacheStatsResponse {
  hitCount: number;
  missCount: number;
  hitRatio: number;
  totalKeys: number;
  memoryUsageBytes: number;
  expiredKeys: number;
  evictionCount: number;
}

export interface CacheRequest {
  namespace?: string;
  key?: string;
  value: unknown;
  type?: CacheDataType;
  ttlPolicy?: TtlPolicy;
  ttlSeconds?: number;
  absoluteExpireAt?: string;
}

export interface CacheResponse {
  namespace: string;
  key: string;
  value: unknown;
  type: CacheDataType;
  serialization: string;
  ttlPolicy: TtlPolicy;
  ttlSecondsRemaining: number;
  createdAt: string;
  updatedAt: string;
}
