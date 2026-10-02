"use client";

import { useEffect, useState } from "react";
import { Settings2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { usePermissions, useUpdateRolePermissions } from "@/hooks/use-rbac";
import type { PermissionResponse, RoleDefinitionResponse } from "@/types/rbac";

export function RolePermissionsDialog({ role }: { role: RoleDefinitionResponse }) {
  const [open, setOpen] = useState(false);
  const { data: allPermissions } = usePermissions({ size: 100 });
  const updateRolePermissions = useUpdateRolePermissions(role.roleKey);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (open) setSelected(new Set(role.permissions.map((p) => p.id)));
  }, [open, role.permissions]);

  function toggle(id: string, checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  async function onSave() {
    await updateRolePermissions.mutateAsync({ permissionIds: Array.from(selected) });
    setOpen(false);
  }

  const grouped = (allPermissions?.content ?? []).reduce<Record<string, PermissionResponse[]>>((acc, p) => {
    (acc[p.category] ??= []).push(p);
    return acc;
  }, {});

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Settings2 />
          Manage permissions
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Permissions for {role.name}</DialogTitle>
          <DialogDescription>Replaces the full permission set for this role when saved.</DialogDescription>
        </DialogHeader>
        <DialogBody className="max-h-96 overflow-y-auto space-y-4">
          {Object.entries(grouped).map(([category, perms]) => (
            <div key={category}>
              <p className="text-xs font-medium text-muted uppercase tracking-wide mb-2">{category}</p>
              <div className="space-y-2">
                {perms.map((p) => (
                  <label key={p.id} className="flex items-center gap-2.5 text-sm cursor-pointer">
                    <Checkbox checked={selected.has(p.id)} onCheckedChange={(c) => toggle(p.id, c === true)} />
                    <span className="font-data">{p.code}</span>
                  </label>
                ))}
              </div>
            </div>
          ))}
          {!allPermissions && <p className="text-sm text-muted">Loading permissions\u2026</p>}
        </DialogBody>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={onSave} loading={updateRolePermissions.isPending}>
            Save ({selected.size} selected)
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
