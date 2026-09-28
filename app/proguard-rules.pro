# Room Database ve Tabloları Koru
-keep class androidx.room.** { *; }
-keep class com.mai.wol.data.** { *; }
-keepnames class com.mai.wol.data.** { *; }

# Shizuku ve ADB Sınıflarını Koru
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }

# ViewModel ve Coroutine Koruması
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Ağ, WoL ve SSH Kapatma Motorunu Koru
-keep class com.mai.wol.network.** { *; }
-keep class !com.jcraft.jsch.bc.**, com.jcraft.jsch.** { *; }
-keep class com.jcraft.jsch.bc.SignatureEdDSA { *; }
-keep class com.jcraft.jsch.bc.SignatureEd25519 { *; }
-keep class com.jcraft.jsch.bc.SignatureEd448 { *; }
-keep class com.jcraft.jsch.bc.KeyPairGenEdDSA { *; }
-keep class com.jcraft.jsch.bc.Argon2 { *; }
-keep class com.jcraft.jsch.bc.XDH { *; }
-keep class com.jcraft.jsch.bc.ChaCha20Poly1305 { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.ietf.jgss.**
-dontwarn org.bouncycastle.**