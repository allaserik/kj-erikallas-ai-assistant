import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { api } from "../../../shared/api/client";
import type { ApiResponse } from "../../../shared/types";

/**
 * SimpleMealGeneratorPage: Simple form to generate AI meal plans
 * Takes user preferences and calls the generate endpoint
 */
export default function SimpleMealGeneratorPage() {
    const navigate = useNavigate();
    const getToken = useAuthToken();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Form fields
    const [daysToGenerate, setDaysToGenerate] = useState(7);
    const [targetCalories, setTargetCalories] = useState(2000);
    const [targetProtein, setTargetProtein] = useState(150);
    const [dietaryRestrictions, setDietaryRestrictions] = useState("");

    const handleGenerate = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        try {
            const token = await getToken();
            if (!token) {
                throw new Error("Not authenticated");
            }

            // Parse dietary restrictions into array
            const dietaryPreferences = dietaryRestrictions
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean);

            // Call backend directly
            const result = await api.post<ApiResponse<any>>(
                "/api/meal-plans/ai/generate-and-save",
                {
                    query: `Generate a ${daysToGenerate}-day meal plan with healthy recipes`,
                    numberOfDays: daysToGenerate,
                    calorieTarget: targetCalories,
                    proteinTargetG: targetProtein,
                    dietaryPreferences: dietaryPreferences.length > 0 ? dietaryPreferences : undefined,
                    cuisinePreferences: undefined,
                    allergies: undefined,
                },
                token
            );

            const mealPlan = result.data || result;

            // Navigate to the generated plan
            navigate(`/planner/meal-plan/${mealPlan.mealPlanId}`, {
                state: { newlyGenerated: true },
            });
        } catch (err) {
            setError(err instanceof Error ? err.message : "An error occurred");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto">
            {/* Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-slate-900">Generate Your Meal Plan</h1>
                <p className="text-slate-600 mt-2">
                    Tell us your preferences and AI will create a personalized meal plan for you
                </p>
            </div>

            {/* Form */}
            <form onSubmit={handleGenerate} className="space-y-6 bg-white rounded-lg border border-slate-200 p-6">
                {/* Number of Days */}
                <div>
                    <label className="block text-sm font-semibold text-slate-900 mb-2">
                        How many days? {daysToGenerate}
                    </label>
                    <input
                        type="range"
                        min="1"
                        max="30"
                        value={daysToGenerate}
                        onChange={(e) => setDaysToGenerate(Number(e.target.value))}
                        className="w-full"
                    />
                    <p className="text-xs text-slate-500 mt-1">We'll generate {daysToGenerate} days of meals</p>
                </div>

                {/* Target Calories */}
                <div>
                    <label htmlFor="calories" className="block text-sm font-semibold text-slate-900 mb-2">
                        Target Calories Per Day
                    </label>
                    <div className="flex gap-2">
                        <input
                            id="calories"
                            type="number"
                            value={targetCalories}
                            onChange={(e) => setTargetCalories(Number(e.target.value))}
                            className="flex-1 px-3 py-2 border border-slate-300 rounded-lg"
                            placeholder="e.g., 2000"
                            min="1000"
                            max="5000"
                            step="100"
                        />
                        <span className="text-slate-600 py-2">kcal</span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">
                        Not sure? 2000 is a common baseline. Adjust based on your goals.
                    </p>
                </div>

                {/* Target Protein */}
                <div>
                    <label htmlFor="protein" className="block text-sm font-semibold text-slate-900 mb-2">
                        Target Protein Per Day
                    </label>
                    <div className="flex gap-2">
                        <input
                            id="protein"
                            type="number"
                            value={targetProtein}
                            onChange={(e) => setTargetProtein(Number(e.target.value))}
                            className="flex-1 px-3 py-2 border border-slate-300 rounded-lg"
                            placeholder="e.g., 150"
                            min="0"
                            max="300"
                            step="10"
                        />
                        <span className="text-slate-600 py-2">g</span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1">
                        {`${(targetProtein / targetCalories * 100).toFixed(1)}% of calories from protein`}
                    </p>
                </div>

                {/* Dietary Restrictions */}
                <div>
                    <label htmlFor="restrictions" className="block text-sm font-semibold text-slate-900 mb-2">
                        Dietary Restrictions (Optional)
                    </label>
                    <input
                        id="restrictions"
                        type="text"
                        value={dietaryRestrictions}
                        onChange={(e) => setDietaryRestrictions(e.target.value)}
                        className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                        placeholder="e.g., vegetarian, gluten-free, dairy-free"
                    />
                    <p className="text-xs text-slate-500 mt-1">
                        Separate multiple restrictions with commas
                    </p>
                </div>

                {/* Error */}
                {error && (
                    <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
                        {error}
                    </div>
                )}

                {/* Buttons */}
                <div className="flex gap-3 pt-4">
                    <button
                        type="button"
                        onClick={() => navigate("/planner")}
                        className="flex-1 px-4 py-2 border border-slate-300 rounded-lg text-slate-700 hover:bg-slate-50 font-medium"
                    >
                        Cancel
                    </button>
                    <button
                        type="submit"
                        disabled={loading}
                        className="flex-1 px-4 py-2 bg-green-600 rounded-lg text-white hover:bg-green-700 disabled:opacity-50 font-medium"
                    >
                        {loading ? "Generating..." : "Generate Plan"}
                    </button>
                </div>
            </form>

            {/* Info Section */}
            <div className="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-6">
                <h3 className="font-semibold text-slate-900 mb-3">How We Generate Plans</h3>
                <ul className="text-sm text-slate-700 space-y-2">
                    <li>
                        <strong>📊 AI Orchestration:</strong> We use Claude to understand your preferences and
                        requirements
                    </li>
                    <li>
                        <strong>🔍 Smart Recipe Search:</strong> Semantic search finds recipes matching your nutrition
                        goals
                    </li>
                    <li>
                        <strong>⚖️ Nutrition Scoring:</strong> Every recipe is scored for accuracy (calories, protein,
                        compatibility)
                    </li>
                    <li>
                        <strong>🍽️ Variety Assured:</strong> AI selects diverse meals to avoid repetition throughout
                        your plan
                    </li>
                </ul>
            </div>
        </div>
    );
}
