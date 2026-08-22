package com.jmprieur.drawinggrid

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DrawingGridTheme { DrawingGridApp() } }
    }
}

@Composable
private fun DrawingGridApp(viewModel: DrawingGridViewModel = viewModel()) {
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { viewModel.selectPhoto(it.toString()) }
    }
    DrawingGridScreen(
        photoUri = photoUri,
        settings = settings,
        onChoosePhoto = { picker.launch(PickVisualMediaRequest(ImageOnly)) },
        onSettingsChange = viewModel::updateSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingGridScreen(
    photoUri: String?,
    settings: GridSettings,
    onChoosePhoto: () -> Unit,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Drawing Grid") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (photoUri == null) {
            EmptyPhotoState(Modifier.padding(padding), onChoosePhoto)
        } else {
            PhotoEditor(
                modifier = Modifier.padding(padding),
                uri = photoUri,
                settings = settings,
                onChoosePhoto = onChoosePhoto,
                onSettingsChange = onSettingsChange,
            )
        }
    }
}

@Composable
private fun EmptyPhotoState(modifier: Modifier, onChoosePhoto: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Choose a reference photo", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Your photo stays on this device. Add a grid to help transfer proportions to paper or canvas.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onChoosePhoto, modifier = Modifier.testTag("choose_photo")) {
            Text("Choose photo")
        }
    }
}

@Composable
private fun PhotoEditor(
    modifier: Modifier,
    uri: String,
    settings: GridSettings,
    onChoosePhoto: () -> Unit,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        PhotoArea(uri, settings, Modifier.weight(1f).fillMaxWidth())
        GridControls(
            settings = settings,
            onChoosePhoto = onChoosePhoto,
            onSettingsChange = onSettingsChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhotoArea(uri: String, settings: GridSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri)))
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds()) {
        bitmap?.let { image ->
            FittedImageWithGrid(image, settings, Modifier.fillMaxSize())
        } ?: Text(
            "Loading photo…",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FittedImageWithGrid(image: ImageBitmap, settings: GridSettings, modifier: Modifier) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val bounds = GridGeometry.fittedBounds(
        containerSize.width.toFloat(), containerSize.height.toFloat(), image.width.toFloat(), image.height.toFloat(),
    )
    Canvas(modifier.onSizeChanged { containerSize = it }.testTag("photo_area")) {
        bounds?.let { fitted ->
            drawImage(
                image = image,
                dstOffset = androidx.compose.ui.unit.IntOffset(fitted.left.toInt(), fitted.top.toInt()),
                dstSize = IntSize(fitted.width.toInt(), fitted.height.toInt()),
            )
            if (settings.visible) {
                clipRect(fitted.left, fitted.top, fitted.right, fitted.bottom) {
                    GridGeometry.lines(fitted, settings.rows, settings.columns).forEach { line ->
                        drawLine(
                            color = Color(settings.color).copy(alpha = settings.opacity),
                            start = androidx.compose.ui.geometry.Offset(line.startX, line.startY),
                            end = androidx.compose.ui.geometry.Offset(line.endX, line.endY),
                            strokeWidth = settings.thickness,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridControls(
    settings: GridSettings,
    onChoosePhoto: () -> Unit,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show grid", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(
                    checked = settings.visible,
                    onCheckedChange = { value -> onSettingsChange { it.copy(visible = value) } },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onChoosePhoto) { Text("Replace photo") }
            }
            SettingSlider("Rows: ${settings.rows}", settings.rows.toFloat(), 1f..12f, steps = 10) {
                onSettingsChange { current -> current.copy(rows = it.toInt()) }
            }
            SettingSlider("Columns: ${settings.columns}", settings.columns.toFloat(), 1f..12f, steps = 10) {
                onSettingsChange { current -> current.copy(columns = it.toInt()) }
            }
            SettingSlider("Line opacity: ${(settings.opacity * 100).toInt()}%", settings.opacity, 0.2f..1f, steps = 7) {
                onSettingsChange { current -> current.copy(opacity = it) }
            }
            SettingSlider("Line thickness: ${"%.1f".format(settings.thickness)} px", settings.thickness, 1f..6f, steps = 4) {
                onSettingsChange { current -> current.copy(thickness = it) }
            }
            Text("Line color", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("White" to 0xFFFFFFFFL, "Black" to 0xFF000000L, "Yellow" to 0xFFFFD740L, "Red" to 0xFFFF5252L).forEach { (name, color) ->
                    FilterChip(
                        selected = settings.color == color,
                        onClick = { onSettingsChange { current -> current.copy(color = color) } },
                        label = { Text(name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Text(label)
    Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
}

@Composable
fun DrawingGridTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        androidx.compose.material3.darkColorScheme()
    } else {
        androidx.compose.material3.lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    DrawingGridTheme { DrawingGridScreen(null, GridSettings(), {}, {}) }
}
