"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { authService } from "@/services/auth.service";
import { useAuthStore } from "@/store/auth-store";
import type { LoginRequest, VerifyOtpRequest } from "@/types/auth";

/** Step 1: POST /api/auth/login - checks the password, triggers a login OTP email. Does not return tokens. */
export function useLoginStep() {
  return useMutation({
    mutationFn: (body: LoginRequest) => authService.login(body),
  });
}

/** Step 2: POST /api/auth/verify-login-otp - confirms the OTP, returns real tokens, fetches the profile. */
export function useVerifyLoginOtpStep() {
  const setTokens = useAuthStore((s) => s.setTokens);
  const setUser = useAuthStore((s) => s.setUser);
  const router = useRouter();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (body: VerifyOtpRequest) => authService.verifyLoginOtp(body),
    onSuccess: async (auth) => {
      setTokens(auth.accessToken, auth.refreshToken);
      const me = await authService.me();
      setUser(me);
      await queryClient.invalidateQueries();
      router.push("/dashboard");
    },
  });
}

/** Current authenticated user - reads from the store first, verifies against GET /api/auth/me. */
export function useCurrentUser() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const setUser = useAuthStore((s) => s.setUser);

  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: async () => {
      const me = await authService.me();
      setUser(me);
      return me;
    },
    enabled: !!accessToken,
    staleTime: 5 * 60_000,
  });
}

export function useLogout() {
  const refreshToken = useAuthStore((s) => s.refreshToken);
  const logout = useAuthStore((s) => s.logout);
  const router = useRouter();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      if (refreshToken) {
        try {
          await authService.logout({ refreshToken });
        } catch {
          // Already invalid/expired server-side - still clear local state below.
        }
      }
    },
    onSettled: () => {
      logout();
      queryClient.clear();
      router.push("/login");
    },
  });
}
