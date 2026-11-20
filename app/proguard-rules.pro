# Sobdosuddhi 1.0 ProGuard / R8 Rules
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod

# Keep Moshi generated adapters & data models
-keep class com.sobdosuddhi.app.model.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { public <init>(com.squareup.moshi.Moshi); }

# Keep Room entities & generated DAOs
-keep class com.sobdosuddhi.app.data.local.** { *; }
