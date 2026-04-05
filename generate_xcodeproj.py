#!/usr/bin/env python3
"""
generate_xcodeproj.py — Generates SpearoGo.xcodeproj for the Spearo Go watchOS app.
Run once from the repo root:  python3 generate_xcodeproj.py
Then open SpearoGo.xcodeproj in Xcode.
"""
import os, uuid, textwrap

BASE    = os.path.dirname(os.path.abspath(__file__))
PROJ    = os.path.join(BASE, "SpearoGo.xcodeproj")
WS      = os.path.join(PROJ, "project.xcworkspace")

def uid():
    return uuid.uuid4().hex[:24].upper()

# ─── File inventory ──────────────────────────────────────────────────────────
# (display_name, SpearoGo-relative path, is_resource)
SOURCES = [
    ("SpearoGoApp.swift",      "SpearoGoApp.swift",               False),
    ("AppState.swift",         "AppState.swift",                  False),
    ("ContentView.swift",      "ContentView.swift",               False),
    # Views
    ("VerdictPage.swift",      "Views/VerdictPage.swift",         False),
    ("ConditionsPage.swift",   "Views/ConditionsPage.swift",      False),
    ("WaterPage.swift",        "Views/WaterPage.swift",           False),
    ("TidesPage.swift",        "Views/TidesPage.swift",           False),
    ("FishActivityPage.swift", "Views/FishActivityPage.swift",    False),
    ("LocationsView.swift",    "Views/LocationsView.swift",       False),
    # Models
    ("WeatherData.swift",      "Models/WeatherData.swift",        False),
    ("MarineData.swift",       "Models/MarineData.swift",         False),
    ("TideData.swift",         "Models/TideData.swift",           False),
    ("SolunarData.swift",      "Models/SolunarData.swift",        False),
    ("DiveScore.swift",        "Models/DiveScore.swift",          False),
    ("SavedLocation.swift",    "Models/SavedLocation.swift",      False),
    # Services
    ("WeatherService.swift",   "Services/WeatherService.swift",   False),
    ("MarineService.swift",    "Services/MarineService.swift",    False),
    ("TideService.swift",      "Services/TideService.swift",      False),
    ("SolunarService.swift",   "Services/SolunarService.swift",   False),
    ("LocationService.swift",  "Services/LocationService.swift",  False),
    ("ScoreService.swift",     "Services/ScoreService.swift",     False),
    ("CacheService.swift",     "Services/CacheService.swift",     False),
    # Utils
    ("Constants.swift",        "Utils/Constants.swift",           False),
    ("Brand.swift",            "Utils/Brand.swift",               False),
    ("Typography.swift",       "Utils/Typography.swift",          False),
    ("Modifiers.swift",        "Utils/Modifiers.swift",           False),
    ("PersonalityCopy.swift",  "Utils/PersonalityCopy.swift",     False),
    ("PreviewHelpers.swift",   "Utils/PreviewHelpers.swift",      False),
    # Resources
    ("Assets.xcassets",        "Assets.xcassets",                 True),
]

# ─── Generate UUIDs ───────────────────────────────────────────────────────────
PROJ_UUID      = uid()
MAIN_GRP       = uid()
PRODUCTS_GRP   = uid()
SOURCES_PHASE  = uid()
RESOURCES_PHASE = uid()
FRAMEWORKS_PHASE = uid()
TARGET_UUID    = uid()
PROJ_CFG_LIST  = uid()
TARGET_CFG_LIST = uid()
DEBUG_PROJ     = uid()
RELEASE_PROJ   = uid()
DEBUG_TARGET   = uid()
RELEASE_TARGET = uid()
PRODUCT_REF    = uid()

# Per-file UUIDs
files = []
for name, path, is_res in SOURCES:
    files.append({
        "name":   name,
        "path":   path,
        "is_res": is_res,
        "ref":    uid(),
        "bf":     uid(),   # PBXBuildFile
    })

# Groups
VIEWS_GRP    = uid()
MODELS_GRP   = uid()
SERVICES_GRP = uid()
UTILS_GRP    = uid()

