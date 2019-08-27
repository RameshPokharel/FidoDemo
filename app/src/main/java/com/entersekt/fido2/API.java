package com.entersekt.fido2;


//import com.merojob.merojobapp.model.job.JobListResponse;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface API {
    @Headers({"Accept: application/json", "Content-Type: application/json"})
    @POST("tests/fido2/attestation/options")//https://devapi.singularkey.com/tests/fido2/attestation/options
    Call<ResponseBody> getResult(@Body RequestBody loginPostBody);
}