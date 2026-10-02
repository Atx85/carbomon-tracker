# CarboMon local catalogue

A single executable serves a JSON API, a paste-JSON contribution page, and an embedded SQLite database. No Apache, Node, Python, or database service is needed to **run** it. Windows and Linux have separate binaries.

## Run

The GitHub Actions `carbomon-catalog-server` artifact includes `carbomon-catalog-windows.zip` and `carbomon-catalog-raspberry-pi.tar.gz`. Extract your package and follow `START-HERE.txt`. The Pi 4B package contains both 64-bit ARM and 32-bit ARMv7 executables; `./start-catalog.sh` selects the matching executable and uses the package directory for data. Allow both private-network HTTP (TCP 8765) and discovery (UDP 5353) if your firewall asks.

Windows (PowerShell):

```powershell
.\carbomon-catalog-windows-amd64.exe
```

Linux:

```sh
chmod +x carbomon-catalog-linux-amd64
./carbomon-catalog-linux-amd64
```

The program stays running in that terminal. Open `http://localhost:8765` on the host computer. On another computer or phone, replace `localhost` with the host's LAN IP, for example `http://192.168.1.10:8765`. Allow inbound TCP port 8765 on your private network if the host firewall asks. The server rejects connections whose immediate peer is outside private, loopback, or link-local address ranges. It is designed for a trusted LAN, with no router port forwarding or public reverse proxy.

On first run it creates `data/catalog.db` relative to the current working directory. **No access key is needed by default.** The website connects automatically; in the Android app, use **Find server → Sync foods and recipes**. An access-key file left over from an older version is preserved but ignored unless you explicitly enable key protection.

Optional arguments:

```sh
./carbomon-catalog-linux-amd64 -listen 0.0.0.0:8765 -data /path/to/carbomon-data
```

Use `-listen 127.0.0.1:8765` for host-only access. Default LAN HTTP is unencrypted; use only a trusted private network. Stop the process before copying the entire data directory for a consistent backup. For optional access-key protection, start the server with `-require-key`. That mode creates or reuses `data/access-key.txt`; the web page shows a key field, and the app reveals one when the server requests it (also available under **Connection options**). The app stores it separately from food data and excludes it from Android backups and transfers. To rotate an enabled key, stop the server, remove `access-key.txt`, restart with `-require-key`, and enter the new key on each client.

## Paste a list from an LLM

1. Choose **Food** or **Recipe**.
2. Click **Show AI prompt**, copy the prompt into your chosen LLM, and provide your food/recipe details and label values.
3. Paste the resulting **JSON object or JSON list** into the page. There is no file-upload step.
4. Click **Validate & preview**, review the names, quantities, and nutrition, then **Save**.

A list contains either foods or recipes, according to the selected type. Import up to 1,000 entries at once within a 16 MiB request. The entire list is validated before anything is written. One invalid item rejects the whole list, and the error identifies its position. Edit and delete existing entries in the catalogue below the editor. To correct a concurrent browser edit, refresh the catalogue, cancel editing, reopen the entry, and apply the correction to the current version.

The schema checks structure, units, bounds, and required fields. It cannot verify whether an LLM's nutrition claims are accurate. Use package labels or a reliable source. Energy is kcal/100 g; protein, carbohydrate, fat, fibre and sugar are g/100 g; minerals and cholesterol are mg/100 g. Optional nutrients omitted from JSON use the app's existing zero default, except cholesterol, where null means unknown. `nutritionSource` can record provenance for a food and survives app round trips.

Recipes contain complete ingredient nutrition snapshots, so they do not depend on another device having the original food. `foodId` and `foodSource` are optional for pasted ingredients. A recipe's optional `finishedWeightGrams` is the measured final cooked weight; otherwise total ingredient weight is used. Nutrients per 100 g of the recipe equal ingredient nutrient totals divided by finished weight, multiplied by 100.

See [food example](examples/food.json), [recipe example](examples/recipe.json), and the [schemas](schemas/).

## Android two-way sync

In **Setup → Shared food and recipe catalogue**, use **Find server** or enter the server address, then tap **Sync foods and recipes**.

- First sync sends saved scanned products, manual foods and recipes and downloads the server catalogue. Built-in staples, general third-party search results, diary entries, and profile information are not shared.
- Subsequent syncs send local additions, edits, and recipe deletions and receive shared changes. Deletion records are retained by the server so an unchanged offline device cannot resurrect a deleted item.
- Stable IDs prevent repeat-sync duplicates. Older app IDs are upgraded once using a persistent device identity. Separately created entries can still represent the same real-world food; matching by name would risk collapsing distinct products.
- The app remembers a baseline of each downloaded revision. A simultaneous edit/delete conflict rejects the whole sync without overwriting either side. The app lists conflicts and offers **Keep this phone’s versions** or **Keep the server’s versions** for those entries. Unrelated pending changes still sync. A further edit on another device triggers another conflict instead of an unconditional overwrite.
- Successful responses replace the catalogue and sync baseline together. Lost responses can be retried. Failed requests leave the offline catalogue available. Recipe ingredient nutrition is a snapshot; changing a food does not silently rewrite existing recipes or diary entries.
- Downloaded foods and recipes work offline and appear in local name search; recipes also appear under Recipes. Sync is manual in this version.
- Clearing the server connection preserves local foods/recipes and forgets the server identity, key and baseline. Do this explicitly before connecting to a different server. Restoring an app backup clears the sync baseline so absent backup entries cannot cause mass deletions on the server. Restored edits may require conflict resolution on the next sync.

