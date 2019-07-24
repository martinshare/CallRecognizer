package com.wepa.callrecognizer.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import com.wepa.callrecognizer.R
import com.wepa.callrecognizer.call.CallDetectService
import com.wepa.callrecognizer.model.ContactsRequest
import com.wepa.callrecognizer.network.ContactsApi
import com.wepa.callrecognizer.utils.makeLongToast
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import retrofit2.Response
import timber.log.Timber
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import javax.inject.Inject

private const val MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 1
private const val MY_PERMISSIONS_REQUEST_PROCESS_OUTGOING_CALLS = 2

class MainActivity : AppCompatActivity() {

    private var detectEnabled: Boolean = false

    @Inject
    internal lateinit var contactsApi: ContactsApi

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AndroidInjection.inject(this)

        checkPermissions()

        buttonDetectToggle.setOnClickListener { setDetectEnabled(!detectEnabled) }

        buttonExit.setOnClickListener {
            setDetectEnabled(false)
            this@MainActivity.finish()
        }
        getContactsRequest()
    }

    private fun getContactsRequest() {
        GlobalScope.launch(Dispatchers.Main) {
            val requestTimestamp = DateTime.now()
//        viewModel.showProgressBar = true
            val getContactsRequest = withContext(Dispatchers.IO) { contactsApi.getContact() }
            try {
                val response = getContactsRequest.await()
                handleResponse(response, requestTimestamp)
            } catch (exception: Exception) {
                when (exception) {
                    is SocketTimeoutException, is ConnectException -> baseContext.makeLongToast(R.string.connection_error)
                    else -> {
                        baseContext.makeLongToast(getString(R.string.problem_occurred) + ": ${exception.message}")
                        Timber.d("connection error $exception.message}")
                    }
                }
            } finally {
//                viewModel.showProgressBar = false
            }
        }
    }

    private fun handleResponse(response: Response<ContactsRequest>, requestTimestamp: DateTime) = try {
        handleResponseUnsafe(response, requestTimestamp)
    } catch (exception: Exception) {
        baseContext.makeLongToast("Unknown response!")
        Timber.d("Unknown response $exception.message}")
    }

    private fun handleResponseUnsafe(response: Response<ContactsRequest>, requestTimestamp: DateTime) =
        when (response.code()) {
            HttpURLConnection.HTTP_BAD_REQUEST -> baseContext.makeLongToast(R.string.invalid_credentials)
            else -> throw IllegalStateException("Unknown response code!")
        }

    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (applicationContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    MY_PERMISSIONS_REQUEST_READ_PHONE_STATE
                )
            }
            if (applicationContext.checkSelfPermission(Manifest.permission.PROCESS_OUTGOING_CALLS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.PROCESS_OUTGOING_CALLS),
                    MY_PERMISSIONS_REQUEST_PROCESS_OUTGOING_CALLS
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    private fun setDetectEnabled(enable: Boolean) {
        detectEnabled = enable

        val intent = Intent(this, CallDetectService::class.java)
        intent.putExtra(MainActivity::class.java.simpleName, editTextNumber.text.toString())
        if (enable) {
            // start detect service
            startService(intent)

            buttonDetectToggle.text = "Turn off"
            textViewDetectState.text = "Detecting"
        } else {
            // stop detect service
            stopService(intent)

            buttonDetectToggle.text = "Turn on"
            textViewDetectState.text = "Not detecting"
        }
    }
}