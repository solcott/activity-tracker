import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

/** The snippets are copied from `iosExport/build/SwiftExport`, as Kotlin 2.5.0-Beta2 generates it, except where a test says otherwise. */
class SwiftExportStubsTest {
    /** `IosShell.swift`: an exported object. Its private initialiser is never called. */
    private val exportedObject =
        """
        extension ExportedKotlinPackages.io.github.solcott.activitytracker.ios {
            public final class FitSupport: KotlinRuntime.KotlinBase {
                public static var shared: ExportedKotlinPackages.io.github.solcott.activitytracker.ios.FitSupport {
                    get {
                        return ExportedKotlinPackages.io.github.solcott.activitytracker.ios.FitSupport.__createClassWrapper(externalRCRef: io_github_solcott_activitytracker_ios_FitSupport_get())
                    }
                }
                private override init() {
                    fatalError()
                }
                package override init(
                    __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer,
                    options: KotlinRuntime.KotlinBaseConstructionOptions
                ) {
                    super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, options: options);
                }
            }
        }
        """
            .trimIndent()

    /** `OrgJetbrainsKotlinxKotlinxDatetime.swift`: `LocalDate.Companion.serializer()`, whose return type is in a hidden module. */
    private val stubbedFunction =
        """
        extension ExportedKotlinPackages.kotlinx.datetime {
            public final class LocalDate: KotlinRuntime.KotlinBase {
                public final class Companion: KotlinRuntime.KotlinBase {
                    public func orNull(
                        year: Swift.Int32,
                        month: ExportedKotlinPackages.kotlinx.datetime.Month,
                        day: Swift.Int32
                    ) -> ExportedKotlinPackages.kotlinx.datetime.LocalDate? {
                        return { switch kotlinx_datetime_LocalDate_Companion_orNull(self.__externalRCRef(), year, month.__externalRCRef(), day) { case nil: .none; case let res?: ExportedKotlinPackages.kotlinx.datetime.LocalDate.__createClassWrapper(externalRCRef: res); } }()
                    }
                    public func serializer() -> any ExportedKotlinPackages.kotlinx.serialization.KSerializer {
                        fatalError()
                    }
                }
            }
        }
        """
            .trimIndent()

    /** `KotlinStdlib.swift`: `ByteArray(size:)`, a stubbed initialiser whose signature is written over several lines. */
    private val stubbedInitialiser =
        """
        extension ExportedKotlinPackages.kotlin {
            public final class ByteArray: KotlinRuntime.KotlinBase {
                public var size: Swift.Int32 {
                    get {
                        return kotlin_ByteArray_size_get(self.__externalRCRef())
                    }
                }
                public init(
                    size: Swift.Int32
                ) {
                    fatalError()
                }
                package override init(
                    __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer,
                    options: KotlinRuntime.KotlinBaseConstructionOptions
                ) {
                    super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, options: options);
                }
            }
        }
        """
            .trimIndent()

    /**
     * A stubbed getter and a stubbed function with a multi-line signature, shaped as the generator writes accessors and signatures. Not
     * copied: the contract probe's stubs of this shape (a state's `TextFieldState` field and its `copy`) are gone with the Compose type.
     */
    private val stubbedGetterAndCopy =
        """
        extension ExportedKotlinPackages.io.github.solcott.activitytracker.app.login {
            public final class LoginState: KotlinRuntime.KotlinBase {
                public var handle: ExportedKotlinPackages.androidx.compose.foundation.text.input.TextFieldState {
                    get {
                        fatalError()
                    }
                }
                public var submitting: Swift.Bool {
                    get {
                        return io_github_solcott_activitytracker_app_login_LoginState_submitting_get(self.__externalRCRef())
                    }
                }
                public func copy(
                    handle: ExportedKotlinPackages.androidx.compose.foundation.text.input.TextFieldState,
                    submitting: Swift.Bool
                ) -> ExportedKotlinPackages.io.github.solcott.activitytracker.app.login.LoginState {
                    fatalError()
                }
            }
        }
        """
            .trimIndent()

