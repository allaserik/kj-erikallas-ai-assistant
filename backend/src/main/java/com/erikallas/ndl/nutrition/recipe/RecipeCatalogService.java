package com.erikallas.ndl.nutrition.recipe;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecipeCatalogService {

    private final RecipeCatalogQueryRepository recipeCatalogQueryRepository;

    public RecipeCatalogService(RecipeCatalogQueryRepository recipeCatalogQueryRepository) {
        this.recipeCatalogQueryRepository = recipeCatalogQueryRepository;
    }

    public List<RecipeCatalogModels.RecipeListItem> search(RecipeCatalogModels.RecipeSearchFilters filters) {
        return recipeCatalogQueryRepository.search(filters);
    }

    public RecipeCatalogModels.RecipeDetail getById(UUID recipeId) {
        return recipeCatalogQueryRepository.getById(recipeId);
    }
}
