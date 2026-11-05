package travel.roame.takehome.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** Persists a single serializable document. Implementations must be safe to call from any coroutine. */
interface DocumentStore<T> {
    /** Returns the stored value, or null when nothing (readable) has been stored yet. Throws on I/O failure. */
    suspend fun read(): T?

    /** Atomically replaces the stored value. Throws on failure, leaving the previous value intact. */
    suspend fun write(value: T)
}

/**
 * Stores a document as a JSON file. Writes go to a temp file that is then atomically moved into place,
 * so a crash mid-write never leaves a half-written document behind. All I/O runs on [dispatcher],
 * never on the caller's (UI) thread.
 *
 * A file that exists but cannot be decoded is moved aside to `<name>.corrupt` and treated as empty,
 * so one bad write can't brick the app — and the bad data is kept for inspection rather than deleted.
 */
class FileDocumentStore<T>(
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentStore<T> {

    private val mutex = Mutex()

    override suspend fun read(): T? = mutex.withLock {
        withContext(dispatcher) {
            if (!SystemFileSystem.exists(path)) return@withContext null
            val text = SystemFileSystem.source(path).buffered().use { it.readString() }
            try {
                SharedJson.decodeFromString(serializer, text)
            } catch (e: SerializationException) {
                quarantine()
                null
            } catch (e: IllegalArgumentException) {
                quarantine()
                null
            }
        }
    }

    override suspend fun write(value: T) = mutex.withLock {
        withContext(dispatcher) {
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            val tmp = Path("$path.tmp")
            SystemFileSystem.sink(tmp).buffered().use { it.writeString(SharedJson.encodeToString(serializer, value)) }
            SystemFileSystem.atomicMove(tmp, path)
        }
    }

    private fun quarantine() {
        runCatching { SystemFileSystem.atomicMove(path, Path("$path.corrupt")) }
    }
}
