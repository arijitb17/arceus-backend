"use client";

import { LogOut, Mail, Phone, ShieldCheck, ShieldX, Server, UserCircle } from "lucide-react";
import { AppShell } from "@/features/layout/app-shell";
import { PageHeader } from "@/components/shared/page-header";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { useAuthStore } from "@/store/auth-store";
import { useLogout } from "@/hooks/use-auth";
import { config } from "@/lib/config";

function initials(name: string | undefined) {
  if (!name) return "?";
  return name.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase();
}

export default function SettingsPage() {
  const user = useAuthStore((s) => s.user);
  const logout = useLogout();

  return (
    <AppShell title="Settings">
      <div className="max-w-3xl space-y-6">
        <PageHeader title="Settings" description="Your account and this console's connection to the platform" />

        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
            <CardDescription>Read-only - managed through your identity provider, not this console</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-3">
              <Avatar className="size-12">
                <AvatarFallback className="text-base">{initials(user?.name)}</AvatarFallback>
              </Avatar>
              <div>
                <p className="text-sm font-medium text-foreground">{user?.name ?? "\u2014"}</p>
                <div className="mt-1 flex gap-1.5">
                  {user?.roles?.map((r) => (
                    <Badge key={r} variant={r === "ROLE_ADMIN" ? "primary" : "outline"}>
                      {r}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 border-t border-border pt-4 sm:grid-cols-2">
              <div className="flex items-center gap-2 text-sm">
                <Mail className="size-4 text-muted-foreground" />
                <span className="text-foreground">{user?.email ?? "\u2014"}</span>
                {user?.emailVerified ? (
                  <ShieldCheck className="size-3.5 text-success" />
                ) : (
                  <ShieldX className="size-3.5 text-error" />
                )}
              </div>
              <div className="flex items-center gap-2 text-sm">
                <Phone className="size-4 text-muted-foreground" />
                <span className="text-foreground">{user?.phoneNumber || "\u2014"}</span>
                {user?.phoneNumber &&
                  (user?.phoneVerified ? (
                    <ShieldCheck className="size-3.5 text-success" />
                  ) : (
                    <ShieldX className="size-3.5 text-error" />
                  ))}
              </div>
              <div className="flex items-center gap-2 text-sm">
                <UserCircle className="size-4 text-muted-foreground" />
                <span className="text-foreground">Signed in via {user?.provider ?? "\u2014"}</span>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Connection</CardTitle>
            <CardDescription>Where this console sends its API requests</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2 text-sm">
              <Server className="size-4 text-muted-foreground" />
              <span className="font-data text-foreground">{config.gatewayUrl}</span>
              <Badge variant="outline">Gateway</Badge>
            </div>
            <p className="mt-2 text-xs text-muted">
              Set <span className="font-data">NEXT_PUBLIC_GATEWAY_URL</span> to point this console at a different gateway-service instance.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Session</CardTitle>
            <CardDescription>Sign out of this console on this device</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="destructive" onClick={() => logout.mutate()} loading={logout.isPending}>
              <LogOut />
              Sign out
            </Button>
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}
