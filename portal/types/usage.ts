export interface UsageDayPoint {
  day: string;
  route: string;
  requestCount: number;
  errorCount: number;
}

/** GET /api/monitoring/projects/{projectId}/usage?organizationId=&environment= - monitoring-service's ProjectUsageController. */
export interface ProjectUsageResponse {
  from: string;
  to: string;
  totalRequests: number;
  totalErrors: number;
  points: UsageDayPoint[];
}
