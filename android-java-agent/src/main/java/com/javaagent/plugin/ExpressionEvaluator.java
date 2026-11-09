package com.javaagent.plugin;

import java.util.Locale;

/** Small recursive-descent arithmetic parser; deliberately uses no script engine or eval. */
public final class ExpressionEvaluator {
    private final String input;
    private int position;

    private ExpressionEvaluator(String input) {
        this.input = input;
    }

    public static double evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression is empty");
        }
        if (expression.length() > 512) {
            throw new IllegalArgumentException("Expression is too long");
        }
        ExpressionEvaluator parser = new ExpressionEvaluator(expression.toLowerCase(Locale.ROOT));
        double result = parser.parseAdditive();
        parser.skipWhitespace();
        if (parser.position != parser.input.length()) {
            throw new IllegalArgumentException("Unexpected token at position " + (parser.position + 1));
        }
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new ArithmeticException("Result is not finite");
        }
        return result;
    }

    private double parseAdditive() {
        double value = parseMultiplicative();
        while (true) {
            skipWhitespace();
            if (consume('+')) value += parseMultiplicative();
            else if (consume('-')) value -= parseMultiplicative();
            else return value;
        }
    }

    private double parseMultiplicative() {
        double value = parseUnary();
        while (true) {
            skipWhitespace();
            if (consume('*')) value *= parseUnary();
            else if (consume('/')) {
                double divisor = parseUnary();
                if (divisor == 0.0) throw new ArithmeticException("Division by zero");
                value /= divisor;
            } else if (consume('%')) {
                double divisor = parseUnary();
                if (divisor == 0.0) throw new ArithmeticException("Modulo by zero");
                value %= divisor;
            } else return value;
        }
    }

    private double parseUnary() {
        skipWhitespace();
        if (consume('+')) return parseUnary();
        if (consume('-')) return -parseUnary();
        return parsePower();
    }

    private double parsePower() {
        double base = parsePrimary();
        skipWhitespace();
        if (consume('^')) {
            double exponent = parseUnary();
            return checked(Math.pow(base, exponent));
        }
        return base;
    }

    private double parsePrimary() {
        skipWhitespace();
        if (consume('(')) {
            double value = parseAdditive();
            skipWhitespace();
            if (!consume(')')) throw new IllegalArgumentException("Missing closing parenthesis");
            return value;
        }
        if (position >= input.length()) throw new IllegalArgumentException("Expected a number");
        char current = input.charAt(position);
        if (Character.isDigit(current) || current == '.') return parseNumber();
        if (Character.isLetter(current)) return parseIdentifier();
        throw new IllegalArgumentException("Expected a number at position " + (position + 1));
    }

    private double parseNumber() {
        int start = position;
        boolean dot = false;
        while (position < input.length()) {
            char c = input.charAt(position);
            if (Character.isDigit(c)) position++;
            else if (c == '.' && !dot) {
                dot = true;
                position++;
            } else break;
        }
        // Scientific notation is accepted only in its complete, unambiguous form.
        if (position < input.length() && input.charAt(position) == 'e') {
            int exponentStart = position++;
            if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) position++;
            int digits = position;
            while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
            if (digits == position) position = exponentStart;
        }
        try {
            return Double.parseDouble(input.substring(start, position));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid number", error);
        }
    }

    private double parseIdentifier() {
        int start = position;
        while (position < input.length() && Character.isLetter(input.charAt(position))) position++;
        String name = input.substring(start, position);
        if ("pi".equals(name)) return Math.PI;
        if ("e".equals(name)) return Math.E;
        skipWhitespace();
        if (!consume('(')) throw new IllegalArgumentException("Unknown constant: " + name);
        double first = parseAdditive();
        skipWhitespace();
        if (consume(')')) {
            if ("sqrt".equals(name)) {
                if (first < 0) throw new ArithmeticException("Square root of a negative number");
                return checked(Math.sqrt(first));
            }
            if ("abs".equals(name)) return Math.abs(first);
            if ("round".equals(name)) return Math.round(first);
            if ("floor".equals(name)) return Math.floor(first);
            if ("ceil".equals(name)) return Math.ceil(first);
            throw new IllegalArgumentException("Unknown function: " + name);
        }
        if (!consume(',')) throw new IllegalArgumentException("Expected ',' or ')'");
        double second = parseAdditive();
        skipWhitespace();
        if (!consume(')')) throw new IllegalArgumentException("Missing closing parenthesis");
        if ("min".equals(name)) return Math.min(first, second);
        if ("max".equals(name)) return Math.max(first, second);
        if ("pow".equals(name)) return checked(Math.pow(first, second));
        throw new IllegalArgumentException("Unknown function: " + name);
    }

    private static double checked(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ArithmeticException("Result is not finite");
        }
        return value;
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
    }

    private boolean consume(char expected) {
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }
}
