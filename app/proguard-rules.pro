# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information so Play Console can retrace release crashes.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# PDFBox Android supports optional JPEG 2000 decoding, but Kairo only extracts selectable text.
-dontwarn com.gemalto.jp2.JP2Decoder

# Kairo rejects encrypted PDFs, so PDFBox's optional public-key decryption integration is unreachable.
-dontwarn org.bouncycastle.asn1.x500.X500Name
-dontwarn org.bouncycastle.cert.X509CertificateHolder
-dontwarn org.bouncycastle.cms.CMSEnvelopedData
-dontwarn org.bouncycastle.cms.CMSException
-dontwarn org.bouncycastle.cms.KeyTransRecipientId
-dontwarn org.bouncycastle.cms.Recipient
-dontwarn org.bouncycastle.cms.RecipientId
-dontwarn org.bouncycastle.cms.RecipientInformation
-dontwarn org.bouncycastle.cms.RecipientInformationStore
-dontwarn org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient
-dontwarn org.bouncycastle.util.Arrays
