# lineitem

Schema: `tpch.sf1.lineitem`

This is the largest and most detailed table. Each row is a single line item within an order, representing a specific part purchased from a specific supplier.

| Column | Type | Description |
|--------|------|-------------|
| orderkey | BIGINT | Foreign key to orders (composite PK) |
| linenumber | INTEGER | Line number within the order (composite PK) |
| partkey | BIGINT | Foreign key to part |
| suppkey | BIGINT | Foreign key to supplier |
| quantity | DOUBLE | Quantity ordered |
| extendedprice | DOUBLE | Base price (= retailprice * quantity) |
| discount | DOUBLE | Discount percentage (0.00 to 0.10) |
| tax | DOUBLE | Tax rate (0.00 to 0.08) |
| returnflag | VARCHAR | R (returned), A (accepted), N (not yet shipped at cut-off) |
| linestatus | VARCHAR | O (open), F (finished) |
| shipdate | DATE | Date the item was shipped |
| commitdate | DATE | Date the item was committed to ship by |
| receiptdate | DATE | Date the customer received the item |
| shipinstruct | VARCHAR | Shipping instructions: DELIVER IN PERSON, COLLECT COD, TAKE BACK RETURN, NONE |
| shipmode | VARCHAR | Shipping mode: REG AIR, AIR, RAIL, SHIP, TRUCK, MAIL, FOB |
| comment | VARCHAR | Free-text comment |

## Primary Key
- Composite: (`orderkey`, `linenumber`)

## Foreign Keys
- `orderkey` → `orders.orderkey`
- `partkey` → `part.partkey`
- `suppkey` → `supplier.suppkey`
- (`partkey`, `suppkey`) → `partsupp.(partkey, suppkey)`

## Key Calculations

### Revenue (net price after discount)
```sql
extendedprice * (1 - discount)
```

### Charge (revenue plus tax)
```sql
extendedprice * (1 - discount) * (1 + tax)
```

### Profit
```sql
extendedprice * (1 - discount) - supplycost * quantity
```
(requires joining to `partsupp` for `supplycost`)

## Common Join Patterns

### Line items with order, customer, and part details
```sql
SELECT o.orderkey, c.name AS customer, p.name AS part,
       l.quantity, l.extendedprice, l.discount,
       l.extendedprice * (1 - l.discount) AS revenue
FROM tpch.sf1.lineitem l
JOIN tpch.sf1.orders o ON l.orderkey = o.orderkey
JOIN tpch.sf1.customer c ON o.custkey = c.custkey
JOIN tpch.sf1.part p ON l.partkey = p.partkey
```

### Revenue by supplier
```sql
SELECT s.name AS supplier,
       SUM(l.extendedprice * (1 - l.discount)) AS total_revenue
FROM tpch.sf1.lineitem l
JOIN tpch.sf1.supplier s ON l.suppkey = s.suppkey
GROUP BY s.name
ORDER BY total_revenue DESC
```
