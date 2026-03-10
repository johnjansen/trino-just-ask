# Common Queries

## 1. Revenue by nation — joins across lineitem, supplier, nation
```sql
SELECT n.name AS nation,
       SUM(l.extendedprice * (1 - l.discount)) AS revenue
FROM tpch.sf1.lineitem l
JOIN tpch.sf1.supplier s ON l.suppkey = s.suppkey
JOIN tpch.sf1.nation n ON s.nationkey = n.nationkey
GROUP BY n.name
ORDER BY revenue DESC
```

## 2. Top 10 customers by total spend — join orders to customer
```sql
SELECT c.name AS customer, c.mktsegment,
       SUM(o.totalprice) AS total_spent,
       COUNT(o.orderkey) AS order_count
FROM tpch.sf1.customer c
JOIN tpch.sf1.orders o ON c.custkey = o.custkey
GROUP BY c.name, c.mktsegment
ORDER BY total_spent DESC
LIMIT 10
```

## 3. Supplier on-time delivery rate — subquery with aggregation
```sql
SELECT s.name AS supplier,
       COUNT(*) AS total_items,
       SUM(CASE WHEN l.receiptdate <= l.commitdate THEN 1 ELSE 0 END) AS on_time,
       ROUND(CAST(SUM(CASE WHEN l.receiptdate <= l.commitdate THEN 1 ELSE 0 END) AS DOUBLE)
             / COUNT(*) * 100, 2) AS on_time_pct
FROM tpch.sf1.lineitem l
JOIN tpch.sf1.supplier s ON l.suppkey = s.suppkey
GROUP BY s.name
ORDER BY on_time_pct DESC
LIMIT 10
```

## 4. Monthly revenue trend for a given year — date extraction with aggregation
```sql
SELECT MONTH(o.orderdate) AS month,
       SUM(o.totalprice) AS monthly_revenue,
       COUNT(DISTINCT o.orderkey) AS order_count
FROM tpch.sf1.orders o
WHERE o.orderdate >= DATE '1995-01-01'
  AND o.orderdate < DATE '1996-01-01'
GROUP BY MONTH(o.orderdate)
ORDER BY month
```

## 5. Market share by region — multi-table join with conditional aggregation
```sql
SELECT r.name AS region,
       SUM(l.extendedprice * (1 - l.discount)) AS region_revenue,
       ROUND(SUM(l.extendedprice * (1 - l.discount))
             / (SELECT SUM(l2.extendedprice * (1 - l2.discount)) FROM tpch.sf1.lineitem l2) * 100, 2)
             AS market_share_pct
FROM tpch.sf1.lineitem l
JOIN tpch.sf1.supplier s ON l.suppkey = s.suppkey
JOIN tpch.sf1.nation n ON s.nationkey = n.nationkey
JOIN tpch.sf1.region r ON n.regionkey = r.regionkey
GROUP BY r.name
ORDER BY region_revenue DESC
```

## 6. Minimum cost supplier for each part — correlated subquery
```sql
SELECT p.name AS part, s.name AS supplier, ps.supplycost, n.name AS nation
FROM tpch.sf1.part p
JOIN tpch.sf1.partsupp ps ON p.partkey = ps.partkey
JOIN tpch.sf1.supplier s ON ps.suppkey = s.suppkey
JOIN tpch.sf1.nation n ON s.nationkey = n.nationkey
WHERE ps.supplycost = (
    SELECT MIN(ps2.supplycost)
    FROM tpch.sf1.partsupp ps2
    WHERE ps2.partkey = p.partkey
)
ORDER BY p.name
LIMIT 20
```
