import Foundation

@MainActor
final class FilePlanPersistence: PlanPersistence {
    let fileURL: URL

    init(fileURL: URL) {
        self.fileURL = fileURL
    }

    static func local() throws -> FilePlanPersistence {
        let support = try FileManager.default.url(for: .applicationSupportDirectory,
                                                  in: .userDomainMask,
                                                  appropriateFor: nil, create: true)
        return FilePlanPersistence(fileURL: support
            .appendingPathComponent("StudyAlarm", isDirectory: true)
            .appendingPathComponent("plans.json"))
    }

    func load() throws -> PlanDatabase {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return PlanDatabase() }
        let database = try JSONDecoder().decode(PlanDatabase.self, from: Data(contentsOf: fileURL))
        try database.validate()
        return database
    }

    func save(_ database: PlanDatabase) throws {
        try database.validate()
        var directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        // Plans and alarm IDs belong to this installation; do not restore them from cloud backup.
        var resources = URLResourceValues()
        resources.isExcludedFromBackup = true
        try directory.setResourceValues(resources)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(database)
        #if os(iOS)
        try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        #else
        try data.write(to: fileURL, options: .atomic)
        #endif
    }
}
