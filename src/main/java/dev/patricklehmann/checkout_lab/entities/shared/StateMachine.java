package dev.patricklehmann.checkout_lab.entities.shared;

import dev.patricklehmann.checkout_lab.contracts.TriFunction;
import java.util.EnumMap;
import java.util.EnumSet;

public class StateMachine<S extends Enum<S>> {
    private final EnumMap<S, EnumSet<S>> transitions;
    private final TriFunction<String, S, S, ? extends RuntimeException> invalidTransitionException;

    public StateMachine(
            EnumMap<S, EnumSet<S>> transitions,
            TriFunction<String, S, S, ? extends RuntimeException> invalidTransactionException) {
        EnumMap<S, EnumSet<S>> copy = new EnumMap<>(transitions);
        copy.replaceAll((state, edges) -> edges.clone());

        this.transitions = copy;
        this.invalidTransitionException = invalidTransactionException;
    }

    public S requireValidTransition(S from, S to, String exceptionReference) {
        if (from.equals(to)) {
            return to;
        }

        EnumSet<S> stateTransitions = transitions.get(from);
        if (stateTransitions == null || !stateTransitions.contains(to)) {
            throw invalidTransitionException.apply(exceptionReference, from, to);
        }

        return to;
    }
}
