"use client";

import { useState } from "react";
import { Undo2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { useRollbackDeployment } from "@/hooks/use-deployments";
import { useAuthStore } from "@/store/auth-store";
import type { DeploymentResponse } from "@/types/deployment";

export function RollbackDeploymentDialog({ deployment }: { deployment: DeploymentResponse }) {
  const [open, setOpen] = useState(false);
  const [targetVersion, setTargetVersion] = useState("");
  const rollback = useRollbackDeployment();
  const email = useAuthStore((s) => s.user?.email);

  async function handleRollback() {
    await rollback.mutateAsync({
      id: deployment.id,
      body: { targetVersion: targetVersion || undefined, initiatedBy: email ?? "admin" },
    });
    setOpen(false);
    setTargetVersion("");
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <Undo2 />
          Rollback
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Roll back {deployment.serviceName}</DialogTitle>
          <DialogDescription>
            Leave the target version blank to roll back to the previous known-good version{deployment.rollbackVersion ? ` (${deployment.rollbackVersion})` : ""}.
          </DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-1.5">
          <Label htmlFor="targetVersion">Target version (optional)</Label>
          <Input
            id="targetVersion"
            className="font-data"
            placeholder={deployment.rollbackVersion ?? "e.g. 1.4.1"}
            value={targetVersion}
            onChange={(e) => setTargetVersion(e.target.value)}
          />
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button variant="destructive" loading={rollback.isPending} onClick={handleRollback}>
            Roll back
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
