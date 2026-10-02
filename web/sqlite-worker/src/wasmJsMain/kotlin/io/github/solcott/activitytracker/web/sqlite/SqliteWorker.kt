package io.github.solcott.activitytracker.web.sqlite

import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

/** Room driver backed by `worker/worker.js` (SQLite WASM persisted to OPFS). */
fun createSqliteWorkerDriver(): WebWorkerSQLiteDriver = WebWorkerSQLiteDriver(jsWorker())

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
