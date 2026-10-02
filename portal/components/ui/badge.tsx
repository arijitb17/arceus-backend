import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-medium transition-colors",
  {
    variants: {
      variant: {
        default: "border-border bg-surface text-muted",
        primary: "border-primary/30 bg-primary-muted/40 text-primary",
        success: "border-success/30 bg-success-muted/40 text-success",
        warning: "border-warning/30 bg-warning-muted/40 text-warning",
        error: "border-error/30 bg-error-muted/40 text-error",
        accent: "border-accent/30 bg-accent/10 text-accent",
        outline: "border-border bg-transparent text-foreground",
      },
    },
    defaultVariants: { variant: "default" },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {
  dot?: boolean;
}

function Badge({ className, variant, dot, children, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props}>
      {dot && (
        <span
          className={cn("size-1.5 rounded-full", {
            "bg-muted": !variant || variant === "default" || variant === "outline",
            "bg-primary": variant === "primary",
            "bg-success": variant === "success",
            "bg-warning": variant === "warning",
            "bg-error": variant === "error",
            "bg-accent": variant === "accent",
          })}
        />
      )}
      {children}
    </div>
  );
}

export { Badge, badgeVariants };
