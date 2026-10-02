"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth-store";
import { defaultHomeFor } from "@/lib/rbac";
import { Loader2 } from "lucide-react";

export default function RootPage() {
  const router = useRouter();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const accessToken = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!hasHydrated) return;
    router.replace(accessToken ? defaultHomeFor(user?.roles) : "/login");
  }, [hasHydrated, accessToken, user?.roles, router]);

  return (
    <div className="flex h-dvh items-center justify-center bg-background">
      <Loader2 className="size-5 animate-spin text-muted" />
    </div>
  );
}
