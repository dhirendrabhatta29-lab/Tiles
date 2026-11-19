package com.matte.launcher

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders OxygenOS-style large tiles:
 * - 2x1 Wide Cards: icon + title + live contextual status + compact action pill.
 * - 2x2+ Rich Cards: header + contextual live card content (media/status/shortcuts).
 * - 2x2 Large Folder Clusters: directly tappable top-3 icons + bottom-right mini-grid to open full folder.
 */
internal object LargeTileRenderer {

    data class Action(val label: String, val onClick: () -> Unit)

    fun buildWideCard(
        context: Context,
        u: Float,
        iconView: View,
        title: String,
        subtitle: String,
        tileColors: TileColors,
        quickAction: Action?
    ): LinearLayout {
        fun px(v: Float) = (v * u).roundToInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12f), px(8f), px(12f), px(8f))
        }
        val iconFrame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor((tileColors.foreground and 0x00FFFFFF) or 0x1A000000)
                cornerRadius = px(12f).toFloat()
            }
            setPadding(px(4f), px(4f), px(4f), px(4f))
            addView(iconView, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(iconFrame, LinearLayout.LayoutParams(px(42f), px(42f)))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textCol.addView(TextView(context).apply {
            text = title
            setTextColor(tileColors.foreground)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u * 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(context).apply {
            text = subtitle
            setTextColor((tileColors.foreground and 0x00FFFFFF) or 0xB8000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u * 11f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, px(2f), 0, 0)
        })
        root.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = px(10f)
            if (quickAction != null) marginEnd = px(8f)
        })

        if (quickAction != null) {
            val pill = TextView(context).apply {
                text = quickAction.label
                setTextColor(tileColors.foreground)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, u * 10.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                setSingleLine()
                setPadding(px(10f), px(6f), px(10f), px(6f))
                background = GradientDrawable().apply {
                    setColor((tileColors.foreground and 0x00FFFFFF) or 0x22000000)
                    cornerRadius = px(14f).toFloat()
                    setStroke(max(1, px(1f)), (tileColors.foreground and 0x00FFFFFF) or 0x38000000)
                }
                setOnClickListener { quickAction.onClick() }
            }
            root.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return root
    }

    /**
     * Builds an OxygenOS-style 2x2 interactive folder cluster:
     * Top-left, top-right, bottom-left cells launch the first 3 folder apps directly;
     * bottom-right cell renders a 2x2 mini-cluster of remaining apps that opens the full folder.
     */
    fun buildLargeFolderContent(
        context: Context,
        u: Float,
        folderName: String,
        apps: List<App>,
        tileColors: TileColors,
        cornerRadiusPx: Float,
        createAppIcon: (App, Boolean) -> Pair<TileView, Int>,
        onLaunchApp: (TileHost, App) -> Unit,
        onOpenFolder: () -> Unit,
        onBindBadge: (TileHost, App, Boolean) -> Unit,
        previewViewsOut: MutableList<TileHost>
    ): LinearLayout {
        fun px(v: Float) = (v * u).roundToInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10f), px(10f), px(10f), px(8f))
        }
        val innerGap = px(6f).coerceAtLeast(3)
        val cellRadius = (cornerRadiusPx * 0.65f).coerceAtLeast(px(8f).toFloat())

        fun directTile(app: App?): View {
            if (app == null) {
                return View(context).apply {
                    background = GradientDrawable().apply {
                        setColor((tileColors.foreground and 0x00FFFFFF) or 0x12000000)
                        cornerRadius = cellRadius
                    }
                }
            }
            val (face, bg) = createAppIcon(app, false)
            face.setBackgroundColor(Color.TRANSPARENT)
            return TileHost(context).apply {
                background = GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = cellRadius
                }
                clipToOutline = true
                this.face = face
                badgeInk = TileColors.solid(bg).foreground
                addView(face, FrameLayout.LayoutParams(-1, -1))
                onBindBadge(this, app, true)
                previewViewsOut.add(this)
                setOnClickListener { onLaunchApp(this, app) }
            }
        }

        val topRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(directTile(apps.getOrNull(0)), LinearLayout.LayoutParams(0, -1, 1f))
        topRow.addView(directTile(apps.getOrNull(1)), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = innerGap })
        root.addView(topRow, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottomRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(directTile(apps.getOrNull(2)), LinearLayout.LayoutParams(0, -1, 1f))

        // Bottom-right cell: mini 2x2 cluster of remaining apps -> opens full folder
        val miniCluster = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(4f), px(4f), px(4f), px(4f))
            background = GradientDrawable().apply {
                setColor((tileColors.foreground and 0x00FFFFFF) or 0x1A000000)
                cornerRadius = cellRadius
            }
            setOnClickListener { onOpenFolder() }
        }
        val miniGap = px(3f).coerceAtLeast(2)
        val remaining = apps.drop(3).take(4)
        repeat(2) { r ->
            val mr = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(2) { c ->
                val subApp = remaining.getOrNull(r * 2 + c)
                val dot: View = if (subApp != null) {
                    val (f, bg) = createAppIcon(subApp, false)
                    f.setBackgroundColor(Color.TRANSPARENT)
                    FrameLayout(context).apply {
                        background = GradientDrawable().apply {
                            setColor(bg)
                            cornerRadius = px(5f).toFloat()
                        }
                        clipToOutline = true
                        addView(f, FrameLayout.LayoutParams(-1, -1))
                    }
                } else {
                    View(context).apply {
                        background = GradientDrawable().apply {
                            setColor((tileColors.foreground and 0x00FFFFFF) or 0x18000000)
                            cornerRadius = px(5f).toFloat()
                        }
                    }
                }
                mr.addView(dot, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    if (c > 0) marginStart = miniGap
                })
            }
            miniCluster.addView(mr, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                if (r > 0) topMargin = miniGap
            })
        }
        bottomRow.addView(miniCluster, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = innerGap })
        root.addView(bottomRow, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = innerGap })

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(2f), px(6f), px(2f), 0)
            setOnClickListener { onOpenFolder() }
        }
        footer.addView(TextView(context).apply {
            text = folderName
            setTextColor(tileColors.foreground)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u * 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(TextView(context).apply {
            text = "${apps.size}"
            setTextColor((tileColors.foreground and 0x00FFFFFF) or 0xB0000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u * 10.5f)
            typeface = Typeface.MONOSPACE
        })
        root.addView(footer, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    /** Formats a concise status/shortcut subtitle for 2x1 wide OxygenOS cards. */
    fun wideSubtitle(app: App?, shortcuts: List<ShortcutInfo>, notifPreview: String?): String {
        if (!notifPreview.isNullOrBlank()) return notifPreview
        val firstShortcut = shortcuts.firstOrNull()?.shortLabel?.toString()
        if (!firstShortcut.isNullOrBlank()) return firstShortcut
        return app?.component?.packageName?.substringAfterLast('.') ?: "tap to open"
    }
}
