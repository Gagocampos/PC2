package br.ufsc.ine5410;

import br.ufsc.ine5410.bank.Account;
import br.ufsc.ine5410.bank.Broker;
import br.ufsc.ine5410.bank.Investor;
import br.ufsc.ine5410.trade.Exchange;
import br.ufsc.ine5410.trade.NoSuchStockException;
import br.ufsc.ine5410.trade.Order;
import org.apache.commons.lang3.time.StopWatch;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static br.ufsc.ine5410.trade.Order.Type.*;
import static java.util.Collections.synchronizedSet;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("FieldCanBeLocal")
public class Main  {
    public enum Source {
        SEPT_2018,
        YEAR_2018;

        String getFilename() {
            switch (this) {
                case YEAR_2018: return "2018.csv";
                case SEPT_2018: return "2018-09.csv";
                default: throw new UnsupportedOperationException();
            }
        }
    };


    @Option(name = "--help", aliases = {"-h"}, help = true)
    private boolean help;

    @Option(name = "--source", aliases = {"-s"},
            usage = "Fonte de dados para gerar as ordens de compra e venda")
    private Source source = Source.SEPT_2018;

    @Option(name = "--proportion", aliases = {"-p"}, usage = "Dispara apenas " +
            "essa proporção do número de negócios executados no dia de referência " +
            "(de acordo com --source). O padrão é disparar apenas 5% do total de ordens")
    private double postProportion = 0.05;

    @Option(name = "--single-stock", aliases = {"-o"},
            usage = "Apenas uma ação será negociada, ao invés de 20")
    private boolean singleStock = false;

    @Option(name = "--n-investors", aliases = {"-i"}, usage = "Número de Investidores")
    private int numInvestors = 20000;

    @Option(name = "--n-brokers", aliases = {"-b"}, usage = "Número de corretoras")
    private int numBrokers = 5;

    private List<Investor> investors;
    private Map<Investor, Broker> brokerOf;
    public static ThreadLocal<Boolean> sacred = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    private final @Nonnull AtomicInteger executed = new AtomicInteger(),
            cancelled = new AtomicInteger(),
            issued = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        Main app = new Main();
        CmdLineParser parser = new CmdLineParser(app);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            app.help = true;
            System.err.printf("Argumentos inválidos! %s\n", e.getLocalizedMessage());
        }
        if (app.help) {
            parser.printSingleLineUsage(System.out);
            System.out.println();
            parser.printUsage(System.out);
        } else {
            app.run();
        }
    }

    private void run() throws Exception {
        List<Broker> brokers = new ArrayList<>();
        for (int i = 0; i < numBrokers; i++) brokers.add(new Broker());

        brokerOf = new HashMap<>();
        investors = new ArrayList<>(numInvestors);
        for (int i = 0; i < numInvestors; i++) {
            Investor investor = new Investor(i + 100);
            investors.add(investor);
            Broker broker = brokers.get((int) (Math.random() * brokers.size()));
            broker.createAccount(investor);
            brokerOf.put(investor, broker);
        }

        ExecutorService executorService = Executors.newCachedThreadPool();

        StopWatch watch = StopWatch.createStarted();
        try (Exchange exchange = new Exchange()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Main.class.getResourceAsStream("/" + source.getFilename())))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String finalLine = line;
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            dispatchOrders(exchange, finalLine);
                        }
                    });
                    if (singleStock) break;
                }
            }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        }

        double seconds = watch.getTime(TimeUnit.MILLISECONDS) / 1000.0;
        System.out.printf("------------------------------------------------\n" +
                        "Enviou %d ordens em %f segundos.\n" +
                        "%d executadas, %d canceladas, %d perdidas e %d inventadas\n" +
                        "Throughput : %f ordens/sec\n" +
                        "           : %f execuções/sec\n" +
                        "------------------------------------------------\n",
                issued.get(), seconds, executed.get(), cancelled.get(),
                issued.get() - (executed.get() + cancelled.get()),
                executed.get() - issued.get(),
                issued.get()/seconds, executed.get()/seconds);
    }

    private void dispatchOrders(final Exchange exchange, @Nonnull String line) {
        String[] pieces = line.split(", ");
        final String code = pieces[0];
        exchange.registerStock(code);

        //for each day...
        final int days = (pieces.length-2)/2 + 1;
        for (int i = 1; i < pieces.length; i += 2) {
            final Set<Order> activeOrders = synchronizedSet(new HashSet<Order>());
            final int day = i / 2 + 1;
            double price = Double.parseDouble(pieces[i]);
            int negs = getNegotiations(pieces[i + 1]);
            CountDownLatch latch = new CountDownLatch(2*negs);
            System.out.printf("Dia %3d/%d -- %s gerando %d ordens...\n", day, days, code, negs);

            OrderFactory factory = new OrderFactory(code, price, activeOrders, exchange, latch);
            //issue SELL and BUY orders for each negotiation
            int investorIdx = 0;
            for (int j = 0; j < negs; j++) {
                factory.dispatch(SELL, investorIdx++);
                factory.dispatch(BUY, investorIdx++);
            }
            try {
                if (!latch.await(5, TimeUnit.SECONDS))
                    latch.await();
            } catch (InterruptedException ignored) {}
        }
    }

    private int getNegotiations(String piece) {
        //limit negotiations to half of investors
        return (int) Math.ceil(postProportion
                * Math.min(Integer.parseInt(piece), investors.size() / 2 - 1));
    }

    private class OrderFactory {
        private final @Nonnull String code;
        private final double price;
        private final @Nonnull Set<Order> active;
        private final @Nonnull Exchange exchange;
        private final @Nonnull CountDownLatch latch;

        public OrderFactory(@Nonnull String code, double price,
                            @Nonnull Set<Order> activeOrders,
                            @Nonnull Exchange exchange,
                            @Nonnull CountDownLatch latch) {
            this.code = code;
            this.price = price;
            this.active = activeOrders;
            this.exchange = exchange;
            this.latch = latch;
        }

        public void dispatch(Order.Type type, int investorIdx) {
            double delta = (type == SELL ? -1 : 1) * Math.ceil(Math.random() * 200)/100;
            Investor investor = investors.get(investorIdx);
            double orderPrice = this.price + delta;
            Order order = new Order(investor, brokerOf.get(investor), type,
                    code, orderPrice);

            //ensure buyer has money and that seller has the stock
            Account account = requireNonNull(brokerOf.get(investor).getAccount(investor));
            if (type == BUY) {
                try {
                    sacred.set(false);
                    account.deposit(Math.ceil(orderPrice));
                } finally {
                    sacred.set(true);
                }
            } else if (type == SELL) {
                account.addStock(code);
            }

            // setup listener
            active.add(order);
            issued.incrementAndGet();
            order.addListener(new Order.Listener() {
                @Override
                public void executed(@Nonnull Order order) {
                    executed.incrementAndGet();
                    active.remove(order);
                    latch.countDown();
                }

                @Override
                public void cancelled(@Nonnull Order order) {
                    cancelled.incrementAndGet();
                    active.remove(order);
                    latch.countDown();
                }
            });

            // dispatch
            try {
                exchange.post(order);
            } catch (NoSuchStockException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
