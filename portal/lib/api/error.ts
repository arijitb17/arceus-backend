import { isAxiosError } from "axios";
import type { ApiErrorShape } from "@/types/api";

/** Reads either error shape (Gateway's or a downstream service's) and returns one string for toasts/inline errors. */
export function getErrorMessage(error: unknown, fallback = "Something went wrong. Please try again."): string {
  if (isAxiosError<ApiErrorShape>(error)) {
    // No `response` at all means the request never got a reply from the
    // server - CORS rejection, DNS failure, backend not running, etc.
    // This is a much more reliable signal than matching a specific
    // `error.code` string, which varies across browsers (Brave's Shields
    // in particular can block a request without axios ever setting
    // ERR_NETWORK). Always log the raw error too, so the real cause -
    // CORS vs. connection-refused vs. something else - shows up in
    // DevTools' Console/Network tabs even though the UI can only show one
    // generic sentence.
    if (!error.response) {
      // eslint-disable-next-line no-console
      console.error("[arceus] Request failed with no response - likely CORS or the backend is unreachable:", error);
      return "Couldn't reach the Gateway. Check that it's running and that GATEWAY_CORS_ALLOWED_ORIGINS includes this app's origin (see README.md).";
    }

    const data = error.response.data;
    if (data?.message) return data.message;
    if (error.response.status === 429) return "Too many requests - please slow down and try again.";

    // eslint-disable-next-line no-console
    console.error("[arceus] Request failed with an unrecognized error body:", error.response.status, data);
  }
  if (error instanceof Error) return error.message;
  return fallback;
}

/** Field-level validation errors, if the backend included any (`fields` on ServiceApiError). */
export function getFieldErrors(error: unknown): Record<string, string> | undefined {
  if (isAxiosError<ApiErrorShape>(error)) {
    const data = error.response?.data;
    if (data && "fields" in data) return data.fields;
  }
  return undefined;
}
