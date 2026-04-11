import { useState, useCallback, useRef } from "react";
import { useAuthedQuery } from "../../shared/auth/useAuthedQuery";
import { getAllGoals, createGoal, updateGoal, deleteGoal } from "../../shared/api/goals";
import { useAppAuth } from "../../shared/auth/AuthContext";
import { explainApiError } from "../../shared/api/errors";
import type { Goal } from "../../shared/types";

export interface GoalsState {
    allGoals: Goal[];
    activeGoals: Goal[];
    isLoading: boolean;
    error: Error | null;
    mutationError: string | null;
    isCreating: boolean;
    isUpdating: boolean;
    isDeleting: boolean;
}

export function useGoalsData() {
    const { isAuthenticated } = useAppAuth();
    const [isCreating, setIsCreating] = useState(false);
    const [isUpdating, setIsUpdating] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [mutationError, setMutationError] = useState<string | null>(null);
    const refetchCounterRef = useRef(0);
    const [refetchCounter, setRefetchCounter] = useState(0);

    // Fetch all goals (re-fetches when refetchCounter changes)
    const allGoalsQ = useAuthedQuery(`allGoals-${refetchCounter}`, getAllGoals, isAuthenticated);
    const isLoading = allGoalsQ.loading;
    const error = allGoalsQ.error || null;
    const allGoals = Array.isArray(allGoalsQ.data) ? allGoalsQ.data : [];
    const activeGoals = allGoals.filter((goal) => goal.isActive);

    // Refetch both queries
    const refetch = useCallback(() => {
        refetchCounterRef.current++;
        setRefetchCounter(refetchCounterRef.current);
    }, []);

    // Action: Create new goal
    const createNewGoal = useCallback(
        async (goalType: string, targetWeightKg?: number, targetActivityDays?: number, notes?: string, targetDate?: string, token?: string) => {
            if (!token) return;

            setIsCreating(true);
            setMutationError(null);
            try {
                const newGoal: Omit<Goal, "id" | "userId" | "createdAt" | "updatedAt"> = {
                    title: goalType,
                    description: notes,
                    goalType: goalType as Goal["goalType"],
                    targetValue: targetWeightKg,
                    targetActivityDaysPerWeek: targetActivityDays,
                    targetDate: targetDate || new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
                    isActive: true,
                    progress: 0,
                };

                await createGoal(newGoal, token);
                refetch();
            } catch (e) {
                setMutationError(explainApiError(e));
                throw e;
            } finally {
                setIsCreating(false);
            }
        },
        [refetch]
    );

    // Action: Update goal
    const updateExistingGoal = useCallback(
        async (goalId: string, updates: Partial<Goal>, token?: string) => {
            if (!token) return;

            setIsUpdating(true);
            setMutationError(null);
            try {
                await updateGoal(goalId, updates, token);
                refetch();
            } catch (e) {
                setMutationError(explainApiError(e));
                throw e;
            } finally {
                setIsUpdating(false);
            }
        },
        [refetch]
    );

    // Action: Delete goal
    const deleteExistingGoal = useCallback(
        async (goalId: string, token?: string) => {
            if (!token) return;

            setIsDeleting(true);
            setMutationError(null);
            try {
                await deleteGoal(goalId, token);
                refetch();
            } catch (e) {
                setMutationError(explainApiError(e));
                throw e;
            } finally {
                setIsDeleting(false);
            }
        },
        [refetch]
    );

    return {
        allGoals,
        activeGoals,
        isLoading,
        error,
        mutationError,
        isCreating,
        isUpdating,
        isDeleting,
        createNewGoal,
        updateExistingGoal,
        deleteExistingGoal,
    };
}
