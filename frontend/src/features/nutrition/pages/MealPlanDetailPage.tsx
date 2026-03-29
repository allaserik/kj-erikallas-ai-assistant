import { useEffect, useState } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { getMealPlanById, type GeneratedMealPlan, type MealPlanMealItem } from "../../../shared/api/mealPlans";
import { MealManagementUI } from "./MealManagementUI";
import { ShoppingList } from "../components/ShoppingList";

/**
 * MealPlanDetailPage: Shows a complete generated meal plan
 * Displays all days, meals, and nutrition information
 * Integrated with meal management for swap, reorder, replace, and add operations
 */
export default function MealPlanDetailPage() {
    const { mealPlanId } = useParams<{ mealPlanId: string }>();
    const location = useLocation();
    const navigate = useNavigate();
    const getToken = useAuthToken();

    const [mealPlan, setMealPlan] = useState<GeneratedMealPlan | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [selectedDay, setSelectedDay] = useState(0);
    const [selectedMeal, setSelectedMeal] = useState<MealPlanMealItem | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [showShoppingList, setShowShoppingList] = useState(false);

    const isNewlyGenerated = (location.state as any)?.newlyGenerated ?? false;

    useEffect(() => {
        const loadMealPlan = async () => {
            try {
                const token = await getToken();
                if (!token) throw new Error("Not authenticated");

                const data = await getMealPlanById(mealPlanId!, token);
                if (!data) {
                    throw new Error("Meal plan not found");
                }
                setMealPlan(data);
            } catch (err) {
                setError(err instanceof Error ? err.message : "An error occurred");
            } finally {
                setLoading(false);
            }
        };

        if (mealPlanId) loadMealPlan();
    }, [mealPlanId, getToken, refreshKey]);

    if (loading) {
        return <div className="text-center text-slate-600 py-12">Loading meal plan...</div>;
    }

    if (error) {
        return (
            <div className="text-center py-12">
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 inline-block">
                    {error}
                </div>
                <button
                    onClick={() => navigate("/planner")}
                    className="mt-4 text-slate-600 hover:text-slate-900"
                >
                    ← Back to Meal Plans
                </button>
            </div>
        );
    }

    if (!mealPlan) {
        return <div className="text-center text-slate-600 py-12">Meal plan not found</div>;
    }

    const currentDay = mealPlan.days[selectedDay];
    const totalCalories = currentDay.meals.reduce((sum, m) => sum + (m.caloriesKcal || 0), 0);
    const totalProtein = currentDay.meals.reduce((sum, m) => sum + (m.proteinG || 0), 0);

    return (
        <div>
            {/* Success Banner */}
            {isNewlyGenerated && (
                <div className="mb-6 bg-green-50 border border-green-200 rounded-lg p-4 text-green-700">
                    ✅ Your meal plan has been generated and saved!
                </div>
            )}

            {/* Header */}
            <div className="mb-8">
                <button
                    onClick={() => navigate("/planner")}
                    className="text-slate-600 hover:text-slate-900 text-sm mb-4 flex items-center gap-1"
                >
                    ← Back to Meal Plans
                </button>
                <h1 className="text-3xl font-bold text-slate-900">
                    Meal Plan {new Date(mealPlan.startDate).toLocaleDateString()} →{" "}
                    {new Date(mealPlan.endDate).toLocaleDateString()}
                </h1>
                <p className="text-slate-600 mt-2">{mealPlan.days.length} days of meals</p>
            </div>

            {/* Day Selector */}
            <div className="mb-8 overflow-x-auto">
                <div className="flex gap-2 pb-2">
                    {mealPlan.days.map((day, idx) => (
                        <button
                            key={idx}
                            onClick={() => setSelectedDay(idx)}
                            className={`px-4 py-2 rounded-lg whitespace-nowrap font-medium transition ${selectedDay === idx
                                ? "bg-green-600 text-white"
                                : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                                }`}
                        >
                            Day {idx + 1}
                            <br />
                            <span className="text-xs">
                                {new Date(day.date).toLocaleDateString("en", {
                                    month: "short",
                                    day: "numeric",
                                })}
                            </span>
                        </button>
                    ))}
                </div>
            </div>

            {/* Daily Summary */}
            <div className="mb-8 grid grid-cols-3 gap-4">
                <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
                    <p className="text-sm text-slate-600 mb-1">Calories</p>
                    <p className="text-2xl font-bold text-slate-900">{Math.round(totalCalories)} kcal</p>
                </div>
                <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
                    <p className="text-sm text-slate-600 mb-1">Protein</p>
                    <p className="text-2xl font-bold text-slate-900">{Math.round(totalProtein)}g</p>
                </div>
                <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
                    <p className="text-sm text-slate-600 mb-1">Meals</p>
                    <p className="text-2xl font-bold text-slate-900">{currentDay.meals.length}</p>
                </div>
            </div>

            {/* Shopping List Button */}
            <div className="mb-8">
                <button
                    onClick={() => setShowShoppingList(true)}
                    className="w-full bg-gradient-to-r from-orange-500 to-orange-600 hover:from-orange-600 hover:to-orange-700 text-white font-semibold py-3 px-6 rounded-lg transition shadow-md flex items-center justify-center gap-2"
                >
                    🛒 Generate Shopping List for This Plan
                </button>
                <p className="text-xs text-slate-600 mt-2">
                    Generate a categorized shopping list for all meals in this plan with editable quantities
                </p>
            </div>

            {/* Manage Meals */}
            <div className="mb-8">
                <h2 className="text-xl font-semibold text-slate-900 mb-4">Manage Meals</h2>
                <MealManagementUI
                    key={refreshKey}
                    date={currentDay.date}
                    onMealsUpdated={() => {
                        setRefreshKey((prev) => prev + 1);
                    }}
                />
            </div>

            {/* Future Features */}
            <div className="mt-12 bg-blue-50 border border-blue-200 rounded-lg p-6">
                <h3 className="font-semibold text-slate-900 mb-3">Coming Soon</h3>
                <ul className="text-sm text-slate-700 space-y-2">
                    <li>✓ Regenerate specific meals you don't like</li>
                    <li>✓ Download as PDF or shopping list</li>
                    <li>✓ Adjust nutrition targets on the fly</li>
                    <li>✓ Save favorite meal combinations</li>
                </ul>
            </div>

            {/* Recipe Detail Modal */}
            {selectedMeal && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
                        <div className="sticky top-0 bg-white border-b p-6 flex justify-between items-start">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                    {selectedMeal.mealType}
                                </p>
                                <h2 className="text-2xl font-bold text-slate-900">{selectedMeal.recipeTitle}</h2>
                            </div>
                            <button
                                onClick={() => setSelectedMeal(null)}
                                className="text-slate-400 hover:text-slate-600 text-2xl"
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
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
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
                                    <ul className="space-y-2">
                                        {selectedMeal.ingredients.map((ing: any, idx: number) => (
                                            <li key={idx} className="flex justify-between text-slate-700">
                                                <span>{ing.label}</span>
                                                <span className="font-medium text-slate-600">
                                                    {ing.quantity} {ing.unit}
                                                </span>
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}

                            {/* Preparation Steps */}
                            {selectedMeal.preparation && selectedMeal.preparation.length > 0 && (
                                <div>
                                    <h3 className="font-semibold text-slate-900 mb-3">Recipe Instructions</h3>
                                    <div className="space-y-3">
                                        {selectedMeal.preparation.map((step: any, idx: number) => (
                                            <div key={idx} className="flex gap-3">
                                                <div className="flex-shrink-0">
                                                    <div className="flex items-center justify-center h-8 w-8 rounded-full bg-green-100 text-green-700 font-semibold text-sm">
                                                        {step.stepNumber}
                                                    </div>
                                                </div>
                                                <div>
                                                    <p className="font-medium text-slate-900">{step.step}</p>
                                                    {step.description && (
                                                        <p className="text-slate-600 text-sm mt-1">{step.description}</p>
                                                    )}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Source */}
                            {selectedMeal.source && (
                                <div className="pt-3 border-t">
                                    <p className="text-xs text-slate-600">
                                        Source: <span className="font-medium">{selectedMeal.source}</span>
                                    </p>
                                </div>
                            )}

                            {/* Action Buttons */}
                            <div className="pt-4 border-t flex gap-3">
                                <button
                                    onClick={() => setSelectedMeal(null)}
                                    className="flex-1 bg-slate-100 hover:bg-slate-200 text-slate-900 font-medium py-2 px-4 rounded-lg transition"
                                >
                                    Close
                                </button>
                                <button
                                    disabled
                                    className="flex-1 bg-blue-100 text-blue-600 font-medium py-2 px-4 rounded-lg opacity-50 cursor-not-allowed"
                                >
                                    Swap Recipe (Coming Soon)
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Shopping List Modal */}
            {showShoppingList && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-40 overflow-y-auto">
                    <div className="bg-white rounded-lg max-w-4xl w-full my-8">
                        {/* Header */}
                        <div className="sticky top-0 bg-white border-b p-6 flex justify-between items-center">
                            <div>
                                <h2 className="text-2xl font-bold text-slate-900">Shopping List</h2>
                                <p className="text-slate-600 mt-1">
                                    {mealPlan?.days.length} days • {mealPlan?.totalMeals} meals
                                </p>
                            </div>
                            <button
                                onClick={() => setShowShoppingList(false)}
                                className="text-slate-400 hover:text-slate-600 text-2xl font-light"
                            >
                                ✕
                            </button>
                        </div>

                        {/* Content */}
                        <div className="max-h-[70vh] overflow-y-auto p-6">
                            <ShoppingList
                                mealIds={mealPlan ? mealPlan.days.flatMap((day) => day.meals.map((meal) => meal.id)) : []}
                            />
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
