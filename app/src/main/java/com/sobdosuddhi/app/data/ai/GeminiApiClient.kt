package com.sobdosuddhi.app.data.ai

import android.util.Base64
import com.sobdosuddhi.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AiServiceException(
  val bengaliMessage: String,
  val httpCode: Int = 0,
  val isNetworkError: Boolean = false,
  val isServerUnavailable: Boolean = false,
  cause: Throwable? = null
) : Exception(bengaliMessage, cause)

enum class ServerHealthStatus {
  READY,
  NO_API_KEY_ON_SERVER,
  AUTH_WALL_BLOCKED,
  UNREACHABLE
}

data class ServerHealthResult(
  val status: ServerHealthStatus,
  val bengaliMessage: String,
  val endpointUrl: String
)

/**
 * Secure, cancellable, timeout-aware HTTPS client communicating exclusively
 * with the Sobdosuddhi server-side AI proxy (/api/gemini/generate and /api/gemini/ocr).
 *
 * Security Guarantee:
 * - Zero API keys or secrets exist in this client, BuildConfig, or the APK.
 * - All Gemini API keys are stored and applied strictly on the server side.
 * - Detects browser auth/cookie redirect walls cleanly instead of misreporting HTML as bad JSON.
 * - Never logs manuscript text or sensitive headers.
 * - Supports safe retries with exponential backoff on transient 429/5xx errors.
 * - Supports immediate coroutine cancellation (cancels in-flight OkHttp calls).
 */
