package com.example.kulapro.feature.admin

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.repository.AdminRepository
import com.example.kulapro.data.repository.PlatformSnapshot
import com.example.kulapro.data.repository.Result
import com.example.kulapro.util.Clock
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Records what window it was asked about, which is the thing a stale answer would get wrong. */
private class FakeAdminRepository(
    var snapshot: PlatformSnapshot = PlatformSnapshot(),
    var failure: String? = null,
) : AdminRepository {

    val windowsAsked = mutableListOf<Int>()

    override suspend fun snapshot(days: Int): Result<PlatformSnapshot> {
        windowsAsked += days
        return failure?.let { Result.Failure(it) } ?: Result.Success(snapshot)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SystemMetricsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    private val clock = Clock { now }

    private lateinit var admin: FakeAdminRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        admin = FakeAdminRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SystemMetricsViewModel(adminRepository = admin, clock = clock)

    @Test
    fun `the console loads the last month without being asked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(MetricsWindow.MONTH.days), admin.windowsAsked)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `what came back is what the screen reads`() = runTest(dispatcher) {
        admin.snapshot = PlatformSnapshot(
            restaurants = listOf(Restaurant(id = "r1"), Restaurant(id = "r2")),
            reservations = listOf(Reservation(partySize = 4)),
            dinerCount = 12,
            pendingRequestCount = 1,
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.restaurantCount)
        assertEquals(12, viewModel.state.value.dinerCount)
        assertEquals(4, viewModel.state.value.coverCount)
    }

    @Test
    fun `choosing another window asks the backend again rather than reusing what is held`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.selectWindow(MetricsWindow.WEEK)
            advanceUntilIdle()

            // Answering "the last 7 days" from documents fetched for the last 30 would be
            // wrong quietly, which is worse than being slow.
            assertEquals(
                listOf(MetricsWindow.MONTH.days, MetricsWindow.WEEK.days),
                admin.windowsAsked,
            )
            assertEquals(MetricsWindow.WEEK, viewModel.state.value.window)
        }

    @Test
    fun `choosing the window already open changes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.selectWindow(MetricsWindow.MONTH)
        advanceUntilIdle()

        assertEquals(1, admin.windowsAsked.size)
    }

    @Test
    fun `a failed load says why and offers the retry`() = runTest(dispatcher) {
        admin.failure = "You are not allowed to read this."

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("You are not allowed to read this.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `retrying clears the previous error before it asks again`() = runTest(dispatcher) {
        admin.failure = "You are offline."
        val viewModel = viewModel()
        advanceUntilIdle()

        admin.failure = null
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(2, admin.windowsAsked.size)
    }

    @Test
    fun `the window the charts are drawn against is the one the numbers came from`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.selectWindow(MetricsWindow.QUARTER)
            advanceUntilIdle()

            assertEquals(MetricsWindow.QUARTER.days, viewModel.state.value.coversByDay.size)
        }
}
