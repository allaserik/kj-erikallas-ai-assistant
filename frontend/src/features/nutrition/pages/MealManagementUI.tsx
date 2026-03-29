import { useState, useEffect } from "react";
import { Card } from "../../../shared/ui/Card";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import {
    reorderMeals,
    swapMeals,
    replaceMeal,
    getLatestMealPlan,
    type MealPlanMealItem,
} from "../../../shared/api/mealPlans";
import { searchRecipes, type RecipeListItem } from "../../../shared/api/recipes";

export interface MealManagementUIProps {
    /**
     * The date we're managing meals for (ISO string: YYYY-MM-DD)
     */
    date: string;
    onMealsUpdated?: () => void;
}

export function MealManagementUI({ date, onMealsUpdated }: MealManagementUIProps) {
    const token = localStorage.getItem("auth_token") || "";

    // State
    const [meals, setMeals] = useState<MealPlanMealItem[]>([]);
    const [recipes, setRecipes] = useState<RecipeListItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);

    // UI state for interactions
    const [selectedMealForSwap, setSelectedMealForSwap] = useState<string | null>(null);
    const [replacingMealId, setReplacingMealId] = useState<string | null>(null);
    const [newRecipeIdForReplace, setNewRecipeIdForReplace] = useState<string>("");

    // Load meals and recipes on mount
    useEffect(() => {
        const loadData = async () => {
            try {
                setIsLoading(true);
                setError(null);

                // Load latest meal plan and filter for specific date
                const mealPlan = await getLatestMealPlan(token);

                // Find meals for this specific date
                const filteredMeals = mealPlan?.days
                    .find((day) => day.date === date)
                    ?.meals || [];

                // Load all recipes using search with empty query
                const recipesData = await searchRecipes({}, token);

                setMeals(filteredMeals);
                setRecipes(recipesData || []);
            } catch (err) {
                const errorMsg = err instanceof Error ? err.message : "Failed to load meal plan";
                setError(errorMsg);
            } finally {
                setIsLoading(false);
            }
        };

        loadData();
    }, [date, token]);

    // Handle reordering meals
    const handleReorderMeals = async () => {
        try {
            setError(null);
            setSuccess(null);

            const mealIds = meals.map((m) => m.id);
            await reorderMeals(mealIds, token);

            setSuccess("Meals reordered successfully!");
            onMealsUpdated?.();

            // Clear success message after 3 seconds
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Failed to reorder meals";
            setError(errorMsg);
        }
    };

    // Handle swapping two meals
    const handleSwapMeal = async (mealId2: string) => {
        if (!selectedMealForSwap) return;

        try {
            setError(null);
            setSuccess(null);

            await swapMeals(selectedMealForSwap, mealId2, token);

            setSuccess("Meals swapped successfully!");
            setSelectedMealForSwap(null);
            onMealsUpdated?.();

            // Reload meals
            const updatedPlan = await getLatestMealPlan(token);
            const updatedMeals = updatedPlan?.days.find((day) => day.date === date)?.meals || [];
            setMeals(updatedMeals);

            // Clear success message after 3 seconds
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Failed to swap meals";
            setError(errorMsg);
        }
    };

    // Handle replacing a meal recipe
    const handleReplaceMeal = async (mealId: string) => {
        if (!newRecipeIdForReplace) {
            setError("Please select a recipe");
            return;
        }

        try {
            setError(null);
            setSuccess(null);

            await replaceMeal(mealId, newRecipeIdForReplace, token);

            setSuccess("Meal replaced successfully!");
            setReplacingMealId(null);
            setNewRecipeIdForReplace("");
            onMealsUpdated?.();

            // Reload meals
            const updatedPlan = await getLatestMealPlan(token);
            const updatedMeals = updatedPlan?.days.find((day) => day.date === date)?.meals || [];
            setMeals(updatedMeals);

            // Clear success message after 3 seconds
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Failed to replace meal";
            setError(errorMsg);
        }
    };

    // Move meal up in order
    const handleMoveUp = (index: number) => {
        if (index <= 0) return;
        const newMeals = [...meals];
        [newMeals[index - 1], newMeals[index]] = [newMeals[index], newMeals[index - 1]];
        setMeals(newMeals);
    };

    // Move meal down in order
    const handleMoveDown = (index: number) => {
        if (index >= meals.length - 1) return;
        const newMeals = [...meals];
        [newMeals[index], newMeals[index + 1]] = [newMeals[index + 1], newMeals[index]];
        setMeals(newMeals);
    };

    if (isLoading) {
        return (
            <Card>
                <div className="text-center text-gray-500">Loading meal plan...</div>
            </Card>
        );
    }

    if (meals.length === 0) {
        return (
            <Card>
                <div className="text-center text-gray-500">No meals planned for {date}</div>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            {error && <Alert tone="error">{error}</Alert>}
            {success && <Alert tone="success">{success}</Alert>}

            <Card>
                <h3 className="text-lg font-semibold mb-4">Manage Meals for {date}</h3>

                <div className="space-y-3">
                    {meals.map((meal, index) => (
                        <div
                            key={meal.id}
                            className="flex items-center justify-between p-4 border rounded-lg hover:bg-gray-50"
                        >
                            <div className="flex-1">
                                <div className="font-medium">{meal.mealType || "Meal"}</div>
                                <div className="text-sm text-gray-600">
                                    {recipes.find((r) => r.id === meal.recipeId)?.title || "Unknown Recipe"}
                                </div>
                            </div>

                            <div className="flex gap-2">
                                {/* Move Up Button */}
                                <button
                                    onClick={() => handleMoveUp(index)}
                                    disabled={index === 0}
                                    className="p-2 text-gray-600 hover:bg-gray-200 rounded disabled:opacity-50 disabled:cursor-not-allowed"
                                    title="Move up"
                                >
                                    ↑
                                </button>

                                {/* Move Down Button */}
                                <button
                                    onClick={() => handleMoveDown(index)}
                                    disabled={index >= meals.length - 1}
                                    className="p-2 text-gray-600 hover:bg-gray-200 rounded disabled:opacity-50 disabled:cursor-not-allowed"
                                    title="Move down"
                                >
                                    ↓
                                </button>

                                {/* Swap Button */}
                                <button
                                    onClick={() =>
                                        selectedMealForSwap === meal.id
                                            ? setSelectedMealForSwap(null)
                                            : setSelectedMealForSwap(meal.id)
                                    }
                                    className={`px-3 py-1 text-sm rounded font-medium transition ${selectedMealForSwap === meal.id
                                        ? "bg-blue-500 text-white"
                                        : "bg-blue-100 text-blue-700 hover:bg-blue-200"
                                        }`}
                                >
                                    {selectedMealForSwap === meal.id ? "Selected" : "Swap"}
                                </button>

                                {/* Replace Button */}
                                {replacingMealId !== meal.id && (
                                    <button
                                        onClick={() => setReplacingMealId(meal.id)}
                                        className="px-3 py-1 text-sm bg-green-100 text-green-700 hover:bg-green-200 rounded font-medium transition"
                                    >
                                        Replace
                                    </button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>

                {/* Swap interaction UI */}
                {selectedMealForSwap && (
                    <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                        <p className="text-sm font-medium text-blue-900 mb-3">
                            Select another meal to swap with
                        </p>
                        <div className="flex gap-2 flex-wrap">
                            {meals.map((meal) => (
                                <button
                                    key={meal.id}
                                    onClick={() => handleSwapMeal(meal.id)}
                                    disabled={meal.id === selectedMealForSwap}
                                    className={`px-3 py-2 text-sm rounded font-medium transition ${meal.id === selectedMealForSwap
                                        ? "opacity-50 cursor-not-allowed"
                                        : "bg-blue-500 text-white hover:bg-blue-600"
                                        }`}
                                >
                                    {meal.mealType}
                                </button>
                            ))}
                            <button
                                onClick={() => setSelectedMealForSwap(null)}
                                className="px-3 py-2 text-sm bg-gray-300 text-gray-700 hover:bg-gray-400 rounded font-medium transition"
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                )}

                {/* Replace interaction UI */}
                {replacingMealId && (
                    <div className="mt-4 p-4 bg-green-50 border border-green-200 rounded-lg">
                        <p className="text-sm font-medium text-green-900 mb-3">
                            Select a recipe to replace {meals.find((m) => m.id === replacingMealId)?.mealType || "meal"} with
                        </p>
                        <div className="mb-3 max-h-60 overflow-y-auto">
                            <div className="space-y-2">
                                {recipes.map((recipe) => (
                                    <button
                                        key={recipe.id}
                                        onClick={() => setNewRecipeIdForReplace(recipe.id)}
                                        className={`w-full text-left px-3 py-2 rounded transition text-sm ${newRecipeIdForReplace === recipe.id
                                            ? "bg-green-500 text-white font-medium"
                                            : "bg-white border hover:bg-green-50"
                                            }`}
                                    >
                                        {recipe.title}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="flex gap-2">
                            <Button
                                onClick={() => handleReplaceMeal(replacingMealId)}
                                disabled={!newRecipeIdForReplace}
                                className="flex-1 bg-green-500 hover:bg-green-600 disabled:opacity-50"
                            >
                                Confirm Replace
                            </Button>
                            <button
                                onClick={() => {
                                    setReplacingMealId(null);
                                    setNewRecipeIdForReplace("");
                                }}
                                className="px-4 py-2 bg-gray-300 text-gray-700 hover:bg-gray-400 rounded font-medium transition"
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                )}

                {/* Reorder Button */}
                <div className="mt-4 flex gap-2">
                    <Button
                        onClick={handleReorderMeals}
                        className="bg-purple-500 hover:bg-purple-600"
                    >
                        Save Meal Order
                    </Button>
                    <p className="text-sm text-gray-600 flex items-center">
                        Use ↑/↓ buttons to reorder meals, then click Save
                    </p>
                </div>
            </Card>
        </div>
    );
}