    /** `OrgJetbrainsKotlinxKotlinxDatetime.swift`: the switches generated for an enum end in a `default:` that can't be reached. */
    private val enumDefaults =
        """
        extension ExportedKotlinPackages.kotlinx.datetime {
            public enum DayOfWeek: KotlinRuntimeSupport._KotlinBridgeable, Swift.CaseIterable, Swift.LosslessStringConvertible, Swift.RawRepresentable {
                case MONDAY
                case SUNDAY
                public var description: Swift.String {
                    get {
                        switch self {
                        case .MONDAY: "MONDAY"
                        case .SUNDAY: "SUNDAY"
                        default: fatalError()
                        }
                    }
                }
                public init(
                    __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer,
                    options: KotlinRuntime.KotlinBaseConstructionOptions
                ) {
                    switch kotlinx_datetime_DayOfWeek_ordinal(__externalRCRefUnsafe) {
                    case 0: self = .MONDAY
                    case 6: self = .SUNDAY
                    default: fatalError()
                    }
                }
            }
        }
        """
            .trimIndent()

    /** `OrgJetbrainsKotlinxKotlinxCoroutinesCore.swift`: a Kotlin function deprecated as an error, exported as an unavailable operator. */
    private val stubbedOperator =
        """
        extension ExportedKotlinPackages.kotlinx.coroutines {
            open class CoroutineDispatcher: ExportedKotlinPackages.kotlin.coroutines.AbstractCoroutineContextElement {
                @available(*, unavailable, message: "Operator '+' on two CoroutineDispatcher objects is meaningless.")
                public static func +(
                    this: ExportedKotlinPackages.kotlinx.coroutines.CoroutineDispatcher,
                    other: ExportedKotlinPackages.kotlinx.coroutines.CoroutineDispatcher
                ) -> ExportedKotlinPackages.kotlinx.coroutines.CoroutineDispatcher {
                    fatalError()
                }
            }
        }
        """
            .trimIndent()

    /** `AndroidxRoom3Room3Runtime.swift`: the Swift side of a suspend function returning `Nothing`, whose continuation is never called. */
    private val neverContinuation =
        """
        @_cdecl("androidx_room3_TransactionScope_rollback_reverse_swift")
        package func androidx_room3_TransactionScope_rollback_reverse_swift(_ `self`: Swift.UnsafeMutableRawPointer, _ continuation: Swift.UnsafeMutableRawPointer) -> Swift.Bool {
            let __continuation: (Swift.Never) -> Swift.Void = {
            let _ = KotlinRuntime.KotlinBase(__externalRCRefUnsafe: continuation, options: .asBestFittingWrapper)
            return { _ in fatalError() }
        }()
            return true
        }
        """
            .trimIndent()

    /** `IoGithubSolcottUistate.swift`: an object whose class extends another exported class has no `override` on its initialiser. */
    private val exportedSubclassObject =
        """
        extension ExportedKotlinPackages.io.github.solcott.uistate {
            open class LoadStatus: KotlinRuntime.KotlinBase {
                public final class Idle: ExportedKotlinPackages.io.github.solcott.uistate.LoadStatus {
                    public static var shared: ExportedKotlinPackages.io.github.solcott.uistate.LoadStatus.Idle {
                        get {
                            return ExportedKotlinPackages.io.github.solcott.uistate.LoadStatus.Idle.__createClassWrapper(externalRCRef: io_github_solcott_uistate_LoadStatus_Idle_get())
                        }
                    }
                    private init() {
                        fatalError()
                    }
                }
            }
        }
        """
            .trimIndent()

