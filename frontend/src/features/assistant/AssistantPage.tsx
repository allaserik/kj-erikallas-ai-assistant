import { TrashIcon } from "@heroicons/react/24/outline";
import { useAssistant } from "./useAssistant";
import AssistantChat from "./AssistantChat";
import AssistantInput from "./AssistantInput";

export default function AssistantPage() {
    const { messages, loading, historyLoading, mode, setMode, error, sendMessage, clearHistory } =
        useAssistant();

    return (
        <div className="flex flex-col h-full bg-slate-50">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-slate-200">
                <div>
                    <h1 className="text-base font-semibold text-slate-800">AI Assistant</h1>
                    <p className="text-xs text-slate-500">Powered by GPT-4o-mini</p>
                </div>
                <button
                    onClick={clearHistory}
                    disabled={messages.length === 0}
                    className="p-2 rounded-lg text-slate-400 hover:text-red-500 hover:bg-red-50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    aria-label="Clear conversation history"
                    title="Clear history"
                >
                    <TrashIcon className="h-5 w-5" />
                </button>
            </div>

            {/* Chat area */}
            <AssistantChat
                messages={messages}
                loading={loading}
                historyLoading={historyLoading}
                error={error}
            />

            {/* Input area */}
            <AssistantInput
                onSend={sendMessage}
                loading={loading}
                mode={mode}
                onModeChange={setMode}
            />
        </div>
    );
}
