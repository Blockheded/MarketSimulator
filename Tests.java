package orderbook;

import java.util.List;
import java.util.Random;

/** Dependency-free tests. Swap for JUnit once you set up Maven/Gradle. */
public final class Tests {
    private static int passed = 0;

    private static void check(boolean cond, String name) {
        if (!cond) throw new AssertionError("FAILED: " + name);
        passed++;
    }

    public static void main(String[] args) {
        restsWhenNotMarketable();
        fullAndPartialFills();
        priceTimePriority();
        tradesAtRestingPrice();
        cancelRemovesOrder();
        marketOrderSweepsLevels();
        fuzzNeverCrossesBook();
        System.out.println("All " + passed + " checks passed.");
    }

    static void restsWhenNotMarketable() {
        OrderBook b = new OrderBook();
        check(b.submitLimit(1, Side.BUY, 99, 10).isEmpty(), "bid rests");
        check(b.submitLimit(2, Side.SELL, 101, 10).isEmpty(), "ask rests");
        check(b.bestBid().getAsLong() == 99 && b.bestAsk().getAsLong() == 101, "best bid/ask");
    }

    static void fullAndPartialFills() {
        OrderBook b = new OrderBook();
        b.submitLimit(1, Side.SELL, 100, 10);
        List<Trade> t = b.submitLimit(2, Side.BUY, 100, 4);
        check(t.size() == 1 && t.get(0).quantity() == 4, "partial fill of maker");
        check(b.depthAt(Side.SELL, 100) == 6, "maker remainder rests");
        t = b.submitLimit(3, Side.BUY, 100, 10);
        check(t.size() == 1 && t.get(0).quantity() == 6, "taker fills what's there");
        check(b.depthAt(Side.BUY, 100) == 4, "taker remainder rests as bid");
    }

    static void priceTimePriority() {
        OrderBook b = new OrderBook();
        b.submitLimit(1, Side.SELL, 100, 5);   // first at 100
        b.submitLimit(2, Side.SELL, 100, 5);   // second at 100
        b.submitLimit(3, Side.SELL, 99, 5);    // better price, arrives last
        List<Trade> t = b.submitMarket(4, Side.BUY, 12);
        check(t.get(0).makerId() == 3, "best price first");
        check(t.get(1).makerId() == 1, "then earliest at that price");
        check(t.get(2).makerId() == 2 && t.get(2).quantity() == 2, "then next in queue");
    }

    static void tradesAtRestingPrice() {
        OrderBook b = new OrderBook();
        b.submitLimit(1, Side.SELL, 100, 5);
        List<Trade> t = b.submitLimit(2, Side.BUY, 105, 5);
        check(t.get(0).price() == 100, "price improvement goes to the taker");
    }

    static void cancelRemovesOrder() {
        OrderBook b = new OrderBook();
        b.submitLimit(1, Side.BUY, 99, 5);
        check(b.cancel(1), "cancel succeeds");
        check(!b.cancel(1), "second cancel fails");
        check(b.bestBid().isEmpty(), "level cleaned up");
    }

    static void marketOrderSweepsLevels() {
        OrderBook b = new OrderBook();
        b.submitLimit(1, Side.SELL, 100, 3);
        b.submitLimit(2, Side.SELL, 101, 3);
        List<Trade> t = b.submitMarket(3, Side.BUY, 10);
        check(t.size() == 2 && b.bestAsk().isEmpty(), "sweeps both levels");
        check(b.restingOrders() == 0, "unfilled market remainder is discarded");
    }

    static void fuzzNeverCrossesBook() {
        OrderBook b = new OrderBook();
        Random rng = new Random(42);
        long id = 1;
        for (int i = 0; i < 200_000; i++) {
            int action = rng.nextInt(10);
            if (action < 6) {
                Side s = rng.nextBoolean() ? Side.BUY : Side.SELL;
                b.submitLimit(id++, s, 1000 + rng.nextInt(41) - 20, 1 + rng.nextInt(20));
            } else if (action < 8) {
                b.submitMarket(id++, rng.nextBoolean() ? Side.BUY : Side.SELL, 1 + rng.nextInt(20));
            } else {
                b.cancel(1 + rng.nextInt((int) id));
            }
            if (b.bestBid().isPresent() && b.bestAsk().isPresent()) {
                check(b.bestBid().getAsLong() < b.bestAsk().getAsLong(), "book never crossed");
            }
        }
    }
}
