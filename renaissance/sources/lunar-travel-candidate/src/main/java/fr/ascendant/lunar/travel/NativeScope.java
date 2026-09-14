package fr.ascendant.lunar.travel;

import java.util.function.Supplier;

public final class NativeScope<T> {
    private final ThreadLocal<T> current = new ThreadLocal<>();
    public T current() { return current.get(); }
    public void clear() { current.remove(); }

    public <R> R call(T context, Supplier<R> body) {
        if (current.get() != null) throw new IllegalStateException("Nested native travel scope");
        current.set(java.util.Objects.requireNonNull(context));
        try { return body.get(); }
        finally { current.remove(); }
    }
}
