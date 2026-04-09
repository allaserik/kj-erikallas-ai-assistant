package com.erikallas.ndl.ai.assistant;

import com.erikallas.ndl.ai.assistant.AssistantModels.ChatRequest;
import com.erikallas.ndl.ai.assistant.AssistantModels.ChatResponse;
import com.erikallas.ndl.ai.assistant.AssistantModels.ConversationMessage;
import com.erikallas.ndl.auth.user.UserService;
import com.erikallas.ndl.common.api.dto.ApiSuccess;
import com.erikallas.ndl.common.ratelimit.RateLimitService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the AI assistant feature.
 *
 * Endpoints: POST /api/assistant/chat — send a message, receive a reply GET
 * /api/assistant/history — get the full conversation history DELETE
 * /api/assistant/history — clear all conversation history
 *
 * Rate limits (per authenticated user): chat: 20 requests/minute (generous for
 * demo; OpenAI costs stay bounded) history: 60 requests/minute
 *
 * Security: All endpoints require a valid Bearer JWT. userId is ALWAYS resolved
 * from the JWT — never from request body/params.
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final UserService userService;
    private final RateLimitService rateLimitService;

    public AssistantController(AssistantService assistantService, UserService userService,
            RateLimitService rateLimitService) {
        this.assistantService = assistantService;
        this.userService = userService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Send a message to the AI assistant and receive a reply.
     *
     * The assistant has access to the authenticated user's health metrics, goals,
     * meal plan, and nutrition data via function calling. The {@code mode} field in
     * the request body controls response verbosity ("concise" or "detailed").
     */
    @PostMapping("/chat")
    public ApiSuccess<ChatResponse> chat(@RequestBody ChatRequest request, JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        rateLimitService.check("assistant:chat:user", user.getId().toString(), 20, Duration.ofMinutes(1));
        return ApiSuccess.of(assistantService.chat(user.getId(), user.getEmail(), request));
    }

    /**
     * Retrieve the full conversation history for the authenticated user. Returns
     * both "user" and "assistant" roles; "system_summary" is excluded.
     */
    @GetMapping("/history")
    public ApiSuccess<List<ConversationMessage>> history(JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        rateLimitService.check("assistant:history:user", user.getId().toString(), 60, Duration.ofMinutes(1));
        return ApiSuccess.of(assistantService.getHistory(user.getId()));
    }

    /**
     * Clear all conversation history for the authenticated user. This is a hard
     * delete — history cannot be restored after this operation.
     */
    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(JwtAuthenticationToken auth) {
        var user = userService.ensureUserFromJwt(auth);
        assistantService.clearHistory(user.getId());
    }
}
