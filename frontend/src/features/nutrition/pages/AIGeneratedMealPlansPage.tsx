import { useState, useEffect } from "react";
import { useAppAuth } from "../../../shared/auth/AuthContext";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import {
    generateAIMealPlan,
    searchRecipesByQuery,
    type AIGenerationRequest,
    type GeneratedMealPlan,
    type RecipeSearchResult,
} from "../../../shared/api/mealPlans";

export default function AIGeneratedMealPlansPage() {
    const { isAuthenticated } = useAppAuth();
    const getToken = useAuthToken();
    const [token, setToken] = useState<string | null>(null);

    // Get token on mount
    useEffect(() => {
        getToken().then(setToken);
    }, [getToken]);

    // Form state
    const [query, setQuery] = useState("healthy high-protein meals");
    const [numberOfDays, setNumberOfDays] = useState(3);
    const [calorieTarget, setCalorieTarget] = useState(2000);
    const [proteinTarget, setProteinTarget] = useState(150);
    const [dietaryPreferences, setDietaryPreferences] = useState<string[]>([]);
    const [cuisinePreferences, setCuisinePreferences] = useState<string[]>([]);
    const [allergies, setAllergies] = useState<string[]>([]);

    // UI state
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [generatedPlan, setGeneratedPlan] = useState<GeneratedMealPlan | null>(null);
    const [searchResults, setSearchResults] = useState<RecipeSearchResult[]>([]);
    const [searchLoading, setSearchLoading] = useState(false);

    const handleGeneratePlan = async () => {
        if (!token) return;
        setLoading(true);
        setError(null);
        try {
            const request: AIGenerationRequest = {
                query,
                numberOfDays,
                calorieTarget,
                proteinTargetG: proteinTarget,
                dietaryPreferences: dietaryPreferences.length > 0 ? dietaryPreferences : undefined,
                cuisinePreferences: cuisinePreferences.length > 0 ? cuisinePreferences : undefined,
                allergies: allergies.length > 0 ? allergies : undefined,
            };
            const plan = await generateAIMealPlan(request, token);
            setGeneratedPlan(plan);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to generate meal plan");
        } finally {
            setLoading(false);
        }
    };

    const handleSearchRecipes = async () => {
        if (!token) return;
        setSearchLoading(true);
        try {
            const results = await searchRecipesByQuery(query, 10, token);
            setSearchResults(results);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to search recipes");
        } finally {
            setSearchLoading(false);
        }
    };

    const togglePreference = (value: string, array: string[], setArray: (arr: string[]) => void) => {
        if (array.includes(value)) {
            setArray(array.filter((p) => p !== value));
        } else {
            setArray([...array, value]);
        }
    };

    const commonDietaryPrefs = ["low-carb", "high-protein", "vegan", "vegetarian", "gluten-free"];
    const commonCuisines = ["mediterranean", "asian", "italian", "mexican", "indian"];
    const commonAllergies = ["peanuts", "shellfish", "dairy", "eggs", "soy"];

    if (!isAuthenticated || !token) {
        return <div className="text-center text-slate-600">Please log in to use AI meal generation.</div>;
    }

    return (
        <div className="space-y-6">
            {/* Generation Forms */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Main Generation Panel */}
                <div className="bg-white rounded-lg border border-slate-200 p-6 space-y-4">
                    <h2 className="text-lg font-semibold text-slate-900">Generate Meal Plan</h2>

                    {/* Query */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">What would you like?</label>
                        <input
                            type="text"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="e.g., healthy high-protein breakfast plan"
                            className="w-full px-3 py-2 border border-slate-300 rounded-md text-sm focus:ring-2 focus:ring-green-500 focus:border-transparent"
                        />
                    </div>

                    {/* Number of Days */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Number of Days</label>
                        <input
                            type="number"
                            min="1"
                            max="14"
                            value={numberOfDays}
                            onChange={(e) => setNumberOfDays(parseInt(e.target.value))}
                            className="w-full px-3 py-2 border border-slate-300 rounded-md text-sm focus:ring-2 focus:ring-green-500 focus:border-transparent"
                        />
                    </div>

                    {/* Nutrition Targets */}
                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">
                                Calorie Target
                            </label>
                            <input
                                type="number"
                                value={calorieTarget}
                                onChange={(e) => setCalorieTarget(parseInt(e.target.value))}
                                className="w-full px-3 py-2 border border-slate-300 rounded-md text-sm focus:ring-2 focus:ring-green-500 focus:border-transparent"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">
                                Protein Target (g)
                            </label>
                            <input
                                type="number"
                                value={proteinTarget}
                                onChange={(e) => setProteinTarget(parseInt(e.target.value))}
                                className="w-full px-3 py-2 border border-slate-300 rounded-md text-sm focus:ring-2 focus:ring-green-500 focus:border-transparent"
                            />
                        </div>
                    </div>

                    {/* Generate Button */}
                    <button
                        onClick={handleGeneratePlan}
                        disabled={loading || !query.trim()}
                        className="w-full bg-green-600 text-white py-2 rounded-md hover:bg-green-700 disabled:opacity-50 font-medium text-sm"
                    >
                        {loading ? "Generating..." : "Generate Meal Plan"}
                    </button>

                    {error && <div className="text-sm text-red-600 bg-red-50 p-2 rounded">{error}</div>}
                </div>

                {/* Preferences Panel */}
                <div className="bg-white rounded-lg border border-slate-200 p-6 space-y-4">
                    <h2 className="text-lg font-semibold text-slate-900">Preferences</h2>

                    {/* Dietary Preferences */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-2">
                            Dietary Preferences
                        </label>
                        <div className="grid grid-cols-2 gap-2">
                            {commonDietaryPrefs.map((pref) => (
                                <label key={pref} className="flex items-center cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={dietaryPreferences.includes(pref)}
                                        onChange={() =>
                                            togglePreference(pref, dietaryPreferences, setDietaryPreferences)
                                        }
                                        className="w-4 h-4 text-green-600 border-slate-300 rounded"
                                    />
                                    <span className="ml-2 text-sm text-slate-700 capitalize">{pref}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Cuisine Preferences */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-2">
                            Cuisine Preferences
                        </label>
                        <div className="grid grid-cols-2 gap-2">
                            {commonCuisines.map((cuisine) => (
                                <label key={cuisine} className="flex items-center cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={cuisinePreferences.includes(cuisine)}
                                        onChange={() =>
                                            togglePreference(cuisine, cuisinePreferences, setCuisinePreferences)
                                        }
                                        className="w-4 h-4 text-green-600 border-slate-300 rounded"
                                    />
                                    <span className="ml-2 text-sm text-slate-700 capitalize">{cuisine}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Allergies */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-2">Allergies</label>
                        <div className="grid grid-cols-2 gap-2">
                            {commonAllergies.map((allergy) => (
                                <label key={allergy} className="flex items-center cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={allergies.includes(allergy)}
                                        onChange={() => togglePreference(allergy, allergies, setAllergies)}
                                        className="w-4 h-4 text-green-600 border-slate-300 rounded"
                                    />
                                    <span className="ml-2 text-sm text-slate-700 capitalize">{allergy}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Search Recipes Button */}
                    <button
                        onClick={handleSearchRecipes}
                        disabled={searchLoading || !query.trim()}
                        className="w-full bg-slate-600 text-white py-2 rounded-md hover:bg-slate-700 disabled:opacity-50 font-medium text-sm"
                    >
                        {searchLoading ? "Searching..." : "Search Recipes"}
                    </button>
                </div>
            </div>

            {/* Search Results */}
            {searchResults.length > 0 && (
                <div className="bg-white rounded-lg border border-slate-200 p-6">
                    <h3 className="text-lg font-semibold text-slate-900 mb-4">Recipe Search Results ({searchResults.length})</h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {searchResults.map((recipe) => (
                            <div key={recipe.id} className="border border-slate-200 rounded-lg p-4">
                                <h4 className="font-semibold text-slate-900 text-sm">{recipe.title}</h4>
                                <p className="text-xs text-slate-500 mt-1 capitalize">
                                    {recipe.mealType} • {recipe.cuisine || "Any"}
                                </p>
                                <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                                    <div className="bg-slate-50 p-2 rounded">
                                        <p className="text-slate-600">Calories</p>
                                        <p className="font-semibold">{Math.round(recipe.caloriesKcal)}</p>
                                    </div>
                                    <div className="bg-slate-50 p-2 rounded">
                                        <p className="text-slate-600">Protein</p>
                                        <p className="font-semibold">{Math.round(recipe.proteinG)}g</p>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Generated Meal Plan */}
            {generatedPlan && (
                <div className="bg-white rounded-lg border border-slate-200 p-6">
                    <div className="flex justify-between items-start mb-4">
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900">Generated Meal Plan</h3>
                            <p className="text-sm text-slate-600 mt-1">
                                {generatedPlan.startDate} to {generatedPlan.endDate} • {generatedPlan.totalMeals} meals
                            </p>
                        </div>
                        <span className="bg-green-100 text-green-800 px-3 py-1 rounded-full text-sm font-medium">
                            Saved ✓
                        </span>
                    </div>

                    {/* Daily Breakdown */}
                    <div className="space-y-4">
                        {generatedPlan.days.map((day, idx) => (
                            <div key={idx} className="border border-slate-200 rounded-lg p-4">
                                <h4 className="font-semibold text-slate-900 mb-3">Day {idx + 1} - {day.date}</h4>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    {day.meals.map((meal, mIdx) => (
                                        <div key={mIdx} className="bg-slate-50 rounded p-3">
                                            <p className="text-sm font-semibold text-slate-900 capitalize">{meal.mealType}</p>
                                            <p className="text-sm text-slate-700 mt-1">{meal.recipeTitle}</p>
                                            <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
                                                <div>
                                                    <p className="text-slate-600">Cal</p>
                                                    <p className="font-semibold text-slate-900">
                                                        {meal.caloriesKcal ? Math.round(meal.caloriesKcal) : "N/A"}
                                                    </p>
                                                </div>
                                                <div>
                                                    <p className="text-slate-600">Protein</p>
                                                    <p className="font-semibold text-slate-900">
                                                        {meal.proteinG ? Math.round(meal.proteinG) : "N/A"}g
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