    /**
     * `FeatureRecordApi.swift`, before `SaveBatch` had a constructor taking a plain map: an event with an `ImmutableMap` in its constructor
     * has a getter and no initialiser. `FilePicked` beside it has one, and `Back` is an object.
     */
    private val events =
        """
        public final class _ExportedKotlinPackages_io_github_solcott_activitytracker_app_record_ImportActivityEvent_Back: KotlinRuntime.KotlinBase, ExportedKotlinPackages.io.github.solcott.activitytracker.app.record.ImportActivityEvent {
            public static var shared: FeatureRecordApi._ExportedKotlinPackages_io_github_solcott_activitytracker_app_record_ImportActivityEvent_Back {
                get {
                    return FeatureRecordApi._ExportedKotlinPackages_io_github_solcott_activitytracker_app_record_ImportActivityEvent_Back.__createClassWrapper(externalRCRef: io_github_solcott_activitytracker_app_record_ImportActivityEvent_Back_get())
                }
            }
            private override init() {
                fatalError()
            }
        }
        public final class _ExportedKotlinPackages_io_github_solcott_activitytracker_app_record_ImportActivityEvent_FilePicked: KotlinRuntime.KotlinBase, ExportedKotlinPackages.io.github.solcott.activitytracker.app.record.ImportActivityEvent {
            public var name: Swift.String {
                get {
                    return io_github_solcott_activitytracker_app_record_ImportActivityEvent_FilePicked_name_get(self.__externalRCRef())
                }
            }
            package override init(
                __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer,
                options: KotlinRuntime.KotlinBaseConstructionOptions
            ) {
                super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, options: options);
            }
            public init(
                name: Swift.String,
                bytes: ExportedKotlinPackages.kotlinx.io.bytestring.ByteString
            ) {
                let __kt = io_github_solcott_activitytracker_app_record_ImportActivityEvent_FilePicked_init_allocate()
                super.init(__externalRCRefUnsafe: __kt, options: .asBoundBridge);
            }
        }
        public final class _ExportedKotlinPackages_io_github_solcott_activitytracker_app_record_ImportActivityEvent_SaveBatch: KotlinRuntime.KotlinBase, ExportedKotlinPackages.io.github.solcott.activitytracker.app.record.ImportActivityEvent {
            public var defaultTitles: any ExportedKotlinPackages.kotlinx.collections.immutable.ImmutableMap {
                get {
                    return KotlinRuntime.KotlinBase.__createProtocolWrapper(externalRCRef: io_github_solcott_activitytracker_app_record_ImportActivityEvent_SaveBatch_defaultTitles_get(self.__externalRCRef()), conformsTo: ExportedKotlinPackages.kotlinx.collections.immutable.ImmutableMap.Type.self) as! any ExportedKotlinPackages.kotlinx.collections.immutable.ImmutableMap
                }
            }
            package override init(
                __externalRCRefUnsafe: Swift.UnsafeMutableRawPointer,
                options: KotlinRuntime.KotlinBaseConstructionOptions
            ) {
                super.init(__externalRCRefUnsafe: __externalRCRefUnsafe, options: options);
            }
            public func sealedType() -> ExportedKotlinPackages.io.github.solcott.activitytracker.app.record.ImportActivityEvent_SealedType {
                .saveBatch(.init(self))
            }
        }
        extension ExportedKotlinPackages.io.github.solcott.activitytracker.app.record {
            public final class ImportActivityState: KotlinRuntime.KotlinBase {
                public var busy: Swift.Bool {
                    get {
                        return io_github_solcott_activitytracker_app_record_ImportActivityState_busy_get(self.__externalRCRef())
                    }
                }
            }
        }
        """
            .trimIndent()

    @Test
    fun `an event with no initialiser is found, and one with an initialiser, an object and a state are not`() {
        assertEquals(listOf("ImportActivityEvent.SaveBatch"), swiftEventsWithoutInitialiser(events))
    }

    @Test
    fun `an object that extends an exported class is not a stub`() {
        val scan = swiftExportStubs(exportedSubclassObject)

        assertEquals(emptyList(), scan.stubs)
        assertEquals(1, scan.objectInitialisers)
    }

    @Test
    fun `a declaration Swift marks unavailable is not a stub, since nothing can call it`() {
        val scan = swiftExportStubs(stubbedOperator)

        assertEquals(emptyList(), scan.stubs)
        assertEquals(emptyList(), scan.unrecognised)
    }

    @Test
    fun `a stubbed operator is found under its symbol`() {
        // The same declaration without the attribute that makes it uncallable.
        val stub = swiftExportStubs(stubbedOperator.lines().filterNot { "@available" in it }.joinToString("\n")).stubs.single()

        assertEquals("ExportedKotlinPackages.kotlinx.coroutines.CoroutineDispatcher.+", stub.declaration)
    }

