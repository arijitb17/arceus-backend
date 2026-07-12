import type { Metadata } from "next";
import { LoginForm } from "@/features/auth/login-form";

export const metadata: Metadata = { title: "Sign in \u2014 Arceus Admin Portal" };

export default function LoginPage() {
  return <LoginForm />;
}
