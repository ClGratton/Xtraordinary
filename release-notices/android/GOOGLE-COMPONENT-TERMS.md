# Google component terms register

Checked 20 August 2026. This register records the controlling official pages for proprietary Google Android components. It does not copy, freeze, or replace those online agreements. A release manager must review the current pages using the publishing account before every public Play release.

| Component family | Runtime examples | Controlling official material |
| --- | --- | --- |
| Android SDK and Google APIs add-ons | POM rows named `Android Software Development Kit License` | [Android SDK License Agreement](https://developer.android.com/studio/terms) and [Google APIs Terms of Service](https://developers.google.com/terms) |
| Google Play services | `com.google.android.gms:*` | [Google Mobile Developer Services Terms](https://developers.google.com/mobile/terms), Google APIs Terms, and the component documentation |
| ML Kit and ODML | `com.google.mlkit:*`, Play-services ML Kit bridges, `com.google.android.odml:image` | [ML Kit Terms and Privacy](https://developers.google.com/ml-kit/terms), Google APIs Terms, and the Android SDK terms |
| Google Play Billing | `com.android.billingclient:billing*` | [Google Play Developer Distribution Agreement](https://play.google.com/intl/ALL_us/about/developer-distribution-agreement.html), [Play Billing integration requirements](https://developer.android.com/google/play/billing/integrate), and applicable Play policies |
| Google Mobile Ads and UMP | `com.google.android.gms:play-services-ads*`, `com.google.android.ump:user-messaging-platform` | [Google Mobile Ads SDK terms pointer](https://developers.google.com/admob/android/sdk), [Google Mobile Developer Services Terms](https://developers.google.com/mobile/terms), and [AdMob policies](https://support.google.com/admob/answer/6128543) |

Open-source transitive components remain governed by their own licenses. The adjacent resolved reports and override register identify those components separately; recording a Google terms URL is not a trademark grant, product approval, or proof that production console configuration is complete.
