package com.xteink.companion.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class LegalDocument(val title: String, val body: String) {
    Privacy(
        title = "Privacy policy",
        body = """
            Effective 20 August 2026

            Xtraordinary is an independent companion app for XTEINK displays, maintained by ClGratton. The app works locally without an account.

            Local data. Imported EPUB metadata, covers, passes, device pairing information, app settings, and reading history are stored on your Android device. Reading sessions are first stored on the X3 and are deleted from its transfer queue only after Android has durably saved them and acknowledged receipt.

            Optional Google backup. If you choose Back up with Google, Xtraordinary requests the narrow Google Drive app-data permission plus basic profile information. It stores only reading sessions (book title, timestamps, page numbers, time per page, word counts, and derived reading pace) and the minimum-page-time preference in a private app-data file in your Google Drive. It does not upload EPUB files, covers, boarding passes, notification contents, Bluetooth identifiers, or Gemini prompts. Google processes this data under its own terms and privacy policy.

            Passes and flights. Pass photos are processed on the device with Google ML Kit. Imported pass files, decoded barcodes, passenger details, and cached flight fields remain local. A Wallet share link or pass-file import may contact the issuer named by that link. Live flight updates contact the configured Xtraordinary flight-status proxy only after you choose Live; a build without a configured proxy does not make that request. Always treat the issuer and airline as authoritative.

            Other network use. Missing book metadata may be requested from Open Library. Firmware and community-release checks contact GitHub or the explicitly selected firmware publisher. The pinned OEM recovery image is downloaded from XTEINK's firmware host only when you choose it. Community builds contain no advertising, billing, or consent SDK. Play builds include Google Play Billing, Google's User Messaging Platform, and Google Mobile Ads. The consent flow runs before an ad request. Xtraordinary does not send reading history, books, passes, Bluetooth identifiers, firmware state, Focus sessions, or device telemetry to Google for advertising, and it sends no app analytics or remote diagnostics.

            Retention and deletion. Local reading history remains until app data is cleared or a future in-app delete action removes it. Google backup remains until you use Delete cloud backup in Settings, remove the app's Drive access in your Google Account, or delete it through Google Drive account controls. Disconnecting without deletion does not delete the cloud copy.

            Security. Network traffic uses HTTPS. OAuth access tokens are short-lived and are not written to app storage. Bluetooth bonding keys remain in Android and X3 system storage. Xtraordinary does not sell reading data.

            Your choices. Google backup is optional and can be refused without losing local features. You can sync, delete the cloud backup, disconnect Google, or continue locally from Settings.

            Questions or privacy requests: use the public support channel at https://github.com/ClGratton/Xtraordinary/issues. Before a public store release, the developer must publish this policy on a verified domain and add the final legal identity and direct privacy contact required for the release jurisdictions.
        """.trimIndent(),
    ),
    Terms(
        title = "Terms of use",
        body = """
            Effective 20 August 2026

            Xtraordinary is independent, experimental companion software for XTEINK hardware. It is not affiliated with or endorsed by XTEINK, Google, airlines, or pass issuers.

            You may use the app and companion firmware only with devices, accounts, books, passes, and data you are authorized to use. Do not use it to bypass DRM, access another person's account, or present an altered or unauthorized travel credential.

            E-ink passes and flight details are conveniences, not authoritative travel documents. Always follow the airline, airport, issuer, and original pass. Confirm gates, times, identity requirements, and barcode acceptance through official sources.

            Installing firmware can make a device temporarily unavailable and may require a documented recovery flash. Keep the device powered and connected during installation. Back up important files first.

            The software is provided as available and may contain defects. Nothing in these terms excludes rights or remedies that cannot legally be excluded, including mandatory consumer protections. You remain responsible for safe device use and for keeping independent copies of important content.

            Google backup is optional. By enabling it, you authorize Xtraordinary to create and update the private app-data file described in the Privacy policy. You can delete that file and revoke access from Settings.

            The Play build may offer a one-time ad-free product through Google Play. The price and purchase terms shown by Google Play control the transaction. Restore uses the current Play account. Purchase acknowledgement is not a substitute for server verification; a public release must verify entitlement before granting durable ad-free access and must honor refunds and revocations. Billing, cancellation, refund, and statutory consumer rights are also governed by Google Play and applicable law.

            These terms may change when functionality or data use changes. Material changes will require a new acknowledgement before affected optional processing resumes.

            Support: https://github.com/ClGratton/Xtraordinary/issues. Public distribution still requires final developer identity, contact details, governing terms where appropriate, and review for the release jurisdictions.
        """.trimIndent(),
    ),
    Notices(
        title = "Open-source notices",
        body = """
            Xtraordinary app and companion additions are distributed under the MIT License. The reader firmware is a modified CrossPoint Reader work and includes the Open X4 SDK, both under MIT terms. The optional firmware picker can also install separately published CrossPoint and CrossInk binaries; both are MIT-licensed community projects, and their own notices, source links, selected release tags, and binary digests remain required. Source and the complete release audit are available at https://github.com/ClGratton/Xtraordinary.

            Android components include AndroidX and Kotlin (Apache-2.0), ZXing (Apache-2.0), Google ML Kit and Google Play services under Google's SDK terms, Google Play Billing 9.1.0, Google Mobile Ads 25.4.0, Google User Messaging Platform 4.0.0, and JUnit for tests (EPL-1.0). Billing, ads, and consent dependencies exist only in the Play distribution; the community distribution remains SDK-free and ad-free.

            Firmware components include ArduinoJson and QRCode (MIT), PNGdec and JPEGDEC (Apache-2.0), SdFat (MIT), NimBLE-Arduino and Apache NimBLE (Apache-2.0 with NOTICE), ArduinoWebSockets (LGPL-2.1), Expat (MIT, with a CC0 SipHash file), and uzlib (zlib-style terms). Redistributed Noto and OpenDyslexic fonts use SIL OFL-1.1; Ubuntu fonts use the Ubuntu Font Licence 1.0.

            XTEINK is a trademark of its owner. Xtraordinary is an independent compatibility project and is not affiliated with or endorsed by XTEINK, Google, airlines, or pass issuers. No third-party license or trademark grants endorsement.

            This screen is a readable summary, not a replacement for required license texts, NOTICE files, source offers, or relinkable-material obligations. The repository's THIRD_PARTY_NOTICES.md and docs/legal-release-audit.md identify the exact release blockers that must be closed before public distribution.
        """.trimIndent(),
    ),
}

@Composable
fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.title) },
        text = {
            Text(
                text = document.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
