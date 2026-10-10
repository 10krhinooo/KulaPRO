package com.example.kulapro.data.scanner

import com.example.kulapro.data.repository.Result
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Talks to the scanner proxy.
 *
 * Sends the caller's Firebase ID token rather than any key of its own. The proxy verifies
 * it against Google's certificates and checks it was minted for this project, so a scan is
 * attributable to a signed in user and chargeable to their daily quota. Nothing in this
 * class is a secret, which is the point.
 */
class ScannerRepositoryHttp(
    private val baseUrl: String,
    private val auth: FirebaseAuth,
    private val client: OkHttpClient = defaultClient(),
) : ScannerRepository {

    override val isAvailable: Boolean get() = baseUrl.isNotBlank()

    override suspend fun scan(imageBase64: String): Result<DishNutrition> {
        if (!isAvailable) {
            return Result.Failure("Scanning is not set up in this build.")
        }
        val user = auth.currentUser
            ?: return Result.Failure("Sign in to scan a dish, so your scans are yours.")

        return try {
            val token = user.getIdToken(false).await().token
                ?: return Result.Failure("Your session expired. Sign in again.")
            withContext(Dispatchers.IO) { post(token, imageBase64) }
        } catch (e: CancellationException) {
            // An Exception, so catching it below would turn every abandoned scan into an
            // error and break structured concurrency with it.
            throw e
        } catch (e: IOException) {
            Result.Failure("We could not reach the scanner. Check your connection.", e)
        } catch (e: Exception) {
            Result.Failure("We could not read that photo. Try again.", e)
        }
    }

    private fun post(token: String, imageBase64: String): Result<DishNutrition> {
        val body = JSONObject()
            .put("image", imageBase64)
            .put("mediaType", "image/jpeg")
            .toString()
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/scan")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) return Result.Failure(errorMessage(text))
            return Result.Success(parse(JSONObject(text)))
        }
    }

    /**
     * The proxy's own wording, which is written for the person reading it.
     *
     * A status code or a stack trace tells the user nothing they can act on, so anything
     * unparseable falls back to something that does.
     */
    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).getJSONObject("error").getString("message")
    }.getOrElse { "We could not read that photo. Try again." }
}

/** Kept out of the class so a test can hand in its own client. */
private fun defaultClient(): OkHttpClient = OkHttpClient.Builder().build()

private val JSON = "application/json; charset=utf-8".toMediaType()

/**
 * Reads the proxy's response into the app's own type.
 *
 * Internal and free of OkHttp so the mapping can be tested directly against a JSON string,
 * which is where the interesting cases are: a missing field, an unknown confidence, a
 * result for something that is not food.
 */
internal fun parse(json: JSONObject): DishNutrition {
    val isFood = json.optBoolean("is_food", false)
    if (!isFood) {
        // Macros are ignored rather than read. The proxy already strips them, and reading
        // them here would make this the one place a non-food photo could arrive with
        // calories attached.
        return DishNutrition(isFood = false, wasCached = json.optBoolean("cached", false))
    }
    return DishNutrition(
        isFood = true,
        dishName = json.optString("dish_name"),
        cuisine = json.optString("cuisine").takeIf { it.isNotBlank() && it != "null" },
        confidence = DishNutrition.Confidence.fromWire(json.optString("confidence")),
        assumedPortion = json.optString("assumed_portion"),
        caloriesKcal = json.optDouble("calories_kcal", 0.0),
        proteinG = json.optDouble("protein_g", 0.0),
        carbsG = json.optDouble("carbs_g", 0.0),
        fatG = json.optDouble("fat_g", 0.0),
        fibreG = json.optDouble("fibre_g", 0.0),
        likelyIngredients = json.optJSONArray("likely_ingredients").toStringList(),
        healthNotes = json.optJSONArray("health_notes").toStringList(),
        wasCached = json.optBoolean("cached", false),
    )
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
