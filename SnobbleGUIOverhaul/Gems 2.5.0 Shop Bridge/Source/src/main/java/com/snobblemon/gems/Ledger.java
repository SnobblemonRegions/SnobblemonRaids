package com.snobblemon.gems;

import com.snobblemon.gems.api.GemsEvents;
import com.snobblemon.gems.api.GemsResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The one authoritative place where Gems, tickets, pending rewards, purchase counts and daily earnings change.
 * State lives in memory (everything is small: a few numbers per player) and every mutation is applied under one lock,
 * then queued to {@link Db} as a single transaction together with its audit row. Nothing outside this class writes balances.
 */
public final class Ledger {
    private final Db db;
    private final Supplier<Cfg> cfg;

    public static final class Account {
        public final UUID id;
        public String name;
        public long balance, earned, spent, updated;

        Account(UUID id, String name) { this.id = id; this.name = name; }
    }

    public static final class TicketRec {
        public final String id;
        public final String owner;
        public final long ts;

        TicketRec(String id, String owner, long ts) { this.id = id; this.owner = owner; this.ts = ts; }
    }

    public static final class PendingRec {
        public final String id;
        public final UUID uuid; // null while only the name is known
        public final String name;
        public final String kind; // ticket | gems | item
        public final String data;
        public final String ref;
        public final long ts;

        PendingRec(String id, UUID uuid, String name, String kind, String data, String ref, long ts) {
            this.id = id; this.uuid = uuid; this.name = name; this.kind = kind; this.data = data; this.ref = ref; this.ts = ts;
        }
    }

    public static final class Purchase {
        public long total;
        public String day = "";
        public long dayCount;
    }

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();
    private final Set<String> orders = new HashSet<>();
    private final Map<String, TicketRec> tickets = new HashMap<>();
    private final Map<String, PendingRec> pending = new HashMap<>();
    private final Map<String, Purchase> purchases = new HashMap<>();
    private final Map<String, Long> earnDaily = new HashMap<>();
    private byte[] secret;

    public Ledger(Db db, Supplier<Cfg> cfg) {
        this.db = db;
        this.cfg = cfg;
    }

    private Cfg c() { return cfg.get(); }

    // ------------------------------------------------------------------ load (startup, before the writer thread runs)

