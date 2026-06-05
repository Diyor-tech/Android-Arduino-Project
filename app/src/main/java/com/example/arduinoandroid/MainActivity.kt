package com.example.arduinoandroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.arduinoandroid.ui.theme.ArduinoAndroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import androidx.compose.material3.ButtonDefaults

class MainActivity : ComponentActivity()
{
    private lateinit var arduinoConnection: ArduinoWifiConnection
    private lateinit var cloudRepository: CloudRepository
    private lateinit var wifiScanner: WifiScanner

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Разрешения запрошены. Подключение к Arduino запускается кнопкой в интерфейсе.
        }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        arduinoConnection = ArduinoWifiConnection(
            context = this,
            defaultSsid = AppConfig.DEFAULT_ARDUINO_SSID,
            defaultPassword = AppConfig.DEFAULT_ARDUINO_PASSWORD,
            defaultBaseUrl = AppConfig.DEFAULT_ARDUINO_BASE_URL
        )

        wifiScanner = WifiScanner(this)

        cloudRepository =
            if (AppConfig.isSupabaseConfigured())
            {
                SupabaseCloudRepository(
                    supabaseUrl = AppConfig.SUPABASE_URL,
                    anonKey = AppConfig.SUPABASE_ANON_KEY
                )
            }
            else
            {
                DemoCloudRepository(this)
            }

        requestWifiPermissions()

        setContent {
            ArduinoAndroidTheme(dynamicColor = false) {
                IrrigationApp(
                    cloudRepository = cloudRepository,
                    arduinoConnection = arduinoConnection,
                    wifiScanner = wifiScanner
                )
            }
        }
    }

    private fun requestWifiPermissions()
    {
        permissionLauncher.launch(requiredWifiPermissions())
    }

    override fun onDestroy()
    {
        arduinoConnection.disconnect()
        super.onDestroy()
    }
}

