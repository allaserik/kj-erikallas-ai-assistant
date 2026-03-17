package com.erikallas.ndl.nutrition.mealplan;

import com.erikallas.ndl.ai.openai.OpenAiClient;
import com.erikallas.ndl.nutrition.preferences.NutritionPreferencesService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@DisplayName("AI Meal Selection Strategy Tests")
class AiMealSelectionStrategyTest {

    private AiMealSelectionStrategy strategy;
    private OpenAiClient openAiClient;
    private ObjectMapper objectMapper;
    private MealSelectionStrategy fallbackStrategy;
    private MealPlanRecipeSelectorRepository selectorRepository;

    @BeforeEach
    void setup() {
        openAiClient = mock(OpenAiClient.class);
        objectMapper = new ObjectMapper();
        fallbackStrategy = mock(MealSelectionStrategy.class);
        selectorRepository = mock(MealPlanRecipeSelectorRepository.class);

        strategy = new AiMealSelectionStrategy(openAiClient, objectMapper, fallbackStrategy, selectorRepository);
    }

    @Test
    @DisplayName("Should use fallback when OpenAI key is not configured")
    void testUseFallbackWhenNoApiKey() {
        when(openAiClient.hasKey()).thenReturn(false);

        var mockRecipe = new MealPlanRecipeSelectorRepository.RecipeCandidate(UUID.randomUUID(), "Test Recipe",
                "breakfast", "american", 15, BigDecimal.valueOf(350), BigDecimal.valueOf(15), BigDecimal.valueOf(45),
                BigDecimal.valueOf(10));

        when(fallbackStrategy.chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()))).thenReturn(mockRecipe);

        var userPrefs = new NutritionPreferencesService.PreferencesResponse("UTC", 2000, BigDecimal.valueOf(150),
                BigDecimal.valueOf(200), BigDecimal.valueOf(70), (short) 3, (short) 2, null, List.of(), List.of(),
                List.of(), List.of(), null);

        var result = strategy.chooseRecipeForSlot("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNotNull(result);
        assertEquals("Test Recipe", result.title());
        verify(openAiClient, times(1)).hasKey();
        verify(fallbackStrategy, times(1)).chooseRecipe(anyString(), anyList(), anyList(), anyList(), anyList(),
                anySet());
    }

    @Test
    @DisplayName("Should fall back to deterministic on AI error")
    void testFallbackOnAiError() {
        when(openAiClient.hasKey()).thenReturn(true);
        when(selectorRepository.findCandidates(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()), eq(30))).thenThrow(new RuntimeException("API Error"));

        var mockRecipe = new MealPlanRecipeSelectorRepository.RecipeCandidate(UUID.randomUUID(), "Backup Recipe",
                "breakfast", "american", 15, BigDecimal.valueOf(350), BigDecimal.valueOf(15), BigDecimal.valueOf(45),
                BigDecimal.valueOf(10));

        when(fallbackStrategy.chooseRecipe(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()))).thenReturn(mockRecipe);

        var userPrefs = new NutritionPreferencesService.PreferencesResponse("UTC", 2000, BigDecimal.valueOf(150),
                BigDecimal.valueOf(200), BigDecimal.valueOf(70), (short) 3, (short) 2, null, List.of(), List.of(),
                List.of(), List.of(), null);

        var result = strategy.chooseRecipeForSlot("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNotNull(result);
        assertEquals("Backup Recipe", result.title());
    }

    @Test
    @DisplayName("Should return null when no candidates available")
    void testEmptyCandidates() {
        when(openAiClient.hasKey()).thenReturn(true);
        when(selectorRepository.findCandidates(eq("breakfast"), eq(List.of()), eq(List.of()), eq(List.of()),
                eq(Set.of()), eq(30))).thenReturn(List.of());

        var userPrefs = new NutritionPreferencesService.PreferencesResponse("UTC", 2000, BigDecimal.valueOf(150),
                BigDecimal.valueOf(200), BigDecimal.valueOf(70), (short) 3, (short) 2, null, List.of(), List.of(),
                List.of(), List.of(), null);

        var result = strategy.chooseRecipeForSlot("breakfast", List.of(), List.of(), List.of(), List.of(), Set.of(),
                userPrefs);

        assertNull(result);
    }

    @Test
    @DisplayName("Should check AI enabled status correctly")
    void testIsEnabledCheck() {
        when(openAiClient.hasKey()).thenReturn(true);
        assertTrue(strategy.isEnabled());

        when(openAiClient.hasKey()).thenReturn(false);
        assertFalse(strategy.isEnabled());
    }
}
