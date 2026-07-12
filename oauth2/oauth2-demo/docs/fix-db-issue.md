# Fix: Database Viewer Display Issues

## Problem

The **Database** tab in the React frontend showed two issues:

1. **Registered OAuth2 Clients** — the `id`, `client_id`, and `client_name` columns displayed `undefined` values.
2. **Authorization Consents** — showed **"No data available."** even though consent data existed.

## Root Causes

### Issue 1: H2 Uppercases Column Names

The backend used raw `SELECT` queries with `JdbcTemplate.queryForList()`, returning `List<Map<String, Object>>`:

```java
String sql = "SELECT id, client_id, client_secret, client_name FROM oauth2_registered_client";
```

**H2** (the embedded database) converts unquoted identifiers to **uppercase**. So the result set column labels were `ID`, `CLIENT_ID`, `CLIENT_NAME` — not lowercase.

Spring's `JdbcTemplate` wraps the result in a `LinkedCaseInsensitiveMap` (case-insensitive lookups on the Java side), but **Jackson serializes the actual map keys** — which are uppercase. The frontend accesses `row['id']` (lowercase) via JavaScript, which is case-sensitive, so `row['id']` returned `undefined`.

### Issue 2: Consents Table Has No `id` Column

The `oauth2_authorization_consent` table schema uses a **composite primary key** `(registered_client_id, principal_name)`:

```sql
CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
```

The original SQL tried to `SELECT id` from this table, which caused a `BadSqlGrammarException` ("Column 'ID' not found"). The exception was caught by the `catch` block, returning an error string. The frontend checked `Array.isArray(errorString)` → `false`, so it set `consents = []`, displaying **"No data available."**

## Fixes Applied

### Fix 1: Lowercase Column Aliases with Double-Quoted Identifiers

**File**: `src/main/java/.../web/DatabaseViewerController.java`

Changed all three SQL queries to use double-quoted aliases, which tell H2 to preserve the exact casing:

```java
// Before
"SELECT id, client_id, client_secret, client_name FROM oauth2_registered_client"

// After
"SELECT id AS \"id\", client_id AS \"client_id\", client_secret AS \"client_secret\", client_name AS \"client_name\" FROM oauth2_registered_client"
```

Applied to all three endpoints:

| Endpoint | Columns |
|----------|---------|
| `/db/clients` | `id`, `client_id`, `client_secret`, `client_name` |
| `/db/authorizations` | `id`, `registered_client_id`, `principal_name`, `authorization_grant_type` |
| `/db/consents` | `registered_client_id`, `principal_name`, `authorities` |

### Fix 2: Remove `id` from Consents Query

**File**: `src/main/java/.../web/DatabaseViewerController.java`

Removed the non-existent `id` column from the consents SQL:

```java
// Before (causes BadSqlGrammarException)
"SELECT id, registered_client_id, principal_name, authorities FROM oauth2_authorization_consent"

// After
"SELECT registered_client_id AS \"registered_client_id\", principal_name AS \"principal_name\", authorities AS \"authorities\" FROM oauth2_authorization_consent"
```

### Fix 3: Update Frontend Table Columns

**File**: `frontend/src/components/DatabaseViewer.js`

Removed `'id'` from the consents table header to match the actual columns returned by the backend:

```jsx
// Before
{renderTable(consents, ['id', 'registered_client_id', 'principal_name', 'authorities'])}

// After
{renderTable(consents, ['registered_client_id', 'principal_name', 'authorities'])}
```

## Files Changed

| File | Change |
|------|--------|
| `src/main/java/.../web/DatabaseViewerController.java` | Added double-quoted lowercased aliases to all SQL queries; removed `id` from consents query |
| `frontend/src/components/DatabaseViewer.js` | Removed `'id'` column from consents table header |

## Verification

1. Start the backend: `.\gradlew :oauth2-demo:bootRun`
2. Start the frontend: `npm start` (in `frontend/` directory)
3. Open `http://localhost:3000`
4. Log in with username `user` / password `password`
5. Complete the OAuth2 authorization flow (authorize + consent)
6. Click the **Database** tab
7. Verify:
   - **Registered Clients** tab shows `id`, `client_id`, and `client_name` with actual values (not `undefined`)
   - **Consents** tab shows consent data with columns `registered_client_id`, `principal_name`, `authorities`

## Technical Notes

### Why Double-Quoted Aliases Work in H2

In H2 (and SQL standard), identifiers in double quotes `"..."` are treated as **delimited identifiers** — their casing is preserved exactly as written. Identifiers without quotes are **folded to uppercase** by default. This is controlled by H2's `DATABASE_TO_UPPER` setting (enabled by default).

### Alternative Approaches Considered

- **Setting `DATABASE_TO_UPPER=FALSE`** in the JDBC URL — would affect the entire database and could break Spring Security OAuth2 internals that rely on uppercase column references.
- **Using `ColumnMapRowMapper` with case-insensitive wrapping** — more complex, unnecessary when SQL aliases solve it cleanly.
- **Using JPA entities** — overkill for a simple diagnostic viewer.

