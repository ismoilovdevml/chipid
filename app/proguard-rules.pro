# Bouncy Castle registers algorithms by class name and loads them reflectively.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# JMRTD / SCUBA / CVC: LDS parsing and protocols resolved by name and via ASN.1 factories.
-keep class org.jmrtd.** { *; }
-keep class net.sf.scuba.** { *; }
-keep class org.ejbca.cvc.** { *; }
-dontwarn org.jmrtd.**
-dontwarn net.sf.scuba.**
-dontwarn org.ejbca.cvc.**

# JDK-only APIs referenced by these libraries but never reached on Android.
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.smartcardio.**
-dontwarn org.slf4j.**

# ML Kit discovers its components reflectively (ComponentRegistrar + MlKitContext); R8 otherwise
# strips them and TextRecognition.getClient() throws a NullPointerException in release builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
