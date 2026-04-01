import { useEffect, useState } from "react";
import { useAppAuth } from "../../../shared/auth/AuthContext";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { generateMealPlan, getLatestMealPlan, getMealPlanById, type GeneratedMealPlan } from "../../../shared/api/mealPlans";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { Card, CardBody, CardTitle } from "../../../shared/ui/Card";
import { Spinner } from "../../../shared/ui/Spinner";
import { TextField } from "../../../shared/ui/TextField";
import { MealManagementUI } from "./MealManagementUI";

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
    const [selectedDate, setSelectedDate] = useState<string>(new Date().toISOString().slice(0, 10));
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        const loadLastPlan = async () => {
            if (!isAuthenticated) return;
            try {
                const token = await getToken();
                if (!token) return;
                const latest = await getLatestMealPlan(token);
                if (latest) {
                    setResult(latest);
                    localStorage.setItem(LAST_MEAL_PLAN_ID_KEY, latest.mealPlanId);
                    return;
                }
                localStorage.removeItem(LAST_MEAL_PLAN_ID_KEY);
            } catch {
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

    // When plan changes, pick today if it's in the plan, otherwise pick the first day
    useEffect(() => {
        if (!result) return;
        const today = new Date().toISOString().slice(0, 10);
        const inPlan = result.days.some((d: any) => d.date === today);
        setSelectedDate(inPlan ? today : result.startDate);
    }, [result]);

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
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setStartDate(e.target.value)}
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
                <>
                    {/* Plan summary bar */}
                    <Card>
                        <div className="flex flex-wrap items-center gap-4 text-sm text-slate-600">
                            <span className="font-semibold text-slate-900 capitalize">{result.periodType} Plan</span>
                            <span>{result.startDate} – {result.endDate}</span>
                            <span>{result.totalMeals} meals</span>
                        </div>
                    </Card>

                    {/* Day selector tabs */}
                    <div className="flex flex-wrap gap-2">
                        {result.days.map((day: any) => (
                            <button
                                key={day.date}
                                onClick={() => { setSelectedDate(day.date); setRefreshKey(k => k + 1); }}
                                className={`px-3 py-1 rounded-full text-sm font-medium transition border ${selectedDate === day.date
                                        ? "bg-blue-600 text-white border-blue-600"
                                        : "bg-white text-slate-700 border-slate-300 hover:border-blue-400"
                                    }`}
                            >
                                {new Date(day.date + "T12:00:00").toLocaleDateString("en", {
                                    weekday: "short",
                                    month: "short",
                                    day: "numeric",
                                })}
                            </button>
                        ))}
                    </div>

                    {/* Meal management for the selected day */}
                    <MealManagementUI
                        key={`${refreshKey}-${selectedDate}`}
                        date={selectedDate}
                        onMealsUpdated={() => setRefreshKey(k => k + 1)}
                    />
                </>
            )}
        </div>
    );
}
