# Foreign-currency transactions (issue #12)

## Money contract

`amountMinor` always means the original positive Long minor-unit amount. `currency`
is its unit; `targetCurrency` is captured when the entry is created. Bank account
currency is independent. Existing documents without currency metadata mean LKR to
LKR. A completed `conversion` snapshot contains the target minor amount, exact
decimal source-to-target rate, provider, provider time, fetch time and cached flag.
`pendingConversion` explicitly distinguishes unresolved entries from the local
cloud outbox. No outbox/worker flags are sent to Firestore.

Target major = source major × rate. Arithmetic uses BigDecimal; round exactly
once at target minor units using HALF_UP. Reject nonpositive amounts/rates,
precision beyond the currency's digits, overflow, and results rounding to zero.
JPY has 0 digits, KWD 3, and the other supported codes 2. Same-currency snapshots
use rate 1 and provider IDENTITY with timestamps 0; they need no network.

Completed snapshots never refresh automatically. Note/category edits preserve
them; amount/currency changes invalidate them. A preference change applies only
to new entries. Totals group by the recorded target and exclude pending entries.
Cash wallet creation/editing remains LKR; its SQL ledger uses original LKR amounts.
Cash entries capture the preferred reporting target too, without changing ledger units.
This checkout has no TransactionProof or receipt verification implementation.
Future receipt verification must bind to original amount AND currency.

## Provider and cache

[ExchangeRate-API open access documentation](https://www.exchangerate-api.com/docs/free)
and [supported codes](https://www.exchangerate-api.com/docs/supported-currencies)
were checked for this implementation. HTTPS `open.er-api.com/v6/latest/{source}`
requires no key. Quotes are daily indicative reference rates, not live market or
settlement quotes. Pickers expose LKR, USD, EUR, GBP, INR, AUD, CAD, SGD, JPY, KWD.
UI includes the required linked attribution and provider UTC timestamp.

Each direct source/target quote is persisted in Room with its provider time,
fetch time and next update. No cross rates are derived. Freshness ends at the
provider's next update or after 24 hours from fetch, whichever is earlier.
On network failure cached quotes up to 7 days from provider time are usable and
identified as cached. Older/future/malformed quotes are rejected. HTTP requests
have 10-second connect/read timeouts and a bounded response. Failures are retried
by network-constrained WorkManager with exponential backoff (minimum 30 minutes,
maximum 5 hours), respecting the provider's documented 20-minute 429 cooldown.

## Durability and synchronization

Local saves use only Room/cache and return without network. With no usable quote,
the entry is saved as pending conversion with no invented converted amount.
Work is scheduled on saves, auth changes and startup, and runs with connectivity.
Pending state/cache survive process restart. Resolution fetches outside mutation
locks and commits only if the owner, version, original amount/currencies, pending
state and deletion state still match. Completion atomically sets the snapshot,
advances the version, and marks the existing cloud outbox pending. Existing
account upload ordering, reconciliation and tombstones remain in use. Background
work also waits for outbox acknowledgement and retries retained work.

Preferred reporting currency defaults to LKR, persists per UID in Room and syncs
to owner-private `users/{uid}/preferences/currency`. Pending local preference edits
win until uploaded; remote conflict resolution uses updatedAt, with server ties.

## Development schema policy

Room schema 8 uses destructive recreation on upgrade/downgrade, per repository
instructions. This can discard unsynced local transactions and preferences during
a schema-changing upgrade. It is NOT an upgrade-safe migration. Within an
installed schema, offline work survives restarts. No reset/deletion commands are
part of this feature. Deploy the reviewed Firestore rules before releasing the app.
