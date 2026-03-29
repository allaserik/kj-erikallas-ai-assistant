package com.erikallas.ndl.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "nutrition.raw-import", name = "enabled", havingValue = "true")
public class RawRecipeImportInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RawRecipeImportInitializer.class);
    private static final String SOURCE = "raw-recipes-csv";
    private static final Pattern QUOTED_ITEM = Pattern.compile("'([^']*)'");

    private final NamedParameterJdbcTemplate jdbc;
    private final int limit;

    public RawRecipeImportInitializer(NamedParameterJdbcTemplate jdbc,
            @Value("${nutrition.raw-import.limit:500}") int limit) {
        this.jdbc = jdbc;
        this.limit = Math.max(1, limit);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM recipes WHERE source = :source",
                Map.of("source", SOURCE), Integer.class);
        if (existing != null && existing >= limit) {
            log.info("Raw recipe import skipped: source={} already has {} recipes (limit={})", SOURCE, existing, limit);
            return;
        }

        Map<String, NutritionPer100g> nutritionByFood = loadNutritionDataset();
        Map<String, UUID> ingredientIdCache = new HashMap<>();
        int imported = 0;
        int matchedNutrition = 0;

        ClassPathResource rawRecipesResource = new ClassPathResource("recipe-csv/RAW_recipes.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(rawRecipesResource.getInputStream(), StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                if (imported >= limit) {
                    break;
                }

                String externalId = row.get("id");
                String title = sanitizeTitle(safe(row.get("name")));
                if (externalId.isBlank() || title.isBlank()) {
                    continue;
                }

                List<String> tags = parseQuotedList(row.get("tags"));
                List<String> ingredients = parseQuotedList(row.get("ingredients"));
                List<String> steps = parseQuotedList(row.get("steps"));
                if (ingredients.isEmpty() || steps.isEmpty()) {
                    continue;
                }

                RecipeNutritionAcc nutritionAcc = new RecipeNutritionAcc();
                List<IngredientImportItem> ingredientItems = new ArrayList<>();

                int sortOrder = 1;
                for (String ingredientLabel : ingredients) {
                    String cleaned = ingredientLabel.trim();
                    if (cleaned.isBlank()) {
                        continue;
                    }

                    UUID ingredientId = findOrCreateIngredient(cleaned, ingredientIdCache);
                    NutritionPer100g n = matchNutrition(cleaned, nutritionByFood);
                    if (n != null) {
                        upsertIngredientNutrition(ingredientId, n);
                        nutritionAcc.add(n);
                        matchedNutrition++;
                    }
                    ingredientItems.add(new IngredientImportItem(ingredientId, cleaned, sortOrder++));
                }
                if (ingredientItems.isEmpty()) {
                    continue;
                }

                Integer minutes = parseInteger(row.get("minutes"));
                BigDecimal rawCalories = parseFirstNutritionValue(row.get("nutrition"));
                BigDecimal recipeCalories = nutritionAcc.calories.compareTo(BigDecimal.ZERO) > 0 ? nutritionAcc.calories
                        : rawCalories;
                BigDecimal recipeProtein = nutritionAcc.protein.compareTo(BigDecimal.ZERO) > 0 ? nutritionAcc.protein
                        : null;
                BigDecimal recipeCarbs = nutritionAcc.carbs.compareTo(BigDecimal.ZERO) > 0 ? nutritionAcc.carbs : null;
                BigDecimal recipeFats = nutritionAcc.fats.compareTo(BigDecimal.ZERO) > 0 ? nutritionAcc.fats : null;

                UUID recipeId = upsertRecipe(externalId, title, guessCuisine(tags), guessMeal(tags), 1,
                        safe(row.get("description")), minutes != null ? minutes : 30, guessDifficulty(tags),
                        toTagArray(tags), recipeCalories, recipeProtein, recipeCarbs, recipeFats);

                replaceRecipeIngredients(recipeId, ingredientItems);
                replaceRecipeSteps(recipeId, steps);

                imported++;
            }
        }

        log.info("Raw recipe import done: imported={}, limit={}, matchedNutritionRows={}", imported, limit,
                matchedNutrition);
    }

    private Map<String, NutritionPer100g> loadNutritionDataset() throws Exception {
        Map<String, NutritionPer100g> map = new HashMap<>();
        ClassPathResource nutritionResource = new ClassPathResource("recipe-csv/cleaned_nutrition_dataset_per100g.csv");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(nutritionResource.getInputStream(), StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                String key = normalizeIngredientName(safe(row.get("food_normalized")));
                if (key.isBlank()) {
                    continue;
                }
                map.put(key,
                        new NutritionPer100g(parseDecimal(row.get("Calories (kcal per 100g)")),
                                parseDecimal(row.get("Protein (g per 100g)")),
                                parseDecimal(row.get("Carbohydrates (g per 100g)")),
                                parseDecimal(row.get("Fat (g per 100g)"))));
            }
        }
        return map;
    }

    private UUID upsertRecipe(String externalId, String title, String cuisine, String meal, int servings,
            String summary, int timeMinutes, String difficultyLevel, String[] tags, BigDecimal caloriesKcal,
            BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatsG) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("externalId", externalId)
                .addValue("title", title).addValue("cuisine", cuisine).addValue("meal", meal)
                .addValue("servings", servings).addValue("summary", summary).addValue("timeMinutes", timeMinutes)
                .addValue("difficultyLevel", difficultyLevel).addValue("dietaryTags", tags).addValue("source", SOURCE)
                .addValue("img", null).addValue("caloriesKcal", caloriesKcal).addValue("proteinG", proteinG)
                .addValue("carbsG", carbsG).addValue("fatsG", fatsG);

        UUID existingId = jdbc.query("""
                SELECT id
                FROM recipes
                WHERE source = :source
                  AND external_id = :externalId
                LIMIT 1
                """, params, rs -> rs.next() ? rs.getObject("id", UUID.class) : null);

        if (existingId != null) {
            params.addValue("id", existingId);
            jdbc.update("""
                    UPDATE recipes
                    SET title = :title,
                        cuisine = :cuisine,
                        meal = :meal,
                        servings = :servings,
                        summary = :summary,
                        time_minutes = :timeMinutes,
                        difficulty_level = :difficultyLevel,
                        dietary_tags = :dietaryTags,
                        img = :img,
                        calories_kcal = :caloriesKcal,
                        protein_g = :proteinG,
                        carbs_g = :carbsG,
                        fats_g = :fatsG,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                    """, params);
            return existingId;
        }

        UUID createdId = jdbc.queryForObject("""
                INSERT INTO recipes (
                    external_id, title, cuisine, meal, servings, summary, time_minutes,
                    difficulty_level, dietary_tags, source, img,
                    calories_kcal, protein_g, carbs_g, fats_g
                ) VALUES (
                    :externalId, :title, :cuisine, :meal, :servings, :summary, :timeMinutes,
                    :difficultyLevel, :dietaryTags, :source, :img,
                    :caloriesKcal, :proteinG, :carbsG, :fatsG
                )
                RETURNING id
                """, params, UUID.class);

        if (createdId == null) {
            throw new IllegalStateException("Failed to insert recipe id for external_id=" + externalId);
        }
        return createdId;
    }

    private void replaceRecipeIngredients(UUID recipeId, List<IngredientImportItem> ingredients) {
        jdbc.update("DELETE FROM recipe_ingredients WHERE recipe_id = :recipeId", Map.of("recipeId", recipeId));
        for (IngredientImportItem item : ingredients) {
            jdbc.update("""
                    INSERT INTO recipe_ingredients (
                        id, recipe_id, ingredient_id, ingredient_name, quantity, unit, sort_order
                    ) VALUES (
                        gen_random_uuid(), :recipeId, :ingredientId, :ingredientName, :quantity, :unit, :sortOrder
                    )
                    """,
                    new MapSqlParameterSource().addValue("recipeId", recipeId)
                            .addValue("ingredientId", item.ingredientId()).addValue("ingredientName", item.label())
                            .addValue("quantity", BigDecimal.valueOf(100)).addValue("unit", "g")
                            .addValue("sortOrder", item.sortOrder()));
        }
    }

    private void replaceRecipeSteps(UUID recipeId, List<String> steps) {
        jdbc.update("DELETE FROM recipe_steps WHERE recipe_id = :recipeId", Map.of("recipeId", recipeId));
        int stepNumber = 1;
        for (String step : steps) {
            String description = step.trim();
            if (description.isBlank()) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO recipe_steps (
                        id, recipe_id, step_number, step, description, ingredient_refs
                    ) VALUES (
                        gen_random_uuid(), :recipeId, :stepNumber, :stepName, :description, :ingredientRefs
                    )
                    """,
                    new MapSqlParameterSource().addValue("recipeId", recipeId).addValue("stepNumber", stepNumber)
                            .addValue("stepName", "Step " + stepNumber).addValue("description", description)
                            .addValue("ingredientRefs", new String[0]));
            stepNumber++;
        }
    }

    private UUID findOrCreateIngredient(String label, Map<String, UUID> cache) {
        String key = normalizeIngredientName(label);
        if (key.isBlank()) {
            key = normalizeIngredientName("unknown ingredient");
        }
        UUID cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        UUID existing = jdbc.query("""
                SELECT id
                FROM ingredients
                WHERE lower(name) = :name
                LIMIT 1
                """, Map.of("name", key), rs -> rs.next() ? rs.getObject("id", UUID.class) : null);
        if (existing != null) {
            cache.put(key, existing);
            return existing;
        }

        UUID created = jdbc.queryForObject("""
                INSERT INTO ingredients (id, name, category, default_unit)
                VALUES (gen_random_uuid(), :name, :category, :unit)
                RETURNING id
                """,
                new MapSqlParameterSource().addValue("name", key).addValue("category", "other").addValue("unit", "g"),
                UUID.class);
        if (created == null) {
            throw new IllegalStateException("Failed to create ingredient: " + key);
        }
        cache.put(key, created);
        return created;
    }

    private void upsertIngredientNutrition(UUID ingredientId, NutritionPer100g n) {
        jdbc.update("""
                INSERT INTO ingredient_nutrition (
                    ingredient_id, base_quantity, base_unit, calories_kcal, carbs_g, protein_g, fats_g
                ) VALUES (
                    :ingredientId, :baseQuantity, :baseUnit, :calories, :carbs, :protein, :fats
                )
                ON CONFLICT (ingredient_id) DO UPDATE SET
                    calories_kcal = EXCLUDED.calories_kcal,
                    carbs_g = EXCLUDED.carbs_g,
                    protein_g = EXCLUDED.protein_g,
                    fats_g = EXCLUDED.fats_g,
                    updated_at = CURRENT_TIMESTAMP
                """,
                new MapSqlParameterSource().addValue("ingredientId", ingredientId)
                        .addValue("baseQuantity", BigDecimal.valueOf(100)).addValue("baseUnit", "g")
                        .addValue("calories", n.caloriesKcal()).addValue("carbs", n.carbsG())
                        .addValue("protein", n.proteinG()).addValue("fats", n.fatsG()));
    }

    private NutritionPer100g matchNutrition(String ingredientLabel, Map<String, NutritionPer100g> nutritionByFood) {
        String normalized = normalizeIngredientName(ingredientLabel);
        NutritionPer100g exact = nutritionByFood.get(normalized);
        if (exact != null) {
            return exact;
        }
        if (normalized.endsWith("s") && normalized.length() > 4) {
            NutritionPer100g singular = nutritionByFood.get(normalized.substring(0, normalized.length() - 1));
            if (singular != null) {
                return singular;
            }
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length > 1) {
            String head = parts[0] + " " + parts[1];
            NutritionPer100g headMatch = nutritionByFood.get(head);
            if (headMatch != null) {
                return headMatch;
            }
            NutritionPer100g tailMatch = nutritionByFood.get(parts[parts.length - 1]);
            if (tailMatch != null) {
                return tailMatch;
            }
        }
        return null;
    }

    private String normalizeIngredientName(String input) {
        if (input == null) {
            return "";
        }
        String lowered = input.toLowerCase(Locale.ROOT).trim();
        lowered = lowered.replaceAll("\\([^)]*\\)", " ");
        lowered = lowered.replaceAll("[^a-z0-9\\s]", " ");
        lowered = lowered.replaceAll("\\s+", " ").trim();
        return lowered;
    }

    private List<String> parseQuotedList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = QUOTED_ITEM.matcher(raw);
        while (matcher.find()) {
            String item = matcher.group(1);
            if (item != null && !item.isBlank()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private Integer parseInteger(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return Integer.valueOf(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseFirstNutritionValue(String rawList) {
        if (rawList == null || rawList.isBlank()) {
            return null;
        }
        String cleaned = rawList.replace("[", "").replace("]", "").trim();
        if (cleaned.isBlank()) {
            return null;
        }
        String[] parts = cleaned.split(",");
        if (parts.length == 0) {
            return null;
        }
        return parseDecimal(parts[0]);
    }

    private String[] toTagArray(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new String[0];
        }
        Set<String> tags = new HashSet<>();
        for (String t : rawTags) {
            String n = normalizeIngredientName(t).replace(' ', '_');
            if (!n.isBlank() && n.length() <= 40) {
                tags.add(n);
            }
            if (tags.size() >= 12) {
                break;
            }
        }
        return tags.toArray(String[]::new);
    }

    private String guessCuisine(List<String> tags) {
        List<String> cuisines = List.of("italian", "mexican", "indian", "chinese", "thai", "mediterranean", "french",
                "american", "japanese", "korean");
        Set<String> normalized = new HashSet<>();
        for (String t : tags) {
            normalized.add(normalizeIngredientName(t));
        }
        for (String c : cuisines) {
            if (normalized.contains(c)) {
                return c;
            }
        }
        return null;
    }

    private String guessMeal(List<String> tags) {
        Set<String> normalized = new HashSet<>();
        for (String t : tags) {
            normalized.add(normalizeIngredientName(t));
        }
        if (normalized.contains("breakfast") || normalized.contains("brunch")) {
            return "breakfast";
        }
        if (normalized.contains("lunch")) {
            return "lunch";
        }
        if (normalized.contains("dessert") || normalized.contains("snacks") || normalized.contains("snack")) {
            return "snack";
        }
        return "dinner";
    }

    private String guessDifficulty(List<String> tags) {
        Set<String> normalized = new HashSet<>();
        for (String t : tags) {
            normalized.add(normalizeIngredientName(t));
        }
        if (normalized.contains("easy") || normalized.contains("beginner cook")) {
            return "easy";
        }
        if (normalized.contains("hard") || normalized.contains("advanced")) {
            return "hard";
        }
        return "medium";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitizeTitle(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String value = rawTitle.trim().replaceAll("\\s+", " ");
        // Remove leading index-like number prefixes from noisy dataset titles.
        value = value.replaceFirst("^\\d+[\\d\\s_\\-]*", "").trim();
        if (value.isBlank()) {
            return rawTitle.trim().replaceAll("\\s+", " ");
        }
        return value;
    }

    private record NutritionPer100g(BigDecimal caloriesKcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatsG) {
    }

    private record IngredientImportItem(UUID ingredientId, String label, int sortOrder) {
    }

    private static class RecipeNutritionAcc {
        private BigDecimal calories = BigDecimal.ZERO;
        private BigDecimal protein = BigDecimal.ZERO;
        private BigDecimal carbs = BigDecimal.ZERO;
        private BigDecimal fats = BigDecimal.ZERO;

        void add(NutritionPer100g value) {
            calories = calories.add(value.caloriesKcal());
            protein = protein.add(value.proteinG());
            carbs = carbs.add(value.carbsG());
            fats = fats.add(value.fatsG());
        }
    }
}
