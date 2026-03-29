import { api } from "./client";
import type { ApiResponse } from "../types";
import { unwrapApiData } from "./unwrap";

export type RecipeIngredient = {
    id: string;
    label: string;
    unit: string;
    quantity: number;
    caloriesKcal: number | null;
    proteinG: number | null;
    carbsG: number | null;
    fatsG: number | null;
};

export type RecipeStep = {
    stepNumber: number;
    step: string;
    description: string;
};

export type MealPlanMealItem = {
    id: string;
    mealType: string;
    recipeId: string;
    recipeTitle: string;
    cuisine: string | null;
    timeMinutes: number | null;
    difficultyLevel: string | null;
    summary: string | null;
    source: string | null;
    img: string | null;
    caloriesKcal: number | null;
    proteinG: number | null;
    carbsG: number | null;
    fatsG: number | null;
    ingredients: RecipeIngredient[];
    preparation: RecipeStep[];
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

// ===== PHASE 3-4: AI MEAL PLAN GENERATION =====

export type AIGenerationRequest = {
    query: string;
    numberOfDays: number;
    mealType?: string;
    calorieTarget?: number;
    proteinTargetG?: number;
    dietaryPreferences?: string[];
    cuisinePreferences?: string[];
    allergies?: string[];
};

export type RecipeSearchResult = {
    id: string;
    title: string;
    cuisine: string | null;
    mealType: string;
    caloriesKcal: number;
    proteinG: number;
    carbsG: number;
    fatsG: number;
    servings: number;
};

export function generateAIMealPlan(request: AIGenerationRequest, token: string): Promise<GeneratedMealPlan> {
    return api
        .post<GeneratedMealPlan | ApiResponse<GeneratedMealPlan>>(
            "/api/meal-plans/ai/generate-and-save",
            request,
            token
        )
        .then((response) => unwrapApiData(response));
}

export function searchRecipesByQuery(query: string, limit: number, token: string): Promise<RecipeSearchResult[]> {
    return api
        .get<RecipeSearchResult[] | ApiResponse<RecipeSearchResult[]>>(
            `/api/meal-plans/ai/search?query=${encodeURIComponent(query)}&limit=${limit}`,
            token
        )
        .then((response) => unwrapApiData(response));
}

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
