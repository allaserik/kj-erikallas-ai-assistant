import { api } from "./client";
import type { ApiResponse } from "../types";
import { unwrapApiData } from "./unwrap";

export type MealPlanMealItem = {
    id: string;
    mealType: string;
    recipeId: string | null;
    recipeTitle: string;
    cuisine: string | null;
    timeMinutes: number | null;
    caloriesKcal: number | null;
    proteinG: number | null;
    carbsG: number | null;
    fatsG: number | null;
};

export type MealPlanDay = {
    date: string;
    meals: MealPlanMealItem[];
};

export type GeneratedMealPlan = {
    mealPlanId: string;
    periodType: "daily" | "weekly";
    startDate: string;
    endDate: string;
    totalMeals: number;
    days: MealPlanDay[];
};

export type GenerateMealPlanRequest = {
    duration: "daily" | "weekly";
    start_date?: string;
};

export function generateMealPlan(request: GenerateMealPlanRequest, token: string): Promise<GeneratedMealPlan> {
    return api
        .post<GeneratedMealPlan | ApiResponse<GeneratedMealPlan>>("/api/meal-plans/generate", request, token)
        .then((response) => unwrapApiData(response));
}

export function getMealPlanById(mealPlanId: string, token: string): Promise<GeneratedMealPlan | null> {
    return api
        .get<GeneratedMealPlan | null | ApiResponse<GeneratedMealPlan | null>>(`/api/meal-plans/${mealPlanId}`, token)
        .then((response) => unwrapApiData(response));
}

export function getLatestMealPlan(token: string): Promise<GeneratedMealPlan | null> {
    return api
        .get<GeneratedMealPlan | null | ApiResponse<GeneratedMealPlan | null>>("/api/meal-plans/latest", token)
        .then((response) => unwrapApiData(response));
}

// ===== MEAL MANAGEMENT =====

/**
 * Reorder meals within the same day.
 * 
 * @param mealIds List of meal IDs in desired order (all must be on same day)
 * @param token Auth token
 */
export function reorderMeals(mealIds: string[], token: string): Promise<string | null> {
    return api
        .post<string | null | ApiResponse<string | null>>(
            "/api/meal-plans/meals/reorder",
            { mealIds },
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Swap two meals (can be on same day or different days).
 * 
 * @param mealId1 First meal ID
 * @param mealId2 Second meal ID
 * @param token Auth token
 */
export function swapMeals(mealId1: string, mealId2: string, token: string): Promise<string | null> {
    return api
        .post<string | null | ApiResponse<string | null>>(
            "/api/meal-plans/meals/swap",
            { mealId1, mealId2 },
            token
        )
        .then((response) => unwrapApiData(response));
}

/**
 * Replace a meal with a different recipe.
 * 
 * @param mealId Meal ID to replace
 * @param recipeId New recipe ID
 * @param token Auth token
 */
export function replaceMeal(mealId: string, recipeId: string, token: string): Promise<string | null> {
    return api
        .post<string | null | ApiResponse<string | null>>(
            `/api/meal-plans/meals/${mealId}/replace`,
            { recipeId },
            token
        )
        .then((response) => unwrapApiData(response));
}
