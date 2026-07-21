package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patricklehmann.checkout_lab.entities.shared.Money;
import org.junit.jupiter.api.Test;

class MoneyTests {

    @Test
    void formatsTypicalPrice() {
        assertThat(Money.ofCents(1999).formatted()).isEqualTo("19.99");
    }

    @Test
    void formatsWholeEurosWithTwoFractionDigits() {
        assertThat(Money.ofCents(1900).formatted()).isEqualTo("19.00");
    }

    @Test
    void zeroPadsCentsBelowTen() {
        assertThat(Money.ofCents(5).formatted()).isEqualTo("0.05");
    }

    @Test
    void formatsZero() {
        assertThat(Money.zero().formatted()).isEqualTo("0.00");
    }

    @Test
    void plusAddsAmounts() {
        assertThat(Money.ofCents(1999).plus(Money.ofCents(1))).isEqualTo(Money.ofCents(2000));
    }

    @Test
    void timesMultipliesByQuantity() {
        assertThat(Money.ofCents(1999).times(3)).isEqualTo(Money.ofCents(5997));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.ofCents(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusFailsLoudlyOnOverflowInsteadOfWrapping() {
        Money max = Money.ofCents(Long.MAX_VALUE);
        assertThatThrownBy(() -> max.plus(Money.ofCents(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void timesFailsLoudlyOnOverflowInsteadOfWrapping() {
        Money max = Money.ofCents(Long.MAX_VALUE);
        assertThatThrownBy(() -> max.times(2)).isInstanceOf(ArithmeticException.class);
    }
}
