import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
    children: ReactNode;
    /** Optional custom fallback. Receives the error and a reset callback. */
    fallback?: (error: Error, reset: () => void) => ReactNode;
}

interface State {
    error: Error | null;
}

/**
 * Catches uncaught React render errors and shows a graceful fallback
 * instead of a blank screen. Wrap at the app level (or per-route).
 */
export class ErrorBoundary extends Component<Props, State> {
    state: State = { error: null };

    static getDerivedStateFromError(error: Error): State {
        return { error };
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        console.error("[ErrorBoundary] Uncaught render error:", error, info.componentStack);
    }

    reset = () => this.setState({ error: null });

    render() {
        const { error } = this.state;
        if (!error) return this.props.children;

        if (this.props.fallback) return this.props.fallback(error, this.reset);

        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50 p-6">
                <div className="bg-white rounded-xl shadow border border-red-200 p-8 max-w-md w-full text-center space-y-4">
                    <div className="text-4xl">⚠️</div>
                    <h2 className="text-lg font-semibold text-slate-800">Something went wrong</h2>
                    <p className="text-sm text-slate-600">
                        An unexpected error occurred. You can try refreshing the page.
                    </p>
                    {import.meta.env.DEV && (
                        <details className="text-left text-xs bg-slate-100 rounded p-3 text-slate-700 overflow-auto max-h-40">
                            <summary className="cursor-pointer font-medium">Error details</summary>
                            <pre className="mt-2 whitespace-pre-wrap">{error.message}</pre>
                        </details>
                    )}
                    <button
                        onClick={this.reset}
                        className="px-4 py-2 rounded-lg bg-green-700 text-white text-sm font-medium hover:bg-green-800"
                    >
                        Try again
                    </button>
                </div>
            </div>
        );
    }
}
