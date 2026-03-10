# TPC-H Catalog

Documentation for Trino's built-in `tpch` connector (TPC-H benchmark dataset). Use this to test the plugin out of the box.

- `index.md` — Overview of the TPC-H schema with links to all docs.
- `tables/` — All 8 TPC-H tables (nation, region, part, supplier, partsupp, customer, orders, lineitem) with full column schemas, primary/foreign keys, and join patterns.
- `concepts/business-terms.md` — Maps natural language terms ("best supplier", "profitable", "on time", "market share") to concrete SQL expressions.
- `concepts/date-handling.md` — Date literals, range filters, and delivery analysis patterns.
- `examples/common-queries.md` — Example queries covering joins, aggregations, subqueries, and window functions.

All table references use `tpch.sf1.<table>` (scale factor 1).
