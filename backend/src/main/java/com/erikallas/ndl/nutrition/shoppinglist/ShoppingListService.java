package com.erikallas.ndl.nutrition.shoppinglist;

import java.math.BigDecimal;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating shopping lists from meal plans or individual meals.
 * 
 * Aggregates ingredients from recipes, categorizes them by food group, and
 * allows customization (quantity adjustments, exclusions).
 */
@Service
public class ShoppingListService {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingListService.class);

    /**
     * Food group categories for organizing shopping list items.
     * 
     * At least 5 categories per assignment requirements: 1. Proteins (meat, fish,
     * eggs, legumes) 2. Dairy (milk, cheese, yogurt) 3. Produce (fruits,
     * vegetables) 4. Grains (bread, pasta, rice) 5. Pantry Staples (oils, spices,
     * condiments)
     */
    public enum FoodCategory {
        PROTEINS("Proteins",
                new String[] { "chicken", "beef", "pork", "fish", "salmon", "tuna", "shrimp", "egg", "tofu", "lentil",
                        "bean", "pea", "chickpea", "turkey", "lamb" }),
        DAIRY("Dairy", new String[] { "milk", "cheese", "yogurt", "butter", "cream", "sour cream", "cottage cheese" }),
        PRODUCE("Produce",
                new String[] { "apple", "banana", "orange", "tomato", "carrot", "lettuce", "broccoli", "spinach",
                        "kale", "potato", "onion", "garlic", "cucumber", "pepper", "zucchini", "mushroom", "berry",
                        "grape", "lemon", "lime" }),
        GRAINS("Grains", new String[] { "bread", "pasta", "rice", "oat", "cereal", "flour", "quinoa", "barley" }),
        PANTRY("Pantry Staples", new String[] { "oil", "salt", "sugar", "honey", "spice", "sauce", "vinegar",
                "soy sauce", "olive", "almond", "nut", "seed", "dried" });

        private final String displayName;
        private final String[] keywords;

        FoodCategory(String displayName, String[] keywords) {
            this.displayName = displayName;
            this.keywords = keywords;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Categorize an ingredient by matching keywords.
         */
        public static FoodCategory categorize(String ingredientLabel) {
            String lower = ingredientLabel.toLowerCase();

            for (FoodCategory category : FoodCategory.values()) {
                for (String keyword : category.keywords) {
                    if (lower.contains(keyword)) {
                        return category;
                    }
                }
            }

            // Default to PANTRY if no match
            return PANTRY;
        }
    }

    /**
     * Generate a shopping list from recipes.
     * 
     * Aggregates all ingredients, groups by category, and returns as map with
     * quantities summed.
     */
    @Transactional(readOnly = true)
    public ShoppingListDTO generateFromRecipes(List<RecipeIngredientInfo> recipeIngredients) {
        if (recipeIngredients == null || recipeIngredients.isEmpty()) {
            return new ShoppingListDTO(new HashMap<>(), new HashMap<>());
        }

        Map<String, ShoppingListItemDTO> itemsByLabel = new LinkedHashMap<>();
        Map<FoodCategory, List<ShoppingListItemDTO>> categorizedItems = new LinkedHashMap<>();

        // Initialize category maps
        for (FoodCategory category : FoodCategory.values()) {
            categorizedItems.put(category, new ArrayList<>());
        }

        // Aggregate ingredients
        for (var recipeIngredient : recipeIngredients) {
            String label = recipeIngredient.label();
            String unit = recipeIngredient.unit();
            BigDecimal quantity = recipeIngredient.quantity();

            if (itemsByLabel.containsKey(label)) {
                // Combine quantities for same ingredient
                ShoppingListItemDTO existing = itemsByLabel.get(label);
                BigDecimal newQty = existing.quantity().add(quantity);
                ShoppingListItemDTO updated = new ShoppingListItemDTO(existing.id(), label, unit, newQty,
                        existing.category(), false, null);
                itemsByLabel.put(label, updated);
            } else {
                // New ingredient
                FoodCategory category = FoodCategory.categorize(label);
                ShoppingListItemDTO item = new ShoppingListItemDTO(UUID.randomUUID().toString(), label, unit, quantity,
                        category, false, null);
                itemsByLabel.put(label, item);
            }
        }

        // Organize by category
        for (ShoppingListItemDTO item : itemsByLabel.values()) {
            categorizedItems.get(item.category()).add(item);
        }

        logger.debug("Generated shopping list with {} items across {} categories", itemsByLabel.size(),
                categorizedItems.values().stream().filter(list -> !list.isEmpty()).count());

        return new ShoppingListDTO(itemsByLabel, categorizedItems);
    }

    /**
     * Adjust quantity of a shopping list item.
     */
    public void adjustQuantity(ShoppingListDTO list, String itemId, BigDecimal newQuantity) {
        for (var item : list.allItems().values()) {
            if (item.id().equals(itemId)) {
                ShoppingListItemDTO updated = new ShoppingListItemDTO(item.id(), item.label(), item.unit(), newQuantity,
                        item.category(), item.excluded(), item.notes());
                list.allItems().put(item.label(), updated);
                logger.debug("Adjusted quantity for item {}: {}{}", itemId, newQuantity, item.unit());
                return;
            }
        }
    }

    /**
     * Exclude an item from the shopping list.
     */
    public void excludeItem(ShoppingListDTO list, String itemId, String notes) {
        for (var item : list.allItems().values()) {
            if (item.id().equals(itemId)) {
                ShoppingListItemDTO updated = new ShoppingListItemDTO(item.id(), item.label(), item.unit(),
                        item.quantity(), item.category(), true, notes);
                list.allItems().put(item.label(), updated);
                logger.debug("Excluded item {}: {}", itemId, item.label());
                return;
            }
        }
    }

    /**
     * Get shopping list as categorized view (by FoodCategory).
     */
    public Map<String, List<ShoppingListItemDTO>> getCategorized(ShoppingListDTO list) {
        Map<String, List<ShoppingListItemDTO>> result = new LinkedHashMap<>();

        for (FoodCategory category : FoodCategory.values()) {
            List<ShoppingListItemDTO> items = list.byCategory().getOrDefault(category, new ArrayList<>()).stream()
                    .filter(item -> !item.excluded()).toList();

            if (!items.isEmpty()) {
                result.put(category.getDisplayName(), items);
            }
        }

        return result;
    }

    /**
     * Data class for ingredient information from recipes.
     */
    public record RecipeIngredientInfo(String label, String unit, BigDecimal quantity) {
    }

    /**
     * DTO for a single shopping list item.
     */
    public record ShoppingListItemDTO(String id, String label, String unit, BigDecimal quantity, FoodCategory category,
            boolean excluded, String notes) {
    }

    /**
     * DTO for complete shopping list with all items and categorized view.
     */
    public static class ShoppingListDTO {
        private final Map<String, ShoppingListItemDTO> allItems;
        private final Map<FoodCategory, List<ShoppingListItemDTO>> byCategory;

        public ShoppingListDTO(Map<String, ShoppingListItemDTO> allItems,
                Map<FoodCategory, List<ShoppingListItemDTO>> byCategory) {
            this.allItems = allItems;
            this.byCategory = byCategory;
        }

        public Map<String, ShoppingListItemDTO> allItems() {
            return allItems;
        }

        public Map<FoodCategory, List<ShoppingListItemDTO>> byCategory() {
            return byCategory;
        }

        /**
         * Get items not excluded.
         */
        public List<ShoppingListItemDTO> getActiveItems() {
            return allItems.values().stream().filter(item -> !item.excluded()).toList();
        }

        /**
         * Get item count excluding excluded items.
         */
        public int getItemCount() {
            return (int) allItems.values().stream().filter(item -> !item.excluded()).count();
        }
    }
}
