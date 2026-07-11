package com.example.terminalicon

import com.intellij.openapi.util.IconLoader
import java.awt.Color
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.FilteredImageSource
import java.awt.image.RGBImageFilter
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Builds the per-command icon shown in the Fast Run menu, the terminal context menu and the
 * command list in the dialog. It takes the monochrome `/icons/terminal.svg` as a base and tints
 * it with the color selected for the command ("default", "green", "blue", "red", "yellow",
 * "purple"). "default" keeps the icon's original color. Antialiased edges are preserved because
 * only the RGB channels are replaced; per-pixel alpha is kept.
 */
object CommandIcons {
    private val baseIcon: Icon = IconLoader.getIcon("/icons/terminal.svg", CommandIcons::class.java)
    private val cache = ConcurrentHashMap<String, Icon>()

    private fun colorFor(name: String): Color? = when (name) {
        "green" -> Color(0x59, 0xA8, 0x69)
        "blue" -> Color(0x38, 0x9F, 0xD6)
        "red" -> Color(0xDB, 0x58, 0x60)
        "yellow" -> Color(0xED, 0xA2, 0x00)
        "purple" -> Color(0x93, 0x70, 0xDB)
        else -> null // "default" (or unknown): keep the original icon color
    }

    fun forColor(color: String): Icon {
        val tint = colorFor(color) ?: return baseIcon
        return cache.getOrPut(color) { tint(baseIcon, tint) }
    }

    /** Renders [icon] to an image, then replaces every pixel's RGB with [color] while keeping its alpha. */
    private fun tint(icon: Icon, color: Color): Icon {
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        icon.paintIcon(null, g, 0, 0)
        g.dispose()

        val rgb = color.rgb and 0x00FFFFFF
        val filter = object : RGBImageFilter() {
            override fun filterRGB(x: Int, y: Int, argb: Int): Int = (argb and 0xFF000000.toInt()) or rgb
        }
        val producer = FilteredImageSource(image.source, filter)
        return ImageIcon(Toolkit.getDefaultToolkit().createImage(producer))
    }
}