    public synchronized void load() throws SQLException {
        Connection c = db.connection();
        try (Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT uuid,name,balance,earned,spent,updated FROM accounts")) {
                while (r.next()) {
                    Account a = new Account(UUID.fromString(r.getString(1)), r.getString(2));
                    a.balance = r.getLong(3); a.earned = r.getLong(4); a.spent = r.getLong(5); a.updated = r.getLong(6);
                    accounts.put(a.id, a);
                    if (a.name != null) byName.put(a.name.toLowerCase(Locale.ROOT), a.id);
                }
            }
            try (ResultSet r = s.executeQuery("SELECT ref FROM orders")) {
                while (r.next()) orders.add(r.getString(1));
            }
            try (ResultSet r = s.executeQuery("SELECT id,ts,owner FROM tickets WHERE status='ISSUED'")) {
                while (r.next()) tickets.put(r.getString(1), new TicketRec(r.getString(1), r.getString(3), r.getLong(2)));
            }
            try (ResultSet r = s.executeQuery("SELECT id,uuid,name,kind,data,ts,ref FROM pending WHERE status='PENDING'")) {
                while (r.next()) {
                    String u = r.getString(2);
                    pending.put(r.getString(1), new PendingRec(r.getString(1), u == null ? null : UUID.fromString(u), r.getString(3), r.getString(4), r.getString(5), r.getString(7), r.getLong(6)));
                }
            }
            try (ResultSet r = s.executeQuery("SELECT uuid,product,total,day,day_count FROM purchases")) {
                while (r.next()) {
                    Purchase p = new Purchase();
                    p.total = r.getLong(3); p.day = r.getString(4) == null ? "" : r.getString(4); p.dayCount = r.getLong(5);
                    purchases.put(r.getString(1) + "|" + r.getString(2), p);
                }
            }
            try (ResultSet r = s.executeQuery("SELECT uuid,day,kind,amount FROM earn_daily WHERE day >= '" + LocalDate.now(zone()).minusDays(2) + "'")) {
                while (r.next()) earnDaily.put(r.getString(1) + "|" + r.getString(2) + "|" + r.getString(3), r.getLong(4));
            }
            try (ResultSet r = s.executeQuery("SELECT v FROM meta WHERE k='secret'")) {
                if (r.next()) secret = hex(r.getString(1));
            }
        }
        if (secret == null) {
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO meta(k,v) VALUES('secret',?)")) {
                p.setString(1, hex(secret));
                p.executeUpdate();
            }
        }
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(c().chat.dailyLimit.timezone);
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    public String today() { return LocalDate.now(zone()).toString(); }

    // ------------------------------------------------------------------ accounts and balances

    public synchronized long balance(UUID id) {
        Account a = accounts.get(id);
        return a == null ? 0 : a.balance;
    }

    public synchronized Account account(UUID id) { return accounts.get(id); }

    public synchronized UUID uuidByName(String name) { return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT)); }

    public synchronized String nameOf(UUID id) {
        Account a = accounts.get(id);
        return a == null ? null : a.name;
    }

    public synchronized int accountCount() { return accounts.size(); }

    /** Creates the account on first sight (paying the starting balance) and keeps the stored name current. */
    public synchronized Account touch(UUID id, String name) {
        Account a = accounts.get(id);
        long now = System.currentTimeMillis();
        if (a == null) {
            a = new Account(id, name);
            a.updated = now;
            accounts.put(id, a);
            long start = c().currency.startingBalance;
            if (start > 0) {
                a.balance = Math.min(start, c().currency.maxBalance);
                a.earned = a.balance;
            }
            if (name != null) byName.put(name.toLowerCase(Locale.ROOT), id);
            saveAccount(a, a.balance > 0 ? new TxRow(now, id, name, a.balance, a.balance, "starting balance", "system", null) : null, null);
            return a;
        }
        if (name != null && !name.equals(a.name)) {
            if (a.name != null) byName.remove(a.name.toLowerCase(Locale.ROOT), id);
            a.name = name;
            byName.put(name.toLowerCase(Locale.ROOT), id);
            saveAccount(a, null, null);
        }
        return a;
    }

    private record TxRow(long ts, UUID uuid, String name, long delta, long after, String reason, String actor, String ref) {}

    private void saveAccount(Account a, TxRow tx, String orderKey) {
        final UUID id = a.id; final String name = a.name; final long bal = a.balance, earned = a.earned, spent = a.spent, upd = a.updated;
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("INSERT INTO accounts(uuid,name,balance,earned,spent,updated) VALUES(?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,balance=excluded.balance,earned=excluded.earned,spent=excluded.spent,updated=excluded.updated")) {
                p.setString(1, id.toString()); p.setString(2, name); p.setLong(3, bal); p.setLong(4, earned); p.setLong(5, spent); p.setLong(6, upd);
                p.executeUpdate();
            }
            if (tx != null) {
                try (PreparedStatement p = con.prepareStatement("INSERT INTO tx(ts,uuid,name,delta,balance_after,reason,actor,ref) VALUES(?,?,?,?,?,?,?,?)")) {
                    p.setLong(1, tx.ts); p.setString(2, tx.uuid.toString()); p.setString(3, tx.name); p.setLong(4, tx.delta); p.setLong(5, tx.after);
                    p.setString(6, tx.reason); p.setString(7, tx.actor); p.setString(8, tx.ref);
                    p.executeUpdate();
                }
            }
            if (orderKey != null) {
                try (PreparedStatement p = con.prepareStatement("INSERT OR IGNORE INTO orders(ref,ts,kind,uuid,detail) VALUES(?,?,?,?,?)")) {
                    p.setString(1, orderKey); p.setLong(2, upd); p.setString(3, "gems"); p.setString(4, id.toString()); p.setString(5, tx == null ? "" : tx.reason);
                    p.executeUpdate();
                }
            }
        });
    }

    /**
     * Adds (delta &gt; 0) or removes (delta &lt; 0) Gems. {@code orderKey} (nullable) makes the change idempotent.
     * A credit is cut to fit under maxBalance; a debit that would go below zero fails and changes nothing.
     */
    public GemsResult adjust(UUID id, String name, long delta, String reason, String actor, String orderKey) {
        Account a;
        long old, after, applied;
        synchronized (this) {
            if (delta == 0 || delta == Long.MIN_VALUE || Math.abs(delta) > 1_000_000_000_000L) return new GemsResult(GemsResult.Status.INVALID, 0, balance(id));
            if (orderKey != null && orders.contains(orderKey)) return new GemsResult(GemsResult.Status.DUPLICATE, 0, balance(id));
            a = touch(id, name);
            old = a.balance;
            if (delta < 0 && old + delta < 0) return new GemsResult(GemsResult.Status.INSUFFICIENT, 0, old);
            after = old + delta;
            long max = c().currency.maxBalance;
            if (after > max) after = max;
            applied = after - old;
            a.balance = after;
            if (applied > 0) a.earned += applied; else a.spent += -applied;
            a.updated = System.currentTimeMillis();
            if (orderKey != null) orders.add(orderKey);
            saveAccount(a, new TxRow(a.updated, id, a.name, applied, after, reason, actor, orderKey), orderKey);
        }
        if (c().logging.transactionsToConsole) SnobbleGems.LOG.info("[SnobbleGems] {} ({}) {}{} -> {} [{}] by {}{}", a.name, id, applied >= 0 ? "+" : "", applied, after, reason, actor, orderKey == null ? "" : " ref=" + orderKey);
        GemsEvents.BALANCE_CHANGED.invoker().onChange(id, a.name, old, after, reason);
        return new GemsResult(GemsResult.Status.OK, applied, after);
    }

    public GemsResult setBalance(UUID id, String name, long value, String reason, String actor) {
        Account a;
        long old, after;
        synchronized (this) {
            if (value < 0 || value > c().currency.maxBalance) return new GemsResult(GemsResult.Status.INVALID, 0, balance(id));
            a = touch(id, name);
            old = a.balance;
            after = value;
            long d = after - old;
            a.balance = after;
            if (d > 0) a.earned += d; else a.spent += -d;
            a.updated = System.currentTimeMillis();
            saveAccount(a, new TxRow(a.updated, id, a.name, d, after, reason, actor, null), null);
        }
        if (c().logging.transactionsToConsole) SnobbleGems.LOG.info("[SnobbleGems] {} ({}) SET {} -> {} [{}] by {}", a.name, id, old, after, reason, actor);
        GemsEvents.BALANCE_CHANGED.invoker().onChange(id, a.name, old, after, reason);
        return new GemsResult(GemsResult.Status.OK, after - old, after);
    }

    // ------------------------------------------------------------------ orders (idempotency keys for non-Gem rewards)

    /** True the first time a key is seen; false if it was already used. Persisted. */
    public synchronized boolean claimOrder(String key, String kind, UUID uuid, String detail) {
        if (key == null) return true;
        if (!orders.add(key)) return false;
        final long now = System.currentTimeMillis();
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("INSERT OR IGNORE INTO orders(ref,ts,kind,uuid,detail) VALUES(?,?,?,?,?)")) {
                p.setString(1, key); p.setLong(2, now); p.setString(3, kind); p.setString(4, uuid == null ? null : uuid.toString()); p.setString(5, detail);
                p.executeUpdate();
            }
        });
        return true;
    }

    public synchronized boolean orderSeen(String key) { return key != null && orders.contains(key); }

    // ------------------------------------------------------------------ tickets

    public String sign(String ticketId) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return hex(m.doFinal(ticketId.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates {@code count} fresh ticket records (status ISSUED) and returns them. */
    public synchronized List<TicketRec> issueTickets(UUID owner, int count, String source) {
        List<TicketRec> out = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            TicketRec t = new TicketRec(UUID.randomUUID().toString(), owner == null ? null : owner.toString(), now);
            tickets.put(t.id, t);
            out.add(t);
            db.submit(con -> {
                try (PreparedStatement p = con.prepareStatement("INSERT INTO tickets(id,ts,owner,status,source) VALUES(?,?,?,'ISSUED',?)")) {
                    p.setString(1, t.id); p.setLong(2, now); p.setString(3, t.owner); p.setString(4, source);
                    p.executeUpdate();
                }
            });
        }
        return out;
    }

    public synchronized boolean ticketIssued(String id) { return tickets.containsKey(id); }

    /** Atomically retires a ticket. Returns false if it was not ISSUED (forged, already used, duplicated). */
    public synchronized boolean redeemTicket(String id, UUID by) {
        if (tickets.remove(id) == null) return false;
        final long now = System.currentTimeMillis();
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("UPDATE tickets SET status='REDEEMED',redeemed_ts=?,redeemed_by=? WHERE id=? AND status='ISSUED'")) {
                p.setLong(1, now); p.setString(2, by.toString()); p.setString(3, id);
                p.executeUpdate();
            }
        });
        return true;
    }

    /** Undo of {@link #redeemTicket}, used when the boost could not be started. */
    public synchronized void reinstateTicket(String id, UUID owner) {
        tickets.put(id, new TicketRec(id, owner.toString(), System.currentTimeMillis()));
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("UPDATE tickets SET status='ISSUED',redeemed_ts=NULL,redeemed_by=NULL WHERE id=?")) {
                p.setString(1, id);
                p.executeUpdate();
            }
        });
    }

    /** Takes a ticket record out of circulation without redeeming it (e.g. the stack could not be handed over). */
    public synchronized void voidTicket(String id) {
        if (tickets.remove(id) == null) return;
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("UPDATE tickets SET status='VOID' WHERE id=? AND status='ISSUED'")) {
                p.setString(1, id);
                p.executeUpdate();
            }
        });
    }

    public synchronized int issuedTicketCount() { return tickets.size(); }

    // ------------------------------------------------------------------ pending rewards (offline delivery)

    public synchronized PendingRec addPending(UUID uuid, String name, String kind, String data, String ref) {
        PendingRec p = new PendingRec(UUID.randomUUID().toString(), uuid, name, kind, data, ref, System.currentTimeMillis());
        pending.put(p.id, p);
        db.submit(con -> {
            try (PreparedStatement s = con.prepareStatement("INSERT INTO pending(id,uuid,name,kind,data,ts,ref,status) VALUES(?,?,?,?,?,?,?,'PENDING')")) {
                s.setString(1, p.id); s.setString(2, uuid == null ? null : uuid.toString()); s.setString(3, name); s.setString(4, kind);
                s.setString(5, data); s.setLong(6, p.ts); s.setString(7, ref);
                s.executeUpdate();
            }
        });
        return p;
    }

    /** Rewards waiting for this player: stored under their UUID, or (not yet resolved) under their name. */
    public synchronized List<PendingRec> pendingFor(UUID uuid, String name) {
        List<PendingRec> out = new ArrayList<>();
        for (PendingRec p : pending.values()) {
            if (uuid != null && uuid.equals(p.uuid)) out.add(p);
            else if (p.uuid == null && name != null && p.name != null && p.name.equalsIgnoreCase(name)) out.add(p);
        }
        out.sort((x, y) -> Long.compare(x.ts, y.ts));
        return out;
    }

    public synchronized int pendingTotal() { return pending.size(); }

    /** Marks a pending record delivered. Returns false if it was already taken (so it can never be delivered twice). */
    public synchronized boolean completePending(String id, UUID by) {
        if (pending.remove(id) == null) return false;
        final long now = System.currentTimeMillis();
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("UPDATE pending SET status='DELIVERED',uuid=COALESCE(uuid,?) WHERE id=? AND status='PENDING'")) {
                p.setString(1, by.toString());
                p.setString(2, id);
                p.executeUpdate();
            }
        });
        return true;
    }

    /** Rewrites the payload of a pending record (partial delivery: some of the tickets fit, the rest wait). */
    public synchronized void updatePendingData(String id, String data) {
        PendingRec old = pending.get(id);
        if (old == null) return;
        pending.put(id, new PendingRec(old.id, old.uuid, old.name, old.kind, data, old.ref, old.ts));
        db.submit(con -> {
            try (PreparedStatement p = con.prepareStatement("UPDATE pending SET data=? WHERE id=? AND status='PENDING'")) {
                p.setString(1, data); p.setString(2, id);
                p.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------ shop purchase counts

    public synchronized Purchase purchase(UUID id, String product) {
        Purchase p = purchases.get(id + "|" + product);
        if (p == null) return new Purchase();
        Purchase copy = new Purchase();
        copy.total = p.total;
        copy.day = p.day;
        copy.dayCount = p.day.equals(today()) ? p.dayCount : 0;
        return copy;
    }

    public synchronized void recordPurchase(UUID id, String product, int n) {
        Purchase p = purchases.computeIfAbsent(id + "|" + product, k -> new Purchase());
        String d = today();
        if (!d.equals(p.day)) { p.day = d; p.dayCount = 0; }
        p.total += n;
        p.dayCount += n;
        final long total = p.total, dc = p.dayCount; final String day = p.day;
        db.submit(con -> {
            try (PreparedStatement s = con.prepareStatement("INSERT INTO purchases(uuid,product,total,day,day_count) VALUES(?,?,?,?,?) ON CONFLICT(uuid,product) DO UPDATE SET total=excluded.total,day=excluded.day,day_count=excluded.day_count")) {
                s.setString(1, id.toString()); s.setString(2, product); s.setLong(3, total); s.setString(4, day); s.setLong(5, dc);
                s.executeUpdate();
            }
        });
    }

    /** Gives back one purchase count (delivery failed after payment was refunded). */
    public synchronized void unrecordPurchase(UUID id, String product, int n) {
        Purchase p = purchases.get(id + "|" + product);
        if (p == null) return;
        p.total = Math.max(0, p.total - n);
        p.dayCount = Math.max(0, p.dayCount - n);
        final long total = p.total, dc = p.dayCount; final String day = p.day;
        db.submit(con -> {
            try (PreparedStatement s = con.prepareStatement("UPDATE purchases SET total=?,day_count=? WHERE uuid=? AND product=? AND day=?")) {
                s.setLong(1, total); s.setLong(2, dc); s.setString(3, id.toString()); s.setString(4, product); s.setString(5, day);
                s.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------ daily earning limits

    public synchronized long earnedToday(UUID id) {
        long sum = 0;
        String prefix = id + "|" + today() + "|";
        for (Map.Entry<String, Long> e : earnDaily.entrySet()) if (e.getKey().startsWith(prefix)) sum += e.getValue();
        return sum;
    }

    public synchronized void addEarnedToday(UUID id, String kind, long amount) {
        String day = today();
        String key = id + "|" + day + "|" + kind;
        long v = earnDaily.merge(key, amount, Long::sum);
        db.submit(con -> {
            try (PreparedStatement s = con.prepareStatement("INSERT INTO earn_daily(uuid,day,kind,amount) VALUES(?,?,?,?) ON CONFLICT(uuid,day,kind) DO UPDATE SET amount=excluded.amount")) {
                s.setString(1, id.toString()); s.setString(2, day); s.setString(3, kind); s.setLong(4, v);
                s.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------ audit view

    public record TxView(long ts, long delta, long after, String reason, String actor, String ref) {}

    public void history(UUID id, int limit, java.util.function.Consumer<List<TxView>> done) {
        db.read(con -> {
            List<TxView> out = new ArrayList<>();
            try (PreparedStatement p = con.prepareStatement("SELECT ts,delta,balance_after,reason,actor,ref FROM tx WHERE uuid=? ORDER BY id DESC LIMIT ?")) {
                p.setString(1, id.toString()); p.setInt(2, limit);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(new TxView(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getString(5), r.getString(6)));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return out;
        }, done);
    }

    // ------------------------------------------------------------------ hex

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
