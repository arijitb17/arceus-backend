"use client";

import { Toaster as SonnerToaster, type ToasterProps } from "sonner";

export function Toaster(props: ToasterProps) {
  return (
    <SonnerToaster
      theme="dark"
      position="top-right"
      toastOptions={{
        classNames: {
          toast:
            "!bg-card !border !border-border !text-foreground !shadow-elevated !rounded-md",
          title: "!text-sm !font-medium",
          description: "!text-muted !text-xs",
          actionButton: "!bg-primary !text-primary-foreground",
          cancelButton: "!bg-surface !text-muted",
          success: "!border-l-2 !border-l-success",
          error: "!border-l-2 !border-l-error",
          warning: "!border-l-2 !border-l-warning",
          info: "!border-l-2 !border-l-primary",
        },
      }}
      {...props}
    />
  );
}
