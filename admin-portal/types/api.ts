/** The one paginated-list shape every Arceus service returns - see audit-service PagedResponse.java et al. */
export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Error shape returned by every downstream service's GlobalExceptionHandler. */
export interface ServiceApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  correlationId?: string;
  fields?: Record<string, string>;
}

/** Error shape returned by gateway-service (ApiError.java) - has `path`/`requestId` instead of `correlationId`. */
export interface GatewayApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  requestId?: string;
}

export type ApiErrorShape = ServiceApiError | GatewayApiError;

export interface PageParams {
  page?: number;
  size?: number;
}
