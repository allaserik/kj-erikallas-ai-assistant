import { useEffect, useRef } from "react";
import type { ConversationMessage } from "../../shared/api/assistant";
import AssistantMessage from "./AssistantMessage";

type Props = {
    messages: ConversationMessage[];
    loading: boolean;
    historyLoading: boolean;
    error: string | null;
};

export default function AssistantChat({ messages, loading, historyLoading, error }: Props) {
    const bottomRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, loading]);

    if (historyLoading) {
        return (
            <div className="flex-1 flex items-center justify-center text-slate-400 text-sm">
                Loading conversation…
            </div>
        );
    }

    return (
        <div className="flex-1 overflow-y-auto px-4 py-4">
            {messages.length === 0 && !loading && (
                <div className="flex flex-col items-center justify-center h-full text-center text-slate-400 gap-3 py-12">
                    <div className="text-4xl">💬</div>
                    <p className="text-base font-medium text-slate-500">Your AI wellness assistant</p>
                    <p className="text-sm max-w-xs">
                        Ask me about your health metrics, goals, nutrition summary, or meal plan.
                    </p>
                </div>
            )}
            {messages.map((msg, i) => (
                <AssistantMessage key={i} message={msg} />
            ))}
            {loading && (
                <div className="flex justify-start mb-3">
                    <div className="bg-white border border-slate-200 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
                        <div className="flex gap-1 items-center">
                            <span className="w-2 h-2 rounded-full bg-slate-400 animate-bounce [animation-delay:0ms]" />
                            <span className="w-2 h-2 rounded-full bg-slate-400 animate-bounce [animation-delay:150ms]" />
                            <span className="w-2 h-2 rounded-full bg-slate-400 animate-bounce [animation-delay:300ms]" />
                        </div>
                    </div>
                </div>
            )}
            {error && (
                <div className="text-center text-red-500 text-xs py-2">{error}</div>
            )}
            <div ref={bottomRef} />
        </div>
    );
}
