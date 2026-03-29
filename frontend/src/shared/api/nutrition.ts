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

export interface MealIntakeLog {
    id: string;
    consumedDate: string;
    consumedTime: string | null;
    mealType: string;
    servings: number;
    caloriesKcal: number | null;
    proteinG: number | null;
    carbsG: number | null;
    fatsG: number | null;
    customMealName: string | null;
    customMealNotes: string | null;
    recipeId: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface LogMealRequest {
    recipeId: string;
    servings: number;
    mealType: string;
    consumedTime: string | null;
}

export interface LogCustomMealRequest {
    mealName: string;
    mealType: string;
    caloriesKcal: number;
    proteinG: number;
    carbsG: number;
    fatsG: number;
    notes?: string;
    consumedTime: string | null;
}

/**
 * Log a meal from a recipe.
 */
export function logMeal(request: LogMealRequest, token: string): Promise<MealIntakeLog | null> {
    return api
        .post<MealIntakeLog | null | ApiResponse<MealIntakeLog | null>>(
            "/api/nutrition/meals/log",
            request,
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Log a custom meal.
 */
export function logCustomMeal(request: LogCustomMealRequest, token: string): Promise<MealIntakeLog | null> {
    return api
        .post<MealIntakeLog | null | ApiResponse<MealIntakeLog | null>>(
            "/api/nutrition/meals/log-custom",
            request,
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Get meals logged for a specific date.
 */
export function getMealsForDate(date: string, token: string): Promise<MealIntakeLog[] | null> {
    return api
        .get<MealIntakeLog[] | null | ApiResponse<MealIntakeLog[] | null>>(
            `/api/nutrition/meals/daily?date=${date}`,
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Get meals logged for a date range.
 */
export function getMealsForDateRange(
    startDate: string,
    endDate: string,
    token: string
): Promise<MealIntakeLog[] | null> {
    return api
        .get<MealIntakeLog[] | null | ApiResponse<MealIntakeLog[] | null>>(
            `/api/nutrition/meals/range?startDate=${startDate}&endDate=${endDate}`,
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Delete a meal log entry.
 */
export function deleteMealLog(mealLogId: string, token: string): Promise<void> {
    return api.del(`/api/nutrition/meals/${mealLogId}`, token);
}

// ===== TREND TYPES & FUNCTIONS =====

export interface DailyTrendPoint {
    date: string;
    consumed: number;
    target: number;
    deficit: number; // positive = under target, negative = over target
}

export interface WeeklyTrendPoint {
    weekStart: string;
    weekEnd: string;
    totalConsumed: number;
    totalTarget: number;
    avgDailyDeficit: number;
    daysLogged: number;
}

export interface MonthlySummaryPoint {
    monthStart: string;
    monthEnd: string;
    totalConsumed: number;
    totalTarget: number;
    totalDeficit: number;
    daysLogged: number;
}

/**
 * Get daily caloric trends for a date range.
 */
export function getDailyTrends(
    startDate: string,
    endDate: string,
    token: string
): Promise<DailyTrendPoint[] | null> {
    return api
        .get<DailyTrendPoint[] | null | ApiResponse<DailyTrendPoint[] | null>>(
            `/api/nutrition/dashboard/trends/daily?startDate=${startDate}&endDate=${endDate}`,
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Get weekly aggregated trends.
 */
export function getWeeklyTrends(
    startDate: string,
    endDate: string,
    token: string
): Promise<WeeklyTrendPoint[] | null> {
    return api
        .get<WeeklyTrendPoint[] | null | ApiResponse<WeeklyTrendPoint[] | null>>(
            `/api/nutrition/dashboard/trends/weekly?startDate=${startDate}&endDate=${endDate}`,
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Get monthly aggregated trends.
 */
export function getMonthlySummaries(
    startDate: string,
    endDate: string,
    token: string
): Promise<MonthlySummaryPoint[] | null> {
    return api
        .get<MonthlySummaryPoint[] | null | ApiResponse<MonthlySummaryPoint[] | null>>(
            `/api/nutrition/dashboard/trends/monthly?startDate=${startDate}&endDate=${endDate}`,
            token
        )
        .then((response) => unwrapApiData(response));
}

// ===== SHOPPING LIST TYPES & FUNCTIONS =====

export interface ShoppingListItem {
    id: string;
    label: string;
    unit: string;
    quantity: number;
    category: string;
    excluded: boolean;
    notes: string | null;
}

export interface ShoppingListResponse {
    itemCount: number;
    allItems: ShoppingListItem[];
    categorized: Record<string, ShoppingListItem[]>;
}

/**
 * Generate shopping list from meal IDs.
 */
export function generateShoppingListFromMeals(
    mealIds: string[],
    token: string
): Promise<ShoppingListResponse | null> {
    return api
        .post<ShoppingListResponse | null | ApiResponse<ShoppingListResponse | null>>(
            "/api/nutrition/shopping-list/from-meals",
            { mealIds },
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Generate shopping list from date range.
 */
export function generateShoppingListFromDateRange(
    startDate: string,
    endDate: string,
    token: string
): Promise<ShoppingListResponse | null> {
    return api
        .post<ShoppingListResponse | null | ApiResponse<ShoppingListResponse | null>>(
            "/api/nutrition/shopping-list/from-date-range",
            { startDate, endDate },
            token
        )
        .then((response) => unwrapApiData(response));
}