def pbxproj():
    lines = []
    a = lines.append

    a("// !$*UTF8*$!")
    a("{")
    a("  archiveVersion = 1;")
    a("  classes = {};")
    a("  objectVersion = 56;")
    a("  objects = {")
    a("")

    # ── PBXBuildFile ──────────────────────────────────────────────────────────
    a("/* Begin PBXBuildFile section */")
    for f in files:
        phase = "PBXResourcesBuildPhase" if f["is_res"] else "PBXSourcesBuildPhase"
        a(f"    {f['bf']} /* {f['name']} in {'Resources' if f['is_res'] else 'Sources'} */ = {{isa = PBXBuildFile; fileRef = {f['ref']} /* {f['name']} */; }};")
    a("/* End PBXBuildFile section */")
    a("")

    # ── PBXFileReference ─────────────────────────────────────────────────────
    a("/* Begin PBXFileReference section */")
    a(f"    {PRODUCT_REF} /* SpearoGo.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = SpearoGo.app; sourceTree = BUILT_PRODUCTS_DIR; }};")
    for f in files:
        if f["is_res"] and f["name"].endswith(".xcassets"):
            ftype = "folder.assetcatalog"
            last  = f["name"]
            src   = "\"<group>\""
        elif f["name"] == "Info.plist":
            ftype = "text.plist.xml"
            last  = f["name"]
            src   = "\"<group>\""
        else:
            ftype = "sourcecode.swift"
            last  = f["name"]
            src   = "\"<group>\""
        a(f"    {f['ref']} /* {f['name']} */ = {{isa = PBXFileReference; lastKnownFileType = {ftype}; path = {last}; sourceTree = {src}; }};")
    a("/* End PBXFileReference section */")
    a("")

    # ── PBXFrameworksBuildPhase ───────────────────────────────────────────────
    a("/* Begin PBXFrameworksBuildPhase section */")
    a(f"    {FRAMEWORKS_PHASE} /* Frameworks */ = {{")
    a("        isa = PBXFrameworksBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    a("/* End PBXFrameworksBuildPhase section */")
    a("")

    # ── PBXGroup ─────────────────────────────────────────────────────────────
    def group_children(names):
        matched = [f for f in files if f["name"] in names]
        return "\n".join(f"                {f['ref']} /* {f['name']} */," for f in matched)

    root_files   = [f for f in files if "/" not in f["path"]]
    views_files  = [f for f in files if f["path"].startswith("Views/")]
    models_files = [f for f in files if f["path"].startswith("Models/")]
    svc_files    = [f for f in files if f["path"].startswith("Services/")]
    util_files   = [f for f in files if f["path"].startswith("Utils/")]

    def file_refs(lst):
        return "\n".join(f"                {f['ref']} /* {f['name']} */," for f in lst)

    a("/* Begin PBXGroup section */")

    # Root group
    a(f"    {MAIN_GRP} = {{")
    a("        isa = PBXGroup;")
    a("        children = (")
    for f in root_files:
        a(f"            {f['ref']} /* {f['name']} */,")
    a(f"            {VIEWS_GRP} /* Views */,")
    a(f"            {MODELS_GRP} /* Models */,")
    a(f"            {SERVICES_GRP} /* Services */,")
    a(f"            {UTILS_GRP} /* Utils */,")
    a(f"            {PRODUCTS_GRP} /* Products */,")
    a("        );")
    a("        sourceTree = \"<group>\";")
    a("    };")

    # Products group
    a(f"    {PRODUCTS_GRP} /* Products */ = {{")
    a("        isa = PBXGroup;")
    a("        children = (")
    a(f"            {PRODUCT_REF} /* SpearoGo.app */,")
    a("        );")
    a("        name = Products;")
    a("        sourceTree = \"<group>\";")
    a("    };")

    for grp_uuid, grp_name, grp_files in [
        (VIEWS_GRP,    "Views",    views_files),
        (MODELS_GRP,   "Models",   models_files),
        (SERVICES_GRP, "Services", svc_files),
        (UTILS_GRP,    "Utils",    util_files),
    ]:
        a(f"    {grp_uuid} /* {grp_name} */ = {{")
        a("        isa = PBXGroup;")
        a("        children = (")
        for f in grp_files:
            a(f"            {f['ref']} /* {f['name']} */,")
        a("        );")
        a(f"        name = {grp_name};")
        a("        path = \"" + grp_name + "\";")
        a("        sourceTree = \"<group>\";")
        a("    };")

    a("/* End PBXGroup section */")
    a("")

    # ── PBXNativeTarget ───────────────────────────────────────────────────────
    a("/* Begin PBXNativeTarget section */")
    a(f"    {TARGET_UUID} /* SpearoGo */ = {{")
    a("        isa = PBXNativeTarget;")
    a("        buildConfigurationList = " + TARGET_CFG_LIST + " /* Build configuration list for PBXNativeTarget \"SpearoGo\" */;")
    a("        buildPhases = (")
    a(f"            {SOURCES_PHASE} /* Sources */,")
    a(f"            {FRAMEWORKS_PHASE} /* Frameworks */,")
    a(f"            {RESOURCES_PHASE} /* Resources */,")
    a("        );")
    a("        buildRules = (")
    a("        );")
    a("        dependencies = (")
    a("        );")
    a("        name = SpearoGo;")
    a("        productName = SpearoGo;")
    a(f"        productReference = {PRODUCT_REF} /* SpearoGo.app */;")
    a("        productType = \"com.apple.product-type.application\";")
    a("    };")
    a("/* End PBXNativeTarget section */")
    a("")

    # ── PBXProject ───────────────────────────────────────────────────────────
    a("/* Begin PBXProject section */")
    a(f"    {PROJ_UUID} /* Project object */ = {{")
    a("        isa = PBXProject;")
    a("        attributes = {")
    a("            BuildIndependentTargetsInParallel = 1;")
    a("            LastSwiftUpdateCheck = 1500;")
    a("            LastUpgradeCheck = 1500;")
    a("            TargetAttributes = {")
    a(f"                {TARGET_UUID} = {{")
    a("                    CreatedOnToolsVersion = 15.0;")
    a("                };")
    a("            };")
    a("        };")
    a(f"        buildConfigurationList = {PROJ_CFG_LIST} /* Build configuration list for PBXProject \"SpearoGo\" */;")
    a("        compatibilityVersion = \"Xcode 14.0\";")
    a("        developmentRegion = en;")
    a("        hasScannedForEncodings = 0;")
    a("        knownRegions = (")
    a("            en,")
    a("            Base,")
    a("        );")
    a(f"        mainGroup = {MAIN_GRP};")
    a(f"        productRefGroup = {PRODUCTS_GRP} /* Products */;")
    a("        projectDirPath = \"\";")
    a("        projectRoot = \"\";")
    a("        targets = (")
    a(f"            {TARGET_UUID} /* SpearoGo */,")
    a("        );")
    a("    };")
    a("/* End PBXProject section */")
    a("")

    # ── PBXResourcesBuildPhase ────────────────────────────────────────────────
    a("/* Begin PBXResourcesBuildPhase section */")
    a(f"    {RESOURCES_PHASE} /* Resources */ = {{")
    a("        isa = PBXResourcesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    for f in files:
        if f["is_res"]:
            a(f"            {f['bf']} /* {f['name']} in Resources */,")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    a("/* End PBXResourcesBuildPhase section */")
    a("")

    # ── PBXSourcesBuildPhase ──────────────────────────────────────────────────
    a("/* Begin PBXSourcesBuildPhase section */")
    a(f"    {SOURCES_PHASE} /* Sources */ = {{")
    a("        isa = PBXSourcesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    for f in files:
        if not f["is_res"]:
            a(f"            {f['bf']} /* {f['name']} in Sources */,")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    a("/* End PBXSourcesBuildPhase section */")
    a("")

    # ── XCBuildConfiguration ──────────────────────────────────────────────────
    base_settings = [
        ("ALWAYS_SEARCH_USER_PATHS", "NO"),
        ("CLANG_ANALYZER_NONNULL", "YES"),
        ("CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION", "YES_AGGRESSIVE"),
        ("CLANG_CXX_LANGUAGE_STANDARD", "\"gnu++20\""),
        ("CLANG_ENABLE_MODULES", "YES"),
        ("CLANG_ENABLE_OBJC_ARC", "YES"),
        ("CLANG_ENABLE_OBJC_WEAK", "YES"),
        ("CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING", "YES"),
        ("CLANG_WARN_BOOL_CONVERSION", "YES"),
        ("CLANG_WARN_COMMA", "YES"),
        ("CLANG_WARN_CONSTANT_CONVERSION", "YES"),
        ("CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS", "YES"),
        ("CLANG_WARN_DIRECT_OBJC_ISA_USAGE", "YES_ERROR"),
        ("CLANG_WARN_DOCUMENTATION_COMMENTS", "YES"),
        ("CLANG_WARN_EMPTY_BODY", "YES"),
        ("CLANG_WARN_ENUM_CONVERSION", "YES"),
        ("CLANG_WARN_INFINITE_RECURSION", "YES"),
        ("CLANG_WARN_INT_CONVERSION", "YES"),
        ("CLANG_WARN_NON_LITERAL_NULL_CONVERSION", "YES"),
        ("CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF", "YES"),
        ("CLANG_WARN_OBJC_LITERAL_CONVERSION", "YES"),
        ("CLANG_WARN_OBJC_ROOT_CLASS", "YES_ERROR"),
        ("CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER", "YES"),
        ("CLANG_WARN_RANGE_LOOP_ANALYSIS", "YES"),
        ("CLANG_WARN_STRICT_PROTOTYPES", "YES"),
        ("CLANG_WARN_SUSPICIOUS_MOVE", "YES"),
        ("CLANG_WARN_UNGUARDED_AVAILABILITY", "YES_AGGRESSIVE"),
        ("CLANG_WARN_UNREACHABLE_CODE", "YES"),
        ("CLANG_WARN__DUPLICATE_METHOD_MATCH", "YES"),
        ("GCC_C_LANGUAGE_STANDARD", "gnu17"),
        ("GCC_WARN_64_TO_32_BIT_CONVERSION", "YES"),
        ("GCC_WARN_ABOUT_RETURN_TYPE", "YES_ERROR"),
        ("GCC_WARN_UNDECLARED_SELECTOR", "YES"),
        ("GCC_WARN_UNINITIALIZED_AUTOS", "YES_AGGRESSIVE"),
        ("GCC_WARN_UNUSED_FUNCTION", "YES"),
        ("GCC_WARN_UNUSED_VARIABLE", "YES"),
        ("SDKROOT", "watchos"),
        ("SWIFT_VERSION", "5.0"),
        ("WATCHOS_DEPLOYMENT_TARGET", "10.0"),
    ]

    a("/* Begin XCBuildConfiguration section */")

    # Project Debug
    a(f"    {DEBUG_PROJ} /* Debug */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in base_settings:
        a(f"            {k} = {v};")
    a("            DEBUG_INFORMATION_FORMAT = dwarf;")
    a("            ENABLE_TESTABILITY = YES;")
    a("            GCC_DYNAMIC_NO_PIC = NO;")
    a("            GCC_OPTIMIZATION_LEVEL = 0;")
    a("            GCC_PREPROCESSOR_DEFINITIONS = (\"DEBUG=1\", \"$(inherited)\",);")
    a("            MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;")
    a("            MTL_FAST_MATH = YES;")
    a("            ONLY_ACTIVE_ARCH = YES;")
    a("            SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;")
    a("            SWIFT_OPTIMIZATION_LEVEL = \"-Onone\";")
    a("        };")
    a("        name = Debug;")
    a("    };")

    # Project Release
    a(f"    {RELEASE_PROJ} /* Release */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in base_settings:
        a(f"            {k} = {v};")
    a("            COPY_PHASE_STRIP = NO;")
    a("            DEBUG_INFORMATION_FORMAT = \"dwarf-with-dsym\";")
    a("            ENABLE_NS_ASSERTIONS = NO;")
    a("            MTL_FAST_MATH = YES;")
    a("            SWIFT_COMPILATION_MODE = wholemodule;")
    a("            SWIFT_OPTIMIZATION_LEVEL = \"-O\";")
    a("            VALIDATE_PRODUCT = YES;")
    a("        };")
    a("        name = Release;")
    a("    };")

    target_settings = [
        ("ASSETCATALOG_COMPILER_APPICON_NAME", "AppIcon"),
        ("ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME", "OceanBlue"),
        ("CODE_SIGN_STYLE", "Automatic"),
        ("CURRENT_PROJECT_VERSION", "1"),
        ("ENABLE_PREVIEWS", "YES"),
        ("GENERATE_INFOPLIST_FILE", "NO"),
        ("INFOPLIST_FILE", "SpearoGo/Info.plist"),
        ("MARKETING_VERSION", "1.0.0"),
        ("PRODUCT_BUNDLE_IDENTIFIER", "agency.visivo.SpearoGo"),
        ("PRODUCT_NAME", "$(TARGET_NAME)"),
        ("SKIP_INSTALL", "YES"),
        ("SWIFT_EMIT_LOC_STRINGS", "YES"),
        ("SWIFT_VERSION", "5.0"),
        ("TARGETED_DEVICE_FAMILY", "4"),
        ("WATCHOS_DEPLOYMENT_TARGET", "10.0"),
    ]

    # Target Debug
    a(f"    {DEBUG_TARGET} /* Debug */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in target_settings:
        a(f"            {k} = {v};")
    a("            SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;")
    a("        };")
    a("        name = Debug;")
    a("    };")

    # Target Release
    a(f"    {RELEASE_TARGET} /* Release */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in target_settings:
        a(f"            {k} = {v};")
    a("        };")
    a("        name = Release;")
    a("    };")

    a("/* End XCBuildConfiguration section */")
    a("")

    # ── XCConfigurationList ───────────────────────────────────────────────────
    a("/* Begin XCConfigurationList section */")
    a(f"    {PROJ_CFG_LIST} /* Build configuration list for PBXProject \"SpearoGo\" */ = {{")
    a("        isa = XCConfigurationList;")
    a("        buildConfigurations = (")
    a(f"            {DEBUG_PROJ} /* Debug */,")
    a(f"            {RELEASE_PROJ} /* Release */,")
    a("        );")
    a("        defaultConfigurationIsVisible = 0;")
    a("        defaultConfigurationName = Release;")
    a("    };")
    a(f"    {TARGET_CFG_LIST} /* Build configuration list for PBXNativeTarget \"SpearoGo\" */ = {{")
    a("        isa = XCConfigurationList;")
    a("        buildConfigurations = (")
    a(f"            {DEBUG_TARGET} /* Debug */,")
    a(f"            {RELEASE_TARGET} /* Release */,")
    a("        );")
    a("        defaultConfigurationIsVisible = 0;")
    a("        defaultConfigurationName = Release;")
    a("    };")
    a("/* End XCConfigurationList section */")
    a("")
    a("  };")
    a(f"  rootObject = {PROJ_UUID} /* Project object */;")
    a("}")

    return "\n".join(lines)

def workspace_contents():
    return textwrap.dedent("""\
    <?xml version="1.0" encoding="UTF-8"?>
    <Workspace version = "1.0">
       <FileRef location = "self:">
       </FileRef>
    </Workspace>
    """)

def workspace_settings():
    return textwrap.dedent("""\
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>IDEDidComputeMac32BitWarning</key>
        <true/>
        <key>PreviewsEnabled</key>
        <true/>
    </dict>
    </plist>
    """)

# ─── Write files ─────────────────────────────────────────────────────────────
os.makedirs(PROJ, exist_ok=True)
os.makedirs(WS,   exist_ok=True)

pbx_path = os.path.join(PROJ, "project.pbxproj")
with open(pbx_path, "w") as f:
    f.write(pbxproj())
print(f"✓ {pbx_path}")

ws_path = os.path.join(WS, "contents.xcworkspacedata")
with open(ws_path, "w") as f:
    f.write(workspace_contents())
print(f"✓ {ws_path}")

ws_settings = os.path.join(WS, "xcshareddata")
os.makedirs(ws_settings, exist_ok=True)
ws_settings_path = os.path.join(ws_settings, "IDEWorkspaceChecks.plist")
with open(ws_settings_path, "w") as f:
    f.write(workspace_settings())
print(f"✓ {ws_settings_path}")

print("\nDone. Open SpearoGo.xcodeproj in Xcode.")
print("Select scheme → Apple Watch Series 9 (45mm) → ⌘R")
