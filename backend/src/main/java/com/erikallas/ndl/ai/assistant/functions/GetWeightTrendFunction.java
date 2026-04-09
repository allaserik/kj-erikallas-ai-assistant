package com.erikallas.ndl.ai.assistant.functions;

import com.erikallas.ndl.health.weight.WeightEntryEntity;
import com.erikallas.ndl.health.weight.WeightEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Function handler for get_weight_trend.
 *
 * Returns a time-series of weight entries suitable for chart rendering. The
 * response embeds a {@code _chart_data} node (stripped by AssistantService
 * before reaching the AI) so the frontend can render a Recharts line chart
 * without any additional API calls.
 */
@Component
public class GetWeightTrendFunction {

    private final WeightEntryRepository weightRepo;
    private final ObjectMapper om;

    public GetWeightTrendFunction(WeightEntryRepository weightRepo, ObjectMapper om) {
        this.weightRepo = weightRepo;
        this.om = om;
    }

    /**
     * @param userId Authenticated user ID.
     * @param period "30d" (default) or "90d".
     */
    public String execute(UUID userId, String period) {
        boolean wide = "90d".equals(period);
        List<WeightEntryEntity> raw = wide ? weightRepo.findTop90ByUserIdOrderByMeasuredAtDesc(userId)
                : weightRepo.findTop30ByUserIdOrderByMeasuredAtDesc(userId);

        ObjectNode result = om.createObjectNode();
        if (raw.isEmpty()) {
            result.put("message",
                    "No weight entries found. Ask the user to record their first weight entry from the check-in page.");
            return result.toString();
        }

        // Sort ascending by date for chart and trend description
        List<WeightEntryEntity> entries = new ArrayList<>(raw);
        entries.sort(Comparator.comparing(WeightEntryEntity::getMeasuredAt));

        WeightEntryEntity first = entries.get(0);
        WeightEntryEntity last = entries.get(entries.size() - 1);
        double change = roundOne(last.getWeightKg() - first.getWeightKg());

        result.put("entries_count", entries.size());
        result.put("from_date", first.getMeasuredAt().toLocalDate().toString());
        result.put("to_date", last.getMeasuredAt().toLocalDate().toString());
        result.put("start_weight_kg", roundOne(first.getWeightKg()));
        result.put("current_weight_kg", roundOne(last.getWeightKg()));
        result.put("change_kg", change);
        result.put("trend", change < -0.5 ? "declining" : change > 0.5 ? "increasing" : "stable");

        // --- Embedded chart payload (stripped before sending to AI) ---
        ArrayNode points = om.createArrayNode();
        for (WeightEntryEntity e : entries) {
            ObjectNode pt = om.createObjectNode();
            pt.put("x", e.getMeasuredAt().toLocalDate().toString());
            pt.put("y", roundOne(e.getWeightKg()));
            points.add(pt);
        }

        ObjectNode chartData = result.putObject("_chart_data");
        chartData.put("type", "line");
        chartData.put("title", "Weight Trend");
        ArrayNode seriesArr = chartData.putArray("series");
        ObjectNode series = om.createObjectNode();
        series.put("label", "Weight");
        series.put("unit", "kg");
        series.set("data", points);
        seriesArr.add(series);

        return result.toString();
    }

    private static double roundOne(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
