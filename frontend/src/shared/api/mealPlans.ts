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
