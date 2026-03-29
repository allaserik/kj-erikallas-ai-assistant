import { useEffect, useState } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import { useAuthToken } from "../../shared/auth/useAuthToken";
import { getMealPlanById, type GeneratedMealPlan } from "../../shared/api/mealPlans";

/**
 * MealPlanDetailPage: Shows a complete generated meal plan
 * Displays all days, meals, and nutrition information
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
    }, [mealPlanId, getToken]);

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

            {/* Meals List */}
            <div className="space-y-4">
                {currentDay.meals.map((meal, idx) => (
                    <div
                        key={idx}
                        className="bg-white border border-slate-200 rounded-lg p-6 hover:shadow-md transition"
                    >
                        <div className="flex items-start justify-between mb-4">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">
                                    {meal.mealType}
                                </p>
                                <h3 className="text-lg font-semibold text-slate-900">{meal.recipeTitle}</h3>
                            </div>
                        </div>

                        {/* Cuisine */}
                        {meal.cuisine && (
                            <p className="text-sm text-slate-600 mb-4">Cuisine: {meal.cuisine}</p>
                        )}

                        {/* Nutrition Grid */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
                            <div className="bg-blue-50 rounded p-3">
                                <p className="text-xs text-slate-600 mb-1">Calories</p>
                                <p className="font-semibold text-slate-900">
                                    {meal.caloriesKcal ? Math.round(meal.caloriesKcal) : "—"} kcal
                                </p>
                            </div>
                            <div className="bg-red-50 rounded p-3">
                                <p className="text-xs text-slate-600 mb-1">Protein</p>
                                <p className="font-semibold text-slate-900">
                                    {meal.proteinG ? Math.round(meal.proteinG) : "—"}g
                                </p>
                            </div>
                            <div className="bg-yellow-50 rounded p-3">
                                <p className="text-xs text-slate-600 mb-1">Carbs</p>
                                <p className="font-semibold text-slate-900">
                                    {meal.carbsG ? Math.round(meal.carbsG) : "—"}g
                                </p>
                            </div>
                            <div className="bg-orange-50 rounded p-3">
                                <p className="text-xs text-slate-600 mb-1">Fat</p>
                                <p className="font-semibold text-slate-900">
                                    {meal.fatsG ? Math.round(meal.fatsG) : "—"}g
                                </p>
                            </div>
                        </div>

                        {/* Time */}
                        {meal.timeMinutes && (
                            <p className="text-sm text-slate-600">
                                ⏱️ {meal.timeMinutes} minutes
                            </p>
                        )}
                    </div>
                ))}
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
        </div>
    );
}
