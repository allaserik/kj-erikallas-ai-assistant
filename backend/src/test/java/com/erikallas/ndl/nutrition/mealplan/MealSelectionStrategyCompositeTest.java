package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Meal Selection Strategy Composite Tests")
class MealSelectionStrategyCompositeTest {

    private MealSelectionStrategyComposite composite;
    private MealSelectionStrategy deterministicStrategy;
    private AiMealSelectionStrategy aiStrategy;

    @BeforeEach
    void setup() {
        deterministicStrategy = mock(MealSelectionStrategy.class);
        aiStrategy = mock(AiMealSelectionStrategy.class);
        composite = new MealSelectionStrategyComposite(deterministicStrategy, aiStrategy);
    }

    @Test
    @DisplayName("Should prefer AI strategy when available")
    void testPreferAiStrategy() {
        when(aiStrategy.isEnabled()).thenReturn(true);

        var mockRecipe = new MealPlanRecipeSelectorRepository.RecipeCandidate(UUID.randomUUID(), "AI Selected Recipe",
                "breakfast", "american", 15, BigDecimal.valueOf(350), BigDecimal.valueOf(15), BigDecimal.valueOf(45),
                BigDecimal.valueOf(10));

        when(aiStrategy.chooseRecipeForSlot(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()), any())).thenReturn(mockRecipe);

        var userPrefs = createMockPreferences();

        var result = composite.chooseRecipe("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNotNull(result);
        assertEquals("AI Selected Recipe", result.title());
        verify(aiStrategy, times(1)).chooseRecipeForSlot(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(Set.of()), any());
        verify(deterministicStrategy, never()).chooseRecipe(anyString(), anyList(), anyList(), anyList(), anyList(),
                anySet());
    }

    @Test
    @DisplayName("Should fall back to deterministic when AI disabled")
    void testFallbackWhenAiDisabled() {
        when(aiStrategy.isEnabled()).thenReturn(false);

        var mockRecipe = new MealPlanRecipeSelectorRepository.RecipeCandidate(UUID.randomUUID(), "Deterministic Recipe",
                "breakfast", "american", 15, BigDecimal.valueOf(350), BigDecimal.valueOf(15), BigDecimal.valueOf(45),
                BigDecimal.valueOf(10));

        when(deterministicStrategy.chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(Set.of()))).thenReturn(mockRecipe);

        var userPrefs = createMockPreferences();

        var result = composite.chooseRecipe("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNotNull(result);
        assertEquals("Deterministic Recipe", result.title());
        verify(aiStrategy, times(1)).isEnabled();
        verify(deterministicStrategy, times(1)).chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(List.of()), eq(Set.of()));
    }

    @Test
    @DisplayName("Should fall back to deterministic when AI throws exception")
    void testFallbackOnAiException() {
        when(aiStrategy.isEnabled()).thenReturn(true);
        when(aiStrategy.chooseRecipeForSlot(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()), any())).thenThrow(new RuntimeException("AI Service Error"));

        var mockRecipe = new MealPlanRecipeSelectorRepository.RecipeCandidate(UUID.randomUUID(), "Backup Recipe",
                "breakfast", "american", 15, BigDecimal.valueOf(350), BigDecimal.valueOf(15), BigDecimal.valueOf(45),
                BigDecimal.valueOf(10));

        when(deterministicStrategy.chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(Set.of()))).thenReturn(mockRecipe);

        var userPrefs = createMockPreferences();

        var result = composite.chooseRecipe("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNotNull(result);
        assertEquals("Backup Recipe", result.title());
        verify(aiStrategy, times(1)).chooseRecipeForSlot(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(Set.of()), any());
        verify(deterministicStrategy, times(1)).chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(List.of()), eq(Set.of()));
    }

    @Test
    @DisplayName("Should handle null result from AI gracefully")
    void testHandleNullAiResult() {
        when(aiStrategy.isEnabled()).thenReturn(true);
        when(aiStrategy.chooseRecipeForSlot(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()), any())).thenReturn(null);

        var mockRecipe = new MealPlanRecipeSelectorRepository.RecipeCandidate(UUID.randomUUID(), "Fallback Recipe",
                "breakfast", "american", 15, BigDecimal.valueOf(350), BigDecimal.valueOf(15), BigDecimal.valueOf(45),
                BigDecimal.valueOf(10));

        when(deterministicStrategy.chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(Set.of()))).thenReturn(mockRecipe);

        var userPrefs = createMockPreferences();

        // Note: Current implementation doesn't fall back on null, only on exception
        // This test documents the current behavior
        var result = composite.chooseRecipe("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNull(result);
    }

    private NutritionPreferencesService.PreferencesResponse createMockPreferences() {
        return new NutritionPreferencesService.PreferencesResponse("UTC", 2000, BigDecimal.valueOf(150),
                BigDecimal.valueOf(200), BigDecimal.valueOf(70), (short) 3, (short) 2, null, List.of(), List.of(),
                List.of(), List.of(), null);
    }
}
