package br.ufsc.ine5410.trade;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static br.ufsc.ine5410.trade.Order.Type.*;

public class OrderBook extends Thread implements AutoCloseable {
    private final @Nonnull String stockCode;
    public final @Nonnull TransactionProcessor transactionProcessor;
    public final @Nonnull PriorityQueue<Order> sellOrders, buyOrders;
    private boolean closed = false;
    private ReentrantLock lock = new ReentrantLock();
    private Lock closedLock = new ReentrantLock();
    ExecutorService executorService = Executors.newCachedThreadPool();
    ExecutorService tryMatchExecutor = Executors.newCachedThreadPool();

    public OrderBook(@Nonnull String stockCode,
                     @Nonnull TransactionProcessor transactionProcessor) {
        this.stockCode = stockCode;
        this.transactionProcessor = transactionProcessor;
        sellOrders = new PriorityQueue<>(100, new Comparator<Order>() {
            @Override
            public int compare(@Nonnull Order l, @Nonnull Order r) {
                return Double.compare(l.getPrice(), r.getPrice());
            }
        });
        buyOrders = new PriorityQueue<>(100, new Comparator<Order>() {
            @Override
            public int compare(@Nonnull Order l, @Nonnull Order r) {
                return Double.compare(r.getPrice(), l.getPrice());
            }
        });
    }

    private boolean comparaClosed(){
        closedLock.lock();
        if(closed) {
            return true;
        }
        else{
            return false;
        }
    }

    public synchronized void post(@Nonnull Order order) {
        if (!order.getStock().equals(stockCode)) {
            String msg = toString() + " cannot process orders for " + order.getStock();
            throw new IllegalArgumentException(msg);
        }
        if (comparaClosed()) {
            closedLock.unlock();
            order.notifyCancellation();
            return;
        }
        closedLock.unlock();
        lock.lock();
        (order.getType() == BUY ? buyOrders : sellOrders).add(order);
        lock.unlock();
        order.notifyQueued();
        tryMatchExecutor.execute(new Runnable() {
            @Override
            public void run() {
                tryMatch();
            }
        });
    }

    private void tryMatch() {
        Order sell, buy;
        while (comparar()) {
            sell = sellOrders.peek();
            buy = buyOrders.peek();
            Order removed = sellOrders.remove();
            assert removed == sell;
            removed = buyOrders.remove();
            assert removed == buy;
            lock.unlock();

            final Order finalSell = sell;
            final Order finalBuy = buy;
            if (finalSell.getPrice() <= finalBuy.getPrice()) {
                finalSell.notifyProcessing();
                finalBuy.notifyProcessing();
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        Transaction trans = new Transaction(finalSell, finalBuy);
                        transactionProcessor.process(OrderBook.this, trans);
                    }
                });
            } else {
                break;
            }
        }if(lock.isLocked())
            lock.unlock();
    }

    private boolean comparar(){
        lock.lock();
        if(sellOrders.peek() != null && buyOrders.peek() != null){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("OrderBook(%s)", stockCode);
    }

    @Override
    public void close()  {
        if (comparaClosed()){
            closedLock.unlock();
            return;
        }
        closed = true;
        closedLock.unlock();
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //any future post() call will be a no-op

        for (Order order : sellOrders) order.notifyCancellation();
        sellOrders.clear();
        for (Order order : buyOrders) order.notifyCancellation();
        buyOrders.clear();
        tryMatchExecutor.shutdown();
        try {
            tryMatchExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
