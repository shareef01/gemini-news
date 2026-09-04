# Room schema snapshots

Canonical exports live here (`exportSchema = true`).

Copies used by MigrationTestHelper:

* `app/src/test/assets/com.aus.gemini01.data.local.AppDatabase/` (Robolectric / CI)
* `app/src/androidTest/assets/com.aus.gemini01.data.local.AppDatabase/` (device)

After a schema change, re-copy:

```bash
cp app/schemas/com.aus.gemini01.data.local.AppDatabase/*.json \
  app/src/test/assets/com.aus.gemini01.data.local.AppDatabase/
cp app/schemas/com.aus.gemini01.data.local.AppDatabase/*.json \
  app/src/androidTest/assets/com.aus.gemini01.data.local.AppDatabase/
```
