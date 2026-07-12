/**
 * Minimal hand-rolled SSE client using fetch + ReadableStream, instead of
 * the native EventSource. Native EventSource can't set an Authorization
 * header (the backend added an `?access_token=` query-param fallback just
 * for that limitation - see JwtAuthFilter), and more importantly it can
 * only usefully listen for event names known ahead of time via
 * addEventListener. This stream fans out *every* domain event type
 * (deployments, jobs, flags, monitoring, audit - the whole platform event
 * bus), so parsing the raw `event:`/`data:` frames ourselves is the only
 * way to stay generic across event types we don't have a fixed list of.
 */
export interface SseEvent {
  event: string;
  data: string;
}

export function streamSse(
  url: string,
  token: string,
  onEvent: (event: SseEvent) => void,
  signal: AbortSignal
): Promise<void> {
  return fetch(url, {
    headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
    signal,
  }).then(async (response) => {
    if (!response.ok || !response.body) {
      throw new Error(`Live updates stream failed (${response.status})`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) return;

      buffer += decoder.decode(value, { stream: true });
      const frames = buffer.split("\n\n");
      buffer = frames.pop() ?? "";

      for (const frame of frames) {
        let eventName = "message";
        const dataLines: string[] = [];
        for (const line of frame.split("\n")) {
          if (line.startsWith("event:")) eventName = line.slice(6).trim();
          else if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
        }
        if (dataLines.length > 0) {
          onEvent({ event: eventName, data: dataLines.join("\n") });
        }
      }
    }
  });
}
