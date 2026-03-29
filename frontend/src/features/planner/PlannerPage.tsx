import { Link, Outlet, useLocation } from "react-router-dom";

const tabs = [
    { to: "/planner", label: "Overview", exact: true },
    { to: "/planner/generate-meal-plan", label: "Generate Plan" },
    { to: "/planner/today", label: "Log Today" },
    { to: "/planner/goals", label: "Goals" },
    { to: "/planner/nutrition", label: "Nutrition" },
    { to: "/planner/recipes", label: "Recipes" },
    { to: "/planner/shopping-list", label: "Shopping List" },
];

export default function PlannerPage() {
    const location = useLocation();

    const isActive = (to: string, exact?: boolean) => {
        if (exact) {
            return location.pathname === to;
        }
        return location.pathname.startsWith(to);
    };

    return (
        <div className="space-y-4 pb-32 md:pb-4">
            <div>
                <h1 className="text-2xl font-bold text-slate-900">Meal Planner</h1>
                <p className="text-slate-600">Generate AI meal plans, track your nutrition, and log what you eat.</p>
            </div>

            <div className="overflow-x-auto">
                <div className="inline-flex rounded-lg border border-slate-200 bg-white p-1 whitespace-nowrap">
                    {tabs.map((tab) => (
                        <Link
                            key={tab.to}
                            to={tab.to}
                            className={`px-3 py-1.5 text-sm rounded-md transition ${isActive(tab.to, tab.exact)
                                ? "bg-green-600 text-white"
                                : "text-slate-700 hover:bg-slate-100"
                                }`}
                        >
                            {tab.label}
                        </Link>
                    ))}
                </div>
            </div>

            <Outlet />
        </div>
    );
}
