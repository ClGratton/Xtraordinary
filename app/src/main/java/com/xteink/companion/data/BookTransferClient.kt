package com.xteink.companion.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class BookTransferSource(
    val fileName: String,
    val uri: Uri,
    val sizeBytes: Long?,
)

data class BookTransferProgress(
    val completedBooks: Int,
    val totalBooks: Int,
    val currentFileName: String,
    val currentBytes: Long,
    val currentSizeBytes: Long?,
)

class BookTransferException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

class BookTransferClient(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = applicationContext.getSystemService(WifiManager::class.java)
    private val contentResolver: ContentResolver = applicationContext.contentResolver

    fun preflight() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw BookTransferException("Sending books directly requires Android 10 or newer")
        }
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CHANGE_NETWORK_STATE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw BookTransferException("Xtraordinary cannot prepare the private X3 connection. Update or reinstall the app.")
        }
        if (!wifi.isWifiEnabled) {
            throw BookTransferException("Turn on Wi-Fi, then try sending the book again")
        }
    }

    suspend fun upload(
        books: List<BookTransferSource>,
        onProgress: (BookTransferProgress) -> Unit,
    ) {
        require(books.isNotEmpty()) { "No phone books were selected" }
        preflight()
        val acquired = requestTransferNetwork()
        try {
            books.forEachIndexed { index, book ->
                uploadBook(acquired.network, book) { written ->
                    onProgress(
                        BookTransferProgress(
                            completedBooks = index,
                            totalBooks = books.size,
                            currentFileName = book.fileName,
                            currentBytes = written,
                            currentSizeBytes = book.sizeBytes,
                        ),
                    )
                }
            }
        } finally {
            connectivity.unregisterNetworkCallback(acquired.callback)
        }
    }

    private suspend fun requestTransferNetwork(): AcquiredNetwork = suspendCancellableCoroutine { continuation ->
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(TransferSsid)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (continuation.isActive) continuation.resume(AcquiredNetwork(network, this))
            }

            override fun onUnavailable() {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("The XTEINK transfer hotspot was not selected"),
                    )
                }
            }

            override fun onLost(network: Network) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("The XTEINK transfer hotspot disconnected"),
                    )
                }
            }
        }
        continuation.invokeOnCancellation {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
        try {
            connectivity.requestNetwork(request, callback, NetworkTimeoutMs)
        } catch (error: SecurityException) {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    BookTransferException(
                        "Allow Nearby devices so Xtraordinary can connect to the X3 transfer network",
                        error,
                    ),
                )
            }
        }
    }

    private suspend fun uploadBook(
        network: Network,
        source: BookTransferSource,
        onBytesWritten: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val safeFileName = source.fileName
            .substringAfterLast('/')
            .replace("\"", "")
            .take(180)
        require(safeFileName.endsWith(".epub", ignoreCase = true)) {
            "Only EPUB books can be sent from the Android library"
        }
        val boundary = "xtraordinary-${System.nanoTime().toString(16)}"
        val connection = network.openConnection(URL("$TransferBaseUrl/upload?path=/")) as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = HttpConnectTimeoutMs
        connection.readTimeout = HttpReadTimeoutMs
        connection.doOutput = true
        connection.setChunkedStreamingMode(StreamChunkBytes)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.outputStream.buffered(StreamChunkBytes).use { output ->
            output.write("--$boundary\r\n".toByteArray())
            output.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$safeFileName\"\r\n"
                    .toByteArray(),
            )
            output.write("Content-Type: application/epub+zip\r\n\r\n".toByteArray())
            contentResolver.openInputStream(source.uri)?.buffered(StreamChunkBytes).use { input ->
                requireNotNull(input) { "The selected EPUB is no longer available" }
                val buffer = ByteArray(StreamChunkBytes)
                var written = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    written += count
                    onBytesWritten(written)
                }
            }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val reason = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException(reason.ifBlank { "XTEINK rejected $safeFileName ($responseCode)" })
        }
        connection.inputStream.close()
        connection.disconnect()
    }

    private data class AcquiredNetwork(
        val network: Network,
        val callback: ConnectivityManager.NetworkCallback,
    )

    private companion object {
        const val TransferSsid = "CrossPoint-Reader"
        const val TransferBaseUrl = "http://192.168.4.1"
        const val NetworkTimeoutMs = 60_000
        const val HttpConnectTimeoutMs = 15_000
        const val HttpReadTimeoutMs = 120_000
        const val StreamChunkBytes = 32 * 1024
    }
}
