import { useCallback } from "react";
import { useAuthToken } from "./useAuthToken";
import { useApiQuery } from "../hooks/useApiQuery";
import { useAppAuth } from "./AuthContext";

export function useAuthedQuery<T>(key: string, fn: (token: string) => Promise<T>, enabledOverride?: boolean) {
  const { isAuthenticated } = useAppAuth();
  const getToken = useAuthToken();
  const enabled = isAuthenticated && (enabledOverride ?? true);

  const runner = useCallback(async () => {
    const token = await getToken();
    if (!token) throw new Error("Not authenticated");
    return fn(token);
  }, [fn, getToken]);

  return useApiQuery(key, runner, enabled);
}