fun requiredWifiPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    else
    {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

fun hasWifiScanPermissions(context: Context): Boolean =
    requiredWifiPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

fun isLocationServiceEnabled(context: Context): Boolean
{
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    {
        locationManager.isLocationEnabled
    }
    else
    {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

object AppConfig
{
    // Заполни после создания проекта Supabase:
    // SUPABASE_URL: https://<project-id>.supabase.co
    // SUPABASE_ANON_KEY: Project Settings -> API -> anon public key
    const val SUPABASE_URL = "https://vdlkcvoipkdknhqcixvf.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZkbGtjdm9pcGtka25ocWNpeHZmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA2NTc0NzcsImV4cCI6MjA5NjIzMzQ3N30.x-Car1eRXqIPBc2Ya-ZvAz41G3eoa-8eVglPmU2yRA4"

    const val DEFAULT_ARDUINO_SSID = "SmartIrrigation"
    const val DEFAULT_ARDUINO_PASSWORD = "12345678"
    const val DEFAULT_ARDUINO_BASE_URL = "http://192.168.4.1"

    fun isSupabaseConfigured(): Boolean =
        SUPABASE_URL.startsWith("https://") && SUPABASE_ANON_KEY.length > 20
}

data class CloudSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String = ""
)

data class PlantWaterNeed(
    val id: String,
    val name: String,
    val waterNeed: Int,
    val description: String
)

data class IrrigationZone(
    val zoneNumber: Int,
    val name: String,
    val plant: PlantWaterNeed,
    val customWaterNeed: Int,
    val enabled: Boolean
)

data class ArduinoStatus(
    val soilMoisturePercent: Int,
    val temperatureC: Double,
    val lightPercent: Int,
    val waterLevelPercent: Int,
    val flowRateLMin: Double,
    val pumpOn: Boolean,
    val autoMode: Boolean,
    val activeZone: Int,
    val lastUpdatedText: String = "только что"
)

data class WifiNetworkItem(
    val ssid: String,
    val secure: Boolean,
    val signalLevel: Int
)

data class ControllerConnectionConfig(
    val deviceCode: String,
    val deviceName: String,
    val wifiSsid: String,
    val wifiPassword: String,
    val baseUrl: String
)

sealed class ArduinoConnectionState
{
    object Disconnected : ArduinoConnectionState()
    object Connecting : ArduinoConnectionState()
    data class Connected(val status: ArduinoStatus?) : ArduinoConnectionState()
}

class WifiScanner(private val context: Context)
{
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @SuppressLint("MissingPermission")
    suspend fun scan(): List<WifiNetworkItem> =
        withContext(Dispatchers.IO)
        {
            try
            {
                wifiManager.startScan()
                delay(1600)
                wifiManager.scanResults
                    .asSequence()
                    .filter { it.SSID.isNotBlank() }
                    .groupBy { it.SSID }
                    .map { (_, results) -> results.maxBy { it.level } }
                    .sortedWith(
                        compareByDescending<ScanResult> {
                            it.SSID.contains("Smart", ignoreCase = true) ||
                                    it.SSID.contains("Arduino", ignoreCase = true) ||
                                    it.SSID.contains("Irrigation", ignoreCase = true)
                        }.thenByDescending { it.level }
                    )
                    .map {
                        WifiNetworkItem(
                            ssid = it.SSID,
                            secure = it.capabilities.contains("WPA") ||
                                    it.capabilities.contains("WEP") ||
                                    it.capabilities.contains("SAE"),
                            signalLevel = WifiManager.calculateSignalLevel(it.level, 5)
                        )
                    }
                    .toList()
            }
            catch (e: SecurityException)
            {
                throw IllegalStateException("Нет разрешения на поиск Wi-Fi сетей.")
            }
            catch (e: Exception)
            {
                throw IllegalStateException("Не удалось выполнить поиск Wi-Fi: ${e.message}")
            }
        }
}

interface CloudRepository
{
    val title: String
    suspend fun login(email: String, password: String): CloudSession
    suspend fun register(name: String, email: String, password: String): CloudSession
    suspend fun restore(savedAuth: SavedAuth): CloudSession
    suspend fun loadPlants(session: CloudSession): List<PlantWaterNeed>
    suspend fun loadZones(session: CloudSession, plants: List<PlantWaterNeed>): List<IrrigationZone>
    suspend fun loadControllerByCode(session: CloudSession, deviceCode: String): ControllerConnectionConfig
    suspend fun saveZone(session: CloudSession, zone: IrrigationZone)
}

class DemoCloudRepository(context: Context) : CloudRepository
{
    override val title: String = "Локальный режим"
    private val prefs = context.getSharedPreferences("irrigation_demo_db", Context.MODE_PRIVATE)

    override suspend fun login(email: String, password: String): CloudSession =
        withContext(Dispatchers.IO)
        {
            val savedEmail = prefs.getString("email", null)
            val savedPassword = prefs.getString("password", null)

            if (savedEmail == null || savedPassword == null)
            {
                throw IllegalStateException("Пользователь не найден. Сначала выполните регистрацию.")
            }

            if (!email.equals(savedEmail, ignoreCase = true) || password != savedPassword)
            {
                throw IllegalArgumentException("Неверная почта или пароль.")
            }

            CloudSession(
                userId = "demo-user",
                email = savedEmail,
                displayName = prefs.getString("name", "Пользователь") ?: "Пользователь",
                accessToken = "demo-token"
            )
        }

    override suspend fun register(name: String, email: String, password: String): CloudSession =
        withContext(Dispatchers.IO)
        {
            if (password.length < 6)
            {
                throw IllegalArgumentException("Пароль должен быть не короче 6 символов.")
            }

            prefs.edit()
                .putString("name", name.ifBlank { "Пользователь" })
                .putString("email", email.trim())
                .putString("password", password)
                .apply()

            CloudSession(
                userId = "demo-user",
                email = email.trim(),
                displayName = name.ifBlank { "Пользователь" },
                accessToken = "demo-token"
            )
        }

    override suspend fun restore(savedAuth: SavedAuth): CloudSession =
        login(savedAuth.email, savedAuth.password.orEmpty())

    override suspend fun loadPlants(session: CloudSession): List<PlantWaterNeed> =
        defaultPlants()

    override suspend fun loadZones(
        session: CloudSession,
        plants: List<PlantWaterNeed>
    ): List<IrrigationZone> =
        (1..4).map { number ->
            val plantId = prefs.getString("zone_${number}_plant", plants[number % plants.size].id)
            val plant = plants.firstOrNull { it.id == plantId } ?: plants.first()
            IrrigationZone(
                zoneNumber = number,
                name = prefs.getString("zone_${number}_name", "Зона $number") ?: "Зона $number",
                plant = plant,
                customWaterNeed = prefs.getInt("zone_${number}_need", plant.waterNeed),
                enabled = prefs.getBoolean("zone_${number}_enabled", true)
            )
        }

    override suspend fun loadControllerByCode(
        session: CloudSession,
        deviceCode: String
    ): ControllerConnectionConfig =
        withContext(Dispatchers.IO)
        {
            if (!deviceCode.trim().equals("IRR-1001", ignoreCase = true))
            {
                throw IllegalArgumentException("Контроллер с таким кодом не найден.")
            }

            ControllerConnectionConfig(
                deviceCode = "IRR-1001",
                deviceName = "Демо-контроллер орошения",
                wifiSsid = AppConfig.DEFAULT_ARDUINO_SSID,
                wifiPassword = AppConfig.DEFAULT_ARDUINO_PASSWORD,
                baseUrl = AppConfig.DEFAULT_ARDUINO_BASE_URL
            )
        }

    override suspend fun saveZone(session: CloudSession, zone: IrrigationZone)
    {
        withContext(Dispatchers.IO)
        {
            prefs.edit()
                .putString("zone_${zone.zoneNumber}_name", zone.name)
                .putString("zone_${zone.zoneNumber}_plant", zone.plant.id)
                .putInt("zone_${zone.zoneNumber}_need", zone.customWaterNeed)
                .putBoolean("zone_${zone.zoneNumber}_enabled", zone.enabled)
                .apply()
        }
    }
}

class SupabaseCloudRepository(
    private val supabaseUrl: String,
    private val anonKey: String
) : CloudRepository
{
    override val title: String = "Supabase Cloud"

    override suspend fun login(email: String, password: String): CloudSession
    {
        val json = httpJson(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            token = anonKey,
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
        )

        return sessionFromAuthJson(json, email)
    }

    override suspend fun register(name: String, email: String, password: String): CloudSession
    {
        val signUpJson = httpJson(
            method = "POST",
            path = "/auth/v1/signup",
            token = anonKey,
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("full_name", name.ifBlank { "Пользователь" }))
        )

        val session =
            if (signUpJson.has("access_token"))
            {
                sessionFromAuthJson(signUpJson, email)
            }
            else
            {
                login(email, password)
            }

        saveProfile(session, name.ifBlank { "Пользователь" })
        return session
    }

    override suspend fun restore(savedAuth: SavedAuth): CloudSession
    {
        if (savedAuth.refreshToken.isNotBlank())
        {
            val json = httpJson(
                method = "POST",
                path = "/auth/v1/token?grant_type=refresh_token",
                token = anonKey,
                body = JSONObject().put("refresh_token", savedAuth.refreshToken)
            )

            return sessionFromAuthJson(json, savedAuth.email)
        }

        if (!savedAuth.password.isNullOrBlank())
        {
            return login(savedAuth.email, savedAuth.password)
        }

        throw IllegalStateException("Сохраненная сессия недействительна.")
    }

    override suspend fun loadPlants(session: CloudSession): List<PlantWaterNeed>
    {
        val response = httpText(
            method = "GET",
            path = "/rest/v1/plants?select=id,name,water_need,description&order=water_need.asc",
            token = session.accessToken
        )

        val array = JSONArray(response)
        if (array.length() == 0) return defaultPlants()

        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            PlantWaterNeed(
                id = item.getString("id"),
                name = item.getString("name"),
                waterNeed = item.getInt("water_need"),
                description = item.optString("description", "")
            )
        }
    }

    override suspend fun loadZones(
        session: CloudSession,
        plants: List<PlantWaterNeed>
    ): List<IrrigationZone>
    {
        val response = httpText(
            method = "GET",
            path = "/rest/v1/user_zones?select=zone_number,name,plant_id,custom_water_need,enabled&user_id=eq.${session.userId}&order=zone_number.asc",
            token = session.accessToken
        )

        val array = JSONArray(response)

        val savedZonesByNumber = (0 until array.length())
            .map { index -> array.getJSONObject(index) }
            .associateBy { item -> item.getInt("zone_number") }

        return (1..4).map { number ->
            val savedZone = savedZonesByNumber[number]
            val defaultPlant = plants[(number - 1) % plants.size]

            if (savedZone == null)
            {
                IrrigationZone(
                    zoneNumber = number,
                    name = "Зона $number",
                    plant = defaultPlant,
                    customWaterNeed = defaultPlant.waterNeed,
                    enabled = true
                )
            }
            else
            {
                val plant = plants.firstOrNull { it.id == savedZone.optString("plant_id") } ?: defaultPlant

                IrrigationZone(
                    zoneNumber = number,
                    name = savedZone.optString("name", "Зона $number"),
                    plant = plant,
                    customWaterNeed = savedZone.optInt("custom_water_need", plant.waterNeed),
                    enabled = savedZone.optBoolean("enabled", true)
                )
            }
        }
    }

    override suspend fun loadControllerByCode(
        session: CloudSession,
        deviceCode: String
    ): ControllerConnectionConfig
    {
        val trimmedCode = deviceCode.trim()
        if (trimmedCode.isBlank())
        {
            throw IllegalArgumentException("Введите код контроллера.")
        }

        val encodedCode = URLEncoder.encode(trimmedCode, StandardCharsets.UTF_8.name())
        val response = httpText(
            method = "GET",
            path = "/rest/v1/controllers?select=device_code,device_name,wifi_ssid,wifi_password,base_url&device_code=eq.$encodedCode&is_active=eq.true&limit=1",
            token = session.accessToken
        )

        val array = JSONArray(response)
        if (array.length() == 0)
        {
            throw IllegalArgumentException("Контроллер с таким кодом не найден или не привязан к вашему аккаунту.")
        }

        val item = array.getJSONObject(0)

        return ControllerConnectionConfig(
            deviceCode = item.getString("device_code"),
            deviceName = item.optString("device_name", "Контроллер орошения"),
            wifiSsid = item.getString("wifi_ssid"),
            wifiPassword = item.optString("wifi_password", ""),
            baseUrl = item.optString("base_url", AppConfig.DEFAULT_ARDUINO_BASE_URL)
        )
    }

    override suspend fun saveZone(session: CloudSession, zone: IrrigationZone)
    {
        httpText(
            method = "POST",
            path = "/rest/v1/user_zones?on_conflict=user_id,zone_number",
            token = session.accessToken,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            body = JSONObject()
                .put("user_id", session.userId)
                .put("zone_number", zone.zoneNumber)
                .put("name", zone.name)
                .put("plant_id", zone.plant.id)
                .put("custom_water_need", zone.customWaterNeed)
                .put("enabled", zone.enabled)
        )
    }

    private suspend fun saveProfile(session: CloudSession, name: String)
    {
        httpText(
            method = "POST",
            path = "/rest/v1/profiles",
            token = session.accessToken,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            body = JSONObject()
                .put("id", session.userId)
                .put("email", session.email)
                .put("display_name", name)
        )
    }

    private fun sessionFromAuthJson(json: JSONObject, fallbackEmail: String): CloudSession
    {
        val user = json.getJSONObject("user")
        val meta = user.optJSONObject("user_metadata")

        return CloudSession(
            userId = user.getString("id"),
            email = user.optString("email", fallbackEmail),
            displayName = meta?.optString("full_name")?.takeIf { it.isNotBlank() }
                ?: user.optString("email", fallbackEmail),
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token", "")
        )
    }

    private suspend fun httpJson(
        method: String,
        path: String,
        token: String,
        body: JSONObject? = null
    ): JSONObject = JSONObject(httpText(method, path, token, body = body))

    private suspend fun httpText(
        method: String,
        path: String,
        token: String,
        extraHeaders: Map<String, String> = emptyMap(),
        body: JSONObject? = null
    ): String =
        withContext(Dispatchers.IO)
        {
            val connection = (URL(supabaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)

            try
            {
                connection.requestMethod = method
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                connection.setRequestProperty("apikey", anonKey)
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                extraHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }

                if (body != null)
                {
                    connection.doOutput = true
                    connection.outputStream.use { stream ->
                        stream.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                }

                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                if (code !in 200..299)
                {
                    throw IllegalStateException("Ошибка Supabase $code: $response")
                }

                response
            }
            finally
            {
                connection.disconnect()
            }
        }
}

class ArduinoWifiConnection(
    private val context: Context,
    private val defaultSsid: String,
    private val defaultPassword: String,
    private val defaultBaseUrl: String
)
{
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentNetwork: Network? = null
    private var baseUrl: String = defaultBaseUrl

    fun connect(
        ssid: String = defaultSsid,
        password: String = defaultPassword,
        url: String = defaultBaseUrl,
        onConnected: suspend () -> Unit,
        onError: (String) -> Unit
    )
    {
        disconnect()
        baseUrl = url.trimEnd('/')

        if (ssid.isBlank())
        {
            onError("Сначала выберите Wi-Fi сеть контроллера.")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        {
            onError("Автоподключение к Wi-Fi работает с Android 10+. Подключитесь к сети Arduino вручную.")
            return
        }

        val wifiSpecifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)

        if (password.isNotBlank())
        {
            wifiSpecifierBuilder.setWpa2Passphrase(password)
        }

        val wifiSpecifier = wifiSpecifierBuilder.build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(wifiSpecifier)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback()
        {
            override fun onAvailable(network: Network)
            {
                currentNetwork = network

                mainScope.launch {
                    try
                    {
                        onConnected()
                    }
                    catch (e: Exception)
                    {
                        onError(e.message ?: "Ошибка проверки Arduino")
                    }
                }
            }

            override fun onUnavailable()
            {
                mainScope.launch {
                    onError("Сеть Arduino не найдена или подключение отменено.")
                }
            }

            override fun onLost(network: Network)
            {
                if (currentNetwork == network)
                {
                    currentNetwork = null
                }
            }
        }

        networkCallback = callback

        try
        {
            connectivityManager.requestNetwork(networkRequest, callback, 25000)
        }
        catch (e: SecurityException)
        {
            onError("Нет разрешения для подключения к Wi-Fi: ${e.message}")
        }
        catch (e: Exception)
        {
            onError("Ошибка подключения к Wi-Fi: ${e.message}")
        }
    }

    fun disconnect()
    {
        networkCallback?.let {
            try
            {
                connectivityManager.unregisterNetworkCallback(it)
            }
            catch (_: Exception)
            {
            }
        }

        networkCallback = null
        currentNetwork = null
    }

    suspend fun getStatus(): ArduinoStatus
    {
        val json = JSONObject(httpGet("$baseUrl/status"))

        return ArduinoStatus(
            soilMoisturePercent = json.optInt("soilMoisture", 0),
            temperatureC = json.optDouble("temperature", 0.0),
            lightPercent = json.optInt("light", 0),
            waterLevelPercent = json.optInt("waterLevel", 0),
            flowRateLMin = json.optDouble("flowRate", 0.0),
            pumpOn = json.optBoolean("pumpOn", false),
            autoMode = json.optBoolean("autoMode", true),
            activeZone = json.optInt("activeZone", 1)
        )
    }

    suspend fun setPump(enabled: Boolean, zoneNumber: Int): ArduinoStatus
    {
        val state = if (enabled) "on" else "off"
        val response = httpGet("$baseUrl/pump?state=$state&zone=$zoneNumber")
        return parseStatusOrRefresh(response)
    }

    suspend fun setMode(autoMode: Boolean): ArduinoStatus
    {
        val mode = if (autoMode) "auto" else "manual"
        val response = httpGet("$baseUrl/mode?value=$mode")
        return parseStatusOrRefresh(response)
    }

    suspend fun uploadZone(zone: IrrigationZone): ArduinoStatus
    {
        val url = "$baseUrl/zone" +
                "?zone=${zone.zoneNumber}" +
                "&enabled=${if (zone.enabled) 1 else 0}" +
                "&waterNeed=${zone.customWaterNeed}" +
                "&plant=${encode(zone.plant.name)}"

        val response = httpGet(url)
        return parseStatusOrRefresh(response)
    }

    suspend fun uploadZones(zones: List<IrrigationZone>): ArduinoStatus
    {
        var status: ArduinoStatus? = null
        zones.forEach { zone ->
            status = uploadZone(zone)
            delay(120)
        }
        return status ?: getStatus()
    }

    private suspend fun parseStatusOrRefresh(response: String): ArduinoStatus =
        if (response.trim().startsWith("{"))
        {
            val json = JSONObject(response)
            ArduinoStatus(
                soilMoisturePercent = json.optInt("soilMoisture", 0),
                temperatureC = json.optDouble("temperature", 0.0),
                lightPercent = json.optInt("light", 0),
                waterLevelPercent = json.optInt("waterLevel", 0),
                flowRateLMin = json.optDouble("flowRate", 0.0),
                pumpOn = json.optBoolean("pumpOn", false),
                autoMode = json.optBoolean("autoMode", true),
                activeZone = json.optInt("activeZone", 1)
            )
        }
        else
        {
            getStatus()
        }

    private suspend fun httpGet(urlText: String): String =
        withContext(Dispatchers.IO)
        {
            val url = URL(urlText)
            val connection =
                if (currentNetwork != null)
                {
                    currentNetwork!!.openConnection(url) as HttpURLConnection
                }
                else
                {
                    url.openConnection() as HttpURLConnection
                }

            try
            {
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.useCaches = false

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream.bufferedReader().use { it.readText() }

                if (code !in 200..299)
                {
                    throw IllegalStateException("Arduino ответила $code: $text")
                }

                text
            }
            finally
            {
                connection.disconnect()
            }
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

data class SavedAuth(
    val email: String,
    val refreshToken: String,
    val password: String?
)

class SavedAuthStore(context: Context)
{
    private val prefs = context.getSharedPreferences("smart_irrigation_auth", Context.MODE_PRIVATE)

    fun save(session: CloudSession, fallbackPassword: String? = null)
    {
        val editor = prefs.edit()
            .putString("email", session.email.trim())
            .putString("refresh_token", session.refreshToken)

        if (session.refreshToken.isBlank() && !fallbackPassword.isNullOrBlank())
        {
            editor.putString("password", fallbackPassword)
        }
        else
        {
            editor.remove("password")
        }

        editor
            .apply()
    }

    fun load(): SavedAuth?
    {
        val email = prefs.getString("email", null)
        val refreshToken = prefs.getString("refresh_token", "").orEmpty()
        val password = prefs.getString("password", null)

        return if (email.isNullOrBlank() || (refreshToken.isBlank() && password.isNullOrBlank()))
        {
            null
        }
        else
        {
            SavedAuth(email, refreshToken, password)
        }
    }

    fun clear()
    {
        prefs.edit().clear().apply()
    }
}

@Composable
fun IrrigationApp(
    cloudRepository: CloudRepository,
    arduinoConnection: ArduinoWifiConnection,
    wifiScanner: WifiScanner
)
{
    val context = androidx.compose.ui.platform.LocalContext.current
    val savedAuth = remember { SavedAuthStore(context) }
    var session by remember { mutableStateOf<CloudSession?>(null) }
    var plants by remember { mutableStateOf(defaultPlants()) }
    var zones by remember { mutableStateOf(defaultZones(defaultPlants())) }
    var arduinoState by remember {
        mutableStateOf<ArduinoConnectionState>(ArduinoConnectionState.Disconnected)
    }
    var message by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val arduinoRequestMutex = remember { Mutex() }

    DisposableEffect(Unit) {
        onDispose { arduinoConnection.disconnect() }
    }

    LaunchedEffect(Unit) {
        val saved = savedAuth.load()
        if (saved != null)
        {
            try
            {
                message = "Восстанавливаем вход..."
                val restored = cloudRepository.restore(saved)
                plants = cloudRepository.loadPlants(restored)
                zones = cloudRepository.loadZones(restored, plants)
                session = restored
                savedAuth.save(restored)
                message = ""
            }
            catch (_: Exception)
            {
                savedAuth.clear()
                message = ""
            }
        }
    }

    val isArduinoConnected = arduinoState is ArduinoConnectionState.Connected

    LaunchedEffect(isArduinoConnected) {
        if (!isArduinoConnected)
        {
            return@LaunchedEffect
        }

        while (true)
        {
            delay(3000)

            try
            {
                val status = arduinoRequestMutex.withLock {
                    arduinoConnection.getStatus()
                }
                arduinoState = ArduinoConnectionState.Connected(status)
            }
            catch (e: Exception)
            {
                message = e.message ?: "Ошибка автообновления Arduino"
            }
        }
    }

    if (session == null)
    {
        AuthScreen(

            message = message,
            onLogin = { email, password ->
                scope.launch {
                    try
                    {
                        message = "Проверяем пользователя..."
                        val loggedIn = cloudRepository.login(email, password)
                        plants = cloudRepository.loadPlants(loggedIn)
                        zones = cloudRepository.loadZones(loggedIn, plants)
                        session = loggedIn
                        savedAuth.save(loggedIn, password)
                        message = ""
                    }
                    catch (e: Exception)
                    {
                        message = readableAuthError(e, "Ошибка входа")
                    }
                }
            },
            onRegister = { name, email, password ->
                scope.launch {
                    try
                    {
                        message = "Создаём пользователя..."
                        val registered = cloudRepository.register(name, email, password)
                        plants = cloudRepository.loadPlants(registered)
                        zones = cloudRepository.loadZones(registered, plants)
                        session = registered
                        savedAuth.save(registered, password)
                        message = ""
                    }
                    catch (e: Exception)
                    {
                        message = readableAuthError(e, "Ошибка регистрации")
                    }
                }
            }


        )
    }
    else
    {
        MainIrrigationScreen(
            session = session!!,
            plants = plants,
            zones = zones,
            arduinoState = arduinoState,
            message = message,
            wifiScanner = wifiScanner,
            onLoadControllerConfig = { deviceCode ->
                cloudRepository.loadControllerByCode(session!!, deviceCode)
            },
            onLogout = {
                arduinoConnection.disconnect()
                savedAuth.clear()
                session = null
                arduinoState = ArduinoConnectionState.Disconnected
                message = ""
            },
            onConnect = { ssid, password, ip ->
                message = ""
                arduinoState = ArduinoConnectionState.Connecting
                arduinoConnection.connect(
                    ssid = ssid,
                    password = password,
                    url = ip,
                    onConnected = {
                        val status = arduinoRequestMutex.withLock {
                            arduinoConnection.getStatus()
                        }
                        arduinoState = ArduinoConnectionState.Connected(status)
                    },
                    onError = { error ->
                        message = error
                        arduinoState = ArduinoConnectionState.Disconnected
                    }
                )
            },
            onDisconnect = {
                arduinoConnection.disconnect()
                arduinoState = ArduinoConnectionState.Disconnected
            },
            onRefreshArduino = {
                scope.launch {
                    try
                    {
                        val status = arduinoRequestMutex.withLock {
                            arduinoConnection.getStatus()
                        }
                        arduinoState = ArduinoConnectionState.Connected(status)
                        message = ""
                    }
                    catch (e: Exception)
                    {
                        message = e.message ?: "Ошибка обновления Arduino"
                    }
                }
            },
            onUploadCloudToArduino = {
                scope.launch {
                    try
                    {
                        val status = arduinoRequestMutex.withLock {
                            arduinoConnection.uploadZones(zones)
                        }
                        arduinoState = ArduinoConnectionState.Connected(status)
                        message = "Настройки зон загружены в Arduino"
                    }
                    catch (e: Exception)
                    {
                        message = e.message ?: "Не удалось загрузить настройки"
                    }
                }
            },
            onTogglePump = { enabled, zone ->
                scope.launch {
                    try
                    {
                        val status = arduinoRequestMutex.withLock {
                            arduinoConnection.setPump(enabled, zone)
                        }
                        arduinoState = ArduinoConnectionState.Connected(status)
                        message = if (enabled) "Полив зоны $zone включён" else "Полив остановлен"
                    }
                    catch (e: Exception)
                    {
                        message = e.message ?: "Ошибка управления насосом"
                    }
                }
            },
            onToggleMode = { auto ->
                scope.launch {
                    try
                    {
                        val status = arduinoRequestMutex.withLock {
                            arduinoConnection.setMode(auto)
                        }
                        arduinoState = ArduinoConnectionState.Connected(status)
                        message = if (auto) "Автоматический режим включён" else "Ручной режим включён"
                    }
                    catch (e: Exception)
                    {
                        message = e.message ?: "Ошибка режима"
                    }
                }
            },
            onZoneChange = { updated ->
                zones = zones.map { if (it.zoneNumber == updated.zoneNumber) updated else it }
                scope.launch {
                    try
                    {
                        cloudRepository.saveZone(session!!, updated)
                        message = "Зона ${updated.zoneNumber} сохранена"
                    }
                    catch (e: Exception)
                    {
                        message = e.message ?: "Ошибка сохранения зоны"
                    }
                }
            }
        )
    }
}

fun isValidEmail(value: String): Boolean
{
    return Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()
}

fun readableAuthError(e: Exception, defaultText: String): String
{
    val errorText = e.message.orEmpty()

    return when
    {
        errorText.contains("invalid_credentials", ignoreCase = true) ||
                errorText.contains("Invalid login credentials", ignoreCase = true) ->
            "Неверный email или пароль"

        errorText.contains("over_email_send_rate_limit", ignoreCase = true) ->
            "Превышен лимит отправки email. Подождите немного или отключите подтверждение email в Supabase."

        errorText.contains("email rate limit exceeded", ignoreCase = true) ->
            "Превышен лимит отправки email. Подождите немного или отключите подтверждение email в Supabase."

        errorText.isBlank() ->
            defaultText

        else ->
            "$defaultText: $errorText"
    }
}

@Composable
fun AuthScreen(
    message: String,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit

)
{
    var isRegistration by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF6EE), Color(0xFFD8ECDD), Color(0xFFB8D8C0))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Умное орошение",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0D3B2E)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isRegistration = false
                            emailError = null
                            passwordError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isRegistration) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                            contentColor = if (!isRegistration) Color.White else Color(0xFF333333)
                        )
                    ) {
                        Text("Войти")
                    }

                    Button(
                        onClick = {
                            isRegistration = true
                            emailError = null
                            passwordError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRegistration) Color(0xFF2E7D32) else Color(0xFFE0E0E0),
                            contentColor = if (isRegistration) Color.White else Color(0xFF333333)
                        )
                    ) {
                        Text("Регистрация")
                    }
                }

                if (isRegistration)
                {
                    BlackOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Имя пользователя",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                BlackOutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    label = "Электронная почта",
                    modifier = Modifier.fillMaxWidth(),
                    isError = emailError != null,
                    supportingText = emailError
                )

                BlackOutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = "Пароль",
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError != null,
                    supportingText = passwordError
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        emailError = null
                        passwordError = null

                        val trimmedEmail = email.trim()
                        var hasValidationError = false

                        if (trimmedEmail.isBlank())
                        {
                            emailError = "Введите email"
                            hasValidationError = true
                        }
                        else if (!isValidEmail(trimmedEmail))
                        {
                            emailError = "Неверный формат email"
                            hasValidationError = true
                        }

                        if (password.isBlank())
                        {
                            passwordError = "Введите пароль"
                            hasValidationError = true
                        }

                        if (!hasValidationError)
                        {
                            if (isRegistration)
                            {
                                onRegister(name, trimmedEmail, password)
                            }
                            else
                            {
                                onLogin(trimmedEmail, password)
                            }
                        }
                    }
                ) {
                    Text(if (isRegistration) "Создать аккаунт" else "Войти")
                }

                if (message.isNotBlank())
                {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BlackOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
)
{
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Black) },
        textStyle = TextStyle(color = Color.Black),
        singleLine = true,
        isError = isError,
        supportingText = if (supportingText != null)
        {
            { Text(text = supportingText) }
        }
        else
        {
            null
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainIrrigationScreen(
    session: CloudSession,
    plants: List<PlantWaterNeed>,
    zones: List<IrrigationZone>,
    arduinoState: ArduinoConnectionState,
    message: String,
    wifiScanner: WifiScanner,
    onLoadControllerConfig: suspend (String) -> ControllerConnectionConfig,
    onLogout: () -> Unit,
    onConnect: (String, String, String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshArduino: () -> Unit,
    onUploadCloudToArduino: () -> Unit,
    onTogglePump: (Boolean, Int) -> Unit,
    onToggleMode: (Boolean) -> Unit,
    onZoneChange: (IrrigationZone) -> Unit
)
{
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4FAF5))
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Умное орошение", fontWeight = FontWeight.Bold)
                    Text(
                        text = session.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF52796F)
                    )
                }
            },
            actions = {
                TextButton(onClick = onLogout) {
                    Text("Выйти")
                }
            }
        )

        TabRow(selectedTabIndex = selectedTab) {
            listOf("Обзор", "Зоны", "Культуры", "Профиль").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (message.isNotBlank())
            {
                StatusCard(
                    title = "Сообщение",
                    body = message,
                    accent = Color(0xFF52796F)
                )
            }

            when (selectedTab)
            {
                0 -> DashboardScreen(
                    arduinoState = arduinoState,
                    zones = zones,
                    wifiScanner = wifiScanner,
                    onLoadControllerConfig = onLoadControllerConfig,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onRefreshArduino = onRefreshArduino,
                    onUploadCloudToArduino = onUploadCloudToArduino,
                    onTogglePump = onTogglePump,
                    onToggleMode = onToggleMode
                )

                1 -> ZonesScreen(
                    zones = zones,
                    plants = plants,
                    onZoneChange = onZoneChange,
                    onUploadCloudToArduino = onUploadCloudToArduino
                )

                2 -> PlantsScreen(plants = plants)

                3 -> ProfileScreen(
                    session = session,
                    arduinoState = arduinoState
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    arduinoState: ArduinoConnectionState,
    zones: List<IrrigationZone>,
    wifiScanner: WifiScanner,
    onLoadControllerConfig: suspend (String) -> ControllerConnectionConfig,
    onConnect: (String, String, String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshArduino: () -> Unit,
    onUploadCloudToArduino: () -> Unit,
    onTogglePump: (Boolean, Int) -> Unit,
    onToggleMode: (Boolean) -> Unit
)
{
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedNetwork by remember { mutableStateOf<WifiNetworkItem?>(null) }
    var networks by remember { mutableStateOf<List<WifiNetworkItem>>(emptyList()) }
    var password by remember { mutableStateOf(AppConfig.DEFAULT_ARDUINO_PASSWORD) }
    var baseUrl by remember { mutableStateOf(AppConfig.DEFAULT_ARDUINO_BASE_URL) }
    var controllerCode by remember { mutableStateOf("") }
    var selectedControllerName by remember { mutableStateOf<String?>(null) }
    var loadingControllerConfig by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = requiredWifiPermissions().all { permission ->
            permissions[permission] == true ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        scanMessage =
            if (granted)
            {
                "Разрешения выданы. Нажмите «Найти Wi-Fi контроллер» ещё раз."
            }
            else
            {
                "Нет разрешения на поиск Wi-Fi сетей. Разрешите доступ к ближайшим устройствам и геолокации в настройках приложения."
            }
    }

    ConnectionCard(
        arduinoState = arduinoState,
        selectedNetwork = selectedNetwork,
        networks = networks,
        scanning = scanning,
        scanMessage = scanMessage,
        password = password,
        baseUrl = baseUrl,
        controllerCode = controllerCode,
        selectedControllerName = selectedControllerName,
        loadingControllerConfig = loadingControllerConfig,
        onFindNetworks = {
            when
            {
                !hasWifiScanPermissions(context) -> {
                    scanMessage = "Для поиска контроллера нужно разрешение на Wi-Fi/геолокацию."
                    wifiPermissionLauncher.launch(requiredWifiPermissions())
                }

                !isLocationServiceEnabled(context) -> {
                    scanMessage = "Включите геолокацию на телефоне. Android требует её для сканирования Wi-Fi сетей."
                }

                else -> {
                    scope.launch {
                        scanning = true
                        scanMessage = "Ищем Wi-Fi сети рядом..."
                        try
                        {
                            networks = wifiScanner.scan()

                            selectedNetwork =
                                if (selectedNetwork == null || networks.none { it.ssid == selectedNetwork?.ssid })
                                {
                                    networks.firstOrNull()
                                }
                                else
                                {
                                    selectedNetwork
                                }

                            scanMessage = when
                            {
                                networks.isEmpty() ->
                                    "Сети не найдены. Проверьте, что контроллер включён и раздаёт Wi-Fi."

                                selectedNetwork != null ->
                                    "Найдено сетей: ${networks.size}. Выбрано устройство: ${selectedNetwork!!.ssid}"

                                else ->
                                    "Найдено сетей: ${networks.size}. Выберите устройство из списка."
                            }
                        }
                        catch (e: Exception)
                        {
                            scanMessage = e.message ?: "Ошибка поиска Wi-Fi"
                        }
                        finally
                        {
                            scanning = false
                        }
                    }
                }
            }
        },
        onNetworkSelected = { network ->
            selectedNetwork = network
            selectedControllerName = null
            scanMessage = "Выбрано устройство: ${network.ssid}"
        },
        onControllerCodeChange = { controllerCode = it },
        onLoadControllerByCode = {
            if (controllerCode.trim().isBlank())
            {
                scanMessage = "Введите код контроллера."
            }
            else
            {
                scope.launch {
                    loadingControllerConfig = true
                    scanMessage = "Загружаем настройки контроллера из Supabase..."
                    try
                    {
                        val config = onLoadControllerConfig(controllerCode)
                        selectedControllerName = config.deviceName
                        selectedNetwork = WifiNetworkItem(
                            ssid = config.wifiSsid,
                            secure = config.wifiPassword.isNotBlank(),
                            signalLevel = 4
                        )
                        password = config.wifiPassword
                        baseUrl = config.baseUrl
                        controllerCode = config.deviceCode
                        scanMessage = "Загружены настройки: ${config.deviceName}. Можно подключаться."
                    }
                    catch (e: Exception)
                    {
                        scanMessage = e.message ?: "Не удалось загрузить контроллер."
                    }
                    finally
                    {
                        loadingControllerConfig = false
                    }
                }
            }
        },
        onPasswordChange = { password = it },
        onBaseUrlChange = { baseUrl = it },
        onConnect = {
            val network = selectedNetwork
            if (network == null)
            {
                scanMessage = "Сначала нажмите «Найти Wi-Fi контроллер» и выберите устройство."
            }
            else
            {
                val deviceTitle = selectedControllerName ?: network.ssid
                scanMessage = "Подключаемся к устройству: $deviceTitle"
                onConnect(network.ssid, password, baseUrl)
            }
        },
        onDisconnect = onDisconnect,
        onRefreshArduino = onRefreshArduino,
        onUploadCloudToArduino = onUploadCloudToArduino
    )

    val status = (arduinoState as? ArduinoConnectionState.Connected)?.status

    SensorGrid(status = status)

    ControlCard(
        status = status,
        zones = zones,
        onTogglePump = onTogglePump,
        onToggleMode = onToggleMode
    )
}

@Composable
fun ConnectionCard(
    arduinoState: ArduinoConnectionState,
    selectedNetwork: WifiNetworkItem?,
    networks: List<WifiNetworkItem>,
    scanning: Boolean,
    scanMessage: String,
    password: String,
    baseUrl: String,
    controllerCode: String,
    selectedControllerName: String?,
    loadingControllerConfig: Boolean,
    onFindNetworks: () -> Unit,
    onNetworkSelected: (WifiNetworkItem) -> Unit,
    onControllerCodeChange: (String) -> Unit,
    onLoadControllerByCode: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshArduino: () -> Unit,
    onUploadCloudToArduino: () -> Unit
)
{
    var isConnectionBlockExpanded by remember { mutableStateOf(true) }

    val isConnected = arduinoState is ArduinoConnectionState.Connected

    LaunchedEffect(isConnected) {
        if (isConnected)
        {
            isConnectionBlockExpanded = false
        }
    }

    val selectedDeviceTitle = selectedControllerName?.takeIf { it.isNotBlank() } ?: selectedNetwork?.ssid

    val statusText = when (arduinoState)
    {
        ArduinoConnectionState.Disconnected ->
            if (selectedDeviceTitle == null)
            {
                "Контроллер не подключён"
            }
            else
            {
                "Не подключено к $selectedDeviceTitle"
            }

        ArduinoConnectionState.Connecting ->
            "Подключение к ${selectedDeviceTitle ?: "контроллеру"}..."

        is ArduinoConnectionState.Connected ->
            "Подключено к ${selectedDeviceTitle ?: "контроллеру"}"
    }

    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConnectionDot(connected = isConnected)

                Text(
                    text = statusText,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { isConnectionBlockExpanded = !isConnectionBlockExpanded }
                ) {
                    Text(if (isConnectionBlockExpanded) "Скрыть" else "Показать")
                }
            }

            if (isConnectionBlockExpanded)
            {
                Text("Подключение по коду контроллера", fontWeight = FontWeight.Bold)

                BlackOutlinedTextField(
                    value = controllerCode,
                    onValueChange = onControllerCodeChange,
                    label = "Код контроллера, например IRR-1001",
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onLoadControllerByCode,
                    enabled = controllerCode.trim().isNotBlank() && !loadingControllerConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loadingControllerConfig) "Загружаем настройки..." else "Загрузить настройки по коду")
                }

                Divider()

                Button(
                    onClick = onFindNetworks,
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (scanning) "Идёт поиск..." else "Найти Wi-Fi контроллер")
                }

                if (scanMessage.isNotBlank())
                {
                    Text(scanMessage, color = Color(0xFF52796F))
                }

                val selectedDeviceText = when
                {
                    selectedControllerName != null && selectedNetwork != null ->
                        "Выбранное устройство: $selectedControllerName • Wi-Fi: ${selectedNetwork.ssid}"

                    selectedNetwork != null ->
                        "Выбранное устройство: ${selectedNetwork.ssid}"

                    else ->
                        "Устройство не выбрано. Введите код контроллера или найдите Wi-Fi сеть Arduino."
                }

                Text(
                    text = selectedDeviceText,
                    color = Color(0xFF1F2933),
                    fontWeight = FontWeight.Bold
                )

                if (networks.isNotEmpty())
                {
                    var isNetworkListExpanded by remember(networks) { mutableStateOf(true) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Доступные Wi-Fi сети: ${networks.size}",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { isNetworkListExpanded = !isNetworkListExpanded }
                        ) {
                            Text(if (isNetworkListExpanded) "Скрыть" else "Показать")
                        }
                    }

                    if (isNetworkListExpanded)
                    {
                        networks.take(8).forEach { network ->
                            val selected = network.ssid == selectedNetwork?.ssid
                            val label = "${network.ssid}  •  сигнал ${network.signalLevel}/4" +
                                    if (network.secure) "  •  пароль" else ""

                            if (selected)
                            {
                                Button(
                                    onClick = { onNetworkSelected(network) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                            }
                            else
                            {
                                OutlinedButton(
                                    onClick = { onNetworkSelected(network) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                if (selectedNetwork?.secure == true)
                {
                    BlackOutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = "Пароль сети",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                BlackOutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = "Адрес контроллера",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        enabled = selectedNetwork != null && arduinoState !is ArduinoConnectionState.Connecting
                    ) {
                        Text("Подключить")
                    }

                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFC62828)
                        )
                    ) {
                        Text("Отключить", color = Color(0xFFC62828))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefreshArduino,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFEAF6EE),
                            contentColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Text("Обновить")
                    }

                    Button(onClick = onUploadCloudToArduino, modifier = Modifier.weight(1f)) {
                        Text("Загрузить")
                    }
                }
            }
        }
    }
}

