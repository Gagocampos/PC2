package br.ufsc.ine5410.trade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;

public class Exchange  implements AutoCloseable  {
    private final HashMap<String, OrderBook> books = new HashMap<>();
    private final TransactionProcessor transactionProcessor = new TransactionProcessor();

    @Nonnull
    public OrderBook registerStock(@Nonnull String stockCode) {
        synchronized (books) {
            OrderBook book = books.get(stockCode);
            if (book == null) {
                book = new OrderBook(stockCode, transactionProcessor);
                books.put(stockCode, book);
            }
            return book;
        }
    }

    @Nullable
    public OrderBook getOrderBook(@Nonnull String stockCode) {
        synchronized (books) {
            return books.get(stockCode);
        }
    }

    public void post(@Nonnull Order order) throws NoSuchStockException {
        OrderBook orderBook = getOrderBook(order.getStock());
        if (orderBook == null) throw new NoSuchStockException(order.getStock());
        orderBook.post(order);
    }

    @Override
    public void close() {
        transactionProcessor.close();
        for (OrderBook book : books.values()) {
            book.close();
        }
        books.clear();
    }
}
