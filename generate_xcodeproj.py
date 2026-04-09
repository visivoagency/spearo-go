#!/usr/bin/env python3
"""
generate_xcodeproj.py — Generates SpearoGo.xcodeproj for the Spearo Go watchOS app.

Produces two targets:
  1. SpearoGo Watch App  — the real watchOS app (all Swift code + assets)
  2. SpearoGo            — a thin iOS stub that embeds the Watch App
                           (required for App Store distribution)

Run once from the repo root:  python3 generate_xcodeproj.py
Then open SpearoGo.xcodeproj in Xcode.
"""
import os, uuid, textwrap

BASE    = os.path.dirname(os.path.abspath(__file__))
PROJ    = os.path.join(BASE, "SpearoGo.xcodeproj")
WS      = os.path.join(PROJ, "project.xcworkspace")

def uid():
    return uuid.uuid4().hex[:24].upper()

def q(v):
    """Quote a plist value if it contains special chars."""
    if v.startswith('"') and v.endswith('"'):
        return v
    if v.startswith('(') or v.startswith('{'):
        return v
    special = set('$()./- +@,')
    if any(c in special for c in v):
        return f'"{v}"'
    return v

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
    ("OnboardingView.swift",   "Views/OnboardingView.swift",      False),
    ("PrivacyPolicyView.swift","Views/PrivacyPolicyView.swift",   False),
    # Models
    ("WeatherData.swift",      "Models/WeatherData.swift",        False),
    ("MarineData.swift",       "Models/MarineData.swift",         False),
    ("TideData.swift",         "Models/TideData.swift",           False),
    ("SolunarData.swift",      "Models/SolunarData.swift",        False),
    ("DiveScore.swift",        "Models/DiveScore.swift",          False),
    ("SavedLocation.swift",    "Models/SavedLocation.swift",      False),
    ("SharedScore.swift",      "Models/SharedScore.swift",        False),
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
    # Widget (not compiled in main app target — needs a Widget Extension target)
    ("SpearoGoWidget.swift",   "Widget/SpearoGoWidget.swift",     "none"),
    ("WidgetViews.swift",      "Widget/WidgetViews.swift",        "none"),
    # Resources
    ("Assets.xcassets",        "Assets.xcassets",                 True),
    ("PrivacyInfo.xcprivacy",  "PrivacyInfo.xcprivacy",           True),
]

# ─── Generate UUIDs ───────────────────────────────────────────────────────────

# Project
PROJ_UUID      = uid()
MAIN_GRP       = uid()
PRODUCTS_GRP   = uid()
PROJ_CFG_LIST  = uid()
DEBUG_PROJ     = uid()
RELEASE_PROJ   = uid()

# watchOS target
W_TARGET       = uid()
W_SOURCES_PH   = uid()
W_RESOURCES_PH = uid()
W_FRAMEWORKS_PH = uid()
W_CFG_LIST     = uid()
W_DEBUG_CFG    = uid()
W_RELEASE_CFG  = uid()
W_PRODUCT_REF  = uid()

# iOS stub target
I_TARGET       = uid()
I_SOURCES_PH   = uid()
I_RESOURCES_PH = uid()
I_FRAMEWORKS_PH = uid()
I_EMBED_WATCH  = uid()   # Copy Files phase to embed watch app
I_CFG_LIST     = uid()
I_DEBUG_CFG    = uid()
I_RELEASE_CFG  = uid()
I_PRODUCT_REF  = uid()

# iOS stub file refs
I_APP_SWIFT_REF = uid()
I_APP_SWIFT_BF  = uid()
I_ASSETS_REF    = uid()
I_ASSETS_BF     = uid()
I_PLIST_REF     = uid()

# Dependency / container
I_DEP_UUID     = uid()    # PBXTargetDependency
I_DEP_PROXY    = uid()    # PBXContainerItemProxy
W_IN_EMBED_BF  = uid()    # PBXBuildFile for watch app in embed phase

