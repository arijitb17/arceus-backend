export type DeploymentEnvironment = "DEVELOPMENT" | "TESTING" | "STAGING" | "PRODUCTION";

export type DeploymentStatus =
  | "CREATED"
  | "PENDING"
  | "APPROVED"
  | "DEPLOYING"
  | "RUNNING"
  | "FAILED"
  | "ROLLED_BACK"
  | "CANCELLED";

export interface DeploymentResponse {
  id: string;
  serviceName: string;
  serviceVersion: string;
  dockerImage: string;
  environment: DeploymentEnvironment;
  status: DeploymentStatus;
  createdBy: string;
  approvedBy: string | null;
  rollbackVersion: string | null;
  rolledBackFromId: string | null;
  correlationId: string | null;
  releaseNotes: string | null;
  requiresApproval: boolean;
  schedulerJobId: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  scheduledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface CreateDeploymentRequest {
  serviceName: string;
  serviceVersion: string;
  dockerImage: string;
  environment: DeploymentEnvironment;
  createdBy: string;
  releaseNotes?: string;
  correlationId?: string;
  scheduledAt?: string;
}

export interface RollbackRequest {
  targetVersion?: string;
  initiatedBy?: string;
  correlationId?: string;
}
