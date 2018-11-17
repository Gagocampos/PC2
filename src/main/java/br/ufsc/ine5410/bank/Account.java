package br.ufsc.ine5410.bank;

import br.ufsc.ine5410.Main;
import br.ufsc.ine5410.trade.NoSuchStockException;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private static AtomicBoolean eden = new AtomicBoolean();
    private @Nonnull Broker broker;
    private final long id;
    private final @Nonnull Investor owner;
    private double balance;
    private Map<String, Long> shares = new HashMap<>();
    private final Lock lock = new ReentrantLock();

    public Account(@Nonnull Broker broker, long id, @Nonnull Investor owner) {
        this.broker = broker;
        this.id = id;
        this.owner = owner;
        this.balance = 0;
    }

    public long getId() {
        return id;
    }

    @Nonnull
    public Broker getBroker() {
        return broker;
    }

    @Nonnull
    public Investor getOwner() {
        return owner;
    }

    public Lock getLock() {
        return lock;
    }

    public void addStock(@Nonnull String code) {
        getLock().lock();
        try {
            Long value = shares.get(code);
            if (value == null) value = 0L;
            shares.put(code, value+1);
        } finally {
            getLock().unlock();
        }
    }
    public void removeStock(@Nonnull String code) throws NoSuchStockException {
        getLock().lock();
        try {
            Long value = shares.get(code);
            if (value == null) value = 0L;
            if (value == 0)
                throw new NoSuchStockException(code);
            shares.put(code, value-1);
        } finally {
            getLock().unlock();
        }
    }

    public double getBalance() {
        return balance;
    }

    public void deposit(double amount) {
        getLock().lock();
        try {
            doComplicatedStuff();
            balance += amount;
        } finally {
            getLock().unlock();
        }
    }

    public boolean withdraw(double amount) {
        getLock().lock();
        try {
            doComplicatedStuff();
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        } finally {
            getLock().unlock();
        }
    }

    private void doComplicatedStuff() {
        if (Main.sacred.get()) {
            try {
                Thread.sleep((long) (Math.random()*7));
            } catch (InterruptedException ignored) {}
        }
    }

    @Override
    public String toString() {
        return String.format("Account(%s, %d)", getBroker(), getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return id == account.id &&
                Double.compare(account.balance, balance) == 0 &&
                Objects.equals(owner, account.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, owner, balance);
    }
}
