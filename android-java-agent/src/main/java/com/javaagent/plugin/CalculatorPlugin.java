package com.javaagent.plugin;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;

public final class CalculatorPlugin implements AgentPlugin {
    @Override public String getId() { return "calculator"; }
    @Override public String getName() { return "Calculator"; }
    @Override public String getDescription() {
        return "Evaluate arithmetic accurately. Supports +, -, *, /, %, ^, parentheses, pi, e, sqrt, abs, round, floor, ceil, min, max and pow.";
    }

    @Override public JSONObject getParametersSchema() {
        try {
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject().put("expression",
                            new JSONObject().put("type", "string").put("description", "Arithmetic expression to evaluate")))
                    .put("required", new org.json.JSONArray().put("expression"))
                    .put("additionalProperties", false);
        } catch (JSONException error) {
            throw new AssertionError(error);
        }
    }

    public static String calculate(String expression) {
        double value = ExpressionEvaluator.evaluate(expression);
        if (Math.abs(value) < 1e-12) value = 0.0;
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @Override public String execute(Context context, JSONObject arguments) throws Exception {
        String expression = arguments.getString("expression");
        return calculate(expression);
    }
}
