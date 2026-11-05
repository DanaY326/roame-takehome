package travel.roame.takehome

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlinx.serialization.builtins.ListSerializer
import travel.roame.takehome.data.FileDocumentStore
import travel.roame.takehome.model.SavedFlight
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDocumentStoreTest {
    private val dir = Path(SystemTemporaryDirectory, "flight-store-test-${Random.nextLong().toULong()}")
    private val file = Path(dir, "saved.json")

    private fun store() = FileDocumentStore(file, ListSerializer(SavedFlight.serializer()))

    @Test
    fun missingFileReadsAsNull() = runTest {
        assertNull(store().read())
    }

    @Test
    fun writesSurviveANewStoreInstance() = runTest {
        val saved = listOf(SavedFlight(flight("a"), 1L), SavedFlight(flight("b", points = 9_000), 2L))
        store().write(saved)
        // A fresh instance stands in for the app being closed and reopened.
        assertEquals(saved, store().read())

        store().write(saved.take(1))
        assertEquals(saved.take(1), store().read())
        assertTrue(!SystemFileSystem.exists(Path("$file.tmp")), "temp file is moved into place")
    }

    @Test
    fun corruptFileIsQuarantinedInsteadOfCrashing() = runTest {
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(file).buffered().use { it.writeString("{not json") }

        assertNull(store().read())
        assertTrue(SystemFileSystem.exists(Path("$file.corrupt")))
        assertTrue(!SystemFileSystem.exists(file))

        store().write(listOf(SavedFlight(flight("a"), 1L)))
        assertEquals(1, store().read()?.size)
    }
}
