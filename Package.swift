// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SpearoGo",
    platforms: [
        .watchOS(.v10)
    ],
    products: [
        .executable(name: "SpearoGo", targets: ["SpearoGo"])
    ],
    dependencies: [],
    targets: [
        .executableTarget(
            name: "SpearoGo",
            path: "SpearoGo"
        )
    ]
)
