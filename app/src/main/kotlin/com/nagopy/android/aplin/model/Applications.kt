/*
 * Copyright 2015 75py
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nagopy.android.aplin.model

import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import com.nagopy.android.aplin.entity.App
import com.nagopy.android.aplin.model.converter.AppConverter
import com.nagopy.android.aplin.model.converter.AppParameters
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class Applications @Inject constructor() {

    @Inject
    lateinit var appConverter: AppConverter

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var shellCmd: ShellCmd

    val appCache: ConcurrentHashMap<String, App> = ConcurrentHashMap()

    val appObserver: PublishSubject<Int> = PublishSubject.create<Int>() // onNext(null)が不可になったので、ダミー引数Intを使う

    open fun isLoaded(): Boolean = appCache.isNotEmpty()

    open fun initAppCache(): Completable {
        return Completable.create {
            Timber.d("initAppCache appCache.size=%s", appCache.size)
            loadAppCache()
            Timber.d("initAppCache appCache.size=%s refreshed", appCache.size)
            it.onComplete()
        }
    }

    open fun loadAppCache() {
        synchronized(appCache, {
            if (!isLoaded()) {
                appCache.clear()
                val all = getInstalledPackageNames()
                val executorService = Executors.newCachedThreadPool()
                appConverter.prepare()
                all.forEach { packageName ->
                    Timber.v("LOAD start pkg=%s", packageName)
                    executorService.execute {
                        val entity = App()
                        appConverter.setValues(entity, packageName)
                        appCache.put(packageName, entity)
                        Timber.v("LOAD fin  pkg=%s", packageName)
                    }
                }
                executorService.shutdown()
                executorService.awaitTermination(60, TimeUnit.SECONDS)
            }
        })
    }

    open fun getApplicationList(category: Category): Single<List<App>> = Single.create {
        loadAppCache()
        it.onSuccess(userSettings.sort.orderBy(category.where(appCache.values)).toList())
    }

    fun getInstalledPackageNames(): List<String> {
        if (Build.VERSION_CODES.N <= Build.VERSION.SDK_INT) {
            // 24-
            return getInstalledPackageNames24()
        } else {
            return getInstalledPackageNames23()
        }
    }

    fun getInstalledPackageNames23(): List<String> {
        return shellCmd.exec(listOf("pm", "list", "packages"), { seq ->
            seq.filter(String::isNotBlank)
                    .filter { it.startsWith("package:") }
                    .map { it.replace("package:", "") }
                    .toList()
        }, emptyList())
    }

    fun getInstalledPackageNames24(): List<String> {
        return shellCmd.exec(listOf("cmd", "package", "list", "packages"), { seq ->
            seq.filter(String::isNotBlank)
                    .filter { it.startsWith("package:") }
                    .map { it.replace("package:", "") }
                    .toList()
        }, emptyList())
    }

    open fun insert(pkg: String): Observable<Void> {
        Timber.d("insert %s", pkg)
        return upsert(pkg)
    }

    open fun update(pkg: String): Observable<Void> {
        Timber.d("update %s", pkg)
        return upsert(pkg)
    }

    private fun upsert(pkg: String): Observable<Void> {
        return Observable.create {
            val entity = App()
            appConverter.prepare()
            appConverter.setValues(entity, pkg)
            appCache.put(pkg, entity)
            it.onComplete()

            appObserver.onNext(0)
        }
    }

    open fun delete(pkg: String): Observable<Void> {
        Timber.d("delete %s", pkg)
        return Observable.create {
            appCache.remove(pkg)
            it.onComplete()

            appObserver.onNext(0)
        }
    }

    open fun updatePoco(): Observable<Void> {
        return Observable.create {
            val all = getInstalledPackageNames()
            var updated = false
            appConverter.prepare()
            synchronized(appCache) {
                all.forEach { packageName ->
                    val app = appCache[packageName]
                    if (app != null) {
                        val newApp = App()
                        appConverter.setValues(newApp, packageName, AppParameters.isDefaultApp, AppParameters.isEnabled)

                        if (newApp.isDefaultApp != app.isDefaultApp || newApp.isEnabled != app.isEnabled) {
                            app.isDefaultApp = newApp.isDefaultApp
                            app.isEnabled = app.isEnabled
                            appCache.put(packageName, app)
                            updated = true
                        }
                    }
                }
            }
            it.onComplete()

            if (updated) {
                Timber.d("Updated!")
                appObserver.onNext(0)
            }
        }
    }

    companion object {
        /**
         * [android.content.pm.PackageManager.getInstalledApplications]の引数に使うフラグ。以下のクラスを参照
         * /packages/apps/Settings/src/com/android/settings/applications/ApplicationsState.java
         */
        val flags: Int by lazy {
            @Suppress("DEPRECATION")
            val ownerRetrieveFlags = PackageManager.GET_UNINSTALLED_PACKAGES or
                    PackageManager.GET_DISABLED_COMPONENTS or
                    PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS

            @Suppress("DEPRECATION")
            val retrieveFlags = PackageManager.GET_DISABLED_COMPONENTS or
                    PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val myUserIdMethod = UserHandle::class.java.getDeclaredMethod("myUserId")
                if (myUserIdMethod.invoke(null) == 0) {
                    return@lazy ownerRetrieveFlags
                } else {
                    return@lazy retrieveFlags
                }
            } else {
                val myUserHandle = android.os.Process.myUserHandle()
                val isOwnerMethod = UserHandle::class.java.getDeclaredMethod("isOwner")
                if (isOwnerMethod.invoke(myUserHandle) as Boolean) {
                    return@lazy ownerRetrieveFlags
                } else {
                    return@lazy retrieveFlags
                }
            }
        }
    }
}
