# TPC-H Catalog

The `tpch` catalog provides the standard TPC-H benchmark dataset. Tables are accessed at `tpch.sf1.<table>` (scale factor 1).

TPC-H models a parts supplier business: customers place orders containing line items for parts sourced from suppliers across nations and regions.

## Tables
- [nation](tables/nation.md) — Nations (25 rows)
- [region](tables/region.md) — Regions grouping nations (5 rows)
- [part](tables/part.md) — Parts catalog
- [supplier](tables/supplier.md) — Parts suppliers
- [partsupp](tables/partsupp.md) — Parts-supplier availability and cost
- [customer](tables/customer.md) — Customers who place orders
- [orders](tables/orders.md) — Customer orders
- [lineitem](tables/lineitem.md) — Individual line items within orders

## Concepts
- [Business terms](concepts/business-terms.md) — Maps natural language to SQL patterns
- [Date handling](concepts/date-handling.md) — How dates work in TPC-H

## Example Queries
- [Common queries](examples/common-queries.md) — Joins, aggregations, subqueries