open class GeminiApiClient(
  baseUrl: String = BuildConfig.API_PROXY_BASE_URL,
  private val client: OkHttpClient = defaultOkHttpClient()
) {

  @Volatile
  private var activeBaseUrl: String = baseUrl

  companion object {
    const val MODEL_FAST_PROOFREAD = "gemini-3.1-flash-lite"
    const val MODEL_PRIMARY = "gemini-4-hydrogen"
    const val ROUTE_GENERATE = "/api/gemini/generate"
    const val ROUTE_OCR = "/api/gemini/ocr"
    const val ROUTE_HEALTH = "/api/health"
    const val MAX_DOCUMENT_CHARS = 120_000
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .readTimeout(75, TimeUnit.SECONDS)
      .callTimeout(90, TimeUnit.SECONDS)
      .followRedirects(false)
      .followSslRedirects(false)
      .retryOnConnectionFailure(true)
      .build()
  }

  fun updateProxyBaseUrl(customUrl: String) {
    val clean = customUrl.trim().trimEnd('/')
    activeBaseUrl = if (clean.startsWith("https://") || clean.startsWith("http://")) {
      clean
    } else {
      BuildConfig.API_PROXY_BASE_URL
    }
  }

  fun currentBaseUrl(): String = activeBaseUrl.trim().trimEnd('/')

  private fun normalizedBaseUrl(): String {
    val trimmed = activeBaseUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) {
      throw AiServiceException(
        bengaliMessage = "AI সার্ভারের ঠিকানা নির্ধারিত নেই। স্থানীয় বাংলা ইঞ্জিন সক্রিয় রয়েছে।",
        isServerUnavailable = true
      )
    }
    return trimmed
  }

  open suspend fun checkServerHealth(): ServerHealthResult = withContext(Dispatchers.IO) {
    val base = currentBaseUrl()
    if (base.isEmpty()) {
      return@withContext ServerHealthResult(
        status = ServerHealthStatus.UNREACHABLE,
        bengaliMessage = "কোনো HTTPS AI সার্ভার প্রোক্সি URL নির্ধারিত নেই।",
        endpointUrl = ""
      )
    }
    val healthUrl = "$base$ROUTE_HEALTH"
    val request = Request.Builder()
      .url(healthUrl)
      .addHeader("Accept", "application/json")
      .get()
      .build()

    try {
      client.newCall(request).execute().use { res ->
        val code = res.code
        val contentType = res.header("Content-Type").orEmpty().lowercase()
        val body = res.body?.string().orEmpty()
        val location = res.header("Location").orEmpty()

        if (code in 300..399 || isHtmlAuthResponse(contentType, body, location)) {
          return@withContext ServerHealthResult(
            status = ServerHealthStatus.AUTH_WALL_BLOCKED,
            bengaliMessage = "এই প্রিভিউ সার্ভার URL-টি ব্রাউজার লগইন সুরক্ষিত হওয়ায় সরাসরি মোবাইল থেকে অ্যাক্সেস করা যাচ্ছে না। তবে স্থানীয় বাংলা প্রুফরিডিং, AI শনাক্তকরণ ও মানবিক রূপান্তর ইঞ্জিন সচল আছে। পূর্ণ ক্লাউড AI-এর জন্য আপনার নিজস্ব পাবলিক HTTPS প্রোক্সি URL যুক্ত করুন।",
            endpointUrl = base
          )
        }

        if (!res.isSuccessful) {
          return@withContext ServerHealthResult(
            status = ServerHealthStatus.UNREACHABLE,
            bengaliMessage = "সার্ভার সাড়া দিচ্ছে না (কোড $code)। স্থানীয় বাংলা ইঞ্জিন স্বয়ংক্রিয়ভাবে ব্যবহৃত হবে।",
            endpointUrl = base
          )
        }

        val json = runCatching { JSONObject(body) }.getOrNull()
        val aiReady = json?.optBoolean("aiConfigured", false) ?: false
        if (aiReady) {
          ServerHealthResult(
            status = ServerHealthStatus.READY,
            bengaliMessage = "অনলাইন AI সার্ভার সফলভাবে সংযুক্ত ও প্রস্তুত রয়েছে।",
            endpointUrl = base
          )
        } else {
          ServerHealthResult(
            status = ServerHealthStatus.NO_API_KEY_ON_SERVER,
            bengaliMessage = "সার্ভার সংযুক্ত হয়েছে, তবে সার্ভারে GEMINI_API_KEY যুক্ত করা নেই। স্থানীয় বাংলা ইঞ্জিন সচল থাকবে।",
            endpointUrl = base
          )
        }
      }
    } catch (ce: CancellationException) {
      throw ce
    } catch (_: Exception) {
      ServerHealthResult(
        status = ServerHealthStatus.UNREACHABLE,
        bengaliMessage = "AI সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। স্থানীয় বাংলা ইঞ্জিন স্বয়ংক্রিয়ভাবে কাজ করবে।",
        endpointUrl = base
      )
    }
  }

  open suspend fun generateStructuredJson(
    model: String,
    systemInstruction: String,
    userPrompt: String,
    thinkingLevel: String? = null,
    maxRetries: Int = 2
  ): String = withContext(Dispatchers.IO) {
    if (userPrompt.length > MAX_DOCUMENT_CHARS) {
      throw AiServiceException(
        "একক খণ্ডের আকার সর্বোচ্চ সীমা (${MAX_DOCUMENT_CHARS} অক্ষর) অতিক্রম করেছে। অনুগ্রহ করে অংশবিশেষে বিশ্লেষণ করুন।"
      )
    }

    val payload = JSONObject().apply {
      put("model", model)
      put("systemInstruction", systemInstruction)
      put("userPrompt", userPrompt)
      if (!thinkingLevel.isNullOrBlank()) {
        put("thinkingLevel", thinkingLevel)
      }
    }

    val rawResponse = executeWithRetry(
      url = "${normalizedBaseUrl()}$ROUTE_GENERATE",
      bodyJson = payload.toString(),
      maxRetries = maxRetries
    )

    extractTextFromProxyResponse(rawResponse)
  }

  /**
   * Multimodal Bengali OCR for scanned PDF pages via the server-side AI proxy.
   */
  open suspend fun extractBengaliTextFromPageImage(pngBytes: ByteArray): String = withContext(Dispatchers.IO) {
    val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)

    val payload = JSONObject().apply {
      put("imageBase64", base64)
      put("mimeType", "image/png")
    }

    val rawResponse = executeWithRetry(
      url = "${normalizedBaseUrl()}$ROUTE_OCR",
      bodyJson = payload.toString(),
      maxRetries = 1
    )

    extractTextFromProxyResponse(rawResponse)
  }

  private suspend fun executeWithRetry(
    url: String,
    bodyJson: String,
    maxRetries: Int
  ): String {
    var attempt = 0
    var lastError: Throwable? = null

    while (attempt <= maxRetries) {
      try {
        val request = Request.Builder()
          .url(url)
          .addHeader("Content-Type", "application/json")
          .addHeader("Accept", "application/json")
          .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
          .build()

        return awaitCall(client.newCall(request))
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: AiServiceException) {
        lastError = e
        val retryable = !e.isServerUnavailable && (e.httpCode == 429 || (e.httpCode in 500..599 && e.httpCode != 503))
        if (!retryable || attempt == maxRetries) throw e
        delay(1000L * (attempt + 1))
        attempt++
      } catch (e: IOException) {
        lastError = e
        if (attempt == maxRetries) {
          throw AiServiceException(
            bengaliMessage = "ইন্টারনেট বা সার্ভার সংযোগ পাওয়া যায়নি। ইন্টারনেট ফিরে এলে অনলাইন AI মোড স্বয়ংক্রিয়ভাবে কাজ করবে।",
            isNetworkError = true,
            isServerUnavailable = true,
            cause = e
          )
        }
        delay(800L * (attempt + 1))
        attempt++
      }
    }

    throw lastError ?: AiServiceException("AI সার্ভার থেকে উত্তর পাওয়া যায়নি।", isServerUnavailable = true)
  }

  private suspend fun awaitCall(call: Call): String = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
      call.cancel()
    }
    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        if (!cont.isCancelled) {
          cont.resumeWithException(e)
        }
      }

      override fun onResponse(call: Call, response: Response) {
        response.use { res ->
          val body = res.body?.string().orEmpty()
          val contentType = res.header("Content-Type").orEmpty().lowercase()
          val location = res.header("Location").orEmpty()

          // Detect HTTP redirects (e.g. 302 -> /__cookie_check.html or OAuth login wall)
          if (res.code in 300..399 || isHtmlAuthResponse(contentType, body, location)) {
            if (!cont.isCancelled) {
              cont.resumeWithException(
                AiServiceException(
                  bengaliMessage = "অনলাইন AI সার্ভারটি সরাসরি মোবাইল অ্যাক্সেসের জন্য উন্মুক্ত নয় (লগইন অনুমোদন প্রয়োজন)। সেটিংস থেকে আপনার পাবলিক HTTPS AI সার্ভার প্রোক্সি URL যুক্ত করুন।",
                  httpCode = res.code,
                  isNetworkError = false,
                  isServerUnavailable = true
                )
              )
            }
            return
          }

          if (res.isSuccessful) {
            if (!cont.isCancelled) cont.resume(body)
          } else {
            val message = mapHttpErrorToBengali(res.code, body)
            val unavailable = res.code in setOf(401, 403, 404, 502, 503, 504)
            if (!cont.isCancelled) {
              cont.resumeWithException(
                AiServiceException(
                  bengaliMessage = message,
                  httpCode = res.code,
                  isServerUnavailable = unavailable
                )
              )
            }
          }
        }
      }
    })
  }

  private fun isHtmlAuthResponse(contentType: String, body: String, locationHeader: String): Boolean {
    val trimmed = body.trimStart()
    val locLower = locationHeader.lowercase()
    return contentType.contains("text/html") ||
      trimmed.startsWith("<!doctype", ignoreCase = true) ||
      trimmed.startsWith("<html", ignoreCase = true) ||
      trimmed.startsWith("<head", ignoreCase = true) ||
      locLower.contains("__cookie_check") ||
      locLower.contains("accounts.google.com") ||
      locLower.contains("login")
  }

  private fun mapHttpErrorToBengali(code: Int, rawBody: String): String {
    val serverMessage = runCatching { JSONObject(rawBody).optString("message", "").trim() }.getOrDefault("")
    if (serverMessage.isNotEmpty() && code in setOf(400, 401, 403, 503)) {
      return serverMessage
    }
    return when (code) {
      400 -> "অনুরোধ প্রক্রিয়াকরণে ত্রুটি হয়েছে (কোড ৪০০)। অনুগ্রহ করে পাঠ্যের দৈর্ঘ্য পরীক্ষা করে পুনরায় চেষ্টা করুন।"
      401, 403 -> "AI সার্ভারের অনুমোদন পাওয়া যায়নি (কোড $code)। অনুগ্রহ করে সার্ভার কনফিগারেশন যাচাই করুন।"
      404 -> "AI সার্ভারের নির্ধারিত এন্ডপয়েন্ট বা মডেল বর্তমানে উপলব্ধ নয় (কোড ৪০৪)।"
      429 -> "একসাথে অনেক অনুরোধ পাঠানো হয়েছে (কোড ৪২৯)। কয়েক সেকেন্ড পর আবার চেষ্টা করুন।"
      503 -> "সার্ভারে AI সেবা সাময়িকভাবে অনুপলব্ধ বা কনফিগার করা হয়নি (কোড ৫০৩)।"
      in 500..599 -> "সার্ভারে সাময়িক ব্যস্ততা রয়েছে (কোড $code)। কিছুক্ষণ পর আবার চেষ্টা করুন।"
      else -> "সার্ভার থেকে ত্রুটি পাওয়া গেছে (কোড $code)।"
    }
  }

  private fun extractTextFromProxyResponse(rawJson: String): String {
    if (isHtmlAuthResponse("", rawJson, "")) {
      throw AiServiceException(
        bengaliMessage = "অনলাইন AI সার্ভারটি ব্রাউজার লগইন পেজ ফেরত দিয়েছে। সেটিংস থেকে আপনার পাবলিক HTTPS AI সার্ভার URL যুক্ত করুন।",
        isServerUnavailable = true
      )
    }

    val root = runCatching { JSONObject(rawJson) }.getOrElse {
      throw AiServiceException(
        bengaliMessage = "AI সার্ভারের উত্তর পড়া সম্ভব হয়নি।",
        isServerUnavailable = true,
        cause = it
      )
    }

    // Standard server proxy format: { "text": "..." }
    val proxyText = root.optString("text", "").trim()
    if (proxyText.isNotEmpty()) {
      return stripCodeFences(proxyText)
    }

    // Compatibility fallback if proxy forwards raw Gemini envelope
    val candidates = root.optJSONArray("candidates")
    if (candidates != null && candidates.length() > 0) {
      val parts = candidates.getJSONObject(0)
        .optJSONObject("content")
        ?.optJSONArray("parts")
      if (parts != null) {
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
          val partText = parts.getJSONObject(i).optString("text", "")
          if (partText.isNotEmpty()) sb.append(partText)
        }
        val text = sb.toString().trim()
        if (text.isNotEmpty()) return stripCodeFences(text)
      }
    }

    throw AiServiceException("AI সার্ভার থেকে খালি ফলাফল পাওয়া গেছে। পুনরায় চেষ্টা করুন।")
  }

  private fun stripCodeFences(text: String): String {
    var cleaned = text.trim()
    if (cleaned.startsWith("```")) {
      cleaned = cleaned.substringAfter("\n", cleaned)
      if (cleaned.endsWith("```")) {
        cleaned = cleaned.dropLast(3)
      }
    }
    return cleaned.trim()
  }
}
