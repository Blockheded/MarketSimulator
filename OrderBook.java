package orderbook;

import java.util.*;

/**
 * Single-instrument limit order book with price-time priority.
 *
 * v1 data structures:
 *  - TreeMap<price, Level> per side (best price is always firstEntry)
 *  - LinkedHashMap per level: FIFO order within a price, O(1) cancel
 *  - HashMap<id, Order> so cancels don't need to know the price
 */
public final class OrderBook {

    private static final class Level {
        final LinkedHashMap<Long, Order> orders = new LinkedHashMap<>();
        long totalQty;
    }

    private final TreeMap<Long, Level> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, Level> asks = new TreeMap<>();
    private final Map<Long, Order> byId = new HashMap<>();
    private long nextSeq = 0;

    /** Submit a limit order. Crosses the book if marketable; any remainder rests. */
    public List<Trade> submitLimit(long id, Side side, long price, long quantity) {
        if (price <= 0) throw new IllegalArgumentException("price must be positive");
        checkNew(id, quantity);
        Order order = new Order(id, side, price, quantity, false, nextSeq++);
        List<Trade> trades = match(order);
        if (order.remaining > 0) rest(order);
        return trades;
    }

    /** Submit a market order. Sweeps the book; any unfilled remainder is discarded. */
    public List<Trade> submitMarket(long id, Side side, long quantity) {
        checkNew(id, quantity);
        return match(new Order(id, side, 0, quantity, true, nextSeq++));
    }

    /** Cancel a resting order. Returns false if the id isn't resting (filled or unknown). */
    public boolean cancel(long id) {
        Order order = byId.remove(id);
        if (order == null) return false;
        TreeMap<Long, Level> book = bookFor(order.side);
        Level level = book.get(order.price);
        level.orders.remove(id);
        level.totalQty -= order.remaining;
        if (level.orders.isEmpty()) book.remove(order.price);
        return true;
    }

    public OptionalLong bestBid() {
        return bids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(bids.firstKey());
    }

    public OptionalLong bestAsk() {
        return asks.isEmpty() ? OptionalLong.empty() : OptionalLong.of(asks.firstKey());
    }

    /** Total resting quantity at a price level (0 if none). */
    public long depthAt(Side side, long price) {
        Level level = bookFor(side).get(price);
        return level == null ? 0 : level.totalQty;
    }

    /** Number of resting orders. */
    public int restingOrders() {
        return byId.size();
    }

    // ---- internals ----

    private List<Trade> match(Order taker) {
        List<Trade> trades = new ArrayList<>();
        TreeMap<Long, Level> opposite = bookFor(taker.side.opposite());

        while (taker.remaining > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, Level> best = opposite.firstEntry();
            long price = best.getKey();
            if (!taker.market && !crosses(taker, price)) break;

            Level level = best.getValue();
            Iterator<Order> it = level.orders.values().iterator();
            while (it.hasNext() && taker.remaining > 0) {
                Order maker = it.next();
                long qty = Math.min(taker.remaining, maker.remaining);
                taker.remaining -= qty;
                maker.remaining -= qty;
                level.totalQty -= qty;
                trades.add(new Trade(taker.id, maker.id, taker.side, price, qty));
                if (maker.remaining == 0) {
                    it.remove();
                    byId.remove(maker.id);
                }
            }
            if (level.orders.isEmpty()) opposite.remove(price);
        }
        return trades;
    }

    private static boolean crosses(Order taker, long restingPrice) {
        return taker.side == Side.BUY ? taker.price >= restingPrice : taker.price <= restingPrice;
    }

    private void rest(Order order) {
        Level level = bookFor(order.side).computeIfAbsent(order.price, p -> new Level());
        level.orders.put(order.id, order);
        level.totalQty += order.remaining;
        byId.put(order.id, order);
    }

    private TreeMap<Long, Level> bookFor(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private void checkNew(long id, long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (byId.containsKey(id)) throw new IllegalArgumentException("duplicate order id " + id);
    }
}
