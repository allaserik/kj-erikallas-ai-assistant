import { useState } from "react";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { useAuthedQuery } from "../../../shared/auth/useAuthedQuery";
import { getMealsForDate, logMeal, logCustomMeal, deleteMealLog } from "../../../shared/api/nutrition";
import { searchRecipes } from "../../../shared/api/recipes";
import { Button } from "../../../shared/ui/Button";
import { Card, CardBody, CardTitle, CardSubtitle } from "../../../shared/ui/Card";
import { TextField } from "../../../shared/ui/TextField";
import { Alert } from "../../../shared/ui/Alert";
import { ApiError } from "../../../shared/api/client";
import { Spinner } from "../../../shared/ui/Spinner";

type MealLogValidationErrors = {
    recipeId?: string;
    servings?: string;
    mealType?: string;
    customMealName?: string;
    customCalories?: string;
};

type MealMode = "recipe" | "custom";

interface MealLogCheckinProps {
    reloadKey: number;
    onReload: () => void;
}

function validateMealForm(
    mode: MealMode,
    recipeId: string,
    servings: string,
    mealType: string,
    customMealName: string,
    customCalories: string
): MealLogValidationErrors {
    const errors: MealLogValidationErrors = {};
    const servingsNum = Number(servings);
    const caloriesNum = Number(customCalories);

    if (mode === "recipe") {
        if (!recipeId) errors.recipeId = "Please select a recipe";
        if (!servings || !Number.isFinite(servingsNum) || servingsNum <= 0 || servingsNum > 10) {
            errors.servings = "Servings must be between 0.1 and 10";
        }
    } else {
        if (!customMealName?.trim()) errors.customMealName = "Meal name is required";
        if (!customCalories || !Number.isFinite(caloriesNum) || caloriesNum < 0 || caloriesNum > 5000) {
            errors.customCalories = "Calories must be between 0 and 5000";
        }
    }

    if (!mealType) errors.mealType = "Meal type is required";

    return errors;
}

