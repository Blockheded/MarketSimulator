package orderbook;

import java.util.Arrays;
import java.util.Random;

/** Rough throughput/latency benchmark. Numbers are for tracking your own improvements. */
public final class Benchmark {
    public static void main(String[] args) {
        run(500_000, false); // warm up the JIT
        run(2_000_000, true);
    }

    static void run(int ops, boolean report) {
        OrderBook book = new OrderBook();
        Random rng = new Random(7);
        long[] latencies = new long[ops];
        long id = 1;
        long start = System.nanoTime();
        for (int i = 0; i < ops; i++) {
            int action = rng.nextInt(10);
            long t0 = System.nanoTime();
            if (action < 6) {
                Side s = rng.nextBoolean() ? Side.BUY : Side.SELL;
                book.submitLimit(id++, s, 10_000 + rng.nextInt(201) - 100, 1 + rng.nextInt(50));
            } else if (action < 8) {
                book.submitMarket(id++, rng.nextBoolean() ? Side.BUY : Side.SELL, 1 + rng.nextInt(50));
            } else {
                book.cancel(1 + rng.nextInt((int) id));
            }
            latencies[i] = System.nanoTime() - t0;
        }
        double secs = (System.nanoTime() - start) / 1e9;
        if (!report) return;
        Arrays.sort(latencies);
        System.out.printf("ops: %,d  throughput: %,.0f ops/sec%n", ops, ops / secs);
        System.out.printf("latency ns  p50: %,d  p99: %,d  p99.9: %,d%n",
                latencies[ops / 2], latencies[(int) (ops * 0.99)], latencies[(int) (ops * 0.999)]);
    }
}
