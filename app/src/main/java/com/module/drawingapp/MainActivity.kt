package com.module.drawingapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import coil.compose.rememberAsyncImagePainter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        ), 0)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImageEditorScreen()
                }
            }
        }
    }
}


@Composable
fun ImageEditorScreen() {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    val pathList = remember { mutableStateListOf<Pair<ComposePath, ComposeColor>>() }
    var currentPath by remember { mutableStateOf(ComposePath()) }

    val bitmap = remember(imageUri) {
        imageUri?.let {
            MediaStore.Images.Media.getBitmap(context.contentResolver, it)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        imageUri = it
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { pickImageLauncher.launch("image/*") }) { Text("Pick Image") }
            Button(onClick = { rotation += 90f }) { Text("Rotate") }
            Button(onClick = {
                imageUri?.let {
                    saveEditedImage(context, it, pathList, scale, rotation)
                }
            }) { Text("Save") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor.LightGray)
        ) {
            bitmap?.let { bmp ->
                val imageBitmap = bmp.asImageBitmap()
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, rotate ->
                                scale *= zoom
                                rotation += rotate
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    currentPath = ComposePath()
                                    currentPath.moveTo(it.x, it.y)
                                },
                                onDrag = { change, _ ->
                                    currentPath.lineTo(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    pathList.add(currentPath to ComposeColor.Red)
                                }
                            )
                        }
                ) {
                    drawIntoCanvas { canvas ->
                        withTransform({
                            scale(scale, scale)
                            rotate(rotation)
                        }) {
                            drawImage(imageBitmap, topLeft = Offset.Zero)
                        }

                        pathList.forEach { (path, color) ->
                            drawPath(
                                path = path,
                                color = color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun saveEditedImage(
    context: android.content.Context,
    uri: Uri,
    pathList: List<Pair<ComposePath, ComposeColor>>,
    scale: Float,
    rotation: Float
) {
    val sourceBitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(resultBitmap)
    canvas.save()
    canvas.scale(scale, scale)
    canvas.rotate(rotation)
    canvas.drawBitmap(sourceBitmap, 0f, 0f, null)

    val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    pathList.forEach { (p, c) ->
        paint.color = c.toArgb()
        canvas.drawPath(p.asAndroidPath(), paint)
    }
    canvas.restore()

    val filename = "Edited_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
    val contentValues = android.content.ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }
    val uriOut = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    val outputStream: OutputStream? = uriOut?.let { context.contentResolver.openOutputStream(it) }
    outputStream?.use {
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
    }
    outputStream?.flush()
    outputStream?.close()
}

fun ComposeColor.toArgb(): Int {
    return Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
