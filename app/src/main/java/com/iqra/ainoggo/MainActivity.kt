package com.iqra.ainoggo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.camera.core.AspectRatio
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.iqra.ainoggo.ui.theme.AinoggoTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

// Define color scheme
val GreenPrimary = Color(0xFF1B5E20)
val GreenSecondary = Color(0xFF388E3C)
val GreenBackground = Color(0xFFE8F5E9)
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)

// API Service Interface
interface ApiService {
    @Multipart
    @POST("/api/document/analyze")
    fun analyzeDocument(
        @Part file: MultipartBody.Part,
        @Part("document_type") documentType: RequestBody
    ): Call<DocumentAnalysisResponse>

    @POST("/api/query")
    fun submitQuery(@Body request: LegalQueryRequest): Call<LegalQueryResponse>
}

// API Data Classes
data class DocumentAnalysisResponse(
    val success: Boolean,
    val document_type: String,
    val analysis: DocumentAnalysis
)

data class DocumentAnalysis(
    val extracted_text: String,
    val content_analysis: ContentAnalysis,
    val identified_issues: List<String>,
    val recommendations: List<String>,
    val gemini_raw_analysis: String
)

data class ContentAnalysis(
    val document_type: String,
    val key_elements: List<String>
)

data class LegalQueryRequest(
    val question: String,
    val case_type: String
)

data class LegalQueryResponse(
    val success: Boolean,
    val question: String,
    val case_type: String,
    val answer: String
)

// ViewModel for Document Analysis
class DocumentViewModel : ViewModel() {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://ainoggo-server.onrender.com")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    var documentUri by mutableStateOf<Uri?>(null)
    var isLoading by mutableStateOf(false)
    var analysisResult by mutableStateOf<DocumentAnalysisResponse?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    fun analyzeDocument(uri: Uri, context: Context) {
        isLoading = true
        errorMessage = null
        documentUri = uri

        // Convert Uri to File
        val file = uriToFile(uri, context)

        // Create MultipartBody.Part
        val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

        // Create document_type RequestBody
        val documentType = "general".toRequestBody("text/plain".toMediaTypeOrNull())

        // Make API call
        apiService.analyzeDocument(filePart, documentType).enqueue(object : Callback<DocumentAnalysisResponse> {
            override fun onResponse(call: Call<DocumentAnalysisResponse>, response: Response<DocumentAnalysisResponse>) {
                isLoading = false
                if (response.isSuccessful) {
                    analysisResult = response.body()
                } else {
                    errorMessage = "ত্রুটি ঘটেছে: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<DocumentAnalysisResponse>, t: Throwable) {
                isLoading = false
                errorMessage = "সংযোগ ত্রুটি: ${t.message}"
            }
        })
    }

    private fun uriToFile(uri: Uri, context: Context): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("document", ".jpg", context.cacheDir)
        FileOutputStream(tempFile).use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
        return tempFile
    }
}

// ViewModel for Legal Query
class LegalQueryViewModel : ViewModel() {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://ainoggo-server.onrender.com")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    var question by mutableStateOf("")
    var caseType by mutableStateOf("general")
    var isLoading by mutableStateOf(false)
    var queryResult by mutableStateOf<LegalQueryResponse?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    // List of case types
    val caseTypes = listOf("general", "family", "property", "criminal", "business")

    fun submitQuery() {
        if (question.isBlank()) {
            errorMessage = "আপনার প্রশ্ন লিখুন"
            return
        }

        isLoading = true
        errorMessage = null

        val request = LegalQueryRequest(question, caseType)

        apiService.submitQuery(request).enqueue(object : Callback<LegalQueryResponse> {
            override fun onResponse(call: Call<LegalQueryResponse>, response: Response<LegalQueryResponse>) {
                isLoading = false
                if (response.isSuccessful) {
                    queryResult = response.body()
                } else {
                    errorMessage = "ত্রুটি ঘটেছে: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<LegalQueryResponse>, t: Throwable) {
                isLoading = false
                errorMessage = "সংযোগ ত্রুটি: ${t.message}"
            }
        })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AinoggoTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("ডকুমেন্ট বিশ্লেষণ", "আইনি প্রশ্ন")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "আইনজ্ঞ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GreenPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(GreenBackground)
        ) {
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = GreenPrimary,
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index }
                    )
                }
            }

            when (tabIndex) {
                0 -> DocumentTab()
                1 -> LegalQueryTab()
            }
        }
    }
}