# Per-file UUIDs (watchOS target)
files = []
for name, path, is_res in SOURCES:
    files.append({
        "name":   name,
        "path":   path,
        "is_res": is_res,
        "ref":    uid(),
        "bf":     uid(),
    })

# Groups
SOURCES_GRP  = uid()
VIEWS_GRP    = uid()
MODELS_GRP   = uid()
SERVICES_GRP = uid()
UTILS_GRP    = uid()
WIDGET_GRP   = uid()
IOS_STUB_GRP = uid()

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
    # watchOS target files
    for f in files:
        if f["is_res"] == "none":
            continue
        phase_name = "Resources" if f["is_res"] else "Sources"
        a(f"    {f['bf']} /* {f['name']} in {phase_name} */ = {{isa = PBXBuildFile; fileRef = {f['ref']} /* {f['name']} */; }};")
    # iOS stub source
    a(f"    {I_APP_SWIFT_BF} /* SpearoGoiOSApp.swift in Sources */ = {{isa = PBXBuildFile; fileRef = {I_APP_SWIFT_REF} /* SpearoGoiOSApp.swift */; }};")
    # iOS stub assets
    a(f"    {I_ASSETS_BF} /* Assets.xcassets in Resources */ = {{isa = PBXBuildFile; fileRef = {I_ASSETS_REF} /* Assets.xcassets */; }};")
    # Watch app embedded in iOS app
    a(f"    {W_IN_EMBED_BF} /* SpearoGo Watch App.app in Embed Watch Content */ = {{isa = PBXBuildFile; fileRef = {W_PRODUCT_REF} /* SpearoGo Watch App.app */; settings = {{ATTRIBUTES = (RemoveHeadersOnCopy, ); }}; }};")
    a("/* End PBXBuildFile section */")
    a("")

    # ── PBXContainerItemProxy ─────────────────────────────────────────────────
    a("/* Begin PBXContainerItemProxy section */")
    a(f"    {I_DEP_PROXY} /* PBXContainerItemProxy */ = {{")
    a("        isa = PBXContainerItemProxy;")
    a(f"        containerPortal = {PROJ_UUID} /* Project object */;")
    a("        proxyType = 1;")
    a(f"        remoteGlobalIDString = {W_TARGET};")
    a("        remoteInfo = \"SpearoGo Watch App\";")
    a("    };")
    a("/* End PBXContainerItemProxy section */")
    a("")

    # ── PBXCopyFilesBuildPhase (Embed Watch Content) ──────────────────────────
    a("/* Begin PBXCopyFilesBuildPhase section */")
    a(f"    {I_EMBED_WATCH} /* Embed Watch Content */ = {{")
    a("        isa = PBXCopyFilesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        dstPath = \"$(CONTENTS_FOLDER_PATH)/Watch\";")
    a("        dstSubfolderSpec = 16;")
    a("        files = (")
    a(f"            {W_IN_EMBED_BF} /* SpearoGo Watch App.app in Embed Watch Content */,")
    a("        );")
    a("        name = \"Embed Watch Content\";")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    a("/* End PBXCopyFilesBuildPhase section */")
    a("")

    # ── PBXFileReference ─────────────────────────────────────────────────────
    a("/* Begin PBXFileReference section */")
    # iOS stub product
    a(f"    {I_PRODUCT_REF} /* SpearoGo.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = \"SpearoGo.app\"; sourceTree = BUILT_PRODUCTS_DIR; }};")
    # watchOS product
    a(f"    {W_PRODUCT_REF} /* SpearoGo Watch App.app */ = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = \"SpearoGo Watch App.app\"; sourceTree = BUILT_PRODUCTS_DIR; }};")
    # iOS stub swift file
    a(f"    {I_APP_SWIFT_REF} /* SpearoGoiOSApp.swift */ = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = SpearoGoiOSApp.swift; sourceTree = \"<group>\"; }};")
    # iOS stub assets
    a(f"    {I_ASSETS_REF} /* Assets.xcassets */ = {{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = \"<group>\"; }};")
    # iOS stub Info.plist
    a(f"    {I_PLIST_REF} /* Info.plist */ = {{isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = \"<group>\"; }};")
    # watchOS files
    for f in files:
        if f["is_res"] == "none":
            ftype = "sourcecode.swift"
        elif f["is_res"] and f["name"].endswith(".xcassets"):
            ftype = "folder.assetcatalog"
        elif f["name"].endswith(".xcprivacy"):
            ftype = "text.xml"
        elif f["name"] == "Info.plist":
            ftype = "text.plist.xml"
        else:
            ftype = "sourcecode.swift"
        a(f"    {f['ref']} /* {f['name']} */ = {{isa = PBXFileReference; lastKnownFileType = {ftype}; path = {f['name']}; sourceTree = \"<group>\"; }};")
    a("/* End PBXFileReference section */")
    a("")

    # ── PBXFrameworksBuildPhase ───────────────────────────────────────────────
    a("/* Begin PBXFrameworksBuildPhase section */")
    for ph_uuid, name in [(W_FRAMEWORKS_PH, "Watch App"), (I_FRAMEWORKS_PH, "iOS Stub")]:
        a(f"    {ph_uuid} /* Frameworks */ = {{")
        a("        isa = PBXFrameworksBuildPhase;")
        a("        buildActionMask = 2147483647;")
        a("        files = (")
        a("        );")
        a("        runOnlyForDeploymentPostprocessing = 0;")
        a("    };")
    a("/* End PBXFrameworksBuildPhase section */")
    a("")

    # ── PBXGroup ─────────────────────────────────────────────────────────────
    root_files   = [f for f in files if "/" not in f["path"]]
    views_files  = [f for f in files if f["path"].startswith("Views/")]
    models_files = [f for f in files if f["path"].startswith("Models/")]
    svc_files    = [f for f in files if f["path"].startswith("Services/")]
    util_files   = [f for f in files if f["path"].startswith("Utils/")]
    widget_files = [f for f in files if f["path"].startswith("Widget/")]

    a("/* Begin PBXGroup section */")

    # Root group
    a(f"    {MAIN_GRP} = {{")
    a("        isa = PBXGroup;")
    a("        children = (")
    a(f"            {SOURCES_GRP} /* SpearoGo */,")
    a(f"            {IOS_STUB_GRP} /* iOS Stub */,")
    a(f"            {PRODUCTS_GRP} /* Products */,")
    a("        );")
    a("        sourceTree = \"<group>\";")
    a("    };")

    # SpearoGo source group (watchOS files, path = SpearoGo)
    a(f"    {SOURCES_GRP} /* SpearoGo */ = {{")
    a("        isa = PBXGroup;")
    a("        children = (")
    for f in root_files:
        a(f"            {f['ref']} /* {f['name']} */,")
    a(f"            {VIEWS_GRP} /* Views */,")
    a(f"            {MODELS_GRP} /* Models */,")
    a(f"            {SERVICES_GRP} /* Services */,")
    a(f"            {UTILS_GRP} /* Utils */,")
    a(f"            {WIDGET_GRP} /* Widget */,")
    a("        );")
    a("        path = SpearoGo;")
    a("        sourceTree = \"<group>\";")
    a("    };")

    # iOS Stub group
    a(f"    {IOS_STUB_GRP} /* iOS Stub */ = {{")
    a("        isa = PBXGroup;")
    a("        children = (")
    a(f"            {I_APP_SWIFT_REF} /* SpearoGoiOSApp.swift */,")
    a(f"            {I_ASSETS_REF} /* Assets.xcassets */,")
    a(f"            {I_PLIST_REF} /* Info.plist */,")
    a("        );")
    a("        name = \"iOS Stub\";")
    a("        path = \"SpearoGoiOS\";")
    a("        sourceTree = \"<group>\";")
    a("    };")

    # Products group
    a(f"    {PRODUCTS_GRP} /* Products */ = {{")
    a("        isa = PBXGroup;")
    a("        children = (")
    a(f"            {I_PRODUCT_REF} /* SpearoGo.app */,")
    a(f"            {W_PRODUCT_REF} /* SpearoGo Watch App.app */,")
    a("        );")
    a("        name = Products;")
    a("        sourceTree = \"<group>\";")
    a("    };")

    for grp_uuid, grp_name, grp_files in [
        (VIEWS_GRP,    "Views",    views_files),
        (MODELS_GRP,   "Models",   models_files),
        (SERVICES_GRP, "Services", svc_files),
        (UTILS_GRP,    "Utils",    util_files),
        (WIDGET_GRP,   "Widget",   widget_files),
    ]:
        a(f"    {grp_uuid} /* {grp_name} */ = {{")
        a("        isa = PBXGroup;")
        a("        children = (")
        for f in grp_files:
            a(f"            {f['ref']} /* {f['name']} */,")
        a("        );")
        a(f"        name = {grp_name};")
        a(f"        path = \"{grp_name}\";")
        a("        sourceTree = \"<group>\";")
        a("    };")

    a("/* End PBXGroup section */")
    a("")

    # ── PBXNativeTarget ───────────────────────────────────────────────────────
    a("/* Begin PBXNativeTarget section */")

    # watchOS Watch App target
    a(f"    {W_TARGET} /* SpearoGo Watch App */ = {{")
    a("        isa = PBXNativeTarget;")
    a(f"        buildConfigurationList = {W_CFG_LIST} /* Build configuration list for PBXNativeTarget \"SpearoGo Watch App\" */;")
    a("        buildPhases = (")
    a(f"            {W_SOURCES_PH} /* Sources */,")
    a(f"            {W_FRAMEWORKS_PH} /* Frameworks */,")
    a(f"            {W_RESOURCES_PH} /* Resources */,")
    a("        );")
    a("        buildRules = (")
    a("        );")
    a("        dependencies = (")
    a("        );")
    a("        name = \"SpearoGo Watch App\";")
    a("        productName = \"SpearoGo Watch App\";")
    a(f"        productReference = {W_PRODUCT_REF} /* SpearoGo Watch App.app */;")
    a("        productType = \"com.apple.product-type.application\";")
    a("    };")

    # iOS stub target
    a(f"    {I_TARGET} /* SpearoGo */ = {{")
    a("        isa = PBXNativeTarget;")
    a(f"        buildConfigurationList = {I_CFG_LIST} /* Build configuration list for PBXNativeTarget \"SpearoGo\" */;")
    a("        buildPhases = (")
    a(f"            {I_SOURCES_PH} /* Sources */,")
    a(f"            {I_FRAMEWORKS_PH} /* Frameworks */,")
    a(f"            {I_RESOURCES_PH} /* Resources */,")
    a(f"            {I_EMBED_WATCH} /* Embed Watch Content */,")
    a("        );")
    a("        buildRules = (")
    a("        );")
    a("        dependencies = (")
    a(f"            {I_DEP_UUID} /* PBXTargetDependency */,")
    a("        );")
    a("        name = SpearoGo;")
    a("        productName = SpearoGo;")
    a(f"        productReference = {I_PRODUCT_REF} /* SpearoGo.app */;")
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
    a(f"                {W_TARGET} = {{")
    a("                    CreatedOnToolsVersion = 15.0;")
    a("                };")
    a(f"                {I_TARGET} = {{")
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
    a(f"            {I_TARGET} /* SpearoGo */,")
    a(f"            {W_TARGET} /* SpearoGo Watch App */,")
    a("        );")
    a("    };")
    a("/* End PBXProject section */")
    a("")

    # ── PBXResourcesBuildPhase ────────────────────────────────────────────────
    a("/* Begin PBXResourcesBuildPhase section */")
    # watchOS resources
    a(f"    {W_RESOURCES_PH} /* Resources */ = {{")
    a("        isa = PBXResourcesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    for f in files:
        if f["is_res"] is True:
            a(f"            {f['bf']} /* {f['name']} in Resources */,")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    # iOS stub resources
    a(f"    {I_RESOURCES_PH} /* Resources */ = {{")
    a("        isa = PBXResourcesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    a(f"            {I_ASSETS_BF} /* Assets.xcassets in Resources */,")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    a("/* End PBXResourcesBuildPhase section */")
    a("")

    # ── PBXSourcesBuildPhase ──────────────────────────────────────────────────
    a("/* Begin PBXSourcesBuildPhase section */")
    # watchOS sources
    a(f"    {W_SOURCES_PH} /* Sources */ = {{")
    a("        isa = PBXSourcesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    for f in files:
        if f["is_res"] is False:
            a(f"            {f['bf']} /* {f['name']} in Sources */,")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    # iOS stub sources
    a(f"    {I_SOURCES_PH} /* Sources */ = {{")
    a("        isa = PBXSourcesBuildPhase;")
    a("        buildActionMask = 2147483647;")
    a("        files = (")
    a(f"            {I_APP_SWIFT_BF} /* SpearoGoiOSApp.swift in Sources */,")
    a("        );")
    a("        runOnlyForDeploymentPostprocessing = 0;")
    a("    };")
    a("/* End PBXSourcesBuildPhase section */")
    a("")

    # ── PBXTargetDependency ───────────────────────────────────────────────────
    a("/* Begin PBXTargetDependency section */")
    a(f"    {I_DEP_UUID} /* PBXTargetDependency */ = {{")
    a("        isa = PBXTargetDependency;")
    a(f"        target = {W_TARGET} /* SpearoGo Watch App */;")
    a(f"        targetProxy = {I_DEP_PROXY} /* PBXContainerItemProxy */;")
    a("    };")
    a("/* End PBXTargetDependency section */")
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
        ("SWIFT_VERSION", "5.0"),
    ]

    a("/* Begin XCBuildConfiguration section */")

    # Project Debug
    a(f"    {DEBUG_PROJ} /* Debug */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in base_settings:
        a(f"            {k} = {q(v)};")
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
        a(f"            {k} = {q(v)};")
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

    # ── watchOS target settings ─────────────────────────────────────────────
    watch_settings = [
        ("ASSETCATALOG_COMPILER_APPICON_NAME", "AppIcon"),
        ("ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME", "OceanBlue"),
        ("CODE_SIGN_ENTITLEMENTS", "SpearoGo/SpearoGo.entitlements"),
        ("CODE_SIGN_STYLE", "Automatic"),
        ("CURRENT_PROJECT_VERSION", "1"),
        ("DEVELOPMENT_TEAM", "RBDNV7NG89"),
        ("ENABLE_PREVIEWS", "YES"),
        ("GENERATE_INFOPLIST_FILE", "NO"),
        ("INFOPLIST_FILE", "SpearoGo/Info.plist"),
        ("MARKETING_VERSION", "1.0.0"),
        ("PRODUCT_BUNDLE_IDENTIFIER", "agency.visivo.SpearoGo.watchkitapp"),
        ("PRODUCT_NAME", "$(TARGET_NAME)"),
        ("SDKROOT", "watchos"),
        ("SKIP_INSTALL", "YES"),
        ("SUPPORTED_PLATFORMS", "watchos"),
        ("SUPPORTS_MACCATALYST", "NO"),
        ("SWIFT_EMIT_LOC_STRINGS", "YES"),
        ("SWIFT_VERSION", "5.0"),
        ("TARGETED_DEVICE_FAMILY", "4"),
        ("WATCHOS_DEPLOYMENT_TARGET", "10.0"),
    ]

    # Watch App Debug
    a(f"    {W_DEBUG_CFG} /* Debug */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in watch_settings:
        a(f"            {k} = {q(v)};")
    a("            SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;")
    a("        };")
    a("        name = Debug;")
    a("    };")

    # Watch App Release
    a(f"    {W_RELEASE_CFG} /* Release */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in watch_settings:
        a(f"            {k} = {q(v)};")
    a("        };")
    a("        name = Release;")
    a("    };")

    # ── iOS stub target settings ────────────────────────────────────────────
    ios_settings = [
        ("ASSETCATALOG_COMPILER_APPICON_NAME", "AppIcon"),
        ("CODE_SIGN_STYLE", "Automatic"),
        ("CURRENT_PROJECT_VERSION", "1"),
        ("DEVELOPMENT_TEAM", "RBDNV7NG89"),
        ("GENERATE_INFOPLIST_FILE", "NO"),
        ("INFOPLIST_FILE", "SpearoGoiOS/Info.plist"),
        ("MARKETING_VERSION", "1.0.0"),
        ("PRODUCT_BUNDLE_IDENTIFIER", "agency.visivo.SpearoGo"),
        ("PRODUCT_NAME", "$(TARGET_NAME)"),
        ("SDKROOT", "iphoneos"),
        ("SKIP_INSTALL", "NO"),
        ("SUPPORTED_PLATFORMS", "iphoneos"),
        ("SUPPORTS_MACCATALYST", "NO"),
        ("SWIFT_EMIT_LOC_STRINGS", "YES"),
        ("SWIFT_VERSION", "5.0"),
        ("TARGETED_DEVICE_FAMILY", "1,2"),
        ("IPHONEOS_DEPLOYMENT_TARGET", "17.0"),
    ]

    # iOS Stub Debug
    a(f"    {I_DEBUG_CFG} /* Debug */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in ios_settings:
        a(f"            {k} = {q(v)};")
    a("            SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;")
    a("        };")
    a("        name = Debug;")
    a("    };")

    # iOS Stub Release
    a(f"    {I_RELEASE_CFG} /* Release */ = {{")
    a("        isa = XCBuildConfiguration;")
    a("        buildSettings = {")
    for k, v in ios_settings:
        a(f"            {k} = {q(v)};")
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
    a(f"    {W_CFG_LIST} /* Build configuration list for PBXNativeTarget \"SpearoGo Watch App\" */ = {{")
    a("        isa = XCConfigurationList;")
    a("        buildConfigurations = (")
    a(f"            {W_DEBUG_CFG} /* Debug */,")
    a(f"            {W_RELEASE_CFG} /* Release */,")
    a("        );")
    a("        defaultConfigurationIsVisible = 0;")
    a("        defaultConfigurationName = Release;")
    a("    };")
    a(f"    {I_CFG_LIST} /* Build configuration list for PBXNativeTarget \"SpearoGo\" */ = {{")
    a("        isa = XCConfigurationList;")
    a("        buildConfigurations = (")
    a(f"            {I_DEBUG_CFG} /* Debug */,")
    a(f"            {I_RELEASE_CFG} /* Release */,")
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

# ─── Create iOS stub source file ─────────────────────────────────────────────
IOS_STUB_DIR = os.path.join(BASE, "SpearoGoiOS")
os.makedirs(IOS_STUB_DIR, exist_ok=True)

ios_app_swift = os.path.join(IOS_STUB_DIR, "SpearoGoiOSApp.swift")
if not os.path.exists(ios_app_swift):
    with open(ios_app_swift, "w") as f:
        f.write(textwrap.dedent("""\
        import SwiftUI

        @main
        struct SpearoGoiOSApp: App {
            var body: some Scene {
                WindowGroup {
                    Text("Spearo Go is a watch-only app.\\nPlease open it on your Apple Watch.")
                        .multilineTextAlignment(.center)
                        .padding()
                }
            }
        }
        """))
    print(f"✓ {ios_app_swift}")

# ─── Write project files ─────────────────────────────────────────────────────
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
print("Select scheme SpearoGo → Any iOS Device → Product → Archive")
