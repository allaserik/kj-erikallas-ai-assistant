package com.erikallas.ndl.nutrition.mealplan;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMealPlanRecipeSelectorRepository implements MealPlanRecipeSelectorRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMealPlanRecipeSelectorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RecipeCandidate> findCandidates(
            String mealType,
            List<String> preferredCuisines,
            List<String> dietaryTags,
            List<String> blockedIngredientTerms,
            Set<UUID> excludedRecipeIds,
            int limit) {
        StringBuilder query = new StringBuilder("""
                SELECT
                    r.id,
                    r.title,
                    r.meal,
                    r.cuisine,
                    r.time_minutes,
                    COALESCE(r.calories_kcal, 0) AS calories_kcal,
                    COALESCE(r.protein_g, 0) AS protein_g,
                    COALESCE(r.carbs_g, 0) AS carbs_g,
                    COALESCE(r.fats_g, 0) AS fats_g
                FROM recipes r
                WHERE 1=1
                """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", Math.max(1, Math.min(limit, 100)));

        if (mealType != null && !mealType.isBlank()) {
            query.append("AND r.meal = :mealType\n");
            params.addValue("mealType", mealType);
        }

        if (preferredCuisines != null && !preferredCuisines.isEmpty()) {
            List<String> cuisines = preferredCuisines.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList();
            if (!cuisines.isEmpty()) {
                query.append("AND lower(COALESCE(r.cuisine, '')) IN (:cuisines)\n");
                params.addValue("cuisines", cuisines);
            }
        }

        if (dietaryTags != null && !dietaryTags.isEmpty()) {
            List<String> tags = dietaryTags.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .toList();
            if (!tags.isEmpty()) {
                query.append("""
                        AND EXISTS (
                            SELECT 1
                            FROM unnest(r.dietary_tags) AS tag
                            WHERE tag IN (:dietaryTags)
                        )
                        """);
                params.addValue("dietaryTags", tags);
            }
        }

        if (excludedRecipeIds != null && !excludedRecipeIds.isEmpty()) {
            query.append("AND r.id NOT IN (:excludedRecipeIds)\n");
            params.addValue("excludedRecipeIds", excludedRecipeIds);
        }

        List<String> blocked = blockedIngredientTerms == null ? List.of() : blockedIngredientTerms.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .toList();
        for (int i = 0; i < blocked.size(); i++) {
            String key = "blocked" + i;
            query.append("""
                    AND NOT EXISTS (
                        SELECT 1
                        FROM recipe_ingredients ri
                        WHERE ri.recipe_id = r.id
                          AND lower(ri.ingredient_name) LIKE :%s
                    )
                    """.formatted(key));
            params.addValue(key, "%" + blocked.get(i) + "%");
        }

        query.append("""
                ORDER BY random()
                LIMIT :limit
                """);

        return jdbc.query(query.toString(), params, (rs, rowNum) -> new RecipeCandidate(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("meal"),
                rs.getString("cuisine"),
                rs.getInt("time_minutes"),
                rs.getBigDecimal("calories_kcal"),
                rs.getBigDecimal("protein_g"),
                rs.getBigDecimal("carbs_g"),
                rs.getBigDecimal("fats_g")));
    }
}
