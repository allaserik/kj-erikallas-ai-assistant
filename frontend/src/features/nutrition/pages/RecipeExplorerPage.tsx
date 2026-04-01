import { useEffect, useMemo, useState } from "react";
import { useAppAuth } from "../../../shared/auth/AuthContext";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { getNutritionOptions } from "../../../shared/api/nutritionPreferences";
import { getRecipeById, searchRecipes, type RecipeDetail, type RecipeListItem } from "../../../shared/api/recipes";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { Card, CardBody, CardTitle } from "../../../shared/ui/Card";
import { Spinner } from "../../../shared/ui/Spinner";
import { TextField } from "../../../shared/ui/TextField";
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from "recharts";

type Filters = {
    q: string;
    cuisine: string;
    meal: string;
    dietaryTag: string;
    allergyTag: string;
    maxTimeMinutes: string;
    maxCaloriesKcal: string;
    minProteinG: string;
};

const defaultFilters: Filters = {
    q: "",
    cuisine: "",
    meal: "",
    dietaryTag: "",
    allergyTag: "",
    maxTimeMinutes: "",
    maxCaloriesKcal: "",
    minProteinG: "",
};

export default function RecipeExplorerPage() {
    const { isAuthenticated } = useAppAuth();
    const getToken = useAuthToken();
    const [filters, setFilters] = useState<Filters>(defaultFilters);
    const [recipes, setRecipes] = useState<RecipeListItem[]>([]);
    const [selectedRecipe, setSelectedRecipe] = useState<RecipeDetail | null>(null);
    const [selectedRecipeId, setSelectedRecipeId] = useState<string | null>(null);
    const [dietaryOptions, setDietaryOptions] = useState<{ code: string; label: string }[]>([]);
    const [allergyOptions, setAllergyOptions] = useState<{ code: string; label: string }[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [isLoadingDetail, setIsLoadingDetail] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const hasResults = recipes.length > 0;

    const runSearch = async () => {
        if (!isAuthenticated) return;
        setError(null);
        setIsLoading(true);
        try {
            const token = await getToken();
            if (!token) throw new Error("Not authenticated");
            const data = await searchRecipes(
                {
                    q: filters.q || undefined,
                    cuisine: filters.cuisine || undefined,
                    meal: filters.meal || undefined,
                    dietaryTag: filters.dietaryTag || undefined,
                    allergyTag: filters.allergyTag || undefined,
                    maxTimeMinutes: filters.maxTimeMinutes ? Number(filters.maxTimeMinutes) : undefined,
                    maxCaloriesKcal: filters.maxCaloriesKcal ? Number(filters.maxCaloriesKcal) : undefined,
                    minProteinG: filters.minProteinG ? Number(filters.minProteinG) : undefined,
                    limit: 25,
                },
                token
            );
            setRecipes(data);
        } catch (e) {
            const details = e instanceof Error ? e.message : "";
            setError(
                details
                    ? `Could not load recipes right now. Please try again in a moment. (${details})`
                    : "Could not load recipes right now. Please try again in a moment."
            );
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        const init = async () => {
            if (!isAuthenticated) return;
            try {
                const token = await getToken();
                if (!token) throw new Error("Not authenticated");
                const options = await getNutritionOptions(token);
                setDietaryOptions(options.dietaryPreferences || []);
                setAllergyOptions(options.allergies || []);
            } catch {
                setDietaryOptions([]);
            } finally {
                runSearch();
            }
        };
        init();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isAuthenticated]);

    useEffect(() => {
        const loadDetail = async () => {
            if (!selectedRecipeId || !isAuthenticated) {
                setSelectedRecipe(null);
                return;
            }
            setIsLoadingDetail(true);
            setError(null);
            try {
                const token = await getToken();
                if (!token) throw new Error("Not authenticated");
                const detail = await getRecipeById(selectedRecipeId, token);
                setSelectedRecipe(detail);
            } catch (e) {
                const details = e instanceof Error ? e.message : "";
                setError(
                    details
                        ? `Could not load recipe details right now. Please pick another recipe or try again. (${details})`
                        : "Could not load recipe details right now. Please pick another recipe or try again."
                );
            } finally {
                setIsLoadingDetail(false);
            }
        };
        loadDetail();
    }, [getToken, isAuthenticated, selectedRecipeId]);

    const onSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        runSearch();
    };

    const mealOptions = useMemo(() => ["breakfast", "lunch", "dinner", "snack"], []);

    return (
        <div className="space-y-4">
            <Card>
                <CardTitle>Recipe Explorer</CardTitle>
                <CardBody>
                    {!isAuthenticated && <Alert tone="info" message="Please log in to explore recipes." />}
                    {error && <Alert tone="error" title="Error" message={error} />}
                    <form onSubmit={onSubmit} className="grid grid-cols-1 md:grid-cols-3 gap-3">
                        <TextField
                            label="Search"
                            value={filters.q}
                            onChange={(e) => setFilters((prev) => ({ ...prev, q: e.target.value }))}
                            placeholder="name, ingredient, cuisine"
                        />
                        <TextField
                            label="Cuisine"
                            value={filters.cuisine}
                            onChange={(e) => setFilters((prev) => ({ ...prev, cuisine: e.target.value }))}
                            placeholder="Italian"
                        />
                        <label className="flex flex-col gap-1 text-sm text-slate-700">
                            Meal type
                            <select
                                className="border border-slate-300 rounded-md px-2 py-2"
                                value={filters.meal}
                                onChange={(e) => setFilters((prev) => ({ ...prev, meal: e.target.value }))}
                            >
                                <option value="">Any</option>
                                {mealOptions.map((meal) => (
                                    <option key={meal} value={meal}>
                                        {meal}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="flex flex-col gap-1 text-sm text-slate-700">
                            Dietary tag
                            <select
                                className="border border-slate-300 rounded-md px-2 py-2"
                                value={filters.dietaryTag}
                                onChange={(e) => setFilters((prev) => ({ ...prev, dietaryTag: e.target.value }))}
                            >
                                <option value="">Any</option>
                                {dietaryOptions.map((tag) => (
                                    <option key={tag.code} value={tag.code}>
                                        {tag.label}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <label className="flex flex-col gap-1 text-sm text-slate-700">
                            Allergy / Intolerance
                            <select
                                className="border border-slate-300 rounded-md px-2 py-2"
                                value={filters.allergyTag}
                                onChange={(e) => setFilters((prev) => ({ ...prev, allergyTag: e.target.value }))}
                            >
                                <option value="">None</option>
                                {allergyOptions.map((allergy) => (
                                    <option key={allergy.code} value={allergy.code}>
                                        {allergy.label}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <TextField
                            label="Max prep time (minutes)"
                            type="number"
                            value={filters.maxTimeMinutes}
                            onChange={(e) => setFilters((prev) => ({ ...prev, maxTimeMinutes: e.target.value }))}
                            placeholder="30"
                        />
                        <TextField
                            label="Max calories (kcal)"
                            type="number"
                            value={filters.maxCaloriesKcal}
                            onChange={(e) => setFilters((prev) => ({ ...prev, maxCaloriesKcal: e.target.value }))}
                            placeholder="700"
                        />
                        <TextField
                            label="Min protein (g)"
                            type="number"
                            value={filters.minProteinG}
                            onChange={(e) => setFilters((prev) => ({ ...prev, minProteinG: e.target.value }))}
                            placeholder="20"
                        />
                        <div className="md:col-span-3">
                            <Button type="submit" disabled={isLoading || !isAuthenticated}>
                                {isLoading ? "Searching..." : "Search Recipes"}
                            </Button>
                        </div>
                    </form>
                </CardBody>
            </Card>

            {isLoading && <Spinner label="Searching recipes..." />}

            {!isLoading && isAuthenticated && !hasResults && (
                <Alert tone="info" message="No recipes found for the current filters." />
            )}

            {!isLoading && hasResults && (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <Card>
                        <CardTitle>Results ({recipes.length})</CardTitle>
                        <CardBody>
                            <div className="space-y-2">
                                {recipes.map((recipe) => (
                                    <button
                                        key={recipe.id}
                                        type="button"
                                        onClick={() => setSelectedRecipeId(recipe.id)}
                                        className={`w-full text-left border rounded-lg p-3 transition ${selectedRecipeId === recipe.id
                                            ? "border-green-500 bg-green-50"
                                            : "border-slate-200 hover:bg-slate-50"
                                            }`}
                                    >
                                        <div className="font-medium text-slate-900">{recipe.title}</div>
                                        <div className="text-sm text-slate-600">
                                            {recipe.meal} • {recipe.timeMinutes} min • {recipe.nutrition.caloriesKcal} kcal
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </CardBody>
                    </Card>

                    <Card>
                        <CardTitle>Recipe Detail</CardTitle>
                        <CardBody>
                            {isLoadingDetail && <Spinner label="Loading recipe detail..." />}
                            {!isLoadingDetail && !selectedRecipe && (
                                <p className="text-sm text-slate-600">Select a recipe to see ingredients and steps.</p>
                            )}
                            {!isLoadingDetail && selectedRecipe && (
                                <div className="space-y-3">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h3 className="text-lg font-semibold text-slate-900">{selectedRecipe.title}</h3>
                                        {selectedRecipe.difficultyLevel && (
                                            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200 capitalize">
                                                {selectedRecipe.difficultyLevel}
                                            </span>
                                        )}
                                    </div>
                                    <p className="text-sm text-slate-600">
                                        {selectedRecipe.meal} • {selectedRecipe.timeMinutes} min • {selectedRecipe.nutrition.caloriesKcal} kcal
                                    </p>
                                    {selectedRecipe.img ? (
                                        <img
                                            src={selectedRecipe.img}
                                            alt={selectedRecipe.title}
                                            className="w-full rounded-lg object-cover max-h-48"
                                            onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
                                        />
                                    ) : (
                                        <div className="w-full rounded-lg bg-slate-100 border border-slate-200 max-h-48 h-36 flex flex-col items-center justify-center gap-1 text-slate-400">
                                            <svg xmlns="http://www.w3.org/2000/svg" className="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.2}>
                                                <path strokeLinecap="round" strokeLinejoin="round" d="M3 7h2l2-3h10l2 3h2a1 1 0 011 1v11a1 1 0 01-1 1H3a1 1 0 01-1-1V8a1 1 0 011-1z" />
                                                <circle cx="12" cy="13" r="3.5" strokeLinecap="round" strokeLinejoin="round" />
                                            </svg>
                                            <span className="text-xs">No image available</span>
                                        </div>
                                    )}
                                    {selectedRecipe.summary && <p className="text-sm text-slate-700">{selectedRecipe.summary}</p>}

                                    {/* Nutritional Info with Pie Chart */}
                                    <div>
                                        <h4 className="font-medium text-slate-900 mb-2">Nutritional Info</h4>
                                        <div className="grid grid-cols-3 gap-2 text-xs text-center mb-2">
                                            <div className="bg-blue-50 rounded p-2">
                                                <div className="font-bold text-blue-700">{selectedRecipe.nutrition.proteinG?.toFixed(1) ?? "—"} g</div>
                                                <div className="text-slate-500">Protein</div>
                                            </div>
                                            <div className="bg-amber-50 rounded p-2">
                                                <div className="font-bold text-amber-700">{selectedRecipe.nutrition.carbsG?.toFixed(1) ?? "—"} g</div>
                                                <div className="text-slate-500">Carbs</div>
                                            </div>
                                            <div className="bg-red-50 rounded p-2">
                                                <div className="font-bold text-red-700">{selectedRecipe.nutrition.fatsG?.toFixed(1) ?? "—"} g</div>
                                                <div className="text-slate-500">Fats</div>
                                            </div>
                                        </div>
                                        {(selectedRecipe.nutrition.proteinG > 0 || selectedRecipe.nutrition.carbsG > 0 || selectedRecipe.nutrition.fatsG > 0) && (
                                            <ResponsiveContainer width="100%" height={160}>
                                                <PieChart>
                                                    <Pie
                                                        data={[
                                                            { name: "Protein", value: Math.max(0, selectedRecipe.nutrition.proteinG) },
                                                            { name: "Carbs", value: Math.max(0, selectedRecipe.nutrition.carbsG) },
                                                            { name: "Fats", value: Math.max(0, selectedRecipe.nutrition.fatsG) },
                                                        ]}
                                                        cx="50%"
                                                        cy="50%"
                                                        outerRadius={60}
                                                        paddingAngle={2}
                                                        dataKey="value"
                                                    >
                                                        <Cell fill="#3b82f6" />
                                                        <Cell fill="#f59e0b" />
                                                        <Cell fill="#ef4444" />
                                                    </Pie>
                                                    <Tooltip formatter={(value) => typeof value === "number" ? `${value.toFixed(1)} g` : value} />
                                                    <Legend iconSize={10} />
                                                </PieChart>
                                            </ResponsiveContainer>
                                        )}
                                    </div>

                                    <div>
                                        <h4 className="font-medium text-slate-900 mb-1">Ingredients</h4>
                                        <ul className="text-sm text-slate-700 space-y-1">
                                            {selectedRecipe.ingredients.map((ingredient, idx) => (
                                                <li key={`${ingredient.label}-${idx}`}>
                                                    {ingredient.label} - {ingredient.quantity} {ingredient.unit}
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                    <div>
                                        <h4 className="font-medium text-slate-900 mb-1">Preparation</h4>
                                        <ol className="text-sm text-slate-700 space-y-2">
                                            {selectedRecipe.preparation.map((step) => (
                                                <li key={step.stepNumber}>
                                                    <span className="font-medium">{step.stepNumber}. {step.step}</span>
                                                    <div>{step.description}</div>
                                                </li>
                                            ))}
                                        </ol>
                                    </div>
                                    {selectedRecipe.source && (
                                        <p className="text-xs text-slate-400">Source: {selectedRecipe.source}</p>
                                    )}
                                </div>
                            )}
                        </CardBody>
                    </Card>
                </div>
            )}
        </div>
    );
}
