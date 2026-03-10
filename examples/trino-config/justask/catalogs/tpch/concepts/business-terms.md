# Business Terms

Maps natural language questions to SQL patterns for the TPC-H dataset.

## Revenue and Pricing

- **"revenue"** → `SUM(l.extendedprice * (1 - l.discount))`
- **"charge" / "total charge"** → `SUM(l.extendedprice * (1 - l.discount) * (1 + l.tax))`
- **"profit"** → `SUM(l.extendedprice * (1 - l.discount) - ps.supplycost * l.quantity)` (requires partsupp join)
- **"discount amount"** → `SUM(l.extendedprice * l.discount)`
- **"average price"** → `AVG(l.extendedprice)`
- **"cost" / "supply cost"** → `ps.supplycost` from the partsupp table
- **"value of inventory"** → `SUM(ps.supplycost * ps.availqty)` from partsupp

## Rankings and Comparisons

- **"best" / "top"** → `ORDER BY <metric> DESC LIMIT N` (default N=10 if not specified)
- **"best supplier"** → highest revenue or lowest supply cost, depending on context
- **"best customer" / "top customers"** → `ORDER BY SUM(o.totalprice) DESC` or `ORDER BY SUM(l.extendedprice * (1 - l.discount)) DESC`
- **"worst"** → `ORDER BY <metric> ASC LIMIT N`
- **"most popular"** → `ORDER BY COUNT(*) DESC` or `ORDER BY SUM(l.quantity) DESC`
- **"largest orders"** → `ORDER BY o.totalprice DESC`
- **"most profitable"** → `ORDER BY SUM(l.extendedprice * (1 - l.discount) - ps.supplycost * l.quantity) DESC`

## Delivery and Shipping

- **"on time"** → `l.receiptdate <= l.commitdate`
- **"late" / "overdue"** → `l.receiptdate > l.commitdate`
- **"early"** → `l.receiptdate < l.commitdate`
- **"on time rate" / "delivery performance"** →
```sql
CAST(COUNT(CASE WHEN l.receiptdate <= l.commitdate THEN 1 END) AS DOUBLE)
/ COUNT(*) AS on_time_rate
```
- **"returned"** → `l.returnflag = 'R'`
- **"return rate"** →
```sql
CAST(COUNT(CASE WHEN l.returnflag = 'R' THEN 1 END) AS DOUBLE)
/ COUNT(*) AS return_rate
```

## Market Analysis

- **"market share"** → revenue as a percentage of total revenue:
```sql
SUM(CASE WHEN <condition> THEN l.extendedprice * (1 - l.discount) ELSE 0 END)
/ SUM(l.extendedprice * (1 - l.discount)) AS market_share
```
- **"market segment"** → `c.mktsegment` (AUTOMOBILE, BUILDING, FURNITURE, HOUSEHOLD, MACHINERY)
- **"by region" / "regional"** → GROUP BY r.name, joining through nation
- **"by country" / "by nation"** → GROUP BY n.name

## Order Status

- **"open orders" / "pending"** → `o.orderstatus = 'O'`
- **"completed" / "fulfilled"** → `o.orderstatus = 'F'`
- **"partial"** → `o.orderstatus = 'P'`
- **"urgent"** → `o.orderpriority = '1-URGENT'`
- **"high priority"** → `o.orderpriority IN ('1-URGENT', '2-HIGH')`

## Quantity and Counting

- **"how many orders"** → `COUNT(DISTINCT o.orderkey)`
- **"how many items"** → `COUNT(*)` on lineitem or `SUM(l.quantity)`
- **"how many customers"** → `COUNT(DISTINCT c.custkey)`
- **"how many suppliers"** → `COUNT(DISTINCT s.suppkey)`
- **"average order size"** → `AVG(o.totalprice)` or `AVG(items_per_order)` depending on context
