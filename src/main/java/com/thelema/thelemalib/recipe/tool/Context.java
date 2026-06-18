package com.thelema.thelemalib.recipe.tool;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public final class Context {
    public ItemStack current;
    public final List<ItemStack> inputs;
    public final List<ItemStack> output;

    public Context(List<ItemStack> inputs, List<ItemStack> output) {
        this.current = output.getFirst();
        this.inputs = inputs;
        this.output = output;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        Context that = (Context) obj;
        return Objects.equals(current, that.current) &&
               Objects.equals(inputs, that.inputs) &&
               Objects.equals(output, that.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(current, inputs, output);
    }

    @Override
    public String toString() {
        return "Context[" +
               "current=" + current + ", " +
               "inputs=" + inputs + ", " +
               "output=" + output + ']';
    }
}