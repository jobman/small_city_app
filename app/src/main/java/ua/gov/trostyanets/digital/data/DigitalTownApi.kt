package ua.gov.trostyanets.digital.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DigitalTownApi {
    @GET("news/news-list/")
    suspend fun getNews(): List<NewsItem>

    @GET("external-links/all-links/")
    suspend fun getLinks(): List<ExternalLinkItem>

    @GET("notifications/alarm/")
    suspend fun getAlarmState(): AlarmResponse

    @GET("notifications/history/")
    suspend fun getNotificationHistory(
        @Query("address_id") addressId: Int,
        @Query("firebase_token") firebaseToken: String,
    ): NotificationHistoryResponse

    @POST("outages/city-outage/")
    suspend fun getOutages(
        @Body request: OutageLookupRequest,
    ): Response<OutageResponse>
}
