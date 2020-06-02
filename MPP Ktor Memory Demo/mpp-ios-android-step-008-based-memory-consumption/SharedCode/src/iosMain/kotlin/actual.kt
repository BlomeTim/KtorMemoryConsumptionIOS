package com.jetbrains.handson.mpp.mobile

import io.ktor.client.HttpClient
import io.ktor.client.engine.ios.Ios
import io.ktor.client.request.get
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.UIKit.UIDevice
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.CoroutineContext


// launch in default Ktor HttpClient as described in https://ktor.io/clients/http-client/engines.html#ios
fun launchInKtorHttpClient(iterations: Int, urlString: String, expectedSize: Int) {
    MainScope().launch {
        repeat(iterations) {
            val client = HttpClient(Ios)
            var array = client.get<ByteArray>(urlString)
            require(expectedSize == array.size)
            println("$it didRead ${array.size}")
            client.close()
        }
    }
}

// run in default NSUrlSession and Main Thread as used in Ktor Ios Engine: https://github.com/ktorio/ktor/blob/master/ktor-client/ktor-client-ios/darwin/src/io/ktor/client/engine/ios/IosClientEngine.kt
fun runInKotlinNSURLSession(times: Int, urlString: String, expectedSize: Int) {
    runInNSURLSession(urlString) {
        require(expectedSize == it)
        println("$times didRead ${it}")
        if (times >= 1) {
            runInKotlinNSURLSession(times - 1, urlString, expectedSize)
        }
    }
}

private fun runInNSURLSession(urlString: String, onCompleteSize: (Int) -> Unit) {
    class DDelegate() : NSObject(), NSURLSessionDataDelegateProtocol {
        var resultSize = 0

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData
        ) {
            resultSize = resultSize + didReceiveData.length.toInt()
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?
        ) {
            println("didCompleteWithError ${didCompleteWithError?.description}")

            if (didCompleteWithError != null) {
                TODO("completed with error: ${didCompleteWithError.description}")
            }

            onCompleteSize(resultSize)
        }
    }

    val configuration =
        NSURLSessionConfiguration.defaultSessionConfiguration
    val session = NSURLSession.sessionWithConfiguration(
        configuration,
        DDelegate(),
        NSOperationQueue.currentQueue
    )

    val request = NSMutableURLRequest()
    request.setURL(NSURL.URLWithString(urlString))
    request.setCachePolicy(NSURLRequestReloadIgnoringLocalAndRemoteCacheData)
    request.setHTTPMethod("GET")

    val task = session.dataTaskWithRequest(request)
    task.resume()
    session.finishTasksAndInvalidate()
}

private class MainDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch_async(dispatch_get_main_queue()) {
            block.run()
        }
    }
}

private class MainScope : CoroutineScope {
    private val dispatcher = MainDispatcher()
    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = dispatcher + job
}