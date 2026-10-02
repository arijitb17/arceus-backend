"use client";

import { useEffect } from "react";
import { Code2, Copy } from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useGenerateSdkConfig } from "@/hooks/use-sdk-config";
import { getErrorMessage } from "@/lib/api/error";
import type { EnvironmentType } from "@/types/project";

export function SdkConfigPanel({
  organizationId,
  projectId,
  environment,
}: {
  organizationId: string;
  projectId: string;
  environment: EnvironmentType;
}) {
  const generate = useGenerateSdkConfig(organizationId, projectId);

  // Regenerate the view whenever the environment tab changes - cheap and
  // keeps this panel always showing config for the environment currently
  // selected above it, without a manual "refresh" click.
  useEffect(() => {
    generate.mutate(environment);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [environment]);

  function copyConfig() {
    if (!generate.data) return;
    navigator.clipboard.writeText(JSON.stringify(generate.data, null, 2));
    toast.success("Copied to clipboard.");
  }

  if (generate.isPending) {
    return (
      <Card>
        <CardContent className="p-5 text-sm text-muted">Generating\u2026</CardContent>
      </Card>
    );
  }

  if (generate.isError) {
    return (
      <Card>
        <CardContent className="p-5 text-sm text-red-400">{getErrorMessage(generate.error)}</CardContent>
      </Card>
    );
  }

  const config = generate.data;
  if (!config) return null;

  const snippet = `import { ArceusClient } from "@arceus/sdk";

const arceus = new ArceusClient(${JSON.stringify(config, null, 2)});

${config.modules
  .filter((m) => m.enabled)
  .map((m) => `// ${m.module.charAt(0)}${m.module.slice(1).toLowerCase()}.someMethod()`)
  .join("\n")}`;

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="space-y-3 p-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Code2 className="size-4 text-primary" />
              <h3 className="text-sm font-medium text-foreground">arceus.config.json</h3>
            </div>
            <Button size="sm" variant="outline" onClick={copyConfig}>
              <Copy />
              Copy
            </Button>
          </div>
          <pre className="overflow-x-auto rounded-md bg-background p-4 text-xs font-mono text-muted">
            {JSON.stringify(config, null, 2)}
          </pre>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3 p-5">
          <h3 className="text-sm font-medium text-foreground">Enabled modules</h3>
          <div className="flex flex-wrap gap-2">
            {config.modules.length === 0 && <p className="text-xs text-muted">No modules enabled yet for this environment.</p>}
            {config.modules.map((m) => (
              <Badge key={m.module} variant={m.enabled ? "success" : "default"}>
                {m.module}
              </Badge>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3 p-5">
          <h3 className="text-sm font-medium text-foreground">Quick start</h3>
          <pre className="overflow-x-auto rounded-md bg-background p-4 text-xs font-mono text-muted whitespace-pre-wrap">
            {snippet}
          </pre>
        </CardContent>
      </Card>
    </div>
  );
}