@Composable
fun DocumentTab() {
    val context = LocalContext.current
    val viewModel: DocumentViewModel = viewModel()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val hasPermissions = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermissions.value = isGranted
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.analyzeDocument(it, context)
        }
    }

    var showCamera by remember { mutableStateOf(false) }
    var resetState by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (showCamera && hasPermissions.value) {
            // Full screen camera
            CameraView(
                cameraExecutor = cameraExecutor,
                onPhotoTaken = { uri ->
                    showCamera = false
                    viewModel.analyzeDocument(uri, context)
                },
                onDismiss = { showCamera = false }
            )
        } else {
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Reset image and response if resetState is true
                if (resetState) {
                    viewModel.documentUri = null
                    viewModel.analysisResult = null
                    resetState = false
                }

                // Upload section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 4.dp
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "ডকুমেন্ট আপলোড করুন",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = {
                                filePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = "Upload",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "গ্যালারি থেকে নির্বাচন করুন",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show selected image preview
                viewModel.documentUri?.let {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 2.dp
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(it),
                                contentDescription = "Selected Document",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Overlay for better visibility
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.3f)
                                            )
                                        )
                                    )
                            )

                            Button(
                                onClick = { resetState = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Try Again",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Loading indicator
                if (viewModel.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                    }
                }

                // Error message
                viewModel.errorMessage?.let {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Analysis result
                viewModel.analysisResult?.let { result ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = 4.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                "বিশ্লেষণ ফলাফল",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                thickness = 1.dp
                            )

                            Text(
                                "ডকুমেন্ট ধরন: ${result.analysis.content_analysis.document_type}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "মূল উপাদান:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp)
                            ) {
                                result.analysis.content_analysis.key_elements.forEach { element ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .align(Alignment.CenterVertically)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            element,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "চিহ্নিত সমস্যা:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp)
                            ) {
                                result.analysis.identified_issues.forEach { issue ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.error, CircleShape)
                                                .align(Alignment.CenterVertically)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            issue,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "সুপারিশ:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp)
                            ) {
                                result.analysis.recommendations.forEach { recommendation ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                                                .align(Alignment.CenterVertically)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            recommendation,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating action button for camera
            FloatingActionButton(
                onClick = {
                    if (hasPermissions.value) {
                        showCamera = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "Camera",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun CameraView(
    cameraExecutor: Executor,
    onPhotoTaken: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    // Full screen camera view
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Camera UI overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // Top bar with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Camera",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom capture section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                // Capture button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Color.White.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .border(4.dp, Color.White, CircleShape)
                        .clickable {
                            val photoFile = File(
                                context.cacheDir,
                                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                            )

                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                cameraExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        val savedUri = Uri.fromFile(photoFile)
                                        onPhotoTaken(savedUri)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Toast.makeText(
                                            context,
                                            "ছবি তোলার সময় ত্রুটি: ${exception.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            // Camera guidelines (optional)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                val strokeWidth = 2.dp.toPx()
                val cornerLength = 40.dp.toPx()

                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(0f, size.height / 3),
                    end = Offset(size.width, size.height / 3),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(0f, 2 * size.height / 3),
                    end = Offset(size.width, 2 * size.height / 3),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(size.width / 3, 0f),
                    end = Offset(size.width / 3, size.height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(2 * size.width / 3, 0f),
                    end = Offset(2 * size.width / 3, size.height),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

@Composable
fun LegalQueryTab() {
    val viewModel: LegalQueryViewModel = viewModel()
    // State for dialog visibility
    var showDialog by remember { mutableStateOf(false) }

    // Main scroll state for the entire screen
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Input Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 4.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "আইনি প্রশ্ন জিজ্ঞাসা করুন",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Case Type Selector - Using Dialog approach
                val options = listOf("সাধারণ", "পারিবারিক", "সম্পত্তি", "ফৌজদারি", "ব্যবসা")
                val caseTypeValues = listOf("general", "family", "property", "criminal", "business")

                val selectedOptionText = when (viewModel.caseType) {
                    "general" -> "সাধারণ"
                    "family" -> "পারিবারিক"
                    "property" -> "সম্পত্তি"
                    "criminal" -> "ফৌজদারি"
                    "business" -> "ব্যবসা"
                    else -> "সাধারণ"
                }

                Text(
                    "বিষয় নির্বাচন করুন:",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom selector that opens a dialog
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDialog = true },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GreenSecondary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedOptionText,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select",
                            tint = GreenPrimary
                        )
                    }
                }

                // Dialog for selection
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = {
                            Text(
                                "বিষয় নির্বাচন করুন",
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        },
                        text = {
                            Column {
                                options.forEachIndexed { index, label ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.caseType = caseTypeValues[index]
                                                showDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = viewModel.caseType == caseTypeValues[index],
                                            onClick = {
                                                viewModel.caseType = caseTypeValues[index]
                                                showDialog = false
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = GreenPrimary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            label,
                                            fontWeight = if (viewModel.caseType == caseTypeValues[index])
                                                FontWeight.Medium else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDialog = false }) {
                                Text("বাতিল", color = GreenPrimary)
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Question Input
                OutlinedTextField(
                    value = viewModel.question,
                    onValueChange = { viewModel.question = it },
                    label = { Text("আপনার প্রশ্ন লিখুন") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = GreenSecondary.copy(alpha = 0.5f),
                        cursorColor = GreenPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.submitQuery() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Text(
                        "জিজ্ঞাসা করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Loading indicator
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = GreenPrimary,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
            }
        }

        // Error message
        viewModel.errorMessage?.let {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = it,
                        color = Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Query result
        viewModel.queryResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 4.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "উত্তর",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenPrimary
                        )

                        // Reset button
                        OutlinedButton(
                            onClick = {
                                viewModel.question = ""
                                viewModel.queryResult = null
                                viewModel.errorMessage = null
                            },
                            border = BorderStroke(1.dp, GreenSecondary),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = GreenSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = GreenSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("রিসেট করুন")
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = GreenSecondary.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )

                    Text(
                        text = result.answer.ifEmpty { "কোন উত্তর পাওয়া যায়নি" },
                        color = TextPrimary,
                        lineHeight = 24.sp
                    )
                }
            }

            // Add another reset button at the bottom for convenience if there's a long answer
            OutlinedButton(
                onClick = {
                    viewModel.question = ""
                    viewModel.queryResult = null
                    viewModel.errorMessage = null
                },
                border = BorderStroke(1.dp, GreenPrimary),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = GreenPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = GreenPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "নতুন প্রশ্ন জিজ্ঞাসা করুন",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Add some bottom padding to ensure content doesn't get cut off when scrolling
        Spacer(modifier = Modifier.height(24.dp))
    }
}