    @Test
    fun `the continuation of a call that never returns is not a stub`() {
        val scan = swiftExportStubs(neverContinuation)

        assertEquals(emptyList(), scan.stubs)
        assertEquals(emptyList(), scan.unrecognised)
    }

    @Test
    fun `an exported object's private initialiser is not a stub`() {
        val scan = swiftExportStubs(exportedObject)

        assertEquals(emptyList(), scan.stubs)
        assertEquals(1, scan.objectInitialisers)
        assertEquals(emptyList(), scan.unrecognised)
    }

    @Test
    fun `a stubbed function is found by name, with the types around it`() {
        val stub = swiftExportStubs(stubbedFunction).stubs.single()

        assertEquals("ExportedKotlinPackages.kotlinx.datetime.LocalDate.Companion.serializer", stub.declaration)
        assertEquals("serializer", stub.member)
        assertEquals(12, stub.line)
    }

    @Test
    fun `a stubbed initialiser with its signature over several lines is found`() {
        val stub = swiftExportStubs(stubbedInitialiser).stubs.single()

        assertEquals("ExportedKotlinPackages.kotlin.ByteArray.init", stub.declaration)
    }

    @Test
    fun `a stubbed getter is named for its property, and a stubbed copy is found beside it`() {
        val scan = swiftExportStubs(stubbedGetterAndCopy)

        assertEquals(
            listOf(
                "ExportedKotlinPackages.io.github.solcott.activitytracker.app.login.LoginState.handle",
                "ExportedKotlinPackages.io.github.solcott.activitytracker.app.login.LoginState.copy",
            ),
            scan.stubs.map { it.declaration },
        )
        assertEquals(emptyList(), scan.unrecognised)
    }

    @Test
    fun `an enum's unreachable defaults are not stubs`() {
        val scan = swiftExportStubs(enumDefaults)

        assertEquals(emptyList(), scan.stubs)
        assertEquals(emptyList(), scan.unrecognised)
    }

    @Test
    fun `a fatalError in any other shape is reported as unrecognised, not passed over`() {
        val scan = swiftExportStubs("public func f() -> Swift.Int32 {\n    return x ?? fatalError()\n}\n")

        assertEquals(emptyList(), scan.stubs)
        assertEquals(listOf(2), scan.unrecognised)
    }

    @Test
    fun `the contract modules are the text field state and every API module, named for Swift`() {
        val paths = listOf(":feature-login-ui", ":feature-gear-list-api", ":core-data", ":app-common-api", ":core-textfield", ":iosShell")

        assertEquals(
            listOf(
                SwiftExportedModule(":app-common-api", "AppCommonApi"),
                SwiftExportedModule(":core-textfield", "CoreTextfield"),
                SwiftExportedModule(":feature-gear-list-api", "FeatureGearListApi"),
            ),
            swiftContractModules(paths),
        )
        assertEquals("IosShell", swiftModuleName(":iosShell"))
    }

    @Test
    fun `an allowed name is a member or the end of a declaration`() {
        val stub = swiftExportStubs(stubbedFunction).stubs.single()

        assertEquals("serializer", stub.allowedBy(setOf("serializer")))
        assertEquals("LocalDate.Companion.serializer", stub.allowedBy(setOf("LocalDate.Companion.serializer")))
        assertEquals(null, stub.allowedBy(setOf("Date.Companion.serializer", "copy")))
    }

