import { useState, useRef, type KeyboardEvent } from "react";
import { PaperAirplaneIcon } from "@heroicons/react/24/solid";

type Props = {
    onSend: (message: string) => void;
    loading: boolean;
    mode: "concise" | "detailed";
    onModeChange: (mode: "concise" | "detailed") => void;
};

export default function AssistantInput({ onSend, loading, mode, onModeChange }: Props) {
    const [input, setInput] = useState("");
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    const handleSend = () => {
        const trimmed = input.trim();
        if (!trimmed || loading) return;
        onSend(trimmed);
        setInput("");
        if (textareaRef.current) {
            textareaRef.current.style.height = "auto";
        }
    };

    const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleInput = () => {
        const el = textareaRef.current;
        if (!el) return;
        el.style.height = "auto";
        el.style.height = `${Math.min(el.scrollHeight, 120)}px`;
    };

    return (
        <div className="border-t border-slate-200 bg-white px-4 py-3">
            {/* Mode selector */}
            <div className="flex gap-2 mb-2">
                <button
                    onClick={() => onModeChange("concise")}
                    className={`px-3 py-1 text-xs rounded-full font-medium transition-colors ${mode === "concise"
                            ? "bg-green-100 text-green-700 border border-green-300"
                            : "bg-slate-100 text-slate-500 border border-transparent"
                        }`}
                >
                    Concise
                </button>
                <button
                    onClick={() => onModeChange("detailed")}
                    className={`px-3 py-1 text-xs rounded-full font-medium transition-colors ${mode === "detailed"
                            ? "bg-green-100 text-green-700 border border-green-300"
                            : "bg-slate-100 text-slate-500 border border-transparent"
                        }`}
                >
                    Detailed
                </button>
            </div>
            {/* Input row */}
            <div className="flex items-end gap-2">
                <textarea
                    ref={textareaRef}
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    onInput={handleInput}
                    placeholder="Ask about your health, goals, or meal plan…"
                    rows={1}
                    disabled={loading}
                    className="flex-1 resize-none rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm focus:outline-none focus:border-green-500 focus:ring-1 focus:ring-green-400 disabled:bg-slate-50 leading-relaxed"
                    style={{ overflowY: "hidden" }}
                />
                <button
                    onClick={handleSend}
                    disabled={!input.trim() || loading}
                    className="flex-shrink-0 p-2.5 rounded-xl bg-green-600 text-white hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    aria-label="Send message"
                >
                    <PaperAirplaneIcon className="h-5 w-5" />
                </button>
            </div>
        </div>
    );
}
