export type JobStatus =
  | "PENDING"
  | "SCHEDULED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "RETRYING"
  | "CANCELLED"
  | "EXPIRED"
  | "PAUSED";

export type JobTriggerType = "IMMEDIATE" | "DELAY" | "CRON" | "FIXED_RATE" | "FIXED_DELAY";
export type ExecutionTarget = "HTTP_CALLBACK" | "MESSAGE_PUBLISH";

export interface JobResponse {
  id: string;
  jobName: string;
  serviceName: string;
  triggerType: JobTriggerType;
  cronExpression: string | null;
  delayMs: number | null;
  fixedRateMs: number | null;
  fixedDelayMs: number | null;
  executionTarget: ExecutionTarget;
  callbackUrl: string | null;
  targetExchange: string | null;
  targetRoutingKey: string | null;
  payload: string | null;
  priority: number;
  status: JobStatus;
  retryCount: number;
  maxRetries: number;
  retryDelayMs: number;
  backoffMultiplier: number;
  exponentialBackoff: boolean;
  correlationId: string | null;
  createdAt: string;
  updatedAt: string;
  nextFireAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  expiresAt: string | null;
  lastError: string | null;
}

export interface JobStatsResponse {
  countsByStatus: Record<string, number>;
  totalJobs: number;
  workerPoolSize: number;
  workerActiveCount: number;
  workerQueueSize: number;
  deadLetterCount: number;
}
