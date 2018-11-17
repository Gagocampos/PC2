package br.ufsc.ine5410;

import br.ufsc.ine5410.trade.Order;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static br.ufsc.ine5410.trade.Order.State.CANCELLED;
import static br.ufsc.ine5410.trade.Order.State.EXECUTED;

public class OrderBookTest extends OrderBookTestBase {

    private EnhancedOrder createOrder(int investor, double price, EnhancedOrder.Type type) {
        return new EnhancedOrder(investors.get(investor), broker, type, "CODE", price);
    }
    private EnhancedOrder postBuy(int investor, double price) {
        EnhancedOrder order = createOrder(investor, price, EnhancedOrder.Type.BUY);
        orderBook.post(order);
        return order;
    }
    private EnhancedOrder postSell(int investor, double price) {
        EnhancedOrder order = createOrder(investor, price, Order.Type.SELL);
        orderBook.post(order);
        return order;
    }

    private void close() {
        processor.close();
        orderBook.close();
    }

    @Test(timeout = 2000)
    public void addExactMatchingOrders() throws InterruptedException, TimeoutException {
        EnhancedOrder buy = postBuy(0, 20);
        EnhancedOrder sell = postSell(1, 20);

        //ordens devem ser executadas eventualmente
        buy.waitTerminated(2, TimeUnit.SECONDS);
        Assert.assertEquals(EXECUTED, buy.getState());
        sell.waitTerminated(2, TimeUnit.SECONDS);
        Assert.assertEquals(EXECUTED, sell.getState());

        close(); //mesmo que paralelizado, isso deve terminar tarefas pendentes

        //ordens já executadas não foram canceladas
        Assert.assertEquals(EXECUTED, buy.getState());
        Assert.assertEquals(EXECUTED, sell.getState());

        //investidor 0 paga R$ 20
        Assert.assertEquals(980, accounts.get(0).getBalance(), 0);
        //investidor 1 recebe R$ 20
        Assert.assertEquals(1020, accounts.get(1).getBalance(), 0);
    }

    @Test(timeout = 2000)
    public void testCloseWaits() {
        EnhancedOrder buy = postBuy(0, 20);
        EnhancedOrder sell = postSell(1, 20);

        close();

        //não há garantia que haja tempo das ordens executarem. Pode ser EXECUTED ou CANCELLED
        Assert.assertTrue(buy.isTerminated());
        Assert.assertTrue(sell.isTerminated());

        //mas se um terminou em EXECUTED, o outro também deveria
        Assert.assertEquals(buy.getState(), sell.getState());

        //se terminaram em EXECUTED, a transferencia aconteceu
        if (buy.getState() == EXECUTED) {
            //investidor 0 paga R$ 20
            Assert.assertEquals(980, accounts.get(0).getBalance(), 0);
            //investidor 1 recebe R$ 20
            Assert.assertEquals(1020, accounts.get(1).getBalance(), 0);
        }
    }

    @Test(timeout = 2000)
    public void testDoubleClose() {
        EnhancedOrder buy = postBuy(0, 20);
        EnhancedOrder sell = postSell(1, 20);

        close(); //ordens que não executaram a tempo são canceladas
        Assert.assertTrue(buy.isTerminated());
        Assert.assertTrue(sell.isTerminated());

        close(); //sem efeito. não deve causar problemas
        close();

        Assert.assertTrue(buy.isTerminated());
        Assert.assertTrue(sell.isTerminated());
    }

