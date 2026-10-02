import type { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export function ModuleUnavailable({
  icon: Icon,
  title,
  description,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
}) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
        <div className="flex size-11 items-center justify-center rounded-lg bg-surface text-muted">
          <Icon className="size-5" />
        </div>
        <div className="max-w-md space-y-1.5">
          <p className="text-sm font-medium text-foreground">{title}</p>
          <p className="text-xs leading-relaxed text-muted">{description}</p>
        </div>
      </CardContent>
    </Card>
  );
}
