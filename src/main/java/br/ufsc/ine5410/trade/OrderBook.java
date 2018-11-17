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
    public ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    ExecutorService executorService = Executors.newCachedThreadPool();

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

    public synchronized void post(@Nonnull Order order) {
        if (!order.getStock().equals(stockCode)) {
            String msg = toString() + " cannot process orders for " + order.getStock();
            throw new IllegalArgumentException(msg);
        }
        if (closed) {
            order.notifyCancellation();
            return;
        }
        (order.getType() == BUY ? buyOrders : sellOrders).add(order);
        order.notifyQueued();
        tryMatch();
    }

    private void tryMatch() {
        Order sell, buy;
        while (comparar() == true) {
            lock.writeLock().lock();
            sell = sellOrders.peek();
            buy = buyOrders.peek();
            Order removed = sellOrders.remove();
            assert removed == sell;
            removed = buyOrders.remove();
            assert removed == buy;
            lock.writeLock().unlock();

            final Order finalSell = sell;
            final Order finalBuy = buy;
            if (finalSell.getPrice() <= finalBuy.getPrice()) {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        Transaction trans = new Transaction(finalSell, finalBuy);
                        finalSell.notifyProcessing();
                        finalBuy.notifyProcessing();
                        transactionProcessor.process(OrderBook.this, trans);
                    }
                });
            } else {
                break;
            }
        }
    }

    private boolean comparar(){
        lock.readLock().lock();
        if(sellOrders.peek() != null && buyOrders.peek() != null){
            lock.readLock().unlock();
            return true;
        }else{
            lock.readLock().unlock();
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("OrderBook(%s)", stockCode);
    }

    @Override
    public void close()  {
        if (closed) return;
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            closed = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //any future post() call will be a no-op

        for (Order order : sellOrders) order.notifyCancellation();
        sellOrders.clear();
        for (Order order : buyOrders) order.notifyCancellation();
        buyOrders.clear();
    }
}