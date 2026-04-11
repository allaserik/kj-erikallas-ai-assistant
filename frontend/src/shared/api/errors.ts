import { ApiError } from "./client";

export function isApiError(e: unknown): e is ApiError {
    return e instanceof ApiError;
}

export function explainApiError(e: unknown): string {
    if (e instanceof ApiError) {
        const msg = e.bodyJson?.message || e.message || "";
        if (e.status === 401) return "Session expired. Please log in again.";
        if (e.status === 403) return "You don't have permission to do that.";
        if (e.status === 404) return msg || "Not found. You may need to complete setup first.";
        if (e.status === 400) return msg || "Invalid request. Please check your input.";
        if (e.status === 429) return "Too many requests. Please wait a moment and try again.";
        if (e.status >= 500) return "A server error occurred. Please try again in a moment.";
        return msg || `Request failed (HTTP ${e.status}).`;
    }
    if (e instanceof Error) return e.message;
    return "An unexpected error occurred.";
}

export function fieldErrorsByName(e: unknown): Record<string, string> {
    if (!(e instanceof ApiError)) return {};
    const fes = e.bodyJson?.fieldErrors ?? [];
    return Object.fromEntries(fes.map((fe: { field: string; message: string }) => [fe.field, fe.message]));
}