# Firestore maps documents by hand (no reflection on our classes), so only library rules are needed.
-keep class io.livekit.android.** { *; }
-keep class livekit.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# Credential Manager / Google ID
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