@Composable
fun SensorGrid(status: ArduinoStatus?)
{
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorCard("Влажность почвы", "${status?.soilMoisturePercent ?: 0}%", Color(0xFF2D6A4F), Modifier.weight(1f))
            SensorCard("Температура", "${format(status?.temperatureC ?: 0.0)} °C", Color(0xFFB7791F), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorCard("Освещённость", "${status?.lightPercent ?: 0}%", Color(0xFF6B8E23), Modifier.weight(1f))
            SensorCard("Уровень воды", "${status?.waterLevelPercent ?: 0}%", Color(0xFF1D4ED8), Modifier.weight(1f))
        }
        SensorCard("Расход воды", "${format(status?.flowRateLMin ?: 0.0)} л/мин", Color(0xFF0F766E), Modifier.fillMaxWidth())
    }
}

@Composable
fun SensorCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier)
{
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF52796F), style = MaterialTheme.typography.bodySmall)
            Text(value, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun ControlCard(
    status: ArduinoStatus?,
    zones: List<IrrigationZone>,
    onTogglePump: (Boolean, Int) -> Unit,
    onToggleMode: (Boolean) -> Unit
)
{
    var selectedZone by remember { mutableIntStateOf(1) }
    val autoMode = status?.autoMode ?: true

    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Управление поливом", fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Автоматический режим", modifier = Modifier.weight(1f))
                Switch(checked = autoMode, onCheckedChange = onToggleMode)
            }

            Text("Активная зона: ${status?.activeZone ?: "-"}")
            Text("Насос: ${if (status?.pumpOn == true) "включён" else "выключен"}")

            Divider()

            Text("Выбор зоны для ручного управления")
            ZoneSelector(
                zones = zones,
                selectedZone = selectedZone,
                onSelected = { selectedZone = it }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onTogglePump(true, selectedZone) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Включить")
                }
                OutlinedButton(
                    onClick = { onTogglePump(false, selectedZone) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Выключить")
                }
            }
        }
    }
}

