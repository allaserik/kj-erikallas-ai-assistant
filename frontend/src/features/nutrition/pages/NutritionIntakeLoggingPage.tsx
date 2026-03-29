import { useState } from 'react';
import { useAuthedQuery } from '../../../shared/auth/useAuthedQuery';
import { getDailySummary } from '../../../shared/api/nutrition';
import { NutritionSummaryCard } from '../../dashboard/components/NutritionSummaryCard';
import { MealLogCheckin } from '../../training/components/MealLogCheckin';
import { Spinner } from '../../../shared/ui/Spinner';
import { Alert } from '../../../shared/ui/Alert';

/**
 * NutritionIntakeLoggingPage
 * 
 * Dedicated page for logging meals and tracking daily nutrition intake.
 * Features:
 * - Today's nutrition summary with macros progress
 * - Meal logging form (from recipes or custom)
 * - Today's logged meals with delete option
 * - Color-coded progress indicators (surplus/deficit)
 */
export default function NutritionIntakeLoggingPage() {
    const [reloadKey, setReloadKey] = useState(0);

    // Fetch today's nutrition summary
    const summaryQ = useAuthedQuery(
        `nutrition-summary-${reloadKey}`,
        (token: string) => getDailySummary(token),
        true
    );

    return (
        <div className="space-y-6 pb-32 md:pb-4">
            {/* Header */}
            <div>
                <h1 className="text-2xl font-bold text-slate-900">Nutrition Intake</h1>
                <p className="text-slate-600">Log your meals and track your daily nutrition goals.</p>
            </div>

            {/* Today's Nutrition Summary */}
            {summaryQ.loading && (
                <div className="flex justify-center py-8">
                    <Spinner label="Loading nutrition summary..." />
                </div>
            )}

            {summaryQ.error && (
                <Alert
                    tone="error"
                    title="Failed to Load Summary"
                    message={summaryQ.error.message || "Unable to load today's nutrition summary"}
                />
            )}

            {summaryQ.data && (
                <NutritionSummaryCard summary={summaryQ.data} />
            )}

            {/* Meal Logging Form */}
            <MealLogCheckin
                reloadKey={reloadKey}
                onReload={() => setReloadKey((v) => v + 1)}
            />
        </div>
    );
}
