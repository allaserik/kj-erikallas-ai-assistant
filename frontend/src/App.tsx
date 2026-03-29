import './App.css';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import AppShell from './layout/AppShell';
import DashboardPage from './features/dashboard/DashboardPage';
import GoalsPage from './features/goals/GoalsPage';
import ProfilePage from './features/profile/ProfilePage';
import SettingsPage from './features/settings/SettingsPage';
import ConsentPage from './features/consent/ConsentPage';

// Nutrition domain
import NutritionPage from './features/nutrition/NutritionPage';
import NutritionPreferencesPage from './features/nutrition/pages/NutritionPreferencesPage';
import RecipeExplorerPage from './features/nutrition/pages/RecipeExplorerPage';
import ShoppingListPage from './features/nutrition/pages/ShoppingListPage';
import MealPlanPage from './features/nutrition/pages/MealPlanPage';
import AIGeneratedMealPlansPage from './features/nutrition/pages/AIGeneratedMealPlansPage';
import { MealManagementPage } from './features/nutrition/pages/MealManagementPage';
import MealPlansHubPage from './features/nutrition/pages/MealPlansHubPage';
import SimpleMealGeneratorPage from './features/nutrition/pages/SimpleMealGeneratorPage';
import MealPlanDetailPage from './features/nutrition/pages/MealPlanDetailPage';

// Training domain
import TrainingPage from './features/training/TrainingPage';
import CheckInPage from './features/training/pages/CheckInPage';

// Insights domain
import InsightsPage from './features/insights/InsightsPage';
import TrendsPage from './features/insights/pages/TrendsPage';

// Auth
import { VerifyEmailPage } from './features/auth/VerifyEmailPage';
import { ForgotPasswordPage } from './features/auth/ForgotPasswordPage';
import { ResetPasswordPage } from './features/auth/ResetPasswordPage';
import { LoginModal } from './features/auth/LoginModal';
import { SplashScreen } from './shared/ui/SplashScreen';
import { useAppAuth } from './shared/auth/AuthContext';
import { useAuthedQuery } from './shared/auth/useAuthedQuery';
import { getPrivacyPreferences } from './shared/api/privacy';

/**
 * LoggedInContent: Shown when user is authenticated
 * - Checks for consent requirement
 * - Shows ConsentPage if no consent yet
 * - Shows AppShell with app features if consent given
 */
function LoggedInContent() {
  const location = useLocation();
  const privacyQ = useAuthedQuery(`consent:${location.pathname}`, getPrivacyPreferences, true);
  const hasConsent = !!privacyQ.data?.data_usage_consent;
  const consentLoading = privacyQ.loading;

  // Show loading screen while checking consent status
  if (consentLoading) {
    return <SplashScreen />;
  }

  // If user hasn't given consent, show consent page only
  if (!hasConsent) {
    return (
      <Routes>
        <Route path="*" element={<ConsentPage />} />
      </Routes>
    );
  }

  // User has consent, show full app
  return (
    <Routes>
      <Route path="/" element={<AppShell />}>
        <Route index element={<DashboardPage />} />

        {/* Nutrition Domain */}
        <Route path="nutrition" element={<NutritionPage />}>
          <Route index element={<MealPlansHubPage />} />
          <Route path="plans" element={<MealPlanPage />} />
          <Route path="plan/:mealPlanId" element={<MealPlanDetailPage />} />
          <Route path="recipes" element={<RecipeExplorerPage />} />
          <Route path="shopping-list" element={<ShoppingListPage />} />
          <Route path="preferences" element={<NutritionPreferencesPage />} />
          <Route path="generate" element={<SimpleMealGeneratorPage />} />
          <Route path="ai-generate" element={<AIGeneratedMealPlansPage />} />
          <Route path="manage" element={<MealManagementPage />} />
        </Route>

        {/* Training Domain */}
        <Route path="training" element={<TrainingPage />}>
          <Route index element={<CheckInPage />} />
        </Route>

        {/* Insights Domain */}
        <Route path="insights" element={<InsightsPage />}>
          <Route index element={<TrendsPage />} />
        </Route>

        {/* Goals */}
        <Route path="goals" element={<GoalsPage />} />

        {/* Profile & Settings */}
        <Route path="profile" element={<ProfilePage />} />
        <Route path="settings" element={<SettingsPage />} />

        {/* Legacy route redirects for backward compatibility */}
        <Route path="planner" element={<Navigate to="/nutrition" replace />} />
        <Route path="planner/*" element={<Navigate to="/nutrition" replace />} />
        <Route path="trends" element={<Navigate to="/insights" replace />} />
        <Route path="checkin" element={<Navigate to="/training" replace />} />
      </Route>
    </Routes>
  );
}

/**
 * LoggedOutContent: Shown when user is NOT authenticated
 * - Shows public auth pages (verify-email, forgot-password, reset-password)
 * - Shows LoginModal for all other paths
 */
function LoggedOutContent() {
  const location = useLocation();
  const publicAuthPaths = new Set(['/verify-email', '/forgot-password', '/reset-password']);
  const isPublicAuthPage = publicAuthPaths.has(location.pathname);

  return (
    <>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<div />} />
      </Routes>
      {/* Show login modal on any non-public-auth page */}
      {!isPublicAuthPage && <LoginModal />}
    </>
  );
}

/**
 * Main App Component
 * Routes between LoggedInContent and LoggedOutContent based on auth state
 */
function App() {
  const { isAuthenticated, isLoading } = useAppAuth();

  if (isLoading) return <SplashScreen />;

  return (
    <BrowserRouter key={isAuthenticated ? "authenticated" : "logged-out"}>
      {isAuthenticated ? <LoggedInContent /> : <LoggedOutContent />}
    </BrowserRouter>
  );
}

export default App;
