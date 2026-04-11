import { useCallback } from "react";
import { useAuth0 } from "@auth0/auth0-react";
import { AUTH0_AUDIENCE } from "../config";
import { useLocalAuth } from "./useLocalAuth";
import { refreshAccessToken } from "../../api/auth";

/** Returns true if the JWT is expired or expires within 30 seconds. */
function isLocalTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return (payload.exp as number) * 1000 < Date.now() + 30_000;
  } catch {
    return true;
  }
}

export function useAuthToken() {
  const { isAuthenticated, getAccessTokenSilently } = useAuth0();
  const { getAccessToken: getLocalToken, login, logout } = useLocalAuth();

  return useCallback(async (): Promise<string | null> => {
    const localToken = getLocalToken();
    if (localToken) {
      // Token still valid — return it immediately
      if (!isLocalTokenExpired(localToken)) return localToken;

      // Token expired — try to refresh silently
      const storedRefresh = localStorage.getItem("refreshToken");
      if (storedRefresh) {
        try {
          const { accessToken: newToken } = await refreshAccessToken(storedRefresh);
          // Backend issues a new access token; the refresh token stays the same
          login(newToken, storedRefresh);
          return newToken;
        } catch {
          // Refresh failed (expired or revoked) — force re-login
          logout();
          return null;
        }
      }

      // No refresh token available — clear stale state
      logout();
      return null;
    }

    // Fall back to Auth0 token
    if (!isAuthenticated) return null;

    return getAccessTokenSilently({
      authorizationParams: {
        audience: AUTH0_AUDIENCE,
        scope: "openid profile email",
      },
    });
  }, [isAuthenticated, getAccessTokenSilently, getLocalToken, login, logout]);
}
