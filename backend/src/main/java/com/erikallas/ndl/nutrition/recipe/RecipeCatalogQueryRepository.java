package com.erikallas.ndl.nutrition.recipe;

import java.util.List;
import java.util.UUID;

public interface RecipeCatalogQueryRepository {
    List<RecipeCatalogModels.RecipeListItem> search(RecipeCatalogModels.RecipeSearchFilters filters);

    RecipeCatalogModels.RecipeDetail getById(UUID recipeId);
}
