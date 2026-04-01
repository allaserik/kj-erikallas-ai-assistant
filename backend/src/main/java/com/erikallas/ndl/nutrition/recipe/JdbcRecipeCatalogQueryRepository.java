package com.erikallas.ndl.nutrition.recipe;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRecipeCatalogQueryRepository implements RecipeCatalogQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRecipeCatalogQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RecipeCatalogModels.RecipeListItem> search(RecipeCatalogModels.RecipeSearchFilters filters) {
        int limit = clamp(filters.limit() == null ? 20 : filters.limit(), 1, 100);
        String q = normalizeBlank(filters.q());
        String cuisine = normalizeBlank(filters.cuisine());
        String meal = normalizeBlank(filters.meal());
        String dietaryTag = normalizeBlank(filters.dietaryTag());
        String allergyTag = normalizeBlank(filters.allergyTag());
        Integer maxTimeMinutes = filters.maxTimeMinutes();
        var maxCaloriesKcal = filters.maxCaloriesKcal();
        var minProteinG = filters.minProteinG();
        var maxProteinG = filters.maxProteinG();

        StringBuilder query = new StringBuilder("""
                WITH nutrition AS (
                    SELECT
                        ri.recipe_id AS recipe_id,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.calories_kcal * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS calories_kcal,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.protein_g * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS protein_g,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.carbs_g * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS carbs_g,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.fats_g * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS fats_g
                    FROM recipe_ingredients ri
                    LEFT JOIN ingredient_nutrition inut ON inut.ingredient_id = ri.ingredient_id
                    GROUP BY ri.recipe_id
                )
                SELECT
                    r.id,
                    r.title,
                    r.cuisine,
                    r.meal,
                    r.servings,
                    r.time_minutes,
                    r.difficulty_level,
                    r.dietary_tags,
                    COALESCE(n.calories_kcal, r.calories_kcal, 0) AS calories_kcal,
                    COALESCE(n.protein_g, r.protein_g, 0) AS protein_g,
                    COALESCE(n.carbs_g, r.carbs_g, 0) AS carbs_g,
                    COALESCE(n.fats_g, r.fats_g, 0) AS fats_g
                FROM recipes r
                LEFT JOIN nutrition n ON n.recipe_id = r.id
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        if (q != null) {
            query.append("""
                    AND (
                        r.title ILIKE ('%' || :q || '%')
                        OR r.cuisine ILIKE ('%' || :q || '%')
                        OR EXISTS (
                            SELECT 1
                            FROM recipe_ingredients sri
                            WHERE sri.recipe_id = r.id
                              AND sri.ingredient_name ILIKE ('%' || :q || '%')
                        )
                    )
                    """);
            params.addValue("q", q);
        }
        if (cuisine != null) {
            query.append("AND r.cuisine = :cuisine\n");
            params.addValue("cuisine", cuisine);
        }
        if (meal != null) {
            query.append("AND r.meal = :meal\n");
            params.addValue("meal", meal);
        }
        if (dietaryTag != null) {
            query.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM unnest(r.dietary_tags) AS tag
                        WHERE tag = CAST(:dietaryTag AS text)
                    )
                    """);
            params.addValue("dietaryTag", dietaryTag);
        }
        if (allergyTag != null) {
            // Filter to only recipes safe for this allergy (must carry the "-free" safe
            // tag)
            query.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM unnest(r.dietary_tags) AS tag
                        WHERE tag = CAST(:allergyTag AS text)
                    )
                    """);
            params.addValue("allergyTag", allergyTag);
        }
        if (maxTimeMinutes != null) {
            query.append("AND r.time_minutes <= :maxTimeMinutes\n");
            params.addValue("maxTimeMinutes", maxTimeMinutes);
        }
        if (maxCaloriesKcal != null) {
            query.append("AND COALESCE(n.calories_kcal, r.calories_kcal, 0) <= :maxCaloriesKcal\n");
            params.addValue("maxCaloriesKcal", maxCaloriesKcal);
        }
        if (minProteinG != null) {
            query.append("AND COALESCE(n.protein_g, r.protein_g, 0) >= :minProteinG\n");
            params.addValue("minProteinG", minProteinG);
        }
        if (maxProteinG != null) {
            query.append("AND COALESCE(n.protein_g, r.protein_g, 0) <= :maxProteinG\n");
            params.addValue("maxProteinG", maxProteinG);
        }
        query.append("ORDER BY r.title ASC\nLIMIT :limit");

        return jdbc.query(query.toString(), params, (rs, rowNum) -> toListItem(rs));
    }

    @Override
    public RecipeCatalogModels.RecipeDetail getById(UUID recipeId) {
        String recipeQuery = """
                WITH nutrition AS (
                    SELECT
                        ri.recipe_id AS recipe_id,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.calories_kcal * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS calories_kcal,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.protein_g * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS protein_g,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.carbs_g * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS carbs_g,
                        ROUND(SUM(CASE
                            WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                                THEN inut.fats_g * (ri.quantity / inut.base_quantity)
                            ELSE 0
                        END)::numeric, 2) AS fats_g
                    FROM recipe_ingredients ri
                    LEFT JOIN ingredient_nutrition inut ON inut.ingredient_id = ri.ingredient_id
                    GROUP BY ri.recipe_id
                )
                SELECT
                    r.id,
                    r.title,
                    r.cuisine,
                    r.meal,
                    r.servings,
                    r.summary,
                    r.time_minutes,
                    r.difficulty_level,
                    r.dietary_tags,
                    r.source,
                    r.img,
                    COALESCE(n.calories_kcal, r.calories_kcal, 0) AS calories_kcal,
                    COALESCE(n.protein_g, r.protein_g, 0) AS protein_g,
                    COALESCE(n.carbs_g, r.carbs_g, 0) AS carbs_g,
                    COALESCE(n.fats_g, r.fats_g, 0) AS fats_g
                FROM recipes r
                LEFT JOIN nutrition n ON n.recipe_id = r.id
                WHERE r.id = :recipeId
                """;

        RecipeDetailHeader header = jdbc.query(recipeQuery, Map.of("recipeId", recipeId), rs -> {
            if (!rs.next()) {
                return null;
            }
            return new RecipeDetailHeader((UUID) rs.getObject("id"), rs.getString("title"), rs.getString("cuisine"),
                    rs.getString("meal"), rs.getInt("servings"), rs.getString("summary"), rs.getInt("time_minutes"),
                    rs.getString("difficulty_level"), parseTextArray(rs.getArray("dietary_tags")),
                    rs.getString("source"), rs.getString("img"), toNutritionSummary(rs));
        });

        if (header == null) {
            throw new IllegalArgumentException("Recipe not found");
        }

        List<RecipeCatalogModels.RecipeIngredientItem> ingredients = jdbc.query("""
                SELECT
                    ri.ingredient_id,
                    ri.ingredient_name,
                    ri.unit,
                    ri.quantity,
                    ROUND(CASE
                        WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                            THEN inut.calories_kcal * (ri.quantity / inut.base_quantity)
                        ELSE 0
                    END::numeric, 2) AS calories_kcal,
                    ROUND(CASE
                        WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                            THEN inut.protein_g * (ri.quantity / inut.base_quantity)
                        ELSE 0
                    END::numeric, 2) AS protein_g,
                    ROUND(CASE
                        WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                            THEN inut.carbs_g * (ri.quantity / inut.base_quantity)
                        ELSE 0
                    END::numeric, 2) AS carbs_g,
                    ROUND(CASE
                        WHEN inut.base_quantity > 0 AND ri.unit = inut.base_unit
                            THEN inut.fats_g * (ri.quantity / inut.base_quantity)
                        ELSE 0
                    END::numeric, 2) AS fats_g
                FROM recipe_ingredients ri
                LEFT JOIN ingredient_nutrition inut ON inut.ingredient_id = ri.ingredient_id
                WHERE ri.recipe_id = :recipeId
                ORDER BY ri.sort_order ASC, ri.ingredient_name ASC
                """, Map.of("recipeId", recipeId),
                (rs, rowNum) -> new RecipeCatalogModels.RecipeIngredientItem(rs.getObject("ingredient_id", UUID.class),
                        rs.getString("ingredient_name"), rs.getString("unit"), rs.getBigDecimal("quantity"),
                        toNutritionSummary(rs)));

        List<RecipeCatalogModels.RecipePreparationStep> steps = jdbc.query("""
                SELECT
                    step_number,
                    step,
                    description,
                    ingredient_refs
                FROM recipe_steps
                WHERE recipe_id = :recipeId
                ORDER BY step_number ASC
                """, Map.of("recipeId", recipeId),
                (rs, rowNum) -> new RecipeCatalogModels.RecipePreparationStep(rs.getInt("step_number"),
                        rs.getString("step"), rs.getString("description"),
                        parseTextArray(rs.getArray("ingredient_refs"))));

        return new RecipeCatalogModels.RecipeDetail(header.id(), header.title(), header.cuisine(), header.meal(),
                header.servings(), header.summary(), header.timeMinutes(), header.difficultyLevel(),
                header.dietaryTags(), header.source(), header.img(), ingredients, steps, header.nutrition());
    }

    private RecipeCatalogModels.RecipeListItem toListItem(ResultSet rs) throws SQLException {
        return new RecipeCatalogModels.RecipeListItem(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("cuisine"), rs.getString("meal"), rs.getInt("servings"), rs.getInt("time_minutes"),
                rs.getString("difficulty_level"), parseTextArray(rs.getArray("dietary_tags")), toNutritionSummary(rs));
    }

    private RecipeCatalogModels.RecipeNutritionSummary toNutritionSummary(ResultSet rs) throws SQLException {
        return new RecipeCatalogModels.RecipeNutritionSummary(rs.getBigDecimal("calories_kcal"),
                rs.getBigDecimal("protein_g"), rs.getBigDecimal("carbs_g"), rs.getBigDecimal("fats_g"));
    }

    private List<String> parseTextArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object data = sqlArray.getArray();
        if (data instanceof String[] strings) {
            List<String> values = new ArrayList<>(strings.length);
            for (String s : strings) {
                if (s != null && !s.isBlank()) {
                    values.add(s);
                }
            }
            return values;
        }
        return List.of();
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RecipeDetailHeader(UUID id, String title, String cuisine, String meal, int servings, String summary,
            int timeMinutes, String difficultyLevel, List<String> dietaryTags, String source, String img,
            RecipeCatalogModels.RecipeNutritionSummary nutrition) {
    }
}
