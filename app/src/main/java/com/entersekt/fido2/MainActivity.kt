package com.entersekt.fido2

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.*
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    companion object {
        private const val LOG_TAG = "Fido2Demo"
        private const val REQUEST_CODE_REGISTER = 1
        private const val REQUEST_CODE_SIGN = 2
        private const val KEY_HANDLE_PREF = "key_handle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerFido2Button.setOnClickListener { getChallenge() }
        signFido2Button.setOnClickListener { signFido2() }
        signFido2Button.isEnabled = loadKeyHandle() != null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(LOG_TAG, "onActivityResult - requestCode: $requestCode, resultCode: $resultCode")

        when (resultCode) {
            RESULT_OK -> {
                data?.let {
                    if (it.hasExtra(Fido.FIDO2_KEY_ERROR_EXTRA)) {
                        handleErrorResponse(data.getByteArrayExtra(Fido.FIDO2_KEY_ERROR_EXTRA))
                    } else if (it.hasExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)) {
                        val fido2Response = data.getByteArrayExtra(Fido.FIDO2_KEY_RESPONSE_EXTRA)
                        when (requestCode) {
                            REQUEST_CODE_REGISTER -> handleRegisterResponse(fido2Response)
                            REQUEST_CODE_SIGN -> handleSignResponse(fido2Response)
                        }
                    }
                }
            }
            RESULT_CANCELED -> {
                val result = "Operation is cancelled"
                resultText.text = result
                Log.d(LOG_TAG, result)
            }
            else -> {
                val result = "Operation failed, with resultCode: $resultCode"
                resultText.text = result
                Log.e(LOG_TAG, result)
            }
        }
    }

    private fun getChallenge() {
        val okHttpClient = OkHttpClient().newBuilder()
            .build()
        val retrofit =
            Retrofit.Builder().client(okHttpClient)
                .baseUrl("https://devapi.singularkey.com/").build().create(API::class.java)

        val result = JSONObject()
        val mediaType = MediaType.parse("application/json")
        result.put("username", "test1")
        val requestBody = RequestBody.create(mediaType, result.toString())

        retrofit.getResult(requestBody).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    var obj = JSONObject(response.body()?.string())
                    var challenge = obj?.getString("challenge")
                    var rpname = obj?.getJSONObject("rp").getString("name")
                    var username = obj?.getJSONObject("user").getString("name")
                    var userId = obj?.getJSONObject("user").getString("id")

                    registerFido2("call your rpid ", rpname, challenge, userId, "", username)
                    //{"challenge":"51DoDsakwoW6K3vFmpxFI7Kc1NPK2elN3Xcuimde-nQ",
                    // "rp":{"name":"Safe Bank"},
                    // "user":{"id":"HO4XTjFw1GOpV9eFnN2tmzQ1D5p4qXvX","name":"test1","displayName":null},
                    // "attestation":"direct","status":"ok","excludeCredentials":[],"errorMessage":"","pubKeyCredParams":[{"type":"public-key","alg":-7},{"type":"public-key","alg":-8},{"type":"public-key","alg":-35},{"type":"public-key","alg":-36},{"type":"public-key","alg":-37},{"type":"public-key","alg":-38},{"type":"public-key","alg":-39},{"type":"public-key","alg":-257},{"type":"public-key","alg":-258},{"type":"public-key","alg":-259}]}
                    // call registerFido with challenge and user information

                    Log.d("response", response.message())
                } else {
                    Log.d("response", response.errorBody().toString())

                }

            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d("response", t.message)

            }
        })

    }


    
    private fun registerFido2(
        rpId: String,
        rpname: String,
        challenge: String,
        userId: String,
        userEmail: String?,
        userName: String?
    ) {


        // All the option parameters should come from the Relying Party / server
        val options = PublicKeyCredentialCreationOptions.Builder()
            .setRp(PublicKeyCredentialRpEntity(rpId, rpname, null))
            .setUser(
                PublicKeyCredentialUserEntity(
                    userId.toByteArray(),
                    userEmail,
                    null,
                    userName
                )
            )
            .setChallenge(challenge.toByteArray())
            .setParameters(
                listOf(
                    PublicKeyCredentialParameters(
                        PublicKeyCredentialType.PUBLIC_KEY.toString(),
                        EC2Algorithm.ES256.algoValue
                    )
                )
            )
            .build()

        val fido2ApiClient = Fido.getFido2ApiClient(applicationContext)
        val fido2PendingIntentTask = fido2ApiClient.getRegisterIntent(options)
        fido2PendingIntentTask.addOnSuccessListener { fido2PendingIntent ->
            if (fido2PendingIntent.hasPendingIntent()) {
                try {
                    Log.d(LOG_TAG, "launching Fido2 Pending Intent")
                    fido2PendingIntent.launchPendingIntent(this@MainActivity, REQUEST_CODE_REGISTER)
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun signFido2() {
        // All the option parameters should come from the Relying Party / server
        val options = PublicKeyCredentialRequestOptions.Builder()
            .setRpId("strategics-fido2.firebaseapp.com")
            .setAllowList(
                listOf(
                    PublicKeyCredentialDescriptor(
                        PublicKeyCredentialType.PUBLIC_KEY.toString(),
                        loadKeyHandle(),
                        null
                    )
                )
            )
            .setChallenge(challenge())
            .build()

        val fido2ApiClient = Fido.getFido2ApiClient(applicationContext)
        val fido2PendingIntentTask = fido2ApiClient.getSignIntent(options)
        fido2PendingIntentTask.addOnSuccessListener { fido2PendingIntent ->
            if (fido2PendingIntent.hasPendingIntent()) {
                try {
                    Log.d(LOG_TAG, "launching Fido2 Pending Intent")
                    fido2PendingIntent.launchPendingIntent(this@MainActivity, REQUEST_CODE_SIGN)
                } catch (e: IntentSender.SendIntentException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun handleErrorResponse(errorBytes: ByteArray) {
        val authenticatorErrorResponse = AuthenticatorErrorResponse.deserializeFromBytes(errorBytes)
        val errorName = authenticatorErrorResponse.errorCode.name
        val errorMessage = authenticatorErrorResponse.errorMessage

        Log.e(LOG_TAG, "errorCode.name: $errorName")
        Log.e(LOG_TAG, "errorMessage: $errorMessage")

        val registerFidoResult = "An Error Ocurred\n\nError Name:\n$errorName\n\nError Message:\n$errorMessage"
        resultText.text = registerFidoResult
    }

    /**
     * The response should be sent to the Relying Party / server to validate and store
     */
    private fun handleRegisterResponse(fido2Response: ByteArray) {
        val response = AuthenticatorAttestationResponse.deserializeFromBytes(fido2Response)
        val keyHandleBase64 = Base64.encodeToString(response.keyHandle, Base64.DEFAULT)
        val clientDataJson = String(response.clientDataJSON, Charsets.UTF_8)
        val attestationObjectBase64 = Base64.encodeToString(response.attestationObject, Base64.DEFAULT)

        storeKeyHandle(response.keyHandle)
        signFido2Button.isEnabled = true

        Log.d(LOG_TAG, "keyHandleBase64: $keyHandleBase64")
        Log.d(LOG_TAG, "clientDataJSON: $clientDataJson")
        Log.d(LOG_TAG, "attestationObjectBase64: $attestationObjectBase64")

        val registerFido2Result = "Authenticator Attestation Response\n\n" +
                "keyHandleBase64:\n" +
                "$keyHandleBase64\n\n" +
                "clientDataJSON:\n" +
                "$clientDataJson\n\n" +
                "attestationObjectBase64:\n" +
                "$attestationObjectBase64\n"

        resultText.text = registerFido2Result
    }
//{"type":"webauthn.create",
// "challenge":"Z2hwaG9CaGdCcVktS2lvbVRGMzBEQlFiUXJ0cml5RkJYZHliZmNvdUdEdw",
// "origin":"android:apk-key-hash:xYvjmzazZxLXDNrFnWUq_EObrht2yX2hfmkrehWrJ5Y",
// "androidPackageName":"com.entersekt.fido2"}
    /**
     * The response should be sent to the Relying Party / server to validate
     */
    private fun handleSignResponse(fido2Response: ByteArray) {
        val response = AuthenticatorAssertionResponse.deserializeFromBytes(fido2Response)
        val keyHandleBase64 = Base64.encodeToString(response.keyHandle, Base64.DEFAULT)
        val clientDataJson = String(response.clientDataJSON, Charsets.UTF_8)
        val authenticatorDataBase64 = Base64.encodeToString(response.authenticatorData, Base64.DEFAULT)
        val signatureBase64 = Base64.encodeToString(response.signature, Base64.DEFAULT)

        Log.d(LOG_TAG, "keyHandleBase64: $keyHandleBase64")
        Log.d(LOG_TAG, "clientDataJSON: $clientDataJson")
        Log.d(LOG_TAG, "authenticatorDataBase64: $authenticatorDataBase64")
        Log.d(LOG_TAG, "signatureBase64: $signatureBase64")

        val signFido2Result = "Authenticator Assertion Response\n\n" +
                "keyHandleBase64:\n" +
                "$keyHandleBase64\n\n" +
                "clientDataJSON:\n" +
                "$clientDataJson\n\n" +
                "authenticatorDataBase64:\n" +
                "$authenticatorDataBase64\n\n" +
                "signatureBase64:\n" +
                "$signatureBase64\n"

        resultText.text = signFido2Result
    }

    /**
     * https://www.w3.org/TR/webauthn/#cryptographic-challenges
     */
    private fun challenge(): ByteArray {
        val secureRandom = SecureRandom()
        val challenge = ByteArray(16)
        secureRandom.nextBytes(challenge)
        return challenge
    }

    private fun storeKeyHandle(keyHandle: ByteArray) {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putString(KEY_HANDLE_PREF, Base64.encodeToString(keyHandle, Base64.DEFAULT))
        }
    }

    private fun loadKeyHandle(): ByteArray? {
        val keyHandleBase64 = PreferenceManager.getDefaultSharedPreferences(this).getString(KEY_HANDLE_PREF, null)
            ?: return null
        return Base64.decode(keyHandleBase64, Base64.DEFAULT)
    }
}
