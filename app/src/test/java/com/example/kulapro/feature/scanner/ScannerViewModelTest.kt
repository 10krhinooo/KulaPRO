package com.example.kulapro.feature.scanner

import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.scanner.DishNutrition
import com.example.kulapro.data.scanner.ScannerRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A scanner a test can make succeed, fail, or report itself absent. */
private class FakeScannerRepository(
    var result: DishNutrition? = null,
    var failure: String? = null,
    override val isAvailable: Boolean = true,
) : ScannerRepository {

    var scannedImages = mutableListOf<String>()

    override suspend fun scan(imageBase64: String): Result<DishNutrition> {
        scannedImages += imageBase64
        failure?.let { return Result.Failure(it) }
        return Result.Success(result ?: DishNutrition())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val aDish = DishNutrition(
        isFood = true,
        dishName = "Ugali na nyama",
        assumedPortion = "one restaurant main",
        caloriesKcal = 800.0,
        proteinG = 40.0,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts with nothing to show`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(FakeScannerRepository())

        assertFalse(viewModel.state.value.isScanning)
        assertFalse(viewModel.state.value.hasResult)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `a build with no scanner says so rather than offering a dead button`() =
        runTest(dispatcher) {
            val viewModel = ScannerViewModel(FakeScannerRepository(isAvailable = false))

            assertFalse(viewModel.state.value.isAvailable)
        }

    @Test
    fun `a scan shows the estimate`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(FakeScannerRepository(result = aDish))

        viewModel.scan("base64")
        advanceUntilIdle()

        assertEquals("Ugali na nyama", viewModel.state.value.result?.dishName)
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `a new scan clears the previous dish before it starts`() = runTest(dispatcher) {
        val repository = FakeScannerRepository(result = aDish)
        val viewModel = ScannerViewModel(repository)
        viewModel.scan("first")
        advanceUntilIdle()

        viewModel.scan("second")

        // Leaving the old dish under the spinner would invite reading its numbers as the
        // new one's.
        assertFalse(viewModel.state.value.hasResult)
        assertTrue(viewModel.state.value.isScanning)
    }

    @Test
    fun `a failed scan says what happened and shows no numbers`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(
            FakeScannerRepository(failure = "We could not reach the scanner."),
        )

        viewModel.scan("base64")
        advanceUntilIdle()

        assertEquals("We could not reach the scanner.", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.hasResult)
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `an unreadable photo is reported without going near the network`() =
        runTest(dispatcher) {
            val repository = FakeScannerRepository()
            val viewModel = ScannerViewModel(repository)

            viewModel.reportUnreadableImage()
            advanceUntilIdle()

            assertTrue(repository.scannedImages.isEmpty())
            assertEquals(
                "We could not open that photo. Try taking it again.",
                viewModel.state.value.errorMessage,
            )
        }

    @Test
    fun `a photo that is not food is a friendly answer, not an error`() =
        runTest(dispatcher) {
            val viewModel = ScannerViewModel(
                FakeScannerRepository(result = DishNutrition(isFood = false)),
            )

            viewModel.scan("base64")
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isNotFood)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `adjusting the portion scales what is shown`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(FakeScannerRepository(result = aDish))
        viewModel.scan("base64")
        advanceUntilIdle()

        viewModel.setPortion(HALF)

        assertEquals(400.0, viewModel.state.value.shown?.caloriesKcal ?: 0.0, 0.001)
    }

    @Test
    fun `two adjustments do not compound, because the original is kept`() =
        runTest(dispatcher) {
            val viewModel = ScannerViewModel(FakeScannerRepository(result = aDish))
            viewModel.scan("base64")
            advanceUntilIdle()

            viewModel.setPortion(HALF)
            viewModel.setPortion(ONE_AND_A_HALF)

            assertEquals(1200.0, viewModel.state.value.shown?.caloriesKcal ?: 0.0, 0.001)
        }

    @Test
    fun `a new scan starts back at the whole serving`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(FakeScannerRepository(result = aDish))
        viewModel.scan("base64")
        advanceUntilIdle()
        viewModel.setPortion(HALF)

        viewModel.scan("another")
        advanceUntilIdle()

        assertEquals(1.0, viewModel.state.value.portionFactor, 0.001)
    }

    @Test
    fun `the serving the estimate assumed survives a portion adjustment`() =
        runTest(dispatcher) {
            val viewModel = ScannerViewModel(FakeScannerRepository(result = aDish))
            viewModel.scan("base64")
            advanceUntilIdle()

            viewModel.setPortion(HALF)

            assertEquals("one restaurant main", viewModel.state.value.shown?.assumedPortion)
        }

    @Test
    fun `an error can be dismissed`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(FakeScannerRepository(failure = "No."))
        viewModel.scan("base64")
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `clearing puts the screen back to where it started`() = runTest(dispatcher) {
        val viewModel = ScannerViewModel(FakeScannerRepository(result = aDish))
        viewModel.scan("base64")
        advanceUntilIdle()

        viewModel.clear()

        assertFalse(viewModel.state.value.hasResult)
        assertEquals(1.0, viewModel.state.value.portionFactor, 0.001)
        assertTrue(viewModel.state.value.isAvailable)
    }

    @Test
    fun `the portion is named in words, so the numbers are never unlabelled`() =
        runTest(dispatcher) {
            val state = ScannerUiState(result = aDish)

            assertEquals("The serving below", state.portionLabel)
            assertEquals("Half of it", state.copy(portionFactor = HALF).portionLabel)
            assertEquals(
                "One and a half times",
                state.copy(portionFactor = ONE_AND_A_HALF).portionLabel,
            )
        }
}
