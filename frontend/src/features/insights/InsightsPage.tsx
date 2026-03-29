import { Outlet } from 'react-router-dom';

/**
 * InsightsPage: Main container for insights/analytics domain
 * Handles routing to nutrition insights, training insights, and combined analytics
 */
export default function InsightsPage() {
  return (
    <div className="flex flex-col gap-6">
      <Outlet />
    </div>
  );
}
