export type ServiceStatus = "UP" | "DOWN" | "DEGRADED" | string;

export interface ServiceHealthDto {
  serviceName: string;
  status: ServiceStatus;
  databaseStatus: string;
  cacheStatus: string;
  rabbitStatus: string;
  grpcStatus: string;
  apiAvailable: boolean;
  responseTimeMs: number;
  uptimeSeconds: number;
  version: string;
  errorMessage: string | null;
  checkedAt: string;
}

export interface PlatformHealthResponse {
  overallStatus: string;
  services: ServiceHealthDto[];
  generatedAt: string;
}

export interface ServiceDetailResponse {
  current: ServiceHealthDto;
  history: ServiceHealthDto[];
}

export type AlertType =
  | "SERVICE_DOWN"
  | "HIGH_CPU"
  | "HIGH_MEMORY"
  | "HIGH_ERROR_RATE"
  | "HIGH_RESPONSE_TIME"
  | "RABBITMQ_OFFLINE"
  | "REDIS_OFFLINE"
  | "DATABASE_OFFLINE";

export type AlertSeverity = "CRITICAL" | "WARNING" | "INFO";

export interface AlertRuleResponse {
  id: string;
  name: string;
  type: AlertType;
  serviceName: string | null;
  thresholdValue: number;
  severity: AlertSeverity;
  enabled: boolean;
  description: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AlertRuleRequest {
  name: string;
  type: AlertType;
  serviceName?: string | null;
  thresholdValue: number;
  severity: AlertSeverity;
  enabled?: boolean;
  description?: string | null;
}

export interface AlertEventResponse {
  id: string;
  ruleName: string;
  type: string;
  serviceName: string | null;
  severity: string;
  message: string;
  state: string;
  metricValue: number;
  triggeredAt: string;
  resolvedAt: string | null;
}

export interface PlatformMetricsSummary {
  totalRequestCount: number;
  totalErrorCount: number;
  avgSuccessRate: number | null;
  avgResponseTimeMs: number | null;
}

export interface ResourceUsageSummary {
  avgCpuUsagePercent: number | null;
  totalMemoryUsedMb: number;
  totalMemoryMaxMb: number;
  totalThreadCount: number;
}

export interface DashboardResponse {
  overallStatus: string;
  services: ServiceHealthDto[];
  activeAlerts: AlertEventResponse[];
  recentAlerts: AlertEventResponse[];
  platformMetrics: PlatformMetricsSummary;
  resourceUsage: ResourceUsageSummary;
  generatedAt: string;
}

export interface StatsResponse {
  totalServices: number;
  servicesUp: number;
  servicesDown: number;
  servicesDegraded: number;
  activeAlertsCount: number;
  alertsTriggeredLast24h: number;
  avgPlatformResponseTimeMs: number | null;
  platformAvailabilityPercentage: number | null;
  generatedAt: string;
}

export interface ServiceMetricsDetailResponse {
  current: ServiceMetricsDto;
  history: ServiceMetricsDto[];
}

export interface ServiceMetricsDto {
  serviceName: string;
  requestCount: number | null;
  errorCount: number | null;
  successRate: number | null;
  avgResponseTimeMs: number | null;
  cpuUsagePercent: number | null;
  memoryUsedMb: number | null;
  memoryMaxMb: number | null;
  jvmHeapUsedMb: number | null;
  threadCount: number | null;
  activeConnections: number | null;
  queueSize: number | null;
  cacheHitRatio: number | null;
  cacheMissRatio: number | null;
  schedulerActiveJobs: number | null;
  schedulerFailedJobs: number | null;
  featureFlagEvaluations: number | null;
  rateLimitRequests: number | null;
  rateLimitBlocked: number | null;
  capturedAt: string;
}
