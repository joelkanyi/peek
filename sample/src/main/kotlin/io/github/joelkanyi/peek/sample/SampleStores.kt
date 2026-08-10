/*
 * Copyright 2026 Joel Kanyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.joelkanyi.peek.sample

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.joelkanyi.peek.sample.proto.UserProfile
import java.io.InputStream
import java.io.OutputStream

// All values below are fabricated for the demo. No real users, tokens, or company data.

private val Context.userSettingsStore by preferencesDataStore(name = "user_settings")

private val Context.userProfileStore: DataStore<UserProfile> by dataStore(
    fileName = "user_profile.pb",
    serializer = UserProfileSerializer,
)

private object UserProfileSerializer : Serializer<UserProfile> {
    override val defaultValue: UserProfile = UserProfile.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserProfile =
        try {
            UserProfile.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read user_profile.pb", e)
        }

    override suspend fun writeTo(t: UserProfile, output: OutputStream) = t.writeTo(output)
}

/** Two SharedPreferences files, like a real app: a session file and a settings file. */
internal fun Context.seedSharedPreferences(launchCount: Int, now: Long) {
    getSharedPreferences("user_session", Context.MODE_PRIVATE).edit()
        .putString("user_id", "usr_a1b2c3d4e5")
        .putString("full_name", "Amara Okoro")
        .putString("email", "amara.okoro@example.com")
        .putString("phone", "+254700000000")
        .putString("access_token", "eyJhbGciOiJIUzI1NiJ9.demo-access.token")
        .putString("refresh_token", "rt_demo_9f8e7d6c5b4a")
        .putString("session_id", "sess_5f4e3d2c1b0a")
        .putLong("token_expiry_ms", 1_893_456_000_000L)
        .putLong("last_login_ms", now)
        .putInt("login_count", 42)
        .putBoolean("is_logged_in", true)
        .putBoolean("biometric_unlock", true)
        .apply()

    getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
        .putString("theme", "system")
        .putString("language", "en")
        .putString("country", "KE")
        .putString("currency", "KES")
        .putString("push_token", "fcm_demo_APA91bH8kQ2m3nDemoTokenValue")
        .putString("experiment_group", "checkout_v2")
        .putBoolean("dark_mode", false)
        .putBoolean("notifications_enabled", true)
        .putBoolean("analytics_opt_in", true)
        .putBoolean("onboarding_complete", true)
        .putBoolean("rating_prompt_shown", false)
        .putFloat("font_scale", 1.0f)
        .putFloat("cart_total", 2499.50f)
        .putInt("cart_item_count", 3)
        .putInt("unread_notifications", 5)
        .putInt("app_launch_count", launchCount)
        .putStringSet("feature_flags", setOf("new_home", "wallet_v2", "referrals"))
        .putStringSet("wishlist_ids", setOf("prd_101", "prd_204", "prd_377"))
        .apply()
}

/** A Multiplatform Settings store, backed by its own SharedPreferences file. */
internal fun Context.seedMultiplatformSettings(now: Long) {
    val settings = SharedPreferencesSettings(getSharedPreferences("kaya_settings", Context.MODE_PRIVATE))
    settings.putString("home_layout", "grid")
    settings.putString("currency_symbol", "KSh")
    settings.putInt("items_per_row", 2)
    settings.putInt("max_downloads", 20)
    settings.putLong("last_sync_ms", now)
    settings.putFloat("map_zoom", 14.5f)
    settings.putDouble("default_tip_percent", 10.0)
    settings.putBoolean("autoplay", true)
    settings.putBoolean("data_saver", false)
    settings.putBoolean("reduce_motion", false)
}

/** Preferences DataStore: app configuration across every supported type. */
internal suspend fun Context.seedPreferencesDataStore() {
    userSettingsStore.edit { prefs ->
        prefs[stringPreferencesKey("feed_layout")] = "list"
        prefs[stringPreferencesKey("download_quality")] = "hd"
        prefs[stringPreferencesKey("last_read_id")] = "art_9021"
        prefs[intPreferencesKey("page_size")] = 20
        prefs[intPreferencesKey("max_cache_mb")] = 512
        prefs[intPreferencesKey("sync_interval_min")] = 30
        prefs[intPreferencesKey("session_count")] = 128
        prefs[doublePreferencesKey("font_scale")] = 1.15
        prefs[floatPreferencesKey("daily_budget")] = 5000.0f
        prefs[booleanPreferencesKey("autoplay_videos")] = false
        prefs[booleanPreferencesKey("reduce_motion")] = true
        prefs[booleanPreferencesKey("wallet_pin_set")] = true
        prefs[stringSetPreferencesKey("favorite_categories")] = setOf("electronics", "fashion", "home")
    }
}

/** Proto DataStore: a nested user profile with repeated fields, a map, and an enum. */
internal suspend fun Context.seedUserProfile(now: Long) {
    userProfileStore.updateData {
        UserProfile.newBuilder()
            .setUserId("usr_a1b2c3d4e5")
            .setFullName("Amara Okoro")
            .setEmail("amara.okoro@example.com")
            .setPhone("+254700000000")
            .setTier(UserProfile.Tier.GOLD)
            .setLoyaltyPoints(3450)
            .setMemberSinceEpochMs(1_704_067_200_000L)
            .setMarketingOptIn(true)
            .setWalletBalance(1284.75)
            .setPreferredCurrency("KES")
            .addAllRecentSearches(listOf("wireless earbuds", "running shoes", "coffee maker"))
            .setDefaultAddress(
                UserProfile.Address.newBuilder()
                    .setLabel("Home")
                    .setStreet("12 Riverside Drive")
                    .setCity("Nairobi")
                    .setCountryCode("KE")
                    .setPostalCode("00100")
                    .setLatitude(-1.2921)
                    .setLongitude(36.8219)
                    .build(),
            )
            .putAllFeatureFlags(mapOf("new_home" to true, "wallet_v2" to true, "referrals" to false))
            .setNotifications(
                UserProfile.NotificationSettings.newBuilder()
                    .setPushEnabled(true)
                    .setEmailEnabled(true)
                    .setSmsEnabled(false)
                    .addAllMutedTopics(listOf("promotions", "surveys"))
                    .build(),
            )
            .build()
    }
}
