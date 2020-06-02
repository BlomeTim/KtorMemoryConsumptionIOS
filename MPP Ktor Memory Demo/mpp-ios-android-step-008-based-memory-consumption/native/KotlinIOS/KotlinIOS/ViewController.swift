import UIKit
import SharedCode

let urlString = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Image_created_with_a_mobile_phone.png/2560px-Image_created_with_a_mobile_phone.png"
let expectedSize = 6417453

class ViewController: UIViewController {
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // We are trying to write a NSFileProviderExtension based on a Kotlin core.
        // There we are doing quite a lot of http requests via Ktor.
        // Sadly we did not find a possibility to stay below Apples overall 15 MB memory limit, yet.
        // We know using a default UrlSession ist not ideal, so we have already created a Client that uses a Background Session for downloads.
        // Still adding up memory on each HTTP call, we can not reduce the memory impact enough to stay below that limit.
        
        // Ktor seems to consume way more memory than a default NSUrlSession. And does not release all consumed memory afterwards.
        // For Comparison we implemented almost the same NSUrlSession call in Kotlin and Swift.
        // When firing 30 requests, the UrlSession itself came back to 23 MB Memory consumption, while Ktor does not go below 44MB afterwards.
        // Memory consumption can be checked out via memory tab in Xcode.
        // Is there anything we can do to reach something like the UrlSession memory consumption?
        
        // To keep the mpp project setup as simple as possible, we started with https://github.com/kotlin-hands-on/mpp-ios-android/tree/step-008
        
        let iterations = 30
        // 43 MB
        ActualKt.launchInKtorHttpClient(iterations: Int32(iterations), urlString: urlString, expectedSize: Int32(expectedSize))
        // 23 MB
        // ActualKt.runInKotlinNSURLSession(times:Int32(iterations), urlString: urlString, expectedSize: Int32(expectedSize))
        // 22 MB
        // runInSwiftUrlSession(times: iterations, useBackgroundSession: false)
    }
    
    func runInSwiftUrlSession(times: Int, useBackgroundSession: Bool = true) {
        class Delegate: NSObject, URLSessionDataDelegate {
            
            let loggingIterator: Int
            init(loggingIterator: Int) {
                self.loggingIterator = loggingIterator
            }
            var size = 0
            
            func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
                size = size + data.count
            }
            
            func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
                guard size == expectedSize else {
                    fatalError("receivedContent(\(size)) != expectedSize\(expectedSize)")
                }
                print("didRead \(size)")
            }
        }
        
        (0...times).forEach { (it) in
            let configuration = URLSessionConfiguration.default
            let session = URLSession(configuration: configuration, delegate: Delegate(loggingIterator: it), delegateQueue: OperationQueue.main)
            let request = URLRequest(url: URL(string: urlString)!)
            let task = session.dataTask(with: request)
            task.resume()
            
            // dealloc delegate between session <-> delegate
            session.finishTasksAndInvalidate()
        }
    }
}

