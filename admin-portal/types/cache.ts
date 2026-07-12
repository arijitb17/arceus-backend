export interface CacheStatsResponse {
  hitCount: number;
  missCount: number;
  hitRatio: number;
  totalKeys: number;
  memoryUsageBytes: number;
  expiredKeys: number;
  evictionCount: number;
}
