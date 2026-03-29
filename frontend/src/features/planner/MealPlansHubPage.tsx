import { useEffect, useState } from "react";
import { useAppAuth } from "../../shared/auth/AuthContext";
import { useAuthToken } from "../../shared/auth/useAuthToken";
import { getLatestMealPlan, type GeneratedMealPlan } from "../../shared/api/mealPlans";
import { Link } from "react-router-dom";

/**
 * MealPlansHubPage: Simple hub showing existing meal plans
 * and options to generate new ones or log meals
 */
export default function MealPlansHubPage() {
    const { isAuthenticated } = useAppAuth();
    const getToken = useAuthToken();
    const [token, setToken] = useState<string | null>(null);
    const [latestPlan, setLatestPlan] = useState<GeneratedMealPlan | null>(null);

    useEffect(() => {
        getToken().then(setToken);
    }, [getToken]);

    useEffect(() => {
        if (!token) return;

        getLatestMealPlan(token)
            .then(setLatestPlan)
            .catch(() => {
                setLatestPlan(null);
            });
    }, [token]);

    if (!isAuthenticated || !token) {
        return <div className="text-center text-slate-600">Please log in to view meal plans.</div>;
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold text-slate-900">Meal Plans</h1>
                <p className="text-slate-600 mt-2">
                    Generate AI meal plans or manage your existing meal schedule
                </p>
            </div>

            {/* Quick Actions */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* Generate New Plan */}
                <Link
                    to="/planner/generate-meal-plan"
                    className="bg-gradient-to-br from-green-500 to-green-600 rounded-lg p-6 text-white hover:shadow-lg transition cursor-pointer"
                >
                    <div className="text-3xl mb-2">✨</div>
                    <h3 className="font-semibold text-lg">Generate New Plan</h3>
                    <p className="text-sm text-green-50 mt-2">
                        Create AI-generated meal plans based on your preferences
                    </p>
                </Link>

                {/* View Current Plan */}
                <Link
                    to={latestPlan ? `/planner/meal-plan/${latestPlan.mealPlanId}` : "#"}
                    className={`rounded-lg p-6 transition ${latestPlan
                        ? "bg-blue-50 border border-blue-200 hover:shadow-lg cursor-pointer"
                        : "bg-slate-50 border border-slate-200 cursor-not-allowed opacity-50"
                        }`}
                >
                    <div className="text-3xl mb-2">📅</div>
                    <h3 className="font-semibold text-lg text-slate-900">View Current Plan</h3>
                    <p className="text-sm text-slate-600 mt-2">
                        {latestPlan
                            ? `Plan until ${new Date(latestPlan.endDate).toLocaleDateString()}`
                            : "No active meal plan"}
                    </p>
                </Link>

                {/* Log Meals */}
                <Link
                    to="/planner/today"
                    className="bg-orange-50 border border-orange-200 rounded-lg p-6 hover:shadow-lg transition cursor-pointer"
                >
                    <div className="text-3xl mb-2">🍽️</div>
                    <h3 className="font-semibold text-lg text-slate-900">Log Meals</h3>
                    <p className="text-sm text-slate-600 mt-2">
                        Track what you eat today and monitor nutrition
                    </p>
                </Link>
            </div>

            {/* Latest Plan Preview */}
            {latestPlan && (
                <div className="bg-white rounded-lg border border-slate-200 p-6">
                    <h2 className="text-lg font-semibold text-slate-900 mb-4">Current Meal Plan Preview</h2>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        {latestPlan.days.slice(0, 3).map((day, idx) => (
                            <div key={idx} className="bg-slate-50 rounded-lg p-4">
                                <p className="font-semibold text-slate-900 mb-3">
                                    Day {idx + 1} - {new Date(day.date).toLocaleDateString()}
                                </p>
                                <div className="space-y-2">
                                    {day.meals.map((meal, mIdx) => (
                                        <div key={mIdx} className="text-sm">
                                            <p className="font-medium text-slate-700 capitalize">{meal.mealType}</p>
                                            <p className="text-slate-600 text-xs">{meal.recipeTitle}</p>
                                            <p className="text-slate-500 text-xs">
                                                {meal.caloriesKcal ? Math.round(meal.caloriesKcal) : "?"} cal •{" "}
                                                {meal.proteinG ? Math.round(meal.proteinG) : "?"}g protein
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>

                    <Link
                        to={`/planner/meal-plan/${latestPlan.mealPlanId}`}
                        className="inline-block mt-4 text-green-600 hover:text-green-700 font-medium text-sm"
                    >
                        View full plan →
                    </Link>
                </div>
            )}

            {/* Info Section */}
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
                <h3 className="font-semibold text-slate-900 mb-2">How it Works</h3>
                <ol className="text-sm text-slate-700 space-y-2">
                    <li>
                        <strong>1. Generate:</strong> Set your preferences (calories, protein, dietary restrictions) and
                        AI generates a customized meal plan
                    </li>
                    <li>
                        <strong>2. Review:</strong> See the full plan with all meals and nutrition info
                    </li>
                    <li>
                        <strong>3. Log:</strong> Track what you actually eat throughout the day
                    </li>
                    <li>
                        <strong>4. Analyze:</strong> View trends and see how you're meeting your nutrition goals
                    </li>
                </ol>
            </div>
        </div>
    );
}
