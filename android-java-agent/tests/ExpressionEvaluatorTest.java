import com.javaagent.plugin.ExpressionEvaluator;

public final class ExpressionEvaluatorTest {
    private static int checks;

    public static void main(String[] args) {
        close(14, ExpressionEvaluator.evaluate("2 + 3 * 4"));
        close(20, ExpressionEvaluator.evaluate("(2 + 3) * 4"));
        close(-4, ExpressionEvaluator.evaluate("-2^2"));
        close(512, ExpressionEvaluator.evaluate("2^3^2"));
        close(45, ExpressionEvaluator.evaluate("18 / 100 * 250"));
        close(8, ExpressionEvaluator.evaluate("sqrt(9) + max(2, 5)"));
        close(1002, ExpressionEvaluator.evaluate("1e3 + 2"));
        close(4, ExpressionEvaluator.evaluate("min(4, 8)"));
        close(3, ExpressionEvaluator.evaluate("10 % 7"));
        close(Math.PI, ExpressionEvaluator.evaluate("pi"));
        rejects("1 / 0");
        rejects("2 + ");
        rejects("1e308 * 1e308");
        rejects("System.exit(1)");
        rejects("");
        rejects("(2 + 3");
        System.out.println("ExpressionEvaluatorTest: " + checks + " checks passed");
    }

    private static void close(double expected, double actual) {
        checks++;
        if (Math.abs(expected - actual) > 0.0000001) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void rejects(String expression) {
        checks++;
        try {
            double result = ExpressionEvaluator.evaluate(expression);
            throw new AssertionError("Expected rejection for '" + expression + "' but got " + result);
        } catch (IllegalArgumentException | ArithmeticException expected) {
            // Expected safe parser failure.
        }
    }
}
