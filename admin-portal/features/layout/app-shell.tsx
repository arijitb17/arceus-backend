"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth-store";
import { useCurrentUser } from "@/hooks/use-auth";
import { Sidebar } from "@/features/layout/sidebar";
import { Topbar } from "@/features/layout/topbar";
import { Loader2 } from "lucide-react";

export function AppShell({ title, children }: { title: string; children: React.ReactNode }) {
  const router = useRouter();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const accessToken = useAuthStore((s) => s.accessToken);
  useCurrentUser();

  useEffect(() => {
    if (hasHydrated && !accessToken) {
      router.replace("/login");
    }
  }, [hasHydrated, accessToken, router]);

  if (!hasHydrated || !accessToken) {
    return (
      <div className="flex h-dvh items-center justify-center bg-background">
        <Loader2 className="size-5 animate-spin text-muted" />
      </div>
    );
  }

  return (
    <div className="flex h-dvh overflow-hidden bg-background">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Topbar title={title} />
        <main className="relative flex-1 overflow-y-auto p-6">
          <div
            className="pointer-events-none fixed inset-0 -z-10 opacity-[0.05]"
            style={{
              backgroundImage:
                "radial-gradient(circle at 15% 0%, #2563EB, transparent 35%), radial-gradient(circle at 85% 15%, #7C3AED, transparent 35%)",
            }}
          />
          <div className="animate-fade-in">{children}</div>
        </main>
      </div>
    </div>
  );
}
