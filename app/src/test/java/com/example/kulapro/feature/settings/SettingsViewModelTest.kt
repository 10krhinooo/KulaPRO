package com.example.kulapro.feature.settings

import com.example.kulapro.data.settings.AppSettings
import com.example.kulapro.data.settings.DEFAULT_REMINDER_LEAD_HOURS
import com.example.kulapro.data.settings.FakeSettingsRepository
import com.example.kulapro.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSettingsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The view model shares its flow only while something is watching, so a test has to
     * watch too. Without a collector the stored value never reaches the state flow and every
     * assertion would read the initial defaults instead of what was saved.
     */
    private fun TestScope.watch(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.settings.collect { } }
        advanceUntilIdle()
    }

    @Test
    fun `starts on the defaults before anything is stored`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(repository)

        assertEquals(AppSettings(), viewModel.settings.value)
        assertEquals(ThemeMode.SYSTEM, viewModel.settings.value.themeMode)
        assertFalse(viewModel.settings.value.useDynamicColour)
        assertTrue(viewModel.settings.value.remindersEnabled)
        assertEquals(DEFAULT_REMINDER_LEAD_HOURS, viewModel.settings.value.reminderLeadHours)
    }

    @Test
    fun `reads back what was already stored`() = runTest(dispatcher) {
        repository.current = AppSettings(themeMode = ThemeMode.DARK, reminderLeadHours = 24)
        val viewModel = SettingsViewModel(repository)

        watch(viewModel)

        assertEquals(ThemeMode.DARK, viewModel.settings.value.themeMode)
        assertEquals(24, viewModel.settings.value.reminderLeadHours)
    }

    @Test
    fun `choosing a theme stores it and shows it back`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(repository)
        watch(viewModel)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, repository.current.themeMode)
        assertEquals(ThemeMode.LIGHT, viewModel.settings.value.themeMode)
    }

    @Test
    fun `turning dynamic colour on stores it and shows it back`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(repository)
        watch(viewModel)

        viewModel.setDynamicColour(true)
        advanceUntilIdle()

        assertTrue(repository.current.useDynamicColour)
        assertTrue(viewModel.settings.value.useDynamicColour)
    }

    @Test
    fun `turning reminders off stores it and shows it back`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(repository)
        watch(viewModel)

        viewModel.setRemindersEnabled(false)
        advanceUntilIdle()

        assertFalse(repository.current.remindersEnabled)
        assertFalse(viewModel.settings.value.remindersEnabled)
    }

    @Test
    fun `changing the reminder lead time stores it and shows it back`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(repository)
        watch(viewModel)

        viewModel.setReminderLeadHours(1)
        advanceUntilIdle()

        assertEquals(1, repository.current.reminderLeadHours)
        assertEquals(1, viewModel.settings.value.reminderLeadHours)
    }

    @Test
    fun `one setting does not disturb the others`() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(repository)
        watch(viewModel)

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setReminderLeadHours(24)
        advanceUntilIdle()

        assertEquals(
            AppSettings(themeMode = ThemeMode.DARK, reminderLeadHours = 24),
            viewModel.settings.value,
        )
    }

    @Test
    fun `every theme mode offers a label a person can read`() {
        val labels = ThemeMode.entries.map { it.label }

        assertEquals(ThemeMode.entries.size, labels.distinct().size)
        assertTrue(labels.none { it.isBlank() })
    }
}
