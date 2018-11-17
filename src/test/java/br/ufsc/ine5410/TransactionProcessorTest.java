package br.ufsc.ine5410;

import static br.ufsc.ine5410.trade.Order.State.CANCELLED;
import static br.ufsc.ine5410.trade.Order.State.EXECUTED;
import static org.junit.Assert.assertTrue;

import br.ufsc.ine5410.bank.Account;
import br.ufsc.ine5410.bank.Broker;
import br.ufsc.ine5410.bank.Investor;
import br.ufsc.ine5410.trade.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TransactionProcessorTest extends OrderBookTestBase {

    private Transaction createTransaction(int from, int to, double value) {
        Order buy = new Order(investors.get(from), broker, Order.Type.BUY, "CODE", value);
        Order sell = new Order(investors.get(to), broker, Order.Type.SELL, "CODE", value);
        return new Transaction(sell, buy);
    }

    @Test
    public void testTransfers() {
        processor.process(orderBook, createTransaction(0, 1, 20));

        //close deve garantir que toda transação submetida completa
        processor.close();
        processor = null;

        Assert.assertEquals(980, accounts.get(0).getBalance(), 0);
        Assert.assertEquals(1020, accounts.get(1).getBalance(), 0);
    }

    @Test
    public void testSecondClose() {
        processor.process(orderBook, createTransaction(0, 1, 20));

        //close deve garantir que toda transação submetida completa
        processor.close();

        //segundo close não deve ter efeito
        processor.close();
    }

    @Test
    public void testChecksStock() throws NoSuchStockException {
        for (int i = 0; i < 100; i++)
            accounts.get(1).removeStock("CODE");
        //accounts.get(0) has zero CODE shares: the transaction must not succeed
        Transaction transaction = createTransaction(0, 1, 20);
        processor.process(orderBook, transaction);
        processor.close();
        processor = null;

        //transação não ocorreu
        Assert.assertEquals(1000, accounts.get(0).getBalance(), 0);
        Assert.assertEquals(1000, accounts.get(1).getBalance(), 0);

        //ordem de compra deve voltar para a fila...
        Assert.assertNotEquals(transaction.getBuy().getState(), EXECUTED);
        Assert.assertNotEquals(transaction.getBuy().getState(), CANCELLED);

        //ordem de venda deve ser cancelada
        Assert.assertEquals(transaction.getSell().getState(), CANCELLED);
    }

    @Test
    public void testDeadlock() throws InterruptedException, ExecutionException, TimeoutException {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    processor.process(orderBook, createTransaction(0, 1, 20));
                    processor.process(orderBook, createTransaction(1, 2, 10));
                    processor.process(orderBook, createTransaction(2, 0, 5));
                }
            }));
        }

        Future<?> close = executorService.submit(new Runnable() {
            @Override
            public void run() {
                processor.close();
            }
        });

        for (Future<?> future : futures) {
            //lança TimeoutException se houve ... timeout
            future.get(5, TimeUnit.SECONDS);
        }
        //lança TimeoutException se houve timeout
        close.get(5, TimeUnit.SECONDS);
        processor = null;
    }
}
