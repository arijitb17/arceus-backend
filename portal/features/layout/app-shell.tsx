"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth-store";
import { useCurrentUser } from "@/hooks/use-auth";
import { Sidebar } from "@/features/layout/sidebar";
import { Topbar } from "@/features/layout/topbar";
import { Loader2 } from "lucide-react";
import { canAccessPath, defaultHomeFor } from "@/lib/rbac";

export function AppShell({ title, children }: { title: string; children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const accessToken = useAuthStore((s) => s.accessToken);
  const user = useAuthStore((s) => s.user);
  useCurrentUser();

  // While the profile hasn't loaded yet (e.g. first paint right after a
  // fresh login), don't guess-deny - wait for the real roles before gating.
  const allowed = !user || canAccessPath(pathname, user.roles);

  useEffect(() => {
    if (!hasHydrated) return;
    if (!accessToken) {
      router.replace("/login");
      return;
    }
    // RBAC: admin-only routes (e.g. /dashboard, /auth/users, /monitoring...) are
    // off-limits to plain developer accounts even if they navigate straight to
    // the URL - bounce them back to the part of the portal they're allowed in.
    if (user && !allowed) {
      router.replace(defaultHomeFor(user.roles));
    }
  }, [hasHydrated, accessToken, allowed, user, router]);

  if (!hasHydrated || !accessToken || !allowed) {
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
                "radial-gradient(circle at 15% 0%, #EAB63C, transparent 35%), radial-gradient(circle at 85% 15%, #C08A2E, transparent 35%)",
            }}
          />
          <div className="animate-fade-in">{children}</div>
        </main>
      </div>
    </div>
  );
}
