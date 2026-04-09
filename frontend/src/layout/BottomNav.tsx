import { NavLink } from "react-router-dom";
import {
    HomeIcon,
    SparklesIcon,
    FireIcon,
    ChartBarIcon,
    UserCircleIcon,
    ChatBubbleLeftRightIcon,
} from "@heroicons/react/24/outline";

// BottomNav: Mobile navigation with domain-based organization
// Shows shortcuts to main domain areas: Dashboard, Nutrition, Training, Insights
export default function BottomNav() {
    return (
        <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 z-10">
            <div className="max-w-lg mx-auto flex justify-around py-2">
                {/* Dashboard */}
                <NavLink
                    to="/"
                    end
                    className={({ isActive }) =>
                        `flex flex-col items-center gap-0.5 px-3 py-1.5 text-xs rounded-md transition-colors ${isActive ? "text-green-700 bg-green-50" : "text-slate-600"}`
                    }
                    aria-label="Dashboard"
                >
                    <HomeIcon className="h-6 w-6" />
                    <span>Home</span>
                </NavLink>

                {/* Nutrition */}
                <NavLink
                    to="/nutrition"
                    className={({ isActive }) =>
                        `flex flex-col items-center gap-0.5 px-3 py-1.5 text-xs rounded-md transition-colors ${isActive ? "text-green-700 bg-green-50" : "text-slate-600"}`
                    }
                    aria-label="Nutrition"
                >
                    <SparklesIcon className="h-6 w-6" />
                    <span>Nutrition</span>
                </NavLink>

                {/* Training */}
                <NavLink
                    to="/training"
                    className={({ isActive }) =>
                        `flex flex-col items-center gap-0.5 px-3 py-1.5 text-xs rounded-md transition-colors ${isActive ? "text-green-700 bg-green-50" : "text-slate-600"}`
                    }
                    aria-label="Training"
                >
                    <FireIcon className="h-6 w-6" />
                    <span>Training</span>
                </NavLink>

                {/* Insights */}
                <NavLink
                    to="/insights"
                    className={({ isActive }) =>
                        `flex flex-col items-center gap-0.5 px-3 py-1.5 text-xs rounded-md transition-colors ${isActive ? "text-green-700 bg-green-50" : "text-slate-600"}`
                    }
                    aria-label="Insights"
                >
                    <ChartBarIcon className="h-6 w-6" />
                    <span>Insights</span>
                </NavLink>

                {/* Assistant */}
                <NavLink
                    to="/assistant"
                    className={({ isActive }) =>
                        `flex flex-col items-center gap-0.5 px-3 py-1.5 text-xs rounded-md transition-colors ${isActive ? "text-green-700 bg-green-50" : "text-slate-600"}`
                    }
                    aria-label="Assistant"
                >
                    <ChatBubbleLeftRightIcon className="h-6 w-6" />
                    <span>Assistant</span>
                </NavLink>

                {/* Profile */}
                <NavLink
                    to="/profile"
                    className={({ isActive }) =>
                        `flex flex-col items-center gap-0.5 px-3 py-1.5 text-xs rounded-md transition-colors ${isActive ? "text-green-700 bg-green-50" : "text-slate-600"}`
                    }
                    aria-label="Profile"
                >
                    <UserCircleIcon className="h-6 w-6" />
                    <span>Profile</span>
                </NavLink>
            </div>
        </nav>
    );
}
