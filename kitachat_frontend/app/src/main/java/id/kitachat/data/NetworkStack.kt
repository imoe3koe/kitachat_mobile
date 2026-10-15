package id.kitachat.data

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// URL Utama Server Cloud Railway Anda
const val BASE_URL = "https://kitachat-app-production.up.railway.app/"

// --- Model Data (Entities) ---
data class NetworkMessage(
    val id: String? = null,
    val family_code: String? = null,
    val user_id: Int? = null,
    val sender: String,
    val text: String?,
    val message_type: String = "text",
    val image_url: String? = null,
    val file_url: String? = null,
    val audio_url: String? = null,
    val reply_to_id: String? = null,
    val is_deleted: Boolean = false,
    val timestamp: String? = null,
    val is_read: Boolean = false,
    val read_at: String? = null // <-- TAMBAHAN REVISI: Properti read timestamp dari server
)

data class AuthRequest(
    val phone: String,
    val password: String,
    val name: String? = null,
    val register_type: String? = null,
    val invite_code: String? = null,
    val birthdate: String? = null
)

data class UserEntity(
    val id: Int,
    val phone: String,
    val name: String,
    val family_code: String?,
    val birthdate: String? = null,
    val avatar_url: String? = null,
    val photo_url: String? = null,
    val status: String? = null,
    val is_online: Boolean? = null
)

data class AuthResponse(
    val message: String? = null,
    val error: String? = null,
    val session_token: String? = null,
    val family_code: String? = null,
    val user: UserEntity? = null
)

data class AlbumItem(
    val id: Int,
    val image_url: String,
    val caption: String?,
    val uploader_name: String,
    val created_at: String
)

data class AgendaItem(
    val id: Int,
    val title: String,
    val event_date: String,
    val description: String?,
    val creator_name: String? = null
)

// --- Interface Endpoint REST API (Diselaraskan dengan rute server.js) ---
interface KitachatApiService {
    @POST("api/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("api/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    // Diselaraskan dengan rute tambahan di backend untuk REST fallback pesan
    @GET("api/messages")
    suspend fun getMessages(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String
    ): List<NetworkMessage>

    @Multipart
    @POST("api/send-message")
    suspend fun sendMessage(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String,
        @Part("message") message: RequestBody?,
        @Part("reply_to_id") replyToId: RequestBody?,
        @Part media: MultipartBody.Part?
    ): Response<ResponseBody>

    // --- Endpoint Delete Message ---
    @DELETE("api/messages/{id}")
    suspend fun deleteMessage(
        @Path("id") id: String,
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String
    ): Response<ResponseBody>

    // --- ENDPOINT ALBUM ---
    @GET("api/albums")
    suspend fun getAlbum(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String
    ): List<AlbumItem>

    @Multipart
    @POST("api/albums")
    suspend fun uploadAlbum(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String,
        @Part("caption") caption: RequestBody?,
        @Part image: MultipartBody.Part
    ): Response<ResponseBody>

    // --- ENDPOINT AGENDA ---
    @GET("api/agendas")
    suspend fun getAgenda(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String
    ): List<AgendaItem>

    @POST("api/agendas")
    suspend fun createAgenda(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String,
        @Body request: Map<String, String>
    ): Response<ResponseBody>

    // --- ENDPOINT KELUARGA ---
    @GET("api/users")
    suspend fun getFamilyMembers(
        @Header("x-user-id") userId: String,
        @Header("x-session-token") sessionToken: String
    ): List<UserEntity>
}

// --- HttpClient Singleton dengan Optimasi Timeout & Logging Interceptor ---
object RetrofitClient {
    val apiService: KitachatApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(KitachatApiService::class.java)
    }
}