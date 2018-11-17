package br.ufsc.ine5410;

import br.ufsc.ine5410.bank.Broker;
import br.ufsc.ine5410.bank.Investor;
import br.ufsc.ine5410.trade.Order;
import org.apache.commons.lang3.time.StopWatch;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EnhancedOrder extends Order {
    public EnhancedOrder(@Nonnull Investor investor, @Nonnull Broker broker, @Nonnull Type type, @Nonnull String code, double price) {
        super(investor, broker, type, code, price);
        addListener(new Listener() {
            @Override
            public void executed(Order order) {
                synchronized (EnhancedOrder.this) {
                    EnhancedOrder.this.notifyAll();
                }
            }

            @Override
            public void cancelled(Order order) {
                synchronized (EnhancedOrder.this) {
                    EnhancedOrder.this.notifyAll();
                }
            }
        });
    }

    public synchronized void waitTerminated(long value, TimeUnit timeUnit)
            throws InterruptedException, TimeoutException {
        StopWatch watch = StopWatch.createStarted();
        long milliseconds = TimeUnit.MILLISECONDS.convert(value, timeUnit);
        while (!isTerminated() && watch.getTime() < milliseconds)
            wait(Math.max(0, milliseconds - watch.getTime()));
        if (!isTerminated())
            throw new TimeoutException();
    }

    public boolean isTerminated() {
        return getState() == State.EXECUTED || getState() == State.CANCELLED;
    }
}