    private val projectDir: File by lazy {
        createTempDirectory("swift-export-stubs").toFile().apply {
            // The contract modules only need to exist: the plugin reads the build's module list for the ones to check.
            resolve("settings.gradle.kts")
                .writeText("rootProject.name = \"swift-export-stubs-test\"\ninclude(\":feature-login-api\", \":feature-login-ui\")\n")
            resolve("feature-login-api").mkdirs()
            resolve("feature-login-ui").mkdirs()
            // Stands in for the export tasks Xcode's build phase runs: the check reads what tasks named *SwiftExport write.
            resolve("build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        base
                        id("activitytracker.swift.export")
                    }

                    tasks.register<Sync>("iosSimulatorArm64SwiftExport") {
                        from("generated")
                        into(layout.buildDirectory.dir("SwiftExport/iosSimulatorArm64"))
                    }

                    tasks.named<CheckSwiftExportStubs>("checkSwiftExportStubs") {
                        failOn.add("IosShell")
                        sentinelModule.set("IosShell")
                        allowed.put("serializer", "Swift never asks a screen for its serializer")
                    }
                    """
                        .trimIndent(),
                )
            resolve("generated/files/IosShell").apply { mkdirs() }.resolve("IosShell.swift").writeText(exportedObject)
        }
    }

    private fun generated(module: String, swift: String) {
        projectDir.resolve("generated/files/$module").apply { mkdirs() }.resolve("$module.swift").writeText(swift)
    }

    private val report: File
        get() = projectDir.resolve("build/reports/swift-export/stubs.txt")

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    @Test
    fun `the task runs the export first and passes when nothing checked is a stub`() {
        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":iosSimulatorArm64SwiftExport")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":$CHECK_SWIFT_EXPORT_STUBS_TASK_NAME")?.outcome)
    }

    @Test
    fun `a stub in a checked module fails the build, naming the declaration`() {
        generated("FeatureLoginApi", stubbedGetterAndCopy)

        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).buildAndFail()

        assertContains(
            result.output,
            "FeatureLoginApi: ExportedKotlinPackages.io.github.solcott.activitytracker.app.login.LoginState.handle",
        )
        assertContains(result.output, "FeatureLoginApi: ExportedKotlinPackages.io.github.solcott.activitytracker.app.login.LoginState.copy")
    }

    @Test
    fun `a feature added to the build is checked with no change to the script`() {
        projectDir.resolve("settings.gradle.kts").appendText("include(\":feature-new-api\")\n")
        projectDir.resolve("feature-new-api").mkdirs()
        generated("FeatureNewApi", stubbedGetterAndCopy)

        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).buildAndFail()

        assertContains(result.output, "FeatureNewApi: ExportedKotlinPackages.io.github.solcott.activitytracker.app.login.LoginState.handle")
    }

    @Test
    fun `an event Swift can't build fails the build, naming the event`() {
        generated("FeatureLoginApi", events)

        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).buildAndFail()

        assertContains(result.output, "Swift can't send these events")
        assertContains(result.output, "FeatureLoginApi: ImportActivityEvent.SaveBatch")
        assertFalse("ImportActivityEvent.FilePicked" in result.output)
    }

    @Test
    fun `an allowed stub passes and is listed with its reason`() {
        generated("FeatureLoginApi", stubbedFunction)

        runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).build()

        assertContains(report.readText(), "FeatureLoginApi (checked): 1")
        assertContains(report.readText(), "LocalDate.Companion.serializer [allowed: Swift never asks a screen for its serializer]")
    }

    @Test
    fun `a stub in a module that isn't checked is only reported`() {
        generated("KotlinStdlib", stubbedInitialiser)

        runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).build()

        assertContains(report.readText(), "KotlinStdlib (not checked): 1")
        assertContains(report.readText(), "ExportedKotlinPackages.kotlin.ByteArray.init")
    }

    @Test
    fun `no object initialiser in the sentinel module fails as a changed format`() {
        generated("IosShell", "public final class FitSupport: KotlinRuntime.KotlinBase {\n}\n")

        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).buildAndFail()

        assertContains(result.output, "IosShell.swift has no `private override init()` stub")
    }

    @Test
    fun `a fatalError it can't read fails as a changed format`() {
        generated("FeatureLoginApi", "public func f() -> Swift.Int32 {\n    return x ?? fatalError()\n}\n")

        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).buildAndFail()

        assertContains(result.output, "FeatureLoginApi.swift line 2")
    }

    @Test
    fun `with no export in the build the task fails instead of passing on nothing`() {
        projectDir.resolve("build.gradle.kts").apply { writeText(readText().replace("iosSimulatorArm64SwiftExport", "unrelated")) }

        val result = runner(CHECK_SWIFT_EXPORT_STUBS_TASK_NAME).buildAndFail()

        assertContains(result.output, "No generated Swift to check")
        assertFalse(report.exists())
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*tasks, "--configuration-cache")
}
