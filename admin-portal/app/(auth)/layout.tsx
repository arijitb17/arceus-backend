export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid min-h-dvh grid-cols-1 lg:grid-cols-2 bg-background">
      {/* Branding panel */}
      <div className="relative hidden lg:flex flex-col justify-between overflow-hidden border-r border-border bg-surface p-10">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              "linear-gradient(#F9FAFB 1px, transparent 1px), linear-gradient(90deg, #F9FAFB 1px, transparent 1px)",
            backgroundSize: "32px 32px",
          }}
        />
        <div
          className="pointer-events-none absolute -top-24 -left-24 size-96 rounded-full opacity-20 blur-3xl"
          style={{ background: "radial-gradient(circle, #7C3AED, transparent 70%)" }}
        />
        <div
          className="pointer-events-none absolute -bottom-32 -right-16 size-96 rounded-full opacity-20 blur-3xl"
          style={{ background: "radial-gradient(circle, #2563EB, transparent 70%)" }}
        />

        <div className="relative z-10 flex items-center gap-2.5">
          <div className="flex size-8 items-center justify-center rounded-md bg-primary/15 text-primary font-semibold text-sm">
            A
          </div>
          <span className="text-sm font-semibold tracking-tight text-foreground">Arceus Platform</span>
        </div>

        <div className="relative z-10 max-w-md space-y-3">
          <h2 className="text-2xl font-semibold leading-snug text-foreground text-balance">
            Modular backend infrastructure, one console away.
          </h2>
          <p className="text-sm text-muted leading-relaxed">
            Manage authentication, deployments, feature flags, scheduled jobs,
            and platform health from a single place.
          </p>
        </div>

        <p className="relative z-10 text-xs text-muted-foreground">
          &copy; {new Date().getFullYear()} Arceus. Internal administration tool.
        </p>
      </div>

      {/* Form panel */}
      <div className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm">{children}</div>
      </div>
    </div>
  );
}
