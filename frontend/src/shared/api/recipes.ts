import { api } from "./client";
import type { ApiResponse } from "../types";
import { unwrapApiData } from "./unwrap";

export type RecipeNutritionSummary = {
    caloriesKcal: number;
    proteinG: number;
    carbsG: number;
    fatsG: number;
};

export type RecipeListItem = {
    id: string;
    title: string;
    cuisine: string | null;
    meal: string;
    servings: number;
    timeMinutes: number;
    difficultyLevel: string;
    dietaryTags: string[];
    nutrition: RecipeNutritionSummary;
};

export type RecipeIngredient = {
    id: string | null;
    label: string;
    unit: string;
    quantity: number;
    nutrition: RecipeNutritionSummary;
};

export type RecipePreparationStep = {
    stepNumber: number;
    step: string;
    description: string;
    ingredientRefs: string[];
};

export type RecipeDetail = {
    id: string;
    title: string;
    cuisine: string | null;
    meal: string;
    servings: number;
    summary: string | null;
    timeMinutes: number;
    difficultyLevel: string;
    dietaryTags: string[];
    source: string;
    img: string | null;
    ingredients: RecipeIngredient[];
    preparation: RecipePreparationStep[];
    nutrition: RecipeNutritionSummary;
};

export type RecipeSearchParams = {
    q?: string;
    cuisine?: string;
    meal?: string;
    dietaryTag?: string;
    allergyTag?: string;
    maxTimeMinutes?: number;
    maxCaloriesKcal?: number;
    limit?: number;
};

function toSearchQuery(params: RecipeSearchParams): string {
    const q = new URLSearchParams();
    if (params.q) q.set("q", params.q);
    if (params.cuisine) q.set("cuisine", params.cuisine);
    if (params.meal) q.set("meal", params.meal);
    if (params.dietaryTag) q.set("dietary_tag", params.dietaryTag);
    if (params.allergyTag) q.set("allergy_tag", params.allergyTag);
    if (params.maxTimeMinutes != null) q.set("max_time_minutes", String(params.maxTimeMinutes));
    if (params.maxCaloriesKcal != null) q.set("max_calories_kcal", String(params.maxCaloriesKcal));
    if (params.limit != null) q.set("limit", String(params.limit));
    const query = q.toString();
    return query ? `?${query}` : "";
}

export function searchRecipes(params: RecipeSearchParams, token: string): Promise<RecipeListItem[]> {
    return api
        .get<RecipeListItem[] | ApiResponse<RecipeListItem[]>>(`/api/recipes/search${toSearchQuery(params)}`, token)
        .then((response) => unwrapApiData(response));
}

export function getRecipeById(recipeId: string, token: string): Promise<RecipeDetail> {
    return api
        .get<RecipeDetail | ApiResponse<RecipeDetail>>(`/api/recipes/${recipeId}`, token)
        .then((response) => unwrapApiData(response));
}

export type IngredientSubstitution = {
    originalIngredientLabel: string;
    substituteLabel: string;
    unit: string;
    suggestedQuantity: number;
    rationale: string;
};

export type ScaledRecipeDetail = {
    id: string;
    title: string;
    cuisine: string | null;
    meal: string;
    originalServings: number;
    scaledServings: number;
    summary: string | null;
    timeMinutes: number;
    difficultyLevel: string;
    dietaryTags: string[];
    source: string;
    img: string | null;
    ingredients: RecipeIngredient[];
    preparation: RecipePreparationStep[];
    nutrition: RecipeNutritionSummary;
};

export function substituteIngredient(
    recipeId: string,
    ingredientId: string,
    reason: string | undefined,
    token: string
): Promise<IngredientSubstitution> {
    return api
        .post<IngredientSubstitution | ApiResponse<IngredientSubstitution>>(
            `/api/recipes/${recipeId}/substitute-ingredient`,
            { ingredient_id: ingredientId, reason: reason ?? null },
            token
        )
        .then((response) => unwrapApiData(response));
}

export function scaleServings(
    recipeId: string,
    targetServings: number,
    token: string
): Promise<ScaledRecipeDetail> {
    return api
        .post<ScaledRecipeDetail | ApiResponse<ScaledRecipeDetail>>(
            `/api/recipes/${recipeId}/scale-servings`,
            { target_servings: targetServings },
            token
        )
        .then((response) => unwrapApiData(response));
}
