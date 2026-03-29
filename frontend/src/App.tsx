import './App.css';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import AppShell from './layout/AppShell';
import DashboardPage from './features/dashboard/DashboardPage';
import TrendsPage from './features/trends/TrendsPage';
import GoalsPage from './features/goals/GoalsPage';
import CheckInPage from './features/checkin/CheckInPage';
import ProfilePage from './features/profile/ProfilePage';
import SettingsPage from './features/settings/SettingsPage';
import ConsentPage from './features/consent/ConsentPage';
import NutritionPreferencesPage from './features/nutrition/NutritionPreferencesPage';
import RecipeExplorerPage from './features/nutrition/RecipeExplorerPage';
import ShoppingListPage from './features/nutrition/ShoppingListPage';
import PlannerPage from './features/planner/PlannerPage';
import MealPlanPage from './features/planner/MealPlanPage';
import AIGeneratedMealPlansPage from './features/planner/AIGeneratedMealPlansPage';
import { MealManagementPage } from './features/planner/MealManagementPage';
import MealPlansHubPage from './features/planner/MealPlansHubPage';
import SimpleMealGeneratorPage from './features/planner/SimpleMealGeneratorPage';
import MealPlanDetailPage from './features/planner/MealPlanDetailPage';
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
        <Route path="planner" element={<PlannerPage />}>
          <Route index element={<MealPlansHubPage />} />
          <Route path="today" element={<CheckInPage />} />
          <Route path="meals" element={<MealPlanPage />} />
          <Route path="goals" element={<GoalsPage />} />
          <Route path="nutrition" element={<NutritionPreferencesPage />} />
          <Route path="recipes" element={<RecipeExplorerPage />} />
          <Route path="ai-generate" element={<AIGeneratedMealPlansPage />} />
          <Route path="shopping-list" element={<ShoppingListPage />} />
          <Route path="manage-meals" element={<MealManagementPage />} />
          <Route path="generate-meal-plan" element={<SimpleMealGeneratorPage />} />
          <Route path="meal-plan/:mealPlanId" element={<MealPlanDetailPage />} />
        </Route>
        <Route path="trends" element={<TrendsPage />} />
        <Route path="goals" element={<Navigate to="/planner/goals" replace />} />
        <Route path="checkin" element={<Navigate to="/planner/today" replace />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="nutrition/preferences" element={<Navigate to="/planner/nutrition" replace />} />
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
