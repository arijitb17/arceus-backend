"use client";

import { useState } from "react";
import { AlertTriangle, X } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function DirectServiceNotice({ services }: { services: string[] }) {
  const [dismissed, setDismissed] = useState(false);
  if (dismissed) return null;

  return (
    <Alert variant="warning" className="flex items-start gap-3">
      <AlertTriangle />
      <div className="flex-1">
        <AlertTitle>Not yet routed through the Gateway</AlertTitle>
        <AlertDescription>
          This page reads live data from <span className="font-data">{services.join(", ")}</span> directly,
          bypassing gateway-service, because those services aren&apos;t proxied there yet. It works for local
          development but isn&apos;t a production-safe path - see{" "}
          <span className="font-data">lib/api/direct-client.ts</span> for details.
        </AlertDescription>
      </div>
      <Button variant="ghost" size="icon" className="size-6 shrink-0" onClick={() => setDismissed(true)}>
        <X className="size-3.5" />
      </Button>
    </Alert>
  );
}