@Composable
fun ZoneSelector(
    zones: List<IrrigationZone>,
    selectedZone: Int,
    onSelected: (Int) -> Unit
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        zones.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { zone ->
                    val selected = zone.zoneNumber == selectedZone
                    if (selected)
                    {
                        Button(onClick = { onSelected(zone.zoneNumber) }, modifier = Modifier.weight(1f)) {
                            Text(zone.name)
                        }
                    }
                    else
                    {
                        OutlinedButton(onClick = { onSelected(zone.zoneNumber) }, modifier = Modifier.weight(1f)) {
                            Text(zone.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZonesScreen(
    zones: List<IrrigationZone>,
    plants: List<PlantWaterNeed>,
    onZoneChange: (IrrigationZone) -> Unit,
    onUploadCloudToArduino: () -> Unit
)
{
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onUploadCloudToArduino, modifier = Modifier.fillMaxWidth()) {
            Text("Загрузить настройки всех зон в Arduino")
        }

        zones.forEach { zone ->
            ZoneCard(
                zone = zone,
                plants = plants,
                onZoneChange = onZoneChange
            )
        }
    }
}

@Composable
fun ZoneCard(
    zone: IrrigationZone,
    plants: List<PlantWaterNeed>,
    onZoneChange: (IrrigationZone) -> Unit
)
{
    var selectedPlantIndex by remember(zone.zoneNumber, zone.plant.id) {
        mutableIntStateOf(plants.indexOfFirst { it.id == zone.plant.id }.coerceAtLeast(0))
    }
    var waterNeed by remember(zone.zoneNumber, zone.customWaterNeed) {
        mutableIntStateOf(zone.customWaterNeed)
    }

    val plant = plants[selectedPlantIndex]

    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(zone.name, fontWeight = FontWeight.Bold)
                    Text("${plant.name}: влаголюбивость $waterNeed из 10", color = Color(0xFF52796F))
                }
                Switch(
                    checked = zone.enabled,
                    onCheckedChange = { checked ->
                        onZoneChange(zone.copy(enabled = checked))
                    }
                )
            }

            LinearProgressIndicator(
                progress = waterNeed / 10f,
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2D6A4F),
                trackColor = Color(0xFFD8ECDD)
            )

            Text("Степень влаголюбивости: $waterNeed")
            Slider(
                value = waterNeed.toFloat(),
                onValueChange = { value -> waterNeed = value.toInt().coerceIn(1, 10) },
                onValueChangeFinished = {
                    onZoneChange(zone.copy(plant = plant, customWaterNeed = waterNeed))
                },
                valueRange = 1f..10f,
                steps = 8
            )

            Text("Культура")
            plants.chunked(2).forEachIndexed { rowIndex, rowPlants ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPlants.forEachIndexed { columnIndex, item ->
                        val index = rowIndex * 2 + columnIndex
                        val selected = item.id == plant.id
                        if (selected)
                        {
                            Button(
                                onClick = {
                                    selectedPlantIndex = index
                                    waterNeed = item.waterNeed
                                    onZoneChange(zone.copy(plant = item, customWaterNeed = item.waterNeed))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(item.name)
                            }
                        }
                        else
                        {
                            OutlinedButton(
                                onClick = {
                                    selectedPlantIndex = index
                                    waterNeed = item.waterNeed
                                    onZoneChange(zone.copy(plant = item, customWaterNeed = item.waterNeed))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(item.name)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlantsScreen(plants: List<PlantWaterNeed>)
{
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Справочник влаголюбивости", fontWeight = FontWeight.Bold)
        Text("Шкала от 1 до 10: 1 — низкая потребность в воде, 10 — очень высокая.")

        plants.forEach { plant ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plant.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${plant.waterNeed}/10", color = Color(0xFF2D6A4F), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = plant.waterNeed / 10f,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2D6A4F),
                        trackColor = Color(0xFFD8ECDD)
                    )
                    Text(plant.description, color = Color(0xFF52796F))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    session: CloudSession,
    arduinoState: ArduinoConnectionState
)
{
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard("Пользователь", "${session.displayName}\n${session.email}", Color(0xFF2D6A4F))
        StatusCard(
            "Контроллер",
            if (arduinoState is ArduinoConnectionState.Connected)
            {
                "Arduino подключена, данные датчиков доступны"
            }
            else
            {
                "Arduino не подключена"
            },
            Color(0xFF1D4ED8)
        )
    }
}

@Composable
fun StatusCard(title: String, body: String, accent: Color)
{
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = accent)
            Text(body)
        }
    }
}

@Composable
fun ConnectionDot(connected: Boolean)
{
    val color = if (connected) Color(0xFF2D6A4F) else Color(0xFFB91C1C)
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape)
    )
}

fun defaultPlants(): List<PlantWaterNeed> =
    listOf(
        PlantWaterNeed("tomato", "Помидор", 7, "Регулярный умеренно-высокий полив."),
        PlantWaterNeed("carrot", "Морковь", 3, "Умеренная потребность, избыток влаги нежелателен."),
        PlantWaterNeed("eggplant", "Баклажан", 8, "Требует стабильной влажности почвы."),
        PlantWaterNeed("beans", "Бобы", 5, "Средняя потребность в воде."),
        PlantWaterNeed("peas", "Горох", 5, "Средняя потребность, важен полив в период роста."),
        PlantWaterNeed("zucchini", "Кабачок", 8, "Высокая влаголюбивость, особенно в жару."),
        PlantWaterNeed("daikon", "Дайкон", 4, "Умеренная потребность в воде."),
        PlantWaterNeed("cabbage", "Капуста", 9, "Очень высокая потребность в воде."),
        PlantWaterNeed("potato", "Картофель", 5, "Средняя потребность, критичен период клубнеобразования."),
        PlantWaterNeed("onion", "Лук", 5, "Средняя потребность, переувлажнение нежелательно."),
        PlantWaterNeed("cucumber", "Огурец", 7, "Высокая потребность в регулярном поливе."),
        PlantWaterNeed("pumpkin", "Тыква", 7, "Высокая потребность при росте плодов."),
        PlantWaterNeed("beet", "Свёкла", 5, "Средняя потребность в воде."),
        PlantWaterNeed("pepper", "Перец", 8, "Высокая потребность, чувствителен к пересыханию."),
        PlantWaterNeed("watermelon", "Арбуз", 9, "Очень высокая потребность на раннем этапе роста.")
    )

fun defaultZones(plants: List<PlantWaterNeed>): List<IrrigationZone> =
    (1..4).map { number ->
        val plant = plants[(number - 1) % plants.size]
        IrrigationZone(
            zoneNumber = number,
            name = "Зона $number",
            plant = plant,
            customWaterNeed = plant.waterNeed,
            enabled = true
        )
    }

fun format(value: Double): String = String.format(Locale.US, "%.1f", value)
