package com.thelema.thelemalib.recipe.tool;

public class MathModifyTool {
    /**
     * 算术运算
     */
    public static <T extends Number> double number(String op, Number value, T target) {
        double v = value.doubleValue();
        double t = target.doubleValue();
        return switch (op) {
            case "add", "+" -> t + v;
            case "muti", "*", "multiple" -> t * v;
            case "sub", "-" -> t - v;
            case "divide", "/" -> t / v;
            case "set", "=" -> v;
            default -> throw new IllegalArgumentException("Unknown arithmetic op: " + op);
        };
    }

    /**
     * 比较运算，支持别名
     */
    public static boolean compare(String op, double left, double right) {
        return switch (op) {
            case "=", "==", "equals" -> left == right;
            case "!=", "not_equals" -> left != right;
            case ">", "greater" -> left > right;
            case ">=", "greater_or_equal" -> left >= right;
            case "<", "less" -> left < right;
            case "<=", "less_or_equal" -> left <= right;
            default -> throw new IllegalArgumentException("Unknown comparison op: " + op);
        };
    }

    // 重载：布尔比较（内部转为数值比较）
    public static boolean compare(String op, boolean left, boolean right) {
        return compare(op, left ? 1.0 : 0.0, right ? 1.0 : 0.0);
    }
}