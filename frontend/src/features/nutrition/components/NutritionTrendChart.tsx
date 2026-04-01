import { useState, useEffect } from "react";
import {
    ComposedChart,
    LineChart,
    Line,
    BarChart,
    Bar,
    Cell,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
} from "recharts";
import { useAuthToken } from "../../../shared/auth/useAuthToken";
import { getDailyTrends, getWeeklyTrends, getMonthlySummaries } from "../../../shared/api/nutrition";
import { Spinner } from "../../../shared/ui/Spinner";
import { Alert } from "../../../shared/ui/Alert";

type TrendMode = "daily" | "weekly" | "monthly";

interface NutritionTrendChartProps {
    daysToShow?: number; // Default 14 days
}

export function NutritionTrendChart({ daysToShow = 14 }: NutritionTrendChartProps) {
    const getToken = useAuthToken();
    const [trendMode, setTrendMode] = useState<TrendMode>("daily");
    const [data, setData] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadTrendData();
    }, [trendMode, daysToShow]);

    const loadTrendData = async () => {
        setIsLoading(true);
        setError(null);

        try {
            const token = await getToken();
            if (!token) throw new Error("Not authenticated");

            const endDate = new Date();
            const startDate = new Date();

            if (trendMode === "daily") {
                startDate.setDate(endDate.getDate() - daysToShow);
            } else if (trendMode === "weekly") {
                startDate.setDate(endDate.getDate() - daysToShow * 7);
            } else {
                startDate.setMonth(endDate.getMonth() - 3); // 3 months back
            }

            const startDateStr = startDate.toISOString().split("T")[0];
            const endDateStr = endDate.toISOString().split("T")[0];

            let result;
            if (trendMode === "daily") {
                result = await getDailyTrends(startDateStr, endDateStr, token);
            } else if (trendMode === "weekly") {
                result = await getWeeklyTrends(startDateStr, endDateStr, token);
            } else {
                result = await getMonthlySummaries(startDateStr, endDateStr, token);
            }

            if (!result || result.length === 0) {
                setData([]);
                setError("No nutrition data available");
                return;
            }

            // Transform data for charts
            const chartData = result.map((item: any) => {
                if (trendMode === "daily") {
                    return {
                        date: item.date,
                        consumed: item.consumed,
                        target: item.target,
                        deficit: item.deficit,
                        surplusDeficit: item.deficit, // For coloring
                    };
                } else if (trendMode === "weekly") {
                    return {
                        period: `${item.weekStart} to ${item.weekEnd}`,
                        consumed: item.totalConsumed,
                        target: item.totalTarget,
                        deficit: item.avgDailyDeficit,
                    };
                } else {
                    return {
                        period: item.monthStart.substring(0, 7),
                        consumed: item.totalConsumed,
                        target: item.totalTarget,
                        deficit: item.totalDeficit,
                    };
                }
            });

            setData(chartData);
        } catch (err) {
            const message = err instanceof Error ? err.message : "Failed to load nutrition trends";
            setError(message);
        } finally {
            setIsLoading(false);
        }
    };

    const CustomTooltip = (props: any) => {
        const { active, payload } = props;
        if (active && payload && payload.length) {
            const item = payload[0].payload;
            let deficit = item.deficit || 0;
            const deficitLabel = deficit >= 0 ? `${deficit} kcal under target` : `${Math.abs(deficit)} kcal over target`;

            return (
                <div className="bg-white p-3 rounded-lg border border-slate-200 shadow-lg text-sm">
                    <p className="font-medium text-slate-900">
                        {item.date || item.period}
                    </p>
                    <p className="text-slate-600">
                        Consumed: <span className="font-semibold">{item.consumed} kcal</span>
                    </p>
                    <p className="text-slate-600">
                        Target: <span className="font-semibold">{item.target} kcal</span>
                    </p>
                    <p className={`font-medium ${deficit >= 0 ? "text-green-600" : "text-orange-600"}`}>
                        {deficitLabel}
                    </p>
                </div>
            );
        }
        return null;
    };

    return (
        <div className="space-y-4">
            {/* Mode selector */}
            <div className="flex gap-2">
                <button
                    onClick={() => setTrendMode("daily")}
                    className={`px-4 py-2 rounded-lg font-medium transition ${trendMode === "daily"
                        ? "bg-green-600 text-white"
                        : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                        }`}
                    disabled={isLoading}
                >
                    Daily
                </button>
                <button
                    onClick={() => setTrendMode("weekly")}
                    className={`px-4 py-2 rounded-lg font-medium transition ${trendMode === "weekly"
                        ? "bg-green-600 text-white"
                        : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                        }`}
                    disabled={isLoading}
                >
                    Weekly
                </button>
                <button
                    onClick={() => setTrendMode("monthly")}
                    className={`px-4 py-2 rounded-lg font-medium transition ${trendMode === "monthly"
                        ? "bg-green-600 text-white"
                        : "bg-slate-200 text-slate-700 hover:bg-slate-300"
                        }`}
                    disabled={isLoading}
                >
                    Monthly
                </button>
            </div>

            {/* Chart container */}
            <div className="bg-white rounded-lg border border-slate-200 p-4">
                {isLoading && (
                    <div className="flex justify-center py-12">
                        <Spinner label="Loading trends..." />
                    </div>
                )}

                {error && (
                    <Alert tone="info" title="No Data" message={error} />
                )}

                {!isLoading && !error && data.length > 0 && (
                    <ResponsiveContainer width="100%" height={300}>
                        {trendMode === "daily" ? (
                            <ComposedChart data={data} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                <XAxis
                                    dataKey="date"
                                    tick={{ fontSize: 12 }}
                                    interval={Math.max(0, Math.floor(data.length / 7))}
                                />
                                <YAxis tick={{ fontSize: 12 }} />
                                <Tooltip content={<CustomTooltip />} />
                                <Legend />
                                <Bar dataKey="consumed" name="Consumed (kcal)" isAnimationActive={false}>
                                    {data.map((entry, index) => (
                                        <Cell
                                            key={`cell-${index}`}
                                            fill={entry.consumed <= entry.target ? "#22c55e" : "#ef4444"}
                                        />
                                    ))}
                                </Bar>
                                <Line
                                    type="monotone"
                                    dataKey="target"
                                    stroke="#f59e0b"
                                    strokeDasharray="5 5"
                                    dot={false}
                                    isAnimationActive={false}
                                    name="Target (kcal)"
                                />
                            </ComposedChart>
                        ) : (
                            <BarChart data={data} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
                                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                                <XAxis
                                    dataKey={trendMode === "weekly" ? "period" : "period"}
                                    tick={{ fontSize: 12 }}
                                    interval={0}
                                />
                                <YAxis tick={{ fontSize: 12 }} />
                                <Tooltip content={<CustomTooltip />} />
                                <Legend />
                                <Bar
                                    dataKey="consumed"
                                    isAnimationActive={false}
                                    name="Consumed (kcal)"
                                >
                                    {data.map((entry, index) => (
                                        <Cell
                                            key={`cell-${index}`}
                                            fill={entry.consumed <= entry.target ? "#22c55e" : "#ef4444"}
                                        />
                                    ))}
                                </Bar>
                                <Bar
                                    dataKey="target"
                                    fill="#f59e0b"
                                    isAnimationActive={false}
                                    name="Target (kcal)"
                                />
                            </BarChart>
                        )}
                    </ResponsiveContainer>
                )}

                {!isLoading && !error && data.length === 0 && (
                    <p className="text-center text-slate-500 py-8">No trend data available yet</p>
                )}
            </div>
        </div>
    );
}
