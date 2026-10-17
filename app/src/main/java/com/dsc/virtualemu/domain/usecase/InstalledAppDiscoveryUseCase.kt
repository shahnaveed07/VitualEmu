package com.dsc.virtualemu.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?,
    val isGame: Boolean,
    val isAlreadyAdded: Boolean = false
)

class InstalledAppDiscoveryUseCase(private val context: Context) {

    suspend fun discoverInstalledApps(existingPackages: Set<String> = emptySet()): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = try {
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            } catch (_: Exception) {
                pm.queryIntentActivities(mainIntent, 0)
            }

            val ownPackageName = context.packageName

            val apps = mutableListOf<InstalledApp>()
            val seenPackages = mutableSetOf<String>()

            for (resolveInfo in resolveInfos) {
                val pkgName = resolveInfo.activityInfo?.packageName ?: continue
                if (pkgName == ownPackageName || seenPackages.contains(pkgName)) {
                    continue
                }
                seenPackages.add(pkgName)

                val label = try {
                    resolveInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
                        ?: pkgName
                } catch (_: Exception) {
                    pkgName
                }

                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }

                val appInfo = resolveInfo.activityInfo?.applicationInfo
                val isGame = if (appInfo != null) {
                    @Suppress("DEPRECATION")
                    val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                    val isGameCat = appInfo.category == ApplicationInfo.CATEGORY_GAME
                    isGameFlag || isGameCat
                } else {
                    false
                }

                apps.add(
                    InstalledApp(
                        packageName = pkgName,
                        displayName = label,
                        icon = icon,
                        isGame = isGame,
                        isAlreadyAdded = existingPackages.contains(pkgName)
                    )
                )
            }

            // Sort games first, then alphabetically by displayName
            apps.sortedWith(
                compareByDescending<InstalledApp> { it.isGame }
                    .thenBy { it.displayName.lowercase() }
            )
        }

    fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
