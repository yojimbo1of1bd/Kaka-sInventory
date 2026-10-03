package com.projectkaka.inventory.util

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.EnumMap

/**
 * CameraX ImageAnalysis.Analyzer that decodes QR codes and 1D barcodes offline using ZXing.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(
                DecodeHintType.POSSIBLE_FORMATS,
                listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.UPC_A
                )
            )
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    private var isScanningEnabled = true

    fun pause() {
        isScanningEnabled = false
    }

    fun resume() {
        isScanningEnabled = true
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanningEnabled) {
            imageProxy.close()
            return
        }

        val plane = imageProxy.planes.firstOrNull()
        if (plane == null) {
            imageProxy.close()
            return
        }

        try {
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val buffer = plane.buffer

            // Extract pure Y-plane without hardware rowStride padding
            val yBytes = ByteArray(width * height)
            if (rowStride == width && pixelStride == 1) {
                buffer.get(yBytes, 0, width * height)
            } else {
                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    if (pixelStride == 1) {
                        buffer.get(yBytes, row * width, width)
                    } else {
                        for (col in 0 until width) {
                            yBytes[row * width + col] = buffer.get(row * rowStride + col * pixelStride)
                        }
                    }
                }
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // If rotation is 90 or 270 degrees, rotate the YUV plane
            val (rotatedData, rotatedWidth, rotatedHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                rotateYuv(yBytes, width, height, rotationDegrees)
            } else {
                Triple(yBytes, width, height)
            }

            val source = PlanarYUVLuminanceSource(
                rotatedData,
                rotatedWidth,
                rotatedHeight,
                0,
                0,
                rotatedWidth,
                rotatedHeight,
                false
            )

            val result = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: Exception) {
                try {
                    reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
                } catch (_: Exception) {
                    try {
                        val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                        reader.decodeWithState(invertedBitmap)
                    } catch (_: Exception) {
                        null
                    }
                }
            } finally {
                reader.reset()
            }

            result?.text?.let { rawText ->
                val cleanedText = rawText.trim()
                if (cleanedText.isNotEmpty()) {
                    isScanningEnabled = false
                    onBarcodeDetected(cleanedText)
                }
            }
        } catch (_: Exception) {
            // Ignore frame decode exceptions
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateYuv(
        data: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ): Triple<ByteArray, Int, Int> {
        val rotatedData = ByteArray(imageWidth * imageHeight)

        if (rotationDegrees == 90) {
            var i = 0
            for (x in 0 until imageWidth) {
                for (y in imageHeight - 1 downTo 0) {
                    rotatedData[i++] = data[y * imageWidth + x]
                }
            }
            return Triple(rotatedData, imageHeight, imageWidth)
        } else if (rotationDegrees == 270) {
            var i = 0
            for (x in imageWidth - 1 downTo 0) {
                for (y in 0 until imageHeight) {
                    rotatedData[i++] = data[y * imageWidth + x]
                }
            }
            return Triple(rotatedData, imageHeight, imageWidth)
        }

        return Triple(data, imageWidth, imageHeight)
    }
}
