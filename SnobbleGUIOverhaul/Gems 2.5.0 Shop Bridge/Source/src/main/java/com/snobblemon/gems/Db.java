package com.snobblemon.gems;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * SQLite storage. The game thread works on in-memory state (see {@link Ledger}); every change is queued here as one operation and a single
 * writer thread commits the queue in batches, so the server tick never waits on the disk. Each operation carries the balance row AND its
 * audit row, so the two can never disagree on disk. WAL mode + synchronous=FULL: a committed batch survives a crash or power loss.
 */
public final class Db {
    public interface Op { void run(Connection c) throws SQLException; }

    private final Connection conn;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private Thread writer;
    private volatile boolean running;
    public volatile long failures;
    public volatile long written;

    private static final Object STOP = new Object();

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS accounts(uuid TEXT PRIMARY KEY, name TEXT, balance INTEGER NOT NULL CHECK(balance >= 0), earned INTEGER NOT NULL DEFAULT 0, spent INTEGER NOT NULL DEFAULT 0, updated INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS tx(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT, delta INTEGER NOT NULL, balance_after INTEGER NOT NULL, reason TEXT, actor TEXT, ref TEXT)",
            "CREATE INDEX IF NOT EXISTS tx_uuid ON tx(uuid, id)",
            "CREATE TABLE IF NOT EXISTS orders(ref TEXT PRIMARY KEY, ts INTEGER NOT NULL, kind TEXT, uuid TEXT, detail TEXT)",
            "CREATE TABLE IF NOT EXISTS tickets(id TEXT PRIMARY KEY, ts INTEGER NOT NULL, owner TEXT, status TEXT NOT NULL, source TEXT, redeemed_ts INTEGER, redeemed_by TEXT)",
            "CREATE TABLE IF NOT EXISTS pending(id TEXT PRIMARY KEY, uuid TEXT, name TEXT, kind TEXT NOT NULL, data TEXT, ts INTEGER NOT NULL, ref TEXT, status TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS purchases(uuid TEXT NOT NULL, product TEXT NOT NULL, total INTEGER NOT NULL, day TEXT, day_count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(uuid, product))",
            "CREATE TABLE IF NOT EXISTS earn_daily(uuid TEXT NOT NULL, day TEXT NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(uuid, day, kind))",
    };

    public Db(Path file) throws SQLException {
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception e) {
            throw new SQLException("cannot create " + file.getParent(), e);
        }
        Properties p = new Properties();
        // driver instantiated directly: java.sql.DriverManager does not see drivers living in Fabric's mod class loader
        this.conn = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), p);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=5000");
            for (String sql : SCHEMA) s.execute(sql);
        }
    }

    /** Direct access for the startup load only (before {@link #start()}). */
    public Connection connection() { return conn; }

    public void start() {
        running = true;
        writer = new Thread(this::loop, "SnobbleGems-DB");
        writer.setDaemon(false);
        writer.start();
    }

    public void submit(Op op) {
        if (!running) {
            // after shutdown began: write inline rather than lose the change
            try {
                synchronized (conn) { runBatch(List.of(op)); }
            } catch (Throwable t) {
                failures++;
                SnobbleGems.LOG.error("[SnobbleGems] DB write failed after shutdown: {}", t.toString());
            }
            return;
        }
        queue.add(op);
    }

    /** Runs a read on the writer thread (so it sees every queued write) and hands the result back via the callback (on the writer thread). */
    public <T> void read(java.util.function.Function<Connection, T> q, Consumer<T> done) {
        queue.add((Op) c -> {
            T r = null;
            try {
                r = q.apply(c);
            } catch (Throwable t) {
                SnobbleGems.LOG.error("[SnobbleGems] DB read failed: {}", t.toString());
            }
            done.accept(r);
        });
    }

    /** Completes once everything queued before this call has been committed. */
    public CompletableFuture<Void> barrier() {
        CompletableFuture<Void> f = new CompletableFuture<>();
        if (!running) { f.complete(null); return f; }
        queue.add((Op) c -> f.complete(null));
        return f;
    }

    private void loop() {
        while (true) {
            Object first;
            try {
                first = queue.take();
            } catch (InterruptedException e) {
                break;
            }
            List<Object> batch = new ArrayList<>();
            batch.add(first);
            queue.drainTo(batch, 500);
            boolean stop = false;
            List<Op> ops = new ArrayList<>();
            for (Object o : batch) {
                if (o == STOP) stop = true;
                else ops.add((Op) o);
            }
            if (!ops.isEmpty()) {
                synchronized (conn) {
                    try {
                        runBatch(ops);
                    } catch (Throwable t) {
                        // one bad operation must not take the others with it: retry one by one
                        SnobbleGems.LOG.warn("[SnobbleGems] batch commit failed ({}); retrying operations individually", t.toString());
                        for (Op op : ops) {
                            try {
                                runBatch(List.of(op));
                            } catch (Throwable t2) {
                                failures++;
                                SnobbleGems.LOG.error("[SnobbleGems] DB write FAILED and was skipped: {}", t2.toString());
                            }
                        }
                    }
                }
            }
            if (stop) break;
        }
    }

    private void runBatch(List<Op> ops) throws SQLException {
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Op op : ops) op.run(conn);
            conn.commit();
            written += ops.size();
        } catch (Throwable t) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            if (t instanceof SQLException se) throw se;
            throw new SQLException(t);
        } finally {
            conn.setAutoCommit(auto);
        }
    }

    /** Drains the queue, stops the writer and closes the file. */
    public void close() {
        if (!running) return;
        queue.add(STOP);
        running = false;
        try {
            writer.join(15000);
        } catch (InterruptedException ignored) {}
        try {
            synchronized (conn) { conn.close(); }
        } catch (SQLException ignored) {}
    }

    public int queued() { return queue.size(); }
}
