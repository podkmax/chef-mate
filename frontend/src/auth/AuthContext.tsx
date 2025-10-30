import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from "react";

type Role = "COOK" | "GUEST";

interface AuthContextValue {
  role: Role;
  loginAsCook: () => void;
  logout: () => void;
}

const STORAGE_KEY = "chefmate.role";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<Role>("GUEST");

  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY) as Role | null;
    if (stored === "COOK") {
      setRole("COOK");
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      role,
      loginAsCook: () => {
        localStorage.setItem(STORAGE_KEY, "COOK");
        setRole("COOK");
      },
      logout: () => {
        localStorage.removeItem(STORAGE_KEY);
        setRole("GUEST");
      }
    }),
    [role]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}

export function useIsCook() {
  return useAuth().role === "COOK";
}
