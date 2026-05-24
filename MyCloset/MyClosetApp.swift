import SwiftUI
import SwiftData
import Combine

@main
struct FitcommitApp: App {
    @StateObject private var modelContainerLoader = SwiftDataModelContainerLoader()
    @StateObject private var authenticationManager = AuthenticationManager.shared

    var body: some Scene {
        WindowGroup {
            FitcommitBootstrapView()
                .environmentObject(modelContainerLoader)
                .environmentObject(authenticationManager)
                .task {
                    await modelContainerLoader.prepareIfNeeded()
                }
        }
    }
}

private struct FitcommitBootstrapView: View {
    @EnvironmentObject private var modelContainerLoader: SwiftDataModelContainerLoader

    var body: some View {
        switch modelContainerLoader.state {
        case .loading:
            FitcommitLaunchLoadingView()
        case .ready(let modelContainer):
            FitcommitRootContentView()
                .modelContainer(modelContainer)
        }
    }
}

private struct FitcommitRootContentView: View {
    @Environment(\.modelContext) private var modelContext
    @EnvironmentObject private var authenticationManager: AuthenticationManager

    var body: some View {
        Group {
            if authenticationManager.isLoggedIn {
                MainTabView()
                    .transition(.opacity.combined(with: .scale(scale: 0.98)))
            } else {
                LoginView()
                    .transition(.opacity.combined(with: .scale(scale: 1.02)))
            }
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.86), value: authenticationManager.isLoggedIn)
        .onAppear {
            modelContext.autosaveEnabled = false
            authenticationManager.refreshLoginStateFromStorage()
        }
    }
}

private struct FitcommitLaunchLoadingView: View {
    var body: some View {
        VStack(spacing: 14) {
            ProgressView()
                .controlSize(.large)

            Text("FitCommit 준비 중")
                .font(.headline)
                .foregroundStyle(.primary)

            Text("옷장 데이터를 확인하고 있습니다.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}

@MainActor
private final class SwiftDataModelContainerLoader: ObservableObject {
    enum State {
        case loading
        case ready(ModelContainer)
    }

    @Published private(set) var state: State = .loading
    private var isPreparing = false

    func prepareIfNeeded() async {
        guard isPreparing == false else { return }
        isPreparing = true

        let modelContainer = await Task.detached(priority: .userInitiated) {
            FitcommitModelContainerFactory.makeModelContainer()
        }.value

        state = .ready(modelContainer)
    }
}

private enum FitcommitModelContainerFactory {
    nonisolated static func makeModelContainer() -> ModelContainer {
        do {
            return try makePersistentModelContainer()
        } catch {
            logContainerLoadFailure(error)
            resetPersistentStoreFiles()

            do {
                print("[SwiftData] Retrying with a clean persistent store after reset.")
                return try makePersistentModelContainer()
            } catch {
                logContainerLoadFailure(error)
            }

            do {
                let fallbackConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
                print("[SwiftData] Persistent store failed to load. Falling back to an in-memory store for this launch.")
                return try ModelContainer(for: schema, configurations: [fallbackConfiguration])
            } catch {
                print("[SwiftData] In-memory fallback also failed: \(error)")
                fatalError("SwiftData ModelContainer could not be created: \(error)")
            }
        }
    }

    private nonisolated static var schema: Schema {
        Schema([
            UserProfile.self,
            BodyProfile.self,
            ClosetItem.self
        ])
    }

    private nonisolated static func makePersistentModelContainer() throws -> ModelContainer {
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        return try ModelContainer(for: schema, configurations: [configuration])
    }

    private nonisolated static func logContainerLoadFailure(_ error: Error) {
        print("[SwiftData] Failed to load persistent ModelContainer.")
        print("[SwiftData] Error: \(error)")
        print("[SwiftData] Localized description: \(error.localizedDescription)")

        if let appSupportURL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            print("[SwiftData] Application Support directory: \(appSupportURL.path)")
        }

        let nsError = error as NSError
        print("[SwiftData] NSError domain: \(nsError.domain), code: \(nsError.code)")
        print("[SwiftData] NSError userInfo: \(nsError.userInfo)")
    }

    private nonisolated static func resetPersistentStoreFiles() {
        guard let appSupportURL = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            print("[SwiftData] Could not locate Application Support directory for store reset.")
            return
        }

        do {
            let storeFiles = try FileManager.default.contentsOfDirectory(
                at: appSupportURL,
                includingPropertiesForKeys: nil
            )
            .filter { url in
                let name = url.lastPathComponent
                return name.hasSuffix(".store")
                    || name.hasSuffix(".store-shm")
                    || name.hasSuffix(".store-wal")
            }

            guard storeFiles.isEmpty == false else {
                print("[SwiftData] No persistent store files found to reset in: \(appSupportURL.path)")
                return
            }

            for fileURL in storeFiles {
                try FileManager.default.removeItem(at: fileURL)
                print("[SwiftData] Removed incompatible store file: \(fileURL.lastPathComponent)")
            }
        } catch {
            print("[SwiftData] Failed to reset persistent store files: \(error)")
        }
    }
}
