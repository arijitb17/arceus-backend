"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { ChevronDown, Boxes } from "lucide-react";
import { cn } from "@/lib/utils";
import { navSections, type NavLeaf } from "@/lib/nav-config";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

function availabilityBadge(availability: NavLeaf["availability"]) {
  if (availability === "direct") {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="size-1.5 rounded-full bg-warning" />
        </TooltipTrigger>
        <TooltipContent>Direct service connection - not yet routed via Gateway</TooltipContent>
      </Tooltip>
    );
  }
  if (availability === "missing") {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="size-1.5 rounded-full bg-muted" />
        </TooltipTrigger>
        <TooltipContent>No backend endpoint yet</TooltipContent>
      </Tooltip>
    );
  }
  return null;
}

export function Sidebar() {
  const pathname = usePathname();
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({
    Authentication: pathname.startsWith("/auth"),
    Notifications: pathname.startsWith("/notifications"),
  });

  return (
    <aside className="hidden md:flex w-64 shrink-0 flex-col border-r border-border bg-surface">
      <div className="flex h-14 items-center gap-2 px-5 border-b border-border">
        <div className="flex size-7 items-center justify-center rounded-md bg-primary/15 text-primary">
          <Boxes className="size-4" />
        </div>
        <span className="text-sm font-semibold tracking-tight">Arceus</span>
        <Badge variant="outline" className="ml-auto text-[10px] px-1.5 py-0">
          Admin
        </Badge>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-0.5">
        {navSections.map((section) => {
          const Icon = section.icon;

          if (section.href) {
            const active = pathname === section.href;
            return (
              <Link
                key={section.label}
                href={section.href}
                className={cn(
                  "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors",
                  active
                    ? "bg-primary/15 text-blue-300 font-medium"
                    : "text-muted hover:bg-card hover:text-foreground"
                )}
              >
                <Icon className="size-4 shrink-0" />
                {section.label}
              </Link>
            );
          }

          const isOpen = openSections[section.label] ?? false;
          const childActive = section.children?.some((c) => pathname.startsWith(c.href));

          return (
            <div key={section.label}>
              <button
                type="button"
                onClick={() => setOpenSections((s) => ({ ...s, [section.label]: !isOpen }))}
                className={cn(
                  "flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors",
                  childActive ? "text-foreground font-medium" : "text-muted hover:bg-card hover:text-foreground"
                )}
              >
                <Icon className="size-4 shrink-0" />
                <span className="flex-1 text-left">{section.label}</span>
                <ChevronDown
                  className={cn("size-3.5 transition-transform", isOpen && "rotate-180")}
                />
              </button>
              {isOpen && (
                <div className="mt-0.5 ml-[1.15rem] space-y-0.5 border-l border-border pl-3">
                  {section.children?.map((child) => {
                    const ChildIcon = child.icon;
                    const active = pathname.startsWith(child.href);
                    return (
                      <Link
                        key={child.href}
                        href={child.href}
                        className={cn(
                          "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-[13px] transition-colors",
                          active
                            ? "bg-primary/15 text-blue-300 font-medium"
                            : "text-muted hover:bg-card hover:text-foreground"
                        )}
                      >
                        <ChildIcon className="size-3.5 shrink-0" />
                        <span className="flex-1">{child.label}</span>
                        {availabilityBadge(child.availability)}
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      <div className="border-t border-border px-4 py-3">
        <p className="text-[11px] text-muted-foreground leading-relaxed">
          Arceus Platform <span className="font-data">v1.0.0</span>
        </p>
      </div>
    </aside>
  );
}
