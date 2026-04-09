import { api } from "./client";
import type { ApiResponse } from "../types";
import { unwrapApiData } from "./unwrap";

export type ChatRequest = {
    message: string;
    mode?: "concise" | "detailed";
};

export type ChatResponse = {
    reply: string;
    functionCalled: string | null;
    timestamp: string;
};

export type ConversationMessage = {
    role: "user" | "assistant";
    content: string;
    createdAt: string;
};

export function sendChatMessage(req: ChatRequest, token: string): Promise<ChatResponse> {
    return api
        .post<ChatResponse | ApiResponse<ChatResponse>>("/api/assistant/chat", req, token)
        .then((response) => unwrapApiData(response) as ChatResponse);
}

export function getConversationHistory(token: string): Promise<ConversationMessage[]> {
    return api
        .get<ConversationMessage[] | ApiResponse<ConversationMessage[]>>("/api/assistant/history", token)
        .then((response) => unwrapApiData(response) as ConversationMessage[]);
}

export function clearConversationHistory(token: string): Promise<void> {
    return api.del<void>("/api/assistant/history", token);
}
