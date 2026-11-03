# Mukhar release shrinking (R8 full mode, M6 part B, D-036).
#
# Most libraries ship their own consumer rules, merged automatically:
#   kotlinx.serialization (generated serializers, companion `serializer()`), Room 2.8 (generated *_Impl),
#   Hilt/Dagger (generated components), Retrofit 3 + OkHttp 5 (service interfaces, suspend methods),
#   Play Billing 9 (com.android.vending.billing AIDL), WorkManager, profileinstaller.
# The rules below cover what is ours and reflective, and keep the generic signatures those libraries read.
# Check after any change: `./gradlew assembleRelease`, then `build/outputs/mapping/release/` (seeds.txt keeps the
# names below; usage.txt lists what was removed).

# Retrofit reads generic return types and parameter annotations at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface io.github.ukpratik.mukhar.data.net.MukharApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Our @Serializable wire and content models (net/ApiModels, prompts.json, words_*.json, pairs.json, routes):
# keep each class's serializer lookup so a renamed or stripped companion never breaks JSON at runtime.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.ukpratik.mukhar.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class io.github.ukpratik.mukhar.**
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities are read through generated code only; enum columns go through TypeConverters (name-based), so
# keep enum constant names in our model packages.
-keepclassmembers enum io.github.ukpratik.mukhar.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Crash reports stay readable with the archived mapping file (NFR-005).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
