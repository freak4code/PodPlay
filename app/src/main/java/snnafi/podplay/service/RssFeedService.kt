package snnafi.podplay.service

import okhttp3.*
import org.w3c.dom.Node
import snnafi.podplay.util.DateUtils
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

class RssFeedService : FeedService {
    override fun getFeed(
        xmlFileURL: String,
        callBack: (RssFeedResponse?) -> Unit
    ) {

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(xmlFileURL)
            .build()

        client.newCall(request).enqueue(object : Callback {
            /**
             * Called when the request could not be executed due to cancellation, a connectivity problem or
             * timeout. Because networks can fail during an exchange, it is possible that the remote server
             * accepted the request before the failure.
             */
            override fun onFailure(call: Call, e: IOException) {
                callBack(null)
            }

            /**
             * Called when the HTTP response was successfully returned by the remote server. The callback may
             * proceed to read the response body with [Response.body]. The response is still live until
             * its response body is [closed][ResponseBody]. The recipient of the callback may
             * consume the response body on another thread.
             *
             *
             * Note that transport-layer success (receiving a HTTP response code, headers and body) does
             * not necessarily indicate application-layer success: `response` may still indicate an
             * unhappy HTTP response code like 404 or 500.
             */
            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        val dbFactory = DocumentBuilderFactory.newInstance()
                        val dBuilder = dbFactory.newDocumentBuilder()
                        val doc = dBuilder.parse(it.byteStream())
                        val rssFeedResponse = RssFeedResponse(episodes = mutableListOf())
                        domToRssFeedResponse(doc, rssFeedResponse)
                        callBack(rssFeedResponse)

                    }
                }
                callBack(null)
            }

        })

    }

    private fun domToRssFeedResponse(
        node: Node,
        rssFeedResponse: RssFeedResponse
    ) {
// 1
        if (node.nodeType == Node.ELEMENT_NODE) {
            // 2
            val nodeName = node.nodeName
            val parentName = node.parentNode.nodeName

            // 1
            val grandParentName = node.parentNode.parentNode?.nodeName ?: ""
// 2
            if (parentName == "item" && grandParentName == "channel") {
// 3
                val currentItem = rssFeedResponse.episodes?.last()
                if (currentItem != null) {
// 4
                    when (nodeName) {
                        "title" -> currentItem.title = node.textContent
                        "description" -> currentItem.description =
                            node.textContent
                        "itunes:duration" -> currentItem.duration =
                            node.textContent
                        "guid" -> currentItem.guid = node.textContent
                        "pubDate" -> currentItem.pubDate = node.textContent
                        "link" -> currentItem.link = node.textContent
                        "enclosure" -> {
                            currentItem.url = node.attributes.getNamedItem("url")
                                .textContent
                            currentItem.type = node.attributes.getNamedItem("type")
                                .textContent
                        }
                    }
                }
            }
            // 3
            if (parentName == "channel") {
// 4
                when (nodeName) {
                    "title" -> rssFeedResponse.title = node.textContent
                    "description" -> rssFeedResponse.description =
                        node.textContent
                    "itunes:summary" -> rssFeedResponse.summary =
                        node.textContent
                    "item" -> rssFeedResponse.episodes?.add(RssFeedResponse.EpisodeResponse())
                    "pubDate" -> rssFeedResponse.lastUpdated =
                        DateUtils.xmlDateToDate(node.textContent)
                }
            }
        }
// 5
        val nodeList = node.childNodes
        for (i in 0 until nodeList.length) {
            val childNode = nodeList.item(i)
            // 6
            domToRssFeedResponse(childNode, rssFeedResponse)
        }
    }

    companion object {
        val instance: FeedService by lazy {
            RssFeedService()
        }
    }
}

interface FeedService {
    // 1
    fun getFeed(
        xmlFileURL: String,
        callBack: (RssFeedResponse?) -> Unit
    )

    // 2

}