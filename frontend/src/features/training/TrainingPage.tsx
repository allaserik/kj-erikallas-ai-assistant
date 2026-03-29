import { Outlet } from 'react-router-dom';

/**
 * TrainingPage: Main container for training/activity domain
 * Handles routing to activity logging, workout tracking, and training history
 */
export default function TrainingPage() {
  return (
    <div className="flex flex-col gap-6">
      <Outlet />
    </div>
  );
}
