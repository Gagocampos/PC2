package br.ufsc.ine5410;

import br.ufsc.ine5410.bank.Account;
import br.ufsc.ine5410.bank.Broker;
import br.ufsc.ine5410.bank.Investor;
import br.ufsc.ine5410.trade.OrderBook;
import br.ufsc.ine5410.trade.TransactionProcessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OrderBookTestBase {
    protected List<Investor> investors;
    protected List<Account> accounts;
    protected Broker broker;
    protected TransactionProcessor processor;
    protected OrderBook orderBook;
    protected ExecutorService executorService;

    @Before
    public void setUp() {
        broker = new Broker();
        investors = new ArrayList<>();
        accounts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Investor investor = new Investor(i);
            investors.add(investor);
            Account acc = broker.createAccount(investor);
            acc.deposit(1000);
            for (int j = 0; j < 100; j++) acc.addStock("CODE");
            accounts.add(acc);
        }
        processor = new TransactionProcessor();
        orderBook = new OrderBook("CODE", processor);
        executorService = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() throws InterruptedException {
        executorService.shutdown();
        if (processor != null)
            processor.close();
        orderBook.close();

        Assert.assertTrue(executorService.awaitTermination(2, TimeUnit.SECONDS));
    }
}
