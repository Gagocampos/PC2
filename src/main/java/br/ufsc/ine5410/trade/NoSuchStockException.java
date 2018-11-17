package br.ufsc.ine5410.trade;

public class NoSuchStockException extends Exception {
    private final String stockCode;

    public NoSuchStockException(String stockCode) {
        this.stockCode = stockCode;
    }

    public String getStockCode() {
        return stockCode;
    }

    @Override
    public String toString() {
        return String.format("NoSuchStockException(%s)", getStockCode());
    }
}
