package dev.patricklehmann.checkout_lab.contracts;

@FunctionalInterface
public interface TriFunction<T, U, V, R> {
    R apply(T t, U u, V v);
}
