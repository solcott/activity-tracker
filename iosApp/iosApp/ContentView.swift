import IosShell
import SwiftUI
import UIKit

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosApp.shared.rootViewController(fitDecoder: SwiftFitDecoder())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
