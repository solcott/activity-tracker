import KotlinRuntimeSupport
import KotlinStdlib

/// Kotlin's read-only collection as Swift export hands it over: a protocol with a size and an iterator, whose elements are untyped.
/// It is what a contract's `ImmutableList` or `ImmutableSet` field is in Swift, since the export erases the element type.
typealias KotlinCollection = ExportedKotlinPackages.kotlin.collections.Collection

extension ExportedKotlinPackages.kotlin.collections.Collection {
    /// The collection's elements as a Swift array of `Element`, in its order: how a native view reads a state's immutable list or set
    /// (dev/ios-swift-interop). Contracts keep their immutable collections; this is the one place their elements are cast.
    ///
    /// What can be `Element` (the roadmap's phase 4 probes):
    /// - `String`, and a Kotlin `Int` as `Int32` or a `Long` as `Int64`, never Swift's `Int`.
    /// - A Kotlin class Swift has a type for. A contract's own classes always have one. A class from another module has one only if
    ///   some exported signature names it, and a collection's type argument doesn't count: name it elsewhere in the contract.
    /// - Never a Kotlin enum: an enum element reaches Swift as an opaque object. The contract has to offer such a collection another
    ///   way.
    ///
    /// An element that isn't an `Element` stops a debug build here, so a wrong type shows in the first preview or snapshot, and is
    /// left out of a release build.
    func elements<Element>(as type: Element.Type = Element.self) -> [Element] {
        var elements: [Element] = []
        elements.reserveCapacity(Int(size))
        let iterator = iterator()
        while iterator.hasNext() {
            let next = iterator.next()
            if let element = next as? Element {
                elements.append(element)
            } else {
                assertionFailure("A Kotlin collection's element is \(next.map { "\(Swift.type(of: $0))" } ?? "nil"), not \(Element.self)")
            }
        }
        return elements
    }
}
