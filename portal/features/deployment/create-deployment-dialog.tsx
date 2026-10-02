"use client";

import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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
import { useCreateDeployment } from "@/hooks/use-deployments";
import { useAuthStore } from "@/store/auth-store";
import type { DeploymentEnvironment } from "@/types/deployment";

const ENVIRONMENTS: DeploymentEnvironment[] = ["DEVELOPMENT", "TESTING", "STAGING", "PRODUCTION"];

interface FormValues {
  serviceName: string;
  serviceVersion: string;
  dockerImage: string;
  environment: DeploymentEnvironment;
  releaseNotes?: string;
}

export function CreateDeploymentDialog() {
  const [open, setOpen] = useState(false);
  const createDeployment = useCreateDeployment();
  const email = useAuthStore((s) => s.user?.email);

  const form = useForm<FormValues>({
    defaultValues: { serviceName: "", serviceVersion: "", dockerImage: "", environment: "DEVELOPMENT", releaseNotes: "" },
  });

  async function onSubmit(values: FormValues) {
    await createDeployment.mutateAsync({
      serviceName: values.serviceName,
      serviceVersion: values.serviceVersion,
      dockerImage: values.dockerImage,
      environment: values.environment,
      createdBy: email ?? "admin",
      releaseNotes: values.releaseNotes,
    });
    setOpen(false);
    form.reset();
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus />
          New Deployment
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create deployment</DialogTitle>
          <DialogDescription>Registers a new deployment. Depending on the environment it may require approval before it can run.</DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <DialogBody className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="serviceName">Service name</Label>
                <Input id="serviceName" className="font-data" placeholder="e.g. notification-service" {...form.register("serviceName", { required: true })} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="serviceVersion">Version</Label>
                <Input id="serviceVersion" className="font-data" placeholder="e.g. 1.4.2" {...form.register("serviceVersion", { required: true })} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="dockerImage">Docker image</Label>
              <Input id="dockerImage" className="font-data" placeholder="e.g. registry.arceus.io/notification-service:1.4.2" {...form.register("dockerImage", { required: true })} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="environment">Environment</Label>
              <Controller
                control={form.control}
                name="environment"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="environment">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {ENVIRONMENTS.map((e) => (
                        <SelectItem key={e} value={e}>
                          {e}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="releaseNotes">Release notes</Label>
              <Textarea id="releaseNotes" rows={3} {...form.register("releaseNotes")} />
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={createDeployment.isPending}>
              Create deployment
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
