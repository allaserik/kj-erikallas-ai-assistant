import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { api } from "../../../shared/api/client";
import type { ApiResponse } from "../../../shared/types";
import type { AIGenerationRequest } from "../../../shared/api/mealPlans";
import MealGenerationForm from "../components/MealGenerationForm";

/**
 * SimpleMealGeneratorPage: Generate AI meal plans with improved form
 * Uses the reusable MealGenerationForm component with all features:
 * - Macro calculator
 * - Form persistence
 * - Advanced preferences
 * - Better UX/validation
 */
export default function SimpleMealGeneratorPage() {
    const navigate = useNavigate();
    const getToken = useAuthToken();

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (request: AIGenerationRequest) => {
        setLoading(true);
        setError(null);

        try {
            const token = await getToken();
            if (!token) {
                throw new Error("Not authenticated");
            }

            // Call backend to generate and save meal plan
            const result = await api.post<ApiResponse<any>>(
                "/api/meal-plans/ai/generate-and-save",
                request,
                token
            );

            const mealPlan = result.data || result;

            // Navigate to the generated plan with success state
            navigate(`/nutrition/plan/${mealPlan.mealPlanId}`, {
                state: { newlyGenerated: true },
            });
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to generate meal plan");
            setLoading(false);
        }
    };

    return (
        <div className="max-w-3xl mx-auto">
            {/* Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-slate-900">Generate Your Meal Plan</h1>
                <p className="text-slate-600 mt-2">
                    Tell us your preferences and our AI will create a personalized meal plan for you
                </p>
            </div>

            {/* Form Component */}
            <div className="bg-white rounded-lg border border-slate-200 p-6 mb-8">
                <MealGenerationForm
                    onSubmit={handleSubmit}
                    loading={loading}
                    error={error}
                    showAdvanced={true}
                />
            </div>

            {/* Info Section */}
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
                <h3 className="font-semibold text-slate-900 mb-3">✨ How We Generate Plans</h3>
                <ul className="text-sm text-slate-700 space-y-2">
                    <li>
                        <strong>🎯 AI Orchestration:</strong> AI understands your preferences and nutrition goals
                    </li>
                    <li>
                        <strong>🔍 Smart Recipe Search:</strong> Semantic search finds recipes matching your criteria
                    </li>
                    <li>
                        <strong>⚖️ Nutrition Scoring:</strong> Every recipe is validated for nutrition accuracy
                    </li>
                    <li>
                        <strong>🍽️ Variety Assured:</strong> AI selects diverse meals to avoid repetition
                    </li>
                    <li>
                        <strong>💾 Saved & Ready:</strong> Your meal plan is automatically saved and ready to use
                    </li>
                </ul>
            </div>
        </div>
    );
}
