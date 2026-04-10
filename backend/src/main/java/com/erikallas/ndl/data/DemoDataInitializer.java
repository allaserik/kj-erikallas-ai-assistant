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
            GoalEntity activeWeightGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.WEIGHT_LOSS, 75.0,
                    null, now.toLocalDate().plusDays(32), "Lose 7kg over 3 months", true, now.minusDays(58),
                    now.minusDays(58));
            goalRepo.save(activeWeightGoal);

            GoalEntity activeFitnessGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.IMPROVE_FITNESS,
                    null, 5, now.toLocalDate().plusDays(23), "Reach 5 active days per week", true, now.minusDays(37),
                    now.minusDays(37));
            goalRepo.save(activeFitnessGoal);

            GoalEntity activeMuscleGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.BUILD_MUSCLE, null,
                    3, now.toLocalDate().plusDays(40), "Add gym strength sessions 3x/week", true, now.minusDays(20),
                    now.minusDays(20));
            goalRepo.save(activeMuscleGoal);

            GoalEntity archivedGoal = new GoalEntity(UUID.randomUUID(), DEMO_USER_ID, GoalType.ENHANCE_ENDURANCE, null,
                    4, now.toLocalDate().minusDays(25), "Run 5km without stopping", false, now.minusDays(120),
                    now.minusDays(25));
            goalRepo.save(archivedGoal);
            log.info("Created demo goals (3 active + 1 archived success story)");

            // 4. Create 90 days of weight entries with realistic progression
            createWeightEntries(DEMO_USER_ID, now);
            log.info("Created 90 days of weight entries");

            // 5. Create goal progress history for trend charts and analytics
            createWeightGoalProgressHistory(activeWeightGoal, now);
            createActivityGoalProgressHistory(activeFitnessGoal, now);
            createMuscleGoalProgressHistory(activeMuscleGoal, now);
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
        // Generate 90 days of weight data, trending downward (weight loss goal)
        // Start from 90 days ago: ~84.5 kg, target trend to ~77.5 today
        double startWeight = 84.5;
        double totalLoss = 7.0; // kg lost over 90 days
        double dailyVariance = 0.25; // kg of daily fluctuation

        for (int daysAgo = 89; daysAgo >= 0; daysAgo--) {
            OffsetDateTime measuredAt = baseTime.minusDays(daysAgo);

            // Linear trend + small random noise
            double progress = (89 - daysAgo) / 89.0;
            double variance = (Math.random() - 0.5) * dailyVariance;
            double weight = startWeight - (totalLoss * progress) + variance;

            WeightEntryEntity entry = new WeightEntryEntity(UUID.randomUUID(), userId, measuredAt,
                    Math.round(weight * 100.0) / 100.0, "Daily weigh-in");
            weightRepo.save(entry);
        }
    }

    private void createWeightGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        // Snapshots from day goal was created (58d ago) up to 1 week ago.
        // Baseline: 84.5 kg, target: 75.0 kg → total delta = 9.5 kg
        // Progress % = (84.5 - current) / 9.5 * 100
        double[] currentWeights = { 84.2, 83.6, 83.0, 82.3, 81.5, 80.7, 79.9, 79.2, 78.5 };
        // days-ago for each snapshot: 56, 49, 42, 35, 28, 21, 14, 7, 0 (today-ish)
        int[] daysAgoList = { 56, 49, 42, 35, 28, 21, 14, 7, 1 };

        for (int i = 0; i < currentWeights.length; i++) {
            int pct = (int) Math.round((84.5 - currentWeights[i]) / 9.5 * 100);
            OffsetDateTime ts = baseTime.minusDays(daysAgoList[i]);
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(ts.toLocalDate(), goal.getTargetDate());
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(currentWeights[i]), pct, true, (int) Math.max(0, daysLeft), ts, ts,
                    ts);
            progress.setMilestonesCompleted(pct / 5);
            progress.setMilestoneDetails(List.of(Map.of("percentage", (pct / 5) * 5, "completed_at", ts.toString())));
            goalProgressRepo.save(progress);
        }
    }

    private void createActivityGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        int[] activityDays = { 2, 3, 3, 4, 4, 5 };
        int[] progressPercents = { 40, 60, 60, 80, 80, 100 };

        for (int i = 0; i < activityDays.length; i++) {
            OffsetDateTime ts = baseTime.minusDays(35 - (i * 7));
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(ts.toLocalDate(), goal.getTargetDate());
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(activityDays[i]), progressPercents[i], true,
                    (int) Math.max(0, daysLeft), ts, ts, ts);
            progress.setMilestonesCompleted(progressPercents[i] / 5);
            progress.setMilestoneDetails(
                    List.of(Map.of("percentage", (progressPercents[i] / 5) * 5, "completed_at", ts.toString())));
            goalProgressRepo.save(progress);
        }
    }

    private void createMuscleGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        // BUILD_MUSCLE: target 3 days/week, ~20 days in, ~33% progress
        // Just one progress snapshot at 7 days ago showing 1 out of 3 days completed
        int[] activityDays = { 1 };
        int[] progressPercents = { 33 };
        int[] daysAgoList = { 7 };

        for (int i = 0; i < activityDays.length; i++) {
            OffsetDateTime ts = baseTime.minusDays(daysAgoList[i]);
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(ts.toLocalDate(), goal.getTargetDate());
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(activityDays[i]), progressPercents[i], true,
                    (int) Math.max(0, daysLeft), ts, ts, ts);
            progress.setMilestonesCompleted(progressPercents[i] / 5);
            progress.setMilestoneDetails(
                    List.of(Map.of("percentage", (progressPercents[i] / 5) * 5, "completed_at", ts.toString())));
            goalProgressRepo.save(progress);
        }
    }

    private void createArchivedGoalProgressHistory(GoalEntity goal, OffsetDateTime baseTime) {
        // ENHANCE_ENDURANCE success story: started ~120d ago, completed ~25d ago at
        // 100%
        // 4 activity days/week target; progress snapshots every ~20 days
        int[] activityDays = { 1, 2, 3, 3, 4 };
        int[] progressPercents = { 25, 50, 75, 88, 100 };
        int[] daysAgoList = { 110, 90, 70, 50, 25 };

        for (int i = 0; i < activityDays.length; i++) {
            OffsetDateTime ts = baseTime.minusDays(daysAgoList[i]);
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(ts.toLocalDate(), goal.getTargetDate());
            GoalProgressEntity progress = new GoalProgressEntity(UUID.randomUUID(), goal.getId(), goal.getUserId(),
                    java.math.BigDecimal.valueOf(activityDays[i]), progressPercents[i], true,
                    (int) Math.max(0, daysLeft), ts, ts, ts);
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
        // Controlled custom meal logs — predictable macros so the nutrition bar chart
        // shows a clear pattern relative to the 2000 kcal / 150p / 200c / 67f target.
        // Weekdays: ~1800 kcal (intentional deficit matching weight-loss goal)
        // Weekends: ~2200 kcal (slightly over target)
        LocalDate today = LocalDate.now();

        // Meal templates [kcal, proteinG, carbsG, fatsG]
        // Weekday meals — total ~1800 kcal, ~155g protein, ~195g carbs, ~60g fats
        double[][] wdBreakfast = { { 380, 32, 40, 10 }, // Scrambled eggs + oats
                { 340, 28, 38, 9 }, // Greek yogurt parfait
                { 360, 30, 42, 8 }, // Protein smoothie + toast
        };
        double[][] wdLunch = { { 520, 45, 55, 13 }, // Chicken rice bowl
                { 490, 42, 52, 12 }, // Tuna salad wrap
                { 510, 44, 54, 13 }, // Turkey & quinoa
        };
        double[][] wdDinner = { { 580, 50, 62, 16 }, // Salmon + sweet potato
                { 560, 48, 60, 15 }, // Lean beef stir-fry
                { 600, 52, 64, 17 }, // Chicken pasta
        };
        double[][] wdSnack = { { 180, 15, 20, 5 }, // Greek yogurt
                { 200, 20, 22, 8 }, // Protein bar
                { 170, 13, 18, 6 }, // Cottage cheese
        };

        // Weekend meals — total ~2200 kcal, ~160g protein, ~245g carbs, ~78g fats
        double[][] weBreakfast = { { 480, 35, 52, 14 }, // Full breakfast
                { 450, 32, 50, 13 }, // Pancakes + eggs
        };
        double[][] weLunch = { { 660, 50, 72, 20 }, // Burger + salad
                { 640, 48, 70, 19 }, // Pizza slice + side
        };
        double[][] weDinner = { { 720, 55, 80, 22 }, // BBQ chicken + rice
                { 700, 52, 78, 21 }, // Pasta bolognese
        };
        double[][] weSnack = { { 260, 18, 30, 9 }, // Granola bar + milk
                { 240, 16, 28, 8 }, // Banana + PB
        };

        LocalTime breakfastTime = LocalTime.of(7, 30);
        LocalTime lunchTime = LocalTime.of(12, 30);
        LocalTime snackTime = LocalTime.of(15, 0);
        LocalTime dinnerTime = LocalTime.of(19, 0);

        String[] breakfastNames = { "Scrambled Eggs with Oats", "Greek Yogurt Parfait", "Protein Smoothie & Toast",
                "Full Breakfast", "Pancakes with Eggs" };
        String[] lunchNames = { "Chicken Rice Bowl", "Tuna Salad Wrap", "Turkey & Quinoa", "Burger & Salad",
                "Pizza & Side Salad" };
        String[] dinnerNames = { "Salmon & Sweet Potato", "Lean Beef Stir-fry", "Chicken Pasta", "BBQ Chicken & Rice",
                "Pasta Bolognese" };
        String[] snackNames = { "Greek Yogurt", "Protein Bar", "Cottage Cheese", "Granola Bar & Milk",
                "Banana with Peanut Butter" };

        for (int daysAgo = 29; daysAgo >= 0; daysAgo--) {
            LocalDate mealDate = today.minusDays(daysAgo);
            int dow = mealDate.getDayOfWeek().getValue(); // 1=Mon, 7=Sun
            boolean isWeekend = dow >= 6;
            int v = daysAgo % 3; // variant index cycling 0-1-2

            try {
                double[] bf, lu, sn, di;
                String bfName, luName, snName, diName;
                if (isWeekend) {
                    int wv = daysAgo % 2;
                    bf = weBreakfast[wv];
                    bfName = breakfastNames[3 + wv];
                    lu = weLunch[wv];
                    luName = lunchNames[3 + wv];
                    di = weDinner[wv];
                    diName = dinnerNames[3 + wv];
                    sn = weSnack[wv];
                    snName = snackNames[3 + wv];
                } else {
                    bf = wdBreakfast[v];
                    bfName = breakfastNames[v];
                    lu = wdLunch[v];
                    luName = lunchNames[v];
                    di = wdDinner[v];
                    diName = dinnerNames[v];
                    sn = wdSnack[v];
                    snName = snackNames[v];
                }

                ZoneOffset tz = ZoneOffset.of("+02:00");
                // Skip breakfast ~1 in 6 weekdays for realism
                if (!isWeekend && daysAgo % 6 != 0) {
                    mealIntakeLogService.logCustomMeal(userId, bfName, "BREAKFAST", BigDecimal.valueOf(bf[0]),
                            BigDecimal.valueOf(bf[1]), BigDecimal.valueOf(bf[2]), BigDecimal.valueOf(bf[3]), null,
                            breakfastTime, OffsetDateTime.of(mealDate.atTime(breakfastTime), tz));
                } else if (isWeekend) {
                    mealIntakeLogService.logCustomMeal(userId, bfName, "BREAKFAST", BigDecimal.valueOf(bf[0]),
                            BigDecimal.valueOf(bf[1]), BigDecimal.valueOf(bf[2]), BigDecimal.valueOf(bf[3]), null,
                            breakfastTime, OffsetDateTime.of(mealDate.atTime(breakfastTime), tz));
                }

                mealIntakeLogService.logCustomMeal(userId, luName, "LUNCH", BigDecimal.valueOf(lu[0]),
                        BigDecimal.valueOf(lu[1]), BigDecimal.valueOf(lu[2]), BigDecimal.valueOf(lu[3]), null,
                        lunchTime, OffsetDateTime.of(mealDate.atTime(lunchTime), tz));

                // Snack on weekends always, weekdays every other day
                if (isWeekend || daysAgo % 2 == 0) {
                    mealIntakeLogService.logCustomMeal(userId, snName, "SNACK", BigDecimal.valueOf(sn[0]),
                            BigDecimal.valueOf(sn[1]), BigDecimal.valueOf(sn[2]), BigDecimal.valueOf(sn[3]), null,
                            snackTime, OffsetDateTime.of(mealDate.atTime(snackTime), tz));
                }

                mealIntakeLogService.logCustomMeal(userId, diName, "DINNER", BigDecimal.valueOf(di[0]),
                        BigDecimal.valueOf(di[1]), BigDecimal.valueOf(di[2]), BigDecimal.valueOf(di[3]), null,
                        dinnerTime, OffsetDateTime.of(mealDate.atTime(dinnerTime), tz));

            } catch (Exception e) {
                log.warn("Failed to log meals for date {}: {}", mealDate, e.getMessage());
            }
        }

        log.info("Meal intake logs created for 30 days (controlled custom macros)");
    }
}
