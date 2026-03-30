import { useState } from "react";
import type { AIGenerationRequest } from "../../../shared/api/mealPlans";
import { Card } from "../../../shared/ui/Card";
import { Button } from "../../../shared/ui/Button";
import { Alert } from "../../../shared/ui/Alert";

export interface MealGenerationFormProps {
    onSubmit: (request: AIGenerationRequest) => Promise<void>;
    loading?: boolean;
    error?: string | null;
    showAdvanced?: boolean;
}

/**
 * Reusable meal generation form with proper validation and UX.
 * Supports basic and advanced mode for different use cases.
 *
 * Features:
 * - Macro calculation helper (suggests carbs/fats based on protein)
 * - Form validation with helpful hints
 * - Saves state to localStorage for persistence
 * - Shows realistic daily nutrition breakdown
 * - Progress tracking ready
 */
export default function MealGenerationForm({
    onSubmit,
    loading = false,
    error = null,
    showAdvanced = false,
}: MealGenerationFormProps) {
    // Store key
    const STORAGE_KEY = "ndl_meal_generation_form";

    // Load from localStorage or use defaults
    const loadSavedState = () => {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved) {
            try {
                return JSON.parse(saved);
            } catch {
                return getDefaults();
            }
        }
        return getDefaults();
    };

    const getDefaults = () => ({
        query: "healthy balanced meal plan",
        numberOfDays: 7,
        calorieTarget: 2000,
        proteinTargetG: 100,
        carbsTargetG: 250,
        fatsTargetG: 65,
        dietaryPreferences: [] as string[],
        cuisinePreferences: [] as string[],
        allergies: [] as string[],
    });

    const [query, setQuery] = useState(loadSavedState().query);
    const [numberOfDays, setNumberOfDays] = useState(loadSavedState().numberOfDays);
    const [calorieTarget, setCalorieTarget] = useState(loadSavedState().calorieTarget);
    const [proteinTargetG, setProteinTargetG] = useState(loadSavedState().proteinTargetG);
    const [carbsTargetG, setCarbsTargetG] = useState(loadSavedState().carbsTargetG);
    const [fatsTargetG, setFatsTargetG] = useState(loadSavedState().fatsTargetG);
    const [dietaryPreferences, setDietaryPreferences] = useState<string[]>(
        loadSavedState().dietaryPreferences
    );
    const [cuisinePreferences, setCuisinePreferences] = useState<string[]>(
        loadSavedState().cuisinePreferences
    );
    const [allergies, setAllergies] = useState<string[]>(loadSavedState().allergies);

    // Macro suggestions
    const commonDietaryPrefs = ["low-carb", "high-protein", "vegan", "vegetarian", "gluten-free", "keto"];
    const commonCuisines = ["mediterranean", "asian", "italian", "mexican", "indian", "thai"];
    const commonAllergies = ["peanuts", "shellfish", "dairy", "eggs", "soy", "tree nuts"];

    // Calculate macros from calories (common distribution)
    const suggestMacros = (calories: number, proteinG: number) => {
        const proteinCals = proteinG * 4;
        const remainingCals = calories - proteinCals;
        const carbCals = remainingCals * 0.5; // 50% carbs
        const fatCals = remainingCals * 0.5; // 50% fats
        return {
            carbs: Math.round(carbCals / 4),
            fats: Math.round(fatCals / 9),
        };
    };

    const handleAutoMacros = () => {
        const suggested = suggestMacros(calorieTarget, proteinTargetG);
        setCarbsTargetG(suggested.carbs);
        setFatsTargetG(suggested.fats);
    };

    const handleTogglePreference = (
        value: string,
        array: string[],
        setArray: (arr: string[]) => void
    ) => {
        if (array.includes(value)) {
            setArray(array.filter((item) => item !== value));
        } else {
            setArray([...array, value]);
        }
    };

    const handleReset = () => {
        const defaults = getDefaults();
        setQuery(defaults.query);
        setNumberOfDays(defaults.numberOfDays);
        setCalorieTarget(defaults.calorieTarget);
        setProteinTargetG(defaults.proteinTargetG);
        setCarbsTargetG(defaults.carbsTargetG);
        setFatsTargetG(defaults.fatsTargetG);
        setDietaryPreferences(defaults.dietaryPreferences);
        setCuisinePreferences(defaults.cuisinePreferences);
        setAllergies(defaults.allergies);
        localStorage.removeItem(STORAGE_KEY);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // Validate
        if (!query.trim()) {
            alert("Please describe your meal plan");
            return;
        }

        if (numberOfDays < 1 || numberOfDays > 30) {
            alert("Number of days must be between 1 and 30");
            return;
        }

        if (calorieTarget < 500 || calorieTarget > 5000) {
            alert("Daily calories should be between 500-5000");
            return;
        }

        // Save to localStorage
        localStorage.setItem(
            STORAGE_KEY,
            JSON.stringify({
                query,
                numberOfDays,
                calorieTarget,
                proteinTargetG,
                carbsTargetG,
                fatsTargetG,
                dietaryPreferences,
                cuisinePreferences,
                allergies,
            })
        );

        // Submit
        const request: AIGenerationRequest = {
            query,
            numberOfDays,
            calorieTarget,
            proteinTargetG,
            carbsTargetG,
            fatsTargetG,
            dietaryPreferences: dietaryPreferences.length > 0 ? dietaryPreferences : undefined,
            cuisinePreferences: cuisinePreferences.length > 0 ? cuisinePreferences : undefined,
            allergies: allergies.length > 0 ? allergies : undefined,
        };

        await onSubmit(request);
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-6">
            {/* Alerts */}
            {error && <Alert tone="error" title="Error" message={error} />}

            {/* Basic Section */}
            <Card>
                <div className="space-y-4">
                    {/* Query */}
                    <div>
                        <label className="block text-sm font-semibold text-slate-900 mb-2">
                            What would you like? <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="e.g., healthy high-protein Mediterranean meals"
                            className="w-full px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
                            disabled={loading}
                        />
                        <p className="text-xs text-slate-500 mt-1">
                            Describe your meal plan goal. The AI uses this to generate personalized recipes.
                        </p>
                    </div>

                    {/* Number of Days */}
                    <div>
                        <label className="block text-sm font-semibold text-slate-900 mb-2">
                            Number of Days <span className="text-red-500">*</span>
                        </label>
                        <div className="flex items-center gap-3">
                            <input
                                type="range"
                                min="1"
                                max="30"
                                value={numberOfDays}
                                onChange={(e) => setNumberOfDays(Number(e.target.value))}
                                className="flex-1"
                                disabled={loading}
                            />
                            <span className="w-12 text-center font-semibold text-slate-900">{numberOfDays}</span>
                        </div>
                        <p className="text-xs text-slate-500 mt-1">
                            Generates {numberOfDays === 1 ? "1 day" : `${numberOfDays} days`} of meals
                        </p>
                    </div>

                    {/* Nutrition Section */}
                    <div className="bg-slate-50 rounded-lg p-4 space-y-3 border border-slate-200">
                        <div className="flex justify-between items-center">
                            <h4 className="font-semibold text-slate-900">Daily Nutrition Targets</h4>
                            <button
                                type="button"
                                onClick={handleAutoMacros}
                                disabled={loading}
                                className="text-xs px-2 py-1 bg-slate-200 hover:bg-slate-300 rounded disabled:opacity-50"
                            >
                                Auto-Calculate
                            </button>
                        </div>

                        {/* Calories */}
                        <div>
                            <label className="block text-xs font-medium text-slate-700 mb-1">
                                Calories {calorieTarget} kcal
                            </label>
                            <input
                                type="range"
                                min="500"
                                max="5000"
                                step="100"
                                value={calorieTarget}
                                onChange={(e) => setCalorieTarget(Number(e.target.value))}
                                className="w-full"
                                disabled={loading}
                            />
                            <p className="text-xs text-slate-500 mt-1">
                                {calorieTarget < 1500 && "Low — suitable for weight loss"}
                                {calorieTarget >= 1500 && calorieTarget < 2000 && "Moderate — weight loss"}
                                {calorieTarget >= 2000 && calorieTarget < 2500 && "Standard — maintenance"}
                                {calorieTarget >= 2500 && "High — weight gain / muscle building"}
                            </p>
                        </div>

                        {/* Macros Grid */}
                        <div className="grid grid-cols-3 gap-2">
                            <div>
                                <label className="block text-xs font-medium text-slate-700 mb-1">
                                    Protein <span className="font-normal">{proteinTargetG}g</span>
                                </label>
                                <input
                                    type="number"
                                    value={proteinTargetG}
                                    onChange={(e) => setProteinTargetG(Number(e.target.value))}
                                    min="20"
                                    max="300"
                                    step="5"
                                    className="w-full px-2 py-1 border border-slate-300 rounded text-sm"
                                    disabled={loading}
                                />
                                <p className="text-xs text-slate-500 mt-1">{Math.round((proteinTargetG * 4) / (calorieTarget / 100))}% of cal</p>
                            </div>

                            <div>
                                <label className="block text-xs font-medium text-slate-700 mb-1">
                                    Carbs <span className="font-normal">{carbsTargetG}g</span>
                                </label>
                                <input
                                    type="number"
                                    value={carbsTargetG}
                                    onChange={(e) => setCarbsTargetG(Number(e.target.value))}
                                    min="50"
                                    max="500"
                                    step="5"
                                    className="w-full px-2 py-1 border border-slate-300 rounded text-sm"
                                    disabled={loading}
                                />
                                <p className="text-xs text-slate-500 mt-1">{Math.round((carbsTargetG * 4) / (calorieTarget / 100))}% of cal</p>
                            </div>

                            <div>
                                <label className="block text-xs font-medium text-slate-700 mb-1">
                                    Fats <span className="font-normal">{fatsTargetG}g</span>
                                </label>
                                <input
                                    type="number"
                                    value={fatsTargetG}
                                    onChange={(e) => setFatsTargetG(Number(e.target.value))}
                                    min="20"
                                    max="200"
                                    step="5"
                                    className="w-full px-2 py-1 border border-slate-300 rounded text-sm"
                                    disabled={loading}
                                />
                                <p className="text-xs text-slate-500 mt-1">{Math.round((fatsTargetG * 9) / (calorieTarget / 100))}% of cal</p>
                            </div>
                        </div>

                        <p className="text-xs text-slate-500 bg-blue-50 p-2 rounded border border-blue-200">
                            💡 <strong>Tip:</strong> P:G:F ratio ~1.2g:1.25g:0.325g is common. Click "Auto-Calculate" to
                            balance based on your protein goal.
                        </p>
                    </div>
                </div>
            </Card>

            {/* Advanced Section */}
            {showAdvanced && (
                <Card>
                    <div className="space-y-4">
                        {/* Dietary Preferences */}
                        <div>
                            <label className="block text-sm font-semibold text-slate-900 mb-2">
                                Dietary Preferences
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {commonDietaryPrefs.map((pref) => (
                                    <button
                                        key={pref}
                                        type="button"
                                        onClick={() =>
                                            handleTogglePreference(pref, dietaryPreferences, setDietaryPreferences)
                                        }
                                        disabled={loading}
                                        className={`px-3 py-1 rounded-full text-sm font-medium transition ${dietaryPreferences.includes(pref)
                                                ? "bg-green-500 text-white"
                                                : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                                            } disabled:opacity-50`}
                                    >
                                        {pref}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Cuisine Preferences */}
                        <div>
                            <label className="block text-sm font-semibold text-slate-900 mb-2">
                                Cuisine Preferences
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {commonCuisines.map((cuisine) => (
                                    <button
                                        key={cuisine}
                                        type="button"
                                        onClick={() =>
                                            handleTogglePreference(cuisine, cuisinePreferences, setCuisinePreferences)
                                        }
                                        disabled={loading}
                                        className={`px-3 py-1 rounded-full text-sm font-medium transition ${cuisinePreferences.includes(cuisine)
                                                ? "bg-orange-500 text-white"
                                                : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                                            } disabled:opacity-50`}
                                    >
                                        {cuisine}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Allergies */}
                        <div>
                            <label className="block text-sm font-semibold text-slate-900 mb-2">
                                Allergies & Intolerances
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {commonAllergies.map((allergy) => (
                                    <button
                                        key={allergy}
                                        type="button"
                                        onClick={() => handleTogglePreference(allergy, allergies, setAllergies)}
                                        disabled={loading}
                                        className={`px-3 py-1 rounded-full text-sm font-medium transition ${allergies.includes(allergy)
                                                ? "bg-red-500 text-white"
                                                : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                                            } disabled:opacity-50`}
                                    >
                                        {allergy}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                </Card>
            )}

            {/* Buttons */}
            <div className="flex gap-3">
                <Button
                    type="button"
                    onClick={handleReset}
                    disabled={loading}
                    variant="outline"
                    className="flex-1"
                >
                    Reset
                </Button>
                <Button type="submit" disabled={loading} className="flex-1">
                    {loading ? "Generating..." : "Generate Meal Plan"}
                </Button>
            </div>
        </form>
    );
}
