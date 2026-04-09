package com.erikallas.ndl.ai.assistant.functions;

import com.erikallas.ndl.health.profile.HealthProfileEntity;
import com.erikallas.ndl.health.profile.HealthProfileService;
import com.erikallas.ndl.health.weight.WeightEntryEntity;
import com.erikallas.ndl.health.weight.WeightEntryRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Function handler for get_health_metrics.
 *
 * Returns BMI, weight history summary, height, wellness score, dietary
 * preferences and restrictions. Never exposes email or credentials to the AI
 * context — userId is always sourced from the authenticated JWT, not from
 * function parameters.
 */
@Component
public class GetHealthMetricsFunction {

    private final HealthProfileService profileService;
    private final WeightEntryRepository weightRepo;
    private final ObjectMapper om;

    public GetHealthMetricsFunction(HealthProfileService profileService, WeightEntryRepository weightRepo,
            ObjectMapper om) {
        this.profileService = profileService;
        this.weightRepo = weightRepo;
        this.om = om;
    }

    public String execute(UUID userId) {
        ObjectNode result = om.createObjectNode();

        HealthProfileEntity profile = profileService.find(userId).orElse(null);
        if (profile == null) {
            result.put("error", "No health profile found. Ask the user to complete their health profile first.");
            return result.toString();
        }

        // BMI
        if (profile.getBmiValue() != null) {
            result.put("bmi", Math.round(profile.getBmiValue().doubleValue() * 10.0) / 10.0);
            result.put("bmi_category", profile.getBmiClassification() != null ? profile.getBmiClassification()
                    : bmiCategory(profile.getBmiValue().doubleValue()));
        }

        // Height
        result.put("height_cm", profile.getHeightCm());
        if (profile.getBirthYear() != null) {
            result.put("age_approx", LocalDate.now().getYear() - profile.getBirthYear());
        }
        if (profile.getGender() != null) {
            result.put("gender", profile.getGender());
        }
        result.put("baseline_activity_level", profile.getBaselineActivityLevel());

        // Wellness score
        if (profile.getWellnessScore() != null) {
            result.put("wellness_score", profile.getWellnessScore());
            result.put("wellness_category", wellnessCategory(profile.getWellnessScore()));
        }

        // Weight — latest reading and 30-day trend
        List<WeightEntryEntity> recent = weightRepo.findTop30ByUserIdOrderByMeasuredAtDesc(userId);
        if (!recent.isEmpty()) {
            WeightEntryEntity latest = recent.get(0);
            result.put("current_weight_kg", Math.round(latest.getWeightKg() * 10.0) / 10.0);
            result.put("current_weight_date", latest.getMeasuredAt().toLocalDate().toString());

            if (recent.size() >= 2) {
                double oldest = recent.get(recent.size() - 1).getWeightKg();
                double change = latest.getWeightKg() - oldest;
                result.put("weight_change_kg_over_period", Math.round(change * 10.0) / 10.0);
                result.put("weight_period_days", recent.size());
                result.put("weight_trend", change < -0.3 ? "declining" : change > 0.3 ? "increasing" : "stable");
            }
        }

        // Dietary preferences & restrictions (decrypted by JPA converter — safe to
        // share with AI)
        if (profile.getDietaryPreferences() != null && !profile.getDietaryPreferences().isEmpty()) {
            ArrayNode prefs = result.putArray("dietary_preferences");
            profile.getDietaryPreferences().forEach(prefs::add);
        }
        if (profile.getDietaryRestrictions() != null && !profile.getDietaryRestrictions().isEmpty()) {
            ArrayNode restr = result.putArray("dietary_restrictions");
            profile.getDietaryRestrictions().forEach(restr::add);
        }

        return result.toString();
    }

    private static String bmiCategory(double bmi) {
        if (bmi < 18.5)
            return "Underweight";
        if (bmi < 25.0)
            return "Normal";
        if (bmi < 30.0)
            return "Overweight";
        return "Obese";
    }

    private static String wellnessCategory(int score) {
        if (score >= 80)
            return "Excellent";
        if (score >= 60)
            return "Good";
        if (score >= 40)
            return "Fair";
        return "Needs attention";
    }
}
