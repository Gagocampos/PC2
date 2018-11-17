package br.ufsc.ine5410.trade;

import br.ufsc.ine5410.bank.Broker;
import br.ufsc.ine5410.bank.Investor;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Objects;

public class Order {
    private final double price;
    private final @Nonnull Investor investor;
    private final @Nonnull Broker broker;
    private final Type type;
    private final @Nonnull String code;
    private final @Nonnull LinkedHashSet<Listener> listeners = new LinkedHashSet<>();
    private @Nonnull State state = State.CREATED;

    public  enum State {
        CREATED,
        QUEUED,
        PROCESSING,
        EXECUTED,
        CANCELLED;
    }

    public enum Type {
        BUY,
        SELL
    }

    public interface Listener {
        void executed(Order order);
        void cancelled(Order order);
    }

    public Order(@Nonnull Investor investor, @Nonnull Broker broker,
                 @Nonnull Type type, @Nonnull String code, double price) {
        this.investor = investor;
        this.broker = broker;
        this.type = type;
        this.code = code;
        this.price = price;
    }

    @Nonnull
    public State getState() {
        return state;
    }

    public double getPrice() {
        return price;
    }

    @Nonnull
    public Investor getInvestor() {
        return investor;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order that = (Order) o;
        return Double.compare(that.getPrice(), getPrice()) == 0 &&
                Objects.equals(getInvestor(), that.getInvestor()) &&
                Objects.equals(getBroker(), that.getBroker()) &&
                Objects.equals(getType(), that.getType()) &&
                Objects.equals(code, that.code);
    }

    public int hashCode() {
        return Objects.hash(getPrice(), getInvestor(), getBroker(), getType(), code);
    }

    public String toString() {
        return String.format("Order(%s, %s, %s, price=%f, %s)",
                getInvestor(), getType(), getStock(), getPrice(), getState());
    }

    @Nonnull
    public Broker getBroker() {
        return broker;
    }

    public Type getType() {
        return type;
    }

    public String getStock() {
        return code;
    }

    public void addListener(@Nonnull Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(@Nonnull Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void notifyQueued() {
        state = State.QUEUED;
    }

    public void notifyProcessing() {
        state = State.PROCESSING;
    }

    public void notifyExecution() {
        state = State.EXECUTED;
        synchronized (listeners) {
            for (Listener listener : listeners)
                listener.executed(this);
        }
    }

    public void notifyCancellation() {
        state = State.CANCELLED;
        synchronized (listeners) {
            for (Listener listener : listeners) listener.cancelled(this);
        }
    }
}
