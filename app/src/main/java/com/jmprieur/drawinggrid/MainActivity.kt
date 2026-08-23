package com.jmprieur.drawinggrid

import android.graphics.ImageDecoder
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DrawingGridTheme { DrawingGridApp() } }
    }
}

@Composable
private fun DrawingGridApp(viewModel: DrawingGridViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val perspective by viewModel.perspective.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { viewModel.selectPhoto(it.toString()) }
    }
    val saver = rememberLauncherForActivityResult(CreateDocument("image/png")) { destination ->
        val source = photoUri?.let(Uri::parse)
        if (destination != null && source != null) {
            coroutineScope.launch {
                val result = GridImageExporter.export(context, source, destination, settings)
                Toast.makeText(
                    context,
                    if (result.isSuccess) "Saved picture with grid" else "Could not save picture",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    DrawingGridScreen(
        photoUri = photoUri,
        settings = settings,
        perspective = perspective,
        onChoosePhoto = { picker.launch(PickVisualMediaRequest(ImageOnly)) },
        onSavePhoto = {
            photoUri?.let { uri ->
                coroutineScope.launch {
                    val fileName = GridImageExporter.suggestedFileName(context, Uri.parse(uri), settings)
                    saver.launch(fileName)
                }
            }
        },
        onSettingsChange = viewModel::updateSettings,
        onPerspectiveChange = viewModel::updatePerspective,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingGridScreen(
    photoUri: String?,
    settings: GridSettings,
    onChoosePhoto: () -> Unit,
    onSavePhoto: () -> Unit,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
    perspective: PerspectiveSettings = PerspectiveSettings(),
    onPerspectiveChange: ((PerspectiveSettings) -> PerspectiveSettings) -> Unit = {},
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
                perspective = perspective,
                onChoosePhoto = onChoosePhoto,
                onSavePhoto = onSavePhoto,
                onSettingsChange = onSettingsChange,
                onPerspectiveChange = onPerspectiveChange,
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
    perspective: PerspectiveSettings,
    onChoosePhoto: () -> Unit,
    onSavePhoto: () -> Unit,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
    onPerspectiveChange: ((PerspectiveSettings) -> PerspectiveSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = decodePhoto(context, uri)
    }
    var mode by remember { mutableStateOf(EditorMode.GRID) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var fitPerspectiveRequest by remember { mutableIntStateOf(0) }
    var detecting by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        PhotoArea(
            bitmap = bitmap,
            settings = settings,
            perspective = perspective,
            mode = mode,
            fitRequest = fitRequest,
            fitPerspectiveRequest = fitPerspectiveRequest,
            onPerspectiveChange = onPerspectiveChange,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        EditorControls(
            mode = mode,
            onModeChange = { mode = it },
            settings = settings,
            perspective = perspective,
            detecting = detecting,
            onChoosePhoto = onChoosePhoto,
            onSavePhoto = onSavePhoto,
            onSettingsChange = onSettingsChange,
            onPerspectiveChange = onPerspectiveChange,
            onFitImage = { fitRequest++ },
            onFitPerspective = { fitPerspectiveRequest++ },
            onDetect = {
                val source = bitmap ?: return@EditorControls
                scope.launch {
                    detecting = true
                    val points = withContext(Dispatchers.Default) { VanishingPointDetector.detect(source) }
                    onPerspectiveChange { it.copy(points = points, anchor = null) }
                    detecting = false
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
        )
    }
}

@Composable
private fun PhotoArea(
    bitmap: Bitmap?,
    settings: GridSettings,
    perspective: PerspectiveSettings,
    mode: EditorMode,
    fitRequest: Int,
    fitPerspectiveRequest: Int,
    onPerspectiveChange: ((PerspectiveSettings) -> PerspectiveSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clipToBounds()) {
        bitmap?.let { image ->
            ImageWorkspace(
                image.asImageBitmap(),
                settings,
                perspective,
                mode,
                fitRequest,
                fitPerspectiveRequest,
                onPerspectiveChange,
                Modifier.fillMaxSize(),
            )
        } ?: Text(
            "Loading photo…",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private suspend fun decodePhoto(context: android.content.Context, uri: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) {
                decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

@Composable
private fun ImageWorkspace(
    image: ImageBitmap,
    settings: GridSettings,
    perspective: PerspectiveSettings,
    mode: EditorMode,
    fitRequest: Int,
    fitPerspectiveRequest: Int,
    onPerspectiveChange: ((PerspectiveSettings) -> PerspectiveSettings) -> Unit,
    modifier: Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var transform by remember(image) { mutableStateOf(ViewTransform()) }
    val latestTransform by rememberUpdatedState(transform)
    val currentPerspective by rememberUpdatedState(perspective)
    val imageBounds = GridGeometry.fittedBounds(
        containerSize.width.toFloat(), containerSize.height.toFloat(), image.width.toFloat(), image.height.toFloat(),
    )
    LaunchedEffect(fitRequest) { transform = ViewTransform() }
    LaunchedEffect(fitPerspectiveRequest, imageBounds, perspective.points) {
        imageBounds?.let {
            transform = PerspectiveGeometry.fitPerspective(
                containerSize.width.toFloat(),
                containerSize.height.toFloat(),
                it,
                perspective.points.filter { point -> point.enabled }.map { point -> point.position },
            )
        }
    }
    val gestureModifier = modifier
        .onSizeChanged { containerSize = it }
        .pointerInput(imageBounds, mode) {
            if (imageBounds != null) {
                awaitEachGesture {
                    val gesturePerspective = currentPerspective
                    val down = awaitFirstDown()
                    val originalTransform = latestTransform
                    var currentTransform = originalTransform
                    var moved = false
                    var totalPan = Offset.Zero
                    val draggedPoint = if (mode == EditorMode.PERSPECTIVE) {
                        gesturePerspective.points.indexOfFirst {
                            val workspace = PerspectiveGeometry.toWorkspace(
                                it.position,
                                imageBounds,
                                originalTransform,
                            )
                            it.enabled && PerspectiveGeometry.distance(
                                PerspectiveGeometry.hitTestPosition(
                                    workspace,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                ),
                                Point2(down.position.x, down.position.y),
                            ) < 48.dp.toPx()
                        }
                    } else {
                        -1
                    }
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        totalPan += pan
                        moved = moved || pressed.size > 1 || totalPan.getDistance() > viewConfiguration.touchSlop
                        if (moved) {
                            if (draggedPoint >= 0 && pressed.size == 1) {
                                val normalized = PerspectiveGeometry.movePoint(
                                    gesturePerspective.points[draggedPoint].position,
                                    Point2(totalPan.x, totalPan.y),
                                    imageBounds,
                                    originalTransform,
                                )
                                onPerspectiveChange {
                                    it.copy(points = it.points.mapIndexed { index, point ->
                                        if (index == draggedPoint) point.copy(position = normalized) else point
                                    })
                                }
                            } else {
                                val centroid = event.calculateCentroid()
                                currentTransform = PerspectiveGeometry.applyGesture(
                                    currentTransform,
                                    Point2(centroid.x, centroid.y),
                                    Point2(pan.x, pan.y),
                                    zoom,
                                )
                                transform = currentTransform
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!moved && mode == EditorMode.PERSPECTIVE) {
                        val tap = down.position
                        val points = gesturePerspective.points.map {
                            PerspectiveGeometry.toWorkspace(it.position, imageBounds, originalTransform)
                        }
                        val indicatorIndex = points.indexOfFirst { point ->
                            PerspectiveGeometry.edgeIndicator(point, size.width.toFloat(), size.height.toFloat())
                                ?.let {
                                    PerspectiveGeometry.distance(it, Point2(tap.x, tap.y)) < 48.dp.toPx()
                                } == true
                        }
                        if (indicatorIndex >= 0) {
                            val point = points[indicatorIndex]
                            transform = originalTransform.copy(
                                offsetX = originalTransform.offsetX + size.width / 2f - point.x,
                                offsetY = originalTransform.offsetY + size.height / 2f - point.y,
                            )
                        } else {
                            val displayed = PerspectiveGeometry.transformedBounds(imageBounds, originalTransform)
                            if (tap.x in displayed.left..displayed.right && tap.y in displayed.top..displayed.bottom) {
                                val anchor = PerspectiveGeometry.toNormalized(
                                    Point2(tap.x, tap.y),
                                    imageBounds,
                                    originalTransform,
                                )
                                onPerspectiveChange { it.copy(anchor = anchor) }
                            }
                        }
                    }
                }
            }
        }
        .testTag("photo_area")
    Canvas(gestureModifier) {
        imageBounds?.let { fitted ->
            val displayed = PerspectiveGeometry.transformedBounds(fitted, transform)
            drawImage(
                image = image,
                dstOffset = androidx.compose.ui.unit.IntOffset(displayed.left.roundToInt(), displayed.top.roundToInt()),
                dstSize = IntSize(displayed.width.roundToInt(), displayed.height.roundToInt()),
            )
            if (settings.visible) {
                clipRect(displayed.left, displayed.top, displayed.right, displayed.bottom) {
                    GridGeometry.lines(displayed, settings.rows, settings.columns).forEach { line ->
                        drawLine(
                            color = Color(settings.color).copy(alpha = settings.opacity),
                            start = androidx.compose.ui.geometry.Offset(line.startX, line.startY),
                            end = androidx.compose.ui.geometry.Offset(line.endX, line.endY),
                            strokeWidth = settings.thickness,
                        )
                    }
                }
            }
            if (perspective.visible) {
                val anchor = perspective.anchor?.let {
                    PerspectiveGeometry.toWorkspace(it, fitted, transform)
                }
                perspective.points.forEachIndexed { index, point ->
                    if (!point.enabled) return@forEachIndexed
                    val target = PerspectiveGeometry.toWorkspace(point.position, fitted, transform)
                    if (anchor != null) {
                        drawLine(
                            color = Color(perspective.color).copy(alpha = perspective.opacity),
                            start = Offset(anchor.x, anchor.y),
                            end = Offset(target.x, target.y),
                            strokeWidth = perspective.thickness,
                        )
                    }
                    val edge = PerspectiveGeometry.edgeIndicator(target, size.width, size.height)
                    val marker = PerspectiveGeometry.hitTestPosition(target, size.width, size.height)
                    drawCircle(
                        color = Color(perspective.color).copy(alpha = perspective.opacity),
                        radius = if (edge == null) 10f else 13f,
                        center = Offset(marker.x, marker.y),
                    )
                    if (edge != null) {
                        val direction = Offset(target.x - marker.x, target.y - marker.y)
                        val length = direction.getDistance().coerceAtLeast(1f)
                        drawLine(
                            color = Color(perspective.color),
                            start = Offset(marker.x, marker.y),
                            end = Offset(marker.x + direction.x / length * 18f, marker.y + direction.y / length * 18f),
                            strokeWidth = 3f,
                        )
                        drawCircle(
                            color = Color(perspective.color).copy(alpha = 0.35f),
                            radius = 17f + index * 3f,
                            center = Offset(marker.x, marker.y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2f),
                        )
                        val distance = hypot(
                            (point.position.x - 0.5f).toDouble(),
                            (point.position.y - 0.5f).toDouble(),
                        ).toFloat()
                        drawContext.canvas.nativeCanvas.drawText(
                            "VP${index + 1} · ${"%.1f".format(distance)}×",
                            marker.x.coerceIn(4f, size.width - 72f),
                            (marker.y - 18f).coerceIn(16f, size.height - 4f),
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = perspective.color.toInt()
                                textSize = 13.dp.toPx()
                            },
                        )
                    }
                }
                anchor?.let {
                    drawCircle(Color(perspective.color), radius = 7f, center = Offset(it.x, it.y))
                }
            }
        }
    }
}

private enum class EditorMode { GRID, PERSPECTIVE }

@Composable
private fun EditorControls(
    mode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    settings: GridSettings,
    perspective: PerspectiveSettings,
    detecting: Boolean,
    onChoosePhoto: () -> Unit,
    onSavePhoto: () -> Unit,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
    onPerspectiveChange: ((PerspectiveSettings) -> PerspectiveSettings) -> Unit,
    onFitImage: () -> Unit,
    onFitPerspective: () -> Unit,
    onDetect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == EditorMode.GRID,
                    onClick = { onModeChange(EditorMode.GRID) },
                    label = { Text("Grid") },
                    modifier = Modifier.testTag("grid_tab"),
                )
                FilterChip(
                    selected = mode == EditorMode.PERSPECTIVE,
                    onClick = { onModeChange(EditorMode.PERSPECTIVE) },
                    label = { Text("Perspective") },
                    modifier = Modifier.testTag("perspective_tab"),
                )
            }
            if (mode == EditorMode.GRID) {
                GridSettingsControls(settings, onSettingsChange)
            } else {
                PerspectiveControls(
                    perspective,
                    detecting,
                    onPerspectiveChange,
                    onFitImage,
                    onFitPerspective,
                    onDetect,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.weight(1f)) {
                    Text("Replace photo")
                }
                Button(
                    onClick = onSavePhoto,
                    modifier = Modifier.weight(1f).testTag("save_grid"),
                ) {
                    Text("Save with grid")
                }
            }
        }
    }
}

@Composable
private fun GridSettingsControls(
    settings: GridSettings,
    onSettingsChange: ((GridSettings) -> GridSettings) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show grid", Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(
            checked = settings.visible,
            onCheckedChange = { value -> onSettingsChange { it.copy(visible = value) } },
        )
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

@Composable
private fun PerspectiveControls(
    perspective: PerspectiveSettings,
    detecting: Boolean,
    onPerspectiveChange: ((PerspectiveSettings) -> PerspectiveSettings) -> Unit,
    onFitImage: () -> Unit,
    onFitPerspective: () -> Unit,
    onDetect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show perspective", Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(
            checked = perspective.visible,
            onCheckedChange = { value -> onPerspectiveChange { it.copy(visible = value) } },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onFitImage, modifier = Modifier.weight(1f)) { Text("Fit image") }
        OutlinedButton(onClick = onFitPerspective, modifier = Modifier.weight(1f)) { Text("Fit perspective") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onDetect, enabled = !detecting, modifier = Modifier.weight(1f)) {
            Text(if (detecting) "Detecting…" else "Detect perspective")
        }
        OutlinedButton(
            onClick = { onPerspectiveChange { PerspectiveSettings() } },
            modifier = Modifier.weight(1f),
        ) { Text("Reset") }
    }
    if (perspective.points.isEmpty()) {
        Text("Detect points, or add one manually. Tap the image to place the guide anchor.")
    } else {
        perspective.points.forEachIndexed { index, point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Point ${index + 1}", Modifier.weight(1f))
                Switch(
                    checked = point.enabled,
                    onCheckedChange = { enabled ->
                        onPerspectiveChange {
                            it.copy(points = it.points.mapIndexed { pointIndex, item ->
                                if (pointIndex == index) item.copy(enabled = enabled) else item
                            })
                        }
                    },
                )
            }
        }
    }
    OutlinedButton(
        onClick = {
            onPerspectiveChange {
                it.copy(points = it.points + VanishingPoint(NormalizedPoint(0.5f, 0.5f)))
            }
        },
    ) { Text("Add point") }
    SettingSlider(
        "Guide opacity: ${(perspective.opacity * 100).toInt()}%",
        perspective.opacity,
        0.2f..1f,
        steps = 7,
    ) { value -> onPerspectiveChange { it.copy(opacity = value) } }
    SettingSlider(
        "Guide thickness: ${"%.1f".format(perspective.thickness)} px",
        perspective.thickness,
        1f..6f,
        steps = 4,
    ) { value -> onPerspectiveChange { it.copy(thickness = value) } }
    Text("Guide color", fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Yellow" to 0xFFFFD740L, "White" to 0xFFFFFFFFL, "Red" to 0xFFFF5252L).forEach { (name, color) ->
            FilterChip(
                selected = perspective.color == color,
                onClick = { onPerspectiveChange { it.copy(color = color) } },
                label = { Text(name) },
            )
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
    DrawingGridTheme { DrawingGridScreen(null, GridSettings(), {}, {}, {}) }
}
