import { useState, useEffect, useCallback } from "react";
import { useAuthToken } from "../../shared/auth/useAuthToken";
import {
    sendChatMessage,
    getConversationHistory,
    clearConversationHistory,
    type ConversationMessage,
} from "../../shared/api/assistant";

export function useAssistant() {
    const getToken = useAuthToken();
    const [messages, setMessages] = useState<ConversationMessage[]>([]);
    const [loading, setLoading] = useState(false);
    const [historyLoading, setHistoryLoading] = useState(true);
    const [mode, setMode] = useState<"concise" | "detailed">("concise");
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        setHistoryLoading(true);
        getToken()
            .then((token) => {
                if (!token) return [];
                return getConversationHistory(token);
            })
            .then((history) => setMessages(history))
            .catch(() => setMessages([]))
            .finally(() => setHistoryLoading(false));
    }, [getToken]);

    const sendMessage = useCallback(
        async (content: string) => {
            if (!content.trim() || loading) return;

            const userMsg: ConversationMessage = {
                role: "user",
                content: content.trim(),
                createdAt: new Date().toISOString(),
            };
            setMessages((prev) => [...prev, userMsg]);
            setLoading(true);
            setError(null);

            try {
                const token = await getToken();
                if (!token) throw new Error("Not authenticated");
                const response = await sendChatMessage({ message: content.trim(), mode }, token);
                const assistantMsg: ConversationMessage = {
                    role: "assistant",
                    content: response.reply,
                    createdAt: response.timestamp,
                };
                setMessages((prev) => [...prev, assistantMsg]);
            } catch (err) {
                setError(err instanceof Error ? err.message : "Failed to send message");
                setMessages((prev) => prev.slice(0, -1));
            } finally {
                setLoading(false);
            }
        },
        [getToken, mode, loading]
    );

    const clearHistory = useCallback(async () => {
        try {
            const token = await getToken();
            if (!token) return;
            await clearConversationHistory(token);
            setMessages([]);
        } catch {
            // silently ignore
        }
    }, [getToken]);

    return { messages, loading, historyLoading, mode, setMode, error, sendMessage, clearHistory };
}
