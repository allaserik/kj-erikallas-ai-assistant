import { useState, useEffect } from "react";
import { Card } from "../../../shared/ui/Card";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import {
    reorderMeals,
    replaceMeal,
    deleteMeal,
    addMealToDay,
    getLatestMealPlan,
    getAlternativeMeals,
    regenerateMeal,
    type MealPlanMealItem,
    type GeneratedMealPlan,
} from "../../../shared/api/mealPlans";
import { searchRecipes, substituteIngredient, scaleServings, type RecipeListItem, type RecipeDetail, type IngredientSubstitution, type ScaledRecipeDetail } from "../../../shared/api/recipes";

export interface MealManagementUIProps {
    /**
     * The date we're managing meals for (ISO string: YYYY-MM-DD)
     */
    date: string;
    onMealsUpdated?: () => void;
}

export function MealManagementUI({ date, onMealsUpdated }: MealManagementUIProps) {
    const getToken = useAuthToken();

    // State
    const [token, setToken] = useState<string | null>(null);
    const [meals, setMeals] = useState<MealPlanMealItem[]>([]);
    const [recipes, setRecipes] = useState<RecipeListItem[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [success, setSuccess] = useState<string | null>(null);
    const [mealPlan, setMealPlan] = useState<GeneratedMealPlan | null>(null);

    // UI state for interactions
    const [selectedMeal, setSelectedMeal] = useState<MealPlanMealItem | null>(null);
    const [selectedMealForSwap, setSelectedMealForSwap] = useState<string | null>(null);
    const [showAlternatives, setShowAlternatives] = useState(false);
    const [alternativeMeals, setAlternativeMeals] = useState<RecipeDetail[]>([]);
    const [isLoadingAlternatives, setIsLoadingAlternatives] = useState(false);
    const [replacingMealId, setReplacingMealId] = useState<string | null>(null);
    const [newRecipeIdForReplace, setNewRecipeIdForReplace] = useState<string>("");
    const [isAddingMeal, setIsAddingMeal] = useState(false);
    const [newMealType, setNewMealType] = useState("snack");
    const [newMealRecipeId, setNewMealRecipeId] = useState<string>("");
    const [openMenuId, setOpenMenuId] = useState<string | null>(null);
    const [regeneratingMealId, setRegeneratingMealId] = useState<string | null>(null);
    // Ingredient substitution state
    const [substitutingIngredientId, setSubstitutingIngredientId] = useState<string | null>(null);
    const [substitutionResult, setSubstitutionResult] = useState<{ ingredientId: string; result: IngredientSubstitution } | null>(null);
    // Scale servings state
    const [scaledTarget, setScaledTarget] = useState<number>(2);
    const [scaledResult, setScaledResult] = useState<ScaledRecipeDetail | null>(null);
    const [isScaling, setIsScaling] = useState(false);

    // Get token on mount
    useEffect(() => {
        getToken().then(setToken);
    }, [getToken]);

    // Load meals and recipes when token is available
    useEffect(() => {
        if (!token) return;

        const loadData = async () => {
            try {
                setIsLoading(true);
                setError(null);

                // Load latest meal plan and filter for specific date
                const mealPlanData = await getLatestMealPlan(token);
                setMealPlan(mealPlanData);

                if (!mealPlanData) {
                    setError(
                        "No meal plan found. Please generate a meal plan first from the Meal Planning hub."
                    );
                    setMeals([]);
                } else {
                    // Find meals for this specific date
                    const dayInPlan = mealPlanData.days.find((day) => day.date === date);

                    if (!dayInPlan) {
                        setError(
                            `No meals found for ${new Date(date).toLocaleDateString()}. ` +
                            `Your meal plan runs from ${new Date(mealPlanData.startDate).toLocaleDateString()} ` +
                            `to ${new Date(mealPlanData.endDate).toLocaleDateString()}.`
                        );
                        setMeals([]);
                    } else {
                        setMeals(dayInPlan.meals);
                    }
                }

                // Load all recipes using search with empty query
                const recipesData = await searchRecipes({}, token);
                setRecipes(recipesData || []);
            } catch (err) {
                const errorMsg = err instanceof Error ? err.message : "Failed to load meal plan";
                setError(errorMsg);
                setMeals([]);
            } finally {
                setIsLoading(false);
            }
        };

        loadData();
    }, [token, date]);

    // Reset modal-related AI state when the selected meal changes
    useEffect(() => {
        setSubstitutionResult(null);
        setScaledResult(null);
        setScaledTarget(2);
    }, [selectedMeal]);

    // Handle regenerating a meal with AI
    const handleRegenerateMeal = async (mealId: string) => {
        if (!token) { setError("Not authenticated"); return; }
        try {
            setError(null);
            setSuccess(null);
            setRegeneratingMealId(mealId);
            await regenerateMeal(mealId, token);
            setSuccess("Meal regenerated successfully!");
            onMealsUpdated?.();
            const updatedPlan = await getLatestMealPlan(token);
            setMeals(updatedPlan?.days.find((d) => d.date === date)?.meals || []);
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to regenerate meal");
        } finally {
            setRegeneratingMealId(null);
        }
    };

    // Handle AI ingredient substitution suggestion
    const handleSubstituteIngredient = async (recipeId: string, ingredientId: string) => {
        if (!token) { setError("Not authenticated"); return; }
        try {
            setError(null);
            setSubstitutingIngredientId(ingredientId);
            setSubstitutionResult(null);
            const result = await substituteIngredient(recipeId, ingredientId, undefined, token);
            setSubstitutionResult({ ingredientId, result });
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to get substitution suggestion");
        } finally {
            setSubstitutingIngredientId(null);
        }
    };

    // Handle scale-servings calculation
    const handleScaleServings = async (recipeId: string) => {
        if (!token) { setError("Not authenticated"); return; }
        if (scaledTarget < 1) { setError("Target servings must be at least 1"); return; }
        try {
            setError(null);
            setIsScaling(true);
            setScaledResult(null);
            const result = await scaleServings(recipeId, scaledTarget, token);
            setScaledResult(result);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to scale servings");
        } finally {
            setIsScaling(false);
        }
    };

    // Handle reordering meals
    const handleReorderMeals = async () => {
        if (!token) { setError("Not authenticated"); return; }
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

    // Handle loading alternatives for a meal
    const handleLoadAlternatives = async (mealId: string) => {
        if (!token) {
            setError("Not authenticated");
            return;
        }

        try {
            setError(null);
            setIsLoadingAlternatives(true);
            const alternatives = await getAlternativeMeals(mealId, 5, token);
            setAlternativeMeals(alternatives);
            setSelectedMealForSwap(mealId);
            setShowAlternatives(true);
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Failed to load alternatives";
            setError(errorMsg);
            setShowAlternatives(false);
        } finally {
            setIsLoadingAlternatives(false);
        }
    };

    // Handle replacing a meal with an alternative
    const handleReplaceWithAlternative = async (alternativeRecipeId: string) => {
        if (!token || !selectedMealForSwap) {
            setError("Not authenticated");
            return;
        }

        try {
            setError(null);
            setSuccess(null);

            await replaceMeal(selectedMealForSwap, alternativeRecipeId, token);

            setSuccess("Meal replaced successfully!");
            setShowAlternatives(false);
            setSelectedMealForSwap(null);
            setAlternativeMeals([]);
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

    // Handle replacing a meal recipe
    const handleReplaceMeal = async (mealId: string) => {
        if (!token) {
            setError("Not authenticated");
            return;
        }
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

    // Handle deleting a meal
    const handleDeleteMeal = async (mealId: string) => {
        if (!token) {
            setError("Not authenticated");
            return;
        }
        if (!window.confirm("Are you sure you want to delete this meal?")) {
            return;
        }

        try {
            setError(null);
            setSuccess(null);

            await deleteMeal(mealId, token);

            setSuccess("Meal deleted successfully!");
            onMealsUpdated?.();

            // Reload meals
            const updatedPlan = await getLatestMealPlan(token);
            const updatedMeals = updatedPlan?.days.find((day) => day.date === date)?.meals || [];
            setMeals(updatedMeals);

            // Clear success message after 3 seconds
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Failed to delete meal";
            setError(errorMsg);
        }
    };

    // Handle adding a new meal
    const handleAddMeal = async () => {
        if (!token) {
            setError("Not authenticated");
            return;
        }
        if (!newMealRecipeId) {
            setError("Please select a recipe");
            return;
        }

        try {
            setError(null);
            setSuccess(null);

            const mealPlan = await getLatestMealPlan(token);
            if (!mealPlan) {
                throw new Error("Meal plan not found");
            }

            await addMealToDay(mealPlan.mealPlanId, date, newMealType, newMealRecipeId, token);

            setSuccess("Meal added successfully!");
            setIsAddingMeal(false);
            setNewMealRecipeId("");
            setNewMealType("snack");
            onMealsUpdated?.();

            // Reload meals
            const updatedPlan = await getLatestMealPlan(token);
            const updatedMeals = updatedPlan?.days.find((day) => day.date === date)?.meals || [];
            setMeals(updatedMeals);

            // Clear success message after 3 seconds
            setTimeout(() => setSuccess(null), 3000);
        } catch (err) {
            const errorMsg = err instanceof Error ? err.message : "Failed to add meal";
            setError(errorMsg);
        }
    };

    if (!token || isLoading) {
        return (
            <Card>
                <div className="text-center text-gray-500">
                    {!token ? "Authenticating..." : "Loading meal plan..."}
                </div>
            </Card>
        );
    }

    if (error) {
        return (
            <div className="space-y-4">
                <Card>
                    <Alert tone="error">{error}</Alert>
                </Card>
                <Card>
                    <div className="text-center text-sm text-gray-600">
                        <p className="mb-3">
                            {!mealPlan
                                ? "Generate a meal plan first to start managing your meals."
                                : "Select a date within your meal plan range to manage meals."}
                        </p>
                        {!mealPlan && (
                            <a
                                href="/nutrition/generate"
                                className="inline-block px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700"
                            >
                                Generate Meal Plan
                            </a>
                        )}
                    </div>
                </Card>
            </div>
        );
    }

    if (meals.length === 0) {
        return (
            <Card>
                <div className="text-center text-gray-500">
                    <p>No meals planned for {new Date(date).toLocaleDateString()}</p>
                    {mealPlan && (
                        <p className="text-sm text-gray-400 mt-2">
                            Your plan runs from {new Date(mealPlan.startDate).toLocaleDateString()} to{" "}
                            {new Date(mealPlan.endDate).toLocaleDateString()}
                        </p>
                    )}
                </div>
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
                            className="flex items-center justify-between p-4 border rounded-lg hover:border-blue-300 transition"
                        >
                            {/* Meal Info - Clickable to view details */}
                            <div
                                className="flex-1 cursor-pointer hover:text-blue-600 transition pr-4"
                                onClick={() => setSelectedMeal(meal)}
                            >
                                <div className="font-medium capitalize">{meal.mealType || "Meal"}</div>
                                <div className="text-sm text-gray-600 font-semibold hover:underline">{meal.recipeTitle}</div>
                                {meal.caloriesKcal && (
                                    <div className="text-xs text-gray-500 mt-1">
                                        {Math.round(meal.caloriesKcal)} cal • {Math.round(meal.proteinG || 0)}g protein
                                    </div>
                                )}
                                <div className="text-xs text-blue-500 mt-1">👁 Click to view details</div>
                            </div>

                            {/* Management Buttons */}
                            <div className="flex gap-1">
                                {/* Swap Button - Most common action */}
                                <button
                                    onClick={() => handleLoadAlternatives(meal.id)}
                                    disabled={isLoadingAlternatives && selectedMealForSwap === meal.id}
                                    className={`px-3 py-1 text-sm rounded font-medium transition ${selectedMealForSwap === meal.id && showAlternatives
                                        ? "bg-blue-500 text-white"
                                        : "bg-blue-100 text-blue-700 hover:bg-blue-200"
                                        } ${isLoadingAlternatives && selectedMealForSwap === meal.id ? "opacity-50 cursor-not-allowed" : ""}`}
                                    title="Find alternative meals"
                                >
                                    {isLoadingAlternatives && selectedMealForSwap === meal.id ? "Loading..." : "Swap"}
                                </button>

                                {/* Delete Button */}
                                <button
                                    onClick={() => handleDeleteMeal(meal.id)}
                                    className="px-3 py-1 text-sm bg-red-100 text-red-700 hover:bg-red-200 rounded font-medium transition"
                                    title="Delete meal"
                                >
                                    Delete
                                </button>

                                {/* More Options - Reorder & Replace */}
                                <div className="relative">
                                    <button
                                        onClick={() => setOpenMenuId(openMenuId === meal.id ? null : meal.id)}
                                        className="px-3 py-1 text-sm bg-gray-100 text-gray-700 hover:bg-gray-200 rounded font-medium transition"
                                        title="More options"
                                    >
                                        ⋮
                                    </button>
                                    {openMenuId === meal.id && (
                                        <div className="flex flex-col absolute right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-10">
                                            <button
                                                onClick={() => { handleRegenerateMeal(meal.id); setOpenMenuId(null); }}
                                                disabled={regeneratingMealId === meal.id}
                                                className="px-4 py-2 text-sm text-left whitespace-nowrap text-purple-700 hover:bg-purple-50 border-b disabled:opacity-50"
                                            >
                                                {regeneratingMealId === meal.id ? "Regenerating…" : "✨ Regenerate"}
                                            </button>
                                            {index > 0 && (
                                                <button
                                                    onClick={() => { handleMoveUp(index); setOpenMenuId(null); }}
                                                    className="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 text-left whitespace-nowrap border-b"
                                                    title="Move up"
                                                >
                                                    ↑ Move Up
                                                </button>
                                            )}
                                            {index < meals.length - 1 && (
                                                <button
                                                    onClick={() => { handleMoveDown(index); setOpenMenuId(null); }}
                                                    className="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 text-left whitespace-nowrap border-b"
                                                    title="Move down"
                                                >
                                                    ↓ Move Down
                                                </button>
                                            )}
                                            <button
                                                onClick={() => { setReplacingMealId(replacingMealId === meal.id ? null : meal.id); setOpenMenuId(null); }}
                                                className={`px-4 py-2 text-sm text-left whitespace-nowrap ${replacingMealId === meal.id
                                                    ? "bg-green-50 text-green-700"
                                                    : "text-gray-700 hover:bg-gray-100"
                                                    }`}
                                            >
                                                🔄 Replace Recipe
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                {/* Swap with Alternatives Modal */}
                {showAlternatives && selectedMealForSwap && (
                    <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                        <p className="text-sm font-medium text-blue-900 mb-3">
                            💡 Alternative meals for {meals.find((m) => m.id === selectedMealForSwap)?.mealType || "meal"}:
                        </p>
                        {alternativeMeals.length === 0 ? (
                            <p className="text-sm text-blue-700 mb-3">No alternatives found. Try a different meal.</p>
                        ) : (
                            <div className="space-y-3 max-h-72 overflow-y-auto">
                                {alternativeMeals.map((alt) => (
                                    <div key={alt.id} className="p-3 bg-white border border-blue-100 rounded hover:border-blue-300 transition">
                                        <div className="flex justify-between items-start gap-3">
                                            <div className="flex-1 min-w-0">
                                                <div className="font-semibold text-gray-800">{alt.title}</div>
                                                {alt.cuisine && <div className="text-xs text-gray-500">{alt.cuisine}</div>}
                                                <div className="text-xs text-gray-600 mt-1">
                                                    {Math.round(alt.nutrition.caloriesKcal)} cal • {Math.round(alt.nutrition.proteinG)}g protein • {Math.round(alt.nutrition.carbsG)}g carbs • {Math.round(alt.nutrition.fatsG)}g fat
                                                </div>
                                                {alt.timeMinutes && <div className="text-xs text-gray-500 mt-1">⏱ {alt.timeMinutes} min</div>}
                                            </div>
                                            <button
                                                onClick={() => handleReplaceWithAlternative(alt.id)}
                                                className="px-3 py-1 text-sm bg-blue-500 text-white hover:bg-blue-600 rounded font-medium whitespace-nowrap transition"
                                            >
                                                Choose
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <button
                            onClick={() => {
                                setShowAlternatives(false);
                                setSelectedMealForSwap(null);
                                setAlternativeMeals([]);
                            }}
                            className="mt-3 px-3 py-2 text-sm bg-gray-300 text-gray-700 hover:bg-gray-400 rounded font-medium transition"
                        >
                            Cancel
                        </button>
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

                {/* Add Meal Section */}
                {!isAddingMeal && (
                    <div className="mt-4">
                        <Button
                            onClick={() => setIsAddingMeal(true)}
                            className="bg-blue-500 hover:bg-blue-600"
                        >
                            + Add Meal
                        </Button>
                    </div>
                )}

                {isAddingMeal && (
                    <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                        <p className="text-sm font-medium text-blue-900 mb-3">
                            Add a new meal to {date}
                        </p>
                        <div className="space-y-3">
                            {/* Meal Type Select */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Meal Type
                                </label>
                                <select
                                    value={newMealType}
                                    onChange={(e) => setNewMealType(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                                >
                                    <option value="breakfast">Breakfast</option>
                                    <option value="lunch">Lunch</option>
                                    <option value="dinner">Dinner</option>
                                    <option value="snack">Snack</option>
                                </select>
                            </div>

                            {/* Recipe Select */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Select Recipe
                                </label>
                                <div className="max-h-60 overflow-y-auto border border-gray-300 rounded-lg p-2">
                                    <div className="space-y-2">
                                        {recipes.map((recipe) => (
                                            <button
                                                key={recipe.id}
                                                onClick={() => setNewMealRecipeId(recipe.id)}
                                                className={`w-full text-left px-3 py-2 rounded transition text-sm ${newMealRecipeId === recipe.id
                                                    ? "bg-blue-500 text-white font-medium"
                                                    : "bg-white border hover:bg-blue-50"
                                                    }`}
                                            >
                                                {recipe.title}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>

                            {/* Action Buttons */}
                            <div className="flex gap-2">
                                <Button
                                    onClick={handleAddMeal}
                                    disabled={!newMealRecipeId}
                                    className="flex-1 bg-blue-500 hover:bg-blue-600 disabled:opacity-50"
                                >
                                    Add Meal
                                </Button>
                                <button
                                    onClick={() => {
                                        setIsAddingMeal(false);
                                        setNewMealRecipeId("");
                                        setNewMealType("snack");
                                    }}
                                    className="px-4 py-2 bg-gray-300 text-gray-700 hover:bg-gray-400 rounded font-medium transition"
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </Card>

            {/* Recipe Detail Modal */}
            {selectedMeal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
                        {/* Header */}
                        <div className="sticky top-0 bg-white border-b p-6 flex justify-between items-start">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                    {selectedMeal.mealType}
                                </p>
                                <h2 className="text-2xl font-bold text-slate-900">{selectedMeal.recipeTitle}</h2>
                            </div>
                            <button
                                onClick={() => setSelectedMeal(null)}
                                className="text-slate-400 hover:text-slate-600 text-2xl font-light"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="p-6 space-y-6">
                            {/* Recipe Image */}
                            {selectedMeal.img && (
                                <div className="w-full h-48 bg-slate-200 rounded-lg overflow-hidden">
                                    <img
                                        src={selectedMeal.img}
                                        alt={selectedMeal.recipeTitle}
                                        className="w-full h-full object-cover"
                                    />
                                </div>
                            )}

                            {/* Summary */}
                            {selectedMeal.summary && (
                                <div>
                                    <h3 className="font-semibold text-slate-900 mb-2">About This Recipe</h3>
                                    <p className="text-slate-700">{selectedMeal.summary}</p>
                                </div>
                            )}

                            {/* Recipe Metadata */}
                            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                                {selectedMeal.timeMinutes && (
                                    <div className="bg-slate-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Time</p>
                                        <p className="font-semibold">{selectedMeal.timeMinutes} min</p>
                                    </div>
                                )}
                                {selectedMeal.difficultyLevel && (
                                    <div className="bg-slate-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Difficulty</p>
                                        <p className="font-semibold capitalize">{selectedMeal.difficultyLevel}</p>
                                    </div>
                                )}
                                {selectedMeal.cuisine && (
                                    <div className="bg-slate-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Cuisine</p>
                                        <p className="font-semibold">{selectedMeal.cuisine}</p>
                                    </div>
                                )}
                            </div>

                            {/* Nutrition */}
                            <div>
                                <h3 className="font-semibold text-slate-900 mb-3">Nutritional Information (per serving)</h3>
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                                    <div className="bg-blue-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Calories</p>
                                        <p className="font-semibold">
                                            {selectedMeal.caloriesKcal ? Math.round(selectedMeal.caloriesKcal) : "—"} kcal
                                        </p>
                                    </div>
                                    <div className="bg-red-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Protein</p>
                                        <p className="font-semibold">
                                            {selectedMeal.proteinG ? Math.round(selectedMeal.proteinG) : "—"}g
                                        </p>
                                    </div>
                                    <div className="bg-yellow-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Carbs</p>
                                        <p className="font-semibold">
                                            {selectedMeal.carbsG ? Math.round(selectedMeal.carbsG) : "—"}g
                                        </p>
                                    </div>
                                    <div className="bg-orange-50 rounded p-3">
                                        <p className="text-xs text-slate-600 mb-1">Fat</p>
                                        <p className="font-semibold">
                                            {selectedMeal.fatsG ? Math.round(selectedMeal.fatsG) : "—"}g
                                        </p>
                                    </div>
                                </div>
                            </div>

                            {/* Ingredients */}
                            {selectedMeal.ingredients && selectedMeal.ingredients.length > 0 && (
                                <div>
                                    <h3 className="font-semibold text-slate-900 mb-3">Ingredients</h3>
                                    <ul className="space-y-3">
                                        {selectedMeal.ingredients.map((ing, idx) => (
                                            <li key={idx} className="text-sm text-slate-700">
                                                <div className="flex justify-between items-center">
                                                    <span>{ing.label}</span>
                                                    <span className="text-slate-600">
                                                        {ing.quantity} {ing.unit}
                                                    </span>
                                                </div>
                                                {ing.id && (
                                                    <div className="mt-1">
                                                        <button
                                                            onClick={() => handleSubstituteIngredient(selectedMeal.recipeId, ing.id)}
                                                            disabled={substitutingIngredientId === ing.id}
                                                            className="text-xs text-blue-500 hover:text-blue-700 disabled:opacity-50"
                                                        >
                                                            {substitutingIngredientId === ing.id ? "Thinking…" : "🔄 Suggest substitute"}
                                                        </button>
                                                        {substitutionResult?.ingredientId === ing.id && (
                                                            <div className="mt-1 p-2 bg-blue-50 border border-blue-200 rounded text-xs">
                                                                <span className="font-semibold text-blue-900">{substitutionResult.result.substituteLabel}</span>
                                                                {" "}— {substitutionResult.result.suggestedQuantity} {substitutionResult.result.unit}
                                                                {substitutionResult.result.rationale && (
                                                                    <p className="text-slate-600 mt-1">{substitutionResult.result.rationale}</p>
                                                                )}
                                                            </div>
                                                        )}
                                                    </div>
                                                )}
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}

                            {/* Preparation Steps */}
                            {selectedMeal.preparation && selectedMeal.preparation.length > 0 && (
                                <div>
                                    <h3 className="font-semibold text-slate-900 mb-3">Preparation Steps</h3>
                                    <ol className="space-y-3">
                                        {selectedMeal.preparation.map((step) => (
                                            <li key={step.stepNumber} className="text-sm text-slate-700">
                                                <div className="font-semibold text-slate-900 mb-1">
                                                    Step {step.stepNumber}: {step.step}
                                                </div>
                                                <div className="text-slate-600">{step.description}</div>
                                            </li>
                                        ))}
                                    </ol>
                                </div>
                            )}

                            {/* Scale Servings */}
                            <div>
                                <h3 className="font-semibold text-slate-900 mb-3">Scale Servings</h3>
                                <div className="flex items-center gap-3">
                                    <label className="text-sm text-slate-600">Target:</label>
                                    <input
                                        type="number"
                                        min={1}
                                        max={20}
                                        value={scaledTarget}
                                        onChange={(e) => setScaledTarget(parseInt(e.target.value) || 1)}
                                        className="w-20 px-2 py-1 border border-slate-300 rounded text-sm"
                                    />
                                    <button
                                        onClick={() => handleScaleServings(selectedMeal.recipeId)}
                                        disabled={isScaling}
                                        className="px-3 py-1 text-sm bg-slate-100 hover:bg-slate-200 rounded font-medium transition disabled:opacity-50"
                                    >
                                        {isScaling ? "Calculating…" : "Calculate"}
                                    </button>
                                </div>
                                {scaledResult && (
                                    <div className="mt-3 p-3 bg-slate-50 border border-slate-200 rounded-lg">
                                        <p className="text-xs font-semibold text-slate-700 mb-2">
                                            {scaledResult.scaledServings} servings (from {scaledResult.originalServings}):
                                        </p>
                                        <ul className="space-y-1 text-xs text-slate-700 mb-2">
                                            {scaledResult.ingredients.map((ing, i) => (
                                                <li key={i} className="flex justify-between">
                                                    <span>{ing.label}</span>
                                                    <span>{Number(ing.quantity).toFixed(1)} {ing.unit}</span>
                                                </li>
                                            ))}
                                        </ul>
                                        <div className="grid grid-cols-4 gap-2 text-xs text-slate-600 pt-2 border-t border-slate-200">
                                            <div><span className="font-medium">Cal:</span> {Math.round(Number(scaledResult.nutrition.caloriesKcal))} kcal</div>
                                            <div><span className="font-medium">Pro:</span> {Math.round(Number(scaledResult.nutrition.proteinG))}g</div>
                                            <div><span className="font-medium">Carb:</span> {Math.round(Number(scaledResult.nutrition.carbsG))}g</div>
                                            <div><span className="font-medium">Fat:</span> {Math.round(Number(scaledResult.nutrition.fatsG))}g</div>
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Close Button */}
                            <div className="flex gap-3 pt-4 border-t">
                                <Button
                                    onClick={() => setSelectedMeal(null)}
                                    className="flex-1 bg-slate-200 text-slate-700 hover:bg-slate-300"
                                >
                                    Close
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
