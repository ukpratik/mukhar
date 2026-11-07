package io.github.ukpratik.mukhar.app.feature.plan

/**
 * D-039: Premium and billing stay in the code but are unreachable in R1. No Plan rows, trial banner or paywall
 * entry points, and no daily limits in on-device or own-key mode. Mukhar Cloud shows as "coming soon" instead.
 * Flip with `AnalysisMode.CLOUD_ENABLED` when the server is funded again.
 */
object PremiumGate {
    const val PAYWALL_ENABLED = false
}
