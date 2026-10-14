import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val localProps = Properties().apply { val f = rootProject.file("local.properties"); if (f.exists()) f.inputStream().use { load(it) } }
fun quoted(value:String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android { buildFeatures { buildConfig = true }
 namespace = "com.ittefaq.clothhouse"; compileSdk = 35
 defaultConfig { applicationId = "com.ittefaq.clothhouse"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.1"; buildConfigField("String","SUPABASE_URL",quoted(localProps.getProperty("supabase.url", ""))); buildConfigField("String","SUPABASE_ANON_KEY",quoted(localProps.getProperty("supabase.anonKey", ""))) } }
