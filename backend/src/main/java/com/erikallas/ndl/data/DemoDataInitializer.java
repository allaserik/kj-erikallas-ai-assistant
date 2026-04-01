package com.erikallas.ndl.data;

import com.erikallas.ndl.auth.user.model.UserEntity;
import com.erikallas.ndl.auth.user.model.UserRepository;
import com.erikallas.ndl.health.goal.GoalEntity;
import com.erikallas.ndl.health.goal.GoalProgressEntity;
import com.erikallas.ndl.health.goal.GoalProgressRepository;
import com.erikallas.ndl.health.goal.GoalRepository;
import com.erikallas.ndl.health.goal.GoalType;
import com.erikallas.ndl.health.activity.ActivityCheckinEntity;
import com.erikallas.ndl.health.activity.ActivityCheckinRepository;
import com.erikallas.ndl.health.profile.HealthProfileEntity;
import com.erikallas.ndl.health.profile.HealthProfileRepository;
import com.erikallas.ndl.health.weight.WeightEntryEntity;
import com.erikallas.ndl.health.weight.WeightEntryRepository;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesEntity;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanEntity;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealEntity;
import com.erikallas.ndl.nutrition.mealplan.MealPlanMealRepository;
import com.erikallas.ndl.nutrition.mealplan.MealPlanRepository;
import com.erikallas.ndl.nutrition.meallog.MealIntakeLogService;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogQueryRepository;
import com.erikallas.ndl.nutrition.recipe.RecipeCatalogModels;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Initializes demo data for testing and visualization. Activated when
 * demo.mode=true
 * 
 * Runs after recipe import (Order=2) to ensure recipes are available for meal
 * logging.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "demo.mode", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final String DEMO_EMAIL = "demo@example.com";
    private static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository userRepo;
    private final HealthProfileRepository profileRepo;
    private final GoalRepository goalRepo;
    private final GoalProgressRepository goalProgressRepo;
    private final WeightEntryRepository weightRepo;
    private final ActivityCheckinRepository activityRepo;
    private final NutritionPreferencesRepository nutritionPreferencesRepo;
    private final MealPlanRepository mealPlanRepo;
    private final MealPlanMealRepository mealPlanMealRepo;
    private final RecipeCatalogQueryRepository recipeQueryRepo;
    private final MealIntakeLogService mealIntakeLogService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(UserRepository userRepo, HealthProfileRepository profileRepo, GoalRepository goalRepo,
            GoalProgressRepository goalProgressRepo, WeightEntryRepository weightRepo,
            ActivityCheckinRepository activityRepo, NutritionPreferencesRepository nutritionPreferencesRepo,
            MealPlanRepository mealPlanRepo, MealPlanMealRepository mealPlanMealRepo,
            RecipeCatalogQueryRepository recipeQueryRepo, MealIntakeLogService mealIntakeLogService,
            PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.goalRepo = goalRepo;
        this.goalProgressRepo = goalProgressRepo;
        this.weightRepo = weightRepo;
        this.activityRepo = activityRepo;
        this.nutritionPreferencesRepo = nutritionPreferencesRepo;
        this.mealPlanRepo = mealPlanRepo;
        this.mealPlanMealRepo = mealPlanMealRepo;
        this.recipeQueryRepo = recipeQueryRepo;
        this.mealIntakeLogService = mealIntakeLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("DemoDataInitializer ApplicationRunner triggered");
        initializeDemo();
    }

    private void initializeDemo() {
        try {
            // Check if demo user already exists
            if (userRepo.findById(DEMO_USER_ID).isPresent()) {
                log.info("Demo user already exists, skipping initialization");
                return;
            }

            log.info("Initializing demo data...");

            // 1. Create demo user with email/password (NOT Auth0)
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            UserEntity user = new UserEntity(DEMO_USER_ID, null, DEMO_EMAIL, now);
            // Set password: demo@example.com (encoded)
            user.setPasswordHash(passwordEncoder.encode(DEMO_EMAIL));
            user.setEmailVerified(true);
            userRepo.save(user);
            log.info("Created demo user: {} (email/password auth)", DEMO_EMAIL);

            // 2. Create health profile
            HealthProfileEntity profile = new HealthProfileEntity(DEMO_USER_ID, 1990, "Other", 175, "MODERATE", now,
                    now);
            profile.setDietaryPreferences(List.of("High-protein", "Low-sugar"));
            profile.setDietaryRestrictions(List.of("Lactose"));
            profile.setFitnessAssessment(Map.<String, Object>ofEntries(Map.entry("occupation_type", "Office"),
                    Map.entry("current_activity_frequency", 4),
                    Map.entry("exercise_types", List.of("Cardio", "Strength", "Outdoors")),
                    Map.entry("average_session_duration", "30_60"),
                    Map.entry("self_assessed_fitness_level", "INTERMEDIATE"),
                    Map.entry("preferred_exercise_environment", "GYM"),
                    Map.entry("exercise_time_preference", "EVENING"), Map.entry("current_endurance_minutes", 35),
                    Map.entry("pushups_count", 30), Map.entry("situps_count", 45), Map.entry("pullups_count", 8),
                    Map.entry("run_3km_time_sec", 980)));
            profile.setFitnessAssessmentCompleted(true);
            profileRepo.save(profile);
            log.info("Created demo health profile (height: 175cm)");

            // 3. Create goals (multiple active + one archived for history testing)
            GoalEntity activeWeightGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.WEIGHT_LOSS, 75.0, 4,
                    now.toLocalDate().plusDays(90), "Lose 5kg in 3 months", true, now, now);
            goalRepo.save(activeWeightGoal);

            GoalEntity activeFitnessGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.IMPROVE_FITNESS,
                    null, 5, now.toLocalDate().plusDays(60), "Reach 5 active days per week", true, now.minusDays(5),
                    now.minusDays(5));
            goalRepo.save(activeFitnessGoal);

            GoalEntity archivedGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.IMPROVE_FITNESS, null, 5,
                    now.toLocalDate().minusDays(10), "Improve weekly training consistency", false, now.minusDays(120),
                    now.minusDays(20));
            goalRepo.save(archivedGoal);
            log.info("Created demo goals (multiple active + archived)");

            // 4. Create 30 days of weight entries with realistic progression
            createWeightEntries(DEMO_USER_ID, now);
            log.info("Created 30 days of weight entries");

            // 5. Create goal progress history for trend charts and analytics
            createWeightGoalProgressHistory(activeWeightGoal, now);
            createActivityGoalProgressHistory(activeFitnessGoal, now);
            createArchivedGoalProgressHistory(archivedGoal, now);
            log.info("Created goal progress history");

            // 6. Create activity check-ins for activity heatmap and timeline demos
            createActivityCheckins(DEMO_USER_ID, now);
            log.info("Created activity check-ins");

            // 7. Create nutrition preferences
            createNutritionPreferences(DEMO_USER_ID, now);
            log.info("Created nutrition preferences");

            // 8. Create meal plans with recipes
            createMealPlans(DEMO_USER_ID, now);
            log.info("Created meal plans with recipes");

            // 9. Create meal intake logs (simulate eating meals for today and yesterday)
            createMealLogs(DEMO_USER_ID, now);
            log.info("Created meal intake logs");

            // 10. Set BMI and wellness score snapshot for faster first-load demo UX
            var latestWeight = weightRepo.findTop30ByUserIdOrderByMeasuredAtDesc(DEMO_USER_ID).stream().findFirst();
            latestWeight.ifPresent(w -> profile.calculateBMI(w.getWeightKg()));
            profile.setWellnessScore(74);
            profile.setUpdatedAt(now);
            profileRepo.save(profile);

            log.info("Demo data initialization complete!");

        } catch (Exception e) {
            log.error("Failed to initialize demo data", e);
            throw new RuntimeException("Demo data initialization failed", e);
        }
    }

    private void createWeightEntries(UUID userId, OffsetDateTime baseTime) {
        // Generate 30 days of weight data, trending downward (weight loss)
        // Start from 30 days ago, daily measurements
        double startWeight = 82.5;
        double dailyVariance = 0.3; // kg of daily fluctuation

        for (int daysAgo = 29; daysAgo >= 0; daysAgo--) {
            OffsetDateTime measuredAt = baseTime.minusDays(daysAgo);

            // Overall trend: losing ~0.15kg per day, plus random variation
            double trendDownward = (29 - daysAgo) * 0.15;
            double variance = (Math.random() - 0.5) * dailyVariance;
            double weight = startWeight - trendDownward + variance;

            WeightEntryEntity entry = new WeightEntryEntity(UUID.randomUUID(), userId, measuredAt,
                    Math.round(weight * 100.0) / 100.0, "Daily weigh-in");
            weightRepo.save(entry);
        }
    }

    private void createWeightGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        // Progress snapshots over ~8 weeks to support weekly/monthly summary testing.
        double[] currentWeights = { 82.1, 81.5, 80.9, 80.4, 79.8, 79.1, 78.4, 77.8, 77.2 };
        int[] progressPercents = { 8, 16, 24, 33, 45, 57, 68, 79, 88 };

        for (int i = 0; i < currentWeights.length; i++) {
            OffsetDateTime ts = baseTime.minusDays(56 - (i * 7));
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(currentWeights[i]), progressPercents[i], true,
                    Math.max(0,
                            (int) java.time.temporal.ChronoUnit.DAYS.between(baseTime,
                                    goal.getTargetDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC))),
                    ts, ts, ts);
            progress.setMilestonesCompleted(progressPercents[i] / 5);
            progress.setMilestoneDetails(
                    List.of(Map.of("percentage", (progressPercents[i] / 5) * 5, "completed_at", ts.toString())));
            goalProgressRepo.save(progress);
        }
    }

    private void createActivityGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        int[] activityDays = { 2, 3, 3, 4, 4, 5 };
        int[] progressPercents = { 40, 60, 60, 80, 80, 100 };

        for (int i = 0; i < activityDays.length; i++) {
            OffsetDateTime ts = baseTime.minusDays(35 - (i * 7));
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(activityDays[i]), progressPercents[i], true,
                    Math.max(0,
                            (int) java.time.temporal.ChronoUnit.DAYS.between(baseTime,
                                    goal.getTargetDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC))),
                    ts, ts, ts);
            progress.setMilestonesCompleted(progressPercents[i] / 5);
            progress.setMilestoneDetails(
                    List.of(Map.of("percentage", (progressPercents[i] / 5) * 5, "completed_at", ts.toString())));
            goalProgressRepo.save(progress);
        }
    }

    private void createArchivedGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        int[] activityDays = { 2, 3, 3, 4, 5 };
        int[] progressPercents = { 40, 55, 60, 75, 100 };

        for (int i = 0; i < activityDays.length; i++) {
            OffsetDateTime ts = baseTime.minusDays(110 - (i * 10));
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(activityDays[i]), progressPercents[i], true, 0, ts, ts, ts);
            progress.setMilestonesCompleted(progressPercents[i] / 5);
            progress.setMilestoneDetails(
                    List.of(Map.of("percentage", (progressPercents[i] / 5) * 5, "completed_at", ts.toString())));
            goalProgressRepo.save(progress);
        }
    }

    private void createActivityCheckins(UUID userId, OffsetDateTime baseTime) {
        String[] activityTypes = { "cardio", "strength", "walking", "cycling", "mobility" };
        String[] intensities = { "low", "medium", "high" };

        for (int daysAgo = 35; daysAgo >= 0; daysAgo--) {
            int sessions = (daysAgo % 6 == 0) ? 0 : (daysAgo % 2 == 0 ? 1 : 2);
            for (int s = 0; s < sessions; s++) {
                OffsetDateTime ts = baseTime.minusDays(daysAgo).withHour(7 + (s * 10)).withMinute(15).withSecond(0)
                        .withNano(0);
                ActivityCheckinEntity entry = new ActivityCheckinEntity(UUID.randomUUID(), userId,
                        activityTypes[(daysAgo + s) % activityTypes.length], 25 + ((daysAgo + s) % 5) * 10,
                        intensities[(daysAgo + s) % intensities.length], "Demo activity check-in", ts, ts, ts);
                activityRepo.save(entry);
            }
        }
    }

    private void createNutritionPreferences(UUID userId, OffsetDateTime now) {
        // Create nutrition preferences with demo macro targets
        Map<String, Object> mealTimes = Map.of("breakfast", "07:00", "lunch", "12:30", "dinner", "18:30");

        NutritionPreferencesEntity prefs = new NutritionPreferencesEntity(userId, "Europe/Tallinn", 2000, // calorieTargetKcal
                new BigDecimal("150"), // proteinTargetG
                new BigDecimal("200"), // carbsTargetG
                new BigDecimal("67"), // fatsTargetG
                (short) 3, // mealsPerDay
                (short) 1, // snacksPerDay
                mealTimes, now, now);
        nutritionPreferencesRepo.save(prefs);
    }

    private void createMealPlans(UUID userId, OffsetDateTime now) {
        // Get available recipes from the catalog
        List<RecipeCatalogModels.RecipeListItem> recipes = recipeQueryRepo.search(
                new RecipeCatalogModels.RecipeSearchFilters(null, null, null, null, null, null, null, null, null, 100));

        if (recipes.isEmpty()) {
            log.warn("No recipes available in catalog, skipping meal plans");
            return;
        }

        // Create 2 meal plans: one from 7 days ago, one starting today
        LocalDate today = LocalDate.now();

        // Plan 1: Last week (7 days ago to yesterday)
        createSingleMealPlan(userId, today.minusDays(7), 7, recipes, now);

        // Plan 2: Current week (today onwards)
        createSingleMealPlan(userId, today, 7, recipes, now);
    }

    private void createSingleMealPlan(UUID userId, LocalDate startDate, int numDays,
            List<RecipeCatalogModels.RecipeListItem> recipes, OffsetDateTime now) {
        LocalDate endDate = startDate.plus(numDays - 1, ChronoUnit.DAYS);
        OffsetDateTime createdAt = OffsetDateTime.of(startDate.atStartOfDay(), ZoneOffset.of("+02:00"));

        MealPlanEntity plan = new MealPlanEntity(UUID.randomUUID(), userId, "weekly", startDate, endDate, "ACTIVE",
                "DEMO", createdAt, now);
        MealPlanEntity savedPlan = mealPlanRepo.save(plan);

        // Add 3 meals per day (breakfast, lunch, dinner)
        String[] mealTypes = { "BREAKFAST", "LUNCH", "DINNER" };
        for (int day = 0; day < numDays; day++) {
            LocalDate dayDate = startDate.plus(day, ChronoUnit.DAYS);
            for (int mealIdx = 0; mealIdx < mealTypes.length; mealIdx++) {
                // Pick a recipe (cycling through available recipes)
                RecipeCatalogModels.RecipeListItem recipe = recipes
                        .get((day * mealTypes.length + mealIdx) % recipes.size());

                OffsetDateTime mealCreatedAt = OffsetDateTime.of(dayDate.atStartOfDay(), ZoneOffset.of("+02:00"));

                MealPlanMealEntity meal = new MealPlanMealEntity(UUID.randomUUID(), savedPlan.getId(), dayDate,
                        mealTypes[mealIdx], recipe.id(), mealIdx, mealCreatedAt, now);
                mealPlanMealRepo.save(meal);
            }
        }
    }

    private void createMealLogs(UUID userId, OffsetDateTime now) {
        // Get available recipes from catalog for variation
        List<RecipeCatalogModels.RecipeListItem> recipes = recipeQueryRepo.search(
                new RecipeCatalogModels.RecipeSearchFilters(null, null, null, null, null, null, null, null, null, 100));

        if (recipes.isEmpty()) {
            log.warn("No recipes available, skipping meal logs");
            return;
        }

        LocalDate today = LocalDate.now();

        // Pre-define meal times and types
        Map<String, LocalTime> mealTimesMap = Map.of("BREAKFAST", LocalTime.of(7, 30), "LUNCH", LocalTime.of(12, 30),
                "SNACK", LocalTime.of(15, 0), "DINNER", LocalTime.of(19, 0));

        // Create 30 days of meal logs with realistic patterns
        for (int daysAgo = 29; daysAgo >= 0; daysAgo--) {
            LocalDate mealDate = today.minusDays(daysAgo);
            int dayOfWeek = mealDate.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
            boolean isWeekend = dayOfWeek >= 6;

            // Weekday patterns: more consistent, weekends more varied
            // Some days skip breakfast, some add snacks
            boolean skipBreakfast = (daysAgo % 5 == 0); // Skip breakfast every 5 days
            boolean addSnack = (daysAgo % 3 == 1 || isWeekend); // Add snack occasionally and on weekends

            // Log breakfast (unless skipped)
            if (!skipBreakfast) {
                logMealVariation(userId, mealDate, "BREAKFAST", mealTimesMap, recipes, daysAgo);
            }

            // Log lunch
            logMealVariation(userId, mealDate, "LUNCH", mealTimesMap, recipes, daysAgo + 1);

            // Log snack (if applicable)
            if (addSnack) {
                // Mix in some custom snacks for variety
                if (daysAgo % 4 == 0) {
                    logCustomSnack(userId, mealDate, mealTimesMap.get("SNACK"));
                } else {
                    logMealVariation(userId, mealDate, "SNACK", mealTimesMap, recipes, daysAgo + 2);
                }
            }

            // Log dinner
            logMealVariation(userId, mealDate, "DINNER", mealTimesMap, recipes, daysAgo + 3);
        }

        log.info("Meal intake logs created for 30 days with varied patterns");
    }

    private void logMealVariation(UUID userId, LocalDate mealDate, String mealType, Map<String, LocalTime> mealTimesMap,
            List<RecipeCatalogModels.RecipeListItem> recipes, int dayVariant) {
        try {
            // Vary recipes based on day
            RecipeCatalogModels.RecipeListItem recipe = recipes.get(dayVariant % recipes.size());

            // Vary servings: 80% of 1, 50% of 1.2, 30% of 0.8, 20% of 1.5
            BigDecimal servings;
            int variation = dayVariant % 10;
            if (variation < 5) {
                servings = BigDecimal.ONE;
            } else if (variation < 7) {
                servings = new BigDecimal("1.2");
            } else if (variation < 9) {
                servings = new BigDecimal("0.8");
            } else {
                servings = new BigDecimal("1.5");
            }

            LocalTime mealTime = mealTimesMap.get(mealType);
            OffsetDateTime mealDateTime = OffsetDateTime.of(mealDate.atTime(mealTime), ZoneOffset.of("+02:00"));

            mealIntakeLogService.logMealFromRecipe(userId, recipe.id(), servings, mealType, mealTime, mealDateTime);
            log.debug("Logged {} ({} servings) for {}", mealType, servings, mealDate);
        } catch (Exception e) {
            log.warn("Failed to log meal {} for date {}: {}", mealType, mealDate, e.getMessage());
        }
    }

    private void logCustomSnack(UUID userId, LocalDate mealDate, LocalTime snackTime) {
        try {
            // Define realistic snack options
            String[] snacks = { "Greek yogurt with berries", "Protein bar", "Apple with almond butter", "Mixed nuts",
                    "Energy drink", "Granola bar", "Banana", "Cheese and crackers" };

            double[] caloriesKcal = { 180, 200, 220, 190, 150, 160, 105, 200 };
            double[] proteins = { 15, 20, 8, 6, 2, 5, 1, 8 };
            double[] carbs = { 20, 22, 25, 8, 35, 22, 27, 18 };
            double[] fats = { 5, 8, 10, 16, 0, 6, 0, 10 };

            int snackIdx = Math.random() < 0.5 ? 0 : (int) (Math.random() * snacks.length);

            OffsetDateTime snackDateTime = OffsetDateTime.of(mealDate.atTime(snackTime), ZoneOffset.of("+02:00"));

            mealIntakeLogService.logCustomMeal(userId, snacks[snackIdx], "SNACK",
                    BigDecimal.valueOf(caloriesKcal[snackIdx]), BigDecimal.valueOf(proteins[snackIdx]),
                    BigDecimal.valueOf(carbs[snackIdx]), BigDecimal.valueOf(fats[snackIdx]), "Demo snack", snackTime,
                    snackDateTime);

            log.debug("Logged custom snack: {}", snacks[snackIdx]);
        } catch (Exception e) {
            log.warn("Failed to log custom snack for date {}: {}", mealDate, e.getMessage());
        }
    }
}
