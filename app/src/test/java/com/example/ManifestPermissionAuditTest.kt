package com.example

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/**
 * Play Protect risk-profile regression gate (v1.3.3).
 *
 * Parses the source AndroidManifest.xml + accessibility service config and
 * fails if a high-risk permission / capability that was deliberately removed
 * (or never allowed) reappears. The CI workflow also runs the equivalent
 * check against the MERGED manifest (tools/manifest_risk_check.py) so that
 * permissions merged in from library dependencies are caught too.
 *
 * Before/after (v1.3.3):
 *   REMOVED  KILL_BACKGROUND_PROCESSES     — never used by any code (grep:
 *                                      zero references in Kotlin/resources)
 *   REMOVED  REQUEST_INSTALL_PACKAGES      — updater hands the APK to the system
 *                                      installer via ACTION_VIEW + FileProvider;
 *                                      no PackageInstaller session API is used
 *   KEPT     QUERY_ALL_PACKAGES            — owner constraint 2026-10-18: the
 *                                      blocked-apps picker is a feature and may
 *                                      not be narrowed without proof-tested
 *                                      parity, so it stays as-declared
 *   ADDED    canPerformGestures (a11y)     — guard genuinely performs
 *                                      GLOBAL_ACTION_BACK / HOME
 */
class ManifestPermissionAuditTest {

  private val androidNs = "http://schemas.android.com/apk/res/android"

  /** Permissions that must NEVER be in Focivo's manifest. */
  private val bannedPermissions = setOf(
    // Removed in v1.3.3 — genuinely unused, with grep evidence (Play Protect red flags)
    "android.permission.KILL_BACKGROUND_PROCESSES",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    // Never existed — Focivo has no calling/SMS/contacts/phone features
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.CALL_PHONE",
    "android.permission.SEND_SMS",
    "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS",
    "android.permission.SEND_RESPOND_VIA_MESSAGE",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.GET_ACCOUNTS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.RECORD_AUDIO",
    "android.permission.CAMERA",
    "android.permission.READ_CALL_LOG",
    "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.ADD_VOICEMAIL",
    "android.permission.PROCESS_OUTGOING_CALLS",
    "android.permission.ANSWER_PHONE_CALLS",
    "android.permission.BODY_SENSORS",
    "android.permission.ACTIVITY_RECOGNITION"
  )

  private fun repoFile(relative: String): File {
    // Robust against Gradle's unit-test working directory: walk upward from
    // the current working directory until the module layout is found.
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val candidate = File(dir, relative)
      if (candidate.isFile) return candidate
      dir = dir.parentFile
    }
    fail("File not found: $relative (searched upward from ${System.getProperty("user.dir")})")
    error("unreachable")
  }

  private fun manifestFile(): File = repoFile("src/main/AndroidManifest.xml")

  private fun manifestDoc(): Element {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    return factory.newDocumentBuilder().parse(manifestFile()).documentElement
  }

  private fun declaredPermissions(doc: Element): Set<String> {
    val nodes = doc.getElementsByTagName("uses-permission")
    return (0 until nodes.length).map { i ->
      (nodes.item(i) as Element).getAttributeNS(androidNs, "name")
    }.toSet()
  }

  @Test
  fun noHighRiskPermissionsDeclared() {
    val declared = declaredPermissions(manifestDoc())
    val violations = declared.intersect(bannedPermissions)
    assertTrue(
      "HIGH-RISK PERMISSIONS REAPPEARED in AndroidManifest.xml: $violations. " +
        "See ManifestPermissionAuditTest KDoc before adding any of these — each " +
        "one increases the chance of a Google Play Protect block on sideloaded installs.",
      violations.isEmpty()
    )
  }

  @Test
  fun blockedAppsPickerHasPackageVisibility() {
    // Owner constraint 2026-10-18: the blocked-apps picker is a feature and
    // QUERY_ALL_PACKAGES stays as-declared (no narrowing without proof-tested
    // parity). This gate accepts either the declared permission or, if it is
    // ever removed in future, a <queries> block that provably covers the
    // picker's launcher + home lookups.
    val doc = manifestDoc()
    val declared = declaredPermissions(doc)
    if ("android.permission.QUERY_ALL_PACKAGES" in declared) return

    val queries = doc.getElementsByTagName("queries")
    assertTrue("manifest must declare a <queries> block", queries.length > 0)
    val intents = (queries.item(0) as Element).getElementsByTagName("intent")

    fun hasIntent(action: String, category: String): Boolean {
      for (i in 0 until intents.length) {
        val intent = intents.item(i) as Element
        val actions = (0 until intent.getElementsByTagName("action").length).map { j ->
          (intent.getElementsByTagName("action").item(j) as Element)
            .getAttributeNS(androidNs, "name")
        }
        val categories = (0 until intent.getElementsByTagName("category").length).map { j ->
          (intent.getElementsByTagName("category").item(j) as Element)
            .getAttributeNS(androidNs, "name")
        }
        if (action in actions && category in categories) return true
      }
      return false
    }

    assertTrue(
      "without QUERY_ALL_PACKAGES, queries must keep ACTION_MAIN/CATEGORY_LAUNCHER " +
        "(blocked-apps picker listing)",
      hasIntent("android.intent.action.MAIN", "android.intent.category.LAUNCHER")
    )
    assertTrue(
      "without QUERY_ALL_PACKAGES, queries must keep ACTION_MAIN/CATEGORY_HOME " +
        "(EssentialAppsGuard home-launcher check)",
      hasIntent("android.intent.action.MAIN", "android.intent.category.HOME")
    )
  }

  @Test
  fun accessibilityConfigKeepsOnlyNeededCapabilities() {
    val config = repoFile("src/main/res/xml/ai_study_accessibility_config.xml")
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    val root = factory.newDocumentBuilder().parse(config).documentElement

    // The guard performs GLOBAL_ACTION_BACK/HOME (AiStudyAccessibilityService),
    // which requires canPerformGestures — both capabilities below are genuine.
    assertEquals(
      "canRetrieveWindowContent must stay true (guard reads window content)",
      "true",
      root.getAttributeNS(androidNs, "canRetrieveWindowContent")
    )
    assertEquals(
      "canPerformGestures must stay true (guard performs GLOBAL_ACTION_BACK/HOME)",
      "true",
      root.getAttributeNS(androidNs, "canPerformGestures")
    )

    // Nothing beyond the two genuine capabilities may be enabled.
    val otherCaps = listOf(
      "canRequestTouchExplorationMode",
      "canRequestEnhancedWebAccessibility",
      "canRequestFilterKeyEvents",
      "canRequestFingerprintGestures",
      "canTakeScreenshot",
      "canControlMagnification",
      "canRequestShortcuts"
    )
    for (cap in otherCaps) {
      assertNotEquals(
        "accessibility capability $cap is not needed by the guard — keep the " +
          "risk profile minimal",
        "true",
        root.getAttributeNS(androidNs, cap)
      )
    }
  }

  @Test
  fun releaseManifestHasNoDebuggableOrSuspiciousMarkers() {
    val doc = manifestDoc()
    val apps = doc.getElementsByTagName("application")
    assertTrue("application element missing", apps.length > 0)
    val app = apps.item(0) as Element
    assertNotEquals(
      "android:debuggable must never be true in the source manifest",
      "true",
      app.getAttributeNS(androidNs, "debuggable")
    )
    val label = app.getAttributeNS(androidNs, "label")
    assertTrue("app label must be set (was: '$label')", label.isNotBlank())
  }
}
