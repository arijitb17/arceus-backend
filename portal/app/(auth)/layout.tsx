export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid min-h-dvh grid-cols-1 lg:grid-cols-2 bg-background">
      {/* Branding panel */}
      <div className="relative hidden lg:flex flex-col justify-between overflow-hidden border-r border-border bg-surface p-10">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.06]"
          style={{
            backgroundImage:
              "linear-gradient(#F4F2EA 1px, transparent 1px), linear-gradient(90deg, #F4F2EA 1px, transparent 1px)",
            backgroundSize: "32px 32px",
          }}
        />
        {/* The Arceus ring, rendered large and soft as the branding panel's
            one signature moment - a single warm gold glow, not the old
            two-tone blue/violet gradient this replaced. */}
        <div
          className="pointer-events-none absolute -top-32 -right-32 size-[32rem] rounded-full opacity-25 blur-3xl"
          style={{ background: "radial-gradient(circle, #EAB63C, transparent 70%)" }}
        />
        <div
          className="pointer-events-none absolute -bottom-24 -left-24 size-80 rounded-full opacity-15 blur-3xl"
          style={{ background: "radial-gradient(circle, #C08A2E, transparent 70%)" }}
        />

        <div className="relative z-10 flex items-center gap-2.5">
          <div className="flex size-8 items-center justify-center rounded-full bg-background text-primary shadow-ring ring-1 ring-primary/40 font-semibold text-sm">
            A
          </div>
          <span className="text-base font-semibold tracking-tight text-foreground">Arceus Platform</span>
        </div>

        <div className="relative z-10 max-w-md space-y-3">
          <h2 className="text-3xl font-semibold leading-snug text-foreground text-balance">
            Modular backend infrastructure, one console away.
          </h2>
          <p className="text-base text-muted leading-relaxed">
            Manage authentication, notifications, feature flags, scheduled jobs,
            and platform health - or build against your organizations, projects,
            and API keys - all from a single place.
          </p>
        </div>

        <p className="relative z-10 text-sm text-muted-foreground">
          &copy; {new Date().getFullYear()} Arceus. Internal platform tool.
        </p>
      </div>

      {/* Form panel */}
      <div className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm">{children}</div>
      </div>
    </div>
  );
}
