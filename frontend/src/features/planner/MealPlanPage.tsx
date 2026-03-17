import { useEffect, useState } from "react";
import { useAppAuth } from "../../shared/auth/AuthContext";
import { useAuthToken } from "../../shared/auth/useAuthToken";
import { generateMealPlan, getLatestMealPlan, getMealPlanById, type GeneratedMealPlan } from "../../shared/api/mealPlans";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Card, CardBody, CardTitle } from "../../shared/ui/Card";
import { Spinner } from "../../shared/ui/Spinner";
import { TextField } from "../../shared/ui/TextField";

type Duration = "daily" | "weekly";
const LAST_MEAL_PLAN_ID_KEY = "ndl_last_meal_plan_id";

export default function MealPlanPage() {
    const { isAuthenticated } = useAppAuth();
    const getToken = useAuthToken();
    const [duration, setDuration] = useState<Duration>("weekly");
    const [startDate, setStartDate] = useState<string>(new Date().toISOString().slice(0, 10));
    const [isGenerating, setIsGenerating] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [result, setResult] = useState<GeneratedMealPlan | null>(null);

    useEffect(() => {
        const loadLastPlan = async () => {
            if (!isAuthenticated) return;
            try {
                const token = await getToken();
                if (!token) return;
                // Prefer backend latest plan so restore works across devices/sessions.
                const latest = await getLatestMealPlan(token);
                if (latest) {
                    setResult(latest);
                    localStorage.setItem(LAST_MEAL_PLAN_ID_KEY, latest.mealPlanId);
                    return;
                }
                localStorage.removeItem(LAST_MEAL_PLAN_ID_KEY);
            } catch {
                // Fallback: use locally remembered id if latest endpoint has no data yet.
                const mealPlanId = localStorage.getItem(LAST_MEAL_PLAN_ID_KEY);
                if (!mealPlanId) return;
                try {
                    const token = await getToken();
                    if (!token) return;
                    const lastPlan = await getMealPlanById(mealPlanId, token);
                    if (lastPlan) {
                        setResult(lastPlan);
                    } else {
                        localStorage.removeItem(LAST_MEAL_PLAN_ID_KEY);
                    }
                } catch {
                    localStorage.removeItem(LAST_MEAL_PLAN_ID_KEY);
                }
            }
        };
        loadLastPlan();
    }, [getToken, isAuthenticated]);

    const onGenerate = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setIsGenerating(true);
        try {
            const token = await getToken();
            if (!token) throw new Error("Not authenticated");
            const generated = await generateMealPlan(
                {
                    duration,
                    start_date: startDate || undefined,
                },
                token
            );
            setResult(generated);
            localStorage.setItem(LAST_MEAL_PLAN_ID_KEY, generated.mealPlanId);
        } catch (e) {
            const details = e instanceof Error ? e.message : "";
            setError(
                details
                    ? `Could not generate meal plan right now. Please try again. (${details})`
                    : "Could not generate meal plan right now. Please try again."
            );
        } finally {
            setIsGenerating(false);
        }
    };

    return (
        <div className="space-y-4 pb-32 md:pb-4">
            <Card>
                <CardTitle>Meal Plan Generator</CardTitle>
                <CardBody>
                    {!isAuthenticated && <Alert tone="info" message="Please log in to generate meal plans." />}
                    {error && <Alert tone="error" title="Error" message={error} />}
                    <form onSubmit={onGenerate} className="grid grid-cols-1 md:grid-cols-3 gap-3">
                        <label className="flex flex-col gap-1 text-sm text-slate-700">
                            Duration
                            <select
                                className="border border-slate-300 rounded-md px-2 py-2"
                                value={duration}
                                onChange={(e) => setDuration(e.target.value as Duration)}
                            >
                                <option value="daily">Daily</option>
                                <option value="weekly">Weekly</option>
                            </select>
                        </label>
                        <TextField
                            label="Start date"
                            type="date"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                        />
                        <div className="flex items-end">
                            <Button type="submit" disabled={isGenerating || !isAuthenticated}>
                                {isGenerating ? "Generating..." : "Generate Meal Plan"}
                            </Button>
                        </div>
                    </form>
                </CardBody>
            </Card>

            {isGenerating && <Spinner label="Generating meal plan..." />}

            {result && !isGenerating && (
                <Card>
                    <CardTitle>
                        Plan Ready ({result.periodType}) - {result.totalMeals} meals
                    </CardTitle>
                    <CardBody>
                        <div className="space-y-4">
                            {result.days.map((day) => (
                                <div key={day.date} className="border border-slate-200 rounded-lg p-3">
                                    <h3 className="font-semibold text-slate-900 mb-2">{day.date}</h3>
                                    {day.meals.length === 0 ? (
                                        <p className="text-sm text-slate-600">No meals generated for this day.</p>
                                    ) : (
                                        <div className="space-y-2">
                                            {day.meals.map((meal) => (
                                                <div
                                                    key={meal.id}
                                                    className="border border-slate-100 rounded-md px-3 py-2 bg-slate-50"
                                                >
                                                    <div className="text-sm font-medium text-slate-900">
                                                        {meal.mealType} - {meal.recipeTitle}
                                                    </div>
                                                    <div className="text-xs text-slate-600">
                                                        {meal.cuisine ? `${meal.cuisine} • ` : ""}
                                                        {meal.timeMinutes != null ? `${meal.timeMinutes} min • ` : ""}
                                                        {meal.caloriesKcal != null ? `${meal.caloriesKcal} kcal` : "kcal n/a"}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </CardBody>
                </Card>
            )}
        </div>
    );
}
