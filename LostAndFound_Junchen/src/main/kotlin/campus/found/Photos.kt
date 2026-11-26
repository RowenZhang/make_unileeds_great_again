package campus.found

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.roundToInt

const val MAX_PHOTO = 5 * 1024 * 1024

fun processPhoto(bytes: ByteArray): Photo {
    if (bytes.isEmpty()) throw InputProblem(Problem.PHOTO_DATA)
    if (bytes.size > MAX_PHOTO) throw InputProblem(Problem.PHOTO_SIZE)
    try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw InputProblem(Problem.PHOTO_TYPE)
            val reader = readers.next()
            try {
                val format = reader.formatName.lowercase()
                if (format !in setOf("jpeg", "png")) throw InputProblem(Problem.PHOTO_TYPE)
                reader.setInput(input, true, true)
                val w = reader.getWidth(0)
                val h = reader.getHeight(0)
                if (w !in 1..8192 || h !in 1..8192 || w.toLong() * h > 16_000_000)
                    throw InputProblem(Problem.PHOTO_DIMENSIONS)
                val param = reader.defaultReadParam
                val sample = (maxOf(w, h) / 1600).coerceAtLeast(1)
                param.setSourceSubsampling(sample, sample, 0, 0)
                val picture = reader.read(0, param)
                val scale = minOf(1.0, 1600.0 / maxOf(w, h))
                val out =
                    BufferedImage(
                        (w * scale).roundToInt().coerceAtLeast(1),
                        (h * scale).roundToInt().coerceAtLeast(1),
                        if (format == "png") BufferedImage.TYPE_INT_ARGB
                        else BufferedImage.TYPE_INT_RGB,
                    )
                val graphics = out.createGraphics()
                try {
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    graphics.drawImage(picture, 0, 0, out.width, out.height, null)
                } finally {
                    graphics.dispose()
                }
                val encoded = ByteArrayOutputStream()
                check(ImageIO.write(out, format, encoded))
                return Photo(encoded.toByteArray(), "image/$format")
            } finally {
                reader.dispose()
            }
        }
    } catch (_: IOException) {
        throw InputProblem(Problem.PHOTO_DATA)
    }
}
