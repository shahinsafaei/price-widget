package ir.pricewidget.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers

interface ApiService {
    // Reads the pre-fetched, shared JSON cached by our GitHub Actions workflow
    // (.github/workflows/fetch-rates.yml) instead of calling brsapi.ir directly
    // from every device. This keeps API usage constant regardless of user count.
    @Headers("Cache-Control: no-cache")
    @GET("shahinsafaei/price-widget/main/data/latest.json")
    suspend fun getGoldCurrency(): GoldCurrencyResponse

    companion object {
        private const val BASE_URL = "https://raw.githubusercontent.com/"

        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}
