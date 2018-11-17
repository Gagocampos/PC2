package br.ufsc.ine5410.bank;

import java.util.Objects;

public class Investor {
    private final long id;

    public Investor(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("Investor(%d)", getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Investor investor = (Investor) o;
        return id == investor.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
