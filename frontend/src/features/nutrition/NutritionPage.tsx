import { Outlet } from 'react-router-dom';

/**
 * NutritionPage: Main container for nutrition domain
 * Handles routing to meal planning, recipes, shopping lists, and nutrition preferences
 */
export default function NutritionPage() {
    return (
        <div className="flex flex-col gap-6">
            <Outlet />
        </div>
    );
}
