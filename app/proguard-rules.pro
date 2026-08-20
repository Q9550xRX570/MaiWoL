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

# Ağ ve WoL Paket Motorunu Koru
-keep class com.mai.wol.network.** { *; }