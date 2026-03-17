import { useState } from "react";
import { Card } from "../../shared/ui/Card";
import { MealManagementUI } from "./MealManagementUI";

export function MealManagementPage() {
    const [selectedDate, setSelectedDate] = useState<string>(
        new Date().toISOString().split("T")[0]
    );
    const [refreshKey, setRefreshKey] = useState(0);

    const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSelectedDate(e.target.value);
    };

    const handleMealsUpdated = () => {
        // Force component remount by changing key
        setRefreshKey((prev) => prev + 1);
    };

    return (
        <div className="min-h-screen bg-gray-50 p-6">
            <div className="max-w-4xl mx-auto space-y-6">
                {/* Header */}
                <div>
                    <h1 className="text-3xl font-bold text-gray-900">Manage Your Meals</h1>
                    <p className="text-gray-600 mt-2">
                        Reorder meals, swap between days, or replace recipes
                    </p>
                </div>

                {/* Date Picker */}
                <Card>
                    <label className="block">
                        <span className="text-sm font-medium text-gray-700 mb-2 block">
                            Select Date
                        </span>
                        <input
                            type="date"
                            value={selectedDate}
                            onChange={handleDateChange}
                            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        />
                    </label>
                    <p className="text-sm text-gray-500 mt-2">
                        Choose a date to manage meals. Use the controls to reorder, swap, or replace meals.
                    </p>
                </Card>

                {/* Meal Management UI */}
                <MealManagementUI
                    key={refreshKey}
                    date={selectedDate}
                    onMealsUpdated={handleMealsUpdated}
                />

                {/* Tips Section */}
                <Card>
                    <h3 className="font-semibold text-blue-900 mb-3">💡 Meal Management Tips</h3>
                    <ul className="space-y-2 text-sm text-blue-800">
                        <li>
                            <strong>Reorder:</strong> Use the ↑ and ↓ buttons to change meal order within a day
                        </li>
                        <li>
                            <strong>Swap:</strong> Click "Swap" on one meal, then select another meal to exchange
                            them
                        </li>
                        <li>
                            <strong>Replace:</strong> Click "Replace" to substitute a meal with a different recipe
                        </li>
                        <li>
                            <strong>Save:</strong> Click "Save Meal Order" after reordering to confirm changes
                        </li>
                        <li>
                            <strong>Multi-day Swap:</strong> You can swap meals between different days using the
                            Swap feature
                        </li>
                    </ul>
                </Card>
            </div>
        </div>
    );
}
