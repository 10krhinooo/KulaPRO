package com.example.kulapro.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the real DataStore, not a stand in.
 *
 * The mapping is tested separately without Android; what is proven here is the part only a
 * real store can prove, that a write reaches disk and comes back through a second repository
 * built over the same file afterwards. A settings screen whose values do not persist is the
 * bug this whole area exists to fix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        // Created then removed, so the store starts from nothing but the path is this
        // test's alone.
        file = folder.newFile("settings.preferences_pb").also { it.delete() }
    }

    /**
     * Opens a repository over the file, runs the block, then closes it again.
     *
     * DataStore permits one active instance per file in a process, so a test that wants to
     * prove persistence has to let go of the first store before opening the second. Doing
     * that here is what makes "read it back" mean read it back from disk.
     */
    private suspend fun <T> withRepository(block: suspend (SettingsRepository) -> T): T {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsRepositoryDataStore(store))
        } finally {
            scope.cancel()
        }
    }

    private suspend fun storedSettings(): AppSettings = withRepository { it.settings.first() }

    @Test
    fun `reads the defaults from an empty store`() = runTest {
        assertEquals(AppSettings(), storedSettings())
    }

    @Test
    fun `a stored theme survives a new repository instance`() = runTest {
        withRepository { it.setThemeMode(ThemeMode.DARK) }

        assertEquals(ThemeMode.DARK, storedSettings().themeMode)
    }

    @Test
    fun `dynamic colour is stored`() = runTest {
        withRepository { it.setDynamicColour(true) }

        assertTrue(storedSettings().useDynamicColour)
    }

    @Test
    fun `reminders can be turned off and stay off`() = runTest {
        withRepository { it.setRemindersEnabled(false) }

        assertFalse(storedSettings().remindersEnabled)
    }

    @Test
    fun `the reminder lead time is stored`() = runTest {
        withRepository { it.setReminderLeadHours(24) }

        assertEquals(24, storedSettings().reminderLeadHours)
    }

    @Test
    fun `writing one preference leaves the rest alone`() = runTest {
        withRepository {
            it.setThemeMode(ThemeMode.LIGHT)
            it.setReminderLeadHours(1)
        }

        val stored = storedSettings()

        assertEquals(ThemeMode.LIGHT, stored.themeMode)
        assertEquals(1, stored.reminderLeadHours)
        assertFalse(stored.useDynamicColour)
        assertTrue(stored.remindersEnabled)
    }
}