The host must be running and reachable only when contributing or syncing. No background Android service is required.

Successful Open Food Facts barcode lookups in both the food and recipe screens are automatically saved as shared foods. They are available by name and barcode offline and join the next phone sync; no manual food entry is needed. A stable barcode-based ID prevents repeated scans, including scans on different phones, from creating duplicate entries. If two phones obtained differing data for that barcode, the existing sync conflict choice applies. Downloaded scanned products can also be found offline by barcode. Older lookup-cache entries are not automatically uploaded; scanning those products again saves them using this path. Unknown products or results without usable core nutrition are not saved as zero-nutrient foods.

Scanned products preserve Open Food Facts attribution in `nutritionSource`. The barcode is encoded in the stable record ID as `catalog-barcode-` followed by a zero-padded 14-digit code. Optional missing nutrients follow the app's existing zero defaults, while unknown cholesterol remains null. OFF `_100g` mineral values are converted from grams to milligrams; serving-only values are not treated as per-100-g values.

### Find the server

Tap **Find server** in the Android catalogue settings. The app searches for up to 10 seconds. One matching server fills in the address automatically; several results give you a choice. Tap **Sync foods and recipes**. No access key is needed unless optional protection was enabled on that server. Searches stop when cancelled, when leaving Setup, or when the app goes into the background.

Once the phone has synced, discovery looks for that catalogue's saved identity. It can find the same catalogue after its address changes or its data folder moves to a new computer. To connect to an unrelated catalogue, first use **Clear server connection**. Discovery does not broadcast access keys, upload food data, or automatically start phone sync.

The executable includes its own mDNS/DNS-SD announcer; no Bonjour, Avahi or other service needs installing. Phone discovery and peer discovery currently use LAN IPv4. Allow UDP 5353 for discovery and TCP 8765 (or your chosen port) for HTTP on the host's private network. Guest Wi-Fi, client isolation and some VPNs can block discovery; manual address entry still works. Use `-discovery=false` to stop announcements. Restart the server if its active network interfaces change.

## Automatic sharing between servers

Servers automatically discover each other on the same trusted LAN and pull missing records, normally about every 30 seconds while running. No approval is required for each exchange. Each server keeps its own database and any optional access key. Foods and recipes contributed from either a browser or a phone can reach the other servers, and their connected phones receive those additions on their next manual sync.

This is **missing-record sharing**, not a full mirror: an existing food, recipe or deletion record is never overwritten by another server's version. Differing versions are kept and reported in the server console. Edits and deletions of entries already present on both servers are not propagated between servers. IDs prevent repeated transfers from making duplicates; separately created entries for the same real-world food remain separate. Missing deletion records are also copied so that a later stale peer cannot resurrect an already-known deletion. Every incoming snapshot is validated and imported atomically with local revision numbers.

By default, devices on the trusted LAN can read, contribute and sync without an access key. `-require-key` optionally protects direct browser edits and phone sync; it does not make automatically shared catalogue data private from other LAN devices. Diary and profile data are never held by the server or exchanged. To disable both outgoing and incoming peer sharing, use `-peer-sync=false`. To restrict sharing to a group of servers, optionally give each the same private key file (at least 24 characters) using `-peer-key-file /path/to/shared-key.txt`. The file contents must match; the key itself is never advertised. Keyed servers discover other keyed servers, and require the matching key before returning data.

Peer snapshots are limited to 10,000 records and 32 MiB. Peers cannot redirect requests to another address, and discovery ignores public or loopback IPs. Servers with different identities remain separate phone connections; automatic peer exchange does not provide automatic phone failover.

## Move to another computer or make a backup

1. Stop the original server (Ctrl+C) before copying its data.
2. Copy the **entire `data` directory**, including `catalog.db` and `access-key.txt` if present, to the destination. If you used `-data`, copy that directory instead. Include an optional shared peer key file separately if it lives elsewhere.
3. Put the executable for the new computer's operating system beside it, then start it from that directory, or point `-data` at the copied directory.
4. On the phone, tap **Find server**, or change the address manually. Keep the existing connection (and optional key): the copied database preserves the server identity, revisions and deletion history.

Keep the old copy stopped after migration. Do not run two copies of the same data folder simultaneously: they share a server identity and are not independent peers. To run two independent servers, start the second with a fresh data directory and let automatic sharing collect missing records. Because peer sharing does not mirror later edits/deletions, it is not a replacement for a stopped-server backup.

