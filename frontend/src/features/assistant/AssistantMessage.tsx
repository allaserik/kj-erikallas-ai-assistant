import {
    ResponsiveContainer,
    LineChart,
    Line,
    BarChart,
    Bar,
    PieChart,
    Pie,
    Cell,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
} from "recharts";
import type { ConversationMessage, ChartData } from "../../shared/api/assistant";

const PIE_COLORS = ["#16a34a", "#3b82f6", "#f59e0b"];

function AssistantChart({ chart }: { chart: ChartData }) {
    if (chart.type === "line" && chart.series.length > 0) {
        const data = chart.series[0].data.map((p) => ({ x: p.x, y: p.y }));
        const unit = chart.series[0].unit;
        return (
            <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
                <p className="text-xs font-semibold text-slate-500 mb-2">{chart.title}</p>
                <ResponsiveContainer width="100%" height={180}>
                    <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                        <XAxis dataKey="x" tick={{ fontSize: 10 }} tickFormatter={(v: string) => v.slice(5)} />
                        <YAxis tick={{ fontSize: 10 }} unit={unit} domain={["auto", "auto"]} width={40} />
                        <Tooltip formatter={(v) => [`${v} ${unit}`, "Weight"]} />
                        <Line type="monotone" dataKey="y" stroke="#16a34a" dot={false} strokeWidth={2} />
                    </LineChart>
                </ResponsiveContainer>
            </div>
        );
    }

    if (chart.type === "bar" && chart.series.length > 0) {
        // Merge series by x label
        const labels = chart.series[0].data.map((p) => p.x);
        const merged = labels.map((x, i) => {
            const row: Record<string, string | number> = { x };
            chart.series.forEach((s) => { row[s.label] = s.data[i]?.y ?? 0; });
            return row;
        });
        return (
            <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
                <p className="text-xs font-semibold text-slate-500 mb-2">{chart.title}</p>
                <ResponsiveContainer width="100%" height={180}>
                    <BarChart data={merged} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                        <XAxis dataKey="x" tick={{ fontSize: 10 }} />
                        <YAxis tick={{ fontSize: 10 }} width={45} />
                        <Tooltip />
                        <Legend iconSize={8} wrapperStyle={{ fontSize: 10 }} />
                        {chart.series.map((s, idx) => (
                            <Bar key={s.label} dataKey={s.label} fill={idx === 0 ? "#16a34a" : "#94a3b8"} radius={[2, 2, 0, 0]} />
                        ))}
                    </BarChart>
                </ResponsiveContainer>
            </div>
        );
    }

    if (chart.type === "pie" && chart.series.length > 0) {
        const pieData = chart.series.map((s) => ({
            name: s.label,
            value: Math.round(s.data[0]?.y ?? 0),
            unit: s.unit,
        }));
        return (
            <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
                <p className="text-xs font-semibold text-slate-500 mb-2">{chart.title}</p>
                <ResponsiveContainer width="100%" height={170}>
                    <PieChart>
                        <Pie
                            data={pieData}
                            dataKey="value"
                            nameKey="name"
                            cx="50%"
                            cy="50%"
                            outerRadius={65}
                            label={(props) => {
                                const p = props as { name?: string; value?: number | string };
                                return `${p.name ?? ""}: ${Math.round(Number(p.value ?? 0))}`;
                            }}
                            labelLine={false}
                        >
                            {pieData.map((_, i) => (
                                <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                            ))}
                        </Pie>
                        <Tooltip formatter={(v) => [`${v} g`]} />
                        <Legend iconSize={8} wrapperStyle={{ fontSize: 10 }} />
                    </PieChart>
                </ResponsiveContainer>
            </div>
        );
    }

    return null;
}

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
                {!isUser && message.chart && <AssistantChart chart={message.chart} />}
            </div>
        </div>
    );
}
