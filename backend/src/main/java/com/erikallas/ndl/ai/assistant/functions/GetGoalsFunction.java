package com.erikallas.ndl.ai.assistant.functions;

import com.erikallas.ndl.health.goal.GoalEntity;
import com.erikallas.ndl.health.goal.GoalProgressEntity;
import com.erikallas.ndl.health.goal.GoalProgressRepository;
import com.erikallas.ndl.health.goal.GoalService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Function handler for get_goals.
 *
 * Returns the user's active goals and optional recent progress snapshots.
 * userId is always sourced from the authenticated JWT.
 */
@Component
public class GetGoalsFunction {

    private final GoalService goalService;
    private final GoalProgressRepository progressRepo;
    private final ObjectMapper om;

    public GetGoalsFunction(GoalService goalService, GoalProgressRepository progressRepo, ObjectMapper om) {
        this.goalService = goalService;
        this.progressRepo = progressRepo;
        this.om = om;
    }

    /**
     * @param userId         Authenticated user ID.
     * @param includeHistory Whether to include recent progress snapshots.
     */
    public String execute(UUID userId, boolean includeHistory) {
        List<GoalEntity> goals = goalService.getActiveGoals(userId);

        ObjectNode result = om.createObjectNode();

        if (goals.isEmpty()) {
            result.put("message", "No active goals found. The user has not set any goals yet.");
            return result.toString();
        }

        ArrayNode goalsArray = result.putArray("active_goals");
        for (GoalEntity goal : goals) {
            ObjectNode g = om.createObjectNode();
            g.put("id", goal.getId().toString());
            g.put("type", goal.getGoalType().name());
            if (goal.getTargetWeightKg() != null) {
                g.put("target_weight_kg", goal.getTargetWeightKg());
            }
            if (goal.getTargetActivityDaysPerWeek() != null) {
                g.put("target_activity_days_per_week", goal.getTargetActivityDaysPerWeek());
            }
            if (goal.getTargetDate() != null) {
                g.put("target_date", goal.getTargetDate().toString());
            }
            if (goal.getNotes() != null && !goal.getNotes().isBlank()) {
                g.put("notes", goal.getNotes());
            }

            // Latest progress snapshot
            GoalProgressEntity latestProgress = progressRepo.findFirstByGoalIdOrderByRecordedAtDesc(goal.getId())
                    .orElse(null);
            if (latestProgress != null) {
                g.put("progress_percentage", latestProgress.getProgressPercentage());
                if (latestProgress.getCurrentValue() != null) {
                    g.put("current_value", latestProgress.getCurrentValue().doubleValue());
                }
                g.put("is_on_track", Boolean.TRUE.equals(latestProgress.getIsOnTrack()));
                g.put("progress_recorded_at", latestProgress.getRecordedAt().toString());
            }

            // Optional history — last 5 snapshots
            if (includeHistory) {
                List<GoalProgressEntity> history = progressRepo.findByGoalIdOrderByRecordedAtDesc(goal.getId());
                int limit = Math.min(5, history.size());
                if (limit > 0) {
                    ArrayNode histArray = g.putArray("recent_progress_history");
                    for (int i = 0; i < limit; i++) {
                        GoalProgressEntity snap = history.get(i);
                        ObjectNode h = om.createObjectNode();
                        if (snap.getCurrentValue() != null) {
                            h.put("value", snap.getCurrentValue().doubleValue());
                        }
                        h.put("percentage", snap.getProgressPercentage());
                        h.put("recorded_at", snap.getRecordedAt().toString());
                        histArray.add(h);
                    }
                }
            }

            goalsArray.add(g);
        }

        return result.toString();
    }
}
