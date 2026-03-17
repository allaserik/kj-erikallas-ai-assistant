import './App.css';
import type { ReactElement } from 'react';
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
import PlannerPage from './features/planner/PlannerPage';
import { VerifyEmailPage } from './features/auth/VerifyEmailPage';
import { ForgotPasswordPage } from './features/auth/ForgotPasswordPage';
import { ResetPasswordPage } from './features/auth/ResetPasswordPage';
import { LoginModal } from './features/auth/LoginModal';
import { SplashScreen } from './shared/ui/SplashScreen';
import { useAppAuth } from './shared/auth/AuthContext';
import { useAuthedQuery } from './shared/auth/useAuthedQuery';
import { getPrivacyPreferences } from './shared/api/privacy';

function AuthenticatedAppContent() {
  const location = useLocation();
  const publicPaths = new Set(['/verify-email', '/forgot-password', '/reset-password']);
  const isPublicRoute = publicPaths.has(location.pathname);
  const privacyQ = useAuthedQuery(`consentGate:${location.pathname}`, getPrivacyPreferences, true);
  const consentLoading = privacyQ.loading;
  const hasConsent = !!privacyQ.data?.data_usage_consent;
  const consentRequired = !consentLoading && !privacyQ.error && !hasConsent;

  const guarded = (element: ReactElement) =>
    consentRequired ? <Navigate to="/consent" replace /> : element;
  const protectedPage = (element: ReactElement) => guarded(element);

  if (consentLoading && !isPublicRoute) {
    return <SplashScreen />;
  }

  return (
    <>
      <Routes>
        {/* Public routes - no auth required */}
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        {/* Protected routes - require auth */}
        <Route path="/" element={<AppShell />}>
          <Route index element={protectedPage(<DashboardPage />)} />
            <Route path="planner" element={protectedPage(<PlannerPage />)}>
              <Route index element={protectedPage(<Navigate to="/planner/today" replace />)} />
              <Route path="today" element={protectedPage(<CheckInPage />)} />
              <Route path="goals" element={protectedPage(<GoalsPage />)} />
              <Route path="nutrition" element={protectedPage(<NutritionPreferencesPage />)} />
              <Route path="recipes" element={protectedPage(<RecipeExplorerPage />)} />
            </Route>
          <Route path="trends" element={protectedPage(<TrendsPage />)} />
          <Route path="goals" element={protectedPage(<Navigate to="/planner/goals" replace />)} />
          <Route path="checkin" element={protectedPage(<Navigate to="/planner/today" replace />)} />
          <Route path="profile" element={protectedPage(<ProfilePage />)} />
          <Route path="settings" element={protectedPage(<SettingsPage />)} />
          <Route path="nutrition/preferences" element={protectedPage(<Navigate to="/planner/nutrition" replace />)} />
          <Route path="consent" element={protectedPage(hasConsent ? <Navigate to="/" replace /> : <ConsentPage />)} />
        </Route>
      </Routes>

    </>
  );
}

function UnauthenticatedAppContent() {
  const location = useLocation();
  const publicPaths = new Set(['/verify-email', '/forgot-password', '/reset-password']);
  const isPublicRoute = publicPaths.has(location.pathname);

  return (
    <>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="*" element={<div />} />
      </Routes>
      {!isPublicRoute && <LoginModal />}
    </>
  );
}

// App: Uses centralized AuthContext from AuthProvider
// All auth checks happen in AuthContext, App just consumes unified state
function App() {
  const { isAuthenticated, isLoading } = useAppAuth();

  // Show splash screen while loading auth state
  if (isLoading) return <SplashScreen />;

  return (
    <BrowserRouter key={isAuthenticated ? "auth" : "guest"}>
      {isAuthenticated ? <AuthenticatedAppContent /> : <UnauthenticatedAppContent />}
    </BrowserRouter>
  );
}

export default App;
