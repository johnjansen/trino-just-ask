# Date Handling

## Date Columns in TPC-H

All date columns are of type `DATE`:

| Table | Column | Range |
|-------|--------|-------|
| orders | orderdate | 1992-01-01 to 1998-08-02 |
| lineitem | shipdate | Shortly after orderdate |
| lineitem | commitdate | Promised delivery date |
| lineitem | receiptdate | Actual delivery date |

## Date Literals in Trino

Use the `DATE` keyword for date literals:
```sql
WHERE o.orderdate >= DATE '1995-01-01'
  AND o.orderdate < DATE '1996-01-01'
```

## Common Date Filters

### Specific year
```sql
WHERE o.orderdate >= DATE '1995-01-01' AND o.orderdate < DATE '1996-01-01'
```

### Specific quarter
```sql
WHERE o.orderdate >= DATE '1995-04-01' AND o.orderdate < DATE '1995-07-01'
```

### Specific month
```sql
WHERE o.orderdate >= DATE '1995-03-01' AND o.orderdate < DATE '1995-04-01'
```

### Year extraction
```sql
SELECT YEAR(o.orderdate) AS order_year, SUM(o.totalprice) AS total
FROM tpch.sf1.orders o
GROUP BY YEAR(o.orderdate)
```

### Month extraction
```sql
SELECT MONTH(o.orderdate) AS order_month, COUNT(*) AS order_count
FROM tpch.sf1.orders o
GROUP BY MONTH(o.orderdate)
```

## Date Arithmetic in Trino

### Add/subtract intervals
```sql
WHERE o.orderdate >= DATE '1995-03-15' - INTERVAL '30' DAY
```

### Difference between dates
```sql
SELECT DATE_DIFF('day', l.shipdate, l.receiptdate) AS transit_days
FROM tpch.sf1.lineitem l
```

## Delivery Analysis Patterns

### On-time vs late
```sql
SELECT
    CASE WHEN l.receiptdate <= l.commitdate THEN 'On Time' ELSE 'Late' END AS delivery_status,
    COUNT(*) AS item_count
FROM tpch.sf1.lineitem l
WHERE l.shipdate IS NOT NULL
GROUP BY CASE WHEN l.receiptdate <= l.commitdate THEN 'On Time' ELSE 'Late' END
```

### Average transit time
```sql
SELECT AVG(DATE_DIFF('day', l.shipdate, l.receiptdate)) AS avg_transit_days
FROM tpch.sf1.lineitem l
```

### Year-over-year comparison
```sql
SELECT YEAR(o.orderdate) AS year,
       SUM(o.totalprice) AS revenue,
       LAG(SUM(o.totalprice)) OVER (ORDER BY YEAR(o.orderdate)) AS prev_year_revenue
FROM tpch.sf1.orders o
GROUP BY YEAR(o.orderdate)
ORDER BY year
```
