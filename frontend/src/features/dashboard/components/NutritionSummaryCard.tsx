import type { DailySummary } from "../../../shared/types";

/**
 * Displays daily nutrition summary with macro progress cards.
 * 
 * Shows:
 * - Calories consumed vs target
 * - Protein consumed vs target
 * - Carbs consumed vs target
 * - Fats consumed vs target
 * - AI-generated summary text
 * 
 * Color coding:
 * - "good" (95-105%): Green - on track
 * - "low" (<95%): Orange - under target
 * - "high" (>105%): Red/Pink - over target
 */
export function NutritionSummaryCard({ summary }: { summary: DailySummary }) {
    const getStatusColor = (status: string): string => {
        switch (status) {
            case "good":
                return "bg-green-100 text-green-800 border-green-300";
            case "low":
                return "bg-orange-100 text-orange-800 border-orange-300";
            case "high":
                return "bg-red-100 text-red-800 border-red-300";
            default:
                return "bg-gray-100 text-gray-800 border-gray-300";
        }
    };

    const getProgressBarColor = (status: string): string => {
        switch (status) {
            case "good":
                return "bg-green-500";
            case "low":
                return "bg-orange-500";
            case "high":
                return "bg-red-500";
            default:
                return "bg-gray-500";
        }
    };

    return (
        <div className="bg-white rounded-lg border border-gray-200 p-6 shadow-sm">
            {/* Header */}
            <div className="mb-6 flex items-center justify-between">
                <div>
                    <h2 className="text-xl font-semibold text-gray-900">Daily Nutrition</h2>
                    <p className="text-sm text-gray-600 mt-1">{summary.totalMeals} meals logged</p>
                </div>
                <span className="text-xs font-medium text-slate-500 bg-slate-100 px-2 py-1 rounded">
                    {summary.source === "ai" ? "AI Generated" : "Rule-Based"}
                </span>
            </div>

            {/* Summary Text */}
            <p className="text-sm text-gray-700 mb-6 p-3 bg-gray-50 rounded italic">
                {summary.summary}
            </p>

            {/* Macro Cards Grid */}
            <div className="grid grid-cols-2 gap-4 mb-6">
                {[summary.calories, summary.protein, summary.carbs, summary.fats].map((nutrient) => (
                    <div
                        key={nutrient.name}
                        className={`rounded-lg border p-4 ${getStatusColor(nutrient.status)}`}
                    >
                        {/* Title and Percentage */}
                        <div className="flex justify-between items-start mb-2">
                            <h3 className="font-semibold text-sm">{nutrient.name}</h3>
                            <span className="text-xs font-bold">{nutrient.percentageOfTarget}%</span>
                        </div>

                        {/* Actual vs Target */}
                        <p className="text-xs mb-3">
                            {nutrient.actual.toFixed(1)} / {nutrient.target.toFixed(1)} {nutrient.unit}
                        </p>

                        {/* Progress Bar */}
                        <div className="w-full bg-gray-300 rounded-full h-2 overflow-hidden">
                            <div
                                className={`h-full ${getProgressBarColor(nutrient.status)}`}
                                style={{
                                    width: `${Math.min(nutrient.percentageOfTarget, 100)}%`,
                                    transition: "width 0.3s ease",
                                }}
                            />
                        </div>

                        {/* Surplus/Deficit */}
                        <p className="text-xs mt-2">
                            {nutrient.surplus > 0 ? "+" : ""}
                            {nutrient.surplus.toFixed(1)} {nutrient.unit}
                        </p>
                    </div>
                ))}
            </div>

            {/* No Meals Fallback */}
            {summary.totalMeals === 0 && (
                <div className="text-center py-8">
                    <p className="text-gray-500 text-sm">No meals logged yet today</p>
                    <p className="text-xs text-gray-400 mt-1">Generate a meal plan to get started</p>
                </div>
            )}
        </div>
    );
}