    @Test(timeout = 2000)
    public void addSingleSell() throws InterruptedException {
        EnhancedOrder sell = postSell(0, 20);

        boolean caught = false;
        try {
            sell.waitTerminated(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            caught = true;
        }
        Assert.assertTrue(caught); //timeout deve ocorrer

        close();
        //ordem cancelada
        Assert.assertEquals(CANCELLED, sell.getState());
    }

    @Test(timeout = 2000)
    public void testAddSingleBuy() throws InterruptedException {
        EnhancedOrder buy = postBuy(0, 23);

        boolean caught = false;
        try {
            buy.waitTerminated(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            caught = true;
        }
        Assert.assertTrue(caught); //timeout deve ocorrer

        close();
        //ordem cancelada
        Assert.assertEquals(CANCELLED, buy.getState());
    }

    @Test(timeout = 2000)
    public void testAddHigherBuy() throws TimeoutException, InterruptedException {
        EnhancedOrder sell = postSell(0, 23);
        EnhancedOrder buy = postBuy(1, 27);

        //ordens executam
        buy.waitTerminated(2, TimeUnit.SECONDS);
        Assert.assertEquals(EXECUTED, buy.getState());
        sell.waitTerminated(2, TimeUnit.SECONDS);
        Assert.assertEquals(EXECUTED, sell.getState());

        close();

        //já executadas e não foram canceladas...
        Assert.assertEquals(EXECUTED, buy.getState());
        Assert.assertEquals(EXECUTED, sell.getState());
    }

    @Test(timeout = 2000)
    public void testAddLowerSell() throws TimeoutException, InterruptedException {
        EnhancedOrder buy = postBuy(1, 27);
        EnhancedOrder sell = postSell(0, 23);

        //ordens executam
        buy.waitTerminated(2, TimeUnit.SECONDS);
        Assert.assertEquals(EXECUTED, buy.getState());
        sell.waitTerminated(2, TimeUnit.SECONDS);
        Assert.assertEquals(EXECUTED, sell.getState());

        close();

        //já executadas e não foram canceladas...
        Assert.assertEquals(EXECUTED, buy.getState());
        Assert.assertEquals(EXECUTED, sell.getState());
    }

    @Test(timeout = 4000)
    public void addBuysAndSells() throws InterruptedException, TimeoutException {
        //testa ordens da figura no enunciado do trabalho
        //começa adicionando ordens incompatíveis entre sí que não serão executadas
        List<EnhancedOrder> leftovers = new ArrayList<>();
        leftovers.add(postSell(0, 27));
        leftovers.add(postSell(1, 27));
        leftovers.add(postSell(2, 26));
        leftovers.add(postBuy(3, 24));

        //não há match
        Thread.sleep(500);
        for (EnhancedOrder order : leftovers)
            Assert.assertFalse(order.isTerminated());

        //essas duas ordens de venda serão executadas...
        List<EnhancedOrder> executed = new ArrayList<>();
        executed.add(postSell(4, 25));
        executed.add(postSell(4, 26));

        //... mas não agora
        Thread.sleep(500);
        for (EnhancedOrder sell : executed)
            Assert.assertFalse(sell.isTerminated());

        //os dois primeiros buys darão match com os sells, o terceiro irá sobrar
        executed.add(postBuy(5, 28));
        executed.add(postBuy(6, 26));
        leftovers.add(postBuy(7, 25));

        //os dois buys executam e dois sells executam...
        for (EnhancedOrder order : executed) {
            order.waitTerminated(2, TimeUnit.SECONDS);
            Assert.assertEquals(EXECUTED, order.getState());
        }
        //os outros não...
        for (EnhancedOrder order : leftovers) {
            Assert.assertNotEquals(EXECUTED, order.getState());
            Assert.assertNotEquals(CANCELLED, order.getState());
        }
    }

    @Test(timeout = 20000)
    public void testMultithreading() throws TimeoutException, InterruptedException {
        final Queue<EnhancedOrder> orders = new ConcurrentLinkedQueue<>();

        //para cada um dos 10 investidores...
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            //rode em paralelo uma tarefa que...
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    if ((idx % 2) == 0) {
                        //para investidores pares, vende com preço em [10, 20)
                        for (int j = 0; j < 30; j++) {
                            orders.add(postSell(idx, 10+idx));
                        }
                    } else {
                        //para investidores ímpares, compra com preço em [20, 30)
                        //o saldo inicial de R$ 1000 permite até 33 compras
                        for (int j = 0; j < 30; j++) {
                            orders.add(postBuy(idx, 20+idx));
                        }
                    }
                }
            });
        }

        executorService.shutdown();
        Assert.assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));

        //todas as ordens serão executadas
        for (EnhancedOrder order : orders) {
            order.waitTerminated(2, TimeUnit.SECONDS);
            Assert.assertEquals(EXECUTED, order.getState());
        }
    }
}