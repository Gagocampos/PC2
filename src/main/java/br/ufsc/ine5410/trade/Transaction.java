package br.ufsc.ine5410.trade;

import javax.annotation.Nonnull;
import java.util.Objects;

public class Transaction {
    private final @Nonnull
    Order sell, buy;

    public Transaction(@Nonnull Order sell, @Nonnull Order buy) {
        this.sell = sell;
        this.buy = buy;
    }

    @Nonnull
    public Order getSell() {
        return sell;
    }

    @Nonnull
    public Order getBuy() {
        return buy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(getSell(), that.getSell()) &&
                Objects.equals(getBuy(), that.getBuy());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSell(), getBuy());
    }

    @Override
    public String toString() {
        return String.format("%s <-> %s", getSell(), getBuy());
    }
}