## API

Only servers started with `-require-key` require `Authorization: Bearer <access-key>` for catalogue access, direct changes and phone sync. JSON bodies require `Content-Type: application/json`. Cross-origin browser calls are rejected; the built-in page uses the same origin.

| Request | Body / purpose |
|---|---|
| `GET /api/v1/schemas/food` | JSON Schema for one food object |
| `GET /api/v1/schemas/recipe` | JSON Schema for one recipe object |
| `GET /api/v1/examples/food` or `/recipe` | One example object; wrap objects in `[...]` for a list |
| `POST /api/v1/validate/food` or `/recipe` | One object **or a list**; returns normalized object(s), no write |
| `POST /api/v1/foods` | One food **or a list of foods** |
| `POST /api/v1/recipes` | One recipe **or a list of recipes** |
| `GET /api/v1/server-info` | Whether optional browser/app access-key protection is enabled; no key required |
| `GET /api/v1/catalog` | Complete catalogue, including deletion records |
| `GET /api/v1/peer-catalog` | Read-only peer snapshot when sharing is enabled; optional `X-Carbomon-Peer-Key`; supports `ETag`/`If-None-Match` |
| `PUT /api/v1/foods/{id}` or `/recipes/{id}` | One replacement object; `If-Match: <entry revision>` required |
| `DELETE /api/v1/foods/{id}` or `/recipes/{id}` | `If-Match: <entry revision>` required |
| `POST /api/v1/sync` | Atomically apply a list of revision-checked changes and return the full catalogue |

Creation returns the complete updated catalogue. Supply an `Idempotency-Key` (1–180 letters/digits/colon/underscore/hyphen) when creating an entry or batch and reuse it with the **same body** when retrying. Without it, each POST creates new IDs. Batch IDs append `:0`, `:1`, etc. The built-in page supplies a stable key for retries.

A minimal food list:

```json
[
  {"description":"Food A", "caloriesPer100g":100, "proteinPer100g":10, "carbsPer100g":15, "fatPer100g":0},
  {"description":"Food B", "caloriesPer100g":200, "proteinPer100g":20, "carbsPer100g":30, "fatPer100g":0}
]
```

Example sync request (an empty `changes` list is a download):

```json
{
  "schemaVersion": 1,
  "serverId": "",
  "changes": [
    {
      "kind": "food",
      "id": "catalog-client-generated-uuid",
      "baseRevision": 0,
      "deleted": false,
      "data": {"description":"Food A", "caloriesPer100g":100, "proteinPer100g":10, "carbsPer100g":15, "fatPer100g":0}
    }
  ]
}
```

Use the returned `serverId` on future syncs. Each returned record has `kind`, `id`, `revision`, `deleted`, and `data`. Updates/deletions must carry that record's `revision` as `baseRevision`. HTTP 409 includes conflicting server records; nothing in that request was saved. Invalid JSON/schema or oversized requests return HTTP 400; missing/wrong keys return 401 when key protection is enabled; absent edit preconditions return 428. Sync accepts at most 10,000 changes and a 16 MiB body; the Android client limits catalogue responses to 32 MiB. This intentionally targets a small LAN collection, not a large public food database.

## Build and test

Go 1.26 or newer is needed **only on the build machine**. SQLite is bundled using a pure-Go driver, so these builds need no C compiler or external SQLite library.

From `server/`:

```sh
go test ./...
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o dist/carbomon-catalog-linux-amd64 .
CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o dist/carbomon-catalog-windows-amd64.exe .
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -trimpath -ldflags="-s -w" -o dist/carbomon-catalog-linux-arm64 .
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 go build -trimpath -ldflags="-s -w" -o dist/carbomon-catalog-linux-armv7 .
python3 package_builds.py
```

Server tests cover persistent storage, atomic validation, batch imports, retry idempotency, conflicts, tombstones, peer gap filling, HTTP caching, peer keys, redirects, LAN restrictions, and discovery filtering. Set `CARBOMON_MDNS_TEST=1` when running Go tests on a multicast-capable LAN to exercise two temporary servers discovering and exchanging records with each other; a random peer key isolates test data from real servers. Android `CatalogDiscoveryTest` covers discovery identity/address validation, and `CatalogSyncTest` covers the sync planner and storage validation. Its optional protected-server HTTP test runs with `CARBOMON_TEST_SERVER` and `CARBOMON_TEST_KEY` set to a **fresh, disposable** server. Run `./gradlew testDebugUnitTest` from the repository root. Never point this integration test at your real catalogue.

To exercise the key-free Android HTTP path as well, set `CARBOMON_TEST_OPEN_SERVER` to a separate fresh test server started without `-require-key`. The protected test server must use `-require-key` and provide `CARBOMON_TEST_KEY`. Disable discovery and peer sharing for these disposable test servers.
