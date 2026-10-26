package app.levelup2027

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

data class MockRequest(val path: String, val headers: Map<String, List<String>>, val body: String)

/** A minimal HTTP/1.1 server for tests: [respond] decides the status and body for each request. */
class MockHttp(private val respond: (MockRequest) -> Pair<Int, String>) : Closeable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val requests = mutableListOf<MockRequest>()
    val url = "http://127.0.0.1:${server.localPort}"

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                sock.use { c ->
                    val input = c.getInputStream().buffered()
                    fun readLine(): String {
                        val sb = StringBuilder()
                        while (true) {
                            val b = input.read()
                            if (b == -1 || b == '\n'.code) break
                            if (b != '\r'.code) sb.append(b.toChar())
                        }
                        return sb.toString()
                    }
                    val path = readLine().split(" ").getOrElse(1) { "/" }
                    val headers = LinkedHashMap<String, MutableList<String>>()
                    while (true) {
                        val h = readLine()
                        if (h.isEmpty()) break
                        val (k, v) = h.split(":", limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
                        headers.getOrPut(k) { mutableListOf() } += v
                    }
                    val len = headers["content-length"]?.firstOrNull()?.toInt() ?: 0
                    val bytes = ByteArray(len)
                    var read = 0
                    while (read < len) { val n = input.read(bytes, read, len - read); if (n < 0) break; read += n }
                    val req = MockRequest(path, headers, bytes.decodeToString())
                    synchronized(requests) { requests += req }
                    val (status, body) = respond(req)
                    val out = body.toByteArray()
                    c.getOutputStream().apply {
                        write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${out.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(out)
                        flush()
                    }
                }
            }
        }
    }

    override fun close() = server.close()
}
