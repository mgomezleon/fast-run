package com.example.terminalicon

import com.intellij.openapi.util.IconLoader
import java.awt.Color
import java.awt.Image
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.FilteredImageSource
import java.awt.image.RGBImageFilter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Builds the icon shown next to a saved command in the Fast Run menu, the terminal context menu
 * and the command list in the dialog.
 *
 * Two modes, chosen per command:
 *  - **Custom icon**: if the command has a [SavedCommand.customIconPath], that image file is used
 *    directly (SVG or raster), scaled to 16x16, with no color tint.
 *  - **Color**: otherwise the monochrome `/icons/terminal.svg` glyph is tinted with the command's
 *    color ("default", "green", "blue", "red", "yellow", "purple"). "default" keeps the original
 *    color. Only the RGB channels are replaced, so antialiased edges are preserved.
 */
object CommandIcons {
    private const val SIZE = 16

    private val baseIcon: Icon = IconLoader.getIcon("/icons/terminal.svg", CommandIcons::class.java)
    private val colorCache = ConcurrentHashMap<String, Icon>()
    private val customCache = ConcurrentHashMap<String, Icon>()

    /** Resolves the icon for [command]: custom image if set (and loadable), else color-tinted glyph. */
    fun forCommand(command: SavedCommand): Icon {
        val path = command.customIconPath
        if (path.isNotBlank()) {
            customCache[path]?.let { return it }
            loadCustomIcon(path)?.let { customCache[path] = it; return it }
            // fall through to the color icon if the custom file is missing or unreadable
        }
        return forColor(command.icon)
    }

    fun forColor(color: String): Icon {
        val tint = colorFor(color) ?: return baseIcon
        return colorCache.getOrPut(color) { tint(baseIcon, tint) }
    }

    private fun colorFor(name: String): Color? = when (name) {
        "green" -> Color(0x59, 0xA8, 0x69)
        "blue" -> Color(0x38, 0x9F, 0xD6)
        "red" -> Color(0xDB, 0x58, 0x60)
        "yellow" -> Color(0xED, 0xA2, 0x00)
        "purple" -> Color(0x93, 0x70, 0xDB)
        else -> null // "default" (or unknown): keep the original icon color
    }

    /** Loads an arbitrary image file (SVG or raster) as a 16x16 icon, or null if it can't be read. */
    private fun loadCustomIcon(path: String): Icon? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            val raster: BufferedImage? = try { ImageIO.read(file) } catch (e: Exception) { null }
            val image: BufferedImage = raster ?: run {
                // Not a raster ImageIO format (e.g. SVG): let the platform render it.
                val icon = IconLoader.findIcon(file.toURI().toURL()) ?: return null
                toImage(icon)
            }
            scaleTo(image, SIZE)
        } catch (e: Exception) {
            null
        }
    }

    private fun toImage(icon: Icon): BufferedImage {
        val image = BufferedImage(icon.iconWidth.coerceAtLeast(1), icon.iconHeight.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            icon.paintIcon(null, g, 0, 0)
        } finally {
            g.dispose()
        }
        return image
    }

    private fun scaleTo(image: BufferedImage, size: Int): Icon {
        if (image.width == size && image.height == size) return ImageIcon(image)
        return ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH))
    }

    /** Renders [icon] to an image, then replaces every pixel's RGB with [color] while keeping its alpha. */
    private fun tint(icon: Icon, color: Color): Icon {
        val image = toImage(icon)
        val rgb = color.rgb and 0x00FFFFFF
        val filter = object : RGBImageFilter() {
            override fun filterRGB(x: Int, y: Int, argb: Int): Int = (argb and 0xFF000000.toInt()) or rgb
        }
        val producer = FilteredImageSource(image.source, filter)
        return ImageIcon(Toolkit.getDefaultToolkit().createImage(producer))
    }
}
