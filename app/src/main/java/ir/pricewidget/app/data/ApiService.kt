package ir.pricewidget.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    // Base path used by brsapi.ir's free gold & currency webservice.
    // Adjust the path here if brsapi's docs show a different route for your key.
    @GET("Api/Market/gold_currency.php")
    suspend fun getGoldCurrency(@Query("key") key: String): GoldCurrencyResponse

    companion object {
        private const val BASE_URL = "https://brsapi.ir/"

        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}
