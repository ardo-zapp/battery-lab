package com.jacktor.batterylab.interfaces

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.adapters.ContributorsAdapter
import com.jacktor.batterylab.utilities.Constants.BACKEND_API_CONTRIBUTORS
import com.jacktor.batterylab.models.ContributorsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

interface ContributorsInterface {
    fun onItemClick(data: ContributorsModel)

    fun showContributorsDialog(context: Context) {
        ContributorsDialogHelper(this).show(context)
    }

    private class ContributorsDialogHelper(private val callback: ContributorsInterface) {

        private lateinit var recyclerView: RecyclerView
        private lateinit var noInternetText: MaterialTextView
        private lateinit var nextButton: MaterialButton
        private lateinit var prevButton: MaterialButton
        private lateinit var progressBar: LinearProgressIndicator

        private val contributorsList = ArrayList<ContributorsModel>()

        private var currentPage = 0
        private val pageSize = 5
        private var totalPages = 0

        fun show(context: Context) {
            val dialogBuilder = MaterialAlertDialogBuilder(context, R.style.ContributorsDialog)
            val inflater = LayoutInflater.from(context)
            val customView = inflater.inflate(R.layout.contributors_dialog, null)

            recyclerView = customView.findViewById(R.id.contributors_recycler)
            noInternetText = customView.findViewById(R.id.no_internet_text)
            nextButton = customView.findViewById(R.id.next_button)
            prevButton = customView.findViewById(R.id.prev_button)
            progressBar = customView.findViewById(R.id.progressBar)

            recyclerView.layoutManager = LinearLayoutManager(context)

            contributorsList.clear()

            val cacheFile = getCacheFile(context)

            if (cacheFile.exists() && !isCacheExpired(cacheFile)) {
                // Cache masih valid
                val cachedData = readFromCache(context)
                if (!cachedData.isNullOrEmpty()) {
                    parseContributors(cachedData)
                    totalPages = (contributorsList.size + pageSize - 1) / pageSize
                    displayPage(context)
                    updateButtonsVisibility()
                }
            } else if (isNetworkAvailable(context)) {
                // Cache expired → bersihkan dan fetch baru
                clearCache(context)
                fetchContributors(context)
            } else {
                // Tidak ada cache dan tidak ada internet
                showNoInternetMessage()
            }

            dialogBuilder.setView(customView)
                .setTitle(context.getString(R.string.contributors))
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.close)) { dialogBtn, _ -> dialogBtn.dismiss() }
                .show()

            prevButton.setOnClickListener {
                if (currentPage > 0) {
                    currentPage--
                    displayPage(context)
                }
            }

            nextButton.setOnClickListener {
                if (currentPage < totalPages - 1) {
                    currentPage++
                    displayPage(context)
                }
            }
        }

        private fun fetchContributors(context: Context) {
            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    retry(times = 3) {
                        val connection =
                            URL(BACKEND_API_CONTRIBUTORS).openConnection() as HttpURLConnection
                        connection.connectTimeout = 15000
                        connection.readTimeout = 20000
                        connection.requestMethod = "GET"

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val response =
                                connection.inputStream.bufferedReader().use { it.readText() }
                            saveToCache(context, response)
                            parseContributors(response)

                            withContext(Dispatchers.Main) {
                                progressBar.visibility = View.GONE
                                currentPage = 0
                                totalPages = (contributorsList.size + pageSize - 1) / pageSize
                                displayPage(context)
                                updateButtonsVisibility()
                            }
                        } else {
                            val code = connection.responseCode
                            val errorResponse =
                                connection.errorStream?.bufferedReader()?.use { it.readText() }
                            val (errCode, errMsg) = parseError(errorResponse, code, context)
                            throw Exception("(HTTP $code) [$errCode] $errMsg")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        noInternetText.text =
                            context.getString(R.string.failed_to_load_data, e.localizedMessage)
                        noInternetText.visibility = View.VISIBLE
                    }
                }
            }
        }

        private fun parseContributors(response: String) {
            val array = JSONArray(response)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                contributorsList.add(
                    ContributorsModel(
                        name = obj.optString("name", obj.getString("login")),
                        username = obj.getString("login"),
                        avatarUrl = obj.optString("avatar_url", ""),
                        contributions = obj.optInt("contributions", 0),
                        htmlUrl = obj.optString("html_url", "")
                    )
                )
            }
            contributorsList.sortByDescending { it.contributions }
        }

        private fun displayPage(context: Context) {
            val start = currentPage * pageSize
            val end = minOf((currentPage + 1) * pageSize, contributorsList.size)
            val pageData = contributorsList.subList(start, end)

            recyclerView.adapter = ContributorsAdapter(context, pageData, callback)
            prevButton.isEnabled = currentPage > 0
            nextButton.isEnabled = currentPage < totalPages - 1
        }

        private fun updateButtonsVisibility() {
            if (contributorsList.size > pageSize) {
                nextButton.visibility = View.VISIBLE
                prevButton.visibility = View.VISIBLE
            } else {
                nextButton.visibility = View.GONE
                prevButton.visibility = View.GONE
            }
        }

        private fun showNoInternetMessage() {
            noInternetText.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
        }

        private fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun getCacheFile(context: Context): File =
            File(context.cacheDir, "contributors.json")

        private fun isCacheExpired(file: File): Boolean {
            if (!file.exists()) return true
            val oneDay = TimeUnit.DAYS.toMillis(1)
            return System.currentTimeMillis() - file.lastModified() > oneDay
        }

        private fun saveToCache(context: Context, data: String) {
            getCacheFile(context).writeText(data)
        }

        private fun readFromCache(context: Context): String? {
            val file = getCacheFile(context)
            return if (file.exists()) file.readText() else null
        }

        private fun clearCache(context: Context) {
            getCacheFile(context).delete()
            try {
                val picassoCache = File(context.cacheDir, "picasso-cache")
                if (picassoCache.exists()) picassoCache.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private suspend fun retry(
            times: Int = 3,
            delayMillis: Long = 2000L,
            block: suspend () -> Unit
        ) {
            var attempt = 0
            while (true) {
                try {
                    block()
                    return
                } catch (e: Exception) {
                    if (++attempt >= times) throw e
                    delay(delayMillis)
                }
            }
        }

        private fun parseError(
            errorResponse: String?,
            responseCode: Int,
            context: Context
        ): Pair<String, String> {
            return try {
                val json = JSONObject(errorResponse ?: "")
                val code = json.optString("code", "UNKNOWN")
                val message = json.optString(
                    "message",
                    context.getString(
                        R.string.failed_to_fetch_data_response_code,
                        responseCode.toString()
                    )
                )
                code to message
            } catch (_: Exception) {
                "UNKNOWN" to context.getString(
                    R.string.failed_to_fetch_data_response_code,
                    responseCode.toString()
                )
            }
        }
    }
}
