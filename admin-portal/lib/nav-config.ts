import type { LucideIcon } from "lucide-react";
import {
  LayoutDashboard,
  ShieldCheck,
  Users,
  KeyRound,
  Fingerprint,
  MonitorSmartphone,
  BellRing,
  FileText,
  Inbox,
  ListTree,
  ScrollText,
  Gauge,
  Database,
  Clock,
  Flag,
  Activity,
  Rocket,
  Settings,
} from "lucide-react";

export type NavAvailability = "live" | "direct" | "missing";

export interface NavLeaf {
  label: string;
  href: string;
  icon: LucideIcon;
  /**
   * live    - fully implemented against the Gateway today
   * direct  - implemented against a backend endpoint, but only reachable via
   *           the temporary direct-client shim until the Gateway routes it
   * missing - no backend endpoint exists yet; the page explains the gap
   *           instead of calling a fake API
   */
  availability: NavAvailability;
}

export interface NavSection {
  label: string;
  icon: LucideIcon;
  href?: string;
  children?: NavLeaf[];
}

export const navSections: NavSection[] = [
  {
    label: "Dashboard",
    icon: LayoutDashboard,
    href: "/dashboard",
  },
  {
    label: "Authentication",
    icon: ShieldCheck,
    children: [
      { label: "Users", href: "/auth/users", icon: Users, availability: "missing" },
      { label: "Roles", href: "/auth/roles", icon: Fingerprint, availability: "missing" },
      { label: "Permissions", href: "/auth/permissions", icon: ListTree, availability: "missing" },
      { label: "Sessions", href: "/auth/sessions", icon: MonitorSmartphone, availability: "missing" },
      { label: "API Keys", href: "/auth/api-keys", icon: KeyRound, availability: "live" },
    ],
  },
  {
    label: "Notifications",
    icon: BellRing,
    children: [
      { label: "Templates", href: "/notifications/templates", icon: FileText, availability: "missing" },
      { label: "History", href: "/notifications/history", icon: ScrollText, availability: "live" },
      { label: "Queue", href: "/notifications/queue", icon: Inbox, availability: "missing" },
    ],
  },
  { label: "Audit", icon: ScrollText, href: "/audit" },
  { label: "Rate Limiter", icon: Gauge, href: "/rate-limiter" },
  { label: "Cache", icon: Database, href: "/cache" },
  { label: "Scheduler", icon: Clock, href: "/scheduler" },
  { label: "Feature Flags", icon: Flag, href: "/feature-flags" },
  { label: "Monitoring", icon: Activity, href: "/monitoring" },
  { label: "Deployment", icon: Rocket, href: "/deployment" },
  { label: "Settings", icon: Settings, href: "/settings" },
];
