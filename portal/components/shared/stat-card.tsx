import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";

interface StatCardProps {
  label: string;
  value: string;
  icon: LucideIcon;
  sublabel?: string;
  tone?: "default" | "success" | "warning" | "error" | "primary" | "accent";
  loading?: boolean;
}

const toneStyles: Record<NonNullable<StatCardProps["tone"]>, { icon: string; glow: string }> = {
  default: { icon: "text-muted bg-surface", glow: "" },
  primary: {
    icon: "text-primary bg-gradient-to-br from-primary/25 to-primary/5",
    glow: "before:bg-primary/40",
  },
  accent: {
    icon: "text-accent bg-gradient-to-br from-accent/25 to-accent/5",
    glow: "before:bg-accent/40",
  },
  success: {
    icon: "text-success bg-gradient-to-br from-success/25 to-success/5",
    glow: "before:bg-success/40",
  },
  warning: {
    icon: "text-warning bg-gradient-to-br from-warning/25 to-warning/5",
    glow: "before:bg-warning/40",
  },
  error: {
    icon: "text-error bg-gradient-to-br from-error/25 to-error/5",
    glow: "before:bg-error/40",
  },
};

export function StatCard({ label, value, icon: Icon, sublabel, tone = "default", loading }: StatCardProps) {
  const styles = toneStyles[tone];
  return (
    <div
      className={cn(
        "group relative overflow-hidden rounded-lg border border-border bg-card p-4 transition-all duration-200",
        "hover:border-border/80 hover:shadow-elevated",
        "before:absolute before:-right-8 before:-top-8 before:size-24 before:rounded-full before:blur-2xl before:opacity-0 before:transition-opacity before:duration-300 hover:before:opacity-100 before:content-['']",
        styles.glow
      )}
    >
      <div className="relative flex items-start justify-between">
        <div className="space-y-1.5 min-w-0">
          <p className="text-xs font-medium text-muted">{label}</p>
          {loading ? (
            <Skeleton className="h-7 w-20" />
          ) : (
            <p className="text-2xl font-semibold text-foreground tabular-nums tracking-tight truncate">{value}</p>
          )}
          {sublabel && <p className="text-[11px] text-muted-foreground">{sublabel}</p>}
        </div>
        <div className={cn("flex size-9 shrink-0 items-center justify-center rounded-md transition-transform duration-200 group-hover:scale-105", styles.icon)}>
          <Icon className="size-4" />
        </div>
      </div>
    </div>
  );
}