export function MealLogCheckin({ reloadKey, onReload }: MealLogCheckinProps) {
    const getToken = useAuthToken();
    const [mealMode, setMealMode] = useState<MealMode>("recipe");

    // Recipe-based meal state
    const [recipeId, setRecipeId] = useState("");
    const [servings, setServings] = useState("1");
    const [mealType, setMealType] = useState("BREAKFAST");
    const [consumedTime, setConsumedTime] = useState("");

    // Custom meal state
    const [customMealName, setCustomMealName] = useState("");
    const [customCalories, setCustomCalories] = useState("");
    const [customProtein, setCustomProtein] = useState("");
    const [customCarbs, setCustomCarbs] = useState("");
    const [customFats, setCustomFats] = useState("");
    const [customNotes, setCustomNotes] = useState("");

    const [isMealSubmitting, setIsMealSubmitting] = useState(false);
    const [mealError, setMealError] = useState<string | null>(null);
    const [mealSuccess, setMealSuccess] = useState(false);
    const [mealValidationErrors, setMealValidationErrors] = useState<MealLogValidationErrors>({});

    const mealTypes = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];

    // Fetch recipes for selection
    const recipesQ = useAuthedQuery(
        `recipes-for-meal-log-${reloadKey}`,
        async (token: string) => {
            const recipes = await searchRecipes(
                { limit: 50 },
                token
            );
            return recipes || [];
        },
        true
    );

    // Fetch logged meals for today
    const today = new Date().toISOString().split("T")[0];
    const mealsQ = useAuthedQuery(
        `meals-today-${reloadKey}`,
        (token: string) => getMealsForDate(today, token),
        true
    );

    const handleMealSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setMealError(null);
        setMealSuccess(false);

        const errors = validateMealForm(mealMode, recipeId, servings, mealType, customMealName, customCalories);
        if (Object.keys(errors).length > 0) {
            setMealValidationErrors(errors);
            return;
        }

        setIsMealSubmitting(true);
        try {
            const token = await getToken();
            if (!token) throw new Error("Not authenticated");

            if (mealMode === "recipe") {
                await logMeal(
                    {
                        recipeId,
                        servings: Number(servings),
                        mealType,
                        consumedTime: consumedTime || null,
                    },
                    token
                );
            } else {
                await logCustomMeal(
                    {
                        mealName: customMealName,
                        mealType,
                        caloriesKcal: Number(customCalories),
                        proteinG: customProtein ? Number(customProtein) : 0,
                        carbsG: customCarbs ? Number(customCarbs) : 0,
                        fatsG: customFats ? Number(customFats) : 0,
                        notes: customNotes || undefined,
                        consumedTime: consumedTime || null,
                    },
                    token
                );
            }

            // Reset form
            setRecipeId("");
            setServings("1");
            setMealType("BREAKFAST");
            setConsumedTime("");
            setCustomMealName("");
            setCustomCalories("");
            setCustomProtein("");
            setCustomCarbs("");
            setCustomFats("");
            setCustomNotes("");
            setMealValidationErrors({});
            setMealSuccess(true);
            onReload();
            setTimeout(() => setMealSuccess(false), 3000);
        } catch (err) {
            const message = err instanceof ApiError ? err.message : "Failed to log meal";
            setMealError(message);
        } finally {
            setIsMealSubmitting(false);
        }
    };

    const handleDeleteMeal = async (mealLogId: string) => {
        try {
            const token = await getToken();
            if (!token) throw new Error("Not authenticated");

            await deleteMealLog(mealLogId, token);
            onReload();
        } catch (err) {
            const message = err instanceof ApiError ? err.message : "Failed to delete meal";
            setMealError(message);
        }
    };

    return (
        <Card>
            <CardTitle>Log Meal</CardTitle>
            <CardSubtitle>Record what you ate today</CardSubtitle>
            <CardBody>
                {mealError && <Alert tone="error" title="Error" message={mealError} />}
                {mealSuccess && (
                    <Alert tone="success" title="Success" message="Meal logged successfully!" />
                )}

                {/* Mode selection */}
                <div className="mb-4 flex gap-2">
                    <button
                        onClick={() => {
                            setMealMode("recipe");
                            setMealValidationErrors({});
                        }}
                        className={`px-4 py-2 rounded-lg font-medium transition ${mealMode === "recipe"
                            ? "bg-green-600 text-white"
                            : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                            }`}
                        disabled={isMealSubmitting}
                    >
                        From Recipe
                    </button>
                    <button
                        onClick={() => {
                            setMealMode("custom");
                            setMealValidationErrors({});
                        }}
                        className={`px-4 py-2 rounded-lg font-medium transition ${mealMode === "custom"
                            ? "bg-green-600 text-white"
                            : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                            }`}
                        disabled={isMealSubmitting}
                    >
                        Custom Meal
                    </button>
                </div>

                <form onSubmit={handleMealSubmit} className="space-y-4">
                    {/* Common fields */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Meal Type</label>
                            <select
                                value={mealType}
                                onChange={(e) => {
                                    setMealType(e.target.value);
                                    if (mealValidationErrors.mealType) setMealValidationErrors({});
                                }}
                                disabled={isMealSubmitting}
                                className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-green-200 transition"
                            >
                                {mealTypes.map((type) => (
                                    <option key={type} value={type}>
                                        {type}
                                    </option>
                                ))}
                            </select>
                            {mealValidationErrors.mealType && (
                                <p className="mt-1 text-sm text-red-600">{mealValidationErrors.mealType}</p>
                            )}
                        </div>

                        <TextField
                            label="Time (optional)"
                            type="time"
                            value={consumedTime}
                            onChange={(e) => setConsumedTime(e.target.value)}
                            disabled={isMealSubmitting}
                        />
                    </div>

                    {/* Recipe-based meal */}
                    {mealMode === "recipe" && (
                        <>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">
                                    Recipe {recipesQ.loading && <Spinner />}
                                </label>
                                {recipesQ.error && (
                                    <p className="text-sm text-red-600">Failed to load recipes</p>
                                )}
                                <select
                                    value={recipeId}
                                    onChange={(e) => {
                                        setRecipeId(e.target.value);
                                        if (mealValidationErrors.recipeId) setMealValidationErrors({});
                                    }}
                                    disabled={isMealSubmitting || recipesQ.loading}
                                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-green-200 transition"
                                >
                                    <option value="">Select a recipe...</option>
                                    {recipesQ.data?.map((recipe: any) => (
                                        <option key={recipe.id} value={recipe.id}>
                                            {recipe.title}
                                        </option>
                                    ))}
                                </select>
                                {mealValidationErrors.recipeId && (
                                    <p className="mt-1 text-sm text-red-600">{mealValidationErrors.recipeId}</p>
                                )}
                            </div>

                            <TextField
                                label="Servings"
                                type="number"
                                value={servings}
                                onChange={(e) => {
                                    setServings(e.target.value);
                                    if (mealValidationErrors.servings) setMealValidationErrors({});
                                }}
                                error={mealValidationErrors.servings}
                                min="0.1"
                                max="10"
                                step="0.5"
                                placeholder="1"
                                disabled={isMealSubmitting}
                            />
                        </>
                    )}

                    {/* Custom meal */}
                    {mealMode === "custom" && (
                        <>
                            <TextField
                                label="Meal Name"
                                type="text"
                                value={customMealName}
                                onChange={(e) => {
                                    setCustomMealName(e.target.value);
                                    if (mealValidationErrors.customMealName) setMealValidationErrors({});
                                }}
                                error={mealValidationErrors.customMealName}
                                placeholder="e.g., Pizza slice"
                                disabled={isMealSubmitting}
                                required
                            />

                            <div className="grid grid-cols-2 gap-4">
                                <TextField
                                    label="Calories (kcal)"
                                    type="number"
                                    value={customCalories}
                                    onChange={(e) => {
                                        setCustomCalories(e.target.value);
                                        if (mealValidationErrors.customCalories) setMealValidationErrors({});
                                    }}
                                    error={mealValidationErrors.customCalories}
                                    min="0"
                                    max="5000"
                                    step="10"
                                    placeholder="250"
                                    disabled={isMealSubmitting}
                                    required
                                />
                                <TextField
                                    label="Protein (g)"
                                    type="number"
                                    value={customProtein}
                                    onChange={(e) => setCustomProtein(e.target.value)}
                                    min="0"
                                    max="200"
                                    step="1"
                                    placeholder="10"
                                    disabled={isMealSubmitting}
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <TextField
                                    label="Carbs (g)"
                                    type="number"
                                    value={customCarbs}
                                    onChange={(e) => setCustomCarbs(e.target.value)}
                                    min="0"
                                    max="300"
                                    step="1"
                                    placeholder="35"
                                    disabled={isMealSubmitting}
                                />
                                <TextField
                                    label="Fats (g)"
                                    type="number"
                                    value={customFats}
                                    onChange={(e) => setCustomFats(e.target.value)}
                                    min="0"
                                    max="200"
                                    step="1"
                                    placeholder="8"
                                    disabled={isMealSubmitting}
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">
                                    Notes (optional)
                                </label>
                                <textarea
                                    value={customNotes}
                                    onChange={(e) => setCustomNotes(e.target.value)}
                                    placeholder="Any details about this meal?"
                                    disabled={isMealSubmitting}
                                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-green-200 transition"
                                    rows={2}
                                />
                            </div>
                        </>
                    )}

                    <Button
                        type="submit"
                        disabled={isMealSubmitting}
                        className="w-full"
                    >
                        {isMealSubmitting ? "Logging..." : "Log Meal"}
                    </Button>
                </form>

                {/* Show logged meals for today */}
                {mealsQ.data && mealsQ.data.length > 0 && (
                    <div className="mt-6 pt-6 border-t border-slate-200">
                        <h3 className="font-semibold text-slate-900 mb-3">
                            Today's Meals ({mealsQ.data.length})
                        </h3>
                        <div className="space-y-2">
                            {mealsQ.data.map((meal) => (
                                <div
                                    key={meal.id}
                                    className="flex items-center justify-between p-3 bg-slate-50 rounded-lg"
                                >
                                    <div className="flex-1">
                                        <p className="font-medium text-slate-900">
                                            {meal.customMealName || "Recipe meal"}
                                        </p>
                                        <p className="text-sm text-slate-600">
                                            {meal.mealType}
                                            {meal.consumedTime && ` • ${meal.consumedTime}`}
                                            {meal.caloriesKcal && ` • ${Math.round(meal.caloriesKcal)} kcal`}
                                        </p>
                                        {meal.customMealNotes && (
                                            <p className="text-xs text-slate-500 mt-1">{meal.customMealNotes}</p>
                                        )}
                                    </div>
                                    <button
                                        onClick={() => handleDeleteMeal(meal.id)}
                                        className="ml-4 px-2 py-1 text-sm text-red-600 hover:bg-red-50 rounded transition"
                                    >
                                        Delete
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
