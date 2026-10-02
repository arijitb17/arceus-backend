"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Boxes, KeyRound, Mail } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { useLoginStep, useVerifyLoginOtpStep } from "@/hooks/use-auth";
import { getErrorMessage } from "@/lib/api/error";
import { loginSchema, otpSchema, type LoginFormValues, type OtpFormValues } from "@/features/auth/schemas";

export function LoginForm() {
  const [step, setStep] = useState<"credentials" | "otp">("credentials");
  const [email, setEmail] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const loginStep = useLoginStep();
  const verifyOtpStep = useVerifyLoginOtpStep();

  const credentialsForm = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const otpForm = useForm<OtpFormValues>({
    resolver: zodResolver(otpSchema),
    defaultValues: { otp: "" },
  });

  async function onSubmitCredentials(values: LoginFormValues) {
    setFormError(null);
    try {
      const res = await loginStep.mutateAsync(values);
      setEmail(values.email);
      setStep("otp");
      toast.success(res.message ?? "Verification code sent to your email.");
    } catch (error) {
      setFormError(getErrorMessage(error, "Couldn't sign in with those credentials."));
    }
  }

  async function onSubmitOtp(values: OtpFormValues) {
    setFormError(null);
    try {
      await verifyOtpStep.mutateAsync({ identifier: email, otp: values.otp });
      toast.success("Welcome back.");
    } catch (error) {
      setFormError(getErrorMessage(error, "That code didn't work. Please try again."));
    }
  }

  if (step === "otp") {
    return (
      <div className="space-y-6">
        <button
          type="button"
          onClick={() => {
            setStep("credentials");
            setFormError(null);
            otpForm.reset();
          }}
          className="inline-flex items-center gap-1.5 text-xs text-muted hover:text-foreground transition-colors"
        >
          <ArrowLeft className="size-3.5" />
          Back
        </button>

        <div className="space-y-1.5">
          <h2 className="text-lg font-semibold text-foreground">Check your email</h2>
          <p className="text-sm text-muted">
            Enter the 6-digit verification code we sent to{" "}
            <span className="font-medium text-foreground">{email}</span>.
          </p>
        </div>

        {formError && (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={otpForm.handleSubmit(onSubmitOtp)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="otp">Verification code</Label>
            <div className="relative">
              <KeyRound className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="otp"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="000000"
                maxLength={6}
                className="pl-9 tracking-[0.4em] font-mono text-center"
                autoFocus
                {...otpForm.register("otp")}
              />
            </div>
            {otpForm.formState.errors.otp && (
              <p className="text-xs text-error">{otpForm.formState.errors.otp.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" loading={verifyOtpStep.isPending}>
            Verify and sign in
          </Button>
        </form>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2.5">
        <div className="flex size-9 items-center justify-center rounded-md bg-primary/15 text-primary">
          <Boxes className="size-5" />
        </div>
        <div>
          <h1 className="text-lg font-semibold text-foreground leading-tight">Arceus</h1>
          <p className="text-xs text-muted">Platform Portal</p>
        </div>
      </div>

      <div className="space-y-1.5">
        <h2 className="text-lg font-semibold text-foreground">Sign in</h2>
        <p className="text-sm text-muted">Use your Arceus platform credentials.</p>
      </div>

      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={credentialsForm.handleSubmit(onSubmitCredentials)} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <div className="relative">
            <Mail className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@company.com"
              className="pl-9"
              autoFocus
              {...credentialsForm.register("email")}
            />
          </div>
          {credentialsForm.formState.errors.email && (
            <p className="text-xs text-error">{credentialsForm.formState.errors.email.message}</p>
          )}
        </div>

        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <Label htmlFor="password">Password</Label>
            <a href="/forgot-password" className="text-xs text-primary hover:underline">
              Forgot password?
            </a>
          </div>
          <div className="relative">
            <KeyRound className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
              className="pl-9"
              {...credentialsForm.register("password")}
            />
          </div>
          {credentialsForm.formState.errors.password && (
            <p className="text-xs text-error">{credentialsForm.formState.errors.password.message}</p>
          )}
        </div>

        <Button type="submit" className="w-full" loading={loginStep.isPending}>
          Continue
        </Button>
      </form>
    </div>
  );
}
