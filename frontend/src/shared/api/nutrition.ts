import { api } from "./client";
import type { ApiResponse, DailySummary } from "../types";
import { unwrapApiData } from "./unwrap";

/**
 * Fetch today's nutrition summary.
 * 
 * Returns aggregated nutrition data for the current day with macro progress
 * towards user targets.
 */
export function getDailySummary(token: string): Promise<DailySummary | null> {
    return api
        .get<DailySummary | null | ApiResponse<DailySummary | null>>(
            "/api/nutrition/dashboard/summary",
            token
        )
        .then((response) => unwrapApiData(response));
}
