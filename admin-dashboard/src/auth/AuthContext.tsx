import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import { apiRequest, clearToken, getToken, setToken, adminLogin } from "../api/client";
import type { User } from "../types";

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function bootstrap() {
      if (!getToken()) {
        setLoading(false);
        return;
      }
      try {
        const me = await apiRequest<User>("/auth/me");
        if (me.role !== "ADMIN") {
          clearToken();
          setUser(null);
        } else {
          setUser(me);
        }
      } catch {
        clearToken();
        setUser(null);
      } finally {
        setLoading(false);
      }
    }
    bootstrap();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      async login(email, password) {
        const data = await adminLogin(email, password);
        setToken(data.access_token);
        if (data.user.role !== "ADMIN") {
          clearToken();
          throw new Error("Admin access required");
        }
        setUser(data.user);
      },
      async logout() {
        try {
          await apiRequest("/auth/logout", { method: "POST" });
        } finally {
          clearToken();
          setUser(null);
        }
      },
    }),
    [user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
