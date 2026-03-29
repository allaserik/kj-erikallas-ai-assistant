import { NavLink } from "react-router-dom";
import {
    HomeIcon,
    FireIcon,
    ChartBarIcon,
    UserCircleIcon,
    Cog6ToothIcon,
    SparklesIcon,
    CheckCircleIcon,
} from "@heroicons/react/24/outline";
import { AuthButton } from "../features/auth/AuthButton";

// Sidebar: Neutral white background, slate grays, soft green accents.
// Modern, professional navigation with clear active states.
// Organized by domain: Nutrition, Training, Insights
export default function Sidebar() {
    return (
        <aside className="hidden md:flex md:flex-col md:w-56 md:h-screen md:fixed md:left-0 md:top-0 bg-white border-r border-slate-200 z-20">
            <div className="flex flex-col gap-1 p-4 flex-1">
                <NavLink
                    to="/"
                    end
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <HomeIcon className="h-5 w-5" /> Dashboard
                </NavLink>

                {/* NUTRITION DOMAIN */}
                <div className="mt-4 mb-2 px-3">
                    <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Nutrition</p>
                </div>
                <NavLink
                    to="/nutrition"
                    end
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <SparklesIcon className="h-5 w-5" /> Meal Planning
                </NavLink>
                <NavLink
                    to="/nutrition/recipes"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 pl-8 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <span className="text-xs">Recipes</span>
                </NavLink>
                <NavLink
                    to="/nutrition/shopping-list"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 pl-8 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <span className="text-xs">Shopping List</span>
                </NavLink>
                <NavLink
                    to="/nutrition/manage"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 pl-8 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <span className="text-xs">Manage Meals</span>
                </NavLink>
                <NavLink
                    to="/nutrition/intake"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 pl-8 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <span className="text-xs">Log Intake</span>
                </NavLink>

                {/* TRAINING DOMAIN */}
                <div className="mt-4 mb-2 px-3">
                    <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Training</p>
                </div>
                <NavLink
                    to="/training"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <FireIcon className="h-5 w-5" /> Activity Log
                </NavLink>

                {/* INSIGHTS DOMAIN */}
                <div className="mt-4 mb-2 px-3">
                    <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Insights</p>
                </div>
                <NavLink
                    to="/insights"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <ChartBarIcon className="h-5 w-5" /> Analytics
                </NavLink>

                {/* GOALS */}
                <div className="mt-4 mb-2 px-3">
                    <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Goals</p>
                </div>
                <NavLink
                    to="/goals"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <CheckCircleIcon className="h-5 w-5" /> Goals
                </NavLink>

                {/* USER */}
                <div className="mt-4 mb-2 px-3">
                    <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">User</p>
                </div>
                <NavLink
                    to="/profile"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <UserCircleIcon className="h-5 w-5" /> Health Profile
                </NavLink>
                <NavLink
                    to="/settings"
                    className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${isActive ? "bg-green-50 text-green-700" : "text-slate-600 hover:bg-slate-50"}`
                    }
                >
                    <Cog6ToothIcon className="h-5 w-5" /> Settings
                </NavLink>
            </div>
            {/* Auth controls at the bottom */}
            <div className="p-4 border-t border-slate-200">
                <AuthButton />
            </div>
        </aside>
    );
}
