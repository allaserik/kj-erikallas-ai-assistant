import type { ConversationMessage } from "../../shared/api/assistant";

type Props = {
    message: ConversationMessage;
};

export default function AssistantMessage({ message }: Props) {
    const isUser = message.role === "user";
    return (
        <div className={`flex ${isUser ? "justify-end" : "justify-start"} mb-3`}>
            <div
                className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap break-words ${isUser
                        ? "bg-green-600 text-white rounded-tr-sm"
                        : "bg-white border border-slate-200 text-slate-800 rounded-tl-sm shadow-sm"
                    }`}
            >
                {!isUser && (
                    <div className="text-xs font-semibold text-green-700 mb-1">AI Assistant</div>
                )}
                {message.content}
            </div>
        </div>
    );
